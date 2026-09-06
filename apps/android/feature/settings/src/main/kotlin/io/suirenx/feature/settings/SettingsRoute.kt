package io.suirenx.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.collect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The settings tab: backend address management without any gate or back stack.
 */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: BackendViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeViewModel: ThemeSettingsViewModel = hiltViewModel()
    val theme by themeViewModel.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportContent by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = exportContent
        if (uri != null && content != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取备份文件")
            }.onSuccess(viewModel::inspectBackup)
                .onFailure { viewModel.inspectBackup("invalid") }
        }
    }
    LaunchedEffect(viewModel, exportLauncher) {
        viewModel.events.collect { event ->
            when (event) {
                is BackendEvent.ExportBackup -> {
                    exportContent = event.content
                    exportLauncher.launch("suirenx-backup.json")
                }
                is BackendEvent.Message -> Unit
            }
        }
    }
    BackendSettingsScreen(
        state = state,
        onAddressChanged = viewModel::onAddressChanged,
        onNameChanged = viewModel::onNameChanged,
        onSave = viewModel::save,
        onSelect = viewModel::select,
        onRetry = viewModel::load,
        onUseLocal = viewModel::useLocal,
        onUseRemote = viewModel::useRemote,
        onExportBackup = viewModel::exportBackup,
        onImportBackup = { importLauncher.launch("application/json") },
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
        themeMode = theme,
        onThemeModeChanged = themeViewModel::select,
        onSoonDaysChanged = viewModel::onSoonDaysChanged,
        onAuthUsernameChanged = viewModel::onAuthUsernameChanged,
        onAuthPasswordChanged = viewModel::onAuthPasswordChanged,
        onRegister = viewModel::register,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onPreviewMigration = viewModel::previewMigration,
        onConfirmMigration = viewModel::confirmMigration,
        onCancelMigration = viewModel::cancelMigration,
        modifier = modifier,
    )
}
