package io.suirenx.core.data.repository

import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import java.time.LocalDate
import javax.inject.Inject

class DefaultAssetRepository @Inject constructor(
    private val scheduler: io.suirenx.core.domain.SyncScheduleRepository,
    private val local: LocalAssetRepository,
) : AssetRepository {
    override suspend fun deleteAsset(id: String): Result<Unit> =
        local.deleteAsset(id).also { if (it.isSuccess) scheduler.onLocalChange() }


    override suspend fun updateAssetArchive(id: String, archive: Boolean): Result<Asset> =
        local.updateAssetArchive(id, archive).also { if (it.isSuccess) scheduler.onLocalChange() }

    override suspend fun updateAssetStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> =
        local.updateAssetStatus(id, status, retiredDate).also { if (it.isSuccess) scheduler.onLocalChange() }

    override suspend fun createAsset(asset: NewAsset): Result<Asset> =
        local.createAsset(asset).also { if (it.isSuccess) scheduler.onLocalChange() }

    override suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset> =
        local.updateAsset(id, asset).also { if (it.isSuccess) scheduler.onLocalChange() }

    override suspend fun getAssets(status: AssetStatus?, includeArchived: Boolean): Result<List<Asset>> =
        local.getAssets(status, includeArchived)

    override suspend fun getAsset(id: String): Result<Asset> =
        local.getAsset(id)
}
