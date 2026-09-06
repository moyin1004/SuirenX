package io.suirenx.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AssetEntity::class, ExpiryEntity::class], version = 2, exportSchema = false)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun expiryDao(): ExpiryDao
}
