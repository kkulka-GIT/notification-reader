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
    val record: NotificationHistoryRecord? = null
)

class NotificationHistoryInterpreter(
    private val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS
) {
    private val recentMeaningfulEvents = LinkedHashMap<String, Long>()

    @Synchronized
    fun interpret(event: RawNotificationEvent): NotificationHistoryInterpretation {
        if (event.title.isEmpty() && event.text.isEmpty()) {
            return NotificationHistoryInterpretation(
                action = NotificationHistoryInterpretationAction.IGNORE,
                reason = "IGNORE_EMPTY"
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
                record = record
            )
        }

        return NotificationHistoryInterpretation(
            action = NotificationHistoryInterpretationAction.CREATE,
            reason = "MEANINGFUL_NOTIFICATION",
            record = record
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
    }
}

