package com.settery.audioswitcher

import android.R.attr.description
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

const val ACTION_UPDATE_MODE = "com.settery.audioswitcher.UPDATE_MODE"
const val EXTRA_MODE = "extra_mode"
//const val KEY_SERVICE_MODE = "service_mode_key"

class VolumeButtonService : AccessibilityService() {

    private var currentMode = Mode.OFF
    private lateinit var keyguardManager: KeyguardManager

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "VolumeButtonServiceChannel"
    private val modeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("VolumeButtonService", "ModeReceiver received broadcast with action: ${intent.action}")
            if (intent.action == ACTION_UPDATE_MODE) {
                val newMode: Mode = getModeFromIntent(intent) ?: Mode.OFF
                Log.d("VolumeButtonService", "Received new mode: $newMode")
                updateMode(newMode)
            }
        }
    }
    private var isLongPress = false
    private var currentKeyCode: Int? = null
    private var pressStartTime = 0L
    private val LONG_PRESS_THRESHOLD = 500L
    private var isHandledAsLongPress = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (currentMode == Mode.OFF) return false
        if (currentMode == Mode.ACTIVE && keyguardManager.isDeviceLocked) return false
        if (currentMode == Mode.FOREGROUND && !keyguardManager.isDeviceLocked) {
            return false
            Log.d("VolumeButtonService", "status: ${keyguardManager.isDeviceLocked}")
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(event)
            KeyEvent.ACTION_UP -> handleKeyUp(event)
            else -> false
        }
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
        pressStartTime = System.currentTimeMillis()
        isHandledAsLongPress = false
        currentKeyCode = event.keyCode
        return true
    }

    private fun handleKeyUp(event: KeyEvent): Boolean {
        val pressDuration = System.currentTimeMillis() - pressStartTime

        if (pressDuration >= LONG_PRESS_THRESHOLD) {
            // Долгое нажатие - переключаем трек
            handleLongPress()
            isHandledAsLongPress = true
        } else {
            // Короткое нажатие - изменяем громкость
            handleShortPress(event.keyCode)
            isHandledAsLongPress = false
        }

        return true
    }

    private fun handleLongPress() {
        when (currentKeyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT)
            KeyEvent.KEYCODE_VOLUME_DOWN -> sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    private fun handleShortPress(keyCode: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_SAME
        }

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

//    private val longPressRunnable = Runnable {
//        isLongPress = true
//        when (currentKeyCode) {
//            KeyEvent.KEYCODE_VOLUME_UP -> sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT)
//            KeyEvent.KEYCODE_VOLUME_DOWN -> sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
//        }
//    }
//    private fun suppressDefaultVolumeChange() {
//        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
//        audioManager.adjustStreamVolume(
//            AudioManager.STREAM_MUSIC,
//            AudioManager.ADJUST_SAME,
//            AudioManager.FLAG_SHOW_UI
//        )
//    }

    private fun getModeFromIntent(intent: Intent): Mode? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MODE, Mode::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MODE) as? Mode?: Mode.OFF
        }
    }

    override fun onCreate() {
        super.onCreate()
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        Log.d("VolumeButtonService", "onCreate: Creating notification channel and starting foreground.")
        createNotificationChannel()
        val notification = createNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.i("VolumeButtonService", "Service successfully started in foreground.")
        } catch (e: Exception) {
            Log.e("VolumeButtonService", "Error starting foreground service", e)
        }

        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        ContextCompat.registerReceiver(this, modeReceiver, IntentFilter(ACTION_UPDATE_MODE), receiverFlags)

        loadCurrentMode()
        Log.d("VolumeButtonService", "Service Created. Initial mode: $currentMode")
        updateMode(currentMode)
    }

    override fun onDestroy() {
        Log.d("VolumeButtonService", "onDestroy: Stopping foreground service.")
        stopForeground(true) // true = убрать уведомление

        try {
            unregisterReceiver(modeReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("VolumeButtonService", "Receiver already unregistered?")
        }
        super.onDestroy()
    }

    // (Android 8+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Volume Button Control Service",
                NotificationManager.IMPORTANCE_HIGH // возможно, стоит поменять на LOW
            ).apply {
                description = "Channel for the volume button control service notification"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d("VolumeButtonService", "Notification channel created.")
        }
    }

    private fun createNotification(): Notification {
        val notificationIcon = R.mipmap.ic_launcher

        val pendingIntent: PendingIntent? = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        Log.d("VolumeButtonService", "Building notification.")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volume Key Control Active")
            .setContentText("Controlling media with volume keys.")
            .setSmallIcon(notificationIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // high?
            .build()
    }

    private fun loadCurrentMode() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val modeName = prefs.getString(KEY_SERVICE_MODE, Mode.OFF.name) ?: Mode.OFF.name
        currentMode = try {
            Mode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            Mode.OFF
        }
        Log.d("VolumeButtonService", "Loaded mode: $currentMode")
    }

    private fun saveCurrentMode(mode: Mode) {
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
            .putString(KEY_SERVICE_MODE, mode.name)
            .apply()
        Log.d("VolumeButtonService", "Saved mode: $mode")
    }

    private fun updateMode(newMode: Mode) {
        Log.d("VolumeButtonService", "Updating mode from $currentMode to $newMode")
        currentMode = newMode
        saveCurrentMode(newMode)

        when (newMode) {
            Mode.OFF -> disableService()
            Mode.ACTIVE, Mode.FOREGROUND -> enableService(newMode)
        }
    }

    private fun enableService(mode: Mode) {
        Log.i("VolumeButtonService", "Service logic enabled in $mode mode.")
    }

    private fun disableService() {
        Log.i("VolumeButtonService", "Service logic disabled (Mode OFF).")
        // нет stopSelf(), т.к. сервис оставаётся
        // запущенным в фоне (как foreground service) даже в режиме OFF,
        // ожидая смены режима. ( но onKeyEvent игнорирует события)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.w("VolumeButtonService", "Accessibility service interrupted!")
    }

//    override fun onKeyEvent(event: KeyEvent): Boolean {
//        Log.d("VolumeButtonService", "onKeyEvent Received! Action: ${event.action}, KeyCode: ${event.keyCode}, Mode: $currentMode")
//
//        if (currentMode == Mode.OFF) {
//            return super.onKeyEvent(event)
//        }
//
//        if (currentMode == Mode.FOREGROUND) {
//            if (!keyguardManager.isKeyguardLocked) {
//                return super.onKeyEvent(event)
//            }
//        }
//
//        if (event.action == KeyEvent.ACTION_DOWN) {
//            when (event.keyCode) {
//                KeyEvent.KEYCODE_VOLUME_UP -> {
//                    sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT)
//                    return true
//                }
//                KeyEvent.KEYCODE_VOLUME_DOWN -> {
//                    sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
//                    return true
//                }
//            }
//        }
//
//        // В данном случае, если это не ACTION_DOWN или не клавиша громкости, передаем дальше.
//        return super.onKeyEvent(event)
//    }


    private fun sendMediaCommand(keyCode: Int) {
        Log.d("VolumeButtonService", "Attempting to send media command: $keyCode")

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?

        if (audioManager == null) {
            Log.e("VolumeButtonService", "AudioManager is null, cannot dispatch media key event.")
            return
        }

        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)

        try {
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            Log.d("VolumeButtonService", "Dispatched media key event $keyCode using dispatchMediaKeyEvent.")
        } catch (e: Exception) {
            Log.e("VolumeButtonService", "Error sending media command $keyCode", e)
        }
    }

    companion object {
        const val ACTION_UPDATE_MODE = "com.settery.audioswitcher.UPDATE_MODE"
        const val EXTRA_MODE = "extra_mode"

        fun updateServiceMode(context: Context, mode: Mode) {
            Log.d("VolumeButtonService", "Sending broadcast to update mode: $mode")
            val intent = Intent(ACTION_UPDATE_MODE).apply {
                putExtra(EXTRA_MODE, mode)
                // это хорошая практика для локальных broadcast'ов
                `package` = context.packageName
            }
            context.sendBroadcast(intent)
        }
    }
}