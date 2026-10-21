package io.suirenx.feature.expiry

import io.suirenx.core.ui.localization.moneyFormat
import io.suirenx.core.ui.localization.moneyText
import io.suirenx.core.ui.localization.dateText

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Surface
import io.suirenx.core.ui.icon.SuirenIcons
import io.suirenx.core.ui.theme.SuirenNumberFont
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.ExpiryBucket
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ExpiryRoute(
    onBack: () -> Unit,
    onAdd: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExpiryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val requestEditorClose = {
        if (state.editor?.isDirty == true && !state.busy) showDiscard = true else viewModel.closeEditor()
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(uiText(CopyR.string.copy_discard_unsaved_changes)) },
            text = { Text(uiText(CopyR.string.copy_this_item_has_unsaved_changes_leaving_discards_them)) },
            confirmButton = { TextButton(onClick = { showDiscard = false; viewModel.closeEditor() }) { Text(uiText(CopyR.string.copy_discard_changes)) } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(uiText(CopyR.string.copy_keep_editing)) } },
        )
    }
    ExpiryScreen(
        state = state, onBack = onBack, onAdd = { viewModel.openNew(); onAdd() },
        onRetry = viewModel::refresh, onRefresh = viewModel::refresh,
        onFilter = viewModel::selectFilter, onArchived = viewModel::selectArchived,
        onQueryChanged = viewModel::setQuery,
        searching = searching,
        onSearch = { searching = !searching },
        onOpen = viewModel::openDetail, onCloseDetail = viewModel::closeDetail, onEdit = viewModel::openEdit,
        onStatus = viewModel::updateStatus, onArchive = viewModel::archive,
        onDelete = viewModel::requestDelete, onConfirmDelete = viewModel::confirmDelete, onDismissDelete = viewModel::dismissDelete,
        onCloseEditor = requestEditorClose, onSaveEditor = viewModel::saveEditor,
        onEditorName = viewModel::editName, onEditorCategory = viewModel::editCategory,
        onEditorPackageExpiry = viewModel::editPackageExpiry, onEditorOpenedDate = viewModel::editOpenedDate,
        onEditorOpenedDays = viewModel::editOpenedDays, onEditorLocation = viewModel::editLocation,
        onEditorNotes = viewModel::editNotes,
        modifier = modifier,
    )
}

@Composable
fun ExpirySummaryBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ExpiryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.expiredCount > 0) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = uiText(CopyR.string.copy_expiry_reminder), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.padding(6.dp))
                Text(uiQuantity(CopyR.plurals.copy_count_items_have_expired, state.expiredCount), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ExpiryScreen(
    state: ExpiryUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit = {},
    onFilter: (ExpiryBucket?) -> Unit,
    onArchived: () -> Unit,
    onQueryChanged: (String) -> Unit = {},
    onOpen: (ExpiryItem) -> Unit,
    onCloseDetail: () -> Unit,
    onEdit: (ExpiryItem) -> Unit,
    onStatus: (ExpiryItemStatus) -> Unit,
    onArchive: (Boolean) -> Unit,
    onCloseEditor: () -> Unit,
    onSaveEditor: () -> Unit,
    onEditorName: (String) -> Unit,
    onEditorCategory: (String) -> Unit,
    onEditorPackageExpiry: (String) -> Unit,
    onEditorOpenedDate: (String) -> Unit,
    onEditorOpenedDays: (String) -> Unit,
    onEditorLocation: (String) -> Unit,
    onEditorNotes: (String) -> Unit,
    searching: Boolean = false,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
) {
    val groupTitles = listOf(uiText(CopyR.string.copy_archived), uiText(CopyR.string.copy_needs_attention), uiText(CopyR.string.copy_upcoming), uiText(CopyR.string.copy_handled))
    state.editor?.let { editor ->
        ExpiryEditorDialog(editor, state.busy, onCloseEditor, onSaveEditor, onEditorName, onEditorCategory, onEditorPackageExpiry, onEditorOpenedDate, onEditorOpenedDays, onEditorLocation, onEditorNotes)
    }
    if (state.deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onDismissDelete() },
            title = { Text(uiText(CopyR.string.copy_delete_this_item)) },
            text = { Column {
                Text(uiText(CopyR.string.copy_count_cannot_be_restored_from_the_list_after_deletion_synced, state.detail?.name.orEmpty()))
                state.deleteError?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = onConfirmDelete, enabled = !state.busy) { Text(uiText(CopyR.string.copy_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissDelete, enabled = !state.busy) { Text(uiText(CopyR.string.copy_cancel)) } },
        )
    }
    state.detail?.takeUnless { state.deleteConfirmation }?.let { item ->
        ExpiryDetailDialog(item, state.soonDays, state.busy, onCloseDetail, { onEdit(item) }, onStatus, { onArchive(item.archivedAt == null) }, onDelete)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText(CopyR.string.copy_back_to_tools), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(uiText(CopyR.string.copy_tools), fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(uiText(CopyR.string.copy_tool_details), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText(CopyR.string.copy_expiry_items), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(uiText(CopyR.string.copy_stay_ahead_of_expiry_dates), fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                Text(uiText(CopyR.string.copy_track_expiry_dates_and_view_upcoming_or_expired_items_in), fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            val currentItems = state.items.filter { it.archivedAt == null }
            val expired = currentItems.count { it.bucket(java.time.LocalDate.now(), state.soonDays) == ExpiryBucket.Expired }
            val soon = currentItems.count { it.bucket(java.time.LocalDate.now(), state.soonDays) in setOf(ExpiryBucket.DueToday, ExpiryBucket.ExpiringSoon) }
            val normal = currentItems.count { it.bucket(java.time.LocalDate.now(), state.soonDays) == ExpiryBucket.Normal }
            val available = !state.loading && state.error == null
            Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(uiText(CopyR.string.copy_status_overview), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (available) uiQuantity(CopyR.plurals.copy_count_records, currentItems.size) else uiText(CopyR.string.copy_records), fontFamily = SuirenNumberFont, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.72).sp)
                            Text(if (available) uiQuantity(CopyR.plurals.copy_count_need_attention_count_need_attention, expired + soon) else uiText(CopyR.string.copy_loading_status), fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(uiText(CopyR.string.copy_local_data), modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min)) {
                        listOf(uiText(CopyR.string.copy_expired) to expired, uiText(CopyR.string.copy_expiring_soon) to soon, uiText(CopyR.string.copy_normal) to normal).forEachIndexed { index, (label, count) ->
                            if (index > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.weight(1f).padding(start = if (index > 0) 10.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (available) "$count" else "—", fontFamily = SuirenNumberFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
                                Text(label, fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText(CopyR.string.copy_back_to_items))
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(uiText(CopyR.string.copy_search_name_category_or_location)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = uiText(CopyR.string.copy_clear_search))
                                }
                            }
                        },
                    )
                } else {
                    Text(uiText(CopyR.string.copy_reminders), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = uiText(CopyR.string.copy_search_items), modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = uiText(CopyR.string.copy_refresh_items), modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = uiText(CopyR.string.copy_add_item), modifier = Modifier.size(20.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to uiText(CopyR.string.copy_all), ExpiryBucket.Expired to uiText(CopyR.string.copy_expired), ExpiryBucket.ExpiringSoon to uiText(CopyR.string.copy_expiring_soon), ExpiryBucket.Normal to uiText(CopyR.string.copy_normal)).forEach { (bucket, label) ->
                    FilterChip(selected = !state.isArchivedFilter && state.filter == bucket, onClick = { onFilter(bucket) }, label = { Text(label, fontSize = 11.sp) }, shape = RoundedCornerShape(50))
                }
                FilterChip(selected = state.isArchivedFilter, onClick = onArchived, label = { Text(uiText(CopyR.string.copy_archived), fontSize = 11.sp) }, shape = RoundedCornerShape(50))
            }
        }
        when {
            state.loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.error != null -> item { Column { Text(uiText(state.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }); TextButton(onClick = onRetry) { Text(uiText(CopyR.string.copy_retry)) } } }
            state.visibleItems.isEmpty() -> item {
                Text(if (state.isArchivedFilter) uiText(CopyR.string.copy_no_archived_items) else uiText(CopyR.string.copy_no_matching_items_tap_to_add_one), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val sorted = state.visibleItems.sortedBy { it.actualExpiryDate }
                val (needsAttention, later) = sorted.partition {
                    it.bucket(java.time.LocalDate.now(), state.soonDays) in setOf(ExpiryBucket.Expired, ExpiryBucket.DueToday, ExpiryBucket.ExpiringSoon)
                }
                val groups = if (state.isArchivedFilter) listOf(groupTitles[0] to sorted) else listOf(
                    groupTitles[1] to needsAttention,
                    groupTitles[2] to later.filter { it.status == ExpiryItemStatus.InUse },
                    groupTitles[3] to later.filter { it.status != ExpiryItemStatus.InUse },
                )
                groups.forEach { (title, entries) ->
                    if (entries.isNotEmpty()) {
                        item(key = "group-$title") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(uiQuantity(CopyR.plurals.copy_count_items, entries.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(entries, key = { it.id }) { item -> ExpiryItemCard(item, state.soonDays, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiryItemCard(item: ExpiryItem, soonDays: Int, onOpen: (ExpiryItem) -> Unit) {
    val bucket = item.bucket(java.time.LocalDate.now(), soonDays)
    val urgent = bucket == ExpiryBucket.Expired || bucket == ExpiryBucket.DueToday
    Card(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (urgent) MaterialTheme.colorScheme.errorContainer else if (bucket == ExpiryBucket.ExpiringSoon) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(SuirenIcons.AssetBox, contentDescription = null, modifier = Modifier.size(23.dp), tint = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), item.actualExpiryDate)
                val reminder = if (bucket == ExpiryBucket.Normal || bucket == ExpiryBucket.ExpiringSoon) uiQuantity(CopyR.plurals.copy_expires_in_count_days, days) else bucketLabel(bucket, item.actualExpiryDate)
                Text(listOf(reminder, item.category).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 10.sp, lineHeight = 16.sp, color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(dateText(item.actualExpiryDate), fontSize = 11.sp, fontFamily = SuirenNumberFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.archivedAt != null) Icon(Icons.Default.Info, contentDescription = uiText(CopyR.string.copy_archived), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun bucketLabel(bucket: ExpiryBucket, date: java.time.LocalDate): String = when (bucket) {
    ExpiryBucket.Expired -> uiQuantity(CopyR.plurals.copy_expired_count_days_ago, java.time.temporal.ChronoUnit.DAYS.between(date, java.time.LocalDate.now()))
    ExpiryBucket.DueToday -> uiText(CopyR.string.copy_expires_today)
    ExpiryBucket.ExpiringSoon -> uiText(CopyR.string.copy_expiring_soon)
    ExpiryBucket.Normal -> uiText(CopyR.string.copy_normal)
    ExpiryBucket.Inactive -> uiText(CopyR.string.copy_handled)
}

@Composable
private fun ExpiryEditorDialog(editor: ExpiryEditorState, busy: Boolean, onClose: () -> Unit, onSave: () -> Unit, onName: (String) -> Unit, onCategory: (String) -> Unit, onPackage: (String) -> Unit, onOpened: (String) -> Unit, onDays: (String) -> Unit, onLocation: (String) -> Unit, onNotes: (String) -> Unit) {
    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.Default.Close, contentDescription = uiText(CopyR.string.copy_close)) }
                    Text(if (editor.id == null) uiText(CopyR.string.copy_add_item) else uiText(CopyR.string.copy_edit_item), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSave, enabled = !busy) { Text(if (busy) uiText(CopyR.string.copy_saving) else uiText(CopyR.string.copy_save)) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ExpiryFormSection(uiText(CopyR.string.copy_item_details)) {
                        OutlinedTextField(editor.name, onName, label = { Text(uiText(CopyR.string.copy_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(editor.category, onCategory, label = { Text(uiText(CopyR.string.copy_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(editor.location, onLocation, label = { Text(uiText(CopyR.string.copy_location_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    ExpiryFormSection(uiText(CopyR.string.copy_expiry_dates)) {
                        ExpiryDateInputField(editor.packageExpiryDate, onPackage, uiText(CopyR.string.copy_package_expiry_date_package_expiry_date), busy)
                        ExpiryDateInputField(editor.openedDate, onOpened, uiText(CopyR.string.copy_opened_date_optional), busy, allowEmpty = true)
                        OutlinedTextField(editor.openedValidityDays, onDays, label = { Text(uiText(CopyR.string.copy_days_valid_after_opening_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        Text(uiText(CopyR.string.copy_provide_both_the_opened_date_and_validity_period_or_leave), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ExpiryFormSection(uiText(CopyR.string.copy_notes)) {
                        OutlinedTextField(editor.notes, onNotes, label = { Text(uiText(CopyR.string.copy_notes_optional)) }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    editor.error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) uiText(CopyR.string.copy_saving) else uiText(CopyR.string.copy_save_item)) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpiryFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ExpiryDateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    busy: Boolean,
    allowEmpty: Boolean = false,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(if (allowEmpty) uiText(CopyR.string.copy_yyyy_mm_dd_optional) else "YYYY-MM-DD") },
        singleLine = true,
        enabled = !busy,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }, enabled = !busy) {
                Icon(Icons.Default.DateRange, contentDescription = uiText(CopyR.string.copy_choose_date))
            }
        },
    )
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value.toLocalDateMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(it.toLocalDateUtc().toString()) }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text(uiText(CopyR.string.copy_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(uiText(CopyR.string.copy_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun String.toLocalDateMillis(): Long? = runCatching {
    LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun ExpiryDetailDialog(item: ExpiryItem, soonDays: Int, busy: Boolean, onClose: () -> Unit, onEdit: () -> Unit, onStatus: (ExpiryItemStatus) -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val bucket = item.bucket(java.time.LocalDate.now(), soonDays)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(item.name) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(uiText(CopyR.string.copy_category_count, item.category)); Text(uiText(CopyR.string.copy_package_expiry_count, dateText(item.packageExpiryDate))); item.openedDate?.let { Text(uiText(CopyR.string.copy_opened_count_validity_days_count, dateText(it), item.openedValidityDays)) }; Text(uiText(CopyR.string.copy_effective_expiry_count, dateText(item.actualExpiryDate))); Text(bucketLabel(bucket, item.actualExpiryDate)); if (item.location.isNotBlank()) Text(uiText(CopyR.string.copy_location_count, item.location)); if (item.notes.isNotBlank()) Text(item.notes); TextButton(onClick = onDelete, enabled = !busy) { Text(uiText(CopyR.string.copy_delete_item), color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = onEdit, enabled = !busy && item.archivedAt == null) { Text(uiText(CopyR.string.copy_edit)) } }, dismissButton = { Row { if (item.status == ExpiryItemStatus.InUse && item.archivedAt == null) { TextButton(onClick = { onStatus(ExpiryItemStatus.UsedUp) }, enabled = !busy) { Text(uiText(CopyR.string.copy_use_up)) }; TextButton(onClick = { onStatus(ExpiryItemStatus.Discarded) }, enabled = !busy) { Text(uiText(CopyR.string.copy_discard)) } }; TextButton(onClick = onArchive, enabled = !busy) { Text(if (item.archivedAt == null) uiText(CopyR.string.copy_archive) else uiText(CopyR.string.copy_unarchive)) }; TextButton(onClick = onClose) { Text(uiText(CopyR.string.copy_close)) } } })
}
