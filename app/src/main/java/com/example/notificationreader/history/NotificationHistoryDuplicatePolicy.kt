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

data class NotificationHistoryContentFingerprint(
    val packageName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val subText: String
) {
    fun safeHash(): String {
        return toString().hashCode().toUInt().toString(16)
    }

    override fun toString(): String {
        return listOf(packageName, title, text, expandedText, subText).joinToString("|")
    }

    companion object {
        fun from(event: RawNotificationEvent): NotificationHistoryContentFingerprint {
            return NotificationHistoryContentFingerprint(
                packageName = NotificationTextNormalizer.normalize(event.packageName),
                title = NotificationTextNormalizer.normalize(event.title),
                text = NotificationTextNormalizer.normalize(event.text),
                expandedText = NotificationTextNormalizer.normalize(event.expandedText),
                subText = NotificationTextNormalizer.normalize(event.subText)
            )
        }

        fun from(record: NotificationHistoryRecord): NotificationHistoryContentFingerprint {
            return NotificationHistoryContentFingerprint(
                packageName = NotificationTextNormalizer.normalize(record.packageName),
                title = NotificationTextNormalizer.normalize(record.title),
                text = NotificationTextNormalizer.normalize(record.text),
                expandedText = NotificationTextNormalizer.normalize(record.expandedText),
                subText = NotificationTextNormalizer.normalize(record.subText)
            )
        }
    }
}

data class NotificationHistoryContentDuplicateMatch(
    val fingerprint: NotificationHistoryContentFingerprint,
    val ageMillis: Long
)

class NotificationHistoryContentDuplicatePolicy(
    private val duplicateWindowMillis: Long = DEFAULT_DUPLICATE_CONTENT_WINDOW_MILLIS
) {
    private val recentFingerprints = LinkedHashMap<NotificationHistoryContentFingerprint, Long>()

    @Synchronized
    fun evaluate(event: RawNotificationEvent): NotificationHistoryContentDuplicateMatch? {
        if (duplicateWindowMillis < 0L) return null

        val fingerprint = NotificationHistoryContentFingerprint.from(event)
        trimOldFingerprints(event.receivedAt)
        val firstSeenAt = recentFingerprints[fingerprint]
        if (firstSeenAt != null) {
            val ageMillis = event.receivedAt - firstSeenAt
            if (ageMillis in 0..duplicateWindowMillis) {
                return NotificationHistoryContentDuplicateMatch(
                    fingerprint = fingerprint,
                    ageMillis = ageMillis
                )
            }
        }

        recentFingerprints[fingerprint] = event.receivedAt
        return null
    }

    private fun trimOldFingerprints(nowMillis: Long) {
        val iterator = recentFingerprints.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > duplicateWindowMillis) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_DUPLICATE_CONTENT_WINDOW_MILLIS = 15 * 60 * 1_000L
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
