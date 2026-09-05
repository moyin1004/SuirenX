package io.suirenx.feature.assets

import io.suirenx.feature.assets.AssetsViewModelTest.FakeRepository
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class AssetDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val sampleAsset = Asset(
        id = "asset-1",
        name = "MacBook Pro",
        priceCents = 1_699_900,
        purchaseDate = LocalDate.of(2026, 1, 1),
        status = AssetStatus.Active,
        imageUrl = "",
        heldDays = 133,
        dailyCostCents = 12_781,
    )

    @Test fun loadShowsAssetOnSuccess() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo))

        assertTrue(vm.uiState.value.isLoading)
        vm.load("asset-1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("MacBook Pro", state.asset?.name)
        assertEquals(133, state.asset?.heldDays)
    }

    @Test fun blankIdIsIgnoredAndKeepsInitialState() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo))

        vm.load(" ")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.asset)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun failureShowsErrorAndRetryReloads() = runTest(dispatcher) {
        val repo = FakeRepository().apply {
            seed(sampleAsset)
            fail = true
        }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo))

        vm.load("asset-1")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.asset)

        repo.fail = false
        vm.retry()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("MacBook Pro", state.asset?.name)
    }

    @Test fun reloadForAlreadyLoadedIdIsSkipped() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo))

        vm.load("asset-1")
        advanceUntilIdle()
        repo.fail = true
        vm.load("asset-1")
        advanceUntilIdle()

        // The cached asset stays; the failing reload is never triggered.
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)
        assertNull(vm.uiState.value.errorMessage)
    }
}
