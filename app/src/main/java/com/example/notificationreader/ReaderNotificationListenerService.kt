package com.example.notificationreader

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import com.example.notificationreader.debug.DebugLogStore
import com.example.notificationreader.history.NotificationHistoryExtractor
import com.example.notificationreader.history.NotificationHistoryRepository
import com.example.notificationreader.history.NotificationHistoryRecord
import com.example.notificationreader.message.MessageNotificationSpeechPath
import java.util.ArrayDeque
import java.util.Locale

class ReaderNotificationListenerService : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingMessages = ArrayDeque<String>()
    private val speechDuplicatePolicy = NotificationSpeechDuplicatePolicy()
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
            DebugLogStore.add(sbn.key, "EXTRACTED", historyMessage(historyRecord))
            historyRepository.saveAsync(historyRecord)
        } else {
            DebugLogStore.add(sbn.key, "SKIPPED", "Empty notification history record")
        }

        if (!NotificationSpeechPrefs.isSpeakingEnabled(applicationContext)) return
        val speechFingerprint = NotificationSpeechFingerprint.fromStatusBarNotification(sbn)
        when (speechDuplicatePolicy.evaluate(sbn.key, speechFingerprint)) {
            NotificationSpeechDuplicateDecision.Allow -> Unit
            NotificationSpeechDuplicateDecision.DuplicateNotificationKey -> {
                DebugLogStore.add(
                    sbn.key,
                    "SKIPPED",
                    "Duplicate speech notification key | notificationKey=${sbn.key} | speechFingerprint=" + speechFingerprint
                )
                return
            }
            NotificationSpeechDuplicateDecision.DuplicateFingerprintWithinWindow -> {
                DebugLogStore.add(
                    sbn.key,
                    "SKIPPED",
                    "Duplicate speech fingerprint within 5 seconds | notificationKey=${sbn.key} | speechFingerprint=" + speechFingerprint
                )
                return
            }
        }

        val speechMessage = MessageNotificationSpeechPath.messageFor(sbn) ?: NotificationAnnouncementMapper.messageFor(sbn)
        DebugLogStore.add(
            sbn.key,
            "SPOKEN",
            speechMessage + " | notificationKey=${sbn.key} | speechFingerprint=" + speechFingerprint
        )
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

    private fun historyMessage(record: NotificationHistoryRecord): String {
        return "${record.title} | ${record.text} | storageKey=${record.storageKey}"
    }

    companion object {
        private const val MAX_PENDING_MESSAGES = 8
    }
}
