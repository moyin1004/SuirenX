package io.suirenx.feature.assets

import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetArchiveUseCase
import io.suirenx.core.domain.UpdateAssetStatusUseCase
import kotlinx.coroutines.CompletableDeferred
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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), notifier, UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))

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
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), notifier, UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))

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

    @Test fun retireAndReactivateNotifiesListAndRetainsLoadedDetail() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val notifier = AssetChangeNotifier()
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            notifier.events.collect { changes++ }
        }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), notifier, UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        vm.load(sampleAsset.id)
        advanceUntilIdle()
        vm.openStatusDialog()
        vm.changeRetiredDate("2026-01-03")
        vm.saveStatus()
        advanceUntilIdle()
        assertEquals(AssetStatus.Retired, vm.uiState.value.asset?.status)
        assertEquals(LocalDate.of(2026, 1, 3), vm.uiState.value.asset?.retiredDate)
        assertNull(vm.uiState.value.statusTarget)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, changes)
        vm.openStatusDialog()
        vm.saveStatus()
        advanceUntilIdle()
        assertEquals(AssetStatus.Active, vm.uiState.value.asset?.status)
        assertNull(vm.uiState.value.asset?.retiredDate)
        assertEquals(2, changes)
    }

    @Test fun invalidDatesDoNotReachRepository() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        vm.load(sampleAsset.id)
        advanceUntilIdle()
        vm.openStatusDialog()
        for (date in listOf("", "2026-02-30", "2025-12-31", LocalDate.now().plusDays(1).toString())) {
            vm.changeRetiredDate(date)
            vm.saveStatus()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.statusError)
            assertEquals(0, repo.statusCalls)
            assertEquals(AssetStatus.Active, vm.uiState.value.asset?.status)
        }
    }

    @Test fun statusSaveBlocksDuplicatesAndFailureRetainsInputForRetry() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        vm.load(sampleAsset.id)
        advanceUntilIdle()
        repo.pending = CompletableDeferred()
        repo.fail = true
        vm.openStatusDialog()
        vm.changeRetiredDate("2026-01-03")
        vm.saveStatus()
        vm.saveStatus()
        vm.dismissStatusDialog()
        vm.changeRetiredDate("2026-01-04")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSavingStatus)
        assertEquals(1, repo.statusCalls)
        assertEquals("2026-01-03", vm.uiState.value.retiredDateInput)
        assertNotNull(vm.uiState.value.statusTarget)
        repo.pending?.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSavingStatus)
        assertNotNull(vm.uiState.value.statusError)
        assertEquals(AssetStatus.Active, vm.uiState.value.asset?.status)
        assertEquals("2026-01-03", vm.uiState.value.retiredDateInput)
        repo.fail = false
        vm.saveStatus()
        advanceUntilIdle()
        assertNull(vm.uiState.value.statusTarget)
        assertEquals(AssetStatus.Retired, vm.uiState.value.asset?.status)
    }

    @Test fun archiveFailureRetryAndRestorePreserveRetirement() = runTest(dispatcher) {
        val retired = sampleAsset.copy(status = AssetStatus.Retired, retiredDate = LocalDate.of(2026, 1, 3))
        val repo = FakeRepository().apply { seed(retired) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        vm.load(retired.id)
        advanceUntilIdle()
        repo.pending = CompletableDeferred()
        repo.fail = true
        vm.openArchiveDialog()
        vm.saveArchive()
        vm.saveArchive()
        vm.dismissArchiveDialog()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaving)
        assertEquals(1, repo.archiveCalls)
        assertEquals(true, vm.uiState.value.archiveTarget)
        repo.pending?.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaving)
        assertNotNull(vm.uiState.value.archiveError)
        assertFalse(vm.uiState.value.asset!!.isArchived)
        repo.fail = false
        vm.saveArchive()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.asset!!.isArchived)
        assertNull(vm.uiState.value.archiveTarget)
        vm.openStatusDialog()
        assertNull(vm.uiState.value.statusTarget)
        vm.openArchiveDialog()
        assertEquals(false, vm.uiState.value.archiveTarget)
        vm.saveArchive()
        advanceUntilIdle()
        assertEquals(retired, vm.uiState.value.asset)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test fun cancellingArchiveDialogDoesNotMutateAsset() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = AssetDetailViewModel(GetAssetUseCase(repo), AssetChangeNotifier(), UpdateAssetStatusUseCase(repo), UpdateAssetArchiveUseCase(repo))
        vm.load(sampleAsset.id)
        advanceUntilIdle()
        vm.openArchiveDialog()
        vm.dismissArchiveDialog()
        vm.saveArchive()
        advanceUntilIdle()
        assertEquals(0, repo.archiveCalls)
        assertEquals(sampleAsset, vm.uiState.value.asset)
    }
}
