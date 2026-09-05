package io.suirenx.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

interface AssetApi {
    @POST("api/v1/assets")
    suspend fun createAsset(@Body request: CreateAssetRequest): CreateAssetResponse

    @GET("api/v1/assets")
    suspend fun getAssets(@Query("status") status: String? = null): AssetListResponse
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
)


@Serializable
data class CreateAssetRequest(
    val name: String,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("purchase_date") val purchaseDate: String,
)

@Serializable
data class CreateAssetResponse(val asset: AssetDto)
