package io.suirenx.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SuirenXApplication : Application() {
    @javax.inject.Inject lateinit var syncSchedule: io.suirenx.core.domain.SyncScheduleRepository
    override fun onCreate() {
        super.onCreate()
        syncSchedule.initialize()
    }
}

