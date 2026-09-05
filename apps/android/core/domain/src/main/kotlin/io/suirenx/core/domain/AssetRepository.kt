package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset

interface AssetRepository {
    suspend fun createAsset(asset: NewAsset): Result<Asset>

    suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset>

    suspend fun getAssets(status: AssetStatus? = null): Result<List<Asset>>

    suspend fun getAsset(id: String): Result<Asset>
}

