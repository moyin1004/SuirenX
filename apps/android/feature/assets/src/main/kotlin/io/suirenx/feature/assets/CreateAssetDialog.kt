package io.suirenx.feature.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.suirenx.core.ui.theme.SuirenXTheme

@Composable
fun CreateAssetDialog(
    state: AssetFormState,
    onNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onPurchaseDateChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(stringResource(R.string.create_asset_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.asset_name_label)) },
                    enabled = !state.isSaving,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.price,
                    onValueChange = onPriceChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.asset_price_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !state.isSaving,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.purchaseDate,
                    onValueChange = onPurchaseDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.asset_purchase_date_label)) },
                    supportingText = { Text("YYYY-MM-DD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    enabled = !state.isSaving,
                    singleLine = true,
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(stringResource(if (state.isSaving) R.string.asset_saving else R.string.asset_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text(stringResource(R.string.asset_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateAssetDialogPreview() {
    SuirenXTheme {
        CreateAssetDialog(
            state = AssetFormState(name = "机械键盘", price = "899.00", purchaseDate = "2026-09-05"),
            onNameChanged = {}, onPriceChanged = {}, onPurchaseDateChanged = {}, onSave = {}, onDismiss = {},
        )
    }
}
