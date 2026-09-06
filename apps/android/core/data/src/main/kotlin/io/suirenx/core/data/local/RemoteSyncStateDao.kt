package io.suirenx.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RemoteSyncStateDao {
    @Query("SELECT * FROM remote_sync_state WHERE serverUrl = :serverUrl LIMIT 1")
    suspend fun get(serverUrl: String): RemoteSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: RemoteSyncStateEntity)
}
