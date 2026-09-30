package io.suirenx.core.data.sync

import android.content.Context
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

/** Host-run in two phases; the delayed probe runs after the first instrumentation process exits. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WorkManagerBackgroundExecutionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = WorkManager.getInstance(context)
    private val prefs get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Test
    fun test1ScheduleDelayedProbeThenExit() {
        runBlocking {
            prefs.getString("nonce", null)?.let { previous ->
                manager.cancelUniqueWork(workName(previous)).result.get(10, TimeUnit.SECONDS)
            }
            val nonce = UUID.randomUUID().toString()
            assertTrue(prefs.edit().putString("nonce", nonce).putInt("pid", Process.myPid()).commit())
            manager.enqueueUniqueWork(
                workName(nonce),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BackgroundExecutionProbeWorker>()
                    .setInitialDelay(15, TimeUnit.SECONDS)
                    .setInputData(Data.Builder().putString(BackgroundExecutionProbeWorker.NONCE, nonce).build())
                    .build(),
            ).result.get(10, TimeUnit.SECONDS)
            val info = manager.getWorkInfosForUniqueWork(workName(nonce)).get(10, TimeUnit.SECONDS).single()
            assertEquals(WorkInfo.State.ENQUEUED, info.state)
        }
    }

    @Test
    fun test2ProbeCompletedAfterProcessRestart() {
        runBlocking {
            val nonce = prefs.getString("nonce", null)
            assertNotNull("Run test1ScheduleDelayedProbeThenExit first", nonce)
            nonce!!
            if (prefs.getInt("pid", -1) == Process.myPid()) {
                manager.cancelUniqueWork(workName(nonce)).result.get(10, TimeUnit.SECONDS)
                prefs.edit().clear().commit()
                assumeTrue("Run this phase after the test process exits", false)
            }

            assertEquals(nonce, prefs.getString("completed", null))
            val info = manager.getWorkInfosForUniqueWork(workName(nonce)).get(10, TimeUnit.SECONDS).single()
            assertEquals(WorkInfo.State.SUCCEEDED, info.state)
            manager.cancelUniqueWork(workName(nonce)).result.get(10, TimeUnit.SECONDS)
            prefs.edit().clear().commit()
        }
    }

    private fun workName(nonce: String) = "workmanager-background-probe-$nonce"

    private companion object {
        const val PREFERENCES = "workmanager-background-probe"
    }
}

class BackgroundExecutionProbeWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val nonce = inputData.getString(NONCE) ?: return Result.failure()
        applicationContext.getSharedPreferences("workmanager-background-probe", Context.MODE_PRIVATE)
            .edit().putString("completed", nonce).commit()
        return Result.success()
    }

    companion object {
        const val NONCE = "nonce"
    }
}
