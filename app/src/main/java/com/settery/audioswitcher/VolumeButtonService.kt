package com.settery.audioswitcher

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
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class VolumeButtonService : AccessibilityService() {

    private var currentMode = Mode.OFF
    private lateinit var keyguardManager: KeyguardManager
    private val cameraPackages = mutableSetOf<String>()
    private var isCameraActive = false


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
    private var currentKeyCode: Int? = null
    private var pressStartTime = 0L
    private val LONG_PRESS_THRESHOLD = 500L
    private var isHandledAsLongPress = false
    private lateinit var wakeLock: PowerManager.WakeLock
    private val powerManager by lazy {getSystemService(POWER_SERVICE) as PowerManager}


    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("HELP", "test log")
        val interactive = powerManager.isInteractive // Получаем один раз для консистентности в этом вызове
        LogManager.log("VolumeButtonService", "[KEY_EVENT_ENTRY] Interactive: $interactive, Mode: $currentMode, Action: ${event.action}, KeyCode: ${event.keyCode}, Time: ${System.currentTimeMillis()}")
        if (isCameraActive) {
            LogManager.log("VolumeButtonService", "Camera is active, ignoring key event to allow shutter control.")
            return false
        }
        if (currentMode == Mode.OFF) return false
        if (currentMode == Mode.ACTIVE && keyguardManager.isDeviceLocked) return false
        if (currentMode == Mode.BACKGROUND) {
            if (!keyguardManager.isDeviceLocked || interactive)
            return false
        }
        LogManager.log("VolumeButtonService", "Not interactive, right mode")

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
        LogManager.log("VolumeButtonService", "handling key up: ${event.action}")
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
        LogManager.log("VolumeButtonService", "Long press detected: $currentKeyCode")
        when (currentKeyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT)
            KeyEvent.KEYCODE_VOLUME_DOWN -> sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    private fun handleShortPress(keyCode: Int) {
        LogManager.log("VolumeButtonService", "Short press detected: $keyCode")
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
    private fun getModeFromIntent(intent: Intent): Mode? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MODE, Mode::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MODE) as? Mode?: Mode.OFF
        }
    }

    private fun loadCameraPackages() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val packageManager = packageManager
        val cameraApps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        for (resolveInfo in cameraApps) {
            val packageName = resolveInfo.activityInfo.packageName
            cameraPackages.add(packageName)
            LogManager.log("VolumeButtonService", "Found camera app: $packageName")
        }
    }

    // notification

    // (Android 8+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Volume Button Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for the volume button control service notification"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            LogManager.log("VolumeButtonService", "Notification channel created.")
        }
    }

    private fun createNotification(): Notification {
        val notificationIcon = R.mipmap.ic_launcher

        val pendingIntent: PendingIntent? = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        LogManager.log("VolumeButtonService", "Building notification.")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volume Key Control Active")
            .setContentText("Controlling media with volume keys.")
            .setSmallIcon(notificationIcon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }


    // modes

    private fun loadCurrentMode() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val modeName = prefs.getString(KEY_MODE, Mode.OFF.name) ?: Mode.OFF.name
        currentMode = try {
            Mode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            Mode.OFF
        }
        LogManager.log("VolumeButtonService", "Loaded mode: $currentMode")
    }

    private fun saveCurrentMode(mode: Mode) {
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .apply()
        LogManager.log("VolumeButtonService", "Saved mode: $mode")
    }

    private fun updateMode(newMode: Mode) {
        LogManager.log("VolumeButtonService", "Updating mode from $currentMode to $newMode")
        currentMode = newMode
        saveCurrentMode(newMode)

        when (newMode) {
            Mode.OFF -> disableService()
            Mode.ACTIVE, Mode.BACKGROUND -> enableService(newMode)
        }
    }

    // service

    private fun enableService(mode: Mode) {
        LogManager.log("VolumeButtonService", "Service logic enabled in $mode mode.", LogLevel.INFO)
    }

    private fun disableService() {
        LogManager.log("VolumeButtonService", "Service logic disabled (Mode OFF).", LogLevel.INFO)
        // нет stopSelf(), т.к. сервис оставаётся
        // запущенным в фоне (как service) даже в режиме OFF,
        // ожидая смены режима. ( но onKeyEvent игнорирует события)
    }


    override fun onCreate() {
        super.onCreate()
//        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
//        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioSwitcher::bs_wakelock")
//        wakeLock.acquire(10*60*1000L /*10 minutes*/)
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        loadCameraPackages()

        LogManager.log("VolumeButtonService", "onCreate: Creating notification channel and starting foreground.", LogLevel.DEBUG)
        createNotificationChannel()
        val notification = createNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            LogManager.log("VolumeButtonService", "Service successfully started in foreground.", LogLevel.INFO)
        } catch (e: Exception) {
            LogManager.log("VolumeButtonService", "Error starting foreground service: \n${e.toString()}", LogLevel.ERROR)
        }

        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        ContextCompat.registerReceiver(this, modeReceiver, IntentFilter(ACTION_UPDATE_MODE), receiverFlags)

        loadCurrentMode()

        LogManager.log("VolumeButtonService", "Service Created. Initial mode: $currentMode")

        updateMode(currentMode)
    }

    override fun onDestroy() {
        LogManager.log("VolumeButtonService", "onDestroy: Stopping foreground service.")
        stopForeground(true) // true = убрать уведомление

        try {
            unregisterReceiver(modeReceiver)
        } catch (e: IllegalArgumentException) {
            LogManager.log("VolumeButtonService", "Receiver already unregistered?", LogLevel.WARNING)
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (cameraPackages.isEmpty()) {
                LogManager.log("VolumeButtonService", "Camera packages list is empty, cannot check for camera.", LogLevel.WARNING)
                return
            }

            val isCurrentWindowCamera = cameraPackages.contains(packageName)

            if (isCurrentWindowCamera && !isCameraActive) {
                // Camera widdow just opened
                LogManager.log("VolumeButtonService", "Camera app opened ($packageName). Pausing volume key handling.", LogLevel.INFO)
                isCameraActive = true
            } else if (!isCurrentWindowCamera && isCameraActive) {
                // User closed camera window -> resume volume key handling
                LogManager.log("VolumeButtonService", "Camera app closed or moved to background. Resuming volume key handling.", LogLevel.INFO)
                isCameraActive = false
            }
        }
    }

    override fun onInterrupt() {
        LogManager.log("VolumeButtonService", "Accessibility service interrupted!", LogLevel.WARNING)
    }

    private fun sendMediaCommand(keyCode: Int) {
        LogManager.log("VolumeButtonService", "Attempting to send media command: $keyCode", LogLevel.DEBUG)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?

        if (audioManager == null) {
            LogManager.log("VolumeButtonService", "AudioManager is null, cannot dispatch media key event.", LogLevel.WARNING)
            return
        }

        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)

        try {
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            LogManager.log("VolumeButtonService", "Dispatched media key event $keyCode using dispatchMediaKeyEvent.")
        } catch (e: Exception) {
            LogManager.log("VolumeButtonService", "Error sending media command $keyCode \n ${e.message}", LogLevel.ERROR)
        }
    }
    override fun onUnbind(intent: Intent?): Boolean {
        LogManager.log("VolumeButtonService", "onUnbind called. Service is being stopped by system or user.",
            LogLevel.ERROR)
        // Важно вызвать stopForeground и unregisterReceiver, как в onDestroy.
        // stopForeground(STOP_FOREGROUND_REMOVE) // или stopForeground(true)
         try {
             unregisterReceiver(modeReceiver)
         } catch (e: IllegalArgumentException) {
             LogManager.log("VolumeButtonService", "Receiver already unregistered in onUnbind?", LogLevel.WARNING)
         }
        return super.onUnbind(intent)
    }

    companion object {
        const val ACTION_UPDATE_MODE = "com.settery.audioswitcher.UPDATE_MODE"
        const val EXTRA_MODE = "extra_mode"

        fun updateServiceMode(context: Context, mode: Mode) {
            LogManager.log("VolumeButtonService", "Sending broadcast to update mode: $mode")
            val intent = Intent(ACTION_UPDATE_MODE).apply {
                putExtra(EXTRA_MODE, mode)
                // это хорошая практика для локальных broadcast'ов
                `package` = context.packageName
            }
            context.sendBroadcast(intent)
        }
    }
}