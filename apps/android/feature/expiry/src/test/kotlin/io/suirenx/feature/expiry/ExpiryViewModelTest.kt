package io.suirenx.feature.expiry

import io.suirenx.core.domain.DataChangeNotifier
import io.suirenx.core.domain.ExpiryRepository
import io.suirenx.core.domain.ExpirySettingsRepository
import io.suirenx.core.domain.RemoteSyncRepository
import io.suirenx.core.domain.RemoteSyncStatus
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import io.suirenx.core.model.StorageMode
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun refreshKeepsExistingItemsVisibleUntilTheNewReadCompletes() = runTest(dispatcher) {
        val item = item("milk", "Milk", "Kitchen")
        val repository = FakeExpiryRepository().apply { items = listOf(item) }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        repository.listPending = CompletableDeferred()
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(item), viewModel.uiState.value.items)
        assertFalse(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.refreshing)

        repository.listPending?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(item), viewModel.uiState.value.items)
        assertFalse(viewModel.uiState.value.refreshing)
        assertTrue(viewModel.uiState.value.hasLoaded)
    }

    @Test
    fun readFailureKeepsItemsAndRetryClearsError() = runTest(dispatcher) {
        val item = item("milk", "Milk", "Kitchen")
        val repository = FakeExpiryRepository().apply { items = listOf(item) }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        repository.failList = true
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(item), viewModel.uiState.value.items)
        assertEquals("本机用品读取失败", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.loading)

        repository.failList = false
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(listOf(item), viewModel.uiState.value.items)
    }

    @Test
    fun locationSearchFiltersLocallyWithoutReloadingRepository() = runTest(dispatcher) {
        val kitchen = item("milk", "Milk", "Kitchen")
        val bathroom = item("soap", "Soap", "Bathroom")
        val repository = FakeExpiryRepository().apply { items = listOf(kitchen, bathroom) }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val readsAfterLoad = repository.listCalls

        viewModel.setQuery("Bathroom")

        assertEquals(listOf(bathroom), viewModel.uiState.value.visibleItems)
        assertEquals(readsAfterLoad, repository.listCalls)
    }

    private fun createViewModel(repository: FakeExpiryRepository) = ExpiryViewModel(
        repository = repository,
        changes = DataChangeNotifier(),
        settings = object : ExpirySettingsRepository {
            override val soonDays = MutableStateFlow(7)
            override suspend fun initialize() = Result.success(Unit)
            override suspend fun selectSoonDays(days: Int) = Result.success(Unit)
        },
        remoteSync = object : RemoteSyncRepository {
            override val status = MutableStateFlow(RemoteSyncStatus())
            override suspend fun refreshStatus() = Result.success(Unit)
            override suspend fun retry() = Result.success(Unit)
            override suspend fun resolveConflict(assetId: String, resolution: io.suirenx.core.domain.SyncConflictResolution) = Result.success(Unit)
            override suspend fun resolveExpiryConflict(expiryId: String, resolution: io.suirenx.core.domain.SyncConflictResolution) = Result.success(Unit)
        },
        modes = object : StorageModeRepository {
            override val mode = MutableStateFlow<StorageMode?>(StorageMode.Local)
            override suspend fun initialize() = Result.success(Unit)
            override suspend fun select(mode: StorageMode) = Result.success(Unit)
        },
    )

    private fun item(id: String, name: String, location: String) = ExpiryItem(
        id = id,
        name = name,
        category = "Household",
        packageExpiryDate = LocalDate.now().plusDays(30),
        location = location,
        status = ExpiryItemStatus.InUse,
    )

    private class FakeExpiryRepository : ExpiryRepository {
        var items = emptyList<ExpiryItem>()
        var failList = false
        var listPending: CompletableDeferred<Unit>? = null
        var listCalls = 0

        override suspend fun list(includeArchived: Boolean): Result<List<ExpiryItem>> {
            listCalls++
            listPending?.await()
            return if (failList) Result.failure(IOException("本机用品读取失败")) else Result.success(items)
        }

        override suspend fun get(id: String) = Result.failure<ExpiryItem>(NoSuchElementException(id))
        override suspend fun create(item: NewExpiryItem) = Result.failure<ExpiryItem>(UnsupportedOperationException())
        override suspend fun update(id: String, item: NewExpiryItem) = Result.failure<ExpiryItem>(UnsupportedOperationException())
        override suspend fun updateStatus(id: String, status: ExpiryItemStatus) = Result.failure<ExpiryItem>(UnsupportedOperationException())
        override suspend fun updateArchive(id: String, archive: Boolean) = Result.failure<ExpiryItem>(UnsupportedOperationException())
        override suspend fun delete(id: String) = Result.failure<Unit>(UnsupportedOperationException())
    }
}
