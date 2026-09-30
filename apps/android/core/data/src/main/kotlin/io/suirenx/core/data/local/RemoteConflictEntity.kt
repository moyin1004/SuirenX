package io.suirenx.core.data.local

import androidx.room.Entity

@Entity(tableName = "remote_asset_conflicts", primaryKeys = ["serverUrl", "assetId"])
data class RemoteConflictEntity(
    val serverUrl: String,
    val assetId: String,
    val baseVersion: Long,
    val remoteVersion: Long,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val createdAt: String,
)
