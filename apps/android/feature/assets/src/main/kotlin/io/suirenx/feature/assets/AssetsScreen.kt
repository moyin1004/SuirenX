package io.suirenx.feature.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun AssetsRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssetsScreen(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onRefresh = viewModel::refresh,
        onAddAsset = viewModel::openCreateForm,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
    state.form?.let { form ->
        CreateAssetDialog(
            state = form,
            onNameChanged = viewModel::onNameChanged,
            onPriceChanged = viewModel::onPriceChanged,
            onPurchaseDateChanged = viewModel::onPurchaseDateChanged,
            onSave = viewModel::saveAsset,
            onDismiss = viewModel::dismissCreateForm,
        )
    }
}

@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onFilterSelected: (AssetFilter) -> Unit,
    onRefresh: () -> Unit,
    onAddAsset: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AssetBottomBar(onOpenSettings) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAsset,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新增资产",
                    modifier = Modifier.size(34.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Header(onRefresh = onRefresh)
            Spacer(Modifier.height(20.dp))
            FilterRow(state.selectedFilter, onFilterSelected)
            Spacer(Modifier.height(20.dp))

            when {
                state.isLoading -> LoadingState()
                state.errorMessage != null -> ErrorState(state.errorMessage, onRefresh)
                state.assets.isEmpty() -> EmptyState()
                else -> AssetGrid(state.assets)
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
        Text("燧人", fontSize = 36.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {}) {
            Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun FilterRow(
    selected: AssetFilter,
    onSelected: (AssetFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
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
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("还没有资产，点击 + 添加第一件")
    }
}

@Composable
private fun AssetGrid(assets: List<Asset>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        items(assets, key = Asset::id) { asset ->
            AssetCard(asset)
        }
    }
}

@Composable
private fun AssetCard(asset: Asset, modifier: Modifier = Modifier) {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    Card(
        modifier = modifier.height(224.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = if (asset.status == AssetStatus.Active) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = if (asset.status == AssetStatus.Active) "服役中" else "已退役",
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(asset.name, fontSize = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${currency.format(asset.priceCents / 100.0)}  |  ${asset.heldDays} 天",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${currency.format(asset.dailyCostCents / 100.0)}/天",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AssetBottomBar(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(true, {}, { Icon(Icons.Default.Home, null) }, label = { Text("资产") })
        NavigationBarItem(false, {}, { Icon(Icons.Outlined.FavoriteBorder, null) }, label = { Text("心愿") })
        NavigationBarItem(false, {}, { Icon(Icons.Outlined.AutoGraph, null) }, label = { Text("趋势") })
        NavigationBarItem(false, onOpenSettings, { Icon(Icons.Outlined.Settings, null) }, label = { Text("设置") })
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
                ),
            ),
            onFilterSelected = {},
            onRefresh = {},
            onAddAsset = {},
            onOpenSettings = {},
        )
    }
}

