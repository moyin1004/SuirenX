package io.suirenx.app

import io.suirenx.core.ui.localization.moneyFormat
import io.suirenx.core.ui.localization.moneyText
import io.suirenx.core.ui.localization.dateText

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

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
internal fun OverviewRoute(
    assetsViewModel: AssetsViewModel,
    expiryViewModel: ExpiryViewModel,
    onAssets: () -> Unit,
    onSupplies: () -> Unit,
    onSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val assets by assetsViewModel.uiState.collectAsStateWithLifecycle()
    val expiry by expiryViewModel.uiState.collectAsStateWithLifecycle()
    OverviewScreen(assets, expiry, onAssets, onSupplies, onSync,
        onRefresh = { assetsViewModel.refresh(); expiryViewModel.refresh() }, modifier = modifier)
}

@Composable
private fun OverviewScreen(
    assets: AssetsUiState,
    expiry: ExpiryUiState,
    onAssets: () -> Unit,
    onSupplies: () -> Unit,
    onSync: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminders = expiry.items.filter { it.bucket(LocalDate.now(), expiry.soonDays) in setOf(ExpiryBucket.Expired, ExpiryBucket.DueToday, ExpiryBucket.ExpiringSoon) }
    val currency = moneyFormat(minimumFractionDigits = 0)
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SuirenHeader {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = uiText(CopyR.string.copy_refresh_overview), modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(uiText(CopyR.string.copy_your_assets_and_everyday_essentials), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewCard(onClick = onAssets) {
                OverviewHeading(uiText(CopyR.string.copy_asset_overview), SuirenIcons.AssetBox,
                    when { assets.isLoading -> uiText(CopyR.string.copy_loading); assets.errorMessage != null -> uiText(CopyR.string.copy_unable_to_load); else -> currency.format(java.math.BigDecimal.valueOf(assets.overview.totalPriceCents, 2)) },
                    if (assets.isLoading || assets.errorMessage != null) "" else uiText(CopyR.string.copy_total_purchase_cost))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryValue(uiText(CopyR.string.copy_active), if (assets.isLoading || assets.errorMessage != null) "—" else "${assets.overview.activeCount}", Modifier.weight(1f))
                    SummaryValue(uiText(CopyR.string.copy_active_assets_cost_per_day), if (assets.isLoading || assets.errorMessage != null) "—" else currency.format(java.math.BigDecimal.valueOf(assets.overview.totalDailyCostCents, 2)), Modifier.weight(1f))
                }
                OverviewFooter(uiText(CopyR.string.copy_view_all_assets), if (assets.isLoading || assets.errorMessage != null) "" else uiQuantity(CopyR.plurals.copy_count_unarchived, assets.overview.totalCount))
            }
            OverviewCard(onClick = onSupplies) {
                OverviewHeading(uiText(CopyR.string.copy_expiry_items), Icons.Default.Warning,
                    when { expiry.loading -> uiText(CopyR.string.copy_loading); expiry.error != null -> uiText(CopyR.string.copy_unable_to_load); else -> "${reminders.size}" },
                    if (expiry.loading || expiry.error != null) "" else uiText(CopyR.string.copy_need_attention))
                Text(uiText(CopyR.string.copy_track_expiry_dates_and_see_what_needs_attention), modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
                OverviewFooter(uiText(CopyR.string.copy_manage_expiry_items), uiText(CopyR.string.copy_1_tool))
            }
        }
        if (assets.errorMessage != null || expiry.error != null) {
            Text(uiText(CopyR.string.copy_some_data_could_not_load_use_refresh_to_try_again), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        val status = assets.syncStatus
        val conflicts = status.conflicts.size + status.expiryConflicts.size
        val notice = when {
            conflicts > 0 -> uiQuantity(CopyR.plurals.copy_count_conflicts_resolve, conflicts)
            status.needsLogin -> uiText(CopyR.string.copy_account_verification_required_view_sync)
            status.error != null -> uiText(CopyR.string.copy_sync_did_not_finish_view_sync)
            status.waitingForNetwork -> uiText(CopyR.string.copy_waiting_for_network_view_sync)
            status.pendingOperations > 0 -> uiQuantity(CopyR.plurals.copy_count_pending_changes_view_sync, status.pendingOperations)
            else -> null
        }
        if (notice != null) {
            TextButton(onClick = { onSync(conflicts > 0) }) { Text(notice) }
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
        OverviewScreen(AssetsUiState(isLoading = false), ExpiryUiState(loading = false), {}, {}, {}, {})
    }
}
