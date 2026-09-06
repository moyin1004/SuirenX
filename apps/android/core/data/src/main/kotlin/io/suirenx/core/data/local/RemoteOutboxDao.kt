package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteOutboxDao {
    @Query("SELECT * FROM remote_asset_outbox WHERE serverUrl = :serverUrl ORDER BY createdAt ASC")
    suspend fun getAll(serverUrl: String): List<RemoteOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RemoteOutboxEntity)

    @Query("DELETE FROM remote_asset_outbox WHERE serverUrl = :serverUrl AND operationId = :operationId")
    suspend fun delete(serverUrl: String, operationId: String)

    @Query("DELETE FROM remote_asset_outbox WHERE serverUrl = :serverUrl AND assetId = :assetId")
    suspend fun deleteForAsset(serverUrl: String, assetId: String)

    @Query("SELECT COUNT(*) FROM remote_asset_outbox WHERE serverUrl = :serverUrl")
    suspend fun count(serverUrl: String): Int
}
