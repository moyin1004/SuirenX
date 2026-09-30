package io.suirenx.core.data.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.suirenx.core.domain.*
import io.suirenx.core.model.StorageMode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncScheduleRepository {
    private val preferences = context.getSharedPreferences("sync_schedule", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(SyncSchedule.entries.firstOrNull { it.name == preferences.getString("schedule", "") } ?: SyncSchedule.OnChange)
    private var periodicWorkName = "suirenx-sync-periodic"
    private var changeWorkName = "suirenx-sync-change"
    private var requiredNetworkType = NetworkType.CONNECTED
    override val schedule: StateFlow<SyncSchedule> = current

    internal constructor(
        context: Context,
        testWorkNameSuffix: String,
        testNetworkType: NetworkType = NetworkType.CONNECTED,
    ) : this(context) {
        periodicWorkName = "suirenx-sync-periodic-$testWorkNameSuffix"
        changeWorkName = "suirenx-sync-change-$testWorkNameSuffix"
        requiredNetworkType = testNetworkType
    }

    override fun select(schedule: SyncSchedule) {
        preferences.edit().putString("schedule", schedule.name).apply()
        current.value = schedule
        enqueuePeriodicWork(ExistingPeriodicWorkPolicy.UPDATE)
    }
    override fun initialize() {
        // Application.onCreate also runs when JobScheduler starts our Worker.
        // Keep an existing request so startup does not replace its generation
        // while WorkManager is recovering or executing it.
        enqueuePeriodicWork(ExistingPeriodicWorkPolicy.KEEP)
    }

    private fun enqueuePeriodicWork(policy: ExistingPeriodicWorkPolicy) {
        // The recovery job also drains changes committed just before process death.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicWorkName, policy,
            PeriodicWorkRequestBuilder<LocalSyncWorker>(current.value.intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(workConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
        )
    }
    // Debounce bursts into one worker. A replaced in-flight request remains in
    // the frozen Room batch and is retried with exactly the same idempotency key.
    override fun onLocalChange() {
        if (current.value != SyncSchedule.OnChange) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            changeWorkName, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<LocalSyncWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .setConstraints(workConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
        )
    }

    private fun workConstraints() = Constraints.Builder()
        .setRequiredNetworkType(requiredNetworkType)
        .build()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerDependencies {
    fun sync(): RemoteSyncRepository
    fun modes(): StorageModeRepository
    fun backends(): BackendRepository
    fun auth(): AuthRepository
}

class LocalSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerDependencies::class.java)
        if (deps.modes().initialize().isFailure || deps.backends().initialize().isFailure || deps.auth().initialize().isFailure) return Result.retry()
        if (deps.modes().mode.value != StorageMode.Remote || !deps.auth().state.value.authenticated) return Result.success()
        return if (deps.sync().retry().isSuccess) Result.success() else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
