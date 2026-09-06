package io.suirenx.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AssetApi {
    @PUT("api/v1/assets/{id}/archive")
    suspend fun updateAssetArchive(@Path("id") id: String, @Body request: UpdateAssetArchiveRequest): UpdateAssetArchiveResponse

    @PUT("api/v1/assets/{id}/status")
    suspend fun updateAssetStatus(@Path("id") id: String, @Body request: UpdateAssetStatusRequest): UpdateAssetStatusResponse

    @POST("api/v1/assets")
    suspend fun createAsset(@Body request: CreateAssetRequest): CreateAssetResponse

    @GET("api/v1/assets")
    suspend fun getAssets(@Query("status") status: String? = null, @Query("scope") scope: String? = null): AssetListResponse

    @GET("api/v1/assets/{id}")
    suspend fun getAsset(@Path("id") id: String): GetAssetResponse

    @PUT("api/v1/assets/{id}")
    suspend fun updateAsset(@Path("id") id: String, @Body request: UpdateAssetRequest): UpdateAssetResponse
}

@Serializable
data class AssetListResponse(
    val assets: List<AssetDto>,
)

@Serializable
data class AssetDto(
    val id: String,
    val name: String,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("purchase_date") val purchaseDate: String,
    val status: String,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("held_days") val heldDays: Int,
    @SerialName("daily_cost_cents") val dailyCostCents: Long,
    @SerialName("retired_date") val retiredDate: String = "",
    @SerialName("archived_at") val archivedAt: String = "",
    @SerialName("icon_key") val iconKey: String = "devices",
)


@Serializable
data class CreateAssetRequest(
    val name: String,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("purchase_date") val purchaseDate: String,
    @SerialName("icon_key") val iconKey: String,
)

@Serializable
data class CreateAssetResponse(val asset: AssetDto)

@Serializable
data class UpdateAssetRequest(
    val name: String,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("purchase_date") val purchaseDate: String,
    @SerialName("icon_key") val iconKey: String,
)

@Serializable
data class UpdateAssetResponse(val asset: AssetDto)

@Serializable
data class GetAssetResponse(val asset: AssetDto)

@Serializable
data class UpdateAssetStatusRequest(
    val status: String,
    @SerialName("retired_date") val retiredDate: String,
)

@Serializable
data class UpdateAssetStatusResponse(val asset: AssetDto)

@Serializable
data class UpdateAssetArchiveRequest(val action: String)

@Serializable
data class UpdateAssetArchiveResponse(val asset: AssetDto)
