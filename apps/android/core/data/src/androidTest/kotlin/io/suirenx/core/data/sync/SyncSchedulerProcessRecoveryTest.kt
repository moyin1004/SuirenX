package io.suirenx.core.data.sync

import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.suirenx.core.domain.SyncSchedule
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Host-run in two phases with the instrumentation process exiting between them; test APK only. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncSchedulerProcessRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = WorkManager.getInstance(context)
    private val marker get() = context.getSharedPreferences("scheduler-process-recovery", Context.MODE_PRIVATE)

    @Test
    fun test1PreparePersistentWorkBeforeHostStopsThisProcess() {
        runBlocking {
            marker.getString("suffix", null)?.let { previousSuffix ->
                manager.cancelUniqueWork(periodicName(previousSuffix)).result.get(10, TimeUnit.SECONDS)
                context.getSharedPreferences("$previousSuffix-sync_schedule", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
            val suffix = "process-recovery-${UUID.randomUUID()}"
            assertTrue(marker.edit().putString("suffix", suffix).putInt("pid", Process.myPid()).commit())
            val scheduler = scheduler(suffix)

            scheduler.select(SyncSchedule.Hourly)

            val work = active(periodicName(suffix)).single()
            assertEquals(NetworkType.METERED, work.constraints.requiredNetworkType)
            assertEquals(SyncSchedule.Hourly, scheduler.schedule.value)
            awaitPreferenceFile(suffix)
        }
    }

    @Test
    fun test2PersistedWorkRemainsAfterHostRestartsTheProcess() {
        runBlocking {
            val suffix = marker.getString("suffix", null)
            assertNotNull("Run test1PreparePersistentWorkBeforeHostStopsThisProcess first", suffix)
            suffix!!
            val previousPid = marker.getInt("pid", -1)
            if (previousPid == Process.myPid()) {
                manager.cancelUniqueWork(periodicName(suffix)).result.get(10, TimeUnit.SECONDS)
                marker.edit().clear().commit()
                context.getSharedPreferences("$suffix-sync_schedule", Context.MODE_PRIVATE).edit().clear().commit()
                assumeTrue("Run this phase after stopping the test package process", false)
            }

            // Inspect WorkManager's persisted record before application startup re-registers anything.
            assertEquals(1, active(periodicName(suffix)).size)
            val restarted = scheduler(suffix)
            assertEquals(SyncSchedule.Hourly, restarted.schedule.value)
            restarted.initialize()

            val work = active(periodicName(suffix)).single()
            assertEquals(NetworkType.METERED, work.constraints.requiredNetworkType)
            manager.cancelUniqueWork(periodicName(suffix)).result.get(10, TimeUnit.SECONDS)
            marker.edit().clear().commit()
            context.getSharedPreferences("$suffix-sync_schedule", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun scheduler(suffix: String) = SyncScheduler(
        object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("$suffix-$name", mode)
        },
        suffix,
        NetworkType.METERED,
    )

    private fun periodicName(suffix: String) = "suirenx-sync-periodic-$suffix"

    private fun awaitPreferenceFile(suffix: String) {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$suffix-sync_schedule.xml")
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(5)
        while (!file.isFile && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue("Schedule preference must reach disk before the process is stopped", file.isFile)
        assertTrue(file.readText().contains(SyncSchedule.Hourly.name))
    }

    private suspend fun active(name: String): List<WorkInfo> =
        manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            .filter { it.state != WorkInfo.State.CANCELLED }
}
