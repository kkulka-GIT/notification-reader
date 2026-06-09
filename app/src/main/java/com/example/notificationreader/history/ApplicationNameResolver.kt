package com.example.notificationreader.history

import android.content.Context
import android.content.pm.PackageManager

object ApplicationNameResolver {
    fun resolve(context: Context, packageName: String): String {
        val fallback = fallbackName(null, packageName)
        return try {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            fallbackName(packageManager.getApplicationLabel(info), packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            fallback
        } catch (_: RuntimeException) {
            fallback
        }
    }

    fun fallbackName(label: CharSequence?, packageName: String): String {
        val normalizedLabel = NotificationTextNormalizer.normalize(label)
        if (normalizedLabel.isNotEmpty()) return normalizedLabel

        val normalizedPackage = NotificationTextNormalizer.normalize(packageName)
        return normalizedPackage.ifEmpty { "Nieznana aplikacja" }
    }
}
