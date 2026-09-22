package io.suirenx.feature.assets

import io.suirenx.core.ui.component.SuirenHeader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.suirenx.core.ui.theme.SuirenNumberFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.icon.MaterialSymbol
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
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    AssetsScreen(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onRefresh = viewModel::refresh,
        onAssetClick = onAssetClick,
        modifier = modifier,
        query = query,
        searching = searching,
        onQueryChange = { query = it },
        onSearch = { searching = !searching; query = "" },
        onSortSelected = viewModel::onSortSelected,
        onSortDirectionToggle = viewModel::toggleSortDirection,
        onTagToggle = viewModel::toggleTag,
        onClearTags = viewModel::clearTags,
    )
}

@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onFilterSelected: (AssetFilter) -> Unit,
    onRefresh: () -> Unit,
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    searching: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSortSelected: (AssetSort) -> Unit = {},
    onSortDirectionToggle: () -> Unit = {},
    onTagToggle: (String) -> Unit = {},
    onClearTags: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val overviewIndex = 1 + (if (searching) 1 else 0)
    val compact by remember(listState, overviewIndex) { derivedStateOf { listState.firstVisibleItemIndex > overviewIndex } }
    val visibleAssets = state.visibleAssets.filter { it.name.contains(query, ignoreCase = true) || it.tags.any { tag -> tag.contains(query, ignoreCase = true) } }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(onRefresh = onRefresh, onSearch = onSearch, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (searching) {
            item { OutlinedTextField(value = query, onValueChange = onQueryChange, placeholder = { Text("搜索资产名称或标签") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(14.dp)) }
        }
        if (state.assets.isNotEmpty()) {
            item {
                AssetOverviewCard(state.overview, Modifier.padding(horizontal = 16.dp), state.assets.filterNot { it.isArchived }.maxOfOrNull { it.heldDays } ?: 0)
            }
        }
        stickyHeader {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                if (compact && state.assets.isNotEmpty()) {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(NumberFormat.getCurrencyInstance(Locale.CHINA).format(state.overview.totalPriceCents / 100.0), fontSize = 18.sp, fontFamily = SuirenNumberFont)
                            Text("购入总额 · ${state.overview.activeCount} 件服役中", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                FilterChips(state.selectedFilter, onFilterSelected)
                if (state.availableTags.isNotEmpty()) {
                    TagFilters(state.availableTags, state.selectedTags, onTagToggle, onClearTags)
                }
            }
        }
        if (searching || state.visibleAssets.isNotEmpty()) {
            item {
                SortControls(
                    sort = state.sort,
                    descending = state.sortDescending,
                    onSortSelected = onSortSelected,
                    onDirectionToggle = onSortDirectionToggle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("我的资产", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${visibleAssets.size} 项记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        when {
            state.isLoading -> item { LoadingState() }
            state.errorMessage != null -> item { ErrorState(state.errorMessage, onRefresh) }
            state.assets.isEmpty() -> item { EmptyState() }
            visibleAssets.isEmpty() -> item { FilteredEmptyState() }
            else -> items(visibleAssets.chunked(2), key = { row -> row.first().id }) { rowAssets ->
                AssetRow(rowAssets, onAssetClick)
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    SuirenHeader(modifier) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: AssetFilter, onSelected: (AssetFilter) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AssetFilter.entries) { filter ->
                val isSelected = filter == selected
                Surface(
                    onClick = { onSelected(filter) },
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        filter.label,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagFilters(
    tags: List<String>,
    selectedTags: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedTags.isNotEmpty()) {
            item {
                Surface(onClick = onClear, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("清除标签", modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp)
                }
            }
        }
        items(tags) { tag ->
            val selected = tag in selectedTags
            Surface(
                onClick = { onToggle(tag) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
            ) { Text(tag, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun SortControls(
    sort: AssetSort,
    descending: Boolean,
    onSortSelected: (AssetSort) -> Unit,
    onDirectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("排序", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                Text(sort.label, fontSize = 11.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AssetSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSortSelected(option); expanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDirectionToggle) { Text(if (descending) "降序" else "升序", fontSize = 11.sp) }
    }
}

@Composable
private fun AssetOverviewCard(overview: AssetOverview, modifier: Modifier = Modifier, longestHeldDays: Int = 0) {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA).apply { minimumFractionDigits = 0 }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("未归档资产购入总额", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                }
            }
            Row(Modifier.padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(currency.format(overview.totalPriceCents / 100.0), modifier = Modifier.alignByBaseline(), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, fontFamily = SuirenNumberFont, letterSpacing = (-0.84).sp)
                Text("元", modifier = Modifier.alignByBaseline(), fontSize = 13.sp, lineHeight = 18.sp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric("服役中日均成本", currency.format(overview.totalDailyCostCents / 100.0), Modifier.weight(1f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OverviewMetric("服役中", "${overview.activeCount}", Modifier.weight(1f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OverviewMetric("最长持有天", "$longestHeldDays", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SuirenNumberFont)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun AssetRow(assets: List<Asset>, onAssetClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        assets.forEach { asset -> AssetCard(asset, { onAssetClick(asset.id) }, Modifier.weight(1f)) }
        if (assets.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AssetCard(asset: Asset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    val active = !asset.isArchived && asset.status == AssetStatus.Active
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 177.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AssetCategoryIcon(
                    option = assetIconOption(asset.iconKey).copy(
                        containerColor = if (active && asset.iconKey == "laptop") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (active && asset.iconKey == "laptop") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.size(44.dp),
                    glyphSize = 25.dp,
                    cornerRadius = 13.dp,
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    label = when {
                        asset.isArchived -> "已归档"
                        active -> "服役中"
                        else -> "已退役"
                    },
                    active = active,
                )
            }
            Spacer(Modifier.height(13.dp))
            Text(asset.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = SuirenNumberFont, letterSpacing = (-0.2).sp)) {
                        append(currency.format(asset.priceCents / 100.0))
                    }
                    append(" · 持有 ${asset.heldDays} 天")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = SuirenNumberFont, letterSpacing = (-0.28).sp)) {
                        append(currency.format(asset.dailyCostCents / 100.0))
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 0.sp)) {
                        append("/天")
                    }
                },
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("无法读取本机数据", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Button(onClick = onRetry) { Text("重试读取") }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Surface(modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                MaterialSymbol(glyph = "\uE326", contentDescription = null, modifier = Modifier.padding(18.dp), size = 38.dp)
            }
            Text("还没有资产", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("把每天正在使用的东西记下来，燧人会帮你算清持有成本。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FilteredEmptyState(modifier: Modifier = Modifier) {
    Surface(modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Text("这个筛选下暂时没有资产", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
