package io.suirenx.core.domain

import javax.inject.Inject

class DeleteAssetUseCase @Inject constructor(private val repository: AssetRepository) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("缺少资产编号"))
        return repository.deleteAsset(id)
    }
}
