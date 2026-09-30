package io.suirenx.feature.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.suirenx.core.ui.component.SuirenHeader
import io.suirenx.core.ui.icon.SuirenIcons
import io.suirenx.core.ui.theme.SuirenXTheme

@Composable
fun ToolsRoute(
    onOpenExpiry: () -> Unit,
    modifier: Modifier = Modifier,
    pendingCount: Int? = null,
) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SuirenHeader()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("把日常工具放在一起", fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
            Text("从一个工具开始，后续可以继续扩展你的个人工作流。", fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = onOpenExpiry,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ToolEntry("用品 / 保质期", "管理耗材、用品和临近到期提醒", pendingCount?.let { "$it 项待处理" } ?: "查看提醒", SuirenIcons.AssetBox, true)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, 0.24f),
            ) {
                ToolEntry("更多工具", "以后会在这里加入新的生活与工作工具", "即将加入", Icons.Default.Refresh, false)
            }
        }
        Text("工具是独立入口；用品 / 保质期总览也会出现在首页，方便快速查看。", fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolEntry(title: String, description: String, badge: String, icon: ImageVector, accent: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (accent) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(badge, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ToolsRoutePreview() {
    SuirenXTheme { ToolsRoute(onOpenExpiry = {}, pendingCount = 2) }
}
