package io.suirenx.core.data.repository

import io.suirenx.core.domain.*
import javax.inject.Inject
import javax.inject.Singleton

/** Compatibility adapter for the existing explicit sync-preview action. */
@Singleton
class RemoteImportRepositoryImpl @Inject constructor(
    private val backups: LocalBackupRepository,
    private val sync: RemoteSyncRepository,
) : RemoteImportRepository {
    override suspend fun preview(): Result<MigrationPreview> = backups.snapshot().map { snapshot ->
        MigrationPreview(snapshot.assets.size, snapshot.assets.size, 0, snapshot.assets,
            snapshot.expiryItems.size, snapshot.expiryItems.size, 0, snapshot.expiryItems)
    }
    override suspend fun import(): Result<MigrationResult> {
        val result = sync.retry()
        return result.map { MigrationResult(0, sync.status.value.pendingOperations, 0) }
    }
}
