package com.example.notificationreader.message

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification

object MessageNotificationNormalizer {
    fun fromStatusBarNotification(sbn: StatusBarNotification): SemanticMessageNotification? {
        return try {
            if (!MessageNotificationDetector.isMessageCandidate(sbn)) return null

            val notification = sbn.notification ?: return null
            val structuredMessage = newestStructuredMessage(sbn.packageName, notification)
            val fallbackMessage = fallbackMessage(sbn.packageName, notification)
            val message = if (structuredMessage?.sender != null && structuredMessage.content != null) {
                structuredMessage
            } else {
                fallbackMessage
            }

            SemanticMessageNotification(
                packageName = MessageSpeechBuilder.clean(message.packageName).orEmpty(),
                sender = MessageSpeechBuilder.clean(message.sender),
                content = MessageSpeechBuilder.clean(message.content)
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun newestStructuredMessage(packageName: String, notification: Notification): SemanticMessageNotification? {
        val messages = notification.extras
            ?.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
            ?: return null

        val newestMessage = messages.lastOrNull() ?: return null
        return SemanticMessageNotification(
            packageName = packageName,
            sender = senderName(newestMessage),
            content = MessageSpeechBuilder.clean(newestMessage.text)
        )
    }

    private fun senderName(message: Notification.MessagingStyle.Message): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            MessageSpeechBuilder.clean(message.senderPerson?.name ?: message.sender)
        } else {
            MessageSpeechBuilder.clean(message.sender)
        }
    }

    private fun fallbackMessage(packageName: String, notification: Notification): SemanticMessageNotification {
        val extras = notification.extras
        return SemanticMessageNotification(
            packageName = packageName,
            sender = MessageSpeechBuilder.clean(extras?.getCharSequence(Notification.EXTRA_TITLE)),
            content = MessageSpeechBuilder.clean(extras?.getCharSequence(Notification.EXTRA_TEXT))
        )
    }
}
