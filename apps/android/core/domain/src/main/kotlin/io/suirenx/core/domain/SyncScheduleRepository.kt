package io.suirenx.core.domain

import kotlinx.coroutines.flow.StateFlow

enum class SyncSchedule(val intervalMinutes: Long, val label: String) {
    OnChange(15, "每次修改后"),
    Every15Minutes(15, "约每 15 分钟"),
    Hourly(60, "约每小时"),
    Every6Hours(360, "约每 6 小时"),
    Daily(1440, "约每天"),
}

interface SyncScheduleRepository {
    val schedule: StateFlow<SyncSchedule>
    fun select(schedule: SyncSchedule)
    fun onLocalChange()
    fun initialize()
}
