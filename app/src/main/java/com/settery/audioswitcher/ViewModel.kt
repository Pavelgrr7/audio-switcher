package com.settery.audioswitcher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

const val KEY_MODE = "key_mode"
const val KEY_LOGGING = "key_logging"

class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentMode = MutableLiveData<Mode>()
    val currentMode: LiveData<Mode> = _currentMode
    private val _loggingState = MutableLiveData<LoggingState>()
    val loggingState: LiveData<LoggingState> = _loggingState

    private val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    init {
        loadMode()
        loadLoggingState()
    }

    fun setMode(mode: Mode) {
        _currentMode.value = mode
        saveMode(mode)
    }

    fun setLoggingState(ls: LoggingState) {
        _loggingState.value = ls
        saveLoggingState(ls)

    }

    fun saveMode(mode: Mode) {
        sharedPrefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun saveLoggingState(ls: LoggingState) {
        sharedPrefs.edit().putString(KEY_LOGGING, ls.name).apply()
    }

    fun loadMode() {
        val modeName = sharedPrefs.getString(KEY_MODE, Mode.OFF.name) ?: Mode.OFF.name
        _currentMode.value = Mode.valueOf(modeName)
    }
    fun loadLoggingState() {
        val state = sharedPrefs.getString(KEY_LOGGING, LoggingState.OFF.name) ?: LoggingState.OFF.name
        _loggingState.value = LoggingState.valueOf(state)
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