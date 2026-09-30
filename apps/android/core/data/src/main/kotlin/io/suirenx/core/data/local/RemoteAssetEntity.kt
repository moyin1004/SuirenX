package io.suirenx.core.data.local

import androidx.room.Entity

@Entity(tableName = "remote_assets", primaryKeys = ["serverUrl", "id"])
data class RemoteAssetEntity(
    val serverUrl: String,
    val id: String,
    val name: String,
    val priceCents: Long,
    val purchaseDate: String,
    val status: String,
    val retiredDate: String?,
    val archivedAt: String?,
    val iconKey: String,
    val imageUrl: String,
    val purchaseChannel: String?,
    val warrantyEndDate: String?,
    val notes: String,
    val tagsJson: String,
    val syncVersion: Long,
    val deletedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)
