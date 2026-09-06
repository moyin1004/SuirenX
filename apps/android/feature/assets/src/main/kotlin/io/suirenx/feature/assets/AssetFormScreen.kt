package io.suirenx.feature.assets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.ui.icon.MaterialSymbol
import io.suirenx.core.ui.theme.SuirenXTheme

@Composable
fun AssetFormRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onClose() } }
    BackHandler(enabled = !state.isSaving) { onClose() }
    AssetFormScreen(
        state = state,
        onNameChanged = viewModel::onNameChanged,
        onPriceChanged = viewModel::onPriceChanged,
        onPurchaseDateChanged = viewModel::onPurchaseDateChanged,
        onPurchaseChannelChanged = viewModel::onPurchaseChannelChanged,
        onWarrantyEndDateChanged = viewModel::onWarrantyEndDateChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onTagsChanged = viewModel::onTagsChanged,
        onOpenIconPicker = viewModel::openIconPicker,
        onCloseIconPicker = viewModel::closeIconPicker,
        onIconSelected = viewModel::onIconSelected,
        onSave = viewModel::save,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
fun AssetFormScreen(
    state: AssetFormUiState,
    onNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onPurchaseDateChanged: (String) -> Unit,
    onPurchaseChannelChanged: (String) -> Unit,
    onWarrantyEndDateChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onTagsChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenIconPicker: () -> Unit,
    onCloseIconPicker: () -> Unit,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isIconPickerOpen) {
        AssetIconPicker(state.iconKey, onIconSelected, onCloseIconPicker)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FormTopBar(
                title = stringResource(
                    if (state.isEdit) R.string.edit_asset_title else R.string.create_asset_title,
                ),
                isSaving = state.isSaving,
                onClose = onClose,
                onSave = onSave,
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "无法加载资产，请检查网络或后端地址后重试",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onClose) { Text("关闭") }
                    }
                }
                else -> FormBody(state, onNameChanged, onPriceChanged, onPurchaseDateChanged, onPurchaseChannelChanged, onWarrantyEndDateChanged, onNotesChanged, onTagsChanged, onOpenIconPicker)
            }
        }
    }
}

@Composable
private fun FormTopBar(
    title: String,
    isSaving: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = { if (!isSaving) onClose() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            onClick = { if (!isSaving) onSave() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "保存",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormBody(
    state: AssetFormUiState,
    onNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onPurchaseDateChanged: (String) -> Unit,
    onPurchaseChannelChanged: (String) -> Unit,
    onWarrantyEndDateChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onTagsChanged: (String) -> Unit,
    onOpenIconPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val iconOption = assetIconOption(state.iconKey)
            Surface(
                onClick = onOpenIconPicker,
                enabled = !state.isSaving,
                shape = RoundedCornerShape(32.dp),
                color = iconOption.containerColor,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaterialSymbol(
                        glyph = iconOption.glyph,
                        tint = iconOption.contentColor,
                        size = 48.dp,
                    )
                    Text("更换图标", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.asset_name_label)) },
            enabled = !state.isSaving,
            singleLine = true,
        )
        OutlinedTextField(
            value = state.purchaseChannel,
            onValueChange = onPurchaseChannelChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("购买渠道（选填）") },
            singleLine = true,
            enabled = !state.isSaving,
        )
        OutlinedTextField(
            value = state.warrantyEndDate,
            onValueChange = onWarrantyEndDateChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("保修截止日（选填）") },
            supportingText = { Text("YYYY-MM-DD") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true,
            enabled = !state.isSaving,
        )
        OutlinedTextField(
            value = state.tags,
            onValueChange = onTagsChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("标签（选填，用逗号分隔）") },
            supportingText = { Text("最多 20 个") },
            singleLine = false,
            enabled = !state.isSaving,
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注（选填）") },
            minLines = 3,
            maxLines = 8,
            enabled = !state.isSaving,
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
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 860)
@Composable
private fun AssetFormScreenPreview() {
    SuirenXTheme {
        AssetFormScreen(
            state = AssetFormUiState(
                isEdit = true,
                name = "机械键盘",
                price = "899.00",
                purchaseDate = "2026-09-05",
            ),
            onNameChanged = {},
            onPriceChanged = {},
            onPurchaseDateChanged = {},
            onPurchaseChannelChanged = {},
            onWarrantyEndDateChanged = {},
            onNotesChanged = {},
            onTagsChanged = {},
            onSave = {},
            onClose = {},
            onOpenIconPicker = {},
            onCloseIconPicker = {},
            onIconSelected = {},
        )
    }
}
