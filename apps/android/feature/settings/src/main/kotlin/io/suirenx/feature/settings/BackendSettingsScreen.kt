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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.suirenx.core.model.BackendSettings
import io.suirenx.core.model.BackendServer
import io.suirenx.core.ui.theme.SuirenXTheme

@Composable
fun BackendSettingsScreen(
    state: BackendUiState,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configured = state.settings?.activeUrl != null
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
                    if (configured) "选择已保存的地址，或添加一个新地址。" else "填写服务的域名或 IP 地址，即可开始管理资产。下次打开将自动进入。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.settings == null) {
                item {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry, enabled = !state.busy) { Text("重新读取配置") }
                }
            } else {
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
                if (state.settings.servers.isNotEmpty()) {
                    item { Text("已保存的地址", style = MaterialTheme.typography.titleMedium) }
                    items(state.settings.servers, key = { it.url }) { server ->
                        val selected = state.settings.activeUrl == server.url
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

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    SuirenXTheme {
        BackendSettingsScreen(BackendUiState(settings = BackendSettings()), {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    SuirenXTheme {
        BackendSettingsScreen(
            BackendUiState(settings = BackendSettings(listOf(BackendServer("https://api.example.com/", "我的服务")), "https://api.example.com/")),
            {}, {}, {}, {}, {},
        )
    }
}
