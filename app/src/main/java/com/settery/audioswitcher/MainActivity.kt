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
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.settery.audioswitcher.ui.theme.AudioSwitcherTheme
lateinit var viewModel: ServiceViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModelHolder = ViewModelHolder(this)
        viewModel = viewModelHolder.get("AlarmViewModel") {
            ServiceViewModel(application)
        }
        viewModel.loadMode()
        viewModel.currentMode.observe(this) { mode ->
            VolumeButtonService.updateServiceMode(this, mode)
        }
        requestIgnoreBatteryOptimizations()
        setContent {
            AudioSwitcherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
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

}
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selectedMode: MutableState<Mode> = viewModel.currentMode.observeAsState() as MutableState<Mode>

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection()
        EnableServiceButton()

        ModeSelectionSection(
            selectedMode = selectedMode,
            onModeSelected = { viewModel.setMode(it) }
        )
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
private fun EnableServiceButton() {
    val context = LocalContext.current

    Button(
        onClick = { if (!isAccessibilityServiceEnabled(context, VolumeButtonService::class.java)) openAccessibilitySettings(context) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.enable_accessibility_button))
    }
}
@Composable
private fun ModeSelectionSection(
    selectedMode: MutableState<Mode>,       // Текущий выбранный режим
    onModeSelected: (Mode) -> Unit // Колбэк при выборе
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
                isSelected = selectedMode.value == (mode),
                onSelected = { onModeSelected(mode) }
            )
        }
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
//
//@Composable
//fun <T> LiveData<T>.observeAsState(): MutableState<T?> {
//    val lifecycleOwner = LocalLifecycleOwner.current
//    val state = remember { mutableStateOf(value) }
//
//    DisposableEffect(this, lifecycleOwner) {
//        val observer = Observer<T> { state.value = it }
//        observe(lifecycleOwner, observer)
//        onDispose { removeObserver(observer) }
//    }
//
//    return state
//}

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
fun Preview() {
    AudioSwitcherTheme {
        MainScreen()
    }
}

class ViewModelHolder(owner: ViewModelStoreOwner) {
    private val store = owner.viewModelStore
    private val viewModels = mutableMapOf<String, ViewModel>()

    fun <T : ViewModel> get(key: String, creator: () -> T): T {
        return viewModels.getOrPut(key) { creator() } as T
    }
}
