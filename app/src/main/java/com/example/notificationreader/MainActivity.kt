package com.example.notificationreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 56, 40, 40)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(this).apply {
            text = "Czytnik powiadomien"
            textSize = 24f
        }

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 28, 0, 28)
        }

        val openSettingsButton = Button(this).apply {
            text = "Dostep do powiadomien"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val speakingSwitch = Switch(this).apply {
            text = "Odczyt glosowy"
            isChecked = NotificationSpeechPrefs.isSpeakingEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                NotificationSpeechPrefs.setSpeakingEnabled(this@MainActivity, isChecked)
            }
        }

        root.addView(titleView)
        root.addView(statusView)
        root.addView(openSettingsButton)
        root.addView(speakingSwitch)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        statusView.text = if (isNotificationAccessEnabled()) {
            "Status: dostep do powiadomien wlaczony"
        } else {
            "Status: wlacz dostep do powiadomien w ustawieniach Androida"
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
}
