package com.example.notificationreader.history

import java.util.ArrayDeque
import java.util.LinkedHashSet

data class NotificationHistoryCandidate(
    val notificationKey: String,
    val packageName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val subText: String,
    val postedAt: Long
)

object NotificationHistoryStorageKey {
    fun from(candidate: NotificationHistoryCandidate): String {
        return if (candidate.notificationKey.isNotEmpty()) {
            "key:${candidate.notificationKey}"
        } else {
            listOf(
                "fallback",
                candidate.packageName,
                candidate.postedAt.toString(),
                candidate.title,
                candidate.text,
                candidate.expandedText,
                candidate.subText
            ).joinToString("|")
        }
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
