package io.suirenx.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expiry_items")
data class ExpiryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val packageExpiryDate: String,
    val openedDate: String?,
    val openedValidityDays: Int?,
    val location: String,
    val notes: String,
    val status: String,
    val archivedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)
