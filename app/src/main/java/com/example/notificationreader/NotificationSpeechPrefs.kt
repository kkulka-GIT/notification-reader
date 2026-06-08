package com.example.notificationreader

import android.content.Context

object NotificationSpeechPrefs {
    private const val PREFS_NAME = "notification_speech"
    private const val KEY_SPEAKING_ENABLED = "speaking_enabled"

    fun isSpeakingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SPEAKING_ENABLED, true)
    }

    fun setSpeakingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SPEAKING_ENABLED, enabled)
            .apply()
    }
}
