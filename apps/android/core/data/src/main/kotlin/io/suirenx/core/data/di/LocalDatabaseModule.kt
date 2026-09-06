package io.suirenx.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.suirenx.core.data.local.LocalDatabase
import io.suirenx.core.data.local.RemoteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalDatabaseModule {
    @Provides
    @Singleton
    fun provideLocalDatabase(@ApplicationContext context: Context): LocalDatabase =
        Room.databaseBuilder(context, LocalDatabase::class.java, "suirenx-local.db")
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE assets ADD COLUMN purchaseChannel TEXT")
                    db.execSQL("ALTER TABLE assets ADD COLUMN warrantyEndDate TEXT")
                    db.execSQL("ALTER TABLE assets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE assets ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
                    db.execSQL("ALTER TABLE assets ADD COLUMN createdAt TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z'")
                    db.execSQL("ALTER TABLE assets ADD COLUMN updatedAt TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z'")
                    db.execSQL("CREATE TABLE IF NOT EXISTS expiry_items (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, packageExpiryDate TEXT NOT NULL, openedDate TEXT, openedValidityDays INTEGER, location TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, archivedAt TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
                }
            })
            .build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideRemoteDatabase(@ApplicationContext context: Context): RemoteDatabase =
        Room.databaseBuilder(context, RemoteDatabase::class.java, "suirenx-remote-cache.db")
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS remote_asset_conflicts (serverUrl TEXT NOT NULL, assetId TEXT NOT NULL, baseVersion INTEGER NOT NULL, remoteVersion INTEGER NOT NULL, localPayloadJson TEXT NOT NULL, remotePayloadJson TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, assetId))")
                }
            })
            .addMigrations(object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS remote_expiry_items (serverUrl TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, packageExpiryDate TEXT NOT NULL, openedDate TEXT, openedValidityDays INTEGER, location TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, archivedAt TEXT, syncVersion INTEGER NOT NULL, deletedAt TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(serverUrl, id))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS remote_expiry_outbox (serverUrl TEXT NOT NULL, operationId TEXT NOT NULL, expiryId TEXT NOT NULL, baseVersion INTEGER NOT NULL, payloadJson TEXT NOT NULL, idempotencyKey TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, operationId))")
                }
            })
            .addMigrations(object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS remote_expiry_conflicts (serverUrl TEXT NOT NULL, expiryId TEXT NOT NULL, baseVersion INTEGER NOT NULL, remoteVersion INTEGER NOT NULL, localPayloadJson TEXT NOT NULL, remotePayloadJson TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, expiryId))")
                }
            })
            .addMigrations(object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE remote_sync_state ADD COLUMN lastSyncedAt TEXT")
                }
            })
            .build()
}
