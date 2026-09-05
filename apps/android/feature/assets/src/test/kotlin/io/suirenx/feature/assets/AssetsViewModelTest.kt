package io.suirenx.feature.assets

import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.domain.GetAssetsUseCase
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

    private class FakeBackends : BackendRepository {
        override val settings = MutableStateFlow<BackendSettings?>(BackendSettings(activeUrl = "https://first.example/"))
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun saveAndSelect(address: String, name: String) = Result.success(Unit)
        override suspend fun select(url: String) = Result.success(Unit)
    }

    internal class FakeRepository : AssetRepository {
        var createCalls = 0
        var updateCalls = 0
        var listCalls = 0
        var fail = false
        var pending: CompletableDeferred<Unit>? = null
        private val assets = mutableListOf<Asset>()

        fun seed(asset: Asset) { assets += asset }

        fun replace(asset: Asset) {
            assets.removeAll { it.id == asset.id }
            assets += asset
        }

        override suspend fun getAssets(status: AssetStatus?): Result<List<Asset>> {
            listCalls++
            return Result.success(assets.filter { status == null || it.status == status })
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
                imageUrl = "", heldDays = 1, dailyCostCents = asset.priceCents,
            )
            assets += saved
            return Result.success(saved)
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
                dailyCostCents = asset.priceCents,
            )
            assets[index] = updated
            return Result.success(updated)
        }
    }
}
