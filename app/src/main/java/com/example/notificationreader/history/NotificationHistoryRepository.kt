package com.example.notificationreader.history

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.notificationreader.debug.DebugLogStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NotificationHistoryRepository private constructor(context: Context) {
    private val database = NotificationHistoryDatabase(context.applicationContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun saveAsync(record: NotificationHistoryRecord) {
        executor.execute {
            save(record)
        }
    }

    fun save(record: NotificationHistoryRecord): Boolean {
        val db = database.writableDatabase
        val inserted = db.insertWithOnConflict(
            TABLE,
            null,
            record.toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (inserted != -1L) {
            trimToLimit(db)
            DebugLogStore.add(record.notificationKey, "SAVED", historyLogMessage(record))
            return true
        }

        val existing = findByStorageKey(db, record.storageKey)
        return when (NotificationHistoryUpsertPolicy.actionFor(existing, record)) {
            NotificationHistoryUpsertAction.SAVED -> {
                DebugLogStore.add(record.notificationKey, "SAVED", historyLogMessage(record))
                true
            }
            NotificationHistoryUpsertAction.UPDATED -> {
                db.update(
                    TABLE,
                    record.toContentValues(),
                    "id = ?",
                    arrayOf(requireNotNull(existing).id.toString())
                )
                trimToLimit(db)
                DebugLogStore.add(record.notificationKey, "UPDATED", historyLogMessage(record))
                true
            }
            NotificationHistoryUpsertAction.UNCHANGED -> {
                DebugLogStore.add(record.notificationKey, "UNCHANGED", historyLogMessage(record))
                false
            }
        }
    }

    fun newest(limit: Int = NotificationHistoryLimitPolicy.MAX_RECORDS): List<NotificationHistoryRecord> {
        return queryRecords(
            selection = null,
            selectionArgs = null,
            orderBy = "posted_at DESC, saved_at DESC, id DESC",
            limit = limit.toString()
        )
    }

    fun byPackage(packageName: String): List<NotificationHistoryRecord> {
        return queryRecords(
            selection = "package_name = ?",
            selectionArgs = arrayOf(packageName),
            orderBy = "posted_at DESC, saved_at DESC, id DESC",
            limit = NotificationHistoryLimitPolicy.MAX_RECORDS.toString()
        )
    }

    fun chronological(): List<NotificationHistoryRecord> {
        return queryRecords(
            selection = null,
            selectionArgs = null,
            orderBy = "posted_at ASC, saved_at ASC, id ASC",
            limit = null
        )
    }

    fun applicationSummaries(): List<ApplicationHistorySummary> {
        val db = database.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT package_name, application_name, COUNT(*) AS notification_count, MAX(posted_at) AS newest_posted_at
            FROM $TABLE
            GROUP BY package_name
            ORDER BY newest_posted_at DESC
            """.trimIndent(),
            null
        )
        return cursor.use { c ->
            val summaries = mutableListOf<ApplicationHistorySummary>()
            while (c.moveToNext()) {
                summaries.add(
                    ApplicationHistorySummary(
                        packageName = c.getString(0),
                        applicationName = c.getString(1),
                        notificationCount = c.getInt(2),
                        newestPostedAt = c.getLong(3)
                    )
                )
            }
            summaries
        }
    }

    fun delete(id: Long) {
        database.writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    fun deleteByPackage(packageName: String) {
        database.writableDatabase.delete(TABLE, "package_name = ?", arrayOf(packageName))
    }

    fun clear() {
        database.writableDatabase.delete(TABLE, null, null)
    }

    fun close() {
        executor.shutdown()
        database.close()
    }

    private fun queryRecords(
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String,
        limit: String?
    ): List<NotificationHistoryRecord> {
        val db = database.readableDatabase
        val cursor = db.query(
            TABLE,
            RECORD_COLUMNS,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit
        )
        return cursor.use { c ->
            val records = mutableListOf<NotificationHistoryRecord>()
            while (c.moveToNext()) records.add(c.toRecord())
            records
        }
    }

    private fun findByStorageKey(db: SQLiteDatabase, storageKey: String): NotificationHistoryRecord? {
        val cursor = db.query(
            TABLE,
            RECORD_COLUMNS,
            "storage_key = ?",
            arrayOf(storageKey),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { c ->
            if (c.moveToNext()) c.toRecord() else null
        }
    }

    private fun historyLogMessage(record: NotificationHistoryRecord): String {
        return "${record.packageName} | ${record.title} | ${record.text} | storageKey=${record.storageKey}"
    }

    private fun trimToLimit(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM $TABLE
            WHERE id NOT IN (
                SELECT id FROM $TABLE
                ORDER BY posted_at DESC, saved_at DESC, id DESC
                LIMIT ${NotificationHistoryLimitPolicy.MAX_RECORDS}
            )
            """.trimIndent()
        )
    }

    private fun NotificationHistoryRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("notification_key", notificationKey)
            put("package_name", packageName)
            put("application_name", applicationName)
            put("title", title)
            put("text", text)
            put("expanded_text", expandedText)
            put("sub_text", subText)
            put("posted_at", postedAt)
            put("saved_at", savedAt)
            put("has_image_or_large_icon", if (hasImageOrLargeIcon) 1 else 0)
            put("storage_key", storageKey)
        }
    }

    private fun Cursor.toRecord(): NotificationHistoryRecord {
        return NotificationHistoryRecord(
            id = getLong(0),
            notificationKey = getString(1),
            packageName = getString(2),
            applicationName = getString(3),
            title = getString(4),
            text = getString(5),
            expandedText = getString(6),
            subText = getString(7),
            postedAt = getLong(8),
            savedAt = getLong(9),
            hasImageOrLargeIcon = getInt(10) == 1,
            storageKey = getString(11)
        )
    }

    companion object {
        private const val TABLE = "notifications"
        private val RECORD_COLUMNS = arrayOf(
            "id",
            "notification_key",
            "package_name",
            "application_name",
            "title",
            "text",
            "expanded_text",
            "sub_text",
            "posted_at",
            "saved_at",
            "has_image_or_large_icon",
            "storage_key"
        )

        @Volatile
        private var instance: NotificationHistoryRepository? = null

        fun getInstance(context: Context): NotificationHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationHistoryRepository(context).also { instance = it }
            }
        }
    }
}
