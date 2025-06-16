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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.settery.audioswitcher.ui.theme.AudioSwitcherTheme
import androidx.activity.viewModels
import java.security.acl.Permission


class MainActivity : ComponentActivity() {

    private val viewModel: ServiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.loadMode()
        viewModel.currentMode.observe(this) { mode ->
            VolumeButtonService.updateServiceMode(this, mode)
        }
        requestIgnoreBatteryOptimizations()
        showPowerManagerSettings()
        setContent {
            AudioSwitcherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentMode by viewModel.currentMode.observeAsState(initial = viewModel.currentMode.value ?: Mode.OFF)

                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentMode = currentMode,
                        onModeSelected = { newMode -> viewModel.setMode(newMode) },
                        onEnableServiceClicked = {
                            if (!isAccessibilityServiceEnabled(this, VolumeButtonService::class.java)) {
                                openAccessibilitySettings(this)
                            }
                        },
                        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(this, VolumeButtonService::class.java)
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
                    Log.w("MainActivity", "Cannot request ignore battery optimizations: Intent not resolvable.")
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

}
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    currentMode: Mode,
    onModeSelected: (Mode) -> Unit,
    onEnableServiceClicked: () -> Unit,
    isAccessibilityServiceEnabled: Boolean
) {

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection()
        EnableServiceButton(
            onClick = onEnableServiceClicked,
        )

        ModeSelectionSection(
            selectedMode = currentMode,
            onModeSelected = onModeSelected
        )
    }
}

@Composable
private fun EnableServiceButton(onClick: () -> Unit) {

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.enable_accessibility_button))
    }
}

@Composable
private fun ModeSelectionSection(
    selectedMode: Mode,
    onModeSelected: (Mode) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.select_mode_text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        listOf(Mode.ACTIVE, Mode.FOREGROUND, Mode.OFF).forEach { mode ->
            ModeSelectionItem(
                mode = mode,
                isSelected = selectedMode == mode,
                onSelected = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun HeaderSection() {
    Row {
        Text(
            text = stringResource(R.string.welcome_text),
            style = MaterialTheme.typography.headlineSmall
        )
    }
}
@Composable
private fun ModeSelectionItem(
    mode: Mode,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelected)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Text(
            text = when(mode) {
                Mode.ACTIVE -> stringResource(R.string.active_mode)
                Mode.FOREGROUND -> stringResource(R.string.foreground_mode)
                Mode.OFF -> stringResource(R.string.off_mode)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AudioSwitcherTheme {
        MainScreen(
            currentMode = Mode.ACTIVE,
            onModeSelected = {},
            onEnableServiceClicked = {},
            isAccessibilityServiceEnabled = true
        )
    }
}