package io.suirenx.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * First-run gate: asks for a data source before showing the main navigation.
 * Remote address and authentication are managed inside the settings tab;
 * changing them must not replace the main navigation with the setup screen.
 */
@Composable
fun BackendGate(
    modifier: Modifier = Modifier,
    viewModel: BackendViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val configured = state.mode != null
    Box(modifier.fillMaxSize()) {
        when {
            state.settings == null && state.error == null ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            !configured -> BackendSettingsScreen(
                state = state,
                onAddressChanged = viewModel::onAddressChanged,
                onNameChanged = viewModel::onNameChanged,
                onSave = viewModel::save,
                onSelect = viewModel::select,
                onRetry = viewModel::load,
                onUseLocal = viewModel::useLocal,
                onUseRemote = viewModel::useRemote,
            )
            else -> content()
        }
    }
}
