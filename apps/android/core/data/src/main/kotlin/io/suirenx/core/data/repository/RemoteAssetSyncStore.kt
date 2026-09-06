package io.suirenx.core.data.repository

import androidx.room.withTransaction
import io.suirenx.core.data.local.RemoteAssetEntity
import io.suirenx.core.data.local.RemoteDatabase
import io.suirenx.core.data.local.RemoteConflictEntity
import io.suirenx.core.data.local.RemoteExpiryEntity
import io.suirenx.core.data.local.RemoteExpiryConflictEntity
import io.suirenx.core.data.local.RemoteExpiryOutboxEntity
import io.suirenx.core.data.local.RemoteOutboxEntity
import io.suirenx.core.data.local.RemoteSyncStateEntity
import io.suirenx.core.data.network.SyncApiProvider
import io.suirenx.core.data.network.SyncAppliedChange
import io.suirenx.core.data.network.SyncAssetPayload
import io.suirenx.core.data.network.SyncConflict
import io.suirenx.core.data.network.SyncRequest
import io.suirenx.core.data.network.SyncResponse
import io.suirenx.core.data.network.SyncExpiryChange
import io.suirenx.core.data.network.SyncExpiryPayload
import io.suirenx.core.data.network.SyncExpiryConflict
import io.suirenx.core.domain.AuthRepository
import io.suirenx.core.domain.AssetSyncConflict
import io.suirenx.core.domain.ExpirySyncConflict
import io.suirenx.core.domain.RemoteSyncRepository
import io.suirenx.core.domain.RemoteSyncStatus
import io.suirenx.core.domain.SyncConflictResolution
import io.suirenx.core.domain.RemoteExpiryRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.model.NewExpiryItem
import io.suirenx.core.model.NewAsset
import io.suirenx.core.model.calculateDailyCostCents
import io.suirenx.core.model.calculateHeldDays
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RemoteAssetSyncStore @Inject constructor(
    private val database: RemoteDatabase,
    private val apiProvider: SyncApiProvider,
    private val auth: AuthRepository,
    private val json: Json,
    private val clock: Clock,
) : RemoteSyncRepository, RemoteExpiryRepository {
    private val syncMutex = Mutex()
    private val _status = MutableStateFlow(RemoteSyncStatus())
    override val status: StateFlow<RemoteSyncStatus> = _status.asStateFlow()

    suspend fun list(status: AssetStatus?, includeArchived: Boolean): Result<List<Asset>> = attempt {
        val serverUrl = cacheKey()
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error) || database.assets().count(serverUrl) == 0) throw error
        }
        database.assets().getAll(serverUrl).map(::toDomain).filter { asset ->
            (includeArchived || !asset.isArchived) && (status == null || asset.status == status)
        }
    }

    suspend fun get(id: String): Result<Asset> = attempt {
        val serverUrl = cacheKey()
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error)) throw error
        }
        database.assets().getById(serverUrl, id)?.takeUnless { it.deletedAt != null }?.let(::toDomain)
            ?: error("未找到资产")
    }

    suspend fun create(input: NewAsset): Result<Asset> = attempt {
        val serverUrl = cacheKey()
        val now = Instant.now(clock)
        val asset = Asset(
            id = input.id ?: UUID.randomUUID().toString(), name = input.name.trim(), priceCents = input.priceCents,
            purchaseDate = input.purchaseDate, status = AssetStatus.Active, imageUrl = "",
            heldDays = 0, dailyCostCents = 0, iconKey = input.iconKey,
            purchaseChannel = input.purchaseChannel, warrantyEndDate = input.warrantyEndDate,
            notes = input.notes, tags = input.tags, createdAt = now, updatedAt = now,
        )
        enqueue(serverUrl, asset, deleted = false)
        flushOrKeep(serverUrl, asset.id, asset)
    }

    /** Enqueues a complete local record for the explicit local-to-remote import flow. */
    suspend fun importAsset(asset: Asset): Result<Asset> = attempt {
        val serverUrl = cacheKey()
        enqueue(serverUrl, asset, deleted = false, baseVersion = 0)
        flushOrKeep(serverUrl, asset.id, asset)
    }

    suspend fun allForMigration(): Result<List<Asset>> = attempt {
        val serverUrl = cacheKey()
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error)) throw error
        }
        database.assets().getAll(serverUrl).map(::toDomain)
    }

    override suspend fun listExpiry(includeArchived: Boolean): Result<List<ExpiryItem>> = attempt {
        val serverUrl = cacheKey()
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error) || database.expiry().count(serverUrl) == 0) throw error
        }
        database.expiry().getAll(serverUrl).map(::toExpiryDomain).filter { includeArchived || it.archivedAt == null }
    }

    override suspend fun getExpiry(id: String): Result<ExpiryItem> = attempt {
        val serverUrl = cacheKey()
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error)) throw error
        }
        database.expiry().getById(serverUrl, id)?.takeUnless { it.deletedAt != null }?.let(::toExpiryDomain)
            ?: error("未找到用品")
    }

    override suspend fun createExpiry(item: NewExpiryItem): Result<ExpiryItem> = attempt {
        val now = Instant.now(clock)
        val saved = ExpiryItem(
            id = UUID.randomUUID().toString(), name = item.name.trim(), category = item.category.trim(),
            packageExpiryDate = item.packageExpiryDate, openedDate = item.openedDate,
            openedValidityDays = item.openedValidityDays, location = item.location.trim(), notes = item.notes.trim(),
            createdAt = now, updatedAt = now,
        )
        enqueueExpiry(cacheKey(), saved, false, 0)
        flushOrKeepExpiry(cacheKey(), saved.id, saved)
    }

    override suspend fun importItem(item: ExpiryItem): Result<ExpiryItem> = attempt {
        enqueueExpiry(cacheKey(), item, false, 0)
        flushOrKeepExpiry(cacheKey(), item.id, item)
    }

    override suspend fun allExpiryForMigration(): Result<List<ExpiryItem>> = attempt {
        val serverUrl = cacheKey()
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error)) throw error
        }
        database.expiry().getAll(serverUrl).map(::toExpiryDomain)
    }

    override suspend fun updateExpiry(id: String, item: NewExpiryItem): Result<ExpiryItem> = attempt {
        val serverUrl = cacheKey()
        val current = cachedExpiryOrPull(serverUrl, id)
        check(current.archivedAt == null) { "归档用品请先恢复" }
        val updated = current.copy(
            name = item.name.trim(), category = item.category.trim(), packageExpiryDate = item.packageExpiryDate,
            openedDate = item.openedDate, openedValidityDays = item.openedValidityDays,
            location = item.location.trim(), notes = item.notes.trim(), syncVersion = current.syncVersion + 1,
            updatedAt = Instant.now(clock),
        )
        enqueueExpiry(serverUrl, updated, false, current.syncVersion)
        flushOrKeepExpiry(serverUrl, id, updated)
    }

    override suspend fun updateExpiryStatus(id: String, status: ExpiryItemStatus): Result<ExpiryItem> = attempt {
        val serverUrl = cacheKey()
        val current = cachedExpiryOrPull(serverUrl, id)
        check(current.archivedAt == null) { "归档用品请先恢复" }
        val updated = current.copy(status = status, syncVersion = current.syncVersion + 1, updatedAt = Instant.now(clock))
        enqueueExpiry(serverUrl, updated, false, current.syncVersion)
        flushOrKeepExpiry(serverUrl, id, updated)
    }

    override suspend fun updateExpiryArchive(id: String, archive: Boolean): Result<ExpiryItem> = attempt {
        val serverUrl = cacheKey()
        val current = cachedExpiryOrPull(serverUrl, id)
        val updated = current.copy(
            archivedAt = if (archive) current.archivedAt ?: Instant.now(clock) else null,
            syncVersion = current.syncVersion + 1, updatedAt = Instant.now(clock),
        )
        enqueueExpiry(serverUrl, updated, false, current.syncVersion)
        flushOrKeepExpiry(serverUrl, id, updated)
    }

    override suspend fun refreshStatus(): Result<Unit> = attempt {
        if (auth.state.value.authenticated && auth.state.value.username.isNotBlank()) {
            updateStatus(cacheKey())
        } else {
            _status.value = RemoteSyncStatus()
        }
    }

    override suspend fun retry(): Result<Unit> = attempt {
        val serverUrl = cacheKey()
        sync(serverUrl)
        updateStatus(serverUrl)
    }

    override suspend fun resolveConflict(assetId: String, resolution: SyncConflictResolution): Result<Unit> = attempt {
        val serverUrl = cacheKey()
        syncMutex.withLock {
            val conflict = database.conflicts().getAll(serverUrl).firstOrNull { it.assetId == assetId }
                ?: error("未找到同步冲突")
            when (resolution) {
                SyncConflictResolution.KeepRemote -> {
                    if (conflict.remotePayloadJson.isBlank()) {
                        database.assets().delete(serverUrl, assetId)
                    } else {
                        val remote = json.decodeFromString<SyncAssetPayload>(conflict.remotePayloadJson)
                        if (remote.id.isBlank() || remote.purchaseDate.isBlank()) {
                            database.assets().delete(serverUrl, assetId)
                        } else {
                            applyChange(
                                serverUrl,
                                SyncAppliedChange(
                                    0, conflict.remoteVersion,
                                    if (remote.deleted) Instant.now(clock).toString() else "",
                                    remote,
                                ),
                            )
                        }
                    }
                    database.outbox().deleteForAsset(serverUrl, assetId)
                }
                SyncConflictResolution.KeepLocal -> {
                    val local = json.decodeFromString<SyncAssetPayload>(conflict.localPayloadJson)
                    val operationId = UUID.randomUUID().toString()
                    val retryPayload = local.copy(baseVersion = conflict.remoteVersion)
                    database.outbox().deleteForAsset(serverUrl, assetId)
                    database.outbox().insert(
                        RemoteOutboxEntity(
                            serverUrl = serverUrl, operationId = operationId, assetId = assetId,
                            baseVersion = conflict.remoteVersion,
                            payloadJson = json.encodeToString(retryPayload), idempotencyKey = operationId,
                            createdAt = Instant.now(clock).toString(),
                        ),
                    )
                }
            }
            database.conflicts().delete(serverUrl, assetId)
            updateStatusLocked(serverUrl)
        }
        if (resolution == SyncConflictResolution.KeepLocal) {
            try {
                sync(serverUrl)
            } catch (error: Exception) {
                if (!isOffline(error)) throw error
            }
            updateStatus(serverUrl)
        }
    }

    override suspend fun resolveExpiryConflict(expiryId: String, resolution: SyncConflictResolution): Result<Unit> = attempt {
        val serverUrl = cacheKey()
        syncMutex.withLock {
            val conflict = database.expiryConflicts().getAll(serverUrl).firstOrNull { it.expiryId == expiryId }
                ?: error("未找到用品同步冲突")
            when (resolution) {
                SyncConflictResolution.KeepRemote -> {
                    if (conflict.remotePayloadJson.isBlank()) {
                        database.expiry().delete(serverUrl, expiryId)
                    } else {
                        val remote = json.decodeFromString<SyncExpiryPayload>(conflict.remotePayloadJson)
                        if (remote.id.isBlank() || remote.packageExpiryDate.isBlank()) {
                            database.expiry().delete(serverUrl, expiryId)
                        } else {
                            applyExpiryChange(
                                serverUrl,
                                SyncExpiryChange(0, conflict.remoteVersion, if (remote.deleted) Instant.now(clock).toString() else "", remote),
                            )
                        }
                    }
                    database.expiryOutbox().deleteForExpiry(serverUrl, expiryId)
                }
                SyncConflictResolution.KeepLocal -> {
                    val local = json.decodeFromString<SyncExpiryPayload>(conflict.localPayloadJson)
                    val operationId = UUID.randomUUID().toString()
                    val retryPayload = local.copy(baseVersion = conflict.remoteVersion)
                    database.expiryOutbox().deleteForExpiry(serverUrl, expiryId)
                    database.expiryOutbox().insert(
                        RemoteExpiryOutboxEntity(
                            serverUrl = serverUrl, operationId = operationId, expiryId = expiryId,
                            baseVersion = conflict.remoteVersion, payloadJson = json.encodeToString(retryPayload),
                            idempotencyKey = operationId, createdAt = Instant.now(clock).toString(),
                        ),
                    )
                }
            }
            database.expiryConflicts().delete(serverUrl, expiryId)
            updateStatusLocked(serverUrl)
        }
        if (resolution == SyncConflictResolution.KeepLocal) {
            try {
                sync(serverUrl)
            } catch (error: Exception) {
                if (!isOffline(error)) throw error
            }
            updateStatus(serverUrl)
        }
    }

    suspend fun update(id: String, input: NewAsset): Result<Asset> = attempt {
        val serverUrl = cacheKey()
        val current = cachedOrPull(serverUrl, id)
        check(!current.isArchived) { "归档资产请先恢复" }
        val updated = current.copy(
            name = input.name.trim(), priceCents = input.priceCents, purchaseDate = input.purchaseDate,
            iconKey = input.iconKey, purchaseChannel = input.purchaseChannel,
            warrantyEndDate = input.warrantyEndDate, notes = input.notes, tags = input.tags,
            syncVersion = current.syncVersion + 1, updatedAt = Instant.now(clock),
        )
        enqueue(serverUrl, updated, deleted = false, baseVersion = current.syncVersion)
        flushOrKeep(serverUrl, id, updated)
    }

    suspend fun updateStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> = attempt {
        val serverUrl = cacheKey()
        val current = cachedOrPull(serverUrl, id)
        check(!current.isArchived) { "归档资产请先恢复" }
        val updated = current.copy(
            status = status, retiredDate = retiredDate, syncVersion = current.syncVersion + 1,
            updatedAt = Instant.now(clock),
        )
        enqueue(serverUrl, updated, deleted = false, baseVersion = current.syncVersion)
        flushOrKeep(serverUrl, id, updated)
    }

    suspend fun updateArchive(id: String, archive: Boolean): Result<Asset> = attempt {
        val serverUrl = cacheKey()
        val current = cachedOrPull(serverUrl, id)
        val updated = current.copy(
            archivedAt = if (archive) current.archivedAt ?: Instant.now(clock) else null,
            syncVersion = current.syncVersion + 1, updatedAt = Instant.now(clock),
        )
        enqueue(serverUrl, updated, deleted = false, baseVersion = current.syncVersion)
        flushOrKeep(serverUrl, id, updated)
    }

    private suspend fun cachedOrPull(serverUrl: String, id: String): Asset {
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error)) throw error
        }
        return database.assets().getById(serverUrl, id)?.takeUnless { it.deletedAt != null }?.let(::toDomain)
            ?: error("未找到资产")
    }

    private suspend fun flushOrKeep(serverUrl: String, id: String, optimistic: Asset): Asset {
        return try {
            sync(serverUrl)
            database.assets().getById(serverUrl, id)?.takeUnless { it.deletedAt != null }?.let(::toDomain) ?: optimistic
        } catch (error: Exception) {
            if (isOffline(error)) optimistic else throw error
        }
    }

    private suspend fun cachedExpiryOrPull(serverUrl: String, id: String): ExpiryItem {
        try {
            sync(serverUrl)
        } catch (error: Exception) {
            if (!isOffline(error)) throw error
        }
        return database.expiry().getById(serverUrl, id)?.takeUnless { it.deletedAt != null }?.let(::toExpiryDomain)
            ?: error("未找到用品")
    }

    private suspend fun flushOrKeepExpiry(serverUrl: String, id: String, optimistic: ExpiryItem): ExpiryItem {
        return try {
            sync(serverUrl)
            database.expiry().getById(serverUrl, id)?.takeUnless { it.deletedAt != null }?.let(::toExpiryDomain) ?: optimistic
        } catch (error: Exception) {
            if (isOffline(error)) optimistic else throw error
        }
    }

    private suspend fun enqueue(serverUrl: String, asset: Asset, deleted: Boolean, baseVersion: Long = asset.syncVersion) {
        val operationId = UUID.randomUUID().toString()
        val payload = toPayload(asset, deleted, baseVersion)
        database.withTransaction {
            database.assets().upsert(toEntity(serverUrl, asset, deleted))
            database.outbox().insert(
                RemoteOutboxEntity(
                    serverUrl = serverUrl, operationId = operationId, assetId = asset.id,
                    baseVersion = baseVersion,
                    payloadJson = json.encodeToString(payload), idempotencyKey = operationId,
                    createdAt = Instant.now(clock).toString(),
                ),
            )
        }
    }

    private suspend fun enqueueExpiry(serverUrl: String, item: ExpiryItem, deleted: Boolean, baseVersion: Long) {
        val operationId = UUID.randomUUID().toString()
        val payload = toExpiryPayload(item, deleted, baseVersion)
        database.withTransaction {
            database.expiry().upsert(toExpiryEntity(serverUrl, item, deleted))
            database.expiryOutbox().insert(
                RemoteExpiryOutboxEntity(
                    serverUrl = serverUrl, operationId = operationId, expiryId = item.id,
                    baseVersion = baseVersion, payloadJson = json.encodeToString(payload),
                    idempotencyKey = operationId, createdAt = Instant.now(clock).toString(),
                ),
            )
        }
    }

    private fun cacheKey(): String {
        check(auth.state.value.authenticated && auth.state.value.username.isNotBlank()) { "请先登录服务器账号" }
        return "${apiProvider.currentUrl()}|${auth.state.value.username}"
    }

    private suspend fun sync(serverUrl: String) = syncMutex.withLock {
        syncLocked(serverUrl)
    }

    private suspend fun syncLocked(serverUrl: String) {
        var state = database.syncState().get(serverUrl) ?: RemoteSyncStateEntity(serverUrl)
        val pending = database.outbox().getAll(serverUrl)
        for (operation in pending) {
            val response = apiProvider.current().sync(
                SyncRequest(
                    cursor = state.cursor, expiryCursor = state.expiryCursor,
                    idempotencyKey = operation.idempotencyKey,
                    changes = listOf(json.decodeFromString(operation.payloadJson)),
                ),
            )
            if (response.conflicts.any { it.id == operation.assetId }) {
                val conflict = response.conflicts.first { it.id == operation.assetId }
                recordConflict(serverUrl, operation, conflict)
                applyResponse(serverUrl, response, skipAssetIds = setOf(operation.assetId))
                updateStatusLocked(serverUrl)
                throw SyncConflictException(operation.assetId, conflict)
            }
            applyResponse(serverUrl, response)
            database.outbox().delete(serverUrl, operation.operationId)
            state = state.copy(cursor = response.nextCursor, expiryCursor = response.nextExpiryCursor)
            database.syncState().save(state)
        }
        val pendingExpiry = database.expiryOutbox().getAll(serverUrl)
        for (operation in pendingExpiry) {
            val response = apiProvider.current().sync(
                SyncRequest(
                    cursor = state.cursor, expiryCursor = state.expiryCursor,
                    idempotencyKey = operation.idempotencyKey,
                    expiryChanges = listOf(json.decodeFromString(operation.payloadJson)),
                ),
            )
            if (response.expiryConflicts.any { it.id == operation.expiryId }) {
                val conflict = response.expiryConflicts.first { it.id == operation.expiryId }
                recordExpiryConflict(serverUrl, operation, conflict)
                applyResponse(serverUrl, response, skipExpiryIds = setOf(operation.expiryId))
                updateStatusLocked(serverUrl)
                throw SyncExpiryConflictException(operation.expiryId)
            }
            applyResponse(serverUrl, response)
            database.expiryOutbox().delete(serverUrl, operation.operationId)
            state = state.copy(cursor = response.nextCursor, expiryCursor = response.nextExpiryCursor)
            database.syncState().save(state)
        }
        val response = apiProvider.current().sync(
            SyncRequest(
                cursor = state.cursor, expiryCursor = state.expiryCursor,
                idempotencyKey = "pull-${UUID.randomUUID()}",
            ),
        )
        applyResponse(serverUrl, response)
            database.syncState().save(state.copy(cursor = response.nextCursor, expiryCursor = response.nextExpiryCursor, lastSyncedAt = Instant.now(clock).toString()))
        updateStatusLocked(serverUrl)
    }

    private suspend fun applyResponse(
        serverUrl: String,
        response: SyncResponse,
        skipAssetIds: Set<String> = emptySet(),
        skipExpiryIds: Set<String> = emptySet(),
    ) {
        for (change in response.changes) if (change.asset.id !in skipAssetIds) applyChange(serverUrl, change)
        for (change in response.applied) if (change.asset.id !in skipAssetIds) applyChange(serverUrl, change)
        for (change in response.expiryChanges) if (change.item.id !in skipExpiryIds) applyExpiryChange(serverUrl, change)
        for (change in response.appliedExpiry) if (change.item.id !in skipExpiryIds) applyExpiryChange(serverUrl, change)
    }

    private suspend fun recordConflict(serverUrl: String, operation: RemoteOutboxEntity, conflict: SyncConflict) {
        val remote = conflict.remote?.takeUnless { it.id.isBlank() || it.purchaseDate.isBlank() }
        database.conflicts().upsert(
            RemoteConflictEntity(
                serverUrl = serverUrl, assetId = operation.assetId,
                baseVersion = conflict.baseVersion, remoteVersion = conflict.remoteVersion,
                localPayloadJson = operation.payloadJson,
                remotePayloadJson = remote?.let(json::encodeToString).orEmpty(), createdAt = Instant.now(clock).toString(),
            ),
        )
    }

    private suspend fun recordExpiryConflict(serverUrl: String, operation: RemoteExpiryOutboxEntity, conflict: SyncExpiryConflict) {
        val remote = conflict.remote?.takeUnless { it.id.isBlank() || it.packageExpiryDate.isBlank() }
        database.expiryConflicts().upsert(
            RemoteExpiryConflictEntity(
                serverUrl = serverUrl, expiryId = operation.expiryId,
                baseVersion = conflict.baseVersion, remoteVersion = conflict.remoteVersion,
                localPayloadJson = operation.payloadJson,
                remotePayloadJson = remote?.let(json::encodeToString).orEmpty(), createdAt = Instant.now(clock).toString(),
            ),
        )
    }

    private suspend fun updateStatus(serverUrl: String) = syncMutex.withLock {
        updateStatusLocked(serverUrl)
    }

    private suspend fun updateStatusLocked(serverUrl: String) {
        val conflicts = database.conflicts().getAll(serverUrl).mapNotNull { conflict ->
            val localPayload = runCatching {
                json.decodeFromString<SyncAssetPayload>(conflict.localPayloadJson)
            }.getOrNull() ?: return@mapNotNull null
            val remotePayload = runCatching {
                json.decodeFromString<SyncAssetPayload>(conflict.remotePayloadJson)
            }.getOrNull()?.takeUnless { it.id.isBlank() || it.purchaseDate.isBlank() }
            AssetSyncConflict(
                assetId = conflict.assetId,
                local = localPayload.toAsset(conflict.baseVersion, ""),
                remote = remotePayload?.takeUnless { it.deleted }?.toAsset(conflict.remoteVersion, ""),
                baseVersion = conflict.baseVersion,
                remoteVersion = conflict.remoteVersion,
            )
        }
        val expiryConflicts = database.expiryConflicts().getAll(serverUrl).mapNotNull { conflict ->
            val local = runCatching { json.decodeFromString<SyncExpiryPayload>(conflict.localPayloadJson) }.getOrNull()
                ?: return@mapNotNull null
            val remote = runCatching { json.decodeFromString<SyncExpiryPayload>(conflict.remotePayloadJson) }.getOrNull()
            ExpirySyncConflict(
                expiryId = conflict.expiryId, localName = local.name, remoteName = remote?.name,
                baseVersion = conflict.baseVersion, remoteVersion = conflict.remoteVersion,
            )
        }
        val lastSyncedAt = database.syncState().get(serverUrl)?.lastSyncedAt?.let(Instant::parse)
        _status.value = RemoteSyncStatus(
            database.outbox().count(serverUrl) + database.expiryOutbox().getAll(serverUrl).size,
            conflicts, expiryConflicts,
            lastSyncedAt,
        )
    }

    private suspend fun applyChange(serverUrl: String, change: SyncAppliedChange) {
        val payload = change.asset
        val asset = payload.toAsset(change.version, change.deletedAt)
        database.assets().upsert(toEntity(serverUrl, asset, change.deletedAt.isNotBlank()))
    }

    private suspend fun applyExpiryChange(serverUrl: String, change: SyncExpiryChange) {
        val item = change.item.toExpiryItem(change.version)
        database.expiry().upsert(toExpiryEntity(serverUrl, item, change.deletedAt.isNotBlank()))
    }

    private fun toEntity(serverUrl: String, asset: Asset, deleted: Boolean): RemoteAssetEntity = RemoteAssetEntity(
        serverUrl = serverUrl, id = asset.id, name = asset.name, priceCents = asset.priceCents,
        purchaseDate = asset.purchaseDate.toString(), status = asset.status.toApiValue(),
        retiredDate = asset.retiredDate?.toString(), archivedAt = asset.archivedAt?.toString(),
        iconKey = asset.iconKey, imageUrl = asset.imageUrl, purchaseChannel = asset.purchaseChannel,
        warrantyEndDate = asset.warrantyEndDate?.toString(), notes = asset.notes,
        tagsJson = json.encodeToString(asset.tags), syncVersion = asset.syncVersion,
        deletedAt = if (deleted) asset.updatedAt.toString() else null,
        createdAt = asset.createdAt.toString(), updatedAt = asset.updatedAt.toString(),
    )

    private fun toPayload(asset: Asset, deleted: Boolean, baseVersion: Long) = SyncAssetPayload(
        id = asset.id, baseVersion = baseVersion, deleted = deleted,
        name = asset.name, priceCents = asset.priceCents, purchaseDate = asset.purchaseDate.toString(),
        status = asset.status.toApiValue(), imageUrl = asset.imageUrl,
        retiredDate = asset.retiredDate?.toString().orEmpty(), archivedAt = asset.archivedAt?.toString().orEmpty(),
        iconKey = asset.iconKey, purchaseChannel = asset.purchaseChannel.orEmpty(),
        warrantyEndDate = asset.warrantyEndDate?.toString().orEmpty(), notes = asset.notes, tags = asset.tags,
    )

    private fun toExpiryEntity(serverUrl: String, item: ExpiryItem, deleted: Boolean) = RemoteExpiryEntity(
        serverUrl = serverUrl, id = item.id, name = item.name, category = item.category,
        packageExpiryDate = item.packageExpiryDate.toString(), openedDate = item.openedDate?.toString(),
        openedValidityDays = item.openedValidityDays, location = item.location, notes = item.notes,
        status = item.status.toApiValue(), archivedAt = item.archivedAt?.toString(), syncVersion = item.syncVersion,
        deletedAt = if (deleted) item.updatedAt.toString() else null,
        createdAt = item.createdAt.toString(), updatedAt = item.updatedAt.toString(),
    )

    private fun toExpiryPayload(item: ExpiryItem, deleted: Boolean, baseVersion: Long) = SyncExpiryPayload(
        id = item.id, baseVersion = baseVersion, deleted = deleted, name = item.name, category = item.category,
        packageExpiryDate = item.packageExpiryDate.toString(), openedDate = item.openedDate?.toString().orEmpty(),
        openedValidityDays = item.openedValidityDays ?: 0, location = item.location, notes = item.notes,
        status = item.status.toApiValue(), archivedAt = item.archivedAt?.toString().orEmpty(),
    )

    private fun toDomain(entity: RemoteAssetEntity): Asset {
        val status = if (entity.status == "RETIRED") AssetStatus.Retired else AssetStatus.Active
        val retiredDate = entity.retiredDate?.let(LocalDate::parse)
        val heldDays = calculateHeldDays(entity.purchaseDate.toDate(), status, retiredDate, today())
        return Asset(
            id = entity.id, name = entity.name, priceCents = entity.priceCents,
            purchaseDate = entity.purchaseDate.toDate(), status = status, imageUrl = entity.imageUrl,
            heldDays = heldDays, dailyCostCents = calculateDailyCostCents(entity.priceCents, heldDays),
            retiredDate = retiredDate, archivedAt = entity.archivedAt?.let(Instant::parse), iconKey = entity.iconKey,
            purchaseChannel = entity.purchaseChannel, warrantyEndDate = entity.warrantyEndDate?.let(LocalDate::parse),
            notes = entity.notes, tags = json.decodeFromString(entity.tagsJson),
            createdAt = Instant.parse(entity.createdAt), updatedAt = Instant.parse(entity.updatedAt),
            syncVersion = entity.syncVersion,
        )
    }

    private fun SyncAssetPayload.toAsset(version: Long, deletedAt: String): Asset {
        val statusValue = if (status == "RETIRED") AssetStatus.Retired else AssetStatus.Active
        val retired = retiredDate.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        val purchase = LocalDate.parse(purchaseDate)
        val heldDays = calculateHeldDays(purchase, statusValue, retired, today())
        val timestamp = deletedAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.now(clock)
        return Asset(
            id = id, name = name, priceCents = priceCents, purchaseDate = purchase, status = statusValue,
            imageUrl = imageUrl, heldDays = heldDays, dailyCostCents = calculateDailyCostCents(priceCents, heldDays),
            retiredDate = retired, archivedAt = archivedAt.takeIf(String::isNotBlank)?.let(Instant::parse),
            iconKey = iconKey.ifBlank { "devices" }, purchaseChannel = purchaseChannel.takeIf(String::isNotBlank),
            warrantyEndDate = warrantyEndDate.takeIf(String::isNotBlank)?.let(LocalDate::parse), notes = notes,
            tags = tags, createdAt = timestamp, updatedAt = timestamp, syncVersion = version,
        )
    }

    private fun SyncExpiryPayload.toExpiryItem(version: Long): ExpiryItem {
        val timestamp = Instant.now(clock)
        return ExpiryItem(
            id = id, name = name, category = category, packageExpiryDate = LocalDate.parse(packageExpiryDate),
            openedDate = openedDate.takeIf(String::isNotBlank)?.let(LocalDate::parse),
            openedValidityDays = openedValidityDays.takeIf { it > 0 }, location = location, notes = notes,
            status = when (status) {
                "USED_UP" -> ExpiryItemStatus.UsedUp
                "DISCARDED" -> ExpiryItemStatus.Discarded
                else -> ExpiryItemStatus.InUse
            }, archivedAt = archivedAt.takeIf(String::isNotBlank)?.let(Instant::parse),
            createdAt = timestamp, updatedAt = timestamp, syncVersion = version,
        )
    }

    private fun toExpiryDomain(entity: RemoteExpiryEntity): ExpiryItem = ExpiryItem(
        id = entity.id, name = entity.name, category = entity.category,
        packageExpiryDate = LocalDate.parse(entity.packageExpiryDate),
        openedDate = entity.openedDate?.let(LocalDate::parse), openedValidityDays = entity.openedValidityDays,
        location = entity.location, notes = entity.notes,
        status = when (entity.status) {
            "USED_UP" -> ExpiryItemStatus.UsedUp
            "DISCARDED" -> ExpiryItemStatus.Discarded
            else -> ExpiryItemStatus.InUse
        }, archivedAt = entity.archivedAt?.let(Instant::parse),
        createdAt = Instant.parse(entity.createdAt), updatedAt = Instant.parse(entity.updatedAt), syncVersion = entity.syncVersion,
    )

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
    private fun String.toDate() = LocalDate.parse(this)
    private fun AssetStatus.toApiValue() = if (this == AssetStatus.Retired) "RETIRED" else "ACTIVE"
    private fun ExpiryItemStatus.toApiValue() = when (this) {
        ExpiryItemStatus.InUse -> "IN_USE"
        ExpiryItemStatus.UsedUp -> "USED_UP"
        ExpiryItemStatus.Discarded -> "DISCARDED"
    }

    private fun isOffline(error: Exception): Boolean = error is IOException

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}

class SyncConflictException(val assetId: String, val conflict: SyncConflict) : IllegalStateException("资产发生同步冲突：$assetId")

class SyncExpiryConflictException(val expiryId: String) : IllegalStateException("用品发生同步冲突：$expiryId")
