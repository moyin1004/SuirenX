package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteConflictDao {
    @Query("SELECT * FROM remote_asset_conflicts WHERE serverUrl = :serverUrl ORDER BY createdAt ASC")
    suspend fun getAll(serverUrl: String): List<RemoteConflictEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conflict: RemoteConflictEntity)

    @Query("DELETE FROM remote_asset_conflicts WHERE serverUrl = :serverUrl AND assetId = :assetId")
    suspend fun delete(serverUrl: String, assetId: String)

    @Query("SELECT COUNT(*) FROM remote_asset_conflicts WHERE serverUrl = :serverUrl")
    suspend fun count(serverUrl: String): Int
}
