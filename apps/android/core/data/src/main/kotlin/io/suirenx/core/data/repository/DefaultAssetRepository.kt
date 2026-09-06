package io.suirenx.core.data.repository

import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.domain.StorageModeRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import java.time.LocalDate
import javax.inject.Inject

class DefaultAssetRepository @Inject constructor(
    private val local: LocalAssetRepository,
    private val modes: StorageModeRepository,
    private val remote: RemoteAssetSyncStore,
) : AssetRepository {
    private fun useLocal() = modes.mode.value == io.suirenx.core.model.StorageMode.Local

    override suspend fun updateAssetArchive(id: String, archive: Boolean): Result<Asset> =
        if (useLocal()) local.updateAssetArchive(id, archive) else remote.updateArchive(id, archive)

    override suspend fun updateAssetStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> =
        if (useLocal()) local.updateAssetStatus(id, status, retiredDate) else remote.updateStatus(id, status, retiredDate)

    override suspend fun createAsset(asset: NewAsset): Result<Asset> =
        if (useLocal()) local.createAsset(asset) else remote.create(asset)

    override suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset> =
        if (useLocal()) local.updateAsset(id, asset) else remote.update(id, asset)

    override suspend fun getAssets(status: AssetStatus?, includeArchived: Boolean): Result<List<Asset>> =
        if (useLocal()) local.getAssets(status, includeArchived) else remote.list(status, includeArchived)

    override suspend fun getAsset(id: String): Result<Asset> =
        if (useLocal()) local.getAsset(id) else remote.get(id)
}
