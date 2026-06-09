package com.example.notificationreader.history

object NotificationTextNormalizer {
    fun normalize(value: CharSequence?): String {
        return value
            ?.toString()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }
}
