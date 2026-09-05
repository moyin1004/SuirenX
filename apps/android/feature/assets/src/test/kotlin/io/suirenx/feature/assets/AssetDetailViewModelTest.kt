package io.suirenx.feature.assets

import io.suirenx.feature.assets.AssetsViewModelTest.FakeRepository
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetUseCase
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

        vm.load("asset-1")
        advanceUntilIdle()
        repo.fail = true
        vm.load("asset-1")
        advanceUntilIdle()

        // The cached asset stays; the failing reload is never triggered.
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun editPrefillsFormAndSaveUpdatesAssetAndNotifies() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val notifier = AssetChangeNotifier()
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), notifier)
        val notifications = mutableListOf<Unit>()
        // Unconfined so the collector subscribes immediately at launch (replay=0
        // SharedFlow would otherwise miss emissions that happen before subscription).
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            notifier.events.toList(notifications)
        }

        vm.load("asset-1")
        advanceUntilIdle()
        vm.openEdit()

        val form = vm.uiState.value.form
        assertNotNull(form)
        assertEquals("MacBook Pro", form!!.name)
        assertEquals("16999.00", form.price)
        assertEquals("2026-01-01", form.purchaseDate)

        vm.onEditNameChanged("MacBook Pro M5")
        vm.onEditPriceChanged("18999")
        vm.saveEdit()
        advanceUntilIdle()

        assertNull(vm.uiState.value.form)
        val updated = vm.uiState.value.asset
        assertEquals("MacBook Pro M5", updated?.name)
        assertEquals(1_899_900L, updated?.priceCents)
        assertEquals(1, notifications.size)
        assertEquals(1, repo.updateCalls)
    }

    @Test fun failedEditKeepsFormInputAndShowsError() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

        vm.load("asset-1")
        advanceUntilIdle()
        repo.fail = true
        vm.openEdit()
        vm.onEditNameChanged("New Name")
        vm.saveEdit()
        advanceUntilIdle()

        val form = vm.uiState.value.form
        assertNotNull(form)
        assertFalse(form!!.isSaving)
        assertEquals("New Name", form.name)
        assertNotNull(form.errorMessage)
        // The loaded asset stays untouched after a failed save.
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)
    }

    @Test fun invalidEditInputNeverCallsRepository() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

        vm.load("asset-1")
        advanceUntilIdle()
        vm.openEdit()
        vm.onEditNameChanged("   ")
        vm.saveEdit()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.form!!.errorMessage)
        assertEquals(0, repo.updateCalls)
        assertEquals("MacBook Pro", vm.uiState.value.asset?.name)
    }

    @Test fun editCannotOpenBeforeAssetLoads() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), UpdateAssetUseCase(repo), AssetChangeNotifier())

        vm.openEdit()
        assertNull(vm.uiState.value.form)
    }
}
