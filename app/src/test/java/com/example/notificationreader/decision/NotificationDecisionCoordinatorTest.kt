package com.example.notificationreader.decision

import com.example.notificationreader.NotificationSpeechDuplicateDecision
import com.example.notificationreader.history.NotificationHistoryInterpreter
import com.example.notificationreader.history.RawNotificationEvent
import com.example.notificationreader.message.MessageSpeechBuilder
import com.example.notificationreader.message.MessageSpeechVariant
import com.example.notificationreader.message.SemanticMessageNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDecisionCoordinatorTest {
    private val coordinator = NotificationDecisionCoordinator()

    @Test
    fun ignoreSystemBlocksHistoryAndSpeech() {
        val interpretation = NotificationHistoryInterpreter().interpret(
            rawEvent(packageName = "com.android.systemui", category = "status")
        )
        val decision = coordinator.decide(rawEvent(packageName = "com.android.systemui", category = "status"), interpretation)

        assertFalse(decision.historyWritingAllowed)
        assertFalse(decision.speechAllowed)
        assertEquals(DecisionRuleId.IGNORE_SYSTEM, decision.finalRuleId)
        assertNull(decision.finalSpeechText)
    }

    @Test
    fun ignoreServiceBlocksHistoryAndSpeech() {
        val event = rawEvent(packageName = "com.windyty.android", category = "service")
        val decision = coordinator.decide(event, NotificationHistoryInterpreter().interpret(event))

        assertFalse(decision.historyWritingAllowed)
        assertFalse(decision.speechAllowed)
        assertEquals(DecisionRuleId.IGNORE_SERVICE, decision.finalRuleId)
    }

    @Test
    fun ignoreEmptyBlocksHistoryAndSpeech() {
        val event = rawEvent(title = "", text = "")
        val decision = coordinator.decide(event, NotificationHistoryInterpreter().interpret(event))

        assertFalse(decision.historyWritingAllowed)
        assertFalse(decision.speechAllowed)
        assertEquals(DecisionRuleId.IGNORE_EMPTY, decision.finalRuleId)
    }

    @Test
    fun meaningfulNotificationAllowsHistoryAndSpeechLikeExistingInterpreter() {
        val event = rawEvent(packageName = "com.example.mail", category = "email", title = "Inbox", text = "Hello")
        val interpretation = NotificationHistoryInterpreter().interpret(event)
        val decision = coordinator.decide(
            event = event,
            historyInterpretation = interpretation,
            speechDuplicateDecision = NotificationSpeechDuplicateDecision.Allow,
            speechSelection = NotificationSpeechSelection("Nowa wiadomosc e-mail", "ANNOUNCEMENT_FALLBACK")
        )

        assertEquals(interpretation.shouldWriteHistory, decision.historyWritingAllowed)
        assertEquals(interpretation.shouldSpeak, decision.speechAllowed)
        assertEquals("Nowa wiadomosc e-mail", decision.finalSpeechText)
        assertEquals(DecisionRuleId.SPEECH_EXECUTED, decision.finalRuleId)
    }

    @Test
    fun historyDuplicateBlocksHistoryWithDuplicateRule() {
        val interpreter = NotificationHistoryInterpreter(duplicateWindowMillis = 15.minutesMillis)
        val first = rawEvent(receivedAt = 10_000L)
        val second = rawEvent(receivedAt = 12_000L)

        interpreter.interpret(first)
        val decision = coordinator.decide(second, interpreter.interpret(second))

        assertFalse(decision.historyWritingAllowed)
        assertFalse(decision.speechAllowed)
        assertEquals(DecisionRuleId.DUPLICATE_CONTENT_WITHIN_WINDOW, decision.finalRuleId)
        assertTrue(decision.trace.any { it.ruleId == DecisionRuleId.DUPLICATE_CONTENT_WITHIN_WINDOW })
    }

    @Test
    fun historyDuplicateDoesNotAutomaticallyInventSpeechBlock() {
        val interpreter = NotificationHistoryInterpreter(duplicateWindowMillis = 15.minutesMillis)
        val first = rawEvent(receivedAt = 10_000L)
        val second = rawEvent(receivedAt = 12_000L)

        interpreter.interpret(first)
        val decision = coordinator.decide(
            event = second,
            historyInterpretation = interpreter.interpret(second),
            speechDuplicateDecision = NotificationSpeechDuplicateDecision.Allow,
            speechSelection = NotificationSpeechSelection("Nowe powiadomienie z aplikacji", "ANNOUNCEMENT_FALLBACK")
        )

        assertFalse(decision.historyWritingAllowed)
        assertTrue(decision.speechAllowed)
        assertEquals("Nowe powiadomienie z aplikacji", decision.finalSpeechText)
        assertEquals(DecisionRuleId.SPEECH_EXECUTED, decision.finalRuleId)
    }

    @Test
    fun speechDuplicatePolicyCanBlockSpeechIndependently() {
        val event = rawEvent()
        val decision = coordinator.decide(
            event = event,
            historyInterpretation = NotificationHistoryInterpreter().interpret(event),
            speechDuplicateDecision = NotificationSpeechDuplicateDecision.DuplicateNotificationKey
        )

        assertTrue(decision.historyWritingAllowed)
        assertFalse(decision.speechAllowed)
        assertEquals(DecisionRuleId.SPEECH_DUPLICATE_KEY, decision.finalRuleId)
    }

    @Test
    fun selectedSpeechTextMatchesExistingMessageBuilderOutput() {
        val message = SemanticMessageNotification(
            packageName = "com.facebook.orca",
            sender = "Anna",
            content = "Czesc"
        )
        val event = rawEvent(packageName = "com.facebook.orca", category = "msg")
        val expectedText = MessageSpeechBuilder.build(message)
        val expectedVariant = MessageSpeechBuilder.variantFor(message)

        val decision = coordinator.decide(
            event = event,
            historyInterpretation = NotificationHistoryInterpreter().interpret(event),
            speechDuplicateDecision = NotificationSpeechDuplicateDecision.Allow,
            speechSelection = NotificationSpeechSelection(expectedText, "MESSAGE_${expectedVariant.name}")
        )

        assertEquals(MessageSpeechVariant.FULL, expectedVariant)
        assertEquals("Anna. Czesc", decision.finalSpeechText)
        assertEquals("MESSAGE_FULL", decision.speechTemplateId)
    }

    @Test
    fun decisionTracePreservesEvaluationOrder() {
        val event = rawEvent(packageName = "com.windyty.android", category = "service")
        val decision = coordinator.decide(event, NotificationHistoryInterpreter().interpret(event))

        assertEquals(
            listOf(
                DecisionRuleId.RAW_EVENT_RECEIVED,
                DecisionRuleId.EVENT_EXTRACTED,
                DecisionRuleId.IGNORE_SYSTEM,
                DecisionRuleId.IGNORE_SERVICE,
                DecisionRuleId.HISTORY_SKIPPED,
                DecisionRuleId.SPEECH_ALLOWED
            ),
            decision.trace.map { it.ruleId }
        )
        assertEquals(DecisionTraceResult.NOT_MATCHED, decision.trace[2].result)
        assertEquals(DecisionTraceResult.MATCHED, decision.trace[3].result)
    }

    @Test
    fun finalRuleIdentifierMatchesRuleThatDeterminedResult() {
        val event = rawEvent()
        val decision = coordinator.decide(
            event = event,
            historyInterpretation = NotificationHistoryInterpreter().interpret(event),
            speechDuplicateDecision = NotificationSpeechDuplicateDecision.DuplicateFingerprintWithinWindow
        )

        assertEquals(DecisionRuleId.SPEECH_DUPLICATE_FINGERPRINT, decision.finalRuleId)
    }

    @Test
    fun diagnosticEntriesContainOneFinalDecisionLog() {
        val event = rawEvent()
        val decision = coordinator.decide(
            event = event,
            historyInterpretation = NotificationHistoryInterpreter().interpret(event),
            speechDuplicateDecision = NotificationSpeechDuplicateDecision.Allow,
            speechSelection = NotificationSpeechSelection("Nowe powiadomienie z aplikacji", "ANNOUNCEMENT_FALLBACK")
        )
        val entries = NotificationDecisionLogFormatter.diagnosticEntries(event, decision)

        assertEquals(1, entries.count { it.stage == "DECISION" })
        assertEquals(1, entries.count { it.stage == "DECISION_TRACE" })
        assertTrue(entries.single { it.stage == "DECISION" }.message.contains("finalRule=SPEECH_EXECUTED"))
    }

    private val Int.minutesMillis: Long
        get() = this * 60 * 1_000L

    private fun rawEvent(
        receivedAt: Long = 1_000L,
        postedAt: Long = 900L,
        notificationKey: String = "notification-key",
        packageName: String = "com.example.app",
        applicationName: String = "Example",
        title: String = "Title",
        text: String = "Text",
        expandedText: String = "Expanded",
        subText: String = "Inbox",
        category: String = "social",
        notificationId: Int = 7,
        tag: String = "tag",
        groupKey: String = "group",
        isGroupSummary: Boolean = false,
        hasImageOrLargeIcon: Boolean = false
    ): RawNotificationEvent {
        return RawNotificationEvent(
            receivedAt = receivedAt,
            postedAt = postedAt,
            notificationKey = notificationKey,
            packageName = packageName,
            applicationName = applicationName,
            title = title,
            text = text,
            expandedText = expandedText,
            subText = subText,
            category = category,
            notificationId = notificationId,
            tag = tag,
            groupKey = groupKey,
            isGroupSummary = isGroupSummary,
            hasImageOrLargeIcon = hasImageOrLargeIcon
        )
    }
}
