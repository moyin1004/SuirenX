package io.suirenx.feature.assets

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.Asset
import io.suirenx.core.ui.icon.MaterialSymbol
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenNumberFont
import io.suirenx.core.ui.theme.SuirenXTheme
import java.text.NumberFormat
import java.time.LocalDate
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
    onDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
) {
    if (state.deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) onDismissDelete() },
            title = { Text("删除资产？") },
            text = { Column {
                Text("删除“${state.asset?.name.orEmpty()}”后无法在列表中恢复。已同步的数据将在下次同步时通知其他设备。")
                state.deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = onConfirmDelete, enabled = !state.isSaving) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissDelete, enabled = !state.isSaving) { Text("取消") } },
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
                            state.errorMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
                state.asset != null -> {
                    AssetDetailContent(state.asset)
                    if (!state.asset.isArchived) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onEdit, enabled = !state.isSaving, modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                                Text("编辑资产")
                            }
                            OutlinedButton(onClick = onChangeStatus, enabled = !state.isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(if (state.asset.status == AssetStatus.Active) "退役" else "恢复服役")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onChangeArchive,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    ) {
                        Text(if (state.asset.isArchived) "恢复到资产列表" else "归档资产")
                    }
                    TextButton(onClick = onDelete, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Text("删除资产", color = MaterialTheme.colorScheme.error)
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
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(if (retiring) "标记为已退役" else "恢复服役") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (retiring) "持有天数和日均成本将计算到退役当天。"
                    else "将清除退役日期，按购买日至今天重新计算持有天数和日均成本。",
                )
                if (retiring) {
                    OutlinedTextField(
                        value = state.retiredDateInput,
                        onValueChange = onDateChange,
                        label = { Text("退役日期") },
                        placeholder = { Text("YYYY-MM-DD") },
                        supportingText = { Text("购买日期：${state.asset?.purchaseDate}") },
                        singleLine = true,
                        enabled = !state.isSaving,
                        isError = state.statusError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.statusError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) "保存中…" else "确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("取消") }
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
        title = { Text(if (archiving) "归档资产" else "恢复资产") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (archiving) "归档后不再显示在日常列表和总览中。原有数据会保留，可随时从「已归档」恢复。"
                    else "将恢复到原来的服役状态，重新显示在资产列表和总览中。",
                )
                state.archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) "保存中…" else if (archiving) "确认归档" else "确认恢复")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("取消") }
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
            Text("资产", modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        if (editEnabled) {
            CircleBarButton(
                onClick = onEdit,
                contentDescription = "编辑资产",
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
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        val iconOption = assetIconOption(asset.iconKey)
        Surface(
            shape = RoundedCornerShape(25.dp),
            color = MaterialTheme.colorScheme.secondary,
        ) {
            MaterialSymbol(
                glyph = iconOption.glyph,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.padding(20.dp),
                size = 38.dp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(asset.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        StatusChip(asset.status)
        Spacer(Modifier.height(14.dp))
        Text(currency.format(asset.priceCents / 100.0), fontSize = 24.sp, lineHeight = 30.sp, fontFamily = SuirenNumberFont, letterSpacing = (-0.48).sp, fontWeight = FontWeight.Normal)
        Text("购入价格", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (asset.isArchived) {
            Spacer(Modifier.height(10.dp))
            Text("已归档 · 恢复后可编辑", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                value = "${asset.heldDays}",
                label = "持有天数",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = currency.format(asset.dailyCostCents / 100.0),
                label = "日均成本",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = warrantyLabel(asset),
                label = "保修状态",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        InfoCard(
            rows = (listOf(
                "购买日期" to asset.purchaseDate.toString(),
            ) + if (asset.status == AssetStatus.Retired) {
                listOf("退役日期" to (asset.retiredDate?.toString() ?: "未记录"))
            } else emptyList()) + asset.archivedAt?.let {
                listOf("归档日期" to it.atZone(ZoneId.systemDefault()).toLocalDate().toString())
            }.orEmpty(),
        )
        val metadata = buildList {
            asset.purchaseChannel?.takeIf(String::isNotBlank)?.let { add("购买渠道" to it) }
            asset.warrantyEndDate?.let { add("保修截止日" to it.toString()) }
            if (asset.tags.isNotEmpty()) add("标签" to asset.tags.joinToString("、"))
            asset.notes.takeIf(String::isNotBlank)?.let { add("备注" to it) }
        }
        if (metadata.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InfoCard(rows = metadata)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun warrantyLabel(asset: Asset): String {
    val warrantyEndDate = asset.warrantyEndDate
    return when {
        warrantyEndDate == null -> "未设置"
        warrantyEndDate.isBefore(LocalDate.now()) -> "已到期"
        else -> "有效"
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
            text = if (active) "服役中" else "已退役",
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
