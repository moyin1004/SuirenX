package io.suirenx.feature.settings

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.suirenx.core.model.BackendServer

/** Shared grouped form for the add sheet and saved-server detail. */
@Composable
internal fun ServerEditorFields(
    state: BackendUiState,
    server: BackendServer?,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onSave: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val busy = state.busy || state.authBusy || state.migrationBusy || state.testingConnection
    val tested = state.connectionTestAddress == state.address.trim() && !state.connectionTestFailed
    val renamed = server != null && state.address.trim() == server.url && state.name != server.name
    val nameDescription = uiText(CopyR.string.copy_name_optional)
    val addressDescription = uiText(CopyR.string.copy_domain_or_ip_address)
    GroupTitle(uiText(CopyR.string.copy_address_details))
    SettingsCard(compact = true) {
        val colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
        TextField(state.name, onNameChanged, Modifier.fillMaxWidth().testTag("server.name").semantics { contentDescription = nameDescription },
            placeholder = { Text(uiText(CopyR.string.copy_name_optional)) }, singleLine = true, enabled = !busy, colors = colors)
        HorizontalDivider()
        TextField(state.address, onAddressChanged, Modifier.fillMaxWidth().testTag("server.address").semantics { contentDescription = addressDescription },
            placeholder = { Text(uiText(CopyR.string.copy_domain_or_ip_address)) }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = colors)
    }
    SettingsCard(compact = true) {
        TextButton(onClick = { onTestConnection(state.address) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.testingConnection) uiText(CopyR.string.copy_testing) else uiText(CopyR.string.copy_test_connection)) }
        ConnectionTestResult(state)
        Caption(uiText(CopyR.string.copy_a_successful_test_only_confirms_reachability_it_does_not_verify))
    }
    SettingsCard(compact = true) {
        TextButton(onClick = onSave, enabled = !busy && state.address.isNotBlank() && (tested || renamed),
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) uiText(CopyR.string.copy_saving) else uiText(CopyR.string.copy_save)) }
        state.error?.let { ErrorNote(it) }
    }
    if (server != null) {
        val selected = state.settings?.activeUrl == server.url
        SettingsCard(compact = true) {
            TextButton(onClick = { onSelect(server.url) }, enabled = !busy && !selected,
                modifier = Modifier.fillMaxWidth()) { Text(if (selected) uiText(CopyR.string.copy_selected_server) else uiText(CopyR.string.copy_select_server)) }
            Caption(uiText(CopyR.string.copy_selecting_an_address_pauses_sync_your_account_stays_signed_in))
        }
        SettingsCard(compact = true) {
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(uiText(CopyR.string.copy_delete_address)) }
        }
    }
}
