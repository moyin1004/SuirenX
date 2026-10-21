package io.suirenx.feature.assets

import io.suirenx.core.ui.localization.moneyFormat
import io.suirenx.core.ui.localization.moneyText
import io.suirenx.core.ui.localization.dateText
import androidx.compose.ui.platform.LocalDensity

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import io.suirenx.core.ui.component.SuirenHeader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.suirenx.core.ui.theme.SuirenNumberFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.icon.MaterialSymbol
import io.suirenx.core.ui.icon.SuirenIcons
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
        onSearch = { searching = !searching },
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
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
    val overviewIndex = 1
    val compact by remember(listState, overviewIndex) { derivedStateOf { listState.firstVisibleItemIndex > overviewIndex } }
    val visibleAssets = state.visibleAssets.filter { it.name.contains(query, ignoreCase = true) || it.tags.any { tag -> tag.contains(query, ignoreCase = true) } }
    val overview = visibleAssets.assetOverview()
    val longestHeldDays = visibleAssets.maxOfOrNull { it.heldDays } ?: 0
    val totalAssetCount = state.assets.size
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(onRefresh = onRefresh, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (state.assets.isNotEmpty()) {
            item {
                AssetOverviewCard(overview, Modifier.padding(horizontal = 16.dp), longestHeldDays, totalAssetCount)
            }
        }
        stickyHeader {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                if (compact && state.assets.isNotEmpty()) {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(moneyText(overview.totalPriceCents), fontSize = 18.sp, fontFamily = SuirenNumberFont)
                            Text(uiText(CopyR.string.copy_filtered_cost_records_count_longest_held_days_count, overview.totalCount, longestHeldDays), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (searching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText(CopyR.string.copy_back_to_assets))
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(uiText(CopyR.string.copy_search_name_channel_or_tags)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = uiText(CopyR.string.copy_clear_search))
                                    }
                                }
                            },
                        )
                    }
                } else {
                    AssetListToolbar(
                        selectedFilter = state.selectedFilter,
                        onFilterSelected = onFilterSelected,
                        sort = state.sort,
                        descending = state.sortDescending,
                        query = query,
                        onSearch = onSearch,
                        onSortSelected = onSortSelected,
                        onDirectionToggle = onSortDirectionToggle,
                        tags = state.availableTags,
                        selectedTags = state.selectedTags,
                        onTagToggle = onTagToggle,
                        onClearTags = onClearTags,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        when {
            state.isLoading -> item { LoadingState() }
            state.errorMessage != null -> item { ErrorState(state.errorMessage, onRefresh) }
            state.assets.isEmpty() -> item { EmptyState() }
            visibleAssets.isEmpty() -> item { FilteredEmptyState() }
            else -> items(visibleAssets.chunked(columns), key = { row -> row.first().id }) { rowAssets ->
                AssetRow(rowAssets, onAssetClick, fillSingle = columns == 1)
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    SuirenHeader(modifier, title = uiText(CopyR.string.copy_assets)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = uiText(CopyR.string.copy_refresh), modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: AssetFilter, onSelected: (AssetFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(AssetFilter.entries) { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier.height(48.dp).widthIn(min = 48.dp).selectable(
                    selected = isSelected,
                    role = Role.Tab,
                    onClick = { onSelected(filter) },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.height(34.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(uiText(filter.label), fontSize = 12.sp, maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFilterMenu(
    tags: List<String>,
    selectedTags: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val tagState = if (selectedTags.isEmpty()) uiText(CopyR.string.copy_all_tags) else uiQuantity(CopyR.plurals.copy_count_tags_selected, selectedTags.size)
    Box(modifier) {
        AssetToolbarButton(onClick = { expanded = true }, active = selectedTags.isNotEmpty(), modifier = Modifier
            .semantics { stateDescription = tagState }) {
            BadgedBox(badge = { if (selectedTags.isNotEmpty()) Badge { Text("${selectedTags.size}") } }) {
                Icon(SuirenIcons.Filter, contentDescription = uiText(CopyR.string.copy_filter_tags), modifier = Modifier.size(21.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 280.dp)) {
            DropdownMenuItem(text = { Text(uiText(CopyR.string.copy_all_tags)) }, onClick = onClear,
                leadingIcon = { Checkbox(checked = selectedTags.isEmpty(), onCheckedChange = null) })
            HorizontalDivider()
            val options = (tags + selectedTags).distinct().sorted()
            if (options.isEmpty()) DropdownMenuItem(text = { Text(uiText(CopyR.string.copy_no_tags)) }, enabled = false, onClick = {})
            options.forEach { tag ->
                DropdownMenuItem(text = { Text(tag, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    leadingIcon = { Checkbox(checked = tag in selectedTags, onCheckedChange = null) },
                    modifier = Modifier.semantics { selected = tag in selectedTags }, onClick = { onToggle(tag) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(uiText(CopyR.string.copy_done)) }, onClick = { expanded = false })
        }
    }
}

@Composable
private fun AssetToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Keep 48 dp touch targets while the 40 dp frames sit only 8 dp apart.
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Box(
            Modifier.size(40.dp).background(
                if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun AssetListToolbar(
    selectedFilter: AssetFilter,
    onFilterSelected: (AssetFilter) -> Unit,
    sort: AssetSort,
    descending: Boolean,
    query: String,
    onSearch: () -> Unit,
    onSortSelected: (AssetSort) -> Unit,
    onDirectionToggle: () -> Unit,
    tags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onClearTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val searchState = if (query.isBlank()) uiText(CopyR.string.copy_search_is_off) else uiText(CopyR.string.copy_search_count, query)
    val sortState = uiText(sort.label) + ", " + if (descending) uiText(CopyR.string.copy_descending) else uiText(CopyR.string.copy_ascending)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilterChips(selectedFilter, onFilterSelected, modifier = Modifier.weight(1f))
        TagFilterMenu(tags, selectedTags, onTagToggle, onClearTags)
        AssetToolbarButton(
            onClick = onSearch,
            active = query.isNotBlank(),
            modifier = Modifier
                .semantics { stateDescription = searchState },
        ) {
            Icon(Icons.Default.Search, contentDescription = uiText(CopyR.string.copy_search_assets), modifier = Modifier.size(20.dp))
        }
        Box {
            AssetToolbarButton(
                onClick = { expanded = true },
                modifier = Modifier.semantics {
                    stateDescription = sortState
                },
            ) {
                Icon(SuirenIcons.Sort, contentDescription = uiText(CopyR.string.copy_sort_assets), modifier = Modifier.size(21.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AssetSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(uiText(option.label)) },
                        leadingIcon = { if (option == sort) Text("✓") },
                        modifier = Modifier.semantics { selected = option == sort },
                        onClick = { onSortSelected(option); expanded = false },
                    )
                }
                HorizontalDivider()
                listOf(false to uiText(CopyR.string.copy_ascending), true to uiText(CopyR.string.copy_descending)).forEach { (targetDescending, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { if (descending == targetDescending) Text("✓") },
                        modifier = Modifier.semantics { selected = descending == targetDescending },
                        onClick = {
                            if (descending != targetDescending) onDirectionToggle()
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetOverviewCard(overview: AssetOverview, modifier: Modifier = Modifier, longestHeldDays: Int = 0, totalAssetCount: Int = overview.totalCount) {
    val currency = moneyFormat(minimumFractionDigits = 0)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                    Text(uiText(CopyR.string.copy_asset_overview_asset_overview), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("${overview.totalCount}/$totalAssetCount", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp, lineHeight = 18.sp, fontFamily = SuirenNumberFont, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewAmount(uiText(CopyR.string.copy_total_purchase_cost_total_purchase_cost), currency.format(java.math.BigDecimal.valueOf(overview.totalPriceCents, 2)), Modifier.weight(1f))
                OverviewAmount(uiText(CopyR.string.copy_cost_per_day), currency.format(java.math.BigDecimal.valueOf(overview.totalDailyCostCents, 2)), Modifier.weight(1f), Alignment.End)
            }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 11.dp)) {
                    OverviewCount(uiText(CopyR.string.copy_active), "${overview.activeCount}", Modifier.weight(1f))
                    OverviewCount(uiText(CopyR.string.copy_retired), "${overview.retiredCount}", Modifier.weight(1f))
                    OverviewCount(uiText(CopyR.string.copy_longest_held), "$longestHeldDays", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverviewAmount(label: String, value: String, modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        Text(value, modifier = Modifier.fillMaxWidth(),
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (alignment == Alignment.End) 24.sp else 28.sp, stepSize = 0.5.sp),
            textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp,
            fontWeight = FontWeight.Medium, fontFamily = SuirenNumberFont, letterSpacing = (-0.8).sp)
    }
}

@Composable
private fun OverviewCount(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)) {
        Text(label, modifier = Modifier.weight(1f, fill = false), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
        Text(value, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = SuirenNumberFont)
    }
}

@Composable
private fun AssetRow(assets: List<Asset>, onAssetClick: (String) -> Unit, modifier: Modifier = Modifier, fillSingle: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        assets.forEach { asset -> AssetCard(asset, { onAssetClick(asset.id) }, Modifier.weight(1f)) }
        if (assets.size == 1 && !fillSingle) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AssetCard(asset: Asset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val currency = moneyFormat()
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
                    option = assetIconOption(asset.iconKey),
                    modifier = Modifier.size(44.dp),
                    glyphSize = 36.dp,
                    cornerRadius = 13.dp,
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    label = when {
                        asset.isArchived -> uiText(CopyR.string.copy_archived)
                        active -> uiText(CopyR.string.copy_active)
                        else -> uiText(CopyR.string.copy_retired)
                    },
                    active = active,
                )
            }
            Spacer(Modifier.height(13.dp))
            Text(asset.name,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2_000, repeatDelayMillis = 2_000, spacing = MarqueeSpacing(32.dp), velocity = 24.dp),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
                maxLines = 1, softWrap = false)
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = SuirenNumberFont, letterSpacing = (-0.2).sp)) {
                        append(currency.format(java.math.BigDecimal.valueOf(asset.priceCents, 2)))
                    }
                    append(uiQuantity(CopyR.plurals.copy_held_for_count_days, asset.heldDays))
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 2,
            )
            // Reuse the original 14 dp gap so a sale price never grows the card.
            val salePrice = asset.salePriceCents
            if (salePrice != null) {
                Text(uiText(CopyR.string.copy_sold_for_cny_count, moneyText(salePrice)),
                    modifier = Modifier.height(14.dp), fontSize = 11.sp, lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    softWrap = false, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 11.sp))
            } else Spacer(Modifier.height(14.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = SuirenNumberFont, letterSpacing = (-0.28).sp)) {
                        append(currency.format(java.math.BigDecimal.valueOf(asset.dailyCostCents, 2)))
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 0.sp)) {
                        append(uiText(CopyR.string.copy_day))
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
            Text(uiText(CopyR.string.copy_unable_to_load_local_data), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(uiText(message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Button(onClick = onRetry) { Text(uiText(CopyR.string.copy_retry_loading)) }
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
            Text(uiText(CopyR.string.copy_no_assets_yet), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(uiText(CopyR.string.copy_record_the_things_you_use_and_understand_their_cost_per), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FilteredEmptyState(modifier: Modifier = Modifier) {
    Surface(modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Text(uiText(CopyR.string.copy_no_assets_match_this_filter), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                    Asset("2", uiText(CopyR.string.copy_honor_magic8_pro), 659_900, LocalDate.now(), AssetStatus.Active, "", 176, 3_749),
                    Asset("3", "Kindle", 49_900, LocalDate.now(), AssetStatus.Retired, "", 420, 119),
                ),
            ),
            onFilterSelected = {},
            onRefresh = {},
            onAssetClick = {},
        )
    }
}
