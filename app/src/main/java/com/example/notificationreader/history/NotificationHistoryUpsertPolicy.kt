package com.example.notificationreader.history

enum class NotificationHistoryUpsertAction {
    SAVED,
    UPDATED,
    UNCHANGED
}

object NotificationHistoryUpsertPolicy {
    fun actionFor(
        existing: NotificationHistoryRecord?,
        incoming: NotificationHistoryRecord
    ): NotificationHistoryUpsertAction {
        if (existing == null) return NotificationHistoryUpsertAction.SAVED
        return if (hasRelevantContentChange(existing, incoming)) {
            NotificationHistoryUpsertAction.UPDATED
        } else {
            NotificationHistoryUpsertAction.UNCHANGED
        }
    }

    private fun hasRelevantContentChange(
        existing: NotificationHistoryRecord,
        incoming: NotificationHistoryRecord
    ): Boolean {
        return existing.title != incoming.title ||
            existing.text != incoming.text ||
            existing.expandedText != incoming.expandedText ||
            existing.subText != incoming.subText ||
            existing.hasImageOrLargeIcon != incoming.hasImageOrLargeIcon
    }
}
