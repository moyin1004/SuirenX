package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import java.time.LocalDate
import javax.inject.Inject

class UpdateAssetStatusUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(asset: Asset, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> {
        if (asset.id.isBlank()) return Result.failure(IllegalArgumentException("error_asset_id_required"))
        if (status == AssetStatus.Retired &&
            (retiredDate == null || retiredDate < asset.purchaseDate || retiredDate > LocalDate.now())
        ) {
            return Result.failure(IllegalArgumentException("error_retirement_date_invalid"))
        }
        if (status == AssetStatus.Active && retiredDate != null) {
            return Result.failure(IllegalArgumentException("error_reactivation_retirement_date"))
        }
        return repository.updateAssetStatus(asset.id, status, retiredDate)
    }
}
