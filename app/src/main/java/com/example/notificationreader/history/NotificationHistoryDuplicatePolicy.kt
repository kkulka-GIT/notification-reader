package com.example.notificationreader.history

import java.util.ArrayDeque
import java.util.LinkedHashSet

data class NotificationHistoryCandidate(
    val packageName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val subText: String,
    val postedAt: Long
)

object NotificationHistoryStorageKey {
    fun from(candidate: NotificationHistoryCandidate): String {
        return listOf(
            "event",
            candidate.packageName,
            candidate.postedAt.toString(),
            candidate.title,
            candidate.text,
            candidate.expandedText,
            candidate.subText
        ).joinToString("|")
    }

    fun from(event: RawNotificationEvent): String {
        return from(
            NotificationHistoryCandidate(
                packageName = event.packageName,
                title = event.title,
                text = event.text,
                expandedText = event.expandedText,
                subText = event.subText,
                postedAt = event.postedAt
            )
        )
    }
}

class NotificationHistoryDuplicatePolicy(
    private val maxRecentEntries: Int = 64
) {
    private val recentStorageKeys = ArrayDeque<String>()
    private val recentStorageKeySet = LinkedHashSet<String>()

    @Synchronized
    fun shouldStore(storageKey: String): Boolean {
        if (storageKey.isEmpty()) return false
        if (!recentStorageKeySet.add(storageKey)) return false

        recentStorageKeys.addLast(storageKey)
        while (recentStorageKeys.size > maxRecentEntries) {
            recentStorageKeySet.remove(recentStorageKeys.removeFirst())
        }
        return true
    }
}
