package io.suirenx.core.domain

import javax.inject.Inject

class UpdateAssetSalePriceUseCase @Inject constructor(private val repository: AssetRepository) {
    suspend operator fun invoke(id: String, cents: Long?) =
        if (id.isBlank() || (cents != null && cents < 0)) Result.failure(IllegalArgumentException("卖出价格无效"))
        else repository.updateAssetSalePrice(id, cents)
}
