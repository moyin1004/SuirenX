package io.suirenx.core.data.local

import androidx.room.Entity

@Entity(tableName = "remote_expiry_conflicts", primaryKeys = ["serverUrl", "expiryId"])
data class RemoteExpiryConflictEntity(
    val serverUrl: String,
    val expiryId: String,
    val baseVersion: Long,
    val remoteVersion: Long,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val createdAt: String,
)
