package com.example.notificationreader.history

data class RawNotificationEvent(
    val receivedAt: Long,
    val postedAt: Long,
    val notificationKey: String,
    val packageName: String,
    val applicationName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val subText: String,
    val category: String,
    val notificationId: Int,
    val tag: String,
    val groupKey: String,
    val isGroupSummary: Boolean,
    val hasImageOrLargeIcon: Boolean
)

