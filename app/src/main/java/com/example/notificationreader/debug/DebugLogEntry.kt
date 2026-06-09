package com.example.notificationreader.debug

data class DebugLogEntry(
    val timestamp: Long,
    val notificationKey: String?,
    val stage: String,
    val message: String
)
