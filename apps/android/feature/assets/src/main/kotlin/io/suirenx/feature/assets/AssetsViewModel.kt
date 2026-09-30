package io.suirenx.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.domain.GetAssetsUseCase
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.domain.DataChangeNotifier
import io.suirenx.core.domain.RemoteSyncRepository
import io.suirenx.core.domain.RemoteSyncStatus
import io.suirenx.core.domain.SyncConflictResolution
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

enum class AssetSort(val label: String) {
    PurchaseDate("购买日期"),
    Price("购买金额"),
    DailyCost("日均成本"),
    HeldDays("持有天数"),
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
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val syncStatus: RemoteSyncStatus = RemoteSyncStatus(),
    val sort: AssetSort = AssetSort.PurchaseDate,
    val sortDescending: Boolean = true,
    val selectedTags: Set<String> = emptySet(),
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

    val availableTags: List<String>
        get() = assets.asSequence().flatMap { it.tags.asSequence() }.filter(String::isNotBlank).distinct().sorted().toList()

    val visibleAssets: List<Asset>
        get() = (when (selectedFilter) {
            AssetFilter.Archived -> assets.filter { it.isArchived }
            AssetFilter.All -> currentAssets
            else -> currentAssets.filter { it.status == selectedFilter.status }
        }).filter { asset -> selectedTags.all { it in asset.tags } }.let { filtered ->
            val comparator = when (sort) {
                AssetSort.PurchaseDate -> compareBy<Asset> { it.purchaseDate }
                AssetSort.Price -> compareBy { it.priceCents }
                AssetSort.DailyCost -> compareBy { it.dailyCostCents }
                AssetSort.HeldDays -> compareBy { it.heldDays }
            }
            if (sortDescending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
        }
}

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val getAssets: GetAssetsUseCase,
    private val backends: BackendRepository,
    private val changeNotifier: AssetChangeNotifier,
    private val modes: StorageModeRepository = UninitializedStorageModeRepository,
    private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
    private val remoteSync: RemoteSyncRepository = UninitializedRemoteSyncRepository,
) : ViewModel() {
    val uiState: StateFlow<AssetsUiState>
        field = MutableStateFlow(AssetsUiState())

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            remoteSync.status.collect { status -> uiState.update { it.copy(syncStatus = status) } }
        }
        viewModelScope.launch {
            changeNotifier.events.collect { refresh() }
        }
        viewModelScope.launch {
            dataChanges.events.collect { refresh() }
        }
    }

    fun onFilterSelected(filter: AssetFilter) {
        if (filter == uiState.value.selectedFilter) return
        uiState.update { it.copy(selectedFilter = filter) }
    }

    fun onSortSelected(sort: AssetSort) = uiState.update { it.copy(sort = sort) }
    fun toggleSortDirection() = uiState.update { it.copy(sortDescending = !it.sortDescending) }
    fun toggleTag(tag: String) = uiState.update {
        it.copy(selectedTags = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag)
    }
    fun clearTags() = uiState.update { it.copy(selectedTags = emptySet()) }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            uiState.update { current ->
                current.copy(
                    // Keep already-rendered data stable during background refresh.
                    isLoading = !current.hasLoaded,
                    isRefreshing = true,
                    errorMessage = null,
                )
            }
            val result = getAssets(null, includeArchived = true)
            ensureActive()
            result.fold(
                onSuccess = { assets ->
                    uiState.update { it.copy(assets = assets, isLoading = false, isRefreshing = false, hasLoaded = true) }
                },
                onFailure = {
                    uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "无法读取本机数据，请重试",
                        )
                    }
                },
            )
            remoteSync.refreshStatus()
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            remoteSync.retry().fold(
                onSuccess = { refresh() },
                onFailure = { refresh() },
            )
        }
    }

    fun resolveConflict(assetId: String, resolution: SyncConflictResolution) {
        viewModelScope.launch {
            remoteSync.resolveConflict(assetId, resolution).fold(
                onSuccess = { refresh() },
                onFailure = { refresh() },
            )
        }
    }
}

private object UninitializedStorageModeRepository : StorageModeRepository {
    override val mode = MutableStateFlow<io.suirenx.core.model.StorageMode?>(null)
    override suspend fun initialize() = Result.success(Unit)
    override suspend fun select(mode: io.suirenx.core.model.StorageMode) = Result.success(Unit)
}

private object UninitializedRemoteSyncRepository : RemoteSyncRepository {
    override val status = MutableStateFlow(RemoteSyncStatus())
    override suspend fun refreshStatus() = Result.success(Unit)
    override suspend fun retry() = Result.success(Unit)
    override suspend fun resolveConflict(id: String, resolution: SyncConflictResolution) = Result.success(Unit)
    override suspend fun resolveExpiryConflict(id: String, resolution: SyncConflictResolution) = Result.success(Unit)
}
