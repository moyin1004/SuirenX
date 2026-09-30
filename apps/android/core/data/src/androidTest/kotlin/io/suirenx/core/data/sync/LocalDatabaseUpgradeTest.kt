package io.suirenx.core.data.sync

import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.data.di.LocalDatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Historical schema fixtures use the production migration chain, never a reset. */
@RunWith(AndroidJUnit4::class)
class LocalDatabaseUpgradeTest {
    @Test fun versionOnePreservesArchivedAssetAndAddsSyncTables() = upgrade(1)

    @Test fun versionTwoPreservesOptionalFieldsAndExpiry() = upgrade(2)

    private fun upgrade(version: Int) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val path = base.getDatabasePath("upgrade-${UUID.randomUUID()}.db")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext() = this
            override fun getDatabasePath(name: String): File = path
        }
        path.parentFile!!.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("CREATE TABLE assets (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, priceCents INTEGER NOT NULL, purchaseDate TEXT NOT NULL, status TEXT NOT NULL, retiredDate TEXT, archivedAt TEXT, iconKey TEXT NOT NULL)")
                old.execSQL("INSERT INTO assets VALUES ('old-asset', '旧电脑', 123456, '2020-01-01', 'Retired', '2021-01-01', '2021-02-01T00:00:00Z', 'laptop')")
                if (version == 2) {
                    old.execSQL("ALTER TABLE assets ADD COLUMN purchaseChannel TEXT")
                    old.execSQL("ALTER TABLE assets ADD COLUMN warrantyEndDate TEXT")
                    old.execSQL("ALTER TABLE assets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                    old.execSQL("ALTER TABLE assets ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'")
                    old.execSQL("ALTER TABLE assets ADD COLUMN createdAt TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z'")
                    old.execSQL("ALTER TABLE assets ADD COLUMN updatedAt TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z'")
                    old.execSQL("UPDATE assets SET notes='保留备注', tagsJson='[\"工作\"]'")
                    old.execSQL("CREATE TABLE expiry_items (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, packageExpiryDate TEXT NOT NULL, openedDate TEXT, openedValidityDays INTEGER, location TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, archivedAt TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
                    old.execSQL("INSERT INTO expiry_items VALUES ('old-expiry', '用品', '日用品', '2030-01-01', NULL, NULL, '', '', 'InUse', NULL, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')")
                }
                old.version = version
            }
            val upgraded = LocalDatabaseModule.provideLocalDatabase(context)
            try {
                val db = upgraded.openHelper.writableDatabase // Room validates the migrated schema.
                assertEquals(3, db.version)
                db.query("SELECT name, priceCents, retiredDate, archivedAt, notes, tagsJson FROM assets WHERE id='old-asset'").use {
                    assertEquals(true, it.moveToFirst())
                    assertEquals("旧电脑", it.getString(0))
                    assertEquals(123456L, it.getLong(1))
                    assertEquals("2021-01-01", it.getString(2))
                    assertEquals("2021-02-01T00:00:00Z", it.getString(3))
                    assertEquals(if (version == 2) "保留备注" else "", it.getString(4))
                    assertEquals(if (version == 2) "[\"工作\"]" else "[]", it.getString(5))
                }
                db.query("SELECT count(*) FROM expiry_items").use {
                    it.moveToFirst()
                    assertEquals(if (version == 2) 1 else 0, it.getInt(0))
                }
                for (table in listOf("local_sync_records", "local_sync_session")) {
                    db.query("SELECT count(*) FROM $table").use {
                        it.moveToFirst()
                        assertEquals(0, it.getInt(0))
                    }
                }
            } finally {
                upgraded.close()
            }
        } finally {
            SQLiteDatabase.deleteDatabase(path)
        }
    }
}
