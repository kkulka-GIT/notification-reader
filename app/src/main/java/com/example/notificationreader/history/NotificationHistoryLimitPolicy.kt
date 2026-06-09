package com.example.notificationreader.history

object NotificationHistoryLimitPolicy {
    const val MAX_RECORDS = 1000

    fun idsToDelete(newestFirstIds: List<Long>, maxRecords: Int = MAX_RECORDS): List<Long> {
        if (newestFirstIds.size <= maxRecords) return emptyList()
        return newestFirstIds.drop(maxRecords)
    }
}
