package com.example.notificationreader.message

data class SemanticMessageNotification(
    val packageName: String,
    val sender: String?,
    val content: String?
)
