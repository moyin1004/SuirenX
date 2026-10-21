package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import javax.inject.Inject

class UpdateAssetArchiveUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(id: String, archive: Boolean): Result<Asset> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("error_asset_id_required"))
        return repository.updateAssetArchive(id, archive)
    }
}
