package io.suirenx.core.data.repository

import io.suirenx.core.domain.LocalBackupRepository
import io.suirenx.core.domain.MigrationPreview
import io.suirenx.core.domain.MigrationResult
import io.suirenx.core.domain.RemoteImportRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.ExpiryItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class RemoteImportRepositoryImpl @Inject constructor(
    private val backups: LocalBackupRepository,
    private val remote: RemoteAssetSyncStore,
) : RemoteImportRepository {
    override suspend fun preview(): Result<MigrationPreview> = attempt {
        val snapshot = backups.snapshot().getOrThrow()
        val local = snapshot.assets
        val localExpiry = snapshot.expiryItems
        val remoteAssets = remote.allForMigration().getOrThrow()
        val remoteExpiry = remote.allExpiryForMigration().getOrThrow()
        val remoteIds = remoteAssets.mapTo(HashSet()) { it.id }
        val remoteFingerprints = remoteAssets.mapTo(HashSet(), ::fingerprint)
        val toImport = local.filter { it.id !in remoteIds && fingerprint(it) !in remoteFingerprints }
        val remoteExpiryIds = remoteExpiry.mapTo(HashSet()) { it.id }
        val remoteExpiryFingerprints = remoteExpiry.mapTo(HashSet(), ::expiryFingerprint)
        val expiryToImport = localExpiry.filter { it.id !in remoteExpiryIds && expiryFingerprint(it) !in remoteExpiryFingerprints }
        MigrationPreview(
            localAssetCount = local.size,
            newAssetCount = toImport.size,
            duplicateAssetCount = local.size - toImport.size,
            assetsToImport = toImport,
            localExpiryCount = localExpiry.size,
            newExpiryCount = expiryToImport.size,
            duplicateExpiryCount = localExpiry.size - expiryToImport.size,
            expiryItemsToImport = expiryToImport,
        )
    }

    override suspend fun import(): Result<MigrationResult> = attempt {
        backups.createSafetyBackup().getOrThrow()
        val snapshot = backups.snapshot().getOrThrow()
        val local = snapshot.assets
        val localExpiry = snapshot.expiryItems
        val remoteAssets = remote.allForMigration().getOrThrow()
        val remoteExpiry = remote.allExpiryForMigration().getOrThrow()
        val remoteIds = remoteAssets.mapTo(HashSet()) { it.id }
        val remoteFingerprints = remoteAssets.mapTo(HashSet(), ::fingerprint)
        var imported = 0
        var queued = 0
        var skipped = 0
        val failures = mutableListOf<String>()
        local.forEach { asset ->
            if (asset.id in remoteIds || fingerprint(asset) in remoteFingerprints) {
                skipped++
                return@forEach
            }
            remote.importAsset(asset).fold(
                onSuccess = {
                    imported++
                    // An offline import is accepted into the durable outbox. It is
                    // counted separately so the UI can explain that upload is pending.
                    if (it.syncVersion == 0L) queued++
                },
                onFailure = { failures += asset.name },
            )
        }
        val remoteExpiryIds = remoteExpiry.mapTo(HashSet()) { it.id }
        val remoteExpiryFingerprints = remoteExpiry.mapTo(HashSet(), ::expiryFingerprint)
        var importedExpiry = 0
        var queuedExpiry = 0
        var skippedExpiry = 0
        val expiryFailures = mutableListOf<String>()
        localExpiry.forEach { item ->
            if (item.id in remoteExpiryIds || expiryFingerprint(item) in remoteExpiryFingerprints) {
                skippedExpiry++
                return@forEach
            }
            remote.importItem(item).fold(
                onSuccess = {
                    importedExpiry++
                    if (it.syncVersion == 0L) queuedExpiry++
                },
                onFailure = { expiryFailures += item.name },
            )
        }
        MigrationResult(imported, queued, skipped, failures, importedExpiry, queuedExpiry, skippedExpiry, expiryFailures)
    }

    private fun fingerprint(asset: Asset): String = listOf(
        asset.name.trim().lowercase(Locale.ROOT), asset.priceCents, asset.purchaseDate,
    ).joinToString("|")

    private fun expiryFingerprint(item: ExpiryItem): String = listOf(
        item.name.trim().lowercase(Locale.ROOT), item.packageExpiryDate,
    ).joinToString("|")

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
