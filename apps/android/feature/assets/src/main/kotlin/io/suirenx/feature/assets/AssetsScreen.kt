package io.suirenx.feature.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.domain.SyncConflictResolution
import io.suirenx.core.ui.theme.SuirenXTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun AssetsRoute(
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetsScreen(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onRefresh = viewModel::refresh,
        onAssetClick = onAssetClick,
        onRetrySync = viewModel::retrySync,
        onResolveConflict = viewModel::resolveConflict,
        modifier = modifier,
    )
}

@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onFilterSelected: (AssetFilter) -> Unit,
    onRefresh: () -> Unit,
    onAssetClick: (String) -> Unit,
    onRetrySync: () -> Unit = {},
    onResolveConflict: (String, SyncConflictResolution) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // One scrolling container: the overview card scrolls away while the filter
    // chips stay pinned, mirroring the 有数-style collapsing overview.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Header(onRefresh = onRefresh, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (state.syncStatus.pendingOperations > 0 || state.syncStatus.conflicts.isNotEmpty()) {
            item {
                SyncStatusCard(
                    state = state,
                    onRetry = onRetrySync,
                    onResolveConflict = onResolveConflict,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (state.assets.isNotEmpty()) {
            item {
                AssetOverviewCard(
                    overview = state.overview,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        stickyHeader {
            FilterChips(selected = state.selectedFilter, onSelected = onFilterSelected)
        }
        when {
            state.isLoading -> item {
                LoadingState(Modifier.fillParentMaxSize())
            }
            state.errorMessage != null -> item {
                ErrorState(state.errorMessage, onRefresh, Modifier.fillParentMaxSize())
            }
            state.assets.isEmpty() -> item {
                EmptyState(Modifier.fillParentMaxSize())
            }
            state.visibleAssets.isEmpty() -> item {
                FilteredEmptyState(Modifier.fillParentMaxSize())
            }
            else -> items(state.visibleAssets.chunked(2), key = { row -> row.first().id }) { rowAssets ->
                AssetRow(rowAssets, onAssetClick)
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    state: AssetsUiState,
    onRetry: () -> Unit,
    onResolveConflict: (String, SyncConflictResolution) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.syncStatus.pendingOperations > 0) {
                Text("有 ${state.syncStatus.pendingOperations} 项变更等待同步", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("立即重试") }
            }
            state.syncStatus.lastSyncedAt?.let { Text("最近成功同步：$it", color = MaterialTheme.colorScheme.onTertiaryContainer) }
            state.syncStatus.conflicts.forEach { conflict ->
                Text("资产“${conflict.local.name}”发生同步冲突", fontWeight = FontWeight.Bold)
                Text(
                    "本机：${conflict.local.name} · 服务器：${conflict.remote?.name ?: "已删除"}",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onResolveConflict(conflict.assetId, SyncConflictResolution.KeepLocal) }, modifier = Modifier.weight(1f)) {
                        Text("保留本机")
                    }
                    OutlinedButton(onClick = { onResolveConflict(conflict.assetId, SyncConflictResolution.KeepRemote) }, modifier = Modifier.weight(1f)) {
                        Text("保留服务器")
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("燧人", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {}) {
            Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FilterChips(
    selected: AssetFilter,
    onSelected: (AssetFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opaque background so cards scrolling underneath stay hidden behind the pinned chips.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(AssetFilter.entries) { filter ->
                val isSelected = filter == selected
                Surface(
                    onClick = { onSelected(filter) },
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = filter.label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetOverviewCard(overview: AssetOverview, modifier: Modifier = Modifier) {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("资产总览", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "服役中 ${overview.activeCount}/${overview.totalCount}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("总资产", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currency.format(overview.totalPriceCents / 100.0),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("日均成本", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${currency.format(overview.totalDailyCostCents / 100.0)}/天",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            DashedDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.size(6.dp))
                Text("服役中 ${overview.activeCount}", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                LegendDot(MaterialTheme.colorScheme.outline)
                Spacer(Modifier.size(6.dp))
                Text("已退役 ${overview.retiredCount}", fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            OverviewBar(overview)
        }
    }
}

@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier
        .fillMaxWidth()
        .height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
        )
    }
}

@Composable
private fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun OverviewBar(overview: AssetOverview, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (overview.activeCount > 0) {
            Box(
                Modifier
                    .weight(overview.activeCount.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
        if (overview.retiredCount > 0) {
            Box(
                Modifier
                    .weight(overview.retiredCount.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
    }
}

// Full-screen state items sit below the header/overview in the LazyColumn; reserving
// bottom space keeps their centered content clear of the floating capsule tab bar.
@Composable
private fun StateContentBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    StateContentBox(modifier) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StateContentBox(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    StateContentBox(modifier) {
        Text("还没有资产，点击 + 添加第一件")
    }
}

@Composable
private fun FilteredEmptyState(modifier: Modifier = Modifier) {
    StateContentBox(modifier) {
        Text("该状态下暂无资产", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AssetRow(
    assets: List<Asset>,
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        assets.forEach { asset ->
            AssetCard(
                asset = asset,
                onClick = { onAssetClick(asset.id) },
                modifier = Modifier.weight(1f),
            )
        }
        // Keep the lone card of the last row at the same width as the two-column cards.
        if (assets.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AssetCard(asset: Asset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    Card(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetCategoryIcon(
                    option = assetIconOption(asset.iconKey),
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = if (!asset.isArchived && asset.status == AssetStatus.Active) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = when {
                        asset.isArchived -> "已归档"
                        asset.status == AssetStatus.Active -> "服役中"
                        else -> "已退役"
                    },
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(asset.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${currency.format(asset.priceCents / 100.0)}  |  ${asset.heldDays} 天",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${currency.format(asset.dailyCostCents / 100.0)}/天",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 860)
@Composable
private fun AssetsScreenPreview() {
    SuirenXTheme {
        AssetsScreen(
            state = AssetsUiState(
                isLoading = false,
                assets = listOf(
                    Asset("1", "MacBook Pro", 1_699_900, LocalDate.now(), AssetStatus.Active, "", 133, 12_781),
                    Asset("2", "荣耀 Magic8 Pro", 659_900, LocalDate.now(), AssetStatus.Active, "", 176, 3_749),
                    Asset("3", "Kindle", 49_900, LocalDate.now(), AssetStatus.Retired, "", 420, 119),
                ),
            ),
            onFilterSelected = {},
            onRefresh = {},
            onAssetClick = {},
        )
    }
}
