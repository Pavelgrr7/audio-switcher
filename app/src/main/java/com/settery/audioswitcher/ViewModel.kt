package com.settery.audioswitcher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
const val KEY_SERVICE_MODE = "service_mode_key"

class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentMode = MutableLiveData<Mode>()
    val currentMode: LiveData<Mode> = _currentMode

    private val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    init {
        loadMode()
    }

    fun setMode(mode: Mode) {
        _currentMode.value = mode
        saveMode(mode)
    }

    fun saveMode(mode: Mode) {
        sharedPrefs.edit().putString(KEY_SERVICE_MODE, mode.name).apply()
    }

    fun loadMode() {
        val modeName = sharedPrefs.getString(KEY_SERVICE_MODE, Mode.OFF.name) ?: Mode.OFF.name
        _currentMode.value = Mode.valueOf(modeName)
    }
}
enum class Mode{
    FOREGROUND,
    ACTIVE,
    OFF
}