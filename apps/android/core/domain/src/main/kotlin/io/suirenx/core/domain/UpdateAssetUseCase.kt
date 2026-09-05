package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.NewAsset
import javax.inject.Inject

class UpdateAssetUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(id: String, asset: NewAsset): Result<Asset> {
        if (id.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing asset id"))
        }
        if (asset.name.isBlank() || asset.priceCents < 0) {
            return Result.failure(IllegalArgumentException("Invalid asset name or price"))
        }
        return repository.updateAsset(id, asset.copy(name = asset.name.trim()))
    }
}
