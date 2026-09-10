package io.suirenx.feature.expiry

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        onDelete = viewModel::requestDelete, onConfirmDelete = viewModel::confirmDelete, onDismissDelete = viewModel::dismissDelete,
        onCloseEditor = viewModel::closeEditor, onSaveEditor = viewModel::saveEditor,
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
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
) {
    state.editor?.let { editor ->
        ExpiryEditorDialog(editor, state.busy, onCloseEditor, onSaveEditor, onEditorName, onEditorCategory, onEditorPackageExpiry, onEditorOpenedDate, onEditorOpenedDays, onEditorLocation, onEditorNotes)
    }
    if (state.deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onDismissDelete() },
            title = { Text("删除用品？") },
            text = { Column {
                Text("删除“${state.detail?.name.orEmpty()}”后无法在列表中恢复。已同步的数据将在下次同步时通知其他设备。")
                state.deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = onConfirmDelete, enabled = !state.busy) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissDelete, enabled = !state.busy) { Text("取消") } },
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回工具", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("工具", fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("工具详情", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("用品 / 保质期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("下一次补充，提前知道", fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                Text("记录消耗品和有期限的用品，提醒只在需要时出现。", fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("状态概览", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (available) "${currentItems.size} 项记录" else "— 项记录", fontFamily = SuirenNumberFont, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.72).sp)
                            Text(if (available) "${expired + soon} 项需要关注" else "正在读取状态", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(if (state.remoteMode) "服务端" else "本地已更新", modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min)) {
                        listOf("已过期" to expired, "临期" to soon, "正常" to normal).forEachIndexed { index, (label, count) ->
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
                Text("提醒列表", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("按日期排序", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "新增用品", modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", ExpiryBucket.Expired to "已过期", ExpiryBucket.ExpiringSoon to "临期", ExpiryBucket.Normal to "正常").forEach { (bucket, label) ->
                    FilterChip(selected = !state.isArchivedFilter && state.filter == bucket, onClick = { onFilter(bucket) }, label = { Text(label, fontSize = 11.sp) }, shape = RoundedCornerShape(50))
                }
                FilterChip(selected = state.isArchivedFilter, onClick = onArchived, label = { Text("已归档", fontSize = 11.sp) }, shape = RoundedCornerShape(50))
            }
        }
        when {
            state.loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            state.error != null -> item { Column { Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }); TextButton(onClick = onRetry) { Text("重试") } } }
            state.visibleItems.isEmpty() -> item {
                Text(if (state.isArchivedFilter) "暂无已归档用品" else "暂无符合条件的用品，点击 + 添加。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val sorted = state.visibleItems.sortedBy { it.actualExpiryDate }
                val (needsAttention, later) = sorted.partition {
                    it.bucket(java.time.LocalDate.now(), state.soonDays) in setOf(ExpiryBucket.Expired, ExpiryBucket.DueToday, ExpiryBucket.ExpiringSoon)
                }
                val groups = if (state.isArchivedFilter) listOf("已归档" to sorted) else listOf(
                    "需要处理" to needsAttention,
                    "后续提醒" to later.filter { it.status == ExpiryItemStatus.InUse },
                    "已处理" to later.filter { it.status != ExpiryItemStatus.InUse },
                )
                groups.forEach { (title, entries) ->
                    if (entries.isNotEmpty()) {
                        item(key = "group-$title") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${entries.size} 项", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                val reminder = if (bucket == ExpiryBucket.Normal || bucket == ExpiryBucket.ExpiringSoon) "$days 天后到期" else bucketLabel(bucket, item.actualExpiryDate)
                Text(listOf(reminder, item.category).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 10.sp, lineHeight = 16.sp, color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.actualExpiryDate.format(java.time.format.DateTimeFormatter.ofPattern("MM.dd")), fontSize = 11.sp, fontFamily = SuirenNumberFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.archivedAt != null) Icon(Icons.Default.Info, contentDescription = "已归档", modifier = Modifier.size(16.dp))
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
private fun ExpiryDetailDialog(item: ExpiryItem, soonDays: Int, busy: Boolean, onClose: () -> Unit, onEdit: () -> Unit, onStatus: (ExpiryItemStatus) -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val bucket = item.bucket(java.time.LocalDate.now(), soonDays)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(item.name) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("分类：${item.category}"); Text("包装到期日：${item.packageExpiryDate}"); item.openedDate?.let { Text("开封日：$it · ${item.openedValidityDays} 天") }; Text("实际到期日：${item.actualExpiryDate}"); Text(bucketLabel(bucket, item.actualExpiryDate)); if (item.location.isNotBlank()) Text("位置：${item.location}"); if (item.notes.isNotBlank()) Text(item.notes); TextButton(onClick = onDelete, enabled = !busy) { Text("删除用品", color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = onEdit, enabled = !busy && item.archivedAt == null) { Text("编辑") } }, dismissButton = { Row { if (item.status == ExpiryItemStatus.InUse && item.archivedAt == null) { TextButton(onClick = { onStatus(ExpiryItemStatus.UsedUp) }, enabled = !busy) { Text("用完") }; TextButton(onClick = { onStatus(ExpiryItemStatus.Discarded) }, enabled = !busy) { Text("丢弃") } }; TextButton(onClick = onArchive, enabled = !busy) { Text(if (item.archivedAt == null) "归档" else "恢复") }; TextButton(onClick = onClose) { Text("关闭") } } })
}
