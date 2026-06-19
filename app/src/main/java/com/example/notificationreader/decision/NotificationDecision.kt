package com.example.notificationreader.decision

import com.example.notificationreader.NotificationSpeechDuplicateDecision
import com.example.notificationreader.history.NotificationHistoryInterpretation
import com.example.notificationreader.history.RawNotificationEvent

data class NotificationDecision(
    val historyWritingAllowed: Boolean,
    val speechAllowed: Boolean,
    val finalSpeechText: String?,
    val speechTemplateId: String?,
    val finalRuleId: DecisionRuleId,
    val explanation: String,
    val trace: List<DecisionTraceStep>
) {
    val historySummary: String
        get() = if (historyWritingAllowed) "ALLOW" else "SKIP"

    val speechSummary: String
        get() = if (speechAllowed) "ALLOW" else "SKIP"
}

data class NotificationSpeechSelection(
    val text: String,
    val templateId: String
)

class NotificationDecisionCoordinator {
    fun decide(
        event: RawNotificationEvent,
        historyInterpretation: NotificationHistoryInterpretation,
        speechDuplicateDecision: NotificationSpeechDuplicateDecision? = null,
        speechSelection: NotificationSpeechSelection? = null,
        speechPrefsEnabled: Boolean = true
    ): NotificationDecision {
        val trace = mutableListOf<DecisionTraceStep>()
        trace += step(
            DecisionRuleId.RAW_EVENT_RECEIVED,
            "Received notification",
            DecisionTraceResult.ALLOWED,
            "Notification event reached processing"
        )
        trace += step(
            DecisionRuleId.EVENT_EXTRACTED,
            "Extracted normalized event",
            DecisionTraceResult.ALLOWED,
            "Raw notification event was extracted",
            safeEventMetadata(event)
        )
        trace += historyTraceSteps(historyInterpretation)

        val historyAllowed = historyInterpretation.shouldWriteHistory
        trace += step(
            if (historyAllowed) DecisionRuleId.HISTORY_ALLOWED else DecisionRuleId.HISTORY_SKIPPED,
            "History decision",
            if (historyAllowed) DecisionTraceResult.ALLOWED else DecisionTraceResult.SKIPPED,
            if (historyAllowed) {
                "History record is allowed by the existing interpreter"
            } else {
                "History record is not written because ${historyInterpretation.reason}"
            }
        )

        val speechOutcome = speechOutcome(historyInterpretation, speechPrefsEnabled, speechDuplicateDecision, speechSelection)
        trace += speechOutcome.steps

        return NotificationDecision(
            historyWritingAllowed = historyAllowed,
            speechAllowed = speechOutcome.allowed,
            finalSpeechText = if (speechOutcome.allowed) speechSelection?.text else null,
            speechTemplateId = if (speechOutcome.allowed) speechSelection?.templateId else null,
            finalRuleId = speechOutcome.finalRuleId ?: historyRuleId(historyInterpretation),
            explanation = speechOutcome.explanation ?: historyInterpretation.explanation,
            trace = trace
        )
    }

    private data class SpeechOutcome(
        val allowed: Boolean,
        val finalRuleId: DecisionRuleId?,
        val explanation: String?,
        val steps: List<DecisionTraceStep>
    )

    private fun speechOutcome(
        historyInterpretation: NotificationHistoryInterpretation,
        speechPrefsEnabled: Boolean,
        speechDuplicateDecision: NotificationSpeechDuplicateDecision?,
        speechSelection: NotificationSpeechSelection?
    ): SpeechOutcome {
        if (!historyInterpretation.shouldSpeak) {
            return SpeechOutcome(
                allowed = false,
                finalRuleId = historyRuleId(historyInterpretation),
                explanation = historyInterpretation.explanation,
                steps = listOf(
                    step(
                        DecisionRuleId.SPEECH_ALLOWED,
                        "Speech decision",
                        DecisionTraceResult.BLOCKED,
                        "Speech is blocked by ${historyInterpretation.reason}"
                    )
                )
            )
        }

        if (!speechPrefsEnabled) {
            return SpeechOutcome(
                allowed = false,
                finalRuleId = DecisionRuleId.SPEECH_PREFS_DISABLED,
                explanation = "Speech is disabled in notification speech preferences",
                steps = listOf(
                    step(
                        DecisionRuleId.SPEECH_PREFS_DISABLED,
                        "Speech preferences",
                        DecisionTraceResult.BLOCKED,
                        "Speech is disabled in notification speech preferences"
                    )
                )
            )
        }

        return when (speechDuplicateDecision) {
            NotificationSpeechDuplicateDecision.DuplicateNotificationKey -> SpeechOutcome(
                allowed = false,
                finalRuleId = DecisionRuleId.SPEECH_DUPLICATE_KEY,
                explanation = "Speech duplicate notification key was blocked by the existing speech policy",
                steps = listOf(
                    step(
                        DecisionRuleId.SPEECH_DUPLICATE_KEY,
                        "Speech duplicate key",
                        DecisionTraceResult.BLOCKED,
                        "Duplicate speech notification key"
                    )
                )
            )
            NotificationSpeechDuplicateDecision.DuplicateFingerprintWithinWindow -> SpeechOutcome(
                allowed = false,
                finalRuleId = DecisionRuleId.SPEECH_DUPLICATE_FINGERPRINT,
                explanation = "Speech duplicate fingerprint was blocked by the existing speech policy",
                steps = listOf(
                    step(
                        DecisionRuleId.SPEECH_DUPLICATE_FINGERPRINT,
                        "Speech duplicate fingerprint",
                        DecisionTraceResult.BLOCKED,
                        "Duplicate speech fingerprint within the speech duplicate window"
                    )
                )
            )
            NotificationSpeechDuplicateDecision.Allow -> SpeechOutcome(
                allowed = speechSelection != null,
                finalRuleId = if (speechSelection != null) DecisionRuleId.SPEECH_EXECUTED else DecisionRuleId.SPEECH_ALLOWED,
                explanation = if (speechSelection != null) {
                    "Speech text was selected and allowed"
                } else {
                    "Speech duplicate policy allowed the event"
                },
                steps = buildList {
                    add(
                        step(
                            DecisionRuleId.SPEECH_ALLOWED,
                            "Speech duplicate decision",
                            DecisionTraceResult.ALLOWED,
                            "Speech duplicate policy allowed the event"
                        )
                    )
                    if (speechSelection != null) {
                        add(
                            step(
                                DecisionRuleId.SPEECH_TEMPLATE_SELECTED,
                                "Speech template selected",
                                DecisionTraceResult.ALLOWED,
                                "Existing speech path selected text",
                                mapOf("template" to speechSelection.templateId)
                            )
                        )
                        add(
                            step(
                                DecisionRuleId.SPEECH_EXECUTED,
                                "Speech executed",
                                DecisionTraceResult.ALLOWED,
                                "Speech text is sent to TextToSpeech"
                            )
                        )
                    }
                }
            )
            null -> SpeechOutcome(
                allowed = false,
                finalRuleId = null,
                explanation = null,
                steps = listOf(
                    step(
                        DecisionRuleId.SPEECH_ALLOWED,
                        "Speech decision",
                        DecisionTraceResult.SKIPPED,
                        "Speech duplicate policy was not evaluated"
                    )
                )
            )
        }
    }

    private fun historyTraceSteps(interpretation: NotificationHistoryInterpretation): List<DecisionTraceStep> {
        val finalHistoryRule = historyRuleId(interpretation)
        val steps = mutableListOf<DecisionTraceStep>()
        steps += orderedHistoryStep(
            ruleId = DecisionRuleId.IGNORE_SYSTEM,
            name = "Checked system-package rule",
            finalHistoryRule = finalHistoryRule,
            matchedExplanation = interpretation.explanation
        )
        if (finalHistoryRule == DecisionRuleId.IGNORE_SYSTEM) return steps

        steps += orderedHistoryStep(
            ruleId = DecisionRuleId.IGNORE_SERVICE,
            name = "Checked service-category rule",
            finalHistoryRule = finalHistoryRule,
            matchedExplanation = interpretation.explanation
        )
        if (finalHistoryRule == DecisionRuleId.IGNORE_SERVICE) return steps

        steps += orderedHistoryStep(
            ruleId = DecisionRuleId.IGNORE_EMPTY,
            name = "Checked empty-content rule",
            finalHistoryRule = finalHistoryRule,
            matchedExplanation = interpretation.explanation
        )
        if (finalHistoryRule == DecisionRuleId.IGNORE_EMPTY) return steps

        steps += orderedHistoryStep(
            ruleId = DecisionRuleId.DUPLICATE_CONTENT_WITHIN_WINDOW,
            name = "Checked history content duplicate rule",
            finalHistoryRule = finalHistoryRule,
            matchedExplanation = interpretation.explanation,
            matchedMetadata = historyMetadata(interpretation)
        )
        if (finalHistoryRule == DecisionRuleId.DUPLICATE_CONTENT_WITHIN_WINDOW) return steps

        steps += step(
            DecisionRuleId.MEANINGFUL_NOTIFICATION,
            "Checked meaningful notification rule",
            DecisionTraceResult.ALLOWED,
            interpretation.explanation
        )
        return steps
    }

    private fun orderedHistoryStep(
        ruleId: DecisionRuleId,
        name: String,
        finalHistoryRule: DecisionRuleId,
        matchedExplanation: String,
        matchedMetadata: Map<String, String> = emptyMap()
    ): DecisionTraceStep {
        val matched = ruleId == finalHistoryRule
        return step(
            ruleId,
            name,
            if (matched) DecisionTraceResult.MATCHED else DecisionTraceResult.NOT_MATCHED,
            if (matched) matchedExplanation else "Rule did not match",
            if (matched) matchedMetadata else emptyMap()
        )
    }

    private fun historyRuleId(interpretation: NotificationHistoryInterpretation): DecisionRuleId {
        return when (interpretation.reason) {
            "IGNORE_SYSTEM" -> DecisionRuleId.IGNORE_SYSTEM
            "IGNORE_SERVICE" -> DecisionRuleId.IGNORE_SERVICE
            "IGNORE_EMPTY" -> DecisionRuleId.IGNORE_EMPTY
            "DUPLICATE_CONTENT_WITHIN_WINDOW" -> DecisionRuleId.DUPLICATE_CONTENT_WITHIN_WINDOW
            else -> DecisionRuleId.MEANINGFUL_NOTIFICATION
        }
    }

    private fun historyMetadata(interpretation: NotificationHistoryInterpretation): Map<String, String> {
        return buildMap {
            interpretation.historyFingerprintHash?.let { put("historyFingerprintHash", it) }
            interpretation.matchingRecordAgeMillis?.let { put("matchingRecordAgeMillis", it.toString()) }
        }
    }

    private fun safeEventMetadata(event: RawNotificationEvent): Map<String, String> {
        return mapOf(
            "packageName" to event.packageName,
            "category" to event.category,
            "hasImageOrLargeIcon" to event.hasImageOrLargeIcon.toString()
        )
    }

    private fun step(
        ruleId: DecisionRuleId,
        name: String,
        result: DecisionTraceResult,
        explanation: String,
        metadata: Map<String, String> = emptyMap()
    ): DecisionTraceStep {
        return DecisionTraceStep(
            ruleId = ruleId,
            name = name,
            result = result,
            explanation = explanation,
            metadata = metadata
        )
    }
}
