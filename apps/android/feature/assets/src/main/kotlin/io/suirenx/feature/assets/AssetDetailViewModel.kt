package io.suirenx.feature.assets

import io.suirenx.core.model.UserFacingErrors
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetArchiveUseCase
import io.suirenx.core.domain.UpdateAssetStatusUseCase
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val deleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val deleteError: String? = null,
    val isLoading: Boolean = true,
    val asset: Asset? = null,
    val errorMessage: String? = null,
    val statusTarget: AssetStatus? = null,
    val retiredDateInput: String = "",
    val statusError: String? = null,
    val isSavingStatus: Boolean = false,
    val archiveTarget: Boolean? = null,
    val archiveError: String? = null,
    val isSavingArchive: Boolean = false,
    val salePriceInput: String = "",
    val salePriceError: String? = null,
    val salePriceDirty: Boolean = false,
    val isSavingSalePrice: Boolean = false,
) {
    val isSaving: Boolean get() = isSavingStatus || isSavingArchive || isDeleting || isSavingSalePrice
}

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val getAsset: GetAssetUseCase,
    private val changeNotifier: AssetChangeNotifier,
    private val updateAssetStatus: UpdateAssetStatusUseCase,
    private val updateAssetArchive: UpdateAssetArchiveUseCase,
    private val modes: StorageModeRepository = NoopDetailModes,
    private val deleteAsset: io.suirenx.core.domain.DeleteAssetUseCase? = null,
    private val dataChanges: io.suirenx.core.domain.DataChangeNotifier = io.suirenx.core.domain.DataChangeNotifier(),
    private val updateAssetSalePrice: io.suirenx.core.domain.UpdateAssetSalePriceUseCase? = null,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState>
        field = MutableStateFlow(AssetDetailUiState())

    private var loadJob: Job? = null
    private var assetId: String? = null
    private var observedMode: io.suirenx.core.model.StorageMode? = null

    init {
        // The edit page slides up over this screen; when a save lands there we
        // silently refresh so the revealed detail already shows the new values.
        viewModelScope.launch {
            changeNotifier.events.collect {
                assetId?.let { fetch(it, silent = true) }
            }
        }
        viewModelScope.launch {
            dataChanges.events.collect { assetId?.let { fetch(it, silent = true) } }
        }
    }

    fun load(id: String) {
        if (id.isBlank() || (id == assetId && uiState.value.asset != null)) return
        assetId = id
        fetch(id)
    }

    fun retry() {
        val id = assetId ?: return
        assetId = null
        load(id)
    }

    fun requestDelete() {
        if (!uiState.value.isSaving) uiState.update { it.copy(deleteConfirmation = true, deleteError = null) }
    }

    fun dismissDelete() {
        if (!uiState.value.isSaving) uiState.update { it.copy(deleteConfirmation = false, deleteError = null) }
    }

    fun confirmDelete() {
        val asset = uiState.value.asset ?: return
        if (uiState.value.isSaving || !uiState.value.deleteConfirmation) return
        val action = deleteAsset ?: return
        loadJob?.cancel()
        uiState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                action(asset.id).fold(
                    onSuccess = {
                        assetId = null
                        uiState.update { it.copy(asset = null, deleted = true, deleteConfirmation = false) }
                        changeNotifier.notifyAssetChanged()
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        uiState.update { it.copy(deleteError = UserFacingErrors.codeOrFallback(error.message, "error_delete_failed")) }
                    },
                )
            } finally {
                uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun changeSalePrice(value: String) {
        if (!uiState.value.isSaving) uiState.update { it.copy(salePriceInput = value, salePriceError = null, salePriceDirty = true) }
    }

    fun saveSalePrice() {
        val state = uiState.value
        val asset = state.asset ?: return
        val action = updateAssetSalePrice ?: return
        if (state.isSaving || !asset.isArchived) return
        val cents = try { parseSalePrice(state.salePriceInput) } catch (error: IllegalArgumentException) {
            uiState.update { it.copy(salePriceError = UserFacingErrors.codeOrFallback(error.message, "error_operation_failed")) }; return
        }
        loadJob?.cancel()
        uiState.update { it.copy(isSavingSalePrice = true, salePriceError = null) }
        viewModelScope.launch {
            try {
                action(asset.id, cents).fold(
                    onSuccess = { saved ->
                        uiState.update { it.copy(asset = saved, salePriceDirty = false,
                            salePriceInput = saved.salePriceCents?.let { cents -> java.math.BigDecimal.valueOf(cents, 2).toPlainString() }.orEmpty()) }
                        changeNotifier.notifyAssetChanged()
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        uiState.update { it.copy(salePriceError = UserFacingErrors.codeOrFallback(error.message, "error_sale_price_save_failed")) }
                    },
                )
            } finally { uiState.update { it.copy(isSavingSalePrice = false) } }
        }
    }

    fun openStatusDialog() {
        val state = uiState.value
        val asset = state.asset ?: return
        if (state.isSaving || asset.isArchived) return
        uiState.update {
            it.copy(
                statusTarget = if (asset.status == AssetStatus.Active) AssetStatus.Retired else AssetStatus.Active,
                retiredDateInput = LocalDate.now().toString(),
                statusError = null,
            )
        }
    }

    fun dismissStatusDialog() {
        if (uiState.value.isSaving) return
        uiState.update { it.copy(statusTarget = null, statusError = null) }
    }

    fun changeRetiredDate(value: String) {
        if (uiState.value.isSaving) return
        uiState.update { it.copy(retiredDateInput = value, statusError = null) }
    }

    fun saveStatus() {
        val state = uiState.value
        val asset = state.asset ?: return
        val target = state.statusTarget ?: return
        if (state.isSaving || asset.isArchived) return
        val date = if (target == AssetStatus.Retired) {
            try {
                if (!state.retiredDateInput.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
                    uiState.update { it.copy(statusError = "error_date_invalid") }
                    return
                }
                LocalDate.parse(state.retiredDateInput)
            } catch (_: DateTimeParseException) {
                uiState.update { it.copy(statusError = "error_date_invalid") }
                return
            }
        } else null
        loadJob?.cancel()
        uiState.update { it.copy(isSavingStatus = true, statusError = null) }
        viewModelScope.launch {
            try {
                updateAssetStatus(asset, target, date).fold(
                    onSuccess = { saved ->
                        uiState.update {
                            it.copy(asset = saved, isSavingStatus = false, statusTarget = null, statusError = null)
                        }
                        changeNotifier.notifyAssetChanged()
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        uiState.update {
                            it.copy(
                                isSavingStatus = false,
                                statusError = if (error is IllegalArgumentException) UserFacingErrors.codeOrFallback(error.message, "error_operation_failed")
                                    else "error_asset_status_save_failed",
                            )
                        }
                    },
                )
            } finally {
                uiState.update { it.copy(isSavingStatus = false) }
            }
        }
    }

    fun openArchiveDialog() {
        val state = uiState.value
        val asset = state.asset ?: return
        if (state.isSaving) return
        uiState.update { it.copy(archiveTarget = !asset.isArchived, archiveError = null) }
    }

    fun dismissArchiveDialog() {
        if (uiState.value.isSaving) return
        uiState.update { it.copy(archiveTarget = null, archiveError = null) }
    }

    fun saveArchive() {
        val state = uiState.value
        val asset = state.asset ?: return
        val target = state.archiveTarget ?: return
        if (state.isSaving) return
        loadJob?.cancel()
        uiState.update { it.copy(isSavingArchive = true, archiveError = null) }
        viewModelScope.launch {
            try {
                updateAssetArchive(asset.id, target).fold(
                    onSuccess = { saved ->
                        uiState.update { it.copy(asset = saved, isSavingArchive = false, archiveTarget = null) }
                        changeNotifier.notifyAssetChanged()
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        uiState.update { it.copy(archiveError = "error_asset_archive_save_failed") }
                    },
                )
            } finally {
                uiState.update { it.copy(isSavingArchive = false) }
            }
        }
    }

    private fun fetch(id: String, silent: Boolean = false) {
        if (uiState.value.isSaving) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!silent) {
                uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            getAsset(id).fold(
                onSuccess = { asset ->
                    uiState.update {
                        it.copy(isLoading = false, asset = asset, errorMessage = null,
                            salePriceInput = if (it.asset?.id != asset.id || !it.salePriceDirty) asset.salePriceCents?.let { cents -> java.math.BigDecimal.valueOf(cents, 2).toPlainString() }.orEmpty() else it.salePriceInput)
                    }
                },
                onFailure = {
                    // Keep the already-visible asset during a background refresh.
                    if (!silent || uiState.value.asset == null) {
                        uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "error_asset_load_failed",
                            )
                        }
                    }
                },
            )
        }
    }
}

private object NoopDetailModes : StorageModeRepository {
    override val mode = MutableStateFlow<io.suirenx.core.model.StorageMode?>(null)
    override suspend fun initialize() = Result.success(Unit)
    override suspend fun select(mode: io.suirenx.core.model.StorageMode) = Result.success(Unit)
}

internal fun parseSalePrice(value: String): Long? {
    val amount = value.trim()
    if (amount.isEmpty()) return null
    require(amount.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) { "error_amount_invalid" }
    return try { java.math.BigDecimal(amount).movePointRight(2).longValueExact() }
    catch (_: ArithmeticException) { throw IllegalArgumentException("error_amount_too_large") }
}
