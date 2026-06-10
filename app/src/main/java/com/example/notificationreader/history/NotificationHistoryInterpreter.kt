package com.example.notificationreader.history

import java.util.LinkedHashMap

enum class NotificationHistoryInterpretationAction {
    IGNORE,
    CREATE,
    UPDATE
}

data class NotificationHistoryInterpretation(
    val action: NotificationHistoryInterpretationAction,
    val reason: String,
    val explanation: String,
    val record: NotificationHistoryRecord? = null
) {
    val shouldWriteHistory: Boolean
        get() = action != NotificationHistoryInterpretationAction.IGNORE && record != null

    val shouldSpeak: Boolean
        get() = action != NotificationHistoryInterpretationAction.IGNORE
}

class NotificationHistoryInterpreter(
    private val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS
) {
    private val recentMeaningfulEvents = LinkedHashMap<String, Long>()

    @Synchronized
    fun interpret(event: RawNotificationEvent): NotificationHistoryInterpretation {
        if (event.packageName == SYSTEM_UI_PACKAGE_NAME) {
            return ignored(
                reason = "IGNORE_SYSTEM",
                explanation = "Android SystemUI notification is technical and excluded from user outputs"
            )
        }

        if (event.category == SERVICE_CATEGORY) {
            return ignored(
                reason = "IGNORE_SERVICE",
                explanation = "Android service notification is technical and excluded from user outputs"
            )
        }

        if (event.title.isEmpty() && event.text.isEmpty()) {
            return ignored(
                reason = "IGNORE_EMPTY",
                explanation = "Notification has no title or text to present to the user"
            )
        }

        val storageKey = NotificationHistoryStorageKey.from(event)
        trimOldEvents(event.receivedAt)
        val lastSeenAt = recentMeaningfulEvents[storageKey]
        recentMeaningfulEvents[storageKey] = event.receivedAt

        val record = event.toHistoryRecord(storageKey)
        if (lastSeenAt != null && event.receivedAt - lastSeenAt <= duplicateWindowMillis) {
            return NotificationHistoryInterpretation(
                action = NotificationHistoryInterpretationAction.UPDATE,
                reason = "DUPLICATE_WITHIN_WINDOW",
                explanation = "Repeated meaningful notification updates the existing history record",
                record = record
            )
        }

        return NotificationHistoryInterpretation(
            action = NotificationHistoryInterpretationAction.CREATE,
            reason = "MEANINGFUL_NOTIFICATION",
            explanation = "Meaningful notification is allowed for user outputs",
            record = record
        )
    }

    private fun ignored(reason: String, explanation: String): NotificationHistoryInterpretation {
        return NotificationHistoryInterpretation(
            action = NotificationHistoryInterpretationAction.IGNORE,
            reason = reason,
            explanation = explanation
        )
    }

    private fun trimOldEvents(nowMillis: Long) {
        val iterator = recentMeaningfulEvents.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > duplicateWindowMillis) {
                iterator.remove()
            }
        }
    }

    private fun RawNotificationEvent.toHistoryRecord(storageKey: String): NotificationHistoryRecord {
        return NotificationHistoryRecord(
            notificationKey = notificationKey,
            packageName = packageName,
            applicationName = applicationName,
            title = title,
            text = text,
            expandedText = expandedText,
            subText = subText,
            postedAt = postedAt,
            savedAt = receivedAt,
            hasImageOrLargeIcon = hasImageOrLargeIcon,
            storageKey = storageKey
        )
    }

    companion object {
        const val DEFAULT_DUPLICATE_WINDOW_MILLIS = 5_000L
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"
        private const val SERVICE_CATEGORY = "service"
    }
}

