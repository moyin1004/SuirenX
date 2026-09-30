package io.suirenx.core.data.sync

import io.suirenx.core.data.local.*
import io.suirenx.core.model.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalSyncJournal @Inject constructor(
    private val database: LocalDatabase,
    private val codec: SyncCodec,
    private val json: Json,
) {
    // Caller owns the same Room transaction as the business row write.
    suspend fun asset(asset: Asset, deleted: Boolean = false) {
        val previous = database.syncDao().get("asset", asset.id)
        val payload = json.encodeToString(codec.toPayload(asset, deleted, previous?.version ?: 0))
        save("asset", asset.id, payload, previous)
    }
    suspend fun expiry(item: ExpiryItem, deleted: Boolean = false) {
        val previous = database.syncDao().get("expiry", item.id)
        val payload = json.encodeToString(codec.toExpiryPayload(item, deleted, previous?.version ?: 0))
        save("expiry", item.id, payload, previous)
    }
    private suspend fun save(kind: String, id: String, payload: String, previous: LocalSyncRecord?) {
        database.syncDao().save(LocalSyncRecord(kind, id, UUID.randomUUID().toString(), previous?.version ?: 0, payload,
            remotePayload = previous?.remotePayload, remoteVersion = previous?.remoteVersion))
    }
}
