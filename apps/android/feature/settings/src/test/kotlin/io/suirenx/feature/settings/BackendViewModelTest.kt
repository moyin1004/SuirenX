package io.suirenx.feature.settings

import androidx.lifecycle.viewModelScope
import io.suirenx.core.domain.*
import io.suirenx.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackendViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val modes = object : StorageModeRepository {
        override val mode = MutableStateFlow<StorageMode?>(StorageMode.Remote)
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun select(mode: StorageMode): Result<Unit> { this.mode.value = mode; return Result.success(Unit) }
    }
    private val testedAddresses = mutableSetOf<String>()
    private val backend = object : BackendRepository {
        override val settings = MutableStateFlow<BackendSettings?>(BackendSettings(listOf(BackendServer("https://one.test/", "One")), "https://one.test/"))
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun saveAndSelect(address: String, name: String): Result<Unit> { settings.value = BackendSettings(activeUrl = address); return Result.success(Unit) }
        override suspend fun select(url: String) = saveAndSelect(url, "")
        override suspend fun testConnection(address: String): Result<String> { testedAddresses += address.trim(); return Result.success("连接测试成功") }
        override suspend fun saveServer(originalUrl: String?, address: String, name: String): Result<String> {
            val current = settings.value!!
            if (originalUrl == null || originalUrl != address.trim()) check(address.trim() in testedAddresses) { "未通过连接测试" }
            settings.value = current.copy(
                servers = current.servers.filterNot { it.url == originalUrl } + BackendServer(address, name),
                activeUrl = if (originalUrl != null && current.activeUrl == originalUrl) address else current.activeUrl,
            )
            return Result.success(address)
        }
        override suspend fun remove(url: String): Result<Unit> {
            settings.value = settings.value?.let { it.copy(servers = it.servers.filterNot { s -> s.url == url }, activeUrl = it.activeUrl.takeUnless { a -> a == url }) }
            return Result.success(Unit)
        }
    }
    private val auth = object : AuthRepository {
        override val state = MutableStateFlow(AuthState("", false))
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun register(username: String, password: String) = login(username, password)
        override suspend fun login(username: String, password: String): Result<Unit> { state.value = AuthState(username, true); return Result.success(Unit) }
        override suspend fun logout(): Result<Unit> { state.value = AuthState("", false); return Result.success(Unit) }
    }
    private var imports = 0
    private var cancelImport = false
    private var failImport = false
    private val importer = object : RemoteImportRepository {
        override suspend fun preview() = Result.success(MigrationPreview(2, 2, 0, emptyList(), 3, 3))
        override suspend fun import(): Result<MigrationResult> {
            imports++
            if (failImport) return Result.failure(IllegalStateException("连接服务器失败"))
            if (cancelImport) throw CancellationException("request stopped")
            return Result.success(MigrationResult(0, 0, 0))
        }
    }
    private val backup = object : LocalBackupRepository {
        override suspend fun snapshot() = Result.success(LocalDataSnapshot(emptyList(), emptyList()))
        override suspend fun createSafetyBackup() = Result.success(Unit)
        override suspend fun export() = Result.success("{}")
        override suspend fun inspect(content: String) = Result.success(BackupSummary(0))
        override suspend fun restore(content: String) = Result.success(BackupSummary(0))
    }
    private val expiry = object : ExpirySettingsRepository {
        override val soonDays = MutableStateFlow(7)
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun selectSoonDays(days: Int) = Result.success(Unit)
    }
    private val sync = object : RemoteSyncRepository {
        override val status = MutableStateFlow(RemoteSyncStatus())
        override suspend fun refreshStatus() = Result.success(Unit)
        override suspend fun retry() = Result.success(Unit)
        override suspend fun resolveConflict(assetId: String, resolution: SyncConflictResolution) = Result.success(Unit)
        override suspend fun resolveExpiryConflict(expiryId: String, resolution: SyncConflictResolution) = Result.success(Unit)
    }
    private val schedule = object : SyncScheduleRepository {
        override val schedule = MutableStateFlow(SyncSchedule.OnChange)
        override fun select(schedule: SyncSchedule) { this.schedule.value = schedule }
        override fun onLocalChange() {}
        override fun initialize() {}
    }
    private lateinit var vm: BackendViewModel
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = BackendViewModel(backend, modes, backup, DataChangeNotifier(), expiry, auth, importer, sync, schedule)
        dispatcher.scheduler.advanceUntilIdle()
    }
    @After fun tearDown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test fun registrationValidatesAndSignalsNavigationOnlyAfterSuccess() {
        vm.onAuthUsernameChanged("owner")
        vm.onAuthPasswordChanged("short")
        vm.register("short")
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.authError)
        assertEquals(0, vm.uiState.value.authSuccessVersion)
        vm.onAuthPasswordChanged("password")
        vm.register("different")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("两次密码不一致", vm.uiState.value.authError)
        vm.register("password")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.authSuccessVersion)
        assertEquals("", vm.uiState.value.authPassword)
        assertNull(vm.uiState.value.authError)
        assertEquals(StorageMode.Local, vm.uiState.value.mode)
    }

    @Test fun connectionCannotChangeDuringLogin() {
        vm.onAuthUsernameChanged("owner"); vm.onAuthPasswordChanged("password")
        vm.login()
        vm.select("https://two.test/")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("https://one.test/", backend.settings.value?.activeUrl)
    }

    @Test fun registrationRulesUseUtf8AndMatchMaximumBytes() {
        assertNotNull(accountValidation(" ", "password", "password"))
        assertNotNull(accountValidation("名".repeat(34), "password", "password"))
        assertNull(accountValidation("name", "中".repeat(24), "中".repeat(24)))
        assertNotNull(accountValidation("name", "中".repeat(25), "中".repeat(25)))
    }

    @Test fun deletingCurrentServerPausesSyncWithoutUploading() {
        vm.removeServer("https://one.test/")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StorageMode.Local, modes.mode.value)
        assertNull(backend.settings.value?.activeUrl)
        assertEquals(0, imports)
    }

    @Test fun deletingCurrentServerClearsFailedFirstSyncError() {
        auth.state.value = AuthState("owner", true)
        dispatcher.scheduler.advanceUntilIdle()
        failImport = true
        vm.previewMigration()
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmMigration()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("连接服务器失败", vm.uiState.value.migrationError)
        vm.removeServer("https://one.test/")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.migrationError)
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.migrationPreview)
        assertEquals(StorageMode.Local, vm.uiState.value.mode)
    }

    @Test fun deletingInactiveServerKeepsSyncEnabled() {
        vm.removeServer("https://inactive.test/")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StorageMode.Remote, modes.mode.value)
        assertEquals("https://one.test/", backend.settings.value?.activeUrl)
        assertEquals(0, imports)
    }

    @Test fun newServerCannotBeSavedUntilTheExactAddressPassesTest() {
        vm.onAddressChanged("https://two.test/")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        assertEquals(1, backend.settings.value?.servers?.size)
        vm.testConnection("https://two.test/")
        dispatcher.scheduler.advanceUntilIdle()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StorageMode.Remote, modes.mode.value)
        assertEquals("https://one.test/", backend.settings.value?.activeUrl)
        assertEquals("https://two.test/", vm.uiState.value.inspectedServerUrl)
        assertEquals(0, imports)
    }

    @Test fun changingActiveAddressPausesSyncButPreservesTheSingleAccount() {
        auth.state.value = AuthState("owner", true)
        dispatcher.scheduler.advanceUntilIdle()
        vm.editServer("https://one.test/")
        vm.onAddressChanged("https://next.test/")
        vm.testConnection("https://next.test/")
        dispatcher.scheduler.advanceUntilIdle()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("https://next.test/", backend.settings.value?.activeUrl)
        assertEquals(StorageMode.Local, modes.mode.value)
        assertTrue(vm.uiState.value.auth.authenticated)
        assertEquals("owner", vm.uiState.value.auth.username)
    }

    @Test fun secondLoginIsRejectedUntilCurrentAccountIsLoggedOut() {
        auth.state.value = AuthState("owner", true)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAuthUsernameChanged("another")
        vm.onAuthPasswordChanged("password")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("当前已有登录账号，请先退出当前账号后再登录", vm.uiState.value.authError)
        assertEquals("owner", vm.uiState.value.auth.username)
    }

    @Test fun loginAndPreviewDoNotUploadUntilConfirmed() {
        vm.onAuthUsernameChanged("new-owner")
        vm.onAuthPasswordChanged("password")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        vm.previewMigration()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StorageMode.Local, modes.mode.value)
        assertEquals(3, vm.uiState.value.migrationPreview?.localExpiryCount)
        assertEquals(0, imports)
        vm.confirmMigration()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StorageMode.Remote, modes.mode.value)
        assertEquals(1, imports)
        assertNull(vm.uiState.value.migrationPreview)
    }

    @Test fun cancelingFirstSyncReleasesBusyState() {
        cancelImport = true
        vm.previewMigration()
        dispatcher.scheduler.advanceUntilIdle()
        vm.confirmMigration()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.migrationBusy)
        assertNull(vm.uiState.value.migrationPreview)
    }
}
