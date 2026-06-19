package com.example.notificationreader.message

import android.service.notification.StatusBarNotification

data class MessageSpeechResult(
    val text: String,
    val variant: MessageSpeechVariant
)

object MessageNotificationSpeechPath {
    fun messageFor(sbn: StatusBarNotification): String? {
        return resultFor(sbn)?.text
    }

    fun resultFor(sbn: StatusBarNotification): MessageSpeechResult? {
        return try {
            val message = MessageNotificationNormalizer.fromStatusBarNotification(sbn) ?: return null
            val text = MessageSpeechBuilder.build(message).takeIf { it.isNotBlank() } ?: return null
            MessageSpeechResult(
                text = text,
                variant = MessageSpeechBuilder.variantFor(message)
            )
        } catch (_: RuntimeException) {
            null
        }
    }
}
