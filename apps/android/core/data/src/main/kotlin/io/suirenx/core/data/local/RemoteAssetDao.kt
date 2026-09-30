package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteAssetDao {
    @Query("SELECT * FROM remote_assets WHERE serverUrl = :serverUrl AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAll(serverUrl: String): List<RemoteAssetEntity>

    @Query("SELECT * FROM remote_assets WHERE serverUrl = :serverUrl AND id = :id LIMIT 1")
    suspend fun getById(serverUrl: String, id: String): RemoteAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: RemoteAssetEntity)

    @Query("DELETE FROM remote_assets WHERE serverUrl = :serverUrl AND id = :id")
    suspend fun delete(serverUrl: String, id: String)

    @Query("DELETE FROM remote_assets WHERE serverUrl = :serverUrl")
    suspend fun deleteAll(serverUrl: String)

    @Query("SELECT COUNT(*) FROM remote_assets WHERE serverUrl = :serverUrl AND deletedAt IS NULL")
    suspend fun count(serverUrl: String): Int
}
