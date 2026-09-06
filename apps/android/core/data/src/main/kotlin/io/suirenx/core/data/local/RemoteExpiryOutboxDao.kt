package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteExpiryOutboxDao {
    @Query("SELECT * FROM remote_expiry_outbox WHERE serverUrl = :serverUrl ORDER BY createdAt ASC")
    suspend fun getAll(serverUrl: String): List<RemoteExpiryOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RemoteExpiryOutboxEntity)

    @Query("DELETE FROM remote_expiry_outbox WHERE serverUrl = :serverUrl AND operationId = :operationId")
    suspend fun delete(serverUrl: String, operationId: String)

    @Query("DELETE FROM remote_expiry_outbox WHERE serverUrl = :serverUrl AND expiryId = :expiryId")
    suspend fun deleteForExpiry(serverUrl: String, expiryId: String)
}
