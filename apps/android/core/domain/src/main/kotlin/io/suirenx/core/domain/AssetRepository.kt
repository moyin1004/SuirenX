package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import java.time.LocalDate

interface AssetRepository {
    suspend fun updateAssetArchive(id: String, archive: Boolean): Result<Asset>

    suspend fun updateAssetStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset>

    suspend fun createAsset(asset: NewAsset): Result<Asset>

    suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset>

    suspend fun getAssets(status: AssetStatus? = null, includeArchived: Boolean = false): Result<List<Asset>>

    suspend fun getAsset(id: String): Result<Asset>
}

