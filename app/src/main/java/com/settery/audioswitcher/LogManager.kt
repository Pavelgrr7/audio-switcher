package com.settery.audioswitcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object LogManager {

    private const val MAX_LOG_ENTRIES = 1000
    private val logBuffer = LinkedList<String>()
    lateinit var prefs: SharedPreferences
    var currentLoggingState = LoggingState.OFF

    var currLocale = Locale.getDefault()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", currLocale)

    fun initialize(context: Context) {
        currLocale = Locale.getDefault()
        prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    fun setLoggingState(ls: LoggingState) {
        currentLoggingState = ls
    }

    fun startRecording() {
        currLocale = Locale.getDefault()
        addLogInternal("LogManager", "USER: Logging started by user.", LogLevel.INFO)
    }

    fun stopRecording() {
        addLogInternal("LogManager", "USER: Logging stopped by user.", LogLevel.INFO)
    }

    // Этот метод будет вызыввется из кода
    fun log(tag: String, message: String, level: LogLevel = LogLevel.DEBUG) {
        if (currentLoggingState == LoggingState.OFF) return
        addLogInternal(tag, message, level)
    }

    @Synchronized // maybe change it later
    private fun addLogInternal(tag: String, message: String, level: LogLevel) {
        val logEntry = "${dateFormat.format(Date())} ${level.name}/$tag: $message"

        if (logBuffer.size >= MAX_LOG_ENTRIES) {
            logBuffer.removeAt(0) // буфер полон -> удаляется самая старая запись
        }
        logBuffer.addLast(logEntry)

        when(level) {
            LogLevel.VERBOSE -> android.util.Log.v(tag, message)
            LogLevel.DEBUG -> android.util.Log.d(tag, message)
            LogLevel.INFO -> android.util.Log.i(tag, message)
            LogLevel.WARNING -> android.util.Log.w(tag, message)
            LogLevel.ERROR -> android.util.Log.e(tag, message)
        }
    }

    @Synchronized
    fun getLogsAsString(): String {
        return logBuffer.joinToString(separator = "\n")
    }

    @Synchronized
    fun getLogsAsFile(context: Context, fileName: String = "app_logs.txt"): File? {
        val logString = getLogsAsString()
        if (logString.isEmpty()) return null

        return try {
            val file = File(context.cacheDir, fileName)
            FileWriter(file).use {
                it.write(getDeviceAndAppInfo(context))
                it.write("\n\n--- LOGS ---\n")
                it.write(logString)
            }
            file
        } catch (e: Exception) {
            log("LogManager", "Error writing logs to file: ${e.message}", LogLevel.ERROR)
            null
        }
    }

    @Synchronized
    fun clearLogs() {
        logBuffer.clear()
        addLogInternal("LogManager", "Logs cleared.", LogLevel.INFO)
    }

    private fun getDeviceAndAppInfo(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        return """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            App Version: $versionName ($versionCode)
            Timestamp: ${dateFormat.format(Date())}
        """.trimIndent()
    }


//    fun onSendReportClicked(context: Context) {
//        val logFile = LogManager.getLogsAsFile(context)
//
//        val emailIntent = Intent(Intent.ACTION_SEND).apply {
//            type = "message/rfc822" // или "text/plain" если отправляете как текст
//            putExtra(Intent.EXTRA_EMAIL, arrayOf("pavelgrr7@hotmail.com"))
//            putExtra(Intent.EXTRA_SUBJECT, "App Bug Report - ${context.getString(R.string.app_name)}")
//
//            val emailBody = StringBuilder()
//            emailBody.append("Please describe the issue you experienced:\n\n\n")
//            emailBody.append("--------------------------------\n")
//            emailBody.append(getDeviceAndAppInfo(context))
//
//            if (logFile != null) {
//                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", logFile)
//                putExtra(Intent.EXTRA_STREAM, uri)
//                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                emailBody.append("\n\nLogs are attached.")
//            } else {
//                emailBody.append("\n\nNo logs were recorded or an error occurred while preparing them.")
//                emailBody.append("\n\n--- RAW LOGS (IF AVAILABLE) ---\n")
//                emailBody.append(getLogsAsString())
//            }
//            putExtra(Intent.EXTRA_TEXT, emailBody.toString())
//        }
//
//        try {
//            context.startActivity(Intent.createChooser(emailIntent, "Send bug report via..."))
//        } catch (ex: ActivityNotFoundException) {
//            Toast.makeText(context, "No email client installed.", Toast.LENGTH_SHORT).show()
//        }
//    }
}

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}