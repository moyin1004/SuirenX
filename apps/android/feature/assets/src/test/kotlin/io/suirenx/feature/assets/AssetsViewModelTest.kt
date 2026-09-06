package io.suirenx.feature.assets

import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.domain.GetAssetsUseCase
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetArchiveUseCase
import io.suirenx.core.domain.UpdateAssetStatusUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.BackendSettings
import io.suirenx.core.model.NewAsset
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun overviewCoversAllAssetsButDailyCostOnlyActive() = runTest(dispatcher) {
        val repo = FakeRepository().apply {
            seed(Asset("a1", "MacBook", 1_000_000, LocalDate.now(), AssetStatus.Active, "", 10, 5_000))
            seed(Asset("a2", "Kindle", 300_000, LocalDate.now(), AssetStatus.Retired, "", 300, 1_000))
        }
        val vm = AssetsViewModel(GetAssetsUseCase(repo), FakeBackends(), AssetChangeNotifier())
        advanceUntilIdle()

        val overview = vm.uiState.value.overview
        assertEquals(1_300_000L, overview.totalPriceCents)
        assertEquals(5_000L, overview.totalDailyCostCents)
        assertEquals(1, overview.activeCount)
        assertEquals(1, overview.retiredCount)
        assertEquals(2, overview.totalCount)
    }

    @Test fun filterChipsApplyLocallyWithoutRefetch() = runTest(dispatcher) {
        val repo = FakeRepository().apply {
            seed(Asset("a1", "One", 100, LocalDate.now(), AssetStatus.Active, "", 1, 100))
            seed(Asset("a2", "Two", 200, LocalDate.now(), AssetStatus.Active, "", 1, 200))
            seed(Asset("a3", "Three", 300, LocalDate.now(), AssetStatus.Retired, "", 1, 300))
        }
        val vm = AssetsViewModel(GetAssetsUseCase(repo), FakeBackends(), AssetChangeNotifier())
        advanceUntilIdle()
        val callsAfterLoad = repo.listCalls

        vm.onFilterSelected(AssetFilter.Retired)
        advanceUntilIdle()
        assertEquals(listOf("Three"), vm.uiState.value.visibleAssets.map { it.name })
        assertEquals(callsAfterLoad, repo.listCalls)

        vm.onFilterSelected(AssetFilter.Active)
        advanceUntilIdle()
        assertEquals(listOf("One", "Two"), vm.uiState.value.visibleAssets.map { it.name })
        // The overview stays global regardless of the selected chip.
        assertEquals(3, vm.uiState.value.overview.totalCount)
        assertEquals(callsAfterLoad, repo.listCalls)
    }

    @Test fun changeNotifierTriggersRefresh() = runTest(dispatcher) {
        val repo = FakeRepository().apply {
            seed(Asset("a1", "One", 100, LocalDate.now(), AssetStatus.Active, "", 1, 100))
        }
        val notifier = AssetChangeNotifier()
        val vm = AssetsViewModel(GetAssetsUseCase(repo), FakeBackends(), notifier)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.assets.size)

        repo.seed(Asset("a2", "Two", 200, LocalDate.now(), AssetStatus.Active, "", 1, 200))
        notifier.notifyAssetChanged()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.assets.size)
    }

    @Test fun switchingBackendReloadsAndResetsFilter() = runTest(dispatcher) {
        val repo = FakeRepository()
        val backends = FakeBackends()
        val vm = AssetsViewModel(GetAssetsUseCase(repo), backends, AssetChangeNotifier())
        advanceUntilIdle()
        val callsAfterFirstLoad = repo.listCalls
        vm.onFilterSelected(AssetFilter.Retired)
        advanceUntilIdle()

        backends.settings.value = BackendSettings(activeUrl = "https://second.example/")
        advanceUntilIdle()

        // Switching backend cancels the old state and reloads from scratch.
        assertTrue(repo.listCalls > callsAfterFirstLoad)
        assertEquals(AssetFilter.All, vm.uiState.value.selectedFilter)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun retiringFromDetailRefreshesActiveFilterAndOverview() = runTest(dispatcher) {
        val asset = Asset("a1", "Keyboard", 10000, LocalDate.of(2020, 1, 1), AssetStatus.Active, "", 5, 2000)
        val repo = FakeRepository().apply { seed(asset) }
        val notifier = AssetChangeNotifier()
        val list = AssetsViewModel(GetAssetsUseCase(repo), FakeBackends(), notifier)
        val detail = AssetDetailViewModel(GetAssetUseCase(repo), notifier, UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        detail.load(asset.id)
        advanceUntilIdle()
        list.onFilterSelected(AssetFilter.Active)
        assertEquals(1, list.uiState.value.visibleAssets.size)
        detail.openStatusDialog()
        detail.changeRetiredDate("2020-01-03")
        detail.saveStatus()
        advanceUntilIdle()
        assertEquals(AssetFilter.Active, list.uiState.value.selectedFilter)
        assertTrue(list.uiState.value.visibleAssets.isEmpty())
        assertEquals(0, list.uiState.value.overview.activeCount)
        assertEquals(1, list.uiState.value.overview.retiredCount)
        assertEquals(10000L, list.uiState.value.overview.totalPriceCents)
        assertEquals(0L, list.uiState.value.overview.totalDailyCostCents)
        detail.openStatusDialog()
        detail.saveStatus()
        advanceUntilIdle()
        assertEquals(1, list.uiState.value.visibleAssets.size)
        assertEquals(1, list.uiState.value.overview.activeCount)
        assertEquals(2000L, list.uiState.value.overview.totalDailyCostCents)
    }

    @Test fun archiveFilterAndOverviewRefreshAfterArchiveAndRestore() = runTest(dispatcher) {
        val asset = Asset("a1", "Keyboard", 10000, LocalDate.of(2020, 1, 1), AssetStatus.Active, "", 5, 2000)
        val repo = FakeRepository().apply { seed(asset) }
        val notifier = AssetChangeNotifier()
        val list = AssetsViewModel(GetAssetsUseCase(repo), FakeBackends(), notifier)
        val detail = AssetDetailViewModel(GetAssetUseCase(repo), notifier, UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        detail.load(asset.id)
        advanceUntilIdle()
        detail.openArchiveDialog()
        detail.saveArchive()
        advanceUntilIdle()
        assertTrue(list.uiState.value.visibleAssets.isEmpty())
        assertEquals(0L, list.uiState.value.overview.totalPriceCents)
        assertEquals(0L, list.uiState.value.overview.totalDailyCostCents)
        assertEquals(0, list.uiState.value.overview.totalCount)
        list.onFilterSelected(AssetFilter.Archived)
        assertEquals(1, list.uiState.value.visibleAssets.size)
        assertEquals(0L, list.uiState.value.overview.totalPriceCents)
        detail.openArchiveDialog()
        detail.saveArchive()
        advanceUntilIdle()
        assertEquals(AssetFilter.Archived, list.uiState.value.selectedFilter)
        assertTrue(list.uiState.value.visibleAssets.isEmpty())
        assertEquals(10000L, list.uiState.value.overview.totalPriceCents)
        assertEquals(2000L, list.uiState.value.overview.totalDailyCostCents)
        list.onFilterSelected(AssetFilter.All)
        assertEquals(listOf(asset), list.uiState.value.visibleAssets)
    }

    private class FakeBackends : BackendRepository {
        override val settings = MutableStateFlow<BackendSettings?>(BackendSettings(activeUrl = "https://first.example/"))
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun saveAndSelect(address: String, name: String) = Result.success(Unit)
        override suspend fun select(url: String) = Result.success(Unit)
    }

    internal class FakeRepository : AssetRepository {
        var createCalls = 0
        var updateCalls = 0
        var statusCalls = 0
        var archiveCalls = 0
        var listCalls = 0
        var fail = false
        var pending: CompletableDeferred<Unit>? = null
        private val assets = mutableListOf<Asset>()

        fun seed(asset: Asset) { assets += asset }

        fun replace(asset: Asset) {
            assets.removeAll { it.id == asset.id }
            assets += asset
        }

        override suspend fun getAssets(status: AssetStatus?, includeArchived: Boolean): Result<List<Asset>> {
            listCalls++
            return Result.success(assets.filter { (status == null || it.status == status) && (includeArchived || !it.isArchived) })
        }

        override suspend fun getAsset(id: String): Result<Asset> {
            if (fail) return Result.failure(IllegalStateException("offline"))
            return assets.firstOrNull { it.id == id }
                ?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException("asset $id not found"))
        }

        override suspend fun createAsset(asset: NewAsset): Result<Asset> {
            createCalls++
            pending?.await()
            if (fail) return Result.failure(IllegalStateException("offline"))
            val saved = Asset(
                id = "created", name = asset.name, priceCents = asset.priceCents,
                purchaseDate = asset.purchaseDate, status = AssetStatus.Active,
                imageUrl = "", heldDays = 1, dailyCostCents = asset.priceCents, iconKey = asset.iconKey,
            )
            assets += saved
            return Result.success(saved)
        }

        override suspend fun updateAssetArchive(id: String, archive: Boolean): Result<Asset> {
            archiveCalls++
            pending?.await()
            if (fail) return Result.failure(IllegalStateException("offline"))
            val index = assets.indexOfFirst { it.id == id }
            if (index < 0) return Result.failure(NoSuchElementException("missing"))
            val updated = assets[index].copy(archivedAt = if (archive) java.time.Instant.now() else null)
            assets[index] = updated
            return Result.success(updated)
        }

        override suspend fun updateAssetStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> {
            statusCalls++
            pending?.await()
            if (fail) return Result.failure(IllegalStateException("offline"))
            val index = assets.indexOfFirst { it.id == id }
            if (index < 0) return Result.failure(NoSuchElementException("asset $id not found"))
            val updated = assets[index].copy(status = status, retiredDate = retiredDate)
            assets[index] = updated
            return Result.success(updated)
        }

        override suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset> {
            updateCalls++
            if (fail) return Result.failure(IllegalStateException("offline"))
            val index = assets.indexOfFirst { it.id == id }
            if (index < 0) return Result.failure(NoSuchElementException("asset $id not found"))
            val updated = assets[index].copy(
                name = asset.name.trim(),
                priceCents = asset.priceCents,
                purchaseDate = asset.purchaseDate,
                iconKey = asset.iconKey,
                dailyCostCents = asset.priceCents,
            )
            assets[index] = updated
            return Result.success(updated)
        }
    }
}
