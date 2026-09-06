package io.suirenx.core.data.local

import androidx.room.Entity

@Entity(tableName = "remote_expiry_items", primaryKeys = ["serverUrl", "id"])
data class RemoteExpiryEntity(
    val serverUrl: String,
    val id: String,
    val name: String,
    val category: String,
    val packageExpiryDate: String,
    val openedDate: String?,
    val openedValidityDays: Int?,
    val location: String,
    val notes: String,
    val status: String,
    val archivedAt: String?,
    val syncVersion: Long,
    val deletedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)
