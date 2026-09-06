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
    val settings: BackendSettings? = null,
    val mode: StorageMode? = null,
    val address: String = "",
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
) : ViewModel() {
    val uiState: StateFlow<BackendUiState>
        field = MutableStateFlow(BackendUiState())
    val events: SharedFlow<BackendEvent>
        field = MutableSharedFlow(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                uiState.update { it.copy(settings = settings) }
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
    fun onAddressChanged(value: String) { uiState.update { it.copy(address = value, error = null) } }
    fun onNameChanged(value: String) { uiState.update { it.copy(name = value, error = null) } }
    fun save() {
        val state = uiState.value
        perform {
            repository.saveAndSelect(state.address, state.name).fold(
                onSuccess = { modes.select(StorageMode.Remote) },
                onFailure = { Result.failure(it) },
            )
        }
    }
    fun select(url: String) = perform {
        repository.select(url).fold(
            onSuccess = { modes.select(StorageMode.Remote) },
            onFailure = { Result.failure(it) },
        )
    }

    fun useLocal() = perform { modes.select(StorageMode.Local) }
    fun useRemote() = perform { modes.select(StorageMode.Remote) }

    fun previewMigration() {
        if (uiState.value.mode != StorageMode.Remote || !uiState.value.auth.authenticated || uiState.value.migrationBusy) return
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
        uiState.update { it.copy(migrationBusy = true, migrationError = null) }
        viewModelScope.launch {
            remoteImport.import().fold(
                onSuccess = { result ->
                    uiState.update { it.copy(migrationBusy = false, migrationPreview = null, migrationResult = result) }
                    dataChanges.notifyChanged()
                },
                onFailure = { error -> uiState.update { it.copy(migrationBusy = false, migrationError = error.message ?: "迁移失败；本地数据未改变") } },
            )
        }
    }

    fun onAuthUsernameChanged(value: String) { uiState.update { it.copy(authUsername = value, authError = null) } }
    fun onAuthPasswordChanged(value: String) { uiState.update { it.copy(authPassword = value, authError = null) } }
    fun register() = authenticate { username, password -> authRepository.register(username, password) }
    fun login() = authenticate { username, password -> authRepository.login(username, password) }
    fun logout() {
        if (uiState.value.authBusy) return
        uiState.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            authRepository.logout().fold(
                onSuccess = { uiState.update { it.copy(authBusy = false, authUsername = "", authPassword = "") } },
                onFailure = { error -> uiState.update { it.copy(authBusy = false, authError = error.message ?: "退出登录失败") } },
            )
        }
    }

    private fun authenticate(action: suspend (String, String) -> Result<Unit>) {
        val state = uiState.value
        if (state.authBusy || state.mode != StorageMode.Remote) return
        uiState.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            action(state.authUsername, state.authPassword).fold(
                onSuccess = { uiState.update { it.copy(authBusy = false, authPassword = "") } },
                onFailure = { error -> uiState.update { it.copy(authBusy = false, authError = error.message ?: "账号操作失败") } },
            )
        }
    }

    fun onSoonDaysChanged(days: Int) {
        if (days !in 1..30) return
        viewModelScope.launch { expirySettings.selectSoonDays(days) }
    }

    fun exportBackup() {
        if (uiState.value.mode != StorageMode.Local || uiState.value.backupBusy) return
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
        if (uiState.value.mode != StorageMode.Local || uiState.value.backupBusy) return
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
                    events.tryEmit(BackendEvent.Message("已恢复 ${summary.assetCount} 件资产；当前模式和服务器地址未改变"))
                },
                onFailure = { error -> uiState.update { it.copy(backupBusy = false, backupError = error.message ?: "恢复失败，原数据未改变") } },
            )
        }
    }

    private fun perform(action: suspend () -> Result<Unit>) {
        if (uiState.value.busy) return
        uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            action().fold(
                onSuccess = {
                    uiState.update { it.copy(busy = false, address = "", name = "") }
                },
                onFailure = { error ->
                    uiState.update { it.copy(busy = false, error = error.message ?: "无法读取或保存地址，请重试") }
                },
            )
        }
    }
}
