package io.suirenx.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.data.local.LocalDatabase
import io.suirenx.core.data.sync.LocalSyncJournal
import io.suirenx.core.data.sync.SyncCodec
import io.suirenx.core.domain.SyncSchedule
import io.suirenx.core.domain.SyncScheduleRepository
import io.suirenx.core.model.NewAsset
import io.suirenx.core.model.NewExpiryItem
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultRepositoryScheduleTest {
    @Test
    fun schedulesOnlyAfterSuccessfulLocalWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build()
        try {
            val json = Json { encodeDefaults = true }
            val clock = Clock.systemUTC()
            val journal = LocalSyncJournal(database, SyncCodec(clock), json)
            val local = LocalAssetRepository(database, json, clock, journal)
            var scheduledChanges = 0
            val scheduler = object : SyncScheduleRepository {
                override val schedule = MutableStateFlow(SyncSchedule.OnChange)
                override fun select(schedule: SyncSchedule) { this.schedule.value = schedule }
                override fun onLocalChange() { scheduledChanges++ }
                override fun initialize() = Unit
            }
            val repository = DefaultAssetRepository(scheduler, local)

            val created = repository.createAsset(NewAsset("Repository test", 123, LocalDate.of(2024, 1, 1)))
            assertTrue(created.isSuccess)
            assertEquals(1, scheduledChanges)

            val missingUpdate = repository.updateAsset("missing", NewAsset("Missing", 123, LocalDate.of(2024, 1, 1)))
            assertFalse(missingUpdate.isSuccess)
            assertEquals(1, scheduledChanges)

            assertTrue(repository.getAssets(status = null, includeArchived = false).isSuccess)
            assertEquals(1, scheduledChanges)

            val localExpiry = LocalExpiryRepository(database, clock, journal)
            val expiryRepository = DefaultExpiryRepository(scheduler, localExpiry)
            val createdExpiry = expiryRepository.create(NewExpiryItem("Milk", "Food", LocalDate.of(2030, 1, 1)))
            assertTrue(createdExpiry.isSuccess)
            assertEquals(2, scheduledChanges)

            val missingExpiryUpdate = expiryRepository.update("missing", NewExpiryItem("Missing", "Food", LocalDate.of(2030, 1, 1)))
            assertFalse(missingExpiryUpdate.isSuccess)
            assertEquals(2, scheduledChanges)

            assertTrue(expiryRepository.list(includeArchived = false).isSuccess)
            assertEquals(2, scheduledChanges)
        } finally {
            database.close()
        }
    }
}
