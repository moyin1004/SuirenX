package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import javax.inject.Inject

class GetAssetUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(id: String): Result<Asset> =
        if (id.isBlank()) {
            Result.failure(IllegalArgumentException("Missing asset id"))
        } else {
            repository.getAsset(id)
        }
}
