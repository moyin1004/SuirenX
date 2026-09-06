package io.suirenx.core.data.local

import androidx.room.Entity

@Entity(tableName = "remote_expiry_outbox", primaryKeys = ["serverUrl", "operationId"])
data class RemoteExpiryOutboxEntity(
    val serverUrl: String,
    val operationId: String,
    val expiryId: String,
    val baseVersion: Long,
    val payloadJson: String,
    val idempotencyKey: String,
    val createdAt: String,
)
