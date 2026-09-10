package io.suirenx.app

import io.suirenx.core.ui.component.SuirenHeader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import io.suirenx.core.ui.icon.SuirenIcons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.suirenx.core.ui.theme.SuirenNumberFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.ui.icon.MaterialSymbol
import io.suirenx.feature.assets.AssetsUiState
import io.suirenx.feature.expiry.ExpiryUiState
import io.suirenx.core.ui.theme.SuirenXTheme
import androidx.compose.ui.tooling.preview.Preview
import io.suirenx.feature.assets.AssetsViewModel
import io.suirenx.feature.expiry.ExpiryViewModel
import io.suirenx.core.model.ExpiryBucket
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun OverviewRoute(onAssets: () -> Unit, onSupplies: () -> Unit, modifier: Modifier = Modifier) {
    val assetsViewModel: AssetsViewModel = hiltViewModel()
    val expiryViewModel: ExpiryViewModel = hiltViewModel()
    val assets by assetsViewModel.uiState.collectAsStateWithLifecycle()
    val expiry by expiryViewModel.uiState.collectAsStateWithLifecycle()
    OverviewScreen(assets, expiry, onAssets, onSupplies,
        onRefresh = { assetsViewModel.refresh(); expiryViewModel.refresh() }, modifier = modifier)
}

@Composable
private fun OverviewScreen(
    assets: AssetsUiState,
    expiry: ExpiryUiState,
    onAssets: () -> Unit,
    onSupplies: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminders = expiry.items.filter { it.bucket(LocalDate.now(), expiry.soonDays) in setOf(ExpiryBucket.Expired, ExpiryBucket.DueToday, ExpiryBucket.ExpiringSoon) }
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA).apply { minimumFractionDigits = 0 }
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SuirenHeader {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新总览", modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("总览", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            Text("资产与日常用品，都在这里。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewCard(onClick = onAssets) {
                OverviewHeading("资产总览", SuirenIcons.AssetBox,
                    when { assets.isLoading -> "读取中…"; assets.errorMessage != null -> "暂时无法读取"; else -> currency.format(assets.overview.totalPriceCents / 100.0) },
                    if (assets.isLoading || assets.errorMessage != null) "" else "总资产")
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryValue("服役中", if (assets.isLoading || assets.errorMessage != null) "—" else "${assets.overview.activeCount}", Modifier.weight(1f))
                    SummaryValue("日均成本", if (assets.isLoading || assets.errorMessage != null) "—" else currency.format(assets.overview.totalDailyCostCents / 100.0), Modifier.weight(1f))
                }
                OverviewFooter("查看全部资产", if (assets.isLoading || assets.errorMessage != null) "" else "${assets.overview.totalCount} 项记录")
            }
            OverviewCard(onClick = onSupplies) {
                OverviewHeading("用品 / 保质期", Icons.Default.Warning,
                    when { expiry.loading -> "读取中…"; expiry.error != null -> "暂时无法读取"; else -> "${reminders.size}" },
                    if (expiry.loading || expiry.error != null) "" else "项待处理")
                Text("记录用品期限，及时查看到期提醒。", modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
                OverviewFooter("打开用品管理", "1 个工具")
            }
        }
        if (assets.errorMessage != null || expiry.error != null) {
            Text("部分数据未能加载，请点击右上角刷新重试。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        if (assets.syncStatus.pendingOperations > 0 || assets.syncStatus.conflicts.isNotEmpty()) {
            TextButton(onClick = onAssets) { Text("有数据待同步或需要确认 · 前往资产查看") }
        }
    }
}

@Composable
private fun OverviewCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun OverviewHeading(label: String, icon: ImageVector, value: String, suffix: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, modifier = Modifier.alignByBaseline(), fontSize = 28.sp, lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium, fontFamily = SuirenNumberFont, letterSpacing = (-0.84).sp)
                Text(suffix, modifier = Modifier.alignByBaseline(), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontFamily = SuirenNumberFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun OverviewFooter(label: String, detail: String) {
    Column(Modifier.padding(top = 15.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            Spacer(Modifier.weight(1f))
            Text(detail, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OverviewPreview() {
    SuirenXTheme {
        OverviewScreen(AssetsUiState(isLoading = false), ExpiryUiState(loading = false), {}, {}, {})
    }
}
