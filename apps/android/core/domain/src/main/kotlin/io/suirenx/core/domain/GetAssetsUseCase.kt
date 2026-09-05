package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import javax.inject.Inject

class GetAssetsUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(status: AssetStatus? = null): Result<List<Asset>> =
        repository.getAssets(status)
}

