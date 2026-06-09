package com.example.notificationreader.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotificationHistoryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                notification_key TEXT NOT NULL,
                package_name TEXT NOT NULL,
                application_name TEXT NOT NULL,
                title TEXT NOT NULL,
                text TEXT NOT NULL,
                expanded_text TEXT NOT NULL,
                sub_text TEXT NOT NULL,
                posted_at INTEGER NOT NULL,
                saved_at INTEGER NOT NULL,
                has_image_or_large_icon INTEGER NOT NULL,
                storage_key TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notifications_posted_at ON notifications(posted_at DESC, saved_at DESC, id DESC)")
        db.execSQL("CREATE INDEX idx_notifications_package ON notifications(package_name, posted_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "notification_history.db"
        private const val DATABASE_VERSION = 1
    }
}
