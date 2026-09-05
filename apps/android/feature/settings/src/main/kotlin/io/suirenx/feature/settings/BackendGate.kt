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
 * First-run gate: shows the backend setup screen until a backend URL is
 * configured, then renders [content]. Settings are otherwise reachable from
 * the settings tab, so the gate never reopens once configured.
 */
@Composable
fun BackendGate(
    modifier: Modifier = Modifier,
    viewModel: BackendViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val configured = state.settings?.activeUrl != null
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
            )
            else -> content()
        }
    }
}
