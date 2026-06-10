package com.example.notificationreader

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.notificationreader.history.NotificationTextNormalizer
import java.util.ArrayDeque
import java.util.LinkedHashSet

data class NotificationSpeechFingerprint(
    val packageName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val subText: String
) {
    override fun toString(): String {
        return listOf(packageName, title, text, expandedText, subText).joinToString("|")
    }

    companion object {
        fun fromStatusBarNotification(sbn: StatusBarNotification): NotificationSpeechFingerprint {
            val extras = sbn.notification?.extras
            return NotificationSpeechFingerprint(
                packageName = NotificationTextNormalizer.normalize(sbn.packageName),
                title = NotificationTextNormalizer.normalize(extras?.getCharSequence(Notification.EXTRA_TITLE)),
                text = NotificationTextNormalizer.normalize(extras?.getCharSequence(Notification.EXTRA_TEXT)),
                expandedText = NotificationTextNormalizer.normalize(
                    extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?: extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
                ),
                subText = NotificationTextNormalizer.normalize(extras?.getCharSequence(Notification.EXTRA_SUB_TEXT))
            )
        }
    }
}

sealed class NotificationSpeechDuplicateDecision {
    object Allow : NotificationSpeechDuplicateDecision()
    object DuplicateNotificationKey : NotificationSpeechDuplicateDecision()
    object DuplicateFingerprintWithinWindow : NotificationSpeechDuplicateDecision()
}

class NotificationSpeechDuplicatePolicy(
    private val maxRecentNotificationKeys: Int = 32,
    private val duplicateFingerprintWindowMillis: Long = DEFAULT_DUPLICATE_FINGERPRINT_WINDOW_MILLIS
) {
    private val recentNotificationKeys = ArrayDeque<String>()
    private val recentNotificationKeySet = LinkedHashSet<String>()
    private val recentFingerprints = LinkedHashMap<String, Long>()

    @Synchronized
    fun evaluate(
        notificationKey: String,
        speechFingerprint: NotificationSpeechFingerprint,
        nowMillis: Long = System.currentTimeMillis()
    ): NotificationSpeechDuplicateDecision {
        if (!recentNotificationKeySet.add(notificationKey)) {
            return NotificationSpeechDuplicateDecision.DuplicateNotificationKey
        }

        recentNotificationKeys.addLast(notificationKey)
        while (recentNotificationKeys.size > maxRecentNotificationKeys) {
            recentNotificationKeySet.remove(recentNotificationKeys.removeFirst())
        }

        val fingerprintKey = speechFingerprint.toString()
        trimOldFingerprints(nowMillis)
        val lastSpokenAt = recentFingerprints[fingerprintKey]
        if (lastSpokenAt != null && nowMillis - lastSpokenAt <= duplicateFingerprintWindowMillis) {
            return NotificationSpeechDuplicateDecision.DuplicateFingerprintWithinWindow
        }

        recentFingerprints[fingerprintKey] = nowMillis
        return NotificationSpeechDuplicateDecision.Allow
    }

    private fun trimOldFingerprints(nowMillis: Long) {
        val iterator = recentFingerprints.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > duplicateFingerprintWindowMillis) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_DUPLICATE_FINGERPRINT_WINDOW_MILLIS = 5_000L
    }
}
