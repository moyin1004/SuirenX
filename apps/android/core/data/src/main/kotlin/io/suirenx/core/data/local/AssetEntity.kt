package io.suirenx.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val priceCents: Long,
    val purchaseDate: String,
    val status: String,
    val retiredDate: String?,
    val archivedAt: String?,
    val iconKey: String,
    val purchaseChannel: String?,
    val warrantyEndDate: String?,
    val notes: String,
    val tagsJson: String,
    val createdAt: String,
    val updatedAt: String,
)
