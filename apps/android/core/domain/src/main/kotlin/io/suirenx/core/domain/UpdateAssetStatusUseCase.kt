package io.suirenx.core.domain

import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import java.time.LocalDate
import javax.inject.Inject

class UpdateAssetStatusUseCase @Inject constructor(
    private val repository: AssetRepository,
) {
    suspend operator fun invoke(asset: Asset, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> {
        if (asset.id.isBlank()) return Result.failure(IllegalArgumentException("缺少资产编号"))
        if (status == AssetStatus.Retired &&
            (retiredDate == null || retiredDate < asset.purchaseDate || retiredDate > LocalDate.now())
        ) {
            return Result.failure(IllegalArgumentException("退役日期须在购买日期与今天之间"))
        }
        if (status == AssetStatus.Active && retiredDate != null) {
            return Result.failure(IllegalArgumentException("恢复服役时须清除退役日期"))
        }
        return repository.updateAssetStatus(asset.id, status, retiredDate)
    }
}
