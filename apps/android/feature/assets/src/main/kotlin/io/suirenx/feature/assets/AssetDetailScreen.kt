package io.suirenx.feature.assets

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Devices
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
import io.suirenx.core.model.AssetStatus
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
) {
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
                        OutlinedButton(
                            onClick = onChangeStatus,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        ) {
                            Text(if (state.asset.status == AssetStatus.Active) "标记为已退役" else "恢复服役")
                        }
                    }
                    OutlinedButton(
                        onClick = onChangeArchive,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    ) {
                        Text(if (state.asset.isArchived) "恢复到资产列表" else "归档资产")
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
        CircleBarButton(
            onClick = onBack,
            contentDescription = "关闭",
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
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
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(44.dp),
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
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Icon(
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(28.dp)
                    .size(64.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(asset.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        StatusChip(asset.status)
        if (asset.isArchived) {
            Spacer(Modifier.height(10.dp))
            Text("已归档 · 恢复后可编辑", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(28.dp))
        InfoCard(
            rows = (listOf(
                "购入价格" to currency.format(asset.priceCents / 100.0),
                "购买日期" to asset.purchaseDate.toString(),
                "持有天数" to "${asset.heldDays} 天",
                "日均成本" to "${currency.format(asset.dailyCostCents / 100.0)}/天",
            ) + if (asset.status == AssetStatus.Retired) {
                listOf("退役日期" to (asset.retiredDate?.toString() ?: "未记录"))
            } else emptyList()) + asset.archivedAt?.let {
                listOf("归档日期" to it.atZone(ZoneId.systemDefault()).toLocalDate().toString())
            }.orEmpty(),
        )
        Spacer(Modifier.height(24.dp))
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
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
