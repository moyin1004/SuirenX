package io.suirenx.feature.expiry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.suirenx.core.domain.SyncConflictResolution

@Composable
fun ExpiryRoute(
    onBack: () -> Unit,
    onAdd: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExpiryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpiryScreen(
        state = state, onBack = onBack, onAdd = { viewModel.openNew(); onAdd() },
        onRetry = viewModel::refresh, onFilter = viewModel::selectFilter, onArchived = viewModel::selectArchived,
        onOpen = viewModel::openDetail, onCloseDetail = viewModel::closeDetail, onEdit = viewModel::openEdit,
        onStatus = viewModel::updateStatus, onArchive = viewModel::archive,
        onCloseEditor = viewModel::closeEditor, onSaveEditor = viewModel::saveEditor,
        onEditorName = viewModel::editName, onEditorCategory = viewModel::editCategory,
        onEditorPackageExpiry = viewModel::editPackageExpiry, onEditorOpenedDate = viewModel::editOpenedDate,
        onEditorOpenedDays = viewModel::editOpenedDays, onEditorLocation = viewModel::editLocation,
        onEditorNotes = viewModel::editNotes, onRetrySync = viewModel::retrySync,
        onResolveSyncConflict = viewModel::resolveSyncConflict, modifier = modifier,
    )
}

@Composable
fun ExpirySummaryBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ExpiryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.expiredCount > 0) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = "过期提醒", tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.padding(6.dp))
                Text("有 ${state.expiredCount} 件用品已过期", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
    onFilter: (ExpiryBucket?) -> Unit,
    onArchived: () -> Unit,
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
    onRetrySync: () -> Unit = {},
    onResolveSyncConflict: (String, SyncConflictResolution) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    state.editor?.let { editor ->
        ExpiryEditorDialog(editor, state.busy, onCloseEditor, onSaveEditor, onEditorName, onEditorCategory, onEditorPackageExpiry, onEditorOpenedDate, onEditorOpenedDays, onEditorLocation, onEditorNotes)
    }
    state.detail?.let { item ->
        ExpiryDetailDialog(item, state.soonDays, state.busy, onCloseDetail, { onEdit(item) }, onStatus, { onArchive(item.archivedAt == null) })
    }
    Scaffold(modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("保质期管理", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "新增用品") }
            }
            Text(
                if (state.remoteMode) "服务器数据 · 离线显示最近缓存，联网后自动重试 · 临期 ${state.soonDays} 天提醒"
                else "保存在本机 · 临期 ${state.soonDays} 天提醒",
                Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.syncStatus.pendingOperations > 0 || state.syncStatus.expiryConflicts.isNotEmpty()) {
                ExpirySyncBanner(state, onRetrySync, onResolveSyncConflict)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", ExpiryBucket.Expired to "已过期", ExpiryBucket.DueToday to "今日到期", ExpiryBucket.ExpiringSoon to "临期", ExpiryBucket.Normal to "正常").forEach { (bucket, label) ->
                    FilterChip(selected = !state.isArchivedFilter && state.filter == bucket, onClick = { onFilter(bucket) }, label = { Text(label) })
                }
                FilterChip(selected = state.isArchivedFilter, onClick = onArchived, label = { Text("已归档") })
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(40.dp))
                state.error != null -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }); Button(onClick = onRetry) { Text("重试") } }
                state.visibleItems.isEmpty() -> Text(if (state.isArchivedFilter) "暂无已归档用品" else "还没有用品，点击右上角新增", Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.visibleItems, key = { it.id }) { item -> ExpiryItemCard(item, state.soonDays, onOpen) }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExpirySyncBanner(
    state: ExpiryUiState,
    onRetry: () -> Unit,
    onResolve: (String, SyncConflictResolution) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.syncStatus.pendingOperations > 0) {
                Text("有 ${state.syncStatus.pendingOperations} 项变更等待同步", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("立即重试") }
            }
            state.syncStatus.lastSyncedAt?.let { Text("最近成功同步：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.syncStatus.expiryConflicts.forEach { conflict ->
                Text("用品“${conflict.localName}”发生同步冲突", fontWeight = FontWeight.Bold)
                Text("本机：${conflict.localName} · 服务器：${conflict.remoteName ?: "已删除"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onResolve(conflict.expiryId, SyncConflictResolution.KeepLocal) }, modifier = Modifier.weight(1f)) { Text("保留本机") }
                    OutlinedButton(onClick = { onResolve(conflict.expiryId, SyncConflictResolution.KeepRemote) }, modifier = Modifier.weight(1f)) { Text("保留服务器") }
                }
            }
        }
    }
}

@Composable
private fun ExpiryItemCard(item: ExpiryItem, soonDays: Int, onOpen: (ExpiryItem) -> Unit) {
    val bucket = item.bucket(java.time.LocalDate.now(), soonDays)
    val urgent = bucket == ExpiryBucket.Expired || bucket == ExpiryBucket.DueToday
    Card(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (urgent) Icons.Default.Warning else Icons.Default.Check, contentDescription = null, tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text("${item.category} · 到期 ${item.actualExpiryDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bucketLabel(bucket, item.actualExpiryDate), color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            if (item.archivedAt != null) Icon(Icons.Default.Info, contentDescription = "已归档")
        }
    }
}

private fun bucketLabel(bucket: ExpiryBucket, date: java.time.LocalDate): String = when (bucket) {
    ExpiryBucket.Expired -> "已过期 ${java.time.temporal.ChronoUnit.DAYS.between(date, java.time.LocalDate.now())} 天"
    ExpiryBucket.DueToday -> "今日到期"
    ExpiryBucket.ExpiringSoon -> "临期"
    ExpiryBucket.Normal -> "正常"
    ExpiryBucket.Inactive -> "已处理"
}

@Composable
private fun ExpiryEditorDialog(editor: ExpiryEditorState, busy: Boolean, onClose: () -> Unit, onSave: () -> Unit, onName: (String) -> Unit, onCategory: (String) -> Unit, onPackage: (String) -> Unit, onOpened: (String) -> Unit, onDays: (String) -> Unit, onLocation: (String) -> Unit, onNotes: (String) -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (editor.id == null) "新增用品" else "编辑用品") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(editor.name, onName, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(editor.category, onCategory, label = { Text("分类") }, singleLine = true)
            OutlinedTextField(editor.packageExpiryDate, onPackage, label = { Text("包装到期日") }, supportingText = { Text("YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(editor.openedDate, onOpened, label = { Text("开封日（选填）") }, singleLine = true)
            OutlinedTextField(editor.openedValidityDays, onDays, label = { Text("开封后有效天数（选填）") }, singleLine = true)
            OutlinedTextField(editor.location, onLocation, label = { Text("存放位置（选填）") }, singleLine = true)
            OutlinedTextField(editor.notes, onNotes, label = { Text("备注（选填）") }, minLines = 2)
            editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }, confirmButton = { TextButton(onClick = onSave, enabled = !busy) { Text(if (busy) "保存中…" else "保存") } }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("取消") } })
}

@Composable
private fun ExpiryDetailDialog(item: ExpiryItem, soonDays: Int, busy: Boolean, onClose: () -> Unit, onEdit: () -> Unit, onStatus: (ExpiryItemStatus) -> Unit, onArchive: () -> Unit) {
    val bucket = item.bucket(java.time.LocalDate.now(), soonDays)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(item.name) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("分类：${item.category}"); Text("包装到期日：${item.packageExpiryDate}"); item.openedDate?.let { Text("开封日：$it · ${item.openedValidityDays} 天") }; Text("实际到期日：${item.actualExpiryDate}"); Text(bucketLabel(bucket, item.actualExpiryDate)); if (item.location.isNotBlank()) Text("位置：${item.location}"); if (item.notes.isNotBlank()) Text(item.notes) } }, confirmButton = { TextButton(onClick = onEdit, enabled = !busy && item.archivedAt == null) { Text("编辑") } }, dismissButton = { Row { if (item.status == ExpiryItemStatus.InUse && item.archivedAt == null) { TextButton(onClick = { onStatus(ExpiryItemStatus.UsedUp) }, enabled = !busy) { Text("用完") }; TextButton(onClick = { onStatus(ExpiryItemStatus.Discarded) }, enabled = !busy) { Text("丢弃") } }; TextButton(onClick = onArchive, enabled = !busy) { Text(if (item.archivedAt == null) "归档" else "恢复") }; TextButton(onClick = onClose) { Text("关闭") } } })
}
