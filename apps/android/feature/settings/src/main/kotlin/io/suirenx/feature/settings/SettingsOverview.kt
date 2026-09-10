package io.suirenx.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.suirenx.core.domain.AssetSyncConflict
import io.suirenx.core.domain.ExpirySyncConflict
import io.suirenx.core.model.*
import java.math.BigDecimal

@Composable
internal fun PageTitle(title: String, description: String, kicker: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        kicker?.let { Caption(it) }
        Text(title, fontSize = 29.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        Text(description, fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun GroupTitle(title: String) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }

@Composable
internal fun Caption(text: String) { Text(text, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
internal fun ErrorNote(text: String) { Text(text, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

@Composable
internal fun SettingsLink(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Caption(description)
        }
        Text("›", fontSize = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.3f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) { Text(label) }
}

@Composable
internal fun SecondaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text(label) }
}

@Composable
internal fun Choice(title: String, description: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontSize = 14.sp); if(description.isNotEmpty()) Caption(description) }
        RadioButton(selected, onClick, enabled = enabled)
    }
}

@Composable
internal fun ChoiceAction(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Column { SecondaryAction(title, enabled, onClick); Caption(description) }
}

internal fun themeLabel(mode: ThemeMode) = when(mode) { ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色" }
internal fun conflictDescription(localDeleted: Boolean, remoteDeleted: Boolean, unavailable: Boolean) = when {
    unavailable -> "无法读取另一端 · 检查账号归属"
    localDeleted || remoteDeleted -> "一端删除，另一端修改"
    else -> "双方都修改了记录 · 查看差异"
}

private fun assetFields(a: Asset) = linkedMapOf(
    "名称" to a.name, "购买金额" to "¥${BigDecimal.valueOf(a.priceCents, 2).toPlainString()}",
    "购买日期" to a.purchaseDate.toString(), "状态" to if(a.status == AssetStatus.Active) "服役中" else "已退役",
    "退役日期" to (a.retiredDate?.toString() ?: "—"), "归档" to if(a.isArchived) "已归档" else "未归档",
    "购买渠道" to a.purchaseChannel.orEmpty(), "保修截止" to (a.warrantyEndDate?.toString() ?: "—"),
    "图标" to a.iconKey, "标签" to a.tags.joinToString("、"), "备注" to a.notes,
)
private fun expiryFields(e: ExpiryItem) = linkedMapOf(
    "名称" to e.name, "分类" to e.category, "包装到期" to e.packageExpiryDate.toString(),
    "开封日期" to (e.openedDate?.toString() ?: "—"), "开封有效天数" to (e.openedValidityDays?.toString() ?: "—"),
    "位置" to e.location, "状态" to when(e.status) { ExpiryItemStatus.InUse -> "使用中"; ExpiryItemStatus.UsedUp -> "已用完"; ExpiryItemStatus.Discarded -> "已丢弃" },
    "归档" to if(e.archivedAt == null) "未归档" else "已归档", "备注" to e.notes,
)

@Composable
internal fun ConflictVersions(asset: AssetSyncConflict?, expiry: ExpirySyncConflict?) {
    val local = asset?.local?.let(::assetFields) ?: expiry?.local?.let(::expiryFields) ?: mapOf("名称" to expiry?.localName.orEmpty())
    val remote = asset?.remote?.let(::assetFields) ?: expiry?.remote?.let(::expiryFields) ?: emptyMap()
    val localDeleted = asset?.localDeleted ?: expiry?.localDeleted ?: false
    val remoteDeleted = asset?.remoteDeleted ?: expiry?.remoteDeleted ?: false
    // Stack versions on narrow screens so long notes and dates remain readable.
    listOf(Triple("本机版本", local, localDeleted), Triple("另一端版本", remote, remoteDeleted)).forEach { (title, fields, deleted) ->
        SettingsCard {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if(deleted) Text("此端已删除记录", color = MaterialTheme.colorScheme.error)
            fields.forEach { (label, value) ->
                val different = local[label] != remote[label]
                Fact(if(different) "$label · 不同" else label, value.ifBlank { "未填写" })
            }
        }
    }
}

internal fun lastSyncLabel(status: io.suirenx.core.domain.RemoteSyncStatus): String =
    status.lastSyncedAt?.atZone(java.time.ZoneId.systemDefault())
        ?.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) ?: "尚未同步"

@Composable
internal fun SyncStatistics(status: io.suirenx.core.domain.RemoteSyncStatus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(lastSyncLabel(status) to "上次成功", "${status.pendingOperations} 项" to "待发送",
            "${status.conflicts.size + status.expiryConflicts.size} 项" to "待处理冲突").forEach { (value, label) ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Caption(label)
                }
            }
        }
    }
}

@Composable
internal fun ConnectionTestResult(state: BackendUiState) {
    if (state.testingConnection) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.connectionTest?.let { if (state.connectionTestFailed) ErrorNote(it) else Caption(it) }
}
