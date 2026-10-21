package io.suirenx.feature.settings

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import io.suirenx.core.ui.localization.LanguagePicker
import io.suirenx.core.ui.localization.languagePreferenceLabel
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.suirenx.core.domain.*
import io.suirenx.core.model.*
import io.suirenx.core.ui.icon.SuirenIcons
import io.suirenx.core.ui.component.SuirenHeader
import io.suirenx.core.ui.theme.SuirenXTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Native implementation of the approved OpenDesign settings flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(
    state: BackendUiState,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onUseLocal: () -> Unit,
    onUseRemote: () -> Unit,
    modifier: Modifier = Modifier,
    entry: SettingsEntry = SettingsEntry.Home,
    onExit: (() -> Unit)? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onSoonDaysChanged: (Int) -> Unit = {},
    onAuthUsernameChanged: (String) -> Unit = {},
    onAuthPasswordChanged: (String) -> Unit = {},
    onAuthConfirmationChanged: (String) -> Unit = {},
    onRegister: (String) -> Unit = {},
    onResetAuthForm: () -> Unit = {},
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onPreviewMigration: () -> Unit = {},
    onConfirmMigration: () -> Unit = {},
    onCancelMigration: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSyncSchedule: (SyncSchedule) -> Unit = {},
    onCancelSync: () -> Unit = {},
    onDeleteServer: (String) -> Unit = {},
    onOpenServer: (String) -> Unit = {},
    onEditServer: (String?) -> Unit = {},
    onTestConnection: (String) -> Unit = {},
    onResolveConflict: (String, Boolean, SyncConflictResolution) -> Unit = { _, _, _ -> },
) {
    var page by rememberSaveable { mutableStateOf(entry.page) }
    var conflictId by rememberSaveable { mutableStateOf("") }
    var expiryConflict by rememberSaveable { mutableStateOf(false) }
    var resolution by remember { mutableStateOf<SyncConflictResolution?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var addingServer by rememberSaveable { mutableStateOf(false) }
    var deletingServer by remember { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    val confirmPassword = state.authConfirmation
    var passwordVisible by remember { mutableStateOf(false) }
    var authReturnPage by rememberSaveable { mutableStateOf("account") }
    var seenAuthSuccess by rememberSaveable { mutableStateOf(state.authSuccessVersion) }
    val connectionBusy = state.busy || state.authBusy || state.migrationBusy
    val inspectedServer = state.settings?.servers?.find { it.url == state.inspectedServerUrl }
    val activeServer = state.settings?.servers?.find { it.url == state.settings?.activeUrl }
    val accountSummary = when {
        state.auth.authenticated -> state.auth.username
        activeServer == null -> uiText(CopyR.string.copy_signed_out_no_server_configured)
        else -> uiText(CopyR.string.copy_signed_out)
    }
    var seenServerSave by rememberSaveable { mutableStateOf(state.serverSavedVersion) }
    LaunchedEffect(state.serverSavedVersion) {
        if (state.serverSavedVersion != seenServerSave) {
            seenServerSave = state.serverSavedVersion
            addingServer = false
            page = "server"
        }
    }
    LaunchedEffect(state.settings, state.busy) {
        if (page == "server-detail" && state.inspectedServerUrl != null && inspectedServer == null && !state.busy) {
            deletingServer = false
            page = "server"
        }
    }
    LaunchedEffect(state.authSuccessVersion) {
        if (state.authSuccessVersion != seenAuthSuccess) {
            seenAuthSuccess = state.authSuccessVersion
            onAuthConfirmationChanged(""); passwordVisible = false; registering = false
            if (page == "auth") page = authReturnPage
        }
    }
    LaunchedEffect(page) {
        if (page != "auth") { onAuthConfirmationChanged(""); passwordVisible = false; onResetAuthForm() }
    }
    val enabled = state.mode == StorageMode.Remote
    val count = state.syncStatus.conflicts.size + state.syncStatus.expiryConflicts.size
    val statusTitle = when {
        state.syncStatus.syncing -> uiText(CopyR.string.copy_syncing)
        !enabled -> uiText(CopyR.string.copy_sync_is_off)
        state.syncStatus.needsLogin -> uiText(CopyR.string.copy_account_verification_failed)
        !state.auth.authenticated -> uiText(CopyR.string.copy_sign_in_required)
        state.syncStatus.waitingForNetwork -> uiText(CopyR.string.copy_waiting_for_network)
        state.syncStatus.error != null -> uiText(CopyR.string.copy_sync_did_not_finish)
        count > 0 -> uiQuantity(CopyR.plurals.copy_count_conflicts, count)
        state.syncStatus.pendingOperations > 0 -> uiText(CopyR.string.copy_waiting_to_sync)
        state.syncStatus.lastSyncedAt != null -> uiText(CopyR.string.copy_synced)
        else -> uiText(CopyR.string.copy_waiting_for_first_sync)
    }
    val parent = when (page) {
        "auth" -> authReturnPage
        "server-detail" -> "server"
        "account" -> "home"
        "conflict" -> "conflicts"
        "conflicts", "frequency" -> "sync"
        else -> "home"
    }
    val exitsToOverview = onExit != null && (page == entry.page || page == "home")
    val goBack: () -> Unit = { if (exitsToOverview) onExit?.invoke() else page = parent }
    BackHandler(page != "home" || onExit != null, onBack = goBack)
    val a = state.syncStatus.conflicts.find { !expiryConflict && it.assetId == conflictId }
    val e = state.syncStatus.expiryConflicts.find { expiryConflict && it.expiryId == conflictId }
    val unavailable = a?.remoteUnavailable ?: e?.remoteUnavailable ?: false
    val localDeleted = a?.localDeleted ?: e?.localDeleted ?: false
    val remoteDeleted = a?.remoteDeleted ?: e?.remoteDeleted ?: false
    val conflictExists = a != null || e != null

    val scroll = rememberScrollState()
    var scrollPage by rememberSaveable { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (scrollPage != page) {
            scroll.scrollTo(0)
            scrollPage = page
            if (page == "server") onEditServer(null)
        }
    }
    Column(modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (page != "home") Spacer(Modifier.height(0.dp))
            if (page == "home" && onExit == null) SuirenHeader(title = uiText(CopyR.string.copy_settings)) else TextButton(onClick = goBack) {
                Text("‹  " + if (exitsToOverview) uiText(CopyR.string.copy_overview) else when(parent) { "server" -> uiText(CopyR.string.copy_server_addresses); "server-detail" -> uiText(CopyR.string.copy_server_details); "sync" -> uiText(CopyR.string.copy_sync_settings); "conflicts" -> uiText(CopyR.string.copy_conflicts_conflicts); else -> uiText(CopyR.string.copy_settings) })
            }
            when (page) {
                "home" -> {
                    Text(uiText(CopyR.string.copy_your_data_stays_on_this_device_you_decide_when_to), fontSize = 13.sp, lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    SettingsHomeGroup {
                        SettingsHomeLink(uiText(CopyR.string.copy_account), accountSummary, Icons.Outlined.AccountCircle) { page = "account" }
                        HorizontalDivider()
                        SettingsHomeLink(uiText(CopyR.string.copy_sync_settings), statusTitle, Icons.Outlined.Refresh) { page = "sync" }
                    }
                    SettingsHomeGroup {
                        SettingsHomeLink(uiText(CopyR.string.copy_backup_and_restore), uiText(CopyR.string.copy_export_preview_and_safely_restore_local_data), SuirenIcons.Backup) { page = "backup" }
                        HorizontalDivider()
                        SettingsHomeLink(uiText(CopyR.string.copy_appearance), themeLabel(themeMode), SuirenIcons.Appearance) { page = "appearance" }
                        HorizontalDivider()
                        SettingsHomeLink(uiText(CopyR.string.language_title), languagePreferenceLabel(), SuirenIcons.Appearance) { page = "language" }
                        HorizontalDivider()
                        SettingsHomeLink(uiText(CopyR.string.copy_expiry_preferences), uiQuantity(CopyR.plurals.copy_mark_items_as_expiring_count_days_ahead, state.soonDays), Icons.Outlined.DateRange) { page = "reminders" }
                    }
                    SettingsHomeGroup {
                        SettingsHomeLink(uiText(CopyR.string.copy_server_addresses), activeServer?.url ?: uiText(CopyR.string.copy_not_selected_optional), SuirenIcons.Server) { onEditServer(null); page = "server" }
                        HorizontalDivider()
                        SettingsHomeLink(uiText(CopyR.string.copy_about), uiText(CopyR.string.copy_personal_toolbox_assets_and_essentials), Icons.Outlined.Info) { page = "about" }
                    }
                }
                "language" -> {
                    PageTitle(uiText(CopyR.string.language_title), uiText(CopyR.string.language_description))
                    SettingsCard { LanguagePicker() }
                }
                "account" -> {
                    PageTitle(uiText(CopyR.string.copy_account), uiText(CopyR.string.copy_manage_your_signed_in_account))
                    SettingsCard {
                        Fact(uiText(CopyR.string.copy_current_account), if (state.auth.authenticated) state.auth.username else uiText(CopyR.string.copy_signed_out))
                        if (state.auth.authenticated) {
                            SecondaryAction(uiText(CopyR.string.copy_sign_out), !connectionBusy) { confirmation = "logout" }
                        } else if (activeServer != null) {
                            SecondaryAction(uiText(CopyR.string.copy_sign_in_register), !connectionBusy) {
                                onOpenServer(activeServer.url)
                                authReturnPage = "account"
                                onResetAuthForm()
                                registering = false
                                page = "auth"
                            }
                        } else {
                            Caption(uiText(CopyR.string.copy_no_server_configured_add_one_under_server_addresses_in_settings))
                        }
                        state.authError?.let { ErrorNote(it) }
                    }
                    SettingsCard { Caption(uiText(CopyR.string.copy_only_one_account_can_be_signed_in_sign_out_before)) }
                }
                "sync" -> {
                    if (!enabled) {
                        PageTitle(uiText(CopyR.string.copy_local_data_is_ready), uiText(CopyR.string.copy_add_edit_archive_and_view_your_records_without_a_network), uiText(CopyR.string.copy_sync_is_off))
                        SettingsCard {
                            Text(uiText(CopyR.string.copy_records_always_stay_on_this_device), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Caption(uiText(CopyR.string.copy_the_server_syncs_devices_your_local_database_remains_the_source))
                            state.migrationError?.let { ErrorNote(it) }
                            PrimaryAction(
                                when {
                                    state.auth.authenticated -> uiText(CopyR.string.copy_preview_and_enable_sync)
                                    activeServer != null -> uiText(CopyR.string.copy_account_settings)
                                    else -> uiText(CopyR.string.copy_back_to_settings)
                                },
                                enabled = !state.migrationBusy,
                            ) {
                                when {
                                    state.auth.authenticated -> onUseRemote()
                                    activeServer != null -> page = "account"
                                    else -> page = "home"
                                }
                            }
                        }
                    } else {
                        PageTitle(uiText(CopyR.string.copy_sync_in_the_background), uiText(CopyR.string.copy_your_local_database_is_the_source_of_truth_the_server))
                        SettingsCard {
                            Caption(uiText(CopyR.string.copy_current_status))
                            Text(statusTitle, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            Caption(uiText(CopyR.string.copy_keep_editing_locally_a_conflict_only_pauses_its_affected_record))
                            if (state.syncStatus.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            listOfNotNull(state.syncStatus.error, state.error, state.migrationError).distinct().forEach { ErrorNote(it) }
                            HorizontalDivider()
                            Caption(uiText(CopyR.string.copy_last_success_count, lastSyncLabel(state.syncStatus)))
                            SecondaryAction(uiText(CopyR.string.copy_current_server)) {
                                state.settings?.activeUrl?.let { onOpenServer(it); page = "server-detail" } ?: run { onEditServer(null); page = "server" }
                            }
                        }
                        SyncStatistics(state.syncStatus)
                        GroupTitle(uiText(CopyR.string.copy_sync_mode))
                        SettingsCard {
                            Choice(uiText(CopyR.string.copy_sync_after_changes), uiText(CopyR.string.copy_nearby_changes_are_combined_before_sending_manual_sync_is_always), state.syncSchedule == SyncSchedule.OnChange) { onSyncSchedule(SyncSchedule.OnChange) }
                            Choice(uiText(CopyR.string.copy_periodic_sync), uiText(CopyR.string.copy_runs_approximately_at_the_selected_interval_subject_to_network_and), state.syncSchedule != SyncSchedule.OnChange) { if (state.syncSchedule == SyncSchedule.OnChange) onSyncSchedule(SyncSchedule.Hourly) }
                        }
                        if (state.syncSchedule != SyncSchedule.OnChange) {
                            GroupTitle(uiText(CopyR.string.copy_requested_interval))
                            SettingsCard {
                                SyncSchedule.entries.filter { it != SyncSchedule.OnChange }.forEach { option ->
                                    Choice(uiText(option.label), uiText(CopyR.string.copy_network_and_battery_restrictions_may_delay_background_sync), state.syncSchedule == option) { onSyncSchedule(option) }
                                }
                            }
                        }
                        GroupTitle(uiText(CopyR.string.copy_actions_and_status))
                        SettingsCard {
                            when {
                                state.syncStatus.syncing -> SettingsLink(uiText(CopyR.string.copy_stop_this_request), uiText(CopyR.string.copy_pending_batches_are_retained_for_retry), onClick = onCancelSync)
                                state.syncStatus.needsLogin -> Caption(uiText(CopyR.string.copy_this_server_cannot_verify_your_account_sign_out_under_account))
                                !state.auth.authenticated -> SettingsLink(uiText(CopyR.string.copy_account_settings), uiText(CopyR.string.copy_sign_in_to_preview_and_enable_sync)) { page = "account" }
                                else -> SettingsLink(if (state.syncStatus.error == null) uiText(CopyR.string.copy_sync_now) else uiText(CopyR.string.copy_retry_sync), uiText(CopyR.string.copy_manual_sync_is_always_available), onClick = onSyncNow)
                            }
                            HorizontalDivider()
                            SettingsLink(uiText(CopyR.string.copy_resolve_conflicts), uiQuantity(CopyR.plurals.copy_count_conflicts_other_records_continue_syncing, count)) { page = "conflicts" }
                            HorizontalDivider()
                            SettingsLink(uiText(CopyR.string.copy_pause_sync), uiText(CopyR.string.copy_pause_network_sync_and_keep_editing_locally)) { confirmation = "pause" }
                        }
                    }
                }
                "server" -> {
                    PageTitle(uiText(CopyR.string.copy_server_addresses), uiText(CopyR.string.copy_test_the_exact_address_before_adding_or_changing_it_tests))
                    state.settings?.servers.orEmpty().forEach { server ->
                        SettingsCard(Modifier.clickable(enabled = !connectionBusy) {
                            onOpenServer(server.url); page = "server-detail"
                        }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(server.name.ifBlank { uiText(CopyR.string.copy_unnamed_server) }, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                if (server.url == state.settings?.activeUrl) {
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary) {
                                        Text(uiText(CopyR.string.copy_selected), Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondary)
                                    }
                                }
                            }
                            Caption(server.url)
                        }
                    }
                    if (state.settings?.servers.orEmpty().isEmpty()) SettingsCard {
                        Text(uiText(CopyR.string.copy_no_servers_yet), fontWeight = FontWeight.Medium)
                        Caption(uiText(CopyR.string.copy_offline_features_are_ready_to_use))
                    }
                    PrimaryAction(uiText(CopyR.string.copy_add_server), !connectionBusy) { onEditServer(null); addingServer = true }
                }
                "server-detail" -> {
                    val server = inspectedServer
                    if (server != null) {
                        Text(uiText(CopyR.string.copy_server_details), fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
                        ServerEditorFields(state, server, onAddressChanged, onNameChanged,
                            onTestConnection, onSave, onSelect, { deletingServer = true })
                    }
                }
                "auth" -> {
                    PageTitle(if (registering) uiText(CopyR.string.copy_register_a_sync_account) else uiText(CopyR.string.copy_sign_in_to_suirenx_sync), uiText(CopyR.string.copy_current_server))
                    val validation = accountValidation(state.authUsername, state.authPassword, if (registering) confirmPassword else null)
                    SettingsCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!registering, { registering = false; onAuthConfirmationChanged(""); onResetAuthForm() }, { Text(uiText(CopyR.string.copy_sign_in)) }, enabled = !connectionBusy)
                            FilterChip(registering, { registering = true; onAuthConfirmationChanged(""); onResetAuthForm() }, { Text(uiText(CopyR.string.copy_register)) }, enabled = !connectionBusy)
                        }
                        OutlinedTextField(state.authUsername, onAuthUsernameChanged, Modifier.fillMaxWidth(), label = { Text(uiText(CopyR.string.copy_account)) }, singleLine = true, enabled = !connectionBusy)
                        AccountPasswordField(state.authPassword, onAuthPasswordChanged, uiText(CopyR.string.copy_password), passwordVisible, !connectionBusy,
                            onToggleVisibility = { passwordVisible = !passwordVisible })
                        Caption(if (registering) uiText(RegistrationPasswordHint) else uiText(CopyR.string.copy_enter_your_existing_account_password))
                        if (registering) AccountPasswordField(confirmPassword, onAuthConfirmationChanged, uiText(CopyR.string.copy_confirm_password), passwordVisible, !connectionBusy,
                            onToggleVisibility = { passwordVisible = !passwordVisible })
                        validation?.let { Caption(uiText(it)) }
                        PrimaryAction(if (state.authBusy) uiText(CopyR.string.copy_working) else if (registering) uiText(CopyR.string.copy_register) else uiText(CopyR.string.copy_sign_in),
                            enabled = !connectionBusy && validation == null && inspectedServer != null && !state.auth.authenticated,
                            onClick = { if (registering) onRegister(confirmPassword) else onLogin() })
                        listOfNotNull(state.authError, state.error, state.migrationError).distinct().forEach { ErrorNote(it) }

                    }
                    Caption(uiText(CopyR.string.copy_review_and_enable_sync_after_signing_in_signing_in_never))
                }
                "conflicts" -> {
                    PageTitle(uiText(CopyR.string.copy_conflicts), uiText(CopyR.string.copy_only_conflicting_records_pause_other_assets_and_items_continue_syncing))
                    if (count == 0) SettingsCard { Text(uiText(CopyR.string.copy_no_conflicts)); Caption(uiText(CopyR.string.copy_if_a_record_changes_on_both_sides_both_versions_are)) }
                    state.syncStatus.conflicts.forEach { conflict -> SettingsCard { SettingsLink(conflict.local.name, conflictDescription(conflict.localDeleted, conflict.remoteDeleted, conflict.remoteUnavailable)) { conflictId = conflict.assetId; expiryConflict = false; page = "conflict" } } }
                    state.syncStatus.expiryConflicts.forEach { conflict -> SettingsCard { SettingsLink(conflict.localName, conflictDescription(conflict.localDeleted, conflict.remoteDeleted, conflict.remoteUnavailable)) { conflictId = conflict.expiryId; expiryConflict = true; page = "conflict" } } }
                }
                "conflict" -> {
                    PageTitle(a?.local?.name ?: e?.localName ?: uiText(CopyR.string.copy_conflict_resolved), uiText(CopyR.string.copy_compare_the_fields_then_choose_how_to_resolve_the_record))
                    if (!conflictExists) PrimaryAction(uiText(CopyR.string.copy_back_to_conflicts)) { page = "conflicts" }
                    else if (unavailable) SettingsCard {
                        Text(uiText(CopyR.string.copy_remote_snapshot_unavailable))
                        Caption(uiText(CopyR.string.copy_this_is_an_ownership_or_permission_conflict_not_a_deleted))
                        PrimaryAction(uiText(CopyR.string.copy_check_account_and_server)) { page = "server" }
                    } else {
                        ConflictVersions(a, e)
                        Caption(uiText(CopyR.string.copy_the_protocol_provides_no_verified_edit_times_for_both_sides))
                        SettingsCard {
                            val busy = state.conflictBusy || state.syncStatus.syncing
                            ChoiceAction(if(localDeleted) uiText(CopyR.string.copy_keep_local_deletion) else uiText(CopyR.string.copy_keep_local), uiText(CopyR.string.copy_use_the_local_version_you_will_be_prompted_again_if), !busy) { resolution = SyncConflictResolution.KeepLocal }
                            ChoiceAction(if(remoteDeleted) uiText(CopyR.string.copy_keep_remote_deletion) else uiText(CopyR.string.copy_keep_remote), uiText(CopyR.string.copy_replace_this_local_record_with_the_remote_version), !busy) { resolution = SyncConflictResolution.KeepRemote }
                            ChoiceAction(if(localDeleted || remoteDeleted) uiText(CopyR.string.copy_save_the_edited_version_as_a_copy) else uiText(CopyR.string.copy_keep_both), uiText(CopyR.string.copy_keep_the_remote_version_and_save_local_edits_as_a), !busy) { resolution = SyncConflictResolution.KeepBoth }
                        }
                    }
                }
                "backup" -> {
                    PageTitle(uiText(CopyR.string.copy_backup_and_restore), uiText(CopyR.string.copy_complete_local_json_including_archived_assets_and_expiry_items_no))
                    SettingsCard {
                        state.localSummary?.let { Fact(uiText(CopyR.string.copy_assets_including_archived), uiQuantity(CopyR.plurals.copy_count_items, it.assetCount)); Fact(uiText(CopyR.string.copy_items), uiQuantity(CopyR.plurals.copy_count_items, it.expiryItemCount)) }
                        PrimaryAction(if(state.backupBusy) uiText(CopyR.string.copy_working) else uiText(CopyR.string.copy_export_local_backup), !state.backupBusy, onExportBackup)
                        SecondaryAction(uiText(CopyR.string.copy_restore_from_file), !state.backupBusy, onImportBackup)
                    }
                    Caption(uiText(CopyR.string.copy_validate_the_file_and_preview_record_counts_first_after_confirmation))
                    state.backupError?.let { ErrorNote(it) }
                }
                "appearance" -> {
                    PageTitle(uiText(CopyR.string.copy_appearance), uiText(CopyR.string.copy_choose_how_the_app_looks))
                    SettingsCard { ThemeMode.entries.forEach { mode -> Choice(themeLabel(mode), "", themeMode == mode) { onThemeModeChanged(mode) } } }
                }
                "reminders" -> {
                    PageTitle(uiText(CopyR.string.copy_expiry_preferences), uiText(CopyR.string.copy_mark_upcoming_expiry_dates_in_the_item_list))
                    SettingsCard { SoonDaysSetting(state.soonDays, onSoonDaysChanged) }
                    Caption(uiText(CopyR.string.copy_this_controls_in_app_expiry_status_it_does_not_enable))
                }
                "about" -> {
                    val context = LocalContext.current
                    val buildInfo = remember(context) { InstalledBuildInfo.read(context) }
                    val packageInfo = remember(context) {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    }
                    val buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                    PageTitle(uiText(CopyR.string.copy_suirenx), uiText(CopyR.string.copy_your_everyday_tools_together))
                    SettingsCard { Text(uiText(CopyR.string.copy_personal_toolbox)); Caption(uiText(CopyR.string.copy_track_assets_calculate_ownership_costs_and_manage_expiry_dates_data)) }
                    SettingsCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Caption(uiText(CopyR.string.copy_version))
                            Text(packageInfo.versionName ?: uiText(CopyR.string.copy_unknown), modifier = Modifier.testTag("about.version"))
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Caption(uiText(CopyR.string.copy_build))
                            Text(buildNumber.toString(), modifier = Modifier.testTag("about.build"))
                        }
                        DevelopmentBuildRows(buildInfo)
                    }
                }
            }
            // Errors are rendered once at the action/status card that owns them.
            if (page == "conflict") state.error?.let { ErrorNote(it) }
            state.notice?.let { Caption(uiText(it)) }
            Spacer(Modifier.height(108.dp))
        }
    }
    state.migrationPreview?.let { preview ->
        AlertDialog(onDismissRequest = { if(!state.migrationBusy) onCancelMigration() }, title = { Text(uiText(CopyR.string.copy_review_before_syncing)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact(uiText(CopyR.string.copy_assets_including_archived), uiQuantity(CopyR.plurals.copy_count_items, preview.localAssetCount))
                Fact(uiText(CopyR.string.copy_items), uiQuantity(CopyR.plurals.copy_count_items, preview.localExpiryCount))
                Fact(uiText(CopyR.string.copy_sync_account), state.auth.username)
                Fact(uiText(CopyR.string.copy_server), state.settings?.activeUrl.orEmpty())
                Caption(uiText(CopyR.string.copy_a_safety_backup_is_created_first_local_data_will_sync))
                state.migrationError?.let { ErrorNote(it) }
            }
        }, confirmButton = { TextButton(onClick = { onConfirmMigration(); page = "sync" }, enabled = !state.migrationBusy) { Text(if(state.migrationBusy) uiText(CopyR.string.copy_syncing_syncing) else uiText(CopyR.string.copy_confirm_and_enable_sync)) } }, dismissButton = { TextButton(onClick = onCancelMigration, enabled = !state.migrationBusy) { Text(uiText(CopyR.string.copy_cancel)) } })
    }
    state.backupSummary?.let { summary ->
        AlertDialog(onDismissRequest = { if(!state.backupBusy) onCancelRestore() }, title = { Text(uiText(CopyR.string.copy_restore_preview)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact(uiText(CopyR.string.copy_assets_in_backup), uiQuantity(CopyR.plurals.copy_count_items, summary.assetCount))
                Fact(uiText(CopyR.string.copy_items_in_backup), uiQuantity(CopyR.plurals.copy_count_items, summary.expiryItemCount))
                Caption(uiText(CopyR.string.copy_file_validation_passed_confirming_replaces_local_data_after_creating_a))
                state.backupError?.let { ErrorNote(it) }
            }
        }, confirmButton = { TextButton(onClick = { confirmation = "restore" }, enabled = !state.backupBusy) { Text(if(state.backupBusy) uiText(CopyR.string.copy_restoring) else uiText(CopyR.string.copy_continue_restore)) } }, dismissButton = { TextButton(onClick = onCancelRestore, enabled = !state.backupBusy) { Text(uiText(CopyR.string.copy_cancel)) } })
    }
    if (addingServer) {
        val editorBusy = connectionBusy || state.testingConnection
        val currentEditorBusy by rememberUpdatedState(editorBusy)
        ModalBottomSheet(
            onDismissRequest = { if (!editorBusy) { addingServer = false; onEditServer(null) } },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.Hidden || !currentEditorBusy }),
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !editorBusy),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.testTag("server.add").fillMaxHeight(0.95f).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { addingServer = false; onEditServer(null) }, enabled = !editorBusy) { Text(uiText(CopyR.string.copy_cancel)) }
                    Text(uiText(CopyR.string.copy_add_server), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                ServerEditorFields(state, null, onAddressChanged, onNameChanged, onTestConnection,
                    onSave, onSelect, {})
            }
        }
    }
    if (deletingServer && inspectedServer != null) {
        AlertDialog(onDismissRequest = { if (!connectionBusy) deletingServer = false },
            title = { Text(uiText(CopyR.string.copy_delete_this_address)) },
            text = { Text(uiText(CopyR.string.copy_only_the_connection_is_removed_account_local_data_and_sync)) },
            confirmButton = { TextButton(onClick = { onDeleteServer(inspectedServer.url); deletingServer = false },
                enabled = !connectionBusy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(uiText(CopyR.string.copy_delete_address_delete_address)) } },
            dismissButton = { TextButton(onClick = { deletingServer = false }, enabled = !connectionBusy) { Text(uiText(CopyR.string.copy_cancel)) } })
    }
    resolution?.let { choice -> AlertDialog(onDismissRequest = { resolution = null }, title = { Text(uiText(CopyR.string.copy_resolve_this_record)) }, text = { Text(if(choice == SyncConflictResolution.KeepBoth) uiText(CopyR.string.copy_the_edited_version_is_saved_as_a_copy_keeping_both) else uiText(CopyR.string.copy_the_unselected_version_is_replaced_only_this_record_is_affected)) }, confirmButton = { TextButton(onClick = { onResolveConflict(conflictId, expiryConflict, choice); resolution = null }, enabled = conflictExists && !state.conflictBusy && !unavailable) { Text(uiText(CopyR.string.copy_confirm_resolution)) } }, dismissButton = { TextButton(onClick = { resolution = null }) { Text(uiText(CopyR.string.copy_cancel)) } }) }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when(action) { "restore" -> uiText(CopyR.string.copy_replace_local_data); "pause" -> uiText(CopyR.string.copy_pause_sync_pause_sync); "logout" -> uiText(CopyR.string.copy_sign_out_of_sync); else -> uiText(CopyR.string.copy_change_sync_connection) }) }, text = { Text(when(action) {
        "restore" -> uiText(CopyR.string.copy_a_safety_backup_is_created_before_restoring_the_selected_file)
        else -> uiText(CopyR.string.copy_network_sync_stops_local_data_and_pending_edits_are_retained)
    }) }, confirmButton = { TextButton(onClick = {
        when(action) { "restore" -> onConfirmRestore(); "pause" -> onUseLocal(); "logout" -> onLogout() }
        confirmation = null
    }) { Text(uiText(CopyR.string.copy_confirm)) } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text(uiText(CopyR.string.copy_cancel)) } }) }
}

@Composable
internal fun SoonDaysSetting(value: Int, onValueChanged: (Int) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { next -> if(next.length <= 2 && next.all(Char::isDigit)) { text = next; next.toIntOrNull()?.takeIf { it in 1..30 }?.let(onValueChanged) } }, Modifier.fillMaxWidth(), label = { Text(uiText(CopyR.string.copy_days_ahead_1_30)) }, supportingText = { Text(uiQuantity(CopyR.plurals.copy_current_setting_mark_expiry_count_days_ahead, value)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = text.toIntOrNull() !in 1..30)
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() { SuirenXTheme { BackendSettingsScreen(BackendUiState(mode = StorageMode.Local), {}, {}, {}, {}, {}, {}, {}) } }

@Composable
private fun AccountPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility, enabled = enabled) {
                Icon(if (visible) SuirenIcons.VisibilityOff else SuirenIcons.Visibility,
                    contentDescription = if (visible) uiText(CopyR.string.copy_hide_password) else uiText(CopyR.string.copy_show_password))
            }
        },
    )
}

/** Development trace is deliberately absent from release About screens. */
@Composable
internal fun DevelopmentBuildRows(buildInfo: InstalledBuildInfo?) {
    if (buildInfo?.isDevelopment == true) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption(uiText(CopyR.string.copy_commit))
            Text(buildInfo.code, modifier = Modifier.testTag("about.code"))
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption(uiText(CopyR.string.copy_build_time))
            Text(buildInfo.localTime, modifier = Modifier.testTag("about.builtAt"))
        }
    }
}
