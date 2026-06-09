package com.example.notificationreader.history

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification

object NotificationHistoryExtractor {
    fun fromStatusBarNotification(
        context: Context,
        sbn: StatusBarNotification,
        savedAt: Long = System.currentTimeMillis()
    ): NotificationHistoryRecord? {
        if (sbn.packageName == context.packageName) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras
        val packageName = NotificationTextNormalizer.normalize(sbn.packageName)
        if (packageName.isEmpty()) return null

        val title = NotificationTextNormalizer.normalize(extras.getCharSequence(Notification.EXTRA_TITLE))
        val text = NotificationTextNormalizer.normalize(extras.getCharSequence(Notification.EXTRA_TEXT))
        val expandedText = NotificationTextNormalizer.normalize(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
        )
        val subText = NotificationTextNormalizer.normalize(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        val notificationKey = NotificationTextNormalizer.normalize(sbn.key)
        val candidate = NotificationHistoryCandidate(
            notificationKey = notificationKey,
            packageName = packageName,
            title = title,
            text = text,
            expandedText = expandedText,
            subText = subText,
            postedAt = sbn.postTime
        )

        return NotificationHistoryRecord(
            notificationKey = notificationKey,
            packageName = packageName,
            applicationName = ApplicationNameResolver.resolve(context, packageName),
            title = title,
            text = text,
            expandedText = expandedText,
            subText = subText,
            postedAt = sbn.postTime,
            savedAt = savedAt,
            hasImageOrLargeIcon = hasImageOrLargeIcon(notification),
            storageKey = NotificationHistoryStorageKey.from(candidate)
        )
    }

    private fun hasImageOrLargeIcon(notification: Notification): Boolean {
        val extras = notification.extras
        return notification.largeIcon != null ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notification.getLargeIcon() != null) ||
            extras.containsKey(Notification.EXTRA_PICTURE) ||
            extras.containsKey(Notification.EXTRA_LARGE_ICON)
    }
}
