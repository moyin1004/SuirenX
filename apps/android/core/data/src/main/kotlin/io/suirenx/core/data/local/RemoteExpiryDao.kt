package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteExpiryDao {
    @Query("SELECT * FROM remote_expiry_items WHERE serverUrl = :serverUrl AND deletedAt IS NULL ORDER BY packageExpiryDate ASC, createdAt DESC")
    suspend fun getAll(serverUrl: String): List<RemoteExpiryEntity>

    @Query("SELECT * FROM remote_expiry_items WHERE serverUrl = :serverUrl AND id = :id LIMIT 1")
    suspend fun getById(serverUrl: String, id: String): RemoteExpiryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RemoteExpiryEntity)

    @Query("DELETE FROM remote_expiry_items WHERE serverUrl = :serverUrl AND id = :id")
    suspend fun delete(serverUrl: String, id: String)

    @Query("SELECT COUNT(*) FROM remote_expiry_items WHERE serverUrl = :serverUrl AND deletedAt IS NULL")
    suspend fun count(serverUrl: String): Int
}
