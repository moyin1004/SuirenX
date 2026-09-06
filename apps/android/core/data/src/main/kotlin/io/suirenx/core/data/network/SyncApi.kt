package io.suirenx.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {
    @POST("api/v1/sync/assets")
    suspend fun sync(@Body request: SyncRequest): SyncResponse
}

@Serializable
data class SyncRequest(
    val cursor: Long = 0,
    @SerialName("expiry_cursor") val expiryCursor: Long = 0,
    @SerialName("idempotency_key") val idempotencyKey: String,
    val changes: List<SyncAssetPayload> = emptyList(),
    @SerialName("expiry_changes") val expiryChanges: List<SyncExpiryPayload> = emptyList(),
)

@Serializable
data class SyncAssetPayload(
    val id: String,
    @SerialName("base_version") val baseVersion: Long = 0,
    val deleted: Boolean = false,
    val name: String = "",
    @SerialName("price_cents") val priceCents: Long = 0,
    @SerialName("purchase_date") val purchaseDate: String = "",
    val status: String = "ACTIVE",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("retired_date") val retiredDate: String = "",
    @SerialName("archived_at") val archivedAt: String = "",
    @SerialName("icon_key") val iconKey: String = "devices",
    @SerialName("purchase_channel") val purchaseChannel: String = "",
    @SerialName("warranty_end_date") val warrantyEndDate: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class SyncExpiryPayload(
    val id: String,
    @SerialName("base_version") val baseVersion: Long = 0,
    val deleted: Boolean = false,
    val name: String = "",
    val category: String = "",
    @SerialName("package_expiry_date") val packageExpiryDate: String = "",
    @SerialName("opened_date") val openedDate: String = "",
    @SerialName("opened_validity_days") val openedValidityDays: Int = 0,
    val location: String = "",
    val notes: String = "",
    val status: String = "IN_USE",
    @SerialName("archived_at") val archivedAt: String = "",
)

@Serializable
data class SyncResponse(
    @SerialName("next_cursor") val nextCursor: Long = 0,
    @SerialName("next_expiry_cursor") val nextExpiryCursor: Long = 0,
    val applied: List<SyncAppliedChange> = emptyList(),
    val changes: List<SyncAppliedChange> = emptyList(),
    @SerialName("applied_expiry") val appliedExpiry: List<SyncExpiryChange> = emptyList(),
    @SerialName("expiry_changes") val expiryChanges: List<SyncExpiryChange> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
    @SerialName("expiry_conflicts") val expiryConflicts: List<SyncExpiryConflict> = emptyList(),
)

@Serializable
data class SyncAppliedChange(
    val cursor: Long,
    val version: Long,
    @SerialName("deleted_at") val deletedAt: String = "",
    val asset: SyncAssetPayload,
)

@Serializable
data class SyncExpiryChange(
    val cursor: Long,
    val version: Long,
    @SerialName("deleted_at") val deletedAt: String = "",
    val item: SyncExpiryPayload,
)

@Serializable
data class SyncConflict(
    val id: String,
    @SerialName("base_version") val baseVersion: Long,
    @SerialName("remote_version") val remoteVersion: Long = 0,
    val remote: SyncAssetPayload? = null,
)

@Serializable
data class SyncExpiryConflict(
    val id: String,
    @SerialName("base_version") val baseVersion: Long,
    @SerialName("remote_version") val remoteVersion: Long = 0,
    val remote: SyncExpiryPayload? = null,
)
