package io.suirenx.core.domain

import kotlinx.coroutines.flow.StateFlow

enum class SyncSchedule(val intervalMinutes: Long, val label: String) {
    OnChange(15, "copy_after_changes"),
    Every15Minutes(15, "copy_about_every_15_minutes"),
    Hourly(60, "copy_about_hourly"),
    Every6Hours(360, "copy_about_every_6_hours"),
    Daily(1440, "copy_about_daily"),
}

interface SyncScheduleRepository {
    val schedule: StateFlow<SyncSchedule>
    fun select(schedule: SyncSchedule)
    fun onLocalChange()
    fun initialize()
}
