package io.suirenx.core.data.sync

import androidx.room.withTransaction
import io.suirenx.core.data.auth.AuthTokenStore
import io.suirenx.core.data.local.*
import io.suirenx.core.data.network.*
import io.suirenx.core.data.repository.LocalAssetRepository
import io.suirenx.core.data.repository.LocalExpiryRepository
import io.suirenx.core.domain.*
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class FrozenBatch(val request: SyncRequest, val revisions: Map<String, String>)

@Singleton
class LocalFirstSyncRepository @Inject constructor(
    private val database: LocalDatabase,
    private val assets: LocalAssetRepository,
    private val expiry: LocalExpiryRepository,
    private val journal: LocalSyncJournal,
    private val codec: SyncCodec,
    private val json: Json,
    private val apiProvider: SyncApiProvider,
    private val tokens: AuthTokenStore,
    private val auth: AuthRepository,
    private val modes: StorageModeRepository,
    private val backups: LocalBackupRepository,
    private val changes: DataChangeNotifier,
    private val legacy: io.suirenx.core.data.repository.RemoteAssetSyncStore,
    private val legacyDatabase: RemoteDatabase,
    @io.suirenx.core.data.di.IoDispatcher private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : RemoteSyncRepository {
    private val mutex = Mutex()
    @Volatile private var activeJob: kotlinx.coroutines.Job? = null
    override fun cancelCurrent() { activeJob?.cancel() }
    private val mutableStatus = MutableStateFlow(RemoteSyncStatus())
    override val status: StateFlow<RemoteSyncStatus> = mutableStatus

    override suspend fun refreshStatus(): Result<Unit> = attempt { publish() }

    override suspend fun retry(): Result<Unit> = attempt {
        check(modes.mode.value == io.suirenx.core.model.StorageMode.Remote) { "请先开启同步" }
        check(auth.state.value.authenticated) { "请先登录同步账号" }
        check(mutex.tryLock()) { "同步正在进行" }
        mutableStatus.value = mutableStatus.value.copy(syncing = true, error = null, waitingForNetwork = false, needsLogin = false)
        activeJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        try {
            val url = apiProvider.currentUrl()
            val target = "$url|${tokens.username(apiProvider.currentUrl())}"
            val token = tokens.tokenFor(url) ?: error("登录已过期，请重新登录")
            val api = apiProvider.current()
            val existing = database.syncDao().session()
            if (existing == null || existing.target != target) {
                backups.createSafetyBackup().getOrThrow()
                database.withTransaction {
                    check(database.syncDao().session() == existing) { "同步配置已改变，请重试" }
                    if (existing != null) {
                        // Version/cursor/idempotency are target-specific. Keep local rows
                        // and tombstones; archive old conflict snapshots in the safety backup.
                        database.syncDao().all().forEach { row ->
                            database.syncDao().save(row.copy(
                                version = 0, revision = UUID.randomUUID().toString(), dirty = true,
                                remoteVersion = null, remotePayload = null,
                            ))
                        }
                    }
                    assets.allForBackup().forEach { if (database.syncDao().get("asset", it.id) == null) journal.asset(it) }
                    expiry.allForBackup().forEach { if (database.syncDao().get("expiry", it.id) == null) journal.expiry(it) }
                    database.syncDao().saveSession(LocalSyncSession(target = target))
                    if (existing == null) importLegacy(target)
                }
            }
            database.withTransaction {
                // One-time, non-destructive enrollment of records from older app versions.
                assets.allForBackup().forEach { if (database.syncDao().get("asset", it.id) == null) journal.asset(it) }
                expiry.allForBackup().forEach { if (database.syncDao().get("expiry", it.id) == null) journal.expiry(it) }
            }
            val completed = withTimeoutOrNull(60_000) {
                var pulled = false
                while (true) {
                    check(target == "${apiProvider.currentUrl()}|${tokens.username(apiProvider.currentUrl())}" && tokens.tokenFor(url) == token && modes.mode.value == io.suirenx.core.model.StorageMode.Remote) { "同步配置已改变，本次已停止" }
                    val batch = claim(pulled) ?: break
                    val response = api.sync(batch.request, "Bearer $token")
                    check(target == "${apiProvider.currentUrl()}|${tokens.username(apiProvider.currentUrl())}" && tokens.tokenFor(url) == token) { "账号已改变，响应未应用；待同步批次已保留" }
                    apply(batch, response)
                    pulled = true
                    changes.notifyChanged()
                    publish()
                }
                database.withTransaction {
                    val session = checkNotNull(database.syncDao().session())
                    database.syncDao().saveSession(session.copy(lastSyncedAt = Instant.now().toString()))
                }
                true
            } ?: false
            if (!completed) throw IOException("同步超时，数据和待发送批次已保留")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val message = syncFailureMessage(error)
            mutableStatus.value = mutableStatus.value.copy(error = message, waitingForNetwork = error is java.net.UnknownHostException || error is java.net.ConnectException, needsLogin = error is retrofit2.HttpException && error.code() == 401)
            throw IllegalStateException(message, error)
        } finally {
            mutableStatus.value = mutableStatus.value.copy(syncing = false)
            activeJob = null
            mutex.unlock()
        }
        publish()
    }

    private suspend fun importLegacy(target: String) {
        // Keep the old database intact as recovery evidence. Only this authenticated
        // account's cache is enrolled, in the same transaction as the binding marker.
        for (asset in legacy.list(null, true).getOrThrow()) {
            if (database.assetDao().getById(asset.id) == null) {
                database.assetDao().upsert(with(assets) { asset.toEntity() })
                journal.asset(asset)
                val row = checkNotNull(database.syncDao().get("asset", asset.id))
                database.syncDao().save(row.copy(version = asset.syncVersion, dirty = false))
            }
        }
        for (item in legacy.listExpiry(true).getOrThrow()) {
            if (database.expiryDao().getById(item.id) == null) {
                database.expiryDao().upsert(with(expiry) { item.toEntity() })
                journal.expiry(item)
                val row = checkNotNull(database.syncDao().get("expiry", item.id))
                database.syncDao().save(row.copy(version = item.syncVersion, dirty = false))
            }
        }
        // Old unacknowledged operations are preserved as new local revisions. The
        // server will detect stale versions rather than silently dropping them.
        for ((id, operations) in legacyDatabase.outbox().getAll(target).groupBy { it.assetId }) {
            val first = operations.first()
            val latest = operations.last()
            val existing = database.syncDao().get("asset", id)
            if (existing?.dirty == true && existing.payload != latest.payloadJson) {
                val wire = json.decodeFromString<SyncAssetPayload>(latest.payloadJson)
                if (!wire.deleted) {
                    val copy = with(codec) { wire.copy(id = UUID.randomUUID().toString(), name = wire.name + "（旧缓存）").toAsset(0, "") }
                    database.assetDao().upsert(with(assets) { copy.toEntity() }); journal.asset(copy)
                }
            } else {
                writeBusiness("asset", id, latest.payloadJson)
                database.syncDao().save(LocalSyncRecord("asset", id, UUID.randomUUID().toString(), first.baseVersion, latest.payloadJson))
            }
        }
        for ((id, operations) in legacyDatabase.expiryOutbox().getAll(target).groupBy { it.expiryId }) {
            val first = operations.first(); val latest = operations.last()
            val existing = database.syncDao().get("expiry", id)
            if (existing?.dirty == true && existing.payload != latest.payloadJson) {
                val wire = json.decodeFromString<SyncExpiryPayload>(latest.payloadJson)
                if (!wire.deleted) {
                    val copy = with(codec) { wire.copy(id = UUID.randomUUID().toString(), name = wire.name + "（旧缓存）").toExpiryItem(0) }
                    database.expiryDao().upsert(with(expiry) { copy.toEntity() }); journal.expiry(copy)
                }
            } else {
                writeBusiness("expiry", id, latest.payloadJson)
                database.syncDao().save(LocalSyncRecord("expiry", id, UUID.randomUUID().toString(), first.baseVersion, latest.payloadJson))
            }
        }
    }

    private suspend fun claim(alreadyPulled: Boolean): FrozenBatch? = database.withTransaction {
        val dao = database.syncDao()
        val session = checkNotNull(dao.session())
        session.batch?.let { return@withTransaction json.decodeFromString<FrozenBatch>(it) }
        val pending = dao.all().filter { it.dirty && it.remoteVersion == null }.take(100)
        if (pending.isEmpty() && alreadyPulled) return@withTransaction null
        val request = SyncRequest(
            cursor = session.cursor, expiryCursor = session.expiryCursor, idempotencyKey = UUID.randomUUID().toString(),
            changes = pending.filter { it.kind == "asset" }.map { json.decodeFromString<SyncAssetPayload>(it.payload).copy(baseVersion = it.version) },
            expiryChanges = pending.filter { it.kind == "expiry" }.map { json.decodeFromString<SyncExpiryPayload>(it.payload).copy(baseVersion = it.version) },
        )
        val batch = FrozenBatch(request, pending.associate { "${it.kind}/${it.id}" to it.revision })
        dao.saveSession(session.copy(batch = json.encodeToString(batch)))
        batch
    }

    private suspend fun apply(batch: FrozenBatch, response: SyncResponse) = database.withTransaction {
        val accounted = response.applied.map { "asset/${it.asset.id}" } + response.conflicts.map { "asset/${it.id}" } +
            response.appliedExpiry.map { "expiry/${it.item.id}" } + response.expiryConflicts.map { "expiry/${it.id}" }
        check(batch.revisions.keys.all { it in accounted }) { "服务器响应缺少确认，批次已保留" }
        for (change in response.applied) accept("asset", change.asset.id, change.version, json.encodeToString(change.asset.copy(deleted = change.asset.deleted || change.deletedAt.isNotBlank())), batch)
        for (change in response.appliedExpiry) accept("expiry", change.item.id, change.version, json.encodeToString(change.item.copy(deleted = change.item.deleted || change.deletedAt.isNotBlank())), batch)
        for (conflict in response.conflicts) conflict("asset", conflict.id, conflict.remoteVersion, conflict.remote?.let(json::encodeToString).orEmpty())
        for (conflict in response.expiryConflicts) conflict("expiry", conflict.id, conflict.remoteVersion, conflict.remote?.let(json::encodeToString).orEmpty())
        for (change in response.changes) pull("asset", change.asset.id, change.version, json.encodeToString(change.asset.copy(deleted = change.asset.deleted || change.deletedAt.isNotBlank())))
        for (change in response.expiryChanges) pull("expiry", change.item.id, change.version, json.encodeToString(change.item.copy(deleted = change.item.deleted || change.deletedAt.isNotBlank())))
        val session = checkNotNull(database.syncDao().session())
        database.syncDao().saveSession(session.copy(batch = null, cursor = maxOf(session.cursor, response.nextCursor), expiryCursor = maxOf(session.expiryCursor, response.nextExpiryCursor)))
    }

    private suspend fun accept(kind: String, id: String, version: Long, payload: String, batch: FrozenBatch) {
        val current = database.syncDao().get(kind, id) ?: return
        if (current.revision == batch.revisions["$kind/$id"]) {
            writeBusiness(kind, id, payload)
            database.syncDao().save(current.copy(version = version, payload = payload, dirty = false, remotePayload = null, remoteVersion = null))
        } else {
            // A newer local edit inherits the acknowledged server base but keeps its own value.
            database.syncDao().save(current.copy(version = version))
        }
    }

    private suspend fun pull(kind: String, id: String, version: Long, payload: String) {
        val current = database.syncDao().get(kind, id)
        if (current != null && (current.dirty || current.version >= version)) return
        writeBusiness(kind, id, payload)
        database.syncDao().save(LocalSyncRecord(kind, id, UUID.randomUUID().toString(), version, payload, dirty = false))
    }

    private suspend fun conflict(kind: String, id: String, version: Long, remote: String) {
        val current = database.syncDao().get(kind, id) ?: return
        database.syncDao().save(current.copy(remoteVersion = version, remotePayload = remote))
    }

    private suspend fun writeBusiness(kind: String, id: String, payload: String) {
        if (kind == "asset") {
            val wire = payload.takeIf { it.isNotBlank() }?.let { json.decodeFromString<SyncAssetPayload>(it) }
            if (wire == null || wire.deleted || wire.purchaseDate.isBlank()) database.assetDao().delete(id)
            else {
                val domain = with(codec) { wire.toAsset(0, "") }
                val old = database.assetDao().getById(id)
                val entity = with(assets) { domain.toEntity() }.copy(createdAt = old?.createdAt ?: domain.createdAt.toString())
                database.assetDao().upsert(entity)
            }
        } else {
            val wire = payload.takeIf { it.isNotBlank() }?.let { json.decodeFromString<SyncExpiryPayload>(it) }
            if (wire == null || wire.deleted || wire.packageExpiryDate.isBlank()) database.expiryDao().delete(id)
            else {
                val domain = with(codec) { wire.toExpiryItem(0) }
                val old = database.expiryDao().getById(id)
                database.expiryDao().upsert(with(expiry) { domain.toEntity() }.copy(createdAt = old?.createdAt ?: domain.createdAt.toString()))
            }
        }
    }

    override suspend fun resolveConflict(assetId: String, resolution: SyncConflictResolution): Result<Unit> = resolve("asset", assetId, resolution)
    override suspend fun resolveExpiryConflict(expiryId: String, resolution: SyncConflictResolution): Result<Unit> = resolve("expiry", expiryId, resolution)

    private suspend fun resolve(kind: String, id: String, resolution: SyncConflictResolution): Result<Unit> = attempt {
        check(mutex.tryLock()) { "同步正在进行，请稍后处理冲突" }
        try {
            database.withTransaction {
                val dao = database.syncDao()
                check(dao.session()?.batch == null) { "请先重试上次未确认的同步" }
                val current = dao.get(kind, id) ?: error("未找到记录")
                val remoteVersion = current.remoteVersion ?: error("该冲突已处理")
                when (resolution) {
                    SyncConflictResolution.KeepLocal -> dao.save(current.copy(version = remoteVersion, revision = UUID.randomUUID().toString(), dirty = true, remotePayload = null, remoteVersion = null))
                    SyncConflictResolution.KeepRemote, SyncConflictResolution.KeepBoth -> {
                        check(!current.remotePayload.isNullOrBlank()) { "无法读取另一端快照，请检查账号归属" }
                        val localDeleted = if (kind == "asset") json.decodeFromString<SyncAssetPayload>(current.payload).deleted else json.decodeFromString<SyncExpiryPayload>(current.payload).deleted
                        if (resolution == SyncConflictResolution.KeepBoth) {
                            val copyPayload = if (localDeleted) current.remotePayload!! else current.payload
                            val newId = UUID.randomUUID().toString()
                            if (kind == "asset") {
                                val wire = json.decodeFromString<SyncAssetPayload>(copyPayload)
                                check(!wire.deleted) { "本机已删除，无法另存；可选择保留另一端" }
                                val duplicate = with(codec) { wire.copy(id = newId, name = wire.name + "（副本）").toAsset(0, "") }
                                database.assetDao().upsert(with(assets) { duplicate.toEntity() })
                                journal.asset(duplicate)
                            } else {
                                val wire = json.decodeFromString<SyncExpiryPayload>(copyPayload)
                                check(!wire.deleted) { "本机已删除，无法另存；可选择保留另一端" }
                                val duplicate = with(codec) { wire.copy(id = newId, name = wire.name + "（副本）").toExpiryItem(0) }
                                database.expiryDao().upsert(with(expiry) { duplicate.toEntity() })
                                journal.expiry(duplicate)
                            }
                        }
                        if (resolution == SyncConflictResolution.KeepBoth && localDeleted) {
                            dao.save(current.copy(version = remoteVersion, revision = UUID.randomUUID().toString(), dirty = true, remotePayload = null, remoteVersion = null))
                            return@withTransaction
                        }
                        val remote = current.remotePayload.orEmpty()
                        writeBusiness(kind, id, remote)
                        dao.save(current.copy(version = remoteVersion, payload = remote, dirty = false, remotePayload = null, remoteVersion = null))
                    }
                }
            }
        } finally { mutex.unlock() }
        publish()
        changes.notifyChanged()
    }

    private suspend fun publish() {
        val records = database.syncDao().all()
        val conflicts = records.filter { it.remoteVersion != null }
        mutableStatus.value = mutableStatus.value.copy(
            pendingOperations = records.count { it.dirty },
            conflicts = conflicts.filter { it.kind == "asset" }.map { row ->
                val local = json.decodeFromString<SyncAssetPayload>(row.payload)
                val remote = row.remotePayload?.takeIf(String::isNotBlank)?.let { json.decodeFromString<SyncAssetPayload>(it) }
                AssetSyncConflict(row.id, with(codec) { local.toAsset(row.version, "") },
                    remote?.takeUnless { it.deleted || it.purchaseDate.isBlank() }?.let { with(codec) { it.toAsset(row.remoteVersion!!, "") } }, row.version, row.remoteVersion!!, local.deleted, remote?.deleted == true, remote == null)
            },
            expiryConflicts = conflicts.filter { it.kind == "expiry" }.map { row ->
                val local = json.decodeFromString<SyncExpiryPayload>(row.payload)
                val remote = row.remotePayload?.takeIf(String::isNotBlank)?.let { json.decodeFromString<SyncExpiryPayload>(it) }
                ExpirySyncConflict(row.id, local.name, remote?.name, row.version, row.remoteVersion!!, local.deleted, remote?.deleted == true, remote == null,
                    with(codec) { local.toExpiryItem(row.version) }, remote?.takeUnless { it.deleted || it.packageExpiryDate.isBlank() }?.let { with(codec) { it.toExpiryItem(row.remoteVersion!!) } })
            },
            lastSyncedAt = database.syncDao().session()?.lastSyncedAt?.let(Instant::parse),
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(kotlinx.coroutines.withContext(dispatcher) { block() })
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }
}
