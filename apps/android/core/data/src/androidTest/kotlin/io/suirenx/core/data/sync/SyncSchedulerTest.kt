package io.suirenx.core.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.suirenx.core.domain.SyncSchedule
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSchedulerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val workManager get() = WorkManager.getInstance(context)
    private lateinit var testWorkNameSuffix: String
    private lateinit var periodicWorkName: String
    private lateinit var changeWorkName: String
    private lateinit var preferencePrefix: String
    private lateinit var isolatedContext: Context

    @Before
    fun setUp() {
        runBlocking {
            preferencePrefix = "sync-scheduler-test-${UUID.randomUUID()}-"
            testWorkNameSuffix = preferencePrefix
            periodicWorkName = "suirenx-sync-periodic-$testWorkNameSuffix"
            changeWorkName = "suirenx-sync-change-$testWorkNameSuffix"
            isolatedContext = object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    super.getSharedPreferences(preferencePrefix + name, mode)
            }
            workManager.cancelUniqueWork(periodicWorkName).result.get(10, TimeUnit.SECONDS)
            workManager.cancelUniqueWork(changeWorkName).result.get(10, TimeUnit.SECONDS)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            workManager.cancelUniqueWork(periodicWorkName).result.get(10, TimeUnit.SECONDS)
            workManager.cancelUniqueWork(changeWorkName).result.get(10, TimeUnit.SECONDS)
            isolatedContext.getSharedPreferences("sync_schedule", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun periodicRecoveryScheduleSurvivesSchedulerRecreationAndStaysUnique() = runBlocking {
        val scheduler = SyncScheduler(isolatedContext, testWorkNameSuffix, NetworkType.METERED)
        scheduler.select(SyncSchedule.Hourly)

        assertEquals(SyncSchedule.Hourly, scheduler.schedule.value)
        val periodicWork = active(periodicWorkName).single()
        assertEquals(NetworkType.METERED, periodicWork.constraints.requiredNetworkType)

        // A new scheduler instance models Application startup after process death:
        // it reloads the saved cadence and re-registers the durable recovery job.
        val restarted = SyncScheduler(isolatedContext, testWorkNameSuffix, NetworkType.METERED)
        assertEquals(SyncSchedule.Hourly, restarted.schedule.value)
        restarted.initialize()

        assertEquals(1, active(periodicWorkName).size)
        assertTrue(active(changeWorkName).isEmpty())
    }

    @Test
    fun initializeKeepsExistingPeriodicWorkGeneration() = runBlocking {
        val scheduler = SyncScheduler(isolatedContext, testWorkNameSuffix, NetworkType.METERED)
        scheduler.select(SyncSchedule.Hourly)
        val before = active(periodicWorkName).single()

        scheduler.initialize()

        val after = active(periodicWorkName).single()
        assertEquals(before.id, after.id)
        assertEquals(before.generation, after.generation)
    }

    @Test
    fun onChangeWorkIsUniqueAndPeriodicRecoveryRemainsRegistered() = runBlocking {
        val scheduler = SyncScheduler(isolatedContext, testWorkNameSuffix, NetworkType.METERED)
        scheduler.initialize()
        scheduler.onLocalChange()
        scheduler.onLocalChange()

        val changeWork = active(changeWorkName).single()
        val periodicWork = active(periodicWorkName).single()
        assertEquals(NetworkType.METERED, changeWork.constraints.requiredNetworkType)
        assertEquals(NetworkType.METERED, periodicWork.constraints.requiredNetworkType)
        val scheduledChangeId = changeWork.id

        scheduler.select(SyncSchedule.Daily)
        scheduler.onLocalChange()

        assertEquals(1, active(periodicWorkName).size)
        assertEquals(scheduledChangeId, active(changeWorkName).single().id)
    }

    private suspend fun active(name: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            .filter { it.state != WorkInfo.State.CANCELLED }
}
