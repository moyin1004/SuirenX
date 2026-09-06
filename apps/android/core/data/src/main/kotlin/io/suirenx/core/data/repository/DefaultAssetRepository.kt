package io.suirenx.core.data.repository

import io.suirenx.core.data.network.AssetApiProvider
import io.suirenx.core.data.network.AssetDto
import io.suirenx.core.data.network.CreateAssetRequest
import io.suirenx.core.data.network.UpdateAssetRequest
import io.suirenx.core.data.network.UpdateAssetStatusRequest
import io.suirenx.core.domain.AssetRepository
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.model.NewAsset
import java.time.LocalDate
import java.time.Instant
import io.suirenx.core.data.network.UpdateAssetArchiveRequest
import java.util.concurrent.CancellationException
import javax.inject.Inject

class DefaultAssetRepository @Inject constructor(
    private val api: AssetApiProvider,
) : AssetRepository {
    override suspend fun updateAssetArchive(id: String, archive: Boolean): Result<Asset> = try {
        val response = api.current().updateAssetArchive(
            id, UpdateAssetArchiveRequest(if (archive) "ARCHIVE" else "RESTORE"),
        )
        Result.success(response.asset.toDomain())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun updateAssetStatus(id: String, status: AssetStatus, retiredDate: LocalDate?): Result<Asset> = try {
        val response = api.current().updateAssetStatus(
            id, UpdateAssetStatusRequest(status.toApiValue(), retiredDate?.toString().orEmpty()),
        )
        Result.success(response.asset.toDomain())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

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

    override suspend fun updateAsset(id: String, asset: NewAsset): Result<Asset> = try {
        val response = api.current().updateAsset(
            id,
            UpdateAssetRequest(asset.name, asset.priceCents, asset.purchaseDate.toString()),
        )
        Result.success(response.asset.toDomain())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun getAssets(status: AssetStatus?, includeArchived: Boolean): Result<List<Asset>> = try {
        val response = api.current().getAssets(status?.toApiValue(), if (includeArchived) "ALL" else null)
        Result.success(response.assets.map(AssetDto::toDomain))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun getAsset(id: String): Result<Asset> = try {
        Result.success(api.current().getAsset(id).asset.toDomain())
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
    retiredDate = retiredDate.takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
    archivedAt = archivedAt.takeIf { it.isNotEmpty() }?.let(Instant::parse),
)

private fun AssetStatus.toApiValue() = when (this) {
    AssetStatus.Active -> "ACTIVE"
    AssetStatus.Retired -> "RETIRED"
}

