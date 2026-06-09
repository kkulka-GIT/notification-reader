package com.example.notificationreader.debug

object DebugLogStore {
    private const val MAX_ENTRIES = 100
    private val entries = mutableListOf<DebugLogEntry>()

    @Synchronized
    fun add(notificationKey: String?, stage: String, message: String) {
        entries.add(
            DebugLogEntry(
                timestamp = System.currentTimeMillis(),
                notificationKey = notificationKey,
                stage = stage,
                message = message
            )
        )
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    @Synchronized
    fun forNotification(notificationKey: String): List<DebugLogEntry> {
        return entries.filter { it.notificationKey == notificationKey }
    }
}
