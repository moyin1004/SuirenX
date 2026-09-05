package io.suirenx.feature.assets

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges asset mutations across navigation-scoped ViewModels: the detail screen
 * edits an asset while the list screen stays on the back stack, and the list
 * refreshes itself whenever a change is announced.
 */
@Singleton
class AssetChangeNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyAssetChanged() {
        _events.tryEmit(Unit)
    }
}
