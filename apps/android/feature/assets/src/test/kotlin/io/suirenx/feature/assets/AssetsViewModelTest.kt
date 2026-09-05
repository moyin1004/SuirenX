package io.suirenx.feature.assets

import io.suirenx.core.domain.BackendRepository
import io.suirenx.core.model.BackendSettings
import kotlinx.coroutines.flow.MutableStateFlow
import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.CreateAssetUseCase
import io.suirenx.core.domain.GetAssetsUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun preventsDuplicateSavesAndRefreshesAllAssets() = runTest(dispatcher) {
        val repo = FakeRepository()
        repo.pending = CompletableDeferred()
        val vm = AssetsViewModel(GetAssetsUseCase(repo), CreateAssetUseCase(repo), FakeBackends())
        advanceUntilIdle()
        vm.onFilterSelected(AssetFilter.Retired)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.onNameChanged("Keyboard")
        vm.onPriceChanged("19.99")
        vm.saveAsset()
        vm.saveAsset()
        vm.dismissCreateForm()
        runCurrent()
        assertTrue(vm.uiState.value.form!!.isSaving)
        assertEquals(1, repo.createCalls)
        repo.pending!!.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.uiState.value.form)
        assertEquals(AssetFilter.All, vm.uiState.value.selectedFilter)
        assertEquals(1999L, vm.uiState.value.assets.single().priceCents)
    }

    @Test fun failedSaveRetainsInputAndAllowsRetry() = runTest(dispatcher) {
        val repo = FakeRepository().apply { fail = true }
        val vm = AssetsViewModel(GetAssetsUseCase(repo), CreateAssetUseCase(repo), FakeBackends())
        advanceUntilIdle()
        vm.openCreateForm()
        vm.onNameChanged("Keyboard")
        vm.onPriceChanged("19.99")
        vm.saveAsset()
        advanceUntilIdle()
        assertEquals("Keyboard", vm.uiState.value.form!!.name)
        assertEquals("19.99", vm.uiState.value.form!!.price)
        assertFalse(vm.uiState.value.form!!.isSaving)
        assertNotNull(vm.uiState.value.form!!.errorMessage)
        repo.fail = false
        vm.saveAsset()
        advanceUntilIdle()
        assertNull(vm.uiState.value.form)
        assertEquals(2, repo.createCalls)
    }

    @Test fun invalidInputNeverCallsRepository() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = AssetsViewModel(GetAssetsUseCase(repo), CreateAssetUseCase(repo), FakeBackends())
        advanceUntilIdle()
        vm.openCreateForm()
        vm.saveAsset()
        advanceUntilIdle()
        assertEquals(0, repo.createCalls)
        assertNotNull(vm.uiState.value.form!!.errorMessage)
    }

    @Test fun switchingBackendCancelsSaveAndClearsDraft() = runTest(dispatcher) {
        val repo = FakeRepository().apply { pending = CompletableDeferred() }
        val backends = FakeBackends()
        val vm = AssetsViewModel(GetAssetsUseCase(repo), CreateAssetUseCase(repo), backends)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.onNameChanged("Old server asset")
        vm.onPriceChanged("20")
        vm.saveAsset()
        runCurrent()
        backends.settings.value = BackendSettings(activeUrl = "https://second.example/")
        runCurrent()
        assertNull(vm.uiState.value.form)
        repo.pending!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.assets.isEmpty())
    }

    private class FakeBackends : BackendRepository {
        override val settings = MutableStateFlow<BackendSettings?>(BackendSettings(activeUrl = "https://first.example/"))
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun saveAndSelect(address: String, name: String) = Result.success(Unit)
        override suspend fun select(url: String) = Result.success(Unit)
    }

    internal class FakeRepository : AssetRepository {
        var createCalls = 0
        var fail = false
        var pending: CompletableDeferred<Unit>? = null
        private val assets = mutableListOf<Asset>()

        fun seed(asset: Asset) { assets += asset }

        override suspend fun getAssets(status: AssetStatus?) =
            Result.success(assets.filter { status == null || it.status == status })

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
    }
}
