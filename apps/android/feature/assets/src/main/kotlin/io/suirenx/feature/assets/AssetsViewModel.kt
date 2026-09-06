package io.suirenx.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.domain.GetAssetsUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssetFilter(val label: String, val status: AssetStatus?) {
    All("全部", null),
    Active("服役中", AssetStatus.Active),
    Retired("已退役", AssetStatus.Retired),
    Archived("已归档", null),
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
) {
    private val currentAssets: List<Asset> get() = assets.filterNot { it.isArchived }

    // Archive is independent of lifecycle: archived records never contribute to
    // the everyday overview, even while browsing the archive filter.
    val overview: AssetOverview
        get() = AssetOverview(
            totalPriceCents = currentAssets.sumOf { it.priceCents },
            totalDailyCostCents = currentAssets.filter { it.status == AssetStatus.Active }.sumOf { it.dailyCostCents },
            activeCount = currentAssets.count { it.status == AssetStatus.Active },
            retiredCount = currentAssets.count { it.status == AssetStatus.Retired },
        )

    val visibleAssets: List<Asset>
        get() = when (selectedFilter) {
            AssetFilter.Archived -> assets.filter { it.isArchived }
            AssetFilter.All -> currentAssets
            else -> currentAssets.filter { it.status == selectedFilter.status }
        }
}

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val getAssets: GetAssetsUseCase,
    private val backends: BackendRepository,
    private val changeNotifier: AssetChangeNotifier,
) : ViewModel() {
    val uiState: StateFlow<AssetsUiState>
        field = MutableStateFlow(AssetsUiState())

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            backends.settings.map { it?.activeUrl }.distinctUntilChanged().collect { url ->
                refreshJob?.cancel()
                uiState.value = AssetsUiState(isLoading = url != null)
                if (url != null) refresh()
            }
        }
        viewModelScope.launch {
            changeNotifier.events.collect { refresh() }
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
            val result = getAssets(null, includeArchived = true)
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
