package io.suirenx.feature.tools

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

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
        SuirenHeader(title = uiText(CopyR.string.copy_tools))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(uiText(CopyR.string.copy_your_everyday_tools_together_your_everyday_tools_together), fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
            Text(uiText(CopyR.string.copy_start_with_one_tool_and_grow_your_personal_workflow), fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = onOpenExpiry,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ToolEntry(uiText(CopyR.string.copy_expiry_items), uiText(CopyR.string.copy_manage_essentials_and_upcoming_expiry_dates), pendingCount?.let { uiQuantity(CopyR.plurals.copy_count_need_attention, it) } ?: uiText(CopyR.string.copy_view_reminders), SuirenIcons.AssetBox, true)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant, 0.24f),
            ) {
                ToolEntry(uiText(CopyR.string.copy_more_tools), uiText(CopyR.string.copy_more_everyday_tools_will_appear_here), uiText(CopyR.string.copy_coming_later), Icons.Default.Refresh, false)
            }
        }
        Text(uiText(CopyR.string.copy_tools_have_their_own_entry_expiry_summaries_also_appear_on), fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
