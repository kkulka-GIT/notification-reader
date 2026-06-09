package com.example.notificationreader

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import com.example.notificationreader.debug.DebugLogStore
import com.example.notificationreader.history.NotificationHistoryDuplicatePolicy
import com.example.notificationreader.history.NotificationHistoryExtractor
import com.example.notificationreader.history.NotificationHistoryRepository
import com.example.notificationreader.message.MessageNotificationSpeechPath
import java.util.ArrayDeque
import java.util.Locale

class ReaderNotificationListenerService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingMessages = ArrayDeque<String>()
    private val recentNotificationKeys = ArrayDeque<String>()
    private val recentNotificationKeySet = LinkedHashSet<String>()
    private val historyDuplicatePolicy = NotificationHistoryDuplicatePolicy()
    private lateinit var historyRepository: NotificationHistoryRepository

    override fun onCreate() {
        super.onCreate()
        historyRepository = NotificationHistoryRepository.getInstance(applicationContext)
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
        if (sbn.packageName == packageName) return
        DebugLogStore.add(sbn.key, "RECEIVED", receivedMessage(sbn))

        val historyRecord = NotificationHistoryExtractor.fromStatusBarNotification(applicationContext, sbn)
        if (historyRecord != null) {
            DebugLogStore.add(sbn.key, "EXTRACTED", historyMessage(historyRecord.title, historyRecord.text))
            if (historyDuplicatePolicy.shouldStore(historyRecord.storageKey)) {
                historyRepository.saveAsync(historyRecord)
            } else {
                DebugLogStore.add(sbn.key, "SKIPPED", "Duplicate history record")
            }
        } else {
            DebugLogStore.add(sbn.key, "SKIPPED", "Empty notification history record")
        }

        if (!NotificationSpeechPrefs.isSpeakingEnabled(applicationContext)) return
        if (isDuplicate(sbn.key)) {
            DebugLogStore.add(sbn.key, "SKIPPED", "Duplicate speech notification")
            return
        }

        val speechMessage = MessageNotificationSpeechPath.messageFor(sbn) ?: NotificationAnnouncementMapper.messageFor(sbn)
        DebugLogStore.add(sbn.key, "SPOKEN", speechMessage)
        speak(speechMessage)
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

    private fun receivedMessage(sbn: StatusBarNotification): String {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        return "${sbn.packageName} | $title | $text"
    }

    private fun historyMessage(title: String, text: String): String {
        return "$title | $text"
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
