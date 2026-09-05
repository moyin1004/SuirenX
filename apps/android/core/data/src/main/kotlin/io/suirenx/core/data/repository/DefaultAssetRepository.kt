package io.suirenx.core.data.repository

import io.suirenx.core.data.network.AssetApiProvider
import io.suirenx.core.data.network.AssetDto
import io.suirenx.core.data.network.CreateAssetRequest
import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import java.time.LocalDate
import java.util.concurrent.CancellationException
import javax.inject.Inject

class DefaultAssetRepository @Inject constructor(
    private val api: AssetApiProvider,
) : AssetRepository {
    override suspend fun createAsset(asset: NewAsset): Result<Asset> = try {
        val response = api.current().createAsset(
            CreateAssetRequest(asset.name, asset.priceCents, asset.purchaseDate.toString()),
        )
        Result.success(response.asset.toDomain())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun getAssets(status: AssetStatus?): Result<List<Asset>> = try {
        val response = api.current().getAssets(status?.toApiValue())
        Result.success(response.assets.map(AssetDto::toDomain))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private fun AssetDto.toDomain() = Asset(
    id = id,
    name = name,
    priceCents = priceCents,
    purchaseDate = LocalDate.parse(purchaseDate),
    status = if (status == "RETIRED") AssetStatus.Retired else AssetStatus.Active,
    imageUrl = imageUrl,
    heldDays = heldDays,
    dailyCostCents = dailyCostCents,
)

private fun AssetStatus.toApiValue() = when (this) {
    AssetStatus.Active -> "ACTIVE"
    AssetStatus.Retired -> "RETIRED"
}

