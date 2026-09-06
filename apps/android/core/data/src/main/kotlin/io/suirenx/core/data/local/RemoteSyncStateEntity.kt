package io.suirenx.core.data.local

import androidx.room.Entity

@Entity(tableName = "remote_sync_state", primaryKeys = ["serverUrl"])
data class RemoteSyncStateEntity(
    val serverUrl: String,
    val cursor: Long = 0,
    val expiryCursor: Long = 0,
    val lastSyncedAt: String? = null,
)
