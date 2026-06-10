package com.example.notificationreader.history

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification

object NotificationHistoryExtractor {
    fun rawEventFromStatusBarNotification(
        context: Context,
        sbn: StatusBarNotification,
        receivedAt: Long = System.currentTimeMillis()
    ): RawNotificationEvent? {
        if (sbn.packageName == context.packageName) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras
        val packageName = NotificationTextNormalizer.normalize(sbn.packageName)
        if (packageName.isEmpty()) return null

        return RawNotificationEvent(
            receivedAt = receivedAt,
            postedAt = sbn.postTime,
            notificationKey = NotificationTextNormalizer.normalize(sbn.key),
            packageName = packageName,
            applicationName = ApplicationNameResolver.resolve(context, packageName),
            title = NotificationTextNormalizer.normalize(extras.getCharSequence(Notification.EXTRA_TITLE)),
            text = NotificationTextNormalizer.normalize(extras.getCharSequence(Notification.EXTRA_TEXT)),
            expandedText = NotificationTextNormalizer.normalize(
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            ),
            subText = NotificationTextNormalizer.normalize(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
            category = NotificationTextNormalizer.normalize(notification.category),
            notificationId = sbn.id,
            tag = NotificationTextNormalizer.normalize(sbn.tag),
            groupKey = NotificationTextNormalizer.normalize(sbn.groupKey),
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            hasImageOrLargeIcon = hasImageOrLargeIcon(notification)
        )
    }

    fun fromStatusBarNotification(
        context: Context,
        sbn: StatusBarNotification,
        savedAt: Long = System.currentTimeMillis()
    ): NotificationHistoryRecord? {
        val rawEvent = rawEventFromStatusBarNotification(context, sbn, savedAt) ?: return null
        return NotificationHistoryInterpreter(duplicateWindowMillis = -1L)
            .interpret(rawEvent)
            .record
    }

    private fun hasImageOrLargeIcon(notification: Notification): Boolean {
        val extras = notification.extras
        return notification.largeIcon != null ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notification.getLargeIcon() != null) ||
            extras.containsKey(Notification.EXTRA_PICTURE) ||
            extras.containsKey(Notification.EXTRA_LARGE_ICON)
    }
}
