package com.settery.audioswitcher

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.settery.audioswitcher.LogManager.getLogsAsString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.content.FileProvider


const val KEY_MODE = "key_mode"
const val KEY_LOGGING = "key_logging"

class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentMode = MutableLiveData<Mode>()
    val currentMode: LiveData<Mode> = _currentMode
    private val _loggingState = MutableStateFlow(LoggingState.OFF)
    val loggingState = _loggingState.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    init {
        loadMode()
        loadLoggingState()
    }

    private fun loadLoggingState() {
        val savedStateName = sharedPrefs.getString(KEY_LOGGING, LoggingState.OFF.name)
        _loggingState.value = try {
            LoggingState.valueOf(savedStateName ?: LoggingState.OFF.name)
        } catch (e: IllegalArgumentException) {
            LoggingState.OFF // Если в SharedPreferences сохранилось что-то некорректное
        }

        // Синхронизируем LogManager с загруженным состоянием
        if (_loggingState.value == LoggingState.ACTIVE) {
            LogManager.startRecording()
        } else {
            LogManager.stopRecording() // или просто убедиться, что isRecordingEnabled = false
        }
    }

    fun toggleLoggingState() {
        val currentState = _loggingState.value
        val newState = if (currentState == LoggingState.ACTIVE) {
            LoggingState.OFF
        } else {
            LoggingState.ACTIVE
        }

        if (newState == LoggingState.ACTIVE) {
            LogManager.startRecording()
        } else {
            LogManager.stopRecording()
        }

        // рекомпозиция UI
        _loggingState.value = newState

        viewModelScope.launch {
            saveLoggingState(newState)
        }
    }

    fun sendEmailWithAttachment(context: Context, logFile: File?) {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822" // или "text/plain" если отправляете как текст
            putExtra(Intent.EXTRA_EMAIL, arrayOf("pavelgrr7@hotmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "App Bug Report - ${context.getString(R.string.app_name)}")

            val emailBody = StringBuilder()
            emailBody.append("Please describe the issue you experienced:\n\n\n")
            emailBody.append("--------------------------------\n")

            if (logFile != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", logFile)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                emailBody.append("\n\nLogs are attached.")
            } else {
                emailBody.append("\n\nNo logs were recorded or an error occurred while preparing them.")
                emailBody.append("\n\n--- RAW LOGS (IF AVAILABLE) ---\n")
                emailBody.append(getLogsAsString())
            }
            putExtra(Intent.EXTRA_TEXT, emailBody.toString())
        }

        try {
            context.startActivity(Intent.createChooser(emailIntent, "Send bug report via..."))
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(context, "No email client installed.", Toast.LENGTH_SHORT).show()
        }

    }

    fun onEnableServiceClicked() {
        if (!isAccessibilityServiceEnabled(getApplication(), VolumeButtonService::class.java)) {
            openAccessibilitySettings(getApplication())
        }
    }

    private fun saveLoggingState(state: LoggingState) {
        sharedPrefs.edit().putString(KEY_LOGGING, state.name).apply()
    }

    fun saveMode(mode: Mode) {
        sharedPrefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setMode(mode: Mode) {
        _currentMode.value = mode
        saveMode(mode)
    }

    fun loadMode() {
        val modeName = sharedPrefs.getString(KEY_MODE, Mode.OFF.name) ?: Mode.OFF.name
        _currentMode.value = Mode.valueOf(modeName)
    }
}
enum class Mode{
    BACKGROUND,
    ACTIVE,
    OFF
}
enum class LoggingState{
    ACTIVE,
    OFF
}