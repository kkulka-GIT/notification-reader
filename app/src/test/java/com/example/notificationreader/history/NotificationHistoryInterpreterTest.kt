package com.example.notificationreader.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryInterpreterTest {
    @Test
    fun emptyRawEventIsIgnored() {
        val result = NotificationHistoryInterpreter().interpret(rawEvent(title = "", text = ""))

        assertEquals(NotificationHistoryInterpretationAction.IGNORE, result.action)
        assertEquals("IGNORE_EMPTY", result.reason)
        assertFalse(result.shouldWriteHistory)
        assertFalse(result.shouldSpeak)
        assertNull(result.record)
    }

    @Test
    fun meaningfulRawEventCreatesHistoryRecord() {
        val result = NotificationHistoryInterpreter().interpret(rawEvent(title = "Mail", text = "Hello"))

        assertEquals(NotificationHistoryInterpretationAction.CREATE, result.action)
        assertEquals("MEANINGFUL_NOTIFICATION", result.reason)
        assertTrue(result.shouldWriteHistory)
        assertTrue(result.shouldSpeak)
        assertEquals("Mail", result.record?.title)
        assertEquals("Hello", result.record?.text)
    }

    @Test
    fun differentMeaningfulEventsWithSameNotificationKeyProduceSeparateHistoryRecords() {
        val interpreter = NotificationHistoryInterpreter()
        val first = interpreter.interpret(
            rawEvent(notificationKey = "same-key", title = "First", text = "Hello", postedAt = 10L, receivedAt = 100L)
        )
        val second = interpreter.interpret(
            rawEvent(notificationKey = "same-key", title = "Second", text = "World", postedAt = 11L, receivedAt = 101L)
        )

        assertEquals(NotificationHistoryInterpretationAction.CREATE, first.action)
        assertEquals(NotificationHistoryInterpretationAction.CREATE, second.action)
        assertNotEquals(first.record?.storageKey, second.record?.storageKey)
        assertEquals(2, listOfNotNull(first.record, second.record).map { it.storageKey }.distinct().size)
    }

    @Test
    fun identicalRepeatedRawEventWithinWindowDoesNotCreateDuplicateHistoryRecord() {
        val interpreter = NotificationHistoryInterpreter(duplicateWindowMillis = 5_000L)
        val first = interpreter.interpret(rawEvent(receivedAt = 10_000L))
        val second = interpreter.interpret(rawEvent(receivedAt = 12_000L))
        val storedRecords = listOf(first, second)
            .mapNotNull { it.record }
            .distinctBy { it.storageKey }

        assertEquals(NotificationHistoryInterpretationAction.CREATE, first.action)
        assertEquals(NotificationHistoryInterpretationAction.UPDATE, second.action)
        assertEquals("DUPLICATE_WITHIN_WINDOW", second.reason)
        assertEquals(1, storedRecords.size)
    }

    @Test
    fun identicalRawEventAfterWindowCreatesAgain() {
        val interpreter = NotificationHistoryInterpreter(duplicateWindowMillis = 5_000L)
        val first = interpreter.interpret(rawEvent(receivedAt = 10_000L))
        val second = interpreter.interpret(rawEvent(receivedAt = 15_001L))

        assertEquals(NotificationHistoryInterpretationAction.CREATE, first.action)
        assertEquals(NotificationHistoryInterpretationAction.CREATE, second.action)
    }

    @Test
    fun rawEventCarriesAndroidNotificationDetails() {
        val event = rawEvent(
            category = "msg",
            notificationId = 42,
            tag = "inbox",
            groupKey = "group",
            isGroupSummary = true,
            hasImageOrLargeIcon = true
        )

        assertEquals("msg", event.category)
        assertEquals(42, event.notificationId)
        assertEquals("inbox", event.tag)
        assertEquals("group", event.groupKey)
        assertTrue(event.isGroupSummary)
        assertTrue(event.hasImageOrLargeIcon)
    }

    @Test
    fun systemUiNotificationIsIgnoredForHistoryAndSpeech() {
        val result = NotificationHistoryInterpreter().interpret(
            rawEvent(packageName = "com.android.systemui", category = "status", title = "Charging", text = "Connected")
        )

        assertEquals(NotificationHistoryInterpretationAction.IGNORE, result.action)
        assertEquals("IGNORE_SYSTEM", result.reason)
        assertFalse(result.shouldWriteHistory)
        assertFalse(result.shouldSpeak)
        assertNull(result.record)
    }

    @Test
    fun serviceNotificationIsIgnoredForHistoryAndSpeech() {
        val result = NotificationHistoryInterpreter().interpret(
            rawEvent(packageName = "com.example.widget", category = "service", title = "Widget", text = "Updating widget")
        )

        assertEquals(NotificationHistoryInterpretationAction.IGNORE, result.action)
        assertEquals("IGNORE_SERVICE", result.reason)
        assertFalse(result.shouldWriteHistory)
        assertFalse(result.shouldSpeak)
        assertNull(result.record)
    }

    @Test
    fun meaningfulK9EmailIsAllowed() {
        val result = NotificationHistoryInterpreter().interpret(
            rawEvent(packageName = "com.fsck.k9", category = "email", title = "Inbox", text = "New mail")
        )

        assertEquals(NotificationHistoryInterpretationAction.CREATE, result.action)
        assertTrue(result.shouldWriteHistory)
        assertTrue(result.shouldSpeak)
    }

    @Test
    fun meaningfulYouTubeNotificationIsAllowed() {
        val result = NotificationHistoryInterpreter().interpret(
            rawEvent(packageName = "com.google.android.youtube", category = "social", title = "Channel", text = "New video")
        )

        assertEquals(NotificationHistoryInterpretationAction.CREATE, result.action)
        assertTrue(result.shouldWriteHistory)
        assertTrue(result.shouldSpeak)
    }

    @Test
    fun ordinaryNotificationWithUnknownCategoryIsAllowed() {
        val result = NotificationHistoryInterpreter().interpret(
            rawEvent(packageName = "com.example.app", category = "", title = "Notice", text = "Hello")
        )

        assertEquals(NotificationHistoryInterpretationAction.CREATE, result.action)
        assertTrue(result.shouldWriteHistory)
        assertTrue(result.shouldSpeak)
    }

    private fun rawEvent(
        receivedAt: Long = 1_000L,
        postedAt: Long = 900L,
        notificationKey: String = "notification-key",
        packageName: String = "com.example.mail",
        applicationName: String = "Mail",
        title: String = "Title",
        text: String = "Text",
        expandedText: String = "Expanded",
        subText: String = "Inbox",
        category: String = "email",
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

