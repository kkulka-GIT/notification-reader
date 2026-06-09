package com.example.notificationreader

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.Locale

object NotificationAnnouncementMapper {
    private val messengerPackages = setOf(
        "com.facebook.orca",
        "com.facebook.mlite"
    )

    private val smsPackages = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms"
    )

    private val emailPackages = setOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "com.yahoo.mobile.client.android.mail"
    )

    fun messageFor(notification: StatusBarNotification): String {
        val packageName = notification.packageName.lowercase(Locale.US)
        val category = notification.notification.category

        return when {
            packageName in messengerPackages -> "Nowe powiadomienie z Messengera"
            category == Notification.CATEGORY_MESSAGE && packageName in smsPackages -> "Nowy SMS"
            packageName in emailPackages || category == Notification.CATEGORY_EMAIL -> "Nowa wiadomosc e-mail"
            else -> "Nowe powiadomienie z aplikacji"
        }
    }

    fun isSupportedMessagePackage(packageName: String): Boolean {
        val normalizedPackageName = packageName.lowercase(Locale.US)
        return normalizedPackageName in messengerPackages || normalizedPackageName in smsPackages
    }
}
