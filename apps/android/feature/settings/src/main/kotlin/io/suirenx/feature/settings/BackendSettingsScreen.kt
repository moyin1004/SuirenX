package io.suirenx.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.suirenx.core.ui.component.SuirenHeader
import io.suirenx.core.ui.theme.SuirenXTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Native implementation of the approved OpenDesign settings flow. */
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
    var pendingServer by remember { mutableStateOf<String?>(null) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var authReturnPage by rememberSaveable { mutableStateOf("account") }
    var serverSaveReturnPage by rememberSaveable { mutableStateOf("server-detail") }
    var seenAuthSuccess by rememberSaveable { mutableStateOf(state.authSuccessVersion) }
    val connectionBusy = state.busy || state.authBusy || state.migrationBusy
    val inspectedServer = state.settings?.servers?.find { it.url == state.inspectedServerUrl }
    val activeServer = state.settings?.servers?.find { it.url == state.settings?.activeUrl }
    val accountSummary = when {
        state.auth.authenticated -> state.auth.username
        activeServer == null -> "未登录 · 尚未配置服务器"
        else -> "未登录"
    }
    var seenServerSave by rememberSaveable { mutableStateOf(state.serverSavedVersion) }
    LaunchedEffect(state.serverSavedVersion) {
        if (state.serverSavedVersion != seenServerSave) {
            seenServerSave = state.serverSavedVersion; page = serverSaveReturnPage
        }
    }
    LaunchedEffect(state.authSuccessVersion) {
        if (state.authSuccessVersion != seenAuthSuccess) {
            seenAuthSuccess = state.authSuccessVersion
            confirmPassword = ""; passwordVisible = false; registering = false
            if (page == "auth") page = authReturnPage
        }
    }
    LaunchedEffect(page) {
        if (page != "auth") { confirmPassword = ""; passwordVisible = false; onResetAuthForm() }
    }
    val enabled = state.mode == StorageMode.Remote
    val count = state.syncStatus.conflicts.size + state.syncStatus.expiryConflicts.size
    val statusTitle = when {
        state.syncStatus.syncing -> "同步中"
        !enabled -> "同步未开启"
        state.syncStatus.needsLogin -> "账号校验失败"
        !state.auth.authenticated -> "需要登录"
        state.syncStatus.waitingForNetwork -> "等待网络"
        state.syncStatus.error != null -> "同步未完成"
        count > 0 -> "$count 项冲突待处理"
        state.syncStatus.pendingOperations > 0 -> "等待同步"
        state.syncStatus.lastSyncedAt != null -> "已同步"
        else -> "等待首次同步"
    }
    val parent = when (page) {
        "auth" -> authReturnPage
        "delete-server" -> "server-detail"
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

    fun requestServerSave(returnPage: String) {
        val changingAddress = state.editingServerUrl != null && state.address.trim() != state.editingServerUrl
        if (changingAddress) {
            serverSaveReturnPage = returnPage
            confirmation = "save-server"
        } else {
            onSave()
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0); if (page == "server") onEditServer(null) }
    Column(modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (page != "home") Spacer(Modifier.height(0.dp))
            if (page == "home" && onExit == null) SuirenHeader() else TextButton(onClick = goBack) {
                Text("‹  " + if (exitsToOverview) "总览" else when(parent) { "server" -> "服务器管理"; "server-detail" -> "服务器详情"; "sync" -> "同步设置"; "conflicts" -> "冲突列表"; else -> "设置" })
            }
            when (page) {
                "home" -> {
                    PageTitle("设置", "本机数据、同步方式和显示偏好，都在这里管理。")
                    GroupTitle("账号")
                    SettingsCard {
                        SettingsLink("账号", accountSummary) { page = "account" }
                    }
                    SettingsCard {
                        Caption(statusTitle)
                        Text(if (enabled) "本机与多端保持同步" else "本机可用，随时可以开启", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Caption("资产和用品始终保存在本机；开启后仅用于多端同步。")
                        state.localSummary?.let { Caption("本机记录 · ${it.assetCount} 项资产 · ${it.expiryItemCount} 项用品（含归档）") }
                        PrimaryAction(if (enabled) "管理同步" else "开启同步") { page = "sync" }
                    }
                    Caption("同步失败不会影响本地保存；无网络时仍可继续编辑。")
                    GroupTitle("同步")
                    SettingsCard {
                        SettingsLink("同步设置", if (enabled) "${state.syncSchedule.label} · $count 项冲突" else "了解同步范围并开启多端同步") { page = "sync" }
                    }
                    GroupTitle("数据")
                    SettingsCard { SettingsLink("备份与恢复", "本机完整 JSON · 含归档和用品，不含凭证") { page = "backup" } }
                    GroupTitle("个性化")
                    SettingsCard {
                        SettingsLink("外观", themeLabel(themeMode)) { page = "appearance" }
                        HorizontalDivider()
                        SettingsLink("用品临期提醒", "提前 ${state.soonDays} 天标记临期") { page = "reminders" }
                    }
                    GroupTitle("关于")
                    SettingsCard { SettingsLink("燧人", "个人工具箱 · 资产与用品管理") { page = "about" } }
                    GroupTitle("连接设置")
                    SettingsCard { SettingsLink("服务器地址", activeServer?.url ?: "可选 · 添加前先测试连接") { onEditServer(null); page = "server" } }
                }
                "account" -> {
                    PageTitle("账号", "像普通应用一样管理当前登录账号。")
                    SettingsCard {
                        Fact("当前账号", if (state.auth.authenticated) state.auth.username else "未登录")
                        if (state.auth.authenticated) {
                            SecondaryAction("退出登录", !connectionBusy) { confirmation = "logout" }
                        } else if (activeServer != null) {
                            SecondaryAction("登录 / 注册", !connectionBusy) {
                                onOpenServer(activeServer.url)
                                authReturnPage = "account"
                                onResetAuthForm()
                                registering = false
                                page = "auth"
                            }
                        } else {
                            Caption("尚未配置服务器地址。可在设置页靠后的“服务器地址”中单独配置，然后回来登录。")
                        }
                        state.authError?.let { ErrorNote(it) }
                    }
                    SettingsCard { Caption("同一时间只能登录一个账号。要使用其他账号，请先退出当前账号；服务器地址与账号独立管理。") }
                }
                "sync" -> {
                    if (!enabled) {
                        PageTitle("本机数据已准备好", "没有网络也能新增、编辑、归档和查看资产与用品。", "同步未开启")
                        SettingsCard {
                            Text("资产和用品始终存本机", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Caption("服务器仅负责多端同步，不会替代本机数据。")
                            state.migrationError?.let { ErrorNote(it) }
                            PrimaryAction(
                                when {
                                    state.auth.authenticated -> "预览并开启同步"
                                    activeServer != null -> "账号设置"
                                    else -> "返回设置"
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
                        PageTitle("把同步交给后台", "本机始终是资产和用品的事实来源，服务器只负责多端同步。")
                        SettingsCard {
                            Caption("当前状态")
                            Text(statusTitle, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            Caption("同步不会阻止本地编辑，冲突只暂停对应记录。")
                            if (state.syncStatus.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            listOfNotNull(state.syncStatus.error, state.error, state.migrationError).distinct().forEach { ErrorNote(it) }
                            HorizontalDivider()
                            Caption("上次成功：${lastSyncLabel(state.syncStatus)}")
                            SecondaryAction("当前服务器") {
                                state.settings?.activeUrl?.let { onOpenServer(it); page = "server-detail" } ?: run { onEditServer(null); page = "server" }
                            }
                        }
                        SyncStatistics(state.syncStatus)
                        GroupTitle("同步方式")
                        SettingsCard {
                            Choice("每次修改后同步", "短暂合并连续修改后尽快后台发送，仍可随时立即同步。", state.syncSchedule == SyncSchedule.OnChange) { onSyncSchedule(SyncSchedule.OnChange) }
                            Choice("定时同步", "约按所选频率运行，受网络和省电策略影响。", state.syncSchedule != SyncSchedule.OnChange) { if (state.syncSchedule == SyncSchedule.OnChange) onSyncSchedule(SyncSchedule.Hourly) }
                        }
                        if (state.syncSchedule != SyncSchedule.OnChange) {
                            GroupTitle("定时频率")
                            SettingsCard {
                                SyncSchedule.entries.filter { it != SyncSchedule.OnChange }.forEach { option ->
                                    Choice(option.label, "后台调度可能受网络和省电策略影响", state.syncSchedule == option) { onSyncSchedule(option) }
                                }
                            }
                        }
                        GroupTitle("处理与状态")
                        SettingsCard {
                            when {
                                state.syncStatus.syncing -> SettingsLink("停止此次请求", "保留待发送批次，可以稍后重试", onCancelSync)
                                state.syncStatus.needsLogin -> Caption("当前服务器无法验证已登录账号。请到设置首页的“账号”中先注销，再重新登录；账号信息不会因切换地址自动清除。")
                                !state.auth.authenticated -> SettingsLink("账号设置", "登录后可预览并开启同步") { page = "account" }
                                else -> SettingsLink(if (state.syncStatus.error == null) "立即同步" else "重试同步", "始终保留的手动同步入口", onSyncNow)
                            }
                            HorizontalDivider()
                            SettingsLink("冲突处理", "$count 项待处理 · 只暂停冲突记录") { page = "conflicts" }
                            HorizontalDivider()
                            SettingsLink("暂停同步", "暂停网络同步，不影响本机编辑") { confirmation = "pause" }
                        }
                    }
                }
                "server" -> {
                    PageTitle("服务器地址", "独立管理可选的同步地址。添加前必须测试连接成功。")
                    SettingsCard {
                        Text("添加服务器", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(state.name, onNameChanged, Modifier.fillMaxWidth(), label = { Text("名称（可选）") }, singleLine = true, enabled = !connectionBusy)
                        OutlinedTextField(state.address, onAddressChanged, Modifier.fillMaxWidth(), label = { Text("服务器地址") }, placeholder = { Text("https://sync.example.com") }, singleLine = true, enabled = !connectionBusy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        Caption("账号与地址设置相互独立。地址变更会保留登录信息；若同步校验失败，请到账号设置注销。")
                        SecondaryAction(if (state.testingConnection) "测试中…" else "测试连接", !connectionBusy && !state.testingConnection && state.address.isNotBlank()) { onTestConnection(state.address) }
                        ConnectionTestResult(state)
                        PrimaryAction("添加服务器", !connectionBusy && state.connectionTestAddress == state.address.trim() && !state.connectionTestFailed, onSave)
                        state.error?.let { ErrorNote(it) }
                    }
                    GroupTitle("已保存的服务器 · ${state.settings?.servers.orEmpty().size} 个")
                    if (state.settings?.servers.orEmpty().isEmpty()) SettingsCard { Caption("还没有服务器地址；测试连接成功后才能添加。") }
                    state.settings?.servers.orEmpty().forEach { server ->
                        SettingsCard {
                            SettingsLink(server.name, server.url + if (server.url == state.settings?.activeUrl) " · 当前同步服务器" else "") {
                                onOpenServer(server.url); page = "server-detail"
                            }
                        }
                    }
                }
                "server-detail" -> {
                    val server = inspectedServer
                    if (server == null) {
                        PageTitle("服务器已删除", "本机数据仍然保留。")
                        PrimaryAction("返回服务器管理") { onEditServer(null); page = "server" }
                    } else {
                        PageTitle(server.name, server.url)
                        SettingsCard {
                            Text("编辑服务器", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(state.name, onNameChanged, Modifier.fillMaxWidth(), label = { Text("名称（可选）") }, singleLine = true, enabled = !connectionBusy)
                            OutlinedTextField(state.address, onAddressChanged, Modifier.fillMaxWidth(), label = { Text("服务器地址") }, singleLine = true, enabled = !connectionBusy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                            Caption("修改地址前先测试新地址。账号信息会保留；如同步校验失败，请到独立的账号设置注销。")
                            SecondaryAction(if (state.testingConnection) "测试中…" else "测试连接", !connectionBusy && !state.testingConnection && state.address.isNotBlank()) { onTestConnection(state.address) }
                            ConnectionTestResult(state)
                            val addressChanged = state.address.trim() != server.url
                            PrimaryAction("保存修改", !connectionBusy && state.address.isNotBlank() && (!addressChanged || state.connectionTestAddress == state.address.trim())) { requestServerSave("server-detail") }
                            state.error?.let { ErrorNote(it) }
                        }
                        SettingsCard {
                            Caption(if (state.settings?.activeUrl == server.url) "当前同步服务器地址。" else "当前未使用此服务器地址。")
                        }
                        if (server.url != state.settings?.activeUrl) PrimaryAction("设为当前同步服务器", !connectionBusy) { pendingServer = server.url; confirmation = "server" }
                        else if (state.auth.authenticated) PrimaryAction("预览本机数据并开启同步", !connectionBusy, onPreviewMigration)
                        SecondaryAction("删除服务器", !connectionBusy) { pendingServer = server.url; page = "delete-server" }
                    }
                }
                "delete-server" -> {
                    val server = state.settings?.servers?.find { it.url == pendingServer }
                    PageTitle("删除这个服务器地址？", "仅删除连接配置，不退出账号。")
                    SettingsCard {
                        Text(server?.name ?: "地址已删除", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Caption(server?.url.orEmpty())
                        Caption("只移除连接配置，不删除账号、本机资产、用品、同步队列或服务端数据。")
                        if (server?.url == state.settings?.activeUrl && server != null) Caption("删除当前地址将停止同步；不会自动选择其他服务器。")
                        SecondaryAction("取消", enabled = !connectionBusy) { page = "server-detail" }
                        PrimaryAction("确认删除", enabled = server != null && !connectionBusy) {
                            server?.let { onDeleteServer(it.url) }; onEditServer(null); page = "server"
                        }
                    }
                }
                "auth" -> {
                    PageTitle(if (registering) "注册同步账号" else "登录燧人同步", "当前服务器")
                    val validation = accountValidation(state.authUsername, state.authPassword, if (registering) confirmPassword else null)
                    SettingsCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!registering, { registering = false; confirmPassword = ""; onResetAuthForm() }, { Text("登录") }, enabled = !connectionBusy)
                            FilterChip(registering, { registering = true; confirmPassword = ""; onResetAuthForm() }, { Text("注册") }, enabled = !connectionBusy)
                        }
                        OutlinedTextField(state.authUsername, onAuthUsernameChanged, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true, enabled = !connectionBusy)
                        OutlinedTextField(state.authPassword, onAuthPasswordChanged, Modifier.fillMaxWidth(), label = { Text("密码") }, singleLine = true, enabled = !connectionBusy, visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        if (registering) OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text("确认密码") }, singleLine = true, enabled = !connectionBusy, visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !connectionBusy) { Text(if (passwordVisible) "隐藏密码" else "显示密码") }
                        if (registering) Caption("账号为 1–100 个 UTF-8 字节；密码为 8–72 个 UTF-8 字节，中文通常占 3 字节。")
                        validation?.let { Caption(it) }
                        PrimaryAction(if (state.authBusy) "处理中…" else if (registering) "注册" else "登录",
                            enabled = !connectionBusy && validation == null && inspectedServer != null && !state.auth.authenticated,
                            onClick = { if (registering) onRegister(confirmPassword) else onLogin() })
                        listOfNotNull(state.authError, state.error, state.migrationError).distinct().forEach { ErrorNote(it) }

                    }
                    Caption("登录成功后仍需确认同步范围。不会因登录自动上传本机数据。")
                }
                "conflicts" -> {
                    PageTitle("待处理冲突", "仅暂停冲突记录，其他资产和用品继续同步。")
                    if (count == 0) SettingsCard { Text("没有待处理的冲突"); Caption("如双方修改同一条记录，会在这里保留两个版本供你选择。") }
                    state.syncStatus.conflicts.forEach { conflict -> SettingsCard { SettingsLink(conflict.local.name, conflictDescription(conflict.localDeleted, conflict.remoteDeleted, conflict.remoteUnavailable)) { conflictId = conflict.assetId; expiryConflict = false; page = "conflict" } } }
                    state.syncStatus.expiryConflicts.forEach { conflict -> SettingsCard { SettingsLink(conflict.localName, conflictDescription(conflict.localDeleted, conflict.remoteDeleted, conflict.remoteUnavailable)) { conflictId = conflict.expiryId; expiryConflict = true; page = "conflict" } } }
                }
                "conflict" -> {
                    PageTitle(a?.local?.name ?: e?.localName ?: "冲突已处理", "先看字段差异，再选择整条记录的处理方式。")
                    if (!conflictExists) PrimaryAction("返回冲突列表") { page = "conflicts" }
                    else if (unavailable) SettingsCard {
                        Text("无法读取另一端快照")
                        Caption("这是归属或权限冲突，不代表记录已删除。不会显示其他账号的内容。")
                        PrimaryAction("检查账号与服务器") { page = "server" }
                    } else {
                        ConflictVersions(a, e)
                        Caption("当前同步协议未提供可核实的双方修改时间；不按设备时间自动选择版本。")
                        SettingsCard {
                            val busy = state.conflictBusy || state.syncStatus.syncing
                            ChoiceAction(if(localDeleted) "保留本机删除" else "保留本机", "使用本机版本；若另一端再次变化，会重新提示。", !busy) { resolution = SyncConflictResolution.KeepLocal }
                            ChoiceAction(if(remoteDeleted) "保留另一端删除" else "保留另一端", "应用另一端版本，替换这条本机记录。", !busy) { resolution = SyncConflictResolution.KeepRemote }
                            ChoiceAction(if(localDeleted || remoteDeleted) "将修改版另存一份" else "两份都保留", "保留原记录的另一端版本，将本机修改另存为副本；删除冲突保留删除并另存修改。", !busy) { resolution = SyncConflictResolution.KeepBoth }
                        }
                    }
                }
                "backup" -> {
                    PageTitle("备份与恢复", "本机完整 JSON，包含归档资产和用品，不含账号凭证。")
                    SettingsCard {
                        state.localSummary?.let { Fact("资产（含归档）", "${it.assetCount} 项"); Fact("用品", "${it.expiryItemCount} 项") }
                        PrimaryAction(if(state.backupBusy) "处理中…" else "导出本机备份", !state.backupBusy, onExportBackup)
                        SecondaryAction("从文件恢复", !state.backupBusy, onImportBackup)
                    }
                    Caption("恢复前先校验文件并预览数量，确认后自动备份当前数据，再恢复；失败会保留原数据。")
                    state.backupError?.let { ErrorNote(it) }
                }
                "appearance" -> {
                    PageTitle("外观", "选择适合你的显示方式。")
                    SettingsCard { ThemeMode.entries.forEach { mode -> Choice(themeLabel(mode), "", themeMode == mode) { onThemeModeChanged(mode) } } }
                }
                "reminders" -> {
                    PageTitle("用品临期提醒", "在用品列表中提前标记需要关注的记录。")
                    SettingsCard { SoonDaysSetting(state.soonDays, onSoonDaysChanged) }
                    Caption("此设置控制应用内临期状态，不会开启系统推送通知。")
                }
                "about" -> {
                    PageTitle("燧人", "把日常工具放在一起。")
                    SettingsCard { Text("个人工具箱"); Caption("记录资产、计算持有成本，管理用品与到期时间。数据始终保存在本机，可选多端同步。") }
                }
            }
            // Errors are rendered once at the action/status card that owns them.
            if (page == "conflict") state.error?.let { ErrorNote(it) }
            state.notice?.let { Caption(it) }
            Spacer(Modifier.height(108.dp))
        }
    }
    state.migrationPreview?.let { preview ->
        AlertDialog(onDismissRequest = { if(!state.migrationBusy) onCancelMigration() }, title = { Text("先确认要同步什么") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("资产（含归档）", "${preview.localAssetCount} 项")
                Fact("用品", "${preview.localExpiryCount} 项")
                Fact("同步账号", state.auth.username)
                Fact("服务器", state.settings?.activeUrl.orEmpty())
                Caption("开启同步前先自动备份。本机数据将同步到上方目标；更换服务器后重新建立同步基线，同 ID 差异交由你处理。地址变更不清除账号信息；新服务器校验失败时请先注销账号。")
                state.migrationError?.let { ErrorNote(it) }
            }
        }, confirmButton = { TextButton(onClick = { onConfirmMigration(); page = "sync" }, enabled = !state.migrationBusy) { Text(if(state.migrationBusy) "同步中…" else "确认并开启同步") } }, dismissButton = { TextButton(onClick = onCancelMigration, enabled = !state.migrationBusy) { Text("取消") } })
    }
    state.backupSummary?.let { summary ->
        AlertDialog(onDismissRequest = { if(!state.backupBusy) onCancelRestore() }, title = { Text("恢复预览") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("备份中的资产", "${summary.assetCount} 项")
                Fact("备份中的用品", "${summary.expiryItemCount} 项")
                Caption("文件校验通过。确认后将替换本机当前数据，恢复前会自动备份。")
                state.backupError?.let { ErrorNote(it) }
            }
        }, confirmButton = { TextButton(onClick = { confirmation = "restore" }, enabled = !state.backupBusy) { Text(if(state.backupBusy) "恢复中…" else "继续恢复") } }, dismissButton = { TextButton(onClick = onCancelRestore, enabled = !state.backupBusy) { Text("取消") } })
    }
    resolution?.let { choice -> AlertDialog(onDismissRequest = { resolution = null }, title = { Text("确认处理此记录？") }, text = { Text(if(choice == SyncConflictResolution.KeepBoth) "将创建修改版副本，保留两份内容。" else "未选择的版本将被替换；此操作只影响当前记录。") }, confirmButton = { TextButton(onClick = { onResolveConflict(conflictId, expiryConflict, choice); resolution = null }, enabled = conflictExists && !state.conflictBusy && !unavailable) { Text("确认处理") } }, dismissButton = { TextButton(onClick = { resolution = null }) { Text("取消") } }) }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when(action) { "restore" -> "替换本机数据？"; "pause" -> "暂停同步？"; "logout" -> "退出同步账号？"; "save-server" -> "切换服务器地址？"; else -> "切换同步连接？" }) }, text = { Text(when(action) {
        "restore" -> "会先自动备份当前资产和用品，再恢复所选文件。此操作会替换本机记录。"
        "save-server" -> "服务器地址将变更，账号信息和登录凭证会保留；网络同步会暂时暂停。本机资产和用品不受影响。重新开启同步时若账号校验失败，请手动注销后再登录。"
        "server" -> "切换后网络同步会暂时暂停；账号信息和登录凭证会保留，不会自动发给新服务器。重新开启同步时若账号校验失败，请先手动注销后再登录。"
        else -> "网络同步将停止，本机数据与待发送修改保留。登录后可预览并开启同步到新目标。"
    }) }, confirmButton = { TextButton(onClick = {
        when(action) { "restore" -> onConfirmRestore(); "pause" -> onUseLocal(); "logout" -> onLogout(); "save-server" -> onSave(); "server" -> pendingServer?.let(onSelect) }
        confirmation = null
    }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } }) }
}

@Composable
internal fun SoonDaysSetting(value: Int, onValueChanged: (Int) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { next -> if(next.length <= 2 && next.all(Char::isDigit)) { text = next; next.toIntOrNull()?.takeIf { it in 1..30 }?.let(onValueChanged) } }, Modifier.fillMaxWidth(), label = { Text("提前天数（1–30）") }, supportingText = { Text("当前设置：提前 $value 天标记临期") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = text.toIntOrNull() !in 1..30)
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() { SuirenXTheme { BackendSettingsScreen(BackendUiState(mode = StorageMode.Local), {}, {}, {}, {}, {}, {}, {}) } }
