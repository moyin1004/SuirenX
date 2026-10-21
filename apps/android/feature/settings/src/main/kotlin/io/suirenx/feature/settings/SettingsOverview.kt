package io.suirenx.feature.settings

import io.suirenx.core.ui.localization.moneyFormat
import io.suirenx.core.ui.localization.moneyText
import io.suirenx.core.ui.localization.dateText

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import io.suirenx.core.domain.AssetSyncConflict
import io.suirenx.core.domain.ExpirySyncConflict
import io.suirenx.core.model.*
import java.math.BigDecimal

@Composable
internal fun PageTitle(title: String, description: String, kicker: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        kicker?.let { Caption(it) }
        Text(title, fontSize = 29.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        Text(description, fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(if (compact) 20.dp else 24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 12.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsHomeGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun SettingsHomeLink(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
        .heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GroupTitle(title: String) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }

@Composable
internal fun Caption(text: String) { Text(text, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
internal fun ErrorNote(text: String) { Text(uiText(text), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

@Composable
internal fun SettingsLink(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 52.dp).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = if (compact) 18.sp else TextUnit.Unspecified, fontWeight = FontWeight.Medium)
            Caption(description)
        }
        Text("›", fontSize = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.3f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) { Text(label) }
}

@Composable
internal fun SecondaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text(label) }
}

@Composable
internal fun Choice(title: String, description: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontSize = 14.sp); if(description.isNotEmpty()) Caption(description) }
        RadioButton(selected, onClick, enabled = enabled)
    }
}

@Composable
internal fun ChoiceAction(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Column { SecondaryAction(title, enabled, onClick); Caption(description) }
}

@Composable
internal fun themeLabel(mode: ThemeMode) = when(mode) { ThemeMode.System -> uiText(CopyR.string.copy_follow_system); ThemeMode.Light -> uiText(CopyR.string.copy_light); ThemeMode.Dark -> uiText(CopyR.string.copy_dark) }
@Composable
internal fun conflictDescription(localDeleted: Boolean, remoteDeleted: Boolean, unavailable: Boolean) = when {
    unavailable -> uiText(CopyR.string.copy_remote_snapshot_unavailable_check_account)
    localDeleted || remoteDeleted -> uiText(CopyR.string.copy_deleted_on_one_side_edited_on_the_other)
    else -> uiText(CopyR.string.copy_both_versions_changed_compare)
}

@Composable
private fun assetFields(a: Asset) = linkedMapOf(
    uiText(CopyR.string.copy_name) to a.name, uiText(CopyR.string.copy_purchase_price) to moneyText(a.priceCents),
    uiText(CopyR.string.copy_purchase_date) to dateText(a.purchaseDate), uiText(CopyR.string.copy_status) to if(a.status == AssetStatus.Active) uiText(CopyR.string.copy_active) else uiText(CopyR.string.copy_retired),
    uiText(CopyR.string.copy_retirement_date) to (a.retiredDate?.let { dateText(it) } ?: "—"), uiText(CopyR.string.copy_archive) to if(a.isArchived) uiText(CopyR.string.copy_archived) else uiText(CopyR.string.copy_unarchived),
    uiText(CopyR.string.copy_purchase_channel) to a.purchaseChannel.orEmpty(), uiText(CopyR.string.copy_warranty_end_date) to (a.warrantyEndDate?.let { dateText(it) } ?: "—"),
    uiText(CopyR.string.copy_icon) to a.iconKey, uiText(CopyR.string.copy_tags) to a.tags.joinToString("、"), uiText(CopyR.string.copy_notes) to a.notes,
)
@Composable
private fun expiryFields(e: ExpiryItem) = linkedMapOf(
    uiText(CopyR.string.copy_name) to e.name, uiText(CopyR.string.copy_category) to e.category, uiText(CopyR.string.copy_package_expiry_date) to dateText(e.packageExpiryDate),
    uiText(CopyR.string.copy_opened_date) to (e.openedDate?.let { dateText(it) } ?: "—"), uiText(CopyR.string.copy_days_valid_after_opening) to (e.openedValidityDays?.toString() ?: "—"),
    uiText(CopyR.string.copy_location) to e.location, uiText(CopyR.string.copy_status) to when(e.status) { ExpiryItemStatus.InUse -> uiText(CopyR.string.copy_in_use); ExpiryItemStatus.UsedUp -> uiText(CopyR.string.copy_used_up); ExpiryItemStatus.Discarded -> uiText(CopyR.string.copy_discarded) },
    uiText(CopyR.string.copy_archive) to if(e.archivedAt == null) uiText(CopyR.string.copy_unarchived) else uiText(CopyR.string.copy_archived), uiText(CopyR.string.copy_notes) to e.notes,
)

@Composable
internal fun ConflictVersions(asset: AssetSyncConflict?, expiry: ExpirySyncConflict?) {
    val local = asset?.local?.let { assetFields(it) } ?: expiry?.local?.let { expiryFields(it) } ?: mapOf(uiText(CopyR.string.copy_name) to expiry?.localName.orEmpty())
    val remote = asset?.remote?.let { assetFields(it) } ?: expiry?.remote?.let { expiryFields(it) } ?: emptyMap()
    val localDeleted = asset?.localDeleted ?: expiry?.localDeleted ?: false
    val remoteDeleted = asset?.remoteDeleted ?: expiry?.remoteDeleted ?: false
    // Stack versions on narrow screens so long notes and dates remain readable.
    listOf(Triple(uiText(CopyR.string.copy_local_version), local, localDeleted), Triple(uiText(CopyR.string.copy_remote_version), remote, remoteDeleted)).forEach { (title, fields, deleted) ->
        SettingsCard {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if(deleted) Text(uiText(CopyR.string.copy_this_version_is_deleted), color = MaterialTheme.colorScheme.error)
            fields.forEach { (label, value) ->
                val different = local[label] != remote[label]
                Fact(if(different) uiText(CopyR.string.copy_count_different, label) else label, value.ifBlank { uiText(CopyR.string.copy_not_provided) })
            }
        }
    }
}

@Composable
internal fun lastSyncLabel(status: io.suirenx.core.domain.RemoteSyncStatus): String =
    status.lastSyncedAt?.atZone(java.time.ZoneId.systemDefault())
        ?.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) ?: uiText(CopyR.string.copy_never_synced)

@Composable
internal fun SyncStatistics(status: io.suirenx.core.domain.RemoteSyncStatus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(lastSyncLabel(status) to uiText(CopyR.string.copy_last_success), uiQuantity(CopyR.plurals.copy_count_items, status.pendingOperations) to uiText(CopyR.string.copy_pending),
            uiQuantity(CopyR.plurals.copy_count_items, status.conflicts.size + status.expiryConflicts.size) to uiText(CopyR.string.copy_conflicts)).forEach { (value, label) ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Caption(label)
                }
            }
        }
    }
}

@Composable
internal fun ConnectionTestResult(state: BackendUiState) {
    if (state.testingConnection) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.connectionTest?.let { if (state.connectionTestFailed) ErrorNote(it) else Caption(uiText(it)) }
}
