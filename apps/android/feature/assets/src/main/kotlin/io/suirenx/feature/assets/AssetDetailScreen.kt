package io.suirenx.feature.assets

import io.suirenx.core.ui.localization.moneyFormat
import io.suirenx.core.ui.localization.moneyText
import io.suirenx.core.ui.localization.dateText

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenNumberFont
import io.suirenx.core.ui.theme.SuirenXTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.Locale

@Composable
fun AssetDetailRoute(
    assetId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(assetId) { viewModel.load(assetId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    BackHandler { if (!state.isSaving) onBack() }
    AssetDetailScreen(
        state = state,
        onBack = { if (!state.isSaving) onBack() },
        onRetry = viewModel::retry,
        onEdit = onEdit,
        onChangeStatus = viewModel::openStatusDialog,
        onRetiredDateChange = viewModel::changeRetiredDate,
        onDismissStatus = viewModel::dismissStatusDialog,
        onSaveStatus = viewModel::saveStatus,
        onChangeArchive = viewModel::openArchiveDialog,
        onDismissArchive = viewModel::dismissArchiveDialog,
        onSaveArchive = viewModel::saveArchive,
        onSalePriceChange = viewModel::changeSalePrice,
        onSaveSalePrice = viewModel::saveSalePrice,
        onDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        modifier = modifier,
    )
}

@Composable
fun AssetDetailScreen(
    state: AssetDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onChangeStatus: () -> Unit,
    onRetiredDateChange: (String) -> Unit,
    onDismissStatus: () -> Unit,
    onSaveStatus: () -> Unit,
    onChangeArchive: () -> Unit,
    onDismissArchive: () -> Unit,
    onSaveArchive: () -> Unit,
    modifier: Modifier = Modifier,
    onSalePriceChange: (String) -> Unit = {},
    onSaveSalePrice: () -> Unit = {},
    onDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
) {
    if (state.deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) onDismissDelete() },
            title = { Text(uiText(CopyR.string.copy_delete_this_asset)) },
            text = { Column {
                Text(uiText(CopyR.string.copy_count_cannot_be_restored_from_the_list_after_deletion_synced, state.asset?.name.orEmpty()))
                state.deleteError?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = onConfirmDelete, enabled = !state.isSaving) { Text(uiText(CopyR.string.copy_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissDelete, enabled = !state.isSaving) { Text(uiText(CopyR.string.copy_cancel)) } },
        )
    }
    if (state.statusTarget != null) {
        StatusChangeDialog(state, onRetiredDateChange, onDismissStatus, onSaveStatus)
    }
    if (state.archiveTarget != null) {
        ArchiveChangeDialog(state, onDismissArchive, onSaveArchive)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DetailTopBar(
                editEnabled = state.asset != null && !state.asset.isArchived && !state.isSaving,
                onBack = onBack,
                onEdit = onEdit,
            )
            when {
                state.isLoading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null && state.asset == null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiText(state.errorMessage),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text(uiText(CopyR.string.copy_retry)) }
                    }
                }
                state.asset != null -> {
                    AssetDetailContent(state.asset)
                    if (state.asset.isArchived) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedTextField(
                                value = state.salePriceInput, onValueChange = onSalePriceChange,
                                label = { Text(uiText(CopyR.string.copy_sale_price_optional)) }, placeholder = { Text(uiText(CopyR.string.copy_leave_blank_if_not_provided)) },
                                enabled = !state.isSaving, singleLine = true, isError = state.salePriceError != null,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(state.salePriceError?.let { uiText(it) } ?: uiText(CopyR.string.copy_zero_and_up_to_two_decimal_places_are_supported_save), style = MaterialTheme.typography.bodySmall,
                                color = if (state.salePriceError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onSaveSalePrice, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.isSavingSalePrice) uiText(CopyR.string.copy_saving) else uiText(CopyR.string.copy_save_sale_price))
                            }
                        }
                    }
                    if (!state.asset.isArchived) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onEdit, enabled = !state.isSaving, modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                                Text(uiText(CopyR.string.copy_edit_asset))
                            }
                            OutlinedButton(onClick = onChangeStatus, enabled = !state.isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(if (state.asset.status == AssetStatus.Active) uiText(CopyR.string.copy_retire) else uiText(CopyR.string.copy_reactivate))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onChangeArchive,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    ) {
                        Text(if (state.asset.isArchived) uiText(CopyR.string.copy_unarchive_asset) else uiText(CopyR.string.copy_archive_asset))
                    }
                    TextButton(onClick = onDelete, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Text(uiText(CopyR.string.copy_delete_asset), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChangeDialog(
    state: AssetDetailUiState,
    onDateChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val retiring = state.statusTarget == AssetStatus.Retired
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val purchaseDate = state.asset?.purchaseDate ?: LocalDate.now()
    val today = LocalDate.now()
    if (showDatePicker && retiring) {
        val selectableDates = remember(purchaseDate, today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isBefore(purchaseDate) && !date.isAfter(today)
                }
                override fun isSelectableYear(year: Int) = year in purchaseDate.year..today.year
            }
        }
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(state.retiredDateInput) }.getOrDefault(today)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = purchaseDate.year..today.year,
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showDatePicker = false
                }, enabled = dateState.selectedDateMillis != null && !state.isSaving) { Text(uiText(CopyR.string.copy_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(uiText(CopyR.string.copy_cancel)) } },
        ) { DatePicker(state = dateState, showModeToggle = false) }
    }
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(if (retiring) uiText(CopyR.string.copy_retire_asset) else uiText(CopyR.string.copy_reactivate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (retiring) uiText(CopyR.string.copy_held_days_and_cost_per_day_include_the_retirement_date)
                    else uiText(CopyR.string.copy_clear_the_retirement_date_and_calculate_held_days_and_cost),
                )
                if (retiring) {
                    Text(uiText(CopyR.string.copy_retirement_date), style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !state.isSaving,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(state.retiredDateInput, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.DateRange, contentDescription = uiText(CopyR.string.copy_choose_retirement_date))
                    }
                    Text(uiText(CopyR.string.copy_purchased_count, state.asset?.purchaseDate), style = MaterialTheme.typography.bodySmall)
                }
                state.statusError?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) uiText(CopyR.string.copy_saving) else uiText(CopyR.string.copy_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(uiText(CopyR.string.copy_cancel)) }
        },
    )
}

@Composable
private fun ArchiveChangeDialog(
    state: AssetDetailUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val archiving = state.archiveTarget == true
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (archiving) uiText(CopyR.string.copy_archive_asset) else uiText(CopyR.string.copy_unarchive_asset_unarchive_asset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (archiving) uiText(CopyR.string.copy_archived_assets_are_excluded_from_everyday_lists_and_totals_data)
                    else uiText(CopyR.string.copy_restore_its_previous_lifecycle_status_and_include_it_in_asset),
                )
                state.archiveError?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) uiText(CopyR.string.copy_saving) else if (archiving) uiText(CopyR.string.copy_confirm_archive) else uiText(CopyR.string.copy_confirm_unarchive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(uiText(CopyR.string.copy_cancel)) }
        },
    )
}

@Composable
private fun DetailTopBar(
    editEnabled: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(uiText(CopyR.string.copy_assets), modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        if (editEnabled) {
            CircleBarButton(
                onClick = onEdit,
                contentDescription = uiText(CopyR.string.copy_edit_asset),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleBarButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(44.dp).semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun AssetDetailContent(asset: Asset, modifier: Modifier = Modifier) {
    val currency = moneyFormat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        val iconOption = assetIconOption(asset.iconKey)
        AssetCategoryIcon(
            option = iconOption,
            modifier = Modifier.size(78.dp),
            glyphSize = 64.dp,
            cornerRadius = 25.dp,
        )
        Spacer(Modifier.height(18.dp))
        Text(asset.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        StatusChip(asset.status)
        Spacer(Modifier.height(14.dp))
        Text(currency.format(java.math.BigDecimal.valueOf(asset.priceCents, 2)), fontSize = 24.sp, lineHeight = 30.sp, fontFamily = SuirenNumberFont, letterSpacing = (-0.48).sp, fontWeight = FontWeight.Normal)
        Text(uiText(CopyR.string.copy_purchase_price_purchase_price), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (asset.isArchived) {
            Spacer(Modifier.height(10.dp))
            Text(uiText(CopyR.string.copy_archived), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                value = "${asset.heldDays}",
                label = uiText(CopyR.string.copy_held_days),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = currency.format(java.math.BigDecimal.valueOf(asset.dailyCostCents, 2)),
                label = uiText(CopyR.string.copy_cost_per_day),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = warrantyLabel(asset),
                label = uiText(CopyR.string.copy_warranty_status),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        InfoCard(
            rows = (listOf(
                uiText(CopyR.string.copy_purchase_date) to dateText(asset.purchaseDate),
            ) + if (asset.status == AssetStatus.Retired) {
                listOf(uiText(CopyR.string.copy_retirement_date) to (asset.retiredDate?.let { dateText(it) } ?: uiText(CopyR.string.copy_not_recorded)))
            } else emptyList()) + asset.archivedAt?.let {
                listOf(uiText(CopyR.string.copy_archive_date) to dateText(it.atZone(ZoneId.systemDefault()).toLocalDate()))
            }.orEmpty(),
        )
        val metadata = buildList {
            asset.purchaseChannel?.takeIf(String::isNotBlank)?.let { add(uiText(CopyR.string.copy_purchase_channel) to it) }
            asset.warrantyEndDate?.let { add(uiText(CopyR.string.copy_warranty_end_date_warranty_end_date) to dateText(it)) }
            if (asset.tags.isNotEmpty()) add(uiText(CopyR.string.copy_tags) to asset.tags.joinToString("、"))
            asset.notes.takeIf(String::isNotBlank)?.let { add(uiText(CopyR.string.copy_notes) to it) }
        }
        if (metadata.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCard(rows = metadata)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun warrantyLabel(asset: Asset): String {
    val warrantyEndDate = asset.warrantyEndDate
    return when {
        warrantyEndDate == null -> uiText(CopyR.string.copy_not_set)
        warrantyEndDate.isBefore(LocalDate.now()) -> uiText(CopyR.string.copy_expired_expired)
        else -> uiText(CopyR.string.copy_valid)
    }
}

@Composable
private fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontFamily = SuirenNumberFont,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusChip(status: AssetStatus, modifier: Modifier = Modifier) {
    val active = status == AssetStatus.Active
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = if (active) uiText(CopyR.string.copy_active) else uiText(CopyR.string.copy_retired),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 860)
@Composable
private fun AssetDetailScreenPreview() {
    SuirenXTheme {
        AssetDetailScreen(
            state = AssetDetailUiState(
                isLoading = false,
                asset = Asset(
                    "1", "MacBook Pro", 1_699_900, LocalDate.now(),
                    AssetStatus.Active, "", 133, 12_781,
                ),
            ),
            onBack = {},
            onRetry = {},
            onEdit = {},
            onChangeStatus = {},
            onRetiredDateChange = {},
            onDismissStatus = {},
            onSaveStatus = {},
            onChangeArchive = {},
            onDismissArchive = {},
            onSaveArchive = {},
        )
    }
}
