package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

enum class SyncConflictResolution {
    KeepLocal,
    KeepRemote,
    KeepBoth,
}

data class AssetSyncConflict(
    val assetId: String,
    val local: Asset,
    val remote: Asset?,
    val baseVersion: Long,
    val remoteVersion: Long,
    val localDeleted: Boolean = false,
    val remoteDeleted: Boolean = false,
    val remoteUnavailable: Boolean = false,
)

data class ExpirySyncConflict(
    val expiryId: String,
    val localName: String,
    val remoteName: String?,
    val baseVersion: Long,
    val remoteVersion: Long,
    val localDeleted: Boolean = false,
    val remoteDeleted: Boolean = false,
    val remoteUnavailable: Boolean = false,
    val local: io.suirenx.core.model.ExpiryItem? = null,
    val remote: io.suirenx.core.model.ExpiryItem? = null,
)

data class RemoteSyncStatus(
    val pendingOperations: Int = 0,
    val conflicts: List<AssetSyncConflict> = emptyList(),
    val expiryConflicts: List<ExpirySyncConflict> = emptyList(),
    val lastSyncedAt: Instant? = null,
    val syncing: Boolean = false,
    val waitingForNetwork: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
)

interface RemoteSyncRepository {
    val status: StateFlow<RemoteSyncStatus>

    fun cancelCurrent() {}

    suspend fun refreshStatus(): Result<Unit>

    suspend fun retry(): Result<Unit>

    suspend fun resolveConflict(assetId: String, resolution: SyncConflictResolution): Result<Unit>

    suspend fun resolveExpiryConflict(expiryId: String, resolution: SyncConflictResolution): Result<Unit>
}
