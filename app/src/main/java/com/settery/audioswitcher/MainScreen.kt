package com.settery.audioswitcher

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: ServiceViewModel,

) {

    val currentMode by viewModel.currentMode.observeAsState(
                        initial = viewModel.currentMode.value ?: Mode.OFF
                    )

    val loggingState by viewModel.loggingState.collectAsState()
    var showLogsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Scaffold(
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { showLogsDialog = true },
                contentColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Filled.Build, "Small floating action button.")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection()
            EnableServiceButton(
                onClick = { viewModel.onEnableServiceClicked() },
            )

            ModeSelectionSection(
                selectedMode = currentMode,
                onModeSelected = { viewModel.setMode(it) }
            )
        }
        if (showLogsDialog) {
            LogsDialog(
                onDismiss = { showLogsDialog = false },
                loggingState = loggingState,
                onToggleClick = { viewModel.toggleLoggingState() },
                onSendLogsAndStop = {
                    scope.launch(Dispatchers.IO) { // Файловые операции в фоновом потоке
                        val logFile: File? = LogManager.getLogsAsFile(context)

                        // главный поток, запуск Intent
                        withContext(Dispatchers.Main) {
                            if (logFile != null) {
                                viewModel.sendEmailWithAttachment(context, logFile)
                            } else {
                                Toast.makeText(context, "Log file could not be created.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    viewModel.toggleLoggingState()
                }
            )
        }
    }
}

@Composable
fun LogsDialog(
    loggingState: LoggingState,
    onDismiss: () -> Unit,
    onToggleClick: () -> Unit,
    onSendLogsAndStop: () -> Unit
) {
Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .wrapContentHeight()
                .wrapContentWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.logs_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.logs_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (loggingState == LoggingState.ACTIVE) {
                            // Если запись идет, вызываем новую лямбду
                            onSendLogsAndStop()
                            onDismiss() // Закрываем диалог
                        } else {
                            // Иначе просто переключаем состояние
                            onToggleClick()
                        }
                    },
//                        modifier = Modifier.fillMaxWidth() //  кнопка на всю ширину
                ) {
                    if (loggingState == LoggingState.ACTIVE) {
                        Text(stringResource(R.string.stop_logs_recording))
                    } else {
                        Text(stringResource(R.string.start_logs_recording))
                    }
                }
            }
        }
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

        listOf(Mode.ACTIVE, Mode.BACKGROUND, Mode.OFF).forEach { mode ->
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
                Mode.BACKGROUND -> stringResource(R.string.foreground_mode)
                Mode.OFF -> stringResource(R.string.off_mode)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

//
//@Preview(showBackground = true)
//@Composable
//fun MainScreenPreview() {
//    AudioSwitcherTheme {
//        MainScreen(
//            currentMode = Mode.ACTIVE,
//            onModeSelected = {},
//            onEnableServiceClicked = {},
//            isAccessibilityServiceEnabled = true,
//            onSendLogsClicked = TODO()
//            )
//    }
//}
