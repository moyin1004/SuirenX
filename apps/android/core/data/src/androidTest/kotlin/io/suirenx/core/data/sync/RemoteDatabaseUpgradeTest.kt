package io.suirenx.core.data.sync

import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.data.di.LocalDatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Exercises every supported remote-cache schema through the production migration chain. */
@RunWith(AndroidJUnit4::class)
class RemoteDatabaseUpgradeTest {
    @Test fun versionOnePreservesAssetAndOutbox() = upgrade(1)

    @Test fun versionTwoPreservesConflicts() = upgrade(2)

    @Test fun versionThreePreservesExpiryOutbox() = upgrade(3)

    @Test fun versionFourAddsLastSyncedAtWithoutLosingCursors() = upgrade(4)

    private fun upgrade(version: Int) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val path = base.getDatabasePath("remote-upgrade-${UUID.randomUUID()}.db")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext() = this
            override fun getDatabasePath(name: String): File = path
        }
        path.parentFile!!.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("CREATE TABLE remote_assets (serverUrl TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, priceCents INTEGER NOT NULL, purchaseDate TEXT NOT NULL, status TEXT NOT NULL, retiredDate TEXT, archivedAt TEXT, iconKey TEXT NOT NULL, imageUrl TEXT NOT NULL, purchaseChannel TEXT, warrantyEndDate TEXT, notes TEXT NOT NULL, tagsJson TEXT NOT NULL, syncVersion INTEGER NOT NULL, deletedAt TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(serverUrl, id))")
                old.execSQL("INSERT INTO remote_assets VALUES ('https://example.test', 'asset-1', '保留资产', 12345, '2020-01-01', 'ACTIVE', NULL, NULL, 'laptop', '', NULL, NULL, '', '[]', 7, NULL, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')")
                old.execSQL("CREATE TABLE remote_asset_outbox (serverUrl TEXT NOT NULL, operationId TEXT NOT NULL, assetId TEXT NOT NULL, baseVersion INTEGER NOT NULL, payloadJson TEXT NOT NULL, idempotencyKey TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, operationId))")
                old.execSQL("INSERT INTO remote_asset_outbox VALUES ('https://example.test', 'op-1', 'asset-1', 6, '{}', 'key-1', '2020-01-01T00:00:00Z')")
                old.execSQL("CREATE TABLE remote_sync_state (serverUrl TEXT NOT NULL, cursor INTEGER NOT NULL, expiryCursor INTEGER NOT NULL, PRIMARY KEY(serverUrl))")
                old.execSQL("INSERT INTO remote_sync_state VALUES ('https://example.test', 11, 13)")

                if (version >= 2) {
                    old.execSQL("CREATE TABLE remote_asset_conflicts (serverUrl TEXT NOT NULL, assetId TEXT NOT NULL, baseVersion INTEGER NOT NULL, remoteVersion INTEGER NOT NULL, localPayloadJson TEXT NOT NULL, remotePayloadJson TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, assetId))")
                    old.execSQL("INSERT INTO remote_asset_conflicts VALUES ('https://example.test', 'asset-1', 6, 7, '{\"local\":1}', '{\"remote\":1}', '2020-01-01T00:00:00Z')")
                }
                if (version >= 3) {
                    old.execSQL("CREATE TABLE remote_expiry_items (serverUrl TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, packageExpiryDate TEXT NOT NULL, openedDate TEXT, openedValidityDays INTEGER, location TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, archivedAt TEXT, syncVersion INTEGER NOT NULL, deletedAt TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(serverUrl, id))")
                    old.execSQL("INSERT INTO remote_expiry_items VALUES ('https://example.test', 'expiry-1', '保留用品', '日用品', '2030-01-01', NULL, NULL, '', '', 'InUse', NULL, 3, NULL, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')")
                    old.execSQL("CREATE TABLE remote_expiry_outbox (serverUrl TEXT NOT NULL, operationId TEXT NOT NULL, expiryId TEXT NOT NULL, baseVersion INTEGER NOT NULL, payloadJson TEXT NOT NULL, idempotencyKey TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, operationId))")
                    old.execSQL("INSERT INTO remote_expiry_outbox VALUES ('https://example.test', 'expiry-op-1', 'expiry-1', 2, '{}', 'expiry-key-1', '2020-01-01T00:00:00Z')")
                }
                if (version >= 4) {
                    old.execSQL("CREATE TABLE remote_expiry_conflicts (serverUrl TEXT NOT NULL, expiryId TEXT NOT NULL, baseVersion INTEGER NOT NULL, remoteVersion INTEGER NOT NULL, localPayloadJson TEXT NOT NULL, remotePayloadJson TEXT NOT NULL, createdAt TEXT NOT NULL, PRIMARY KEY(serverUrl, expiryId))")
                    old.execSQL("INSERT INTO remote_expiry_conflicts VALUES ('https://example.test', 'expiry-1', 2, 3, '{\"local\":1}', '{\"remote\":1}', '2020-01-01T00:00:00Z')")
                }
                old.version = version
            }

            val upgraded = LocalDatabaseModule.provideRemoteDatabase(context)
            try {
                val db = upgraded.openHelper.writableDatabase
                assertEquals(5, db.version)
                db.query("SELECT name, priceCents, syncVersion FROM remote_assets WHERE id='asset-1'").use {
                    assertEquals(true, it.moveToFirst())
                    assertEquals("保留资产", it.getString(0))
                    assertEquals(12345L, it.getLong(1))
                    assertEquals(7L, it.getLong(2))
                }
                db.query("SELECT cursor, expiryCursor, lastSyncedAt FROM remote_sync_state WHERE serverUrl='https://example.test'").use {
                    assertEquals(true, it.moveToFirst())
                    assertEquals(11L, it.getLong(0))
                    assertEquals(13L, it.getLong(1))
                    assertNull(it.getString(2))
                }
                assertEquals(1, count(db, "remote_asset_outbox"))
                assertEquals(if (version >= 2) 1 else 0, count(db, "remote_asset_conflicts"))
                assertEquals(if (version >= 3) 1 else 0, count(db, "remote_expiry_items"))
                assertEquals(if (version >= 3) 1 else 0, count(db, "remote_expiry_outbox"))
                assertEquals(if (version >= 4) 1 else 0, count(db, "remote_expiry_conflicts"))
            } finally {
                upgraded.close()
            }
        } finally {
            SQLiteDatabase.deleteDatabase(path)
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT count(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
