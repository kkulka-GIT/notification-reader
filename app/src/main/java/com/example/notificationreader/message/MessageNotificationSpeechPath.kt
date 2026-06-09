package com.example.notificationreader.message

import android.service.notification.StatusBarNotification

object MessageNotificationSpeechPath {
    fun messageFor(sbn: StatusBarNotification): String? {
        return try {
            val message = MessageNotificationNormalizer.fromStatusBarNotification(sbn) ?: return null
            MessageSpeechBuilder.build(message).takeIf { it.isNotBlank() }
        } catch (_: RuntimeException) {
            null
        }
    }
}
