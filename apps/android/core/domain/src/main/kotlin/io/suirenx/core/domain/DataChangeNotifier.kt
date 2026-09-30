package io.suirenx.core.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Cross-feature signal for local backup restore and other data-source-wide replacements. */
@Singleton
class DataChangeNotifier @Inject constructor() {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = mutableEvents.asSharedFlow()
    fun notifyChanged() { mutableEvents.tryEmit(Unit) }
}
