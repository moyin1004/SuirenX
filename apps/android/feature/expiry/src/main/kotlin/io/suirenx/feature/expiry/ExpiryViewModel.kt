package io.suirenx.feature.expiry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.ExpiryRepository
import io.suirenx.core.domain.DataChangeNotifier
import io.suirenx.core.domain.ExpirySettingsRepository
import io.suirenx.core.domain.RemoteSyncRepository
import io.suirenx.core.domain.RemoteSyncStatus
import io.suirenx.core.domain.SyncConflictResolution
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.ExpiryBucket
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpiryEditorState(
    val id: String? = null,
    val name: String = "",
    val category: String = "日用品",
    val packageExpiryDate: String = LocalDate.now().toString(),
    val openedDate: String = "",
    val openedValidityDays: String = "",
    val location: String = "",
    val notes: String = "",
    val error: String? = null,
)

data class ExpiryUiState(
    val deleteConfirmation: Boolean = false,
    val deleteError: String? = null,
    val items: List<ExpiryItem> = emptyList(),
    val filter: ExpiryBucket? = null,
    val isArchivedFilter: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val editor: ExpiryEditorState? = null,
    val detail: ExpiryItem? = null,
    val busy: Boolean = false,
    val soonDays: Int = 7,
    val syncStatus: RemoteSyncStatus = RemoteSyncStatus(),
    val remoteMode: Boolean = false,
) {
    val expiredCount get() = items.count { it.bucket(LocalDate.now(), soonDays) == ExpiryBucket.Expired }
    val visibleItems get() = items.filter { item ->
        if (isArchivedFilter) item.archivedAt != null
        else item.archivedAt == null && (filter == null || item.bucket(LocalDate.now(), soonDays) == filter ||
            (filter == ExpiryBucket.ExpiringSoon && item.bucket(LocalDate.now(), soonDays) == ExpiryBucket.DueToday))
    }.sortedWith(compareBy<ExpiryItem> { it.bucket(LocalDate.now(), soonDays).ordinal }.thenBy { it.actualExpiryDate })
}

@HiltViewModel
class ExpiryViewModel @Inject constructor(
    private val repository: ExpiryRepository,
    private val changes: DataChangeNotifier,
    private val settings: ExpirySettingsRepository,
    private val remoteSync: RemoteSyncRepository,
    private val modes: StorageModeRepository,
) : ViewModel() {
    val uiState: StateFlow<ExpiryUiState>
        field = MutableStateFlow(ExpiryUiState())

    init {
        viewModelScope.launch {
            settings.initialize()
            settings.soonDays.collect { days -> uiState.update { it.copy(soonDays = days) } }
        }
        refresh()
        viewModelScope.launch { changes.events.collect { refresh() } }
        viewModelScope.launch { remoteSync.status.collect { status -> uiState.update { it.copy(syncStatus = status) } } }
        viewModelScope.launch { modes.mode.collect { mode -> uiState.update { it.copy(remoteMode = mode == io.suirenx.core.model.StorageMode.Remote) } } }
    }

    fun setSoonDays(days: Int) {
        viewModelScope.launch { settings.selectSoonDays(days) }
    }

    fun refresh() {
        viewModelScope.launch {
            uiState.update { it.copy(loading = true, error = null) }
            repository.list(includeArchived = true).fold(
                onSuccess = { items -> uiState.update { it.copy(items = items, loading = false) } },
                onFailure = { error -> uiState.update { it.copy(loading = false, error = error.message ?: "无法读取用品") } },
            )
            remoteSync.refreshStatus()
        }
    }
    fun selectFilter(filter: ExpiryBucket?) = uiState.update { it.copy(filter = filter, isArchivedFilter = false) }
    fun selectArchived() = uiState.update { it.copy(isArchivedFilter = true, filter = null) }
    fun openNew() = uiState.update { it.copy(editor = ExpiryEditorState()) }
    fun openEdit(item: ExpiryItem) = uiState.update { it.copy(detail = null, editor = ExpiryEditorState(item.id, item.name, item.category, item.packageExpiryDate.toString(), item.openedDate?.toString().orEmpty(), item.openedValidityDays?.toString().orEmpty(), item.location, item.notes)) }
    fun closeEditor() = uiState.update { it.copy(editor = null) }
    fun openDetail(item: ExpiryItem) = uiState.update { it.copy(detail = item) }
    fun closeDetail() = uiState.update { it.copy(detail = null) }
    fun editName(value: String) = edit { it.copy(name = value, error = null) }
    fun editCategory(value: String) = edit { it.copy(category = value, error = null) }
    fun editPackageExpiry(value: String) = edit { it.copy(packageExpiryDate = value, error = null) }
    fun editOpenedDate(value: String) = edit { it.copy(openedDate = value, error = null) }
    fun editOpenedDays(value: String) = edit { it.copy(openedValidityDays = value, error = null) }
    fun editLocation(value: String) = edit { it.copy(location = value, error = null) }
    fun editNotes(value: String) = edit { it.copy(notes = value, error = null) }
    fun saveEditor() {
        val editor = uiState.value.editor ?: return
        val packageDate: LocalDate
        val openedDate: LocalDate?
        val openedDays: Int?
        try {
            require(editor.name.isNotBlank()) { "请输入用品名称" }
            packageDate = LocalDate.parse(editor.packageExpiryDate)
            openedDate = editor.openedDate.trim().takeIf(String::isNotEmpty)?.let(LocalDate::parse)
            openedDays = editor.openedValidityDays.trim().takeIf(String::isNotEmpty)?.toInt()?.also { require(it > 0) }
            require((openedDate == null) == (openedDays == null)) { "开封日与开封后有效天数需要成对填写" }
            require(openedDate == null || !openedDate.isAfter(LocalDate.now())) { "开封日不能在未来" }
            require(editor.notes.length <= 2000) { "备注不能超过 2000 个字符" }
        } catch (_: Exception) {
            uiState.update { it.copy(editor = editor.copy(error = "请检查名称、日期和开封期限")) }
            return
        }
        uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val draft = NewExpiryItem(editor.name, editor.category, packageDate, openedDate, openedDays, editor.location, editor.notes)
            val result = editor.id?.let { repository.update(it, draft) } ?: repository.create(draft)
            result.fold(
                onSuccess = { uiState.update { it.copy(editor = null, busy = false) }; changes.notifyChanged(); refresh() },
                onFailure = { error -> uiState.update { it.copy(busy = false, editor = editor.copy(error = error.message ?: "保存失败")) } },
            )
        }
    }
    fun updateStatus(status: ExpiryItemStatus) = mutate { item -> repository.updateStatus(item.id, status) }
    fun archive(archive: Boolean) = mutate { item -> repository.updateArchive(item.id, archive) }
    fun requestDelete() {
        if (!uiState.value.busy && uiState.value.detail != null) uiState.update { it.copy(deleteConfirmation = true, deleteError = null) }
    }
    fun dismissDelete() {
        if (!uiState.value.busy) uiState.update { it.copy(deleteConfirmation = false, deleteError = null) }
    }
    fun confirmDelete() {
        val item = uiState.value.detail ?: return
        if (uiState.value.busy || !uiState.value.deleteConfirmation) return
        uiState.update { it.copy(busy = true, deleteError = null) }
        viewModelScope.launch {
            try {
                repository.delete(item.id).fold(
                    onSuccess = {
                        uiState.update { it.copy(detail = null, deleteConfirmation = false) }
                        changes.notifyChanged()
                        refresh()
                    },
                    onFailure = { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        uiState.update { it.copy(deleteError = error.message ?: "删除失败，请重试") }
                    },
                )
            } finally { uiState.update { it.copy(busy = false) } }
        }
    }
    fun retrySync() {
        viewModelScope.launch { remoteSync.retry(); refresh() }
    }
    fun resolveSyncConflict(id: String, resolution: SyncConflictResolution) {
        viewModelScope.launch { remoteSync.resolveExpiryConflict(id, resolution); refresh() }
    }
    private fun mutate(action: suspend (ExpiryItem) -> Result<ExpiryItem>) {
        val item = uiState.value.detail ?: return
        if (uiState.value.busy) return
        uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            action(item).fold(
                onSuccess = { uiState.update { it.copy(detail = null, busy = false) }; changes.notifyChanged(); refresh() },
                onFailure = { error -> uiState.update { it.copy(busy = false, error = error.message ?: "操作失败") } },
            )
        }
    }
    private fun edit(transform: (ExpiryEditorState) -> ExpiryEditorState) { uiState.value.editor?.let { current -> uiState.update { it.copy(editor = transform(current)) } } }
}
