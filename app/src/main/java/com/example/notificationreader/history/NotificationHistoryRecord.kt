package com.example.notificationreader.history

data class NotificationHistoryRecord(
    val id: Long = 0L,
    val notificationKey: String,
    val packageName: String,
    val applicationName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val subText: String,
    val postedAt: Long,
    val savedAt: Long,
    val hasImageOrLargeIcon: Boolean,
    val storageKey: String
)

data class ApplicationHistorySummary(
    val packageName: String,
    val applicationName: String,
    val notificationCount: Int,
    val newestPostedAt: Long
)
