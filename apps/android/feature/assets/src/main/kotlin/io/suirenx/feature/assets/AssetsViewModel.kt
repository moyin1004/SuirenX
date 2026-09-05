package io.suirenx.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.BackendRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ensureActive
import io.suirenx.core.domain.GetAssetsUseCase
import io.suirenx.core.domain.CreateAssetUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

enum class AssetFilter(val label: String, val status: AssetStatus?) {
    All("全部", null),
    Active("服役中", AssetStatus.Active),
    Retired("已退役", AssetStatus.Retired),
}

data class AssetOverview(
    val totalPriceCents: Long,
    val totalDailyCostCents: Long,
    val activeCount: Int,
    val retiredCount: Int,
) {
    val totalCount: Int = activeCount + retiredCount
}

data class AssetsUiState(
    val assets: List<Asset> = emptyList(),
    val selectedFilter: AssetFilter = AssetFilter.All,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: AssetFormState? = null,
) {
    // The overview always covers every asset, even while a status chip filters the grid.
    val overview: AssetOverview
        get() = AssetOverview(
            totalPriceCents = assets.sumOf { it.priceCents },
            // Retired assets no longer accrue daily cost.
            totalDailyCostCents = assets
                .filter { it.status == AssetStatus.Active }
                .sumOf { it.dailyCostCents },
            activeCount = assets.count { it.status == AssetStatus.Active },
            retiredCount = assets.count { it.status == AssetStatus.Retired },
        )

    // Filtering happens locally; the full list is fetched once so the overview
    // and every chip share the same source of truth.
    val visibleAssets: List<Asset>
        get() = selectedFilter.status
            ?.let { status -> assets.filter { it.status == status } }
            ?: assets
}

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val getAssets: GetAssetsUseCase,
    private val createAsset: CreateAssetUseCase,
    private val backends: BackendRepository,
    private val changeNotifier: AssetChangeNotifier,
) : ViewModel() {
    val uiState: StateFlow<AssetsUiState>
        field = MutableStateFlow(AssetsUiState())

    private var refreshJob: Job? = null

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            backends.settings.map { it?.activeUrl }.distinctUntilChanged().collect { url ->
                saveJob?.cancel()
                refreshJob?.cancel()
                uiState.value = AssetsUiState(isLoading = url != null)
                if (url != null) refresh()
            }
        }
        viewModelScope.launch {
            changeNotifier.events.collect { refresh() }
        }
    }

    fun openCreateForm() {
        if (uiState.value.form == null) uiState.update { it.copy(form = AssetFormState()) }
    }

    fun dismissCreateForm() {
        if (uiState.value.form?.isSaving != true) uiState.update { it.copy(form = null) }
    }

    fun onNameChanged(value: String) = updateForm { copy(name = value, errorMessage = null) }
    fun onPriceChanged(value: String) = updateForm { copy(price = value, errorMessage = null) }
    fun onPurchaseDateChanged(value: String) = updateForm { copy(purchaseDate = value, errorMessage = null) }

    private fun updateForm(transform: AssetFormState.() -> AssetFormState) {
        uiState.update { state ->
            val form = state.form
            if (form == null || form.isSaving) state else state.copy(form = form.transform())
        }
    }

    fun saveAsset() {
        val form = uiState.value.form ?: return
        if (form.isSaving) return
        val draft = try {
            form.toNewAsset()
        } catch (error: IllegalArgumentException) {
            updateForm { copy(errorMessage = error.message) }
            return
        }
        updateForm { copy(isSaving = true, errorMessage = null) }
        saveJob = viewModelScope.launch {
            val result = createAsset(draft)
            ensureActive()
            result.fold(
                onSuccess = { asset ->
                    refreshJob?.cancel()
                    uiState.update {
                        it.copy(
                            form = null,
                            selectedFilter = AssetFilter.All,
                            assets = listOf(asset) + it.assets.filterNot { existing -> existing.id == asset.id },
                        )
                    }
                    refresh()
                },
                onFailure = {
                    uiState.update { state ->
                        state.copy(form = state.form?.copy(
                            isSaving = false,
                            errorMessage = "保存失败，请检查网络和服务后重试",
                        ))
                    }
                },
            )
        }
    }

    fun onFilterSelected(filter: AssetFilter) {
        if (filter == uiState.value.selectedFilter) return
        uiState.update { it.copy(selectedFilter = filter) }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = getAssets(null)
            ensureActive()
            result.fold(
                onSuccess = { assets ->
                    uiState.update { it.copy(assets = assets, isLoading = false) }
                },
                onFailure = {
                    uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "无法连接后端，请检查网络或在设置中切换地址",
                        )
                    }
                },
            )
        }
    }
}
