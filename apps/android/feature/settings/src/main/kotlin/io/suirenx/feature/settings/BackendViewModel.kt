package io.suirenx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.domain.AuthRepository
import io.suirenx.core.domain.AuthState
import io.suirenx.core.model.BackendSettings
import io.suirenx.core.model.StorageMode
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.domain.BackupSummary
import io.suirenx.core.domain.LocalBackupRepository
import io.suirenx.core.domain.MigrationPreview
import io.suirenx.core.domain.MigrationResult
import io.suirenx.core.domain.RemoteImportRepository
import io.suirenx.core.domain.DataChangeNotifier
import io.suirenx.core.domain.ExpirySettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class BackendUiState(
    val localSummary: BackupSummary? = null,
    val conflictBusy: Boolean = false,
    val notice: String? = null,
    val syncSchedule: io.suirenx.core.domain.SyncSchedule = io.suirenx.core.domain.SyncSchedule.OnChange,
    val syncStatus: io.suirenx.core.domain.RemoteSyncStatus = io.suirenx.core.domain.RemoteSyncStatus(),
    val settings: BackendSettings? = null,
    val mode: StorageMode? = null,
    val address: String = "",
    val inspectedServerUrl: String? = null,
    val editingServerUrl: String? = null,
    val serverSavedVersion: Int = 0,
    val accounts: Map<String, AuthState> = emptyMap(),
    val testingConnection: Boolean = false,
    val connectionTest: String? = null,
    val connectionTestFailed: Boolean = false,
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val backupSummary: BackupSummary? = null,
    val backupBusy: Boolean = false,
    val backupError: String? = null,
    val backupContent: String? = null,
    val soonDays: Int = 7,
    val auth: AuthState = AuthState("", false),
    val authUsername: String = "",
    val authPassword: String = "",
    val authBusy: Boolean = false,
    val authSuccessVersion: Int = 0,
    val authError: String? = null,
    val migrationPreview: MigrationPreview? = null,
    val migrationBusy: Boolean = false,
    val migrationError: String? = null,
    val migrationResult: MigrationResult? = null,
)

sealed interface BackendEvent {
    data class ExportBackup(val content: String) : BackendEvent
    data class Message(val text: String) : BackendEvent
}

@HiltViewModel
class BackendViewModel @Inject constructor(
    private val repository: BackendRepository,
    private val modes: StorageModeRepository,
    private val backups: LocalBackupRepository,
    private val dataChanges: DataChangeNotifier,
    private val expirySettings: ExpirySettingsRepository,
    private val authRepository: AuthRepository,
    private val remoteImport: RemoteImportRepository,
    private val sync: io.suirenx.core.domain.RemoteSyncRepository,
    private val schedule: io.suirenx.core.domain.SyncScheduleRepository,
) : ViewModel() {
    val uiState: StateFlow<BackendUiState>
        field = MutableStateFlow(BackendUiState())
    val events: SharedFlow<BackendEvent>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    private var connectionTestJob: kotlinx.coroutines.Job? = null
    init {
        viewModelScope.launch { authRepository.accounts.collect { value -> uiState.update { it.copy(accounts = value) } } }
        viewModelScope.launch { schedule.schedule.collect { value -> uiState.update { it.copy(syncSchedule = value) } } }
        viewModelScope.launch { sync.status.collect { value -> uiState.update { it.copy(syncStatus = value) } } }
        viewModelScope.launch {
            repository.settings.collect { settings ->
                uiState.update {
                    if (it.settings?.activeUrl != settings?.activeUrl) it.copy(
                        settings = settings, authUsername = "", authPassword = "", authError = null,
                        error = null, migrationError = null, migrationPreview = null, migrationResult = null,
                    ) else it.copy(settings = settings)
                }
                authRepository.initialize()
            }
        }
        viewModelScope.launch {
            modes.mode.collect { mode -> uiState.update { it.copy(mode = mode) } }
        }
        viewModelScope.launch {
            expirySettings.soonDays.collect { days -> uiState.update { it.copy(soonDays = days) } }
        }
        viewModelScope.launch {
            authRepository.state.collect { auth -> uiState.update { it.copy(auth = auth) } }
        }
        viewModelScope.launch { authRepository.initialize() }
        viewModelScope.launch { dataChanges.events.collect { refreshLocalSummary(); sync.refreshStatus() } }
        refreshLocalSummary()
        viewModelScope.launch { sync.refreshStatus() }
        load()
    }

    fun load() = perform {
        repository.initialize().fold(
            onSuccess = {
                expirySettings.initialize().fold(
                    onSuccess = {
                        modes.initialize().fold(
                            onSuccess = {
                                // Preserve existing remote users when upgrading from a
                                // build that had no explicit mode preference.
                                if (modes.mode.value == null && repository.settings.value?.activeUrl != null) {
                                    modes.select(StorageMode.Remote)
                                } else Result.success(Unit)
                            },
                            onFailure = { Result.failure(it) },
                        )
                    },
                    onFailure = { Result.failure(it) },
                )
            },
            onFailure = { Result.failure(it) },
        )
    }
    fun syncNow() {
        if (uiState.value.syncStatus.syncing) return
        viewModelScope.launch {
            // The sync repository owns request errors; do not retain a second copy
            // in connection-form state after this server is removed.
            sync.retry()
            dataChanges.notifyChanged()
        }
    }
    fun selectSyncSchedule(value: io.suirenx.core.domain.SyncSchedule) { schedule.select(value) }

    fun onAddressChanged(value: String) {
        connectionTestJob?.cancel()
        uiState.update { it.copy(address = value, error = null, testingConnection = false, connectionTest = null) }
    }
    fun openServer(url: String) {
        connectionTestJob?.cancel()
        val server = uiState.value.settings?.servers?.find { it.url == url }
        uiState.update { it.copy(inspectedServerUrl = url, editingServerUrl = url,
            address = url, name = server?.name.orEmpty(), authUsername = it.accounts[url]?.username.orEmpty(), authPassword = "", authError = null,
            error = null, connectionTest = null, testingConnection = false) }
    }
    fun editServer(url: String?) {
        connectionTestJob?.cancel()
        val server = uiState.value.settings?.servers?.find { it.url == url }
        uiState.update { it.copy(editingServerUrl = url, address = server?.url.orEmpty(), name = server?.name.orEmpty(),
            error = null, connectionTest = null, testingConnection = false) }
    }
    fun testConnection(address: String) {
        if (uiState.value.testingConnection) return
        uiState.update { it.copy(testingConnection = true, connectionTest = null) }
        connectionTestJob = viewModelScope.launch {
            repository.testConnection(address).fold(
                onSuccess = { result -> uiState.update { it.copy(testingConnection = false, connectionTest = result, connectionTestFailed = false) } },
                onFailure = { error -> uiState.update { it.copy(testingConnection = false, connectionTest = error.message, connectionTestFailed = true) } },
            )
        }
    }
    fun onNameChanged(value: String) { uiState.update { it.copy(name = value, error = null) } }
    fun save() {
        val state = uiState.value
        perform {
            val editingCurrent = state.editingServerUrl != null && state.editingServerUrl == state.settings?.activeUrl
            if (editingCurrent && state.address != state.editingServerUrl) sync.cancelCurrent()
            repository.saveServer(state.editingServerUrl, state.address, state.name).mapCatching { url ->
                if (editingCurrent && url != state.editingServerUrl) modes.select(StorageMode.Local).getOrThrow()
                authRepository.initialize().getOrThrow()
                uiState.update { it.copy(inspectedServerUrl = url, editingServerUrl = url, address = url, serverSavedVersion = it.serverSavedVersion + 1, connectionTest = null) }
            }
        }
    }
    fun select(url: String) = perform {
        sync.cancelCurrent()
        modes.select(StorageMode.Local).getOrThrow()
        repository.select(url)
    }
    fun removeServer(url: String) = perform {
        val removingCurrent = repository.settings.value?.activeUrl == url
        if (removingCurrent) {
            sync.cancelCurrent()
            modes.select(StorageMode.Local).getOrThrow()
        }
        repository.remove(url).onSuccess {
            authRepository.initialize()
            uiState.update {
                it.copy(
                    authPassword = "", authError = null, error = null,
                    migrationError = if (removingCurrent) null else it.migrationError,
                    migrationPreview = if (removingCurrent) null else it.migrationPreview,
                    migrationResult = if (removingCurrent) null else it.migrationResult,
                )
            }
        }
    }

    fun useLocal() = perform {
        sync.cancelCurrent()
        modes.select(StorageMode.Local)
    }
    fun useRemote() = previewMigration()
    fun cancelSync() { sync.cancelCurrent(); message("已停止此次请求，待同步数据保留") }
    fun message(text: String) { uiState.update { it.copy(notice = text) } }
    fun refreshLocalSummary() {
        viewModelScope.launch {
            backups.snapshot().onSuccess { snapshot ->
                uiState.update { it.copy(localSummary = BackupSummary(snapshot.assets.size, snapshot.expiryItems.size)) }
            }
        }
    }
    fun resolveConflict(id: String, expiry: Boolean, resolution: io.suirenx.core.domain.SyncConflictResolution) {
        if (uiState.value.conflictBusy) return
        uiState.update { it.copy(conflictBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = if (expiry) sync.resolveExpiryConflict(id, resolution) else sync.resolveConflict(id, resolution)
                result.fold(onSuccess = { message("冲突已处理，其他记录继续同步"); schedule.onLocalChange() },
                    onFailure = { error -> uiState.update { it.copy(error = error.message ?: "处理失败，请重试") } })
            } finally { uiState.update { it.copy(conflictBusy = false) } }
        }
    }

    fun previewMigration() {
        if (!uiState.value.auth.authenticated || uiState.value.migrationBusy) return
        uiState.update { it.copy(migrationBusy = true, migrationError = null, migrationResult = null) }
        viewModelScope.launch {
            remoteImport.preview().fold(
                onSuccess = { preview -> uiState.update { it.copy(migrationBusy = false, migrationPreview = preview) } },
                onFailure = { error -> uiState.update { it.copy(migrationBusy = false, migrationError = error.message ?: "无法读取本地数据或服务器数据") } },
            )
        }
    }

    fun cancelMigration() = uiState.update { it.copy(migrationPreview = null) }

    fun confirmMigration() {
        if (uiState.value.migrationBusy || uiState.value.migrationPreview == null) return
        uiState.update { it.copy(migrationBusy = true, migrationError = null, migrationPreview = null) }
        viewModelScope.launch {
            try {
            guarded {
                modes.select(StorageMode.Remote).getOrThrow()
                remoteImport.import()
            }.fold(
                onSuccess = { result ->
                    uiState.update { it.copy(migrationBusy = false, migrationPreview = null, migrationResult = result) }
                    dataChanges.notifyChanged()
                    schedule.onLocalChange()
                },
                onFailure = { error -> uiState.update { it.copy(migrationBusy = false, migrationError = error.message ?: "迁移失败；本地数据未改变") } },
            )
            } finally { uiState.update { it.copy(migrationBusy = false) } }
        }
    }

    fun onAuthUsernameChanged(value: String) { uiState.update { it.copy(authUsername = value, authError = null) } }
    fun onAuthPasswordChanged(value: String) { uiState.update { it.copy(authPassword = value, authError = null) } }
    fun resetAuthForm() {
        if (!uiState.value.authBusy) uiState.update { it.copy(authPassword = "", authError = null) }
    }
    fun register(confirmation: String) {
        val state = uiState.value
        if (state.authBusy || state.busy) return
        accountValidation(state.authUsername, state.authPassword, confirmation)?.let { reason ->
            uiState.update { it.copy(authError = reason) }; return
        }
        authenticate { url, username, password -> authRepository.registerAt(url, username, password) }
    }
    fun login() = authenticate { url, username, password -> authRepository.loginAt(url, username, password) }
    fun logout() {
        val target = uiState.value.inspectedServerUrl ?: uiState.value.settings?.activeUrl ?: return
        if (uiState.value.authBusy || uiState.value.busy || uiState.value.migrationBusy) return
        uiState.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            guarded {
                if (target == repository.settings.value?.activeUrl) {
                    sync.cancelCurrent(); modes.select(StorageMode.Local).getOrThrow()
                }
                authRepository.logoutAt(target)
            }.fold(
                onSuccess = { uiState.update { it.copy(authBusy = false, authUsername = "", authPassword = "", authError = null,
                    error = null, migrationError = null, migrationPreview = null, migrationResult = null) } },
                onFailure = { error -> uiState.update { it.copy(authBusy = false, authError = error.message ?: "退出登录失败") } },
            )
        }
    }

    private fun authenticate(action: suspend (String, String, String) -> Result<Unit>) {
        val state = uiState.value
        val target = state.inspectedServerUrl ?: state.settings?.activeUrl ?: return
        if (state.authBusy || state.busy) return
        accountValidation(state.authUsername, state.authPassword)?.let { reason ->
            uiState.update { it.copy(authError = reason) }; return
        }
        uiState.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            guarded {
                if (target == repository.settings.value?.activeUrl) {
                    sync.cancelCurrent(); modes.select(StorageMode.Local).getOrThrow()
                }
                action(target, state.authUsername, state.authPassword)
            }.fold(
                onSuccess = { uiState.update { it.copy(authBusy = false, authPassword = "", authError = null,
                    migrationError = null, error = null, authSuccessVersion = it.authSuccessVersion + 1) } },
                onFailure = { error -> uiState.update { it.copy(authBusy = false, authError = error.message ?: "账号操作失败") } },
            )
        }
    }

    fun onSoonDaysChanged(days: Int) {
        if (days !in 1..30) return
        viewModelScope.launch { expirySettings.selectSoonDays(days) }
    }

    fun exportBackup() {
        if (uiState.value.backupBusy) return
        uiState.update { it.copy(backupBusy = true, backupError = null) }
        viewModelScope.launch {
            backups.export().fold(
                onSuccess = { content ->
                    uiState.update { it.copy(backupBusy = false) }
                    events.tryEmit(BackendEvent.ExportBackup(content))
                },
                onFailure = { error ->
                    uiState.update { it.copy(backupBusy = false, backupError = error.message ?: "备份失败，请重试") }
                },
            )
        }
    }

    fun inspectBackup(content: String) {
        if (uiState.value.backupBusy) return
        uiState.update { it.copy(backupBusy = true, backupError = null) }
        viewModelScope.launch {
            backups.inspect(content).fold(
                onSuccess = { summary -> uiState.update { it.copy(backupBusy = false, backupSummary = summary, backupContent = content) } },
                onFailure = { error -> uiState.update { it.copy(backupBusy = false, backupError = error.message ?: "备份文件无效") } },
            )
        }
    }

    fun cancelRestore() = uiState.update { it.copy(backupSummary = null, backupContent = null) }

    fun confirmRestore() {
        val content = uiState.value.backupContent ?: return
        if (uiState.value.backupBusy) return
        uiState.update { it.copy(backupBusy = true, backupError = null) }
        viewModelScope.launch {
            backups.restore(content).fold(
                onSuccess = { summary ->
                    uiState.update { it.copy(backupBusy = false, backupSummary = null, backupContent = null) }
                    dataChanges.notifyChanged()
                    schedule.onLocalChange()
                    events.tryEmit(BackendEvent.Message("已恢复 ${summary.assetCount} 件资产、${summary.expiryItemCount} 件用品"))
                },
                onFailure = { error -> uiState.update { it.copy(backupBusy = false, backupError = error.message ?: "恢复失败，原数据未改变") } },
            )
        }
    }

    private suspend fun <T> guarded(action: suspend () -> Result<T>): Result<T> = try {
        action()
    } catch (error: kotlinx.coroutines.CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }

    private fun perform(action: suspend () -> Result<Unit>) {
        if (uiState.value.busy || uiState.value.authBusy || uiState.value.migrationBusy) return
        uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            guarded(action).fold(
                onSuccess = {
                    uiState.update { it.copy(busy = false) }
                },
                onFailure = { error ->
                    uiState.update { it.copy(busy = false, error = error.message ?: "无法读取或保存地址，请重试") }
                },
            )
        }
    }
}
