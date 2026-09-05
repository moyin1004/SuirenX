package io.suirenx.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The settings tab: backend address management without any gate or back stack.
 */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: BackendViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackendSettingsScreen(
        state = state,
        onAddressChanged = viewModel::onAddressChanged,
        onNameChanged = viewModel::onNameChanged,
        onSave = viewModel::save,
        onSelect = viewModel::select,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}
