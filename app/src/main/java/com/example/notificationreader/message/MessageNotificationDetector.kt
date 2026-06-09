package com.example.notificationreader.message

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.notificationreader.NotificationAnnouncementMapper

object MessageNotificationDetector {
    fun isMessageCandidate(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        return notification.category == Notification.CATEGORY_MESSAGE ||
            notification.extras?.containsKey(Notification.EXTRA_MESSAGES) == true ||
            NotificationAnnouncementMapper.isSupportedMessagePackage(sbn.packageName)
    }
}
