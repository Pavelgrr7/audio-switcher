package com.settery.audioswitcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import com.settery.audioswitcher.ui.theme.AudioSwitcherTheme


class MainActivity : ComponentActivity() {

    private val viewModel: ServiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.loadMode()
        viewModel.currentMode.observe(this) { mode ->
            VolumeButtonService.updateServiceMode(this, mode)
        }
        LogManager.initialize(application)

        requestIgnoreBatteryOptimizations()
        showPowerManagerSettings()


        setContent {
            AudioSwitcherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        )
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")

                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    LogManager.log("MainActivity", "Cannot request ignore battery optimizations: Intent not resolvable.")
                }
            }
        }
    }


    private fun showPowerManagerSettings() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        var intent: Intent? = null

        when {
            // Xiaomi
            manufacturer.contains("xiaomi") -> {
                intent = Intent()
                intent.component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                intent.putExtra("package_name", packageName)
                intent.putExtra("package_label", getString(R.string.app_name))
            }
            // Samsung
            manufacturer.contains("samsung") -> {
                intent = Intent()

                intent.component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
            // Huawei
            manufacturer.contains("huawei") -> {
                intent = Intent()
                intent.component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            //Maybe I should add other manufacturers here
        }
    }

    override fun onStop() {
        super.onStop()

    }
}

fun isAccessibilityServiceEnabled(
    context: Context,
    serviceClass: Class<out AccessibilityService>
): Boolean {
    val expectedComponentName = ComponentName(context, serviceClass)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServices.contains(expectedComponentName.flattenToString())
}

// open settings to grant permission
fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

