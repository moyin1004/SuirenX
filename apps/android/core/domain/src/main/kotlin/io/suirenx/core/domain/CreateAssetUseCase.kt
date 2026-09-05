package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.NewAsset
import javax.inject.Inject

class CreateAssetUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(asset: NewAsset): Result<Asset> {
        if (asset.name.isBlank() || asset.priceCents < 0) {
            return Result.failure(IllegalArgumentException("Invalid asset name or price"))
        }
        return repository.createAsset(asset.copy(name = asset.name.trim()))
    }
}
