package io.suirenx.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.suirenx.core.model.BackendSettings
import io.suirenx.core.model.BackendServer
import io.suirenx.core.model.ThemeMode
import io.suirenx.core.domain.MigrationPreview
import io.suirenx.core.ui.theme.SuirenXTheme

@Composable
fun BackendSettingsScreen(
    state: BackendUiState,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onUseLocal: () -> Unit,
    onUseRemote: () -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onSoonDaysChanged: (Int) -> Unit = {},
    onAuthUsernameChanged: (String) -> Unit = {},
    onAuthPasswordChanged: (String) -> Unit = {},
    onRegister: () -> Unit = {},
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onPreviewMigration: () -> Unit = {},
    onConfirmMigration: () -> Unit = {},
    onCancelMigration: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configured = state.mode != null
    val savedServers = state.settings?.servers.orEmpty()
    state.migrationPreview?.let { preview ->
        MigrationDialog(
            preview = preview,
            busy = state.migrationBusy,
            onConfirm = onConfirmMigration,
            onCancel = onCancelMigration,
        )
    }
    state.backupSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { if (!state.backupBusy) onCancelRestore() },
            title = { Text("确认恢复本地备份") },
            text = { Text("将覆盖本机当前资产，共 ${summary.assetCount} 件。恢复前会自动备份当前本地数据；远程地址和当前模式不会改变。") },
            confirmButton = { TextButton(onClick = onConfirmRestore, enabled = !state.backupBusy) { Text(if (state.backupBusy) "恢复中…" else "确认恢复") } },
            dismissButton = { TextButton(onClick = onCancelRestore, enabled = !state.backupBusy) { Text("取消") } },
        )
    }
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(if (configured) "后端设置" else "连接你的后端", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.mode == io.suirenx.core.model.StorageMode.Local) "当前使用本机数据。资产保存在设备上，不需要网络。"
                    else "选择已保存的地址，或添加一个新地址。也可以先在本机使用，之后再手动切换。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.mode == null) {
                item {
                    Text("选择使用方式", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onUseLocal, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("本地使用")
                    }
                    Text("本地模式无需地址、账号或网络；数据只保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onUseRemote, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("连接服务器")
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = onRetry, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("重新读取配置")
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("资产数据来源", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.mode == io.suirenx.core.model.StorageMode.Local) "本地使用 · 保存在本机"
                                else "连接服务器 · ${state.settings?.activeUrl.orEmpty()}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.mode == io.suirenx.core.model.StorageMode.Local) {
                                OutlinedButton(onClick = onUseRemote, enabled = !state.busy) {
                                    Text("添加服务器地址")
                                }
                            } else {
                                OutlinedButton(onClick = onUseLocal, enabled = !state.busy) {
                                    Text("切换本地使用")
                                }
                            }
                        }
                    }
                }
                if (state.mode == io.suirenx.core.model.StorageMode.Local) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("本地数据备份", style = MaterialTheme.typography.titleMedium)
                                Text("备份包含资产、状态、归档、资料字段和用品；文件不含服务器凭据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onExportBackup, enabled = !state.backupBusy, modifier = Modifier.weight(1f)) { Text("导出备份") }
                                    OutlinedButton(onClick = onImportBackup, enabled = !state.backupBusy, modifier = Modifier.weight(1f)) { Text("恢复备份") }
                                }
                                state.backupError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                            }
                        }
                    }
                }
                item {
                    if (state.mode == io.suirenx.core.model.StorageMode.Remote) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("服务器账号", style = MaterialTheme.typography.titleMedium)
                                if (state.auth.authenticated) {
                                    Text("已登录：${state.auth.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = onLogout, enabled = !state.authBusy, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (state.authBusy) "退出中…" else "退出登录")
                                    }
                                } else {
                                    Text("登录后资产按账号隔离，可在其他设备继续同步。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(value = state.authUsername, onValueChange = onAuthUsernameChanged, label = { Text("账号") }, modifier = Modifier.fillMaxWidth(), enabled = !state.authBusy, singleLine = true)
                                    OutlinedTextField(value = state.authPassword, onValueChange = onAuthPasswordChanged, label = { Text("密码（至少 8 位）") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.authBusy, singleLine = true)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = onLogin, enabled = !state.authBusy, modifier = Modifier.weight(1f)) { Text(if (state.authBusy) "处理中…" else "登录") }
                                        OutlinedButton(onClick = onRegister, enabled = !state.authBusy, modifier = Modifier.weight(1f)) { Text("注册") }
                                    }
                                }
                                state.authError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                Text("本地数据迁入", style = MaterialTheme.typography.titleSmall)
                                Text("仅在你确认后上传本机资产和用品；切换模式不会自动迁移。迁移前会保留本地安全备份。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = onPreviewMigration, enabled = state.auth.authenticated && !state.migrationBusy, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (state.migrationBusy) "处理中…" else "预览并迁入本地资产")
                                }
                                state.migrationError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                                state.migrationResult?.let { result ->
                                    Text(
                                        "已迁入 ${result.importedAssetCount} 件，跳过重复 ${result.skippedDuplicateCount} 件" +
                                            if (result.queuedAssetCount > 0) "；${result.queuedAssetCount} 件等待联网同步" else "。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    )
                                    if (result.failedAssetNames.isNotEmpty()) {
                                        Text("失败：${result.failedAssetNames.joinToString("、")}", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("外观", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(selected = themeMode == mode, onClick = { onThemeModeChanged(mode) }, label = { Text(when (mode) { ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色" }) })
                        }
                    }
                }
                item { SoonDaysSetting(state.soonDays, onSoonDaysChanged) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.address,
                            onValueChange = onAddressChanged,
                            label = { Text("后端地址") },
                            placeholder = { Text("192.168.1.10:8888 或 api.example.com") },
                            supportingText = { Text("可填写完整 http:// 或 https:// 地址，支持端口及路径前缀。") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            enabled = !state.busy,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChanged,
                            label = { Text("名称（选填）") },
                            placeholder = { Text("例如：家里的服务") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                            singleLine = true,
                        )
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.busy) "保存中…" else if (configured) "添加并使用" else "保存并进入")
                        }
                    }
                }
                if (savedServers.isNotEmpty()) {
                    item { Text("已保存的地址", style = MaterialTheme.typography.titleMedium) }
                    items(savedServers, key = { it.url }) { server ->
                        val selected = state.settings?.activeUrl == server.url
                        Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { onSelect(server.url) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                                    Text(server.url, style = MaterialTheme.typography.bodyMedium)
                                    if (selected) Text("当前使用", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            // Bottom space clears the floating capsule tab bar in the settings tab.
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun MigrationDialog(
    preview: MigrationPreview,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("预览本地数据迁入") },
        text = {
            Text(
                "本机共有 ${preview.localAssetCount} 件资产，其中 ${preview.newAssetCount} 件将上传，" +
                    "${preview.duplicateAssetCount} 件按稳定 ID 或“名称 + 金额 + 购买日期”判定为重复并跳过；" +
                    "用品 ${preview.localExpiryCount} 件，其中 ${preview.newExpiryCount} 件将上传、${preview.duplicateExpiryCount} 件跳过。" +
                    "上传前会保存本地安全备份；提醒数据不会混入资产总额。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "迁移中…" else "确认迁入") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun SoonDaysSetting(value: Int, onValueChanged: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("保质期临期提醒", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                if (next.length <= 2 && next.all(Char::isDigit)) {
                    text = next
                    next.toIntOrNull()?.takeIf { it in 1..30 }?.let(onValueChanged)
                }
            },
            label = { Text("提前天数（1–30）") },
            supportingText = { Text("默认提前 7 天显示为临期") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    SuirenXTheme {
        BackendSettingsScreen(
            state = BackendUiState(settings = BackendSettings()),
            onAddressChanged = {}, onNameChanged = {}, onSave = {}, onSelect = {}, onRetry = {},
            onUseLocal = {}, onUseRemote = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    SuirenXTheme {
        BackendSettingsScreen(
            state = BackendUiState(settings = BackendSettings(listOf(BackendServer("https://api.example.com/", "我的服务")), "https://api.example.com/"), mode = io.suirenx.core.model.StorageMode.Remote),
            onAddressChanged = {}, onNameChanged = {}, onSave = {}, onSelect = {}, onRetry = {},
            onUseLocal = {}, onUseRemote = {},
        )
    }
}
