package com.example.notificationreader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.example.notificationreader.debug.DebugLogEntry
import com.example.notificationreader.debug.DebugLogStore
import com.example.notificationreader.history.ApplicationHistorySummary
import com.example.notificationreader.history.NotificationHistoryRecord
import com.example.notificationreader.history.NotificationHistoryRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var repository: NotificationHistoryRepository
    private lateinit var statusView: TextView
    private lateinit var sectionTitleView: TextView
    private lateinit var contentLayout: LinearLayout
    private lateinit var recentButton: Button
    private lateinit var appsButton: Button
    private lateinit var settingsButton: Button
    private var currentSection = Section.RECENT
    private var selectedPackageName: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val dateTimeFormat by lazy {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("pl", "PL"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NotificationHistoryRepository.getInstance(applicationContext)
        tts = TextToSpeech(applicationContext, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(this).apply {
            text = "Czytnik powiadomień"
            textSize = 26f
            setTextColor(getColor(android.R.color.black))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(titleView, fullWidthParams())

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 18)
        }
        recentButton = navigationButton("Ostatnie") { showRecent() }
        appsButton = navigationButton("Aplikacje") { showApplications() }
        settingsButton = navigationButton("Ustawienia") { showSettings() }
        navigation.addView(recentButton, weightedParams())
        navigation.addView(appsButton, weightedParams())
        navigation.addView(settingsButton, weightedParams())
        root.addView(navigation, fullWidthParams())

        sectionTitleView = TextView(this).apply {
            textSize = 22f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 0, 0, 12)
        }
        root.addView(sectionTitleView, fullWidthParams())

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
        }
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(contentLayout, fullWidthParams())
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
        showRecent()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentSection()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val polishStatus = tts?.setLanguage(Locale("pl", "PL"))
            ttsReady = polishStatus != TextToSpeech.LANG_MISSING_DATA &&
                polishStatus != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            ttsReady = false
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun showRecent() {
        currentSection = Section.RECENT
        selectedPackageName = null
        sectionTitleView.text = "Ostatnie"
        updateNavigationState()
        contentLayout.removeAllViews()

        val records = repository.newest()
        if (records.isEmpty()) {
            addEmptyHistoryMessage()
            return
        }
        records.forEach { record -> addRecordRow(record) }
    }

    private fun showApplications() {
        currentSection = Section.APPLICATIONS
        selectedPackageName = null
        sectionTitleView.text = "Aplikacje"
        updateNavigationState()
        contentLayout.removeAllViews()

        val summaries = repository.applicationSummaries()
        if (summaries.isEmpty()) {
            addEmptyHistoryMessage()
            return
        }
        summaries.forEach { summary -> addApplicationRow(summary) }
    }

    private fun showApplicationDetails(packageName: String, applicationName: String) {
        currentSection = Section.APPLICATION_DETAILS
        selectedPackageName = packageName
        sectionTitleView.text = applicationName
        updateNavigationState()
        contentLayout.removeAllViews()

        addActionButton("Wróć do aplikacji", "Wróć do listy aplikacji") { showApplications() }
        addActionButton(
            "Usuń historię tej aplikacji",
            "Usuń historię aplikacji $applicationName"
        ) {
            confirm(
                title = "Usunąć historię tej aplikacji?",
                message = "Zostaną usunięte zapisane powiadomienia aplikacji $applicationName."
            ) {
                repository.deleteByPackage(packageName)
                showApplications()
            }
        }

        val records = repository.byPackage(packageName)
        if (records.isEmpty()) {
            addEmptyHistoryMessage()
            return
        }
        records.forEach { record -> addRecordRow(record) }
    }

    private fun showSettings() {
        currentSection = Section.SETTINGS
        selectedPackageName = null
        sectionTitleView.text = "Ustawienia"
        updateNavigationState()
        contentLayout.removeAllViews()

        statusView = TextView(this).apply {
            textSize = 18f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 8, 0, 20)
        }
        contentLayout.addView(statusView, fullWidthParams())
        updateNotificationAccessStatus()

        addActionButton(
            "Dostęp do powiadomień",
            "Otwórz ustawienia dostępu do powiadomień"
        ) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val speakingSwitch = Switch(this).apply {
            text = "Odczyt głosowy"
            textSize = 20f
            minHeight = dp(56)
            isChecked = NotificationSpeechPrefs.isSpeakingEnabled(this@MainActivity)
            contentDescription = "Odczyt głosowy powiadomień"
            setOnCheckedChangeListener { _, isChecked ->
                NotificationSpeechPrefs.setSpeakingEnabled(this@MainActivity, isChecked)
            }
        }
        contentLayout.addView(speakingSwitch, fullWidthParams())

        addActionButton("Usuń całą historię", "Usuń całą historię powiadomień") {
            confirm(
                title = "Usunąć całą historię?",
                message = "Zostaną usunięte wszystkie zapisane powiadomienia."
            ) {
                repository.clear()
                showSettings()
            }
        }
    }

    private fun addRecordRow(record: NotificationHistoryRecord) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 16)
            minimumHeight = dp(72)
        }

        val summary = TextView(this).apply {
            textSize = 19f
            setTextColor(getColor(android.R.color.black))
            text = buildRecordSummary(record)
            contentDescription = buildRecordSummary(record)
        }
        container.addView(summary, fullWidthParams())

        val openButton = Button(this).apply {
            text = "Szczegóły"
            textSize = 18f
            minHeight = dp(56)
            isAllCaps = false
            contentDescription = "Pokaż szczegóły powiadomienia z aplikacji ${record.applicationName}"
            setOnClickListener { showRecordDetails(record) }
        }
        container.addView(openButton, fullWidthParams())
        addDivider(container)
        contentLayout.addView(container, fullWidthParams())
    }

    private fun showRecordDetails(record: NotificationHistoryRecord) {
        contentLayout.removeAllViews()
        sectionTitleView.text = "Szczegóły powiadomienia"

        addActionButton("Wróć", "Wróć do poprzedniej listy") { refreshCurrentSection() }
        addDetailField("Aplikacja", record.applicationName)
        addDetailField("Pakiet", record.packageName)
        addDetailField("Klucz powiadomienia", record.notificationKey)
        addDetailField("Data powiadomienia", formatTime(record.postedAt))
        addDetailField("Data zapisu", formatTime(record.savedAt))
        addDetailField("Tytuł", record.title)
        addDetailField("Tekst", record.text)
        addDetailField("Tekst rozszerzony", record.expandedText)
        addDetailField("Podtekst", record.subText)
        addDetailField("Zawiera obraz lub dużą ikonę", if (record.hasImageOrLargeIcon) "Tak" else "Nie")
        addDebugLogs(record)

        addActionButton("Odczytaj", "Odczytaj wybrane zapisane powiadomienie") {
            speakRecord(record)
        }
        addActionButton("Usuń", "Usuń wybrane zapisane powiadomienie") {
            confirm(
                title = "Usunąć powiadomienie?",
                message = "Zostanie usunięty tylko ten zapis historii."
            ) {
                repository.delete(record.id)
                refreshCurrentSection()
            }
        }
    }

    private fun addApplicationRow(summary: ApplicationHistorySummary) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 16)
            minimumHeight = dp(72)
        }
        val text = TextView(this).apply {
            textSize = 19f
            setTextColor(getColor(android.R.color.black))
            this.text = "${summary.applicationName}\nLiczba zapisanych powiadomień: ${summary.notificationCount}"
        }
        row.addView(text, fullWidthParams())
        val button = Button(this).apply {
            this.text = "Pokaż powiadomienia"
            textSize = 18f
            minHeight = dp(56)
            isAllCaps = false
            contentDescription = "Pokaż powiadomienia aplikacji ${summary.applicationName}"
            setOnClickListener { showApplicationDetails(summary.packageName, summary.applicationName) }
        }
        row.addView(button, fullWidthParams())
        addDivider(row)
        contentLayout.addView(row, fullWidthParams())
    }

    private fun addDetailField(label: String, value: String) {
        val textValue = value.ifEmpty { "Brak" }
        val view = TextView(this).apply {
            textSize = 18f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 8, 0, 8)
            text = "$label: $textValue"
        }
        contentLayout.addView(view, fullWidthParams())
    }

    private fun addDebugLogs(record: NotificationHistoryRecord) {
        val title = TextView(this).apply {
            textSize = 20f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 20, 0, 8)
            text = "Debug logi"
        }
        contentLayout.addView(title, fullWidthParams())

        val logs = DebugLogStore.forNotification(record.notificationKey)
        if (logs.isEmpty()) {
            val empty = TextView(this).apply {
                textSize = 16f
                setTextColor(getColor(android.R.color.black))
                setPadding(0, 4, 0, 8)
                text = "Brak logów debug"
            }
            contentLayout.addView(empty, fullWidthParams())
            return
        }

        logs.forEach { entry -> addDebugLogEntry(entry) }
    }

    private fun addDebugLogEntry(entry: DebugLogEntry) {
        val view = TextView(this).apply {
            textSize = 16f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 4, 0, 4)
            text = "${formatTime(entry.timestamp)} | ${entry.stage} | ${entry.message}"
        }
        contentLayout.addView(view, fullWidthParams())
    }

    private fun addActionButton(label: String, description: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 18f
            minHeight = dp(56)
            isAllCaps = false
            contentDescription = description
            setOnClickListener { action() }
        }
        contentLayout.addView(button, fullWidthParams())
    }

    private fun addEmptyHistoryMessage() {
        val emptyView = TextView(this).apply {
            text = "Brak zapisanych powiadomień"
            textSize = 20f
            setTextColor(getColor(android.R.color.black))
            setPadding(0, 24, 0, 24)
        }
        contentLayout.addView(emptyView, fullWidthParams())
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply {
            setBackgroundColor(getColor(android.R.color.darker_gray))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun buildRecordSummary(record: NotificationHistoryRecord): String {
        val pieces = mutableListOf(record.applicationName, formatTime(record.postedAt))
        if (record.title.isNotEmpty()) pieces.add(record.title)
        val preview = firstNonEmpty(record.text, record.expandedText, record.subText)
        if (preview.isNotEmpty()) pieces.add(preview)
        return pieces.joinToString("\n")
    }

    private fun speakRecord(record: NotificationHistoryRecord) {
        val message = listOf(
            record.applicationName,
            record.title,
            record.text,
            record.expandedText,
            record.subText
        ).filter { it.isNotEmpty() }.joinToString(". ")
            .ifEmpty { "Brak tekstu do odczytania" }

        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "history-${record.id}")
        }
    }

    private fun refreshCurrentSection() {
        when (currentSection) {
            Section.RECENT -> showRecent()
            Section.APPLICATIONS -> showApplications()
            Section.APPLICATION_DETAILS -> {
                val packageName = selectedPackageName
                val summary = repository.applicationSummaries().firstOrNull { it.packageName == packageName }
                if (packageName != null && summary != null) {
                    showApplicationDetails(packageName, summary.applicationName)
                } else {
                    showApplications()
                }
            }
            Section.SETTINGS -> showSettings()
        }
    }

    private fun updateNotificationAccessStatus() {
        if (!::statusView.isInitialized) return
        statusView.text = if (isNotificationAccessEnabled()) {
            "Status: dostęp do powiadomień włączony"
        } else {
            "Status: włącz dostęp do powiadomień w ustawieniach Androida"
        }
    }

    private fun updateNavigationState() {
        recentButton.isEnabled = currentSection != Section.RECENT
        appsButton.isEnabled = currentSection != Section.APPLICATIONS && currentSection != Section.APPLICATION_DETAILS
        settingsButton.isEnabled = currentSection != Section.SETTINGS
    }

    private fun confirm(title: String, message: String, onConfirmed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Usuń") { _, _ -> onConfirmed() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun navigationButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 18f
            minHeight = dp(56)
            isAllCaps = false
            contentDescription = "Sekcja $label"
            setOnClickListener { action() }
        }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        return enabledListeners.split(':').any { componentName ->
            TextUtils.equals(componentName, "$packageName/${ReaderNotificationListenerService::class.java.name}") ||
                componentName.endsWith("/${ReaderNotificationListenerService::class.java.name}")
        }
    }

    private fun formatTime(timeMillis: Long): String {
        return dateTimeFormat.format(Date(timeMillis))
    }

    private fun firstNonEmpty(vararg values: String): String {
        return values.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    private fun fullWidthParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class Section {
        RECENT,
        APPLICATIONS,
        APPLICATION_DETAILS,
        SETTINGS
    }
}
