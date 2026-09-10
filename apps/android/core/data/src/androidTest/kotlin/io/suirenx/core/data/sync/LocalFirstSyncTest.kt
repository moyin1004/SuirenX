package io.suirenx.core.data.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.data.auth.AuthTokenStore
import io.suirenx.core.data.local.*
import io.suirenx.core.data.network.*
import io.suirenx.core.data.repository.*
import io.suirenx.core.domain.*
import io.suirenx.core.model.*
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFirstSyncTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "sync-test-${java.util.UUID.randomUUID()}.db"
    private lateinit var db: LocalDatabase
    private lateinit var old: RemoteDatabase
    private lateinit var assets: LocalAssetRepository
    private lateinit var expiry: LocalExpiryRepository
    private lateinit var sync: LocalFirstSyncRepository
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val records = linkedMapOf<String, SyncAppliedChange>()
    private val responses = mutableMapOf<String, SyncResponse>()
    private val keys = mutableListOf<String>()
    private var loseResponse = false
    private var duringRequest: (() -> Unit)? = null
    private val date = LocalDate.of(2020, 1, 1)

    @Before fun setUp() {
        db = Room.databaseBuilder(context, LocalDatabase::class.java, databaseName).build()
        old = Room.inMemoryDatabaseBuilder(context, RemoteDatabase::class.java).build()
        wireRepositories()
    }

    private fun wireRepositories() {
        val clock = Clock.systemUTC()
        val codec = SyncCodec(clock)
        val journal = LocalSyncJournal(db, codec, json)
        assets = LocalAssetRepository(db, json, clock, journal)
        expiry = LocalExpiryRepository(db, clock, journal)
        val auth = object : AuthRepository {
            override val state = MutableStateFlow(AuthState("sync-test", true))
            override suspend fun initialize() = Result.success(Unit)
            override suspend fun register(username: String, password: String) = Result.success(Unit)
            override suspend fun login(username: String, password: String) = Result.success(Unit)
            override suspend fun logout() = Result.success(Unit)
        }
        val backends = object : BackendRepository {
            override val settings = MutableStateFlow<BackendSettings?>(BackendSettings(activeUrl = "https://sync.test/"))
            override suspend fun initialize() = Result.success(Unit)
            override suspend fun saveAndSelect(address: String, name: String) = Result.success(Unit)
        override suspend fun remove(url: String) = Result.success(Unit)
            override suspend fun select(url: String) = Result.success(Unit)
        }
        val modes = object : StorageModeRepository {
            override val mode = MutableStateFlow<StorageMode?>(StorageMode.Remote)
            override suspend fun initialize() = Result.success(Unit)
            override suspend fun select(mode: StorageMode) = Result.success(Unit)
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer)
            val request = json.decodeFromString<SyncRequest>(buffer.readUtf8())
            keys += request.idempotencyKey
            val result = responses.getOrPut(request.idempotencyKey) {
                val applied = mutableListOf<SyncAppliedChange>(); val conflicts = mutableListOf<SyncConflict>()
                for (item in request.changes) {
                    val current = records[item.id]
                    if ((current?.version ?: 0) != item.baseVersion) conflicts += SyncConflict(item.id, item.baseVersion, current?.version ?: 0, current?.asset)
                    else {
                        val change = SyncAppliedChange((records.values.maxOfOrNull { it.cursor } ?: 0) + 1,
                            item.baseVersion + 1, if (item.deleted) "2026-01-01T00:00:00Z" else "", item)
                        records[item.id] = change; applied += change
                    }
                }
                SyncResponse(nextCursor = records.values.maxOfOrNull { it.cursor } ?: 0, applied = applied,
                    changes = records.values.filter { it.cursor > request.cursor }, conflicts = conflicts)
            }
            duringRequest?.also { duringRequest = null; it() }
            if (loseResponse) { loseResponse = false; throw IOException("response lost") }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.encodeToString(result).toResponseBody("application/json".toMediaType())).build()
        }.build()
        val provider = SyncApiProvider(backends, client, json)
        val tokens = AuthTokenStore(context).also { it.save("https://sync.test/", "sync-test", "test-token") }
        val backup = LocalBackupRepositoryImpl(context, db, assets, expiry, json, Dispatchers.IO)
        sync = LocalFirstSyncRepository(db, assets, expiry, journal, codec, json, provider, tokens, auth, modes,
            backup, DataChangeNotifier(), RemoteAssetSyncStore(old, provider, auth, json, clock), old, Dispatchers.IO)
    }

    @After fun tearDown() { db.close(); old.close(); context.deleteDatabase(databaseName) }

    @Test fun changingTargetRebasesFrozenBatchAndRetainsLocalData() = runBlocking {
        val asset = assets.createAsset(NewAsset("Retained", 123, date)).getOrThrow()
        loseResponse = true
        assertTrue(sync.retry().isFailure)
        val frozen = db.syncDao().session()!!
        assertNotNull(frozen.batch)
        db.syncDao().saveSession(frozen.copy(target = "https://old.test/|previous-owner", cursor = 55, expiryCursor = 40))
        records.clear(); responses.clear()
        sync.retry().getOrThrow()
        assertEquals(2, keys.distinct().size)
        assertEquals("Retained", assets.getAsset(asset.id).getOrThrow().name)
        assertEquals(1L, records[asset.id]!!.version)
        assertEquals("https://sync.test/|sync-test", db.syncDao().session()!!.target)
        assertNull(db.syncDao().session()!!.batch)
    }

    @Test fun changingTargetPreservesTombstoneAndConflictingRemoteRecord() = runBlocking {
        val asset = assets.createAsset(NewAsset("Local", 123, date)).getOrThrow()
        sync.retry().getOrThrow()
        assets.deleteAsset(asset.id).getOrThrow()
        db.syncDao().saveSession(db.syncDao().session()!!.copy(target = "old|account"))
        sync.retry().getOrThrow()
        assertTrue(json.decodeFromString<SyncAssetPayload>(db.syncDao().get("asset", asset.id)!!.payload).deleted)
        assertEquals(1, sync.status.value.conflicts.size)
        assertFalse(records[asset.id]!!.asset.deleted)
    }

    @Test fun deletionIsLocalAndDurableWithoutNetwork() = runBlocking {
        val asset = assets.createAsset(NewAsset("Offline", 123, date)).getOrThrow()
        assets.deleteAsset(asset.id).getOrThrow()
        assertTrue(assets.getAssets().getOrThrow().isEmpty())
        val row = db.syncDao().get("asset", asset.id)!!
        assertTrue(row.dirty)
        assertTrue(json.decodeFromString<SyncAssetPayload>(row.payload).deleted)
        assertTrue(keys.isEmpty())
    }

    @Test fun lostResponseRetriesFrozenBatchWithoutDuplicateWrite() = runBlocking {
        val asset = assets.createAsset(NewAsset("First", 100, date)).getOrThrow()
        loseResponse = true
        assertTrue(sync.retry().isFailure)
        assertNotNull(db.syncDao().session()!!.batch)
        db.close()
        db = Room.databaseBuilder(context, LocalDatabase::class.java, databaseName).build()
        wireRepositories()
        sync.retry().getOrThrow()
        assertEquals(keys[0], keys[1])
        assertEquals(1L, records[asset.id]!!.version)
        assertFalse(db.syncDao().get("asset", asset.id)!!.dirty)
    }

    @Test fun editingDuringUploadKeepsNewEditAndRebasesNextBatch() = runBlocking {
        val asset = assets.createAsset(NewAsset("Before", 100, date)).getOrThrow()
        duringRequest = { runBlocking { assets.updateAsset(asset.id, NewAsset("After", 200, date)).getOrThrow() } }
        sync.retry().getOrThrow()
        assertEquals("After", assets.getAsset(asset.id).getOrThrow().name)
        assertEquals("After", records[asset.id]!!.asset.name)
        assertEquals(2L, records[asset.id]!!.version)
    }

    @Test fun oneConflictDoesNotBlockOtherRecordsAndCanKeepBoth() = runBlocking {
        val conflict = assets.createAsset(NewAsset("Local", 100, date)).getOrThrow()
        val other = assets.createAsset(NewAsset("Other", 100, date)).getOrThrow()
        records[conflict.id] = SyncAppliedChange(1, 1, asset = SyncAssetPayload(conflict.id, name = "Remote", priceCents = 200, purchaseDate = date.toString()))
        sync.retry().getOrThrow()
        assertEquals(1, sync.status.value.conflicts.size)
        assertTrue(records.containsKey(other.id))
        sync.resolveConflict(conflict.id, SyncConflictResolution.KeepBoth).getOrThrow()
        sync.retry().getOrThrow()
        val names = assets.getAssets().getOrThrow().map { it.name }
        assertTrue(names.contains("Remote")); assertTrue(names.contains("Local（副本）"))
        assertTrue(sync.status.value.conflicts.isEmpty())
    }

    @Test fun remoteTombstoneRemovesLocalBusinessRow() = runBlocking {
        val asset = assets.createAsset(NewAsset("Delete", 100, date)).getOrThrow()
        sync.retry().getOrThrow()
        val previous = records[asset.id]!!
        records[asset.id] = previous.copy(cursor = previous.cursor + 1, version = 2,
            deletedAt = "2026-01-01T00:00:00Z", asset = previous.asset.copy(deleted = true))
        sync.retry().getOrThrow()
        assertTrue(assets.getAssets().getOrThrow().isEmpty())
        assertEquals(2L, db.syncDao().get("asset", asset.id)!!.version)
    }

    @Test fun expiryDeletionJournalsTombstoneWithoutNetwork() = runBlocking {
        val item = expiry.create(NewExpiryItem("Milk", "Food", LocalDate.of(2030, 1, 1))).getOrThrow()
        expiry.delete(item.id).getOrThrow()
        assertTrue(expiry.list().getOrThrow().isEmpty())
        val row = db.syncDao().get("expiry", item.id)!!
        assertTrue(row.dirty)
        assertTrue(json.decodeFromString<SyncExpiryPayload>(row.payload).deleted)
        assertTrue(keys.isEmpty())
    }

    @Test fun editsAreUploadedInBoundedBatchesInsteadOfOneRequestPerRecord() = runBlocking {
        repeat(205) { assets.createAsset(NewAsset("Asset $it", 100, date)).getOrThrow() }
        sync.retry().getOrThrow()
        assertEquals(205, records.size)
        assertEquals(3, keys.size)
        assertEquals(0, sync.status.value.pendingOperations)
    }

    @Test fun localDeletionConflictCanKeepRemoteEditAsNewRecord() = runBlocking {
        val asset = assets.createAsset(NewAsset("Local", 100, date)).getOrThrow()
        sync.retry().getOrThrow()
        assets.deleteAsset(asset.id).getOrThrow()
        val previous = records[asset.id]!!
        records[asset.id] = previous.copy(cursor = 2, version = 2, asset = previous.asset.copy(name = "Remote edit", priceCents = 250))
        sync.retry().getOrThrow()
        assertTrue(sync.status.value.conflicts.single().localDeleted)
        sync.resolveConflict(asset.id, SyncConflictResolution.KeepBoth).getOrThrow()
        val copy = assets.getAssets().getOrThrow().single()
        assertNotEquals(asset.id, copy.id)
        assertEquals("Remote edit（副本）", copy.name)
        assertEquals(250L, copy.priceCents)
        assertTrue(json.decodeFromString<SyncAssetPayload>(db.syncDao().get("asset", asset.id)!!.payload).deleted)
        sync.retry().getOrThrow()
        assertTrue(records[asset.id]!!.asset.deleted)
        assertTrue(sync.status.value.conflicts.isEmpty())
    }

    @Test fun cancelInFlightRequestKeepsFrozenBatchForRetry() = runBlocking {
        assets.createAsset(NewAsset("Cancel", 100, date)).getOrThrow()
        duringRequest = { sync.cancelCurrent() }
        try {
            sync.retry()
            fail("expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertFalse(sync.status.value.syncing)
        assertNotNull(db.syncDao().session()!!.batch)
        sync.retry().getOrThrow()
        assertEquals(keys.first(), keys[1])
        assertEquals(1L, records.values.single().version)
        assertEquals(0, sync.status.value.pendingOperations)
    }

    @Test fun expiryConflictExposesActualFieldValuesForReview() = runBlocking {
        val item = expiry.create(NewExpiryItem("Milk", "Food", LocalDate.of(2030, 1, 1), location = "Kitchen")).getOrThrow()
        val row = db.syncDao().get("expiry", item.id)!!
        val remote = json.decodeFromString<SyncExpiryPayload>(row.payload).copy(location = "Fridge", notes = "Remote note")
        db.syncDao().save(row.copy(remoteVersion = 2, remotePayload = json.encodeToString(remote)))
        sync.refreshStatus().getOrThrow()
        val conflict = sync.status.value.expiryConflicts.single()
        assertEquals("Kitchen", conflict.local!!.location)
        assertEquals("Fridge", conflict.remote!!.location)
        assertEquals("Remote note", conflict.remote!!.notes)
    }

    @Test fun transactionFailureRollsBackBusinessAndJournal() = runBlocking {
        try {
            db.withTransaction {
                assets.createAsset(NewAsset("Rollback", 100, date)).getOrThrow()
                error("rollback")
            }
        } catch (_: IllegalStateException) { }
        assertTrue(assets.getAssets().getOrThrow().isEmpty())
        assertTrue(db.syncDao().all().isEmpty())
    }
}
