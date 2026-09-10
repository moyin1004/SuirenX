package io.suirenx.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshLocalSummary() }
    var exportContent by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = exportContent
        if (uri != null && content != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)) { "无法写入备份文件" }.use { it.write(content.toByteArray()) }
                }
                viewModel.message("备份已导出")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { viewModel.message(error.message ?: "导出失败，请重试") }
            finally { exportContent = null }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取备份文件")
                }
                viewModel.inspectBackup(content)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { viewModel.message(error.message ?: "读取备份失败") }
        }
    }
    LaunchedEffect(viewModel, exportLauncher) {
        viewModel.events.collect { event ->
            when (event) {
                is BackendEvent.ExportBackup -> {
                    exportContent = event.content
                    exportLauncher.launch("suirenx-backup.json")
                }
                is BackendEvent.Message -> viewModel.message(event.text)
            }
        }
    }
    BackendSettingsScreen(
        state = state,
        onAddressChanged = viewModel::onAddressChanged,
        onNameChanged = viewModel::onNameChanged,
        onSave = viewModel::save,
        onOpenServer = viewModel::openServer,
        onEditServer = viewModel::editServer,
        onTestConnection = viewModel::testConnection,
        onSelect = viewModel::select,
        onDeleteServer = viewModel::removeServer,
        onRetry = viewModel::load,
        onUseLocal = viewModel::useLocal,
        onUseRemote = viewModel::useRemote,
        onCancelSync = viewModel::cancelSync,
        onResolveConflict = viewModel::resolveConflict,
        onSyncNow = viewModel::syncNow, onSyncSchedule = viewModel::selectSyncSchedule,
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
        onResetAuthForm = viewModel::resetAuthForm,
        onLogin = viewModel::login,
        onLogout = viewModel::logout,
        onPreviewMigration = viewModel::previewMigration,
        onConfirmMigration = viewModel::confirmMigration,
        onCancelMigration = viewModel::cancelMigration,
        modifier = modifier,
    )
}
