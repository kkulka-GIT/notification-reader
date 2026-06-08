package com.example.notificationreader

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.ArrayDeque
import java.util.Locale

class ReaderNotificationListenerService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingMessages = ArrayDeque<String>()
    private val recentNotificationKeys = ArrayDeque<String>()
    private val recentNotificationKeySet = LinkedHashSet<String>()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val polishStatus = tts?.setLanguage(Locale("pl", "PL"))
            ttsReady = polishStatus != TextToSpeech.LANG_MISSING_DATA &&
                polishStatus != TextToSpeech.LANG_NOT_SUPPORTED
            flushPendingMessages()
        } else {
            ttsReady = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NotificationSpeechPrefs.isSpeakingEnabled(applicationContext)) return
        if (isDuplicate(sbn.key)) return

        speak(NotificationAnnouncementMapper.messageFor(sbn))
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingMessages.clear()
        super.onDestroy()
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            pendingMessages.addLast(message)
            trimPendingMessages()
            return
        }

        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "notification-${System.nanoTime()}")
    }

    private fun flushPendingMessages() {
        while (ttsReady && pendingMessages.isNotEmpty()) {
            speak(pendingMessages.removeFirst())
        }
    }

    private fun trimPendingMessages() {
        while (pendingMessages.size > MAX_PENDING_MESSAGES) {
            pendingMessages.removeFirst()
        }
    }

    private fun isDuplicate(key: String): Boolean {
        if (!recentNotificationKeySet.add(key)) return true

        recentNotificationKeys.addLast(key)
        while (recentNotificationKeys.size > MAX_RECENT_NOTIFICATIONS) {
            recentNotificationKeySet.remove(recentNotificationKeys.removeFirst())
        }
        return false
    }

    companion object {
        private const val MAX_PENDING_MESSAGES = 8
        private const val MAX_RECENT_NOTIFICATIONS = 32
    }
}
