package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.ExpiryItem

data class LocalDataSnapshot(
    val assets: List<Asset>,
    val expiryItems: List<ExpiryItem>,
)

data class MigrationPreview(
    val localAssetCount: Int,
    val newAssetCount: Int,
    val duplicateAssetCount: Int,
    val assetsToImport: List<Asset>,
    val localExpiryCount: Int = 0,
    val newExpiryCount: Int = 0,
    val duplicateExpiryCount: Int = 0,
    val expiryItemsToImport: List<ExpiryItem> = emptyList(),
)

data class MigrationResult(
    val importedAssetCount: Int,
    val queuedAssetCount: Int,
    val skippedDuplicateCount: Int,
    val failedAssetNames: List<String> = emptyList(),
    val importedExpiryCount: Int = 0,
    val queuedExpiryCount: Int = 0,
    val skippedExpiryDuplicateCount: Int = 0,
    val failedExpiryNames: List<String> = emptyList(),
)

interface RemoteImportRepository {
    suspend fun preview(): Result<MigrationPreview>

    suspend fun import(): Result<MigrationResult>
}
