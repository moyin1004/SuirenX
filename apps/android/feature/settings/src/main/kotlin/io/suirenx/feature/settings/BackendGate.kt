package io.suirenx.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BackendGate(
    content: @Composable (onOpenSettings: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackendViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.enterApp.collect { showSettings = false } }
    val configured = state.settings?.activeUrl != null
    BackHandler(enabled = configured && showSettings) { showSettings = false }
    Box(modifier.fillMaxSize()) {
        when {
            state.settings == null && state.error == null ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            !configured || showSettings -> BackendSettingsScreen(
                state = state,
                onAddressChanged = viewModel::onAddressChanged,
                onNameChanged = viewModel::onNameChanged,
                onSave = viewModel::save,
                onSelect = viewModel::select,
                onRetry = viewModel::load,
                onBack = { showSettings = false },
            )
            else -> content { showSettings = true }
        }
    }
}
