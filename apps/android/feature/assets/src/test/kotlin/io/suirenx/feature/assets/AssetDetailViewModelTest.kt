package io.suirenx.feature.assets

import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.feature.assets.AssetsViewModelTest.FakeRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier())

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier())

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier())

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier())

        vm.load("asset-1")
        advanceUntilIdle()
        repo.fail = true
        vm.load("asset-1")
        advanceUntilIdle()

        // The cached asset stays; the failing reload is never triggered.
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun changeNotifierSilentlyRefreshesLoadedAsset() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val notifier = AssetChangeNotifier()
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), notifier)

        vm.load("asset-1")
        advanceUntilIdle()
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)

        val states = mutableListOf<AssetDetailUiState>()
        // Unconfined so the collector subscribes immediately (StateFlow replays
        // the current value) and records every state the silent refresh emits.
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            vm.uiState.toList(states)
        }

        repo.replace(sampleAsset.copy(name = "MacBook Pro M5"))
        notifier.notifyAssetChanged()
        advanceUntilIdle()

        assertEquals("MacBook Pro M5", vm.uiState.value.asset?.name)
        // A background refresh must never flip the screen into its loading state.
        assertTrue(states.none { it.isLoading })
    }

    @Test fun silentRefreshFailureKeepsVisibleAsset() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val notifier = AssetChangeNotifier()
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), notifier)

        vm.load("asset-1")
        advanceUntilIdle()

        repo.fail = true
        notifier.notifyAssetChanged()
        advanceUntilIdle()

        // The background refresh failed: keep the cached asset, show neither
        // spinner nor error on the already-rendered detail screen.
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
    }
}
