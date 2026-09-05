package io.suirenx.feature.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun AssetDetailRoute(
    assetId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(assetId) { viewModel.load(assetId) }
    AssetDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onEdit = viewModel::openEdit,
        modifier = modifier,
    )
    state.form?.let { form ->
        AssetFormDialog(
            title = stringResource(R.string.edit_asset_title),
            state = form,
            onNameChanged = viewModel::onEditNameChanged,
            onPriceChanged = viewModel::onEditPriceChanged,
            onPurchaseDateChanged = viewModel::onEditPurchaseDateChanged,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::dismissEdit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    state: AssetDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("资产详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.asset != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑资产")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.errorMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
                state.asset != null -> AssetDetailContent(state.asset)
            }
        }
    }
}

@Composable
private fun AssetDetailContent(asset: Asset, modifier: Modifier = Modifier) {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(48.dp),
                )
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(asset.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                StatusChip(asset.status)
            }
        }
        Spacer(Modifier.height(28.dp))
        InfoCard(
            rows = listOf(
                "购入价格" to currency.format(asset.priceCents / 100.0),
                "购买日期" to asset.purchaseDate.toString(),
                "持有天数" to "${asset.heldDays} 天",
                "日均成本" to "${currency.format(asset.dailyCostCents / 100.0)}/天",
            ),
        )
    }
}

@Composable
private fun StatusChip(status: AssetStatus, modifier: Modifier = Modifier) {
    val active = status == AssetStatus.Active
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = if (active) "服役中" else "已退役",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 860)
@Composable
private fun AssetDetailScreenPreview() {
    SuirenXTheme {
        AssetDetailScreen(
            state = AssetDetailUiState(
                isLoading = false,
                asset = Asset(
                    "1", "MacBook Pro", 1_699_900, LocalDate.now(),
                    AssetStatus.Active, "", 133, 12_781,
                ),
            ),
            onBack = {},
            onRetry = {},
            onEdit = {},
        )
    }
}
