package io.suirenx.feature.assets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.CreateAssetUseCase
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetUseCase
import io.suirenx.core.domain.StorageModeRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetFormUiState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: Boolean = false,
    val name: String = "",
    val price: String = "",
    val purchaseDate: String = LocalDate.now().toString(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val iconKey: String = "devices",
    val isIconPickerOpen: Boolean = false,
    val purchaseChannel: String = "",
    val warrantyEndDate: String = "",
    val notes: String = "",
    val tags: String = "",
)

/**
 * Backs the full-screen create/edit page. The navigation route supplies the
 * optional `id` argument: `assets/new` has none (create mode), while
 * `assets/{id}/edit` prefills the existing asset.
 */
@HiltViewModel
class AssetFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createAsset: CreateAssetUseCase,
    private val updateAsset: UpdateAssetUseCase,
    getAsset: GetAssetUseCase,
    private val changeNotifier: AssetChangeNotifier,
    private val modes: StorageModeRepository = NoopFormModes,
) : ViewModel() {
    val uiState: StateFlow<AssetFormUiState>
        field = MutableStateFlow(AssetFormUiState())

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private var saveJob: Job? = null
    private val assetId: String? = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }

    init {
        viewModelScope.launch {
            modes.mode.collect { mode ->
                if (mode != null && mode != observedMode) {
                    observedMode = mode
                    if (uiState.value.isEdit || uiState.value.name.isNotBlank()) {
                        uiState.value = AssetFormUiState(errorMessage = "使用模式已切换，请重新打开表单")
                    }
                }
            }
        }
        val id = assetId
        if (id != null) {
            uiState.update { it.copy(isEdit = true, isLoading = true) }
            viewModelScope.launch {
                getAsset(id).fold(
                    onSuccess = { asset ->
                        uiState.update {
                            it.copy(
                                isLoading = false,
                                name = asset.name,
                                price = BigDecimal(asset.priceCents).movePointLeft(2).toPlainString(),
                                purchaseDate = asset.purchaseDate.toString(),
                                iconKey = asset.iconKey,
                                purchaseChannel = asset.purchaseChannel.orEmpty(),
                                warrantyEndDate = asset.warrantyEndDate?.toString().orEmpty(),
                                notes = asset.notes,
                                tags = asset.tags.joinToString(", "),
                            )
                        }
                    },
                    onFailure = {
                        uiState.update { it.copy(isLoading = false, loadError = true) }
                    },
                )
            }
        }
    }

    private var observedMode: io.suirenx.core.model.StorageMode? = null

    fun openIconPicker() = update { it.copy(isIconPickerOpen = true) }
    fun closeIconPicker() = update { it.copy(isIconPickerOpen = false) }
    fun onIconSelected(key: String) = update {
        it.copy(iconKey = key, isIconPickerOpen = false, errorMessage = null)
    }

    fun onNameChanged(value: String) = update { it.copy(name = value, errorMessage = null) }
    fun onPriceChanged(value: String) = update { it.copy(price = value, errorMessage = null) }
    fun onPurchaseDateChanged(value: String) = update { it.copy(purchaseDate = value, errorMessage = null) }
    fun onPurchaseChannelChanged(value: String) = update { it.copy(purchaseChannel = value, errorMessage = null) }
    fun onWarrantyEndDateChanged(value: String) = update { it.copy(warrantyEndDate = value, errorMessage = null) }
    fun onNotesChanged(value: String) = update { it.copy(notes = value, errorMessage = null) }
    fun onTagsChanged(value: String) = update { it.copy(tags = value, errorMessage = null) }

    private fun update(transform: (AssetFormUiState) -> AssetFormUiState) {
        uiState.update { state ->
            if (state.isSaving || state.isLoading) state else transform(state)
        }
    }

    fun save() {
        val state = uiState.value
        if (state.isSaving || state.isLoading || state.loadError) return
        val draft = try {
            AssetFormState(
                state.name, state.price, state.purchaseDate, iconKey = state.iconKey,
                purchaseChannel = state.purchaseChannel, warrantyEndDate = state.warrantyEndDate,
                notes = state.notes, tags = state.tags,
            ).toNewAsset()
        } catch (error: IllegalArgumentException) {
            uiState.update { it.copy(errorMessage = error.message) }
            return
        }
        uiState.update { it.copy(isSaving = true, errorMessage = null) }
        saveJob = viewModelScope.launch {
            val id = assetId
            val result = if (id != null) updateAsset(id, draft) else createAsset(draft)
            result.fold(
                onSuccess = {
                    changeNotifier.notifyAssetChanged()
                    _saved.tryEmit(Unit)
                },
                onFailure = { error ->
                    uiState.update {
                        it.copy(isSaving = false, errorMessage = "保存失败，请检查网络和服务后重试")
                    }
                },
            )
        }
    }
}

private object NoopFormModes : StorageModeRepository {
    override val mode = MutableStateFlow<io.suirenx.core.model.StorageMode?>(null)
    override suspend fun initialize() = Result.success(Unit)
    override suspend fun select(mode: io.suirenx.core.model.StorageMode) = Result.success(Unit)
}
