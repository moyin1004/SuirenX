package io.suirenx.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.suirenx.core.model.BackendServer

/** Shared grouped form for the add sheet and saved-server detail. */
@Composable
internal fun ServerEditorFields(
    state: BackendUiState,
    server: BackendServer?,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onSave: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val busy = state.busy || state.authBusy || state.migrationBusy || state.testingConnection
    val tested = state.connectionTestAddress == state.address.trim() && !state.connectionTestFailed
    val renamed = server != null && state.address.trim() == server.url && state.name != server.name
    GroupTitle("地址信息")
    SettingsCard(compact = true) {
        val colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
        TextField(state.name, onNameChanged, Modifier.fillMaxWidth().testTag("server.name").semantics { contentDescription = "名称（可选）" },
            placeholder = { Text("名称（可选）") }, singleLine = true, enabled = !busy, colors = colors)
        HorizontalDivider()
        TextField(state.address, onAddressChanged, Modifier.fillMaxWidth().testTag("server.address").semantics { contentDescription = "域名或 IP 地址" },
            placeholder = { Text("域名或 IP 地址") }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = colors)
    }
    SettingsCard(compact = true) {
        TextButton(onClick = { onTestConnection(state.address) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text(if (state.testingConnection) "测试中…" else "测试连接") }
        ConnectionTestResult(state)
        Caption("测试成功仅表示此健康接口可达，不代表账号或同步已验证。")
    }
    SettingsCard(compact = true) {
        TextButton(onClick = onSave, enabled = !busy && state.address.isNotBlank() && (tested || renamed),
            modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "保存中…" else "保存") }
        state.error?.let { ErrorNote(it) }
    }
    if (server != null) {
        val selected = state.settings?.activeUrl == server.url
        SettingsCard(compact = true) {
            TextButton(onClick = { onSelect(server.url) }, enabled = !busy && !selected,
                modifier = Modifier.fillMaxWidth()) { Text(if (selected) "已选择此服务器" else "选择此服务器") }
            Caption("选择地址会暂停同步，不会清除账号或自动上传。")
        }
        SettingsCard(compact = true) {
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除地址") }
        }
    }
}
