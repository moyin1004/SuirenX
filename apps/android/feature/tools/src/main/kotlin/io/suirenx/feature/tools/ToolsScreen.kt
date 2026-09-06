package io.suirenx.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolsRoute(
    onOpenExpiry: () -> Unit,
    onAddExpiry: () -> Unit,
    expiredCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("工具", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("把日常管理集中在这里", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(onClick = onOpenExpiry, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("保质期管理", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (expiredCount > 0) "有 $expiredCount 件用品已过期 · 保存在本机"
                    else "记录食品、药品和日用品的到期时间 · 保存在本机",
                    color = if (expiredCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("点击查看清单", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("新增按钮会按当前工具页面创建用品。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
