package io.suirenx.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AssetEntity::class, ExpiryEntity::class, LocalSyncRecord::class, LocalSyncSession::class], version = 3, exportSchema = false)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun syncDao(): LocalSyncDao
    abstract fun assetDao(): AssetDao
    abstract fun expiryDao(): ExpiryDao
}
