package io.suirenx.feature.assets

import androidx.lifecycle.SavedStateHandle
import io.suirenx.core.domain.CreateAssetUseCase
import io.suirenx.core.domain.GetAssetUseCase
import io.suirenx.core.domain.UpdateAssetUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetFormViewModelTest {
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
        iconKey = "laptop",
    )

    private fun viewModel(
        repo: FakeRepository,
        notifier: AssetChangeNotifier = AssetChangeNotifier(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = AssetFormViewModel(
        savedState,
        CreateAssetUseCase(repo),
        UpdateAssetUseCase(repo),
        GetAssetUseCase(repo),
        notifier,
    )

    @Test fun createSaveCallsRepositoryAndAnnouncesChange() = runTest(dispatcher) {
        val repo = FakeRepository()
        val notifier = AssetChangeNotifier()
        val vm = viewModel(repo, notifier)
        val savedEvents = mutableListOf<Unit>()
        val changeEvents = mutableListOf<Unit>()
        // Unconfined so collectors subscribe immediately; replay=0 SharedFlows
        // would otherwise miss emissions that happen before subscription.
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            vm.saved.toList(savedEvents)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            notifier.events.toList(changeEvents)
        }

        vm.onIconSelected("book")
        vm.onNameChanged("Kindle")
        vm.onPriceChanged("899.50")
        vm.save()
        advanceUntilIdle()

        assertEquals(1, repo.createCalls)
        assertEquals(0, repo.updateCalls)
        assertEquals(1, savedEvents.size)
        assertEquals(1, changeEvents.size)
        // On success the page closes via the saved event; the VM stays locked
        // (isSaving = true) until disposal to block any double submit.
        assertTrue(vm.uiState.value.isSaving)
    }

    @Test fun editPrefillsExistingAssetAndSaveUpdates() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = viewModel(repo, savedState = SavedStateHandle(mapOf("id" to "asset-1")))
        advanceUntilIdle()

        val loaded = vm.uiState.value
        assertTrue(loaded.isEdit)
        assertFalse(loaded.isLoading)
        assertEquals("MacBook Pro", loaded.name)
        assertEquals("16999.00", loaded.price)
        assertEquals("2026-01-01", loaded.purchaseDate)
        assertEquals("laptop", loaded.iconKey)

        val savedEvents = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            vm.saved.toList(savedEvents)
        }

        vm.onNameChanged("MacBook Pro M5")
        vm.onPriceChanged("18999")
        vm.save()
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls)
        assertEquals(0, repo.createCalls)
        assertEquals(1, savedEvents.size)
        assertTrue(vm.uiState.value.isSaving)
    }

    @Test fun invalidInputShowsErrorWithoutCallingRepository() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)

        vm.onNameChanged("   ")
        vm.save()
        advanceUntilIdle()

        assertEquals("请输入资产名称", vm.uiState.value.errorMessage)
        assertEquals(0, repo.createCalls)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test fun changingDraftMarksDirtyAndRestoringBaselineClearsIt() = runTest(dispatcher) {
        val vm = viewModel(FakeRepository())

        vm.onNameChanged("Kindle")
        assertTrue(vm.uiState.value.isDirty)

        vm.onNameChanged("")
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test fun failedSaveKeepsInputAndCanRetry() = runTest(dispatcher) {
        val repo = FakeRepository().apply { fail = true }
        val vm = viewModel(repo)
        val savedEvents = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            vm.saved.toList(savedEvents)
        }

        vm.onIconSelected("book")
        vm.onNameChanged("Kindle")
        vm.onPriceChanged("899.50")
        vm.save()
        advanceUntilIdle()

        val failed = vm.uiState.value
        assertFalse(failed.isSaving)
        assertEquals("Kindle", failed.name)
        assertEquals("899.50", failed.price)
        assertEquals("book", failed.iconKey)
        assertEquals("保存失败，请检查网络和服务后重试", failed.errorMessage)
        assertEquals(1, repo.createCalls)
        assertEquals(0, savedEvents.size)

        repo.fail = false
        vm.save()
        advanceUntilIdle()

        assertEquals(2, repo.createCalls)
        assertEquals(1, savedEvents.size)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun editLoadFailureShowsErrorAndBlocksSave() = runTest(dispatcher) {
        val repo = FakeRepository().apply { fail = true }
        val vm = viewModel(repo, savedState = SavedStateHandle(mapOf("id" to "missing")))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loadError)
        vm.onNameChanged("Whatever")
        vm.save()
        advanceUntilIdle()

        // A form that could not prefill must not dispatch a blind update.
        assertEquals(0, repo.updateCalls)
        assertEquals(0, repo.createCalls)
    }

    @Test fun iconSelectionCancelsOrSavesAndIsLockedDuringSave() = runTest(dispatcher) {
        val repo = FakeRepository().apply { seed(sampleAsset) }
        val vm = viewModel(repo, savedState = SavedStateHandle(mapOf("id" to sampleAsset.id)))
        advanceUntilIdle()
        vm.openIconPicker()
        assertTrue(vm.uiState.value.isIconPickerOpen)
        vm.closeIconPicker()
        assertEquals("laptop", vm.uiState.value.iconKey)
        vm.openIconPicker()
        vm.onIconSelected("camera")
        assertFalse(vm.uiState.value.isIconPickerOpen)
        vm.save()
        vm.onIconSelected("phone")
        advanceUntilIdle()
        assertEquals("camera", vm.uiState.value.iconKey)
        assertEquals("camera", repo.getAsset(sampleAsset.id).getOrThrow().iconKey)
    }

    @Test fun createPersistsSelectedIcon() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        vm.onNameChanged("Camera")
        vm.onPriceChanged("100")
        vm.onIconSelected("camera")
        vm.save()
        advanceUntilIdle()
        assertEquals("camera", repo.getAsset("created").getOrThrow().iconKey)
    }
}
