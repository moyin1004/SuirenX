package io.suirenx.app

import io.suirenx.core.ui.R as CopyR
import io.suirenx.core.ui.localization.uiText
import io.suirenx.core.ui.localization.uiQuantity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import io.suirenx.core.model.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material3.Icon
import io.suirenx.core.ui.icon.SuirenIcons
import io.suirenx.core.ui.icon.MaterialSymbol
import io.suirenx.core.ui.theme.SuirenXTheme
import io.suirenx.feature.assets.AssetDetailRoute
import io.suirenx.feature.assets.AssetFormRoute
import io.suirenx.feature.assets.AssetsRoute
import io.suirenx.feature.assets.AssetsViewModel
import io.suirenx.feature.settings.BackendGate
import io.suirenx.feature.settings.SettingsRoute
import io.suirenx.feature.settings.SettingsEntry
import io.suirenx.feature.settings.ThemeSettingsViewModel
import io.suirenx.feature.tools.ToolsRoute
import io.suirenx.feature.expiry.ExpiryRoute
import io.suirenx.feature.expiry.ExpiryViewModel

// Navigation is owned by the app module; feature modules never reference each other.
private object Routes {
    const val MAIN = "main"
    const val ASSET_DETAIL = "assets/{id}"
    const val ASSET_CREATE = "new-asset"
    const val ASSET_EDIT = "assets/{id}/edit"
    const val EXPIRY = "expiry"
    const val SYNC_STATUS = "settings/sync"
    const val SYNC_CONFLICTS = "settings/conflicts"

    fun assetDetail(id: String) = "assets/$id"
    fun assetEdit(id: String) = "assets/$id/edit"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(io.suirenx.core.ui.localization.LanguageSettings.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeSettingsViewModel = hiltViewModel()
            val theme by themeViewModel.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SuirenXTheme(theme) { SuirenXApp() }
        }
    }
}

@Composable
private fun SuirenXApp() {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Overview) }
    val tabStateHolder = rememberSaveableStateHolder()
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { ExitTransition.None },
        sizeTransform = { null },
    ) {
        composable(Routes.MAIN) {
            BackendGate {
                MainScaffold(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    tabStateHolder = tabStateHolder,
                    onAssetClick = { id -> navController.navigate(Routes.assetDetail(id)) },
                    onAddAsset = { navController.navigate(Routes.ASSET_CREATE) },
                    onOpenExpiry = { navController.navigate(Routes.EXPIRY) },
                    onOpenSync = { conflicts ->
                        navController.navigate(if (conflicts) Routes.SYNC_CONFLICTS else Routes.SYNC_STATUS) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        listOf(Routes.SYNC_STATUS to SettingsEntry.Sync, Routes.SYNC_CONFLICTS to SettingsEntry.Conflicts).forEach { (route, entry) ->
            composable(route) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SettingsRoute(entry = entry, onExit = { navController.popBackStack() })
                    FloatingTabBar(
                        currentRoute = HomeTab.Settings.route,
                        onTabSelected = { tab ->
                            if (navController.currentDestination?.route == route) navController.popBackStack()
                            selectedTab = tab
                        },
                        onAddAsset = { navController.navigate(Routes.ASSET_CREATE) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        composable(
            route = Routes.ASSET_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            AssetDetailRoute(
                assetId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.assetEdit(id)) },
            )
        }
        composable(
            route = Routes.ASSET_CREATE,
        ) {
            AssetFormRoute(onClose = { navController.popBackStack() })
        }
        composable(
            route = Routes.ASSET_EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            AssetFormRoute(onClose = { navController.popBackStack() })
        }
        composable(Routes.EXPIRY) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ExpiryRoute(onBack = { navController.popBackStack() })
                FloatingTabBar(
                    currentRoute = HomeTab.Tools.route,
                    onTabSelected = { tab ->
                        // A tab tap may arrive while system-back is finishing.
                        // Never pop the main page a second time.
                        if (navController.currentDestination?.route == Routes.EXPIRY) {
                            navController.popBackStack()
                        }
                        selectedTab = tab
                    },
                    onAddAsset = { navController.navigate(Routes.ASSET_CREATE) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private enum class HomeTab(
    val route: String,
    val label: String,
    // Material Symbols Rounded codepoints; see core/ui MaterialSymbol.
    val glyph: String,
) {
    Overview("tab/overview", "copy_overview", "\uE9B2"),
    Assets("tab/assets", "copy_assets", "\uE326"), // home
    Tools("tab/tools", "copy_tools", "\uE4FB"), // auto_graph
    Settings("tab/settings", "copy_settings", "\uE8B8"), // settings
}

@Composable
private fun MainScaffold(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    tabStateHolder: SaveableStateHolder,
    onAssetClick: (String) -> Unit,
    onAddAsset: () -> Unit,
    onOpenExpiry: () -> Unit,
    onOpenSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Main tabs switch in the same composition as their selected indicator.
    // Their data ViewModels belong to the main destination, not individual tabs.
    val assetsViewModel: AssetsViewModel = hiltViewModel()
    val expiryViewModel: ExpiryViewModel = hiltViewModel()
    BackHandler(selectedTab != HomeTab.Overview) { onTabSelected(HomeTab.Overview) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        tabStateHolder.SaveableStateProvider(selectedTab.route) {
            when (selectedTab) {
                HomeTab.Overview -> OverviewRoute(
                    assetsViewModel = assetsViewModel,
                    expiryViewModel = expiryViewModel,
                    onAssets = { onTabSelected(HomeTab.Assets) },
                    onSupplies = onOpenExpiry,
                    onSync = onOpenSync,
                )
                HomeTab.Assets -> AssetsRoute(onAssetClick = onAssetClick, viewModel = assetsViewModel)
                HomeTab.Tools -> {
                    val expiry by expiryViewModel.uiState.collectAsStateWithLifecycle()
                    val pendingCount = if (expiry.loading || expiry.error != null) null else expiry.items.count {
                        it.archivedAt == null && it.bucket(java.time.LocalDate.now(), expiry.soonDays) in setOf(
                            io.suirenx.core.model.ExpiryBucket.Expired,
                            io.suirenx.core.model.ExpiryBucket.DueToday,
                            io.suirenx.core.model.ExpiryBucket.ExpiringSoon,
                        )
                    }
                    ToolsRoute(onOpenExpiry = onOpenExpiry, pendingCount = pendingCount)
                }
                HomeTab.Settings -> SettingsRoute()
            }
        }
        FloatingTabBar(
            currentRoute = selectedTab.route,
            onTabSelected = onTabSelected,
            onAddAsset = onAddAsset,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun FloatingTabBar(
    currentRoute: String?,
    onTabSelected: (HomeTab) -> Unit,
    onAddAsset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 14.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (currentRoute == HomeTab.Assets.route) {
            Surface(onClick = onAddAsset, shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
                shadowElevation = 8.dp, modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(glyph = "\uE145", contentDescription = uiText(CopyR.string.copy_add_asset), size = 26.dp)
                }
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HomeTab.entries.forEach { tab ->
                    TabItem(tab, currentRoute == tab.route, { onTabSelected(tab) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent)
            .clickable(onClick = onClick)
            .height(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                )
                .size(width = 44.dp, height = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Filled glyph for the selected tab; compensate for the gear's
            // optical size to keep the four tabs visually balanced.
            if (tab == HomeTab.Assets) {
                Icon(SuirenIcons.AssetBox, contentDescription = uiText(tab.label), tint = contentColor, modifier = Modifier.size(20.dp))
            } else MaterialSymbol(
                glyph = tab.glyph,
                contentDescription = uiText(tab.label),
                tint = contentColor,
                filled = selected,
                size = 20.dp,
                modifier = Modifier
                    .scale(if (tab == HomeTab.Settings) 1.1f else 1f),
            )
        }
        Text(text = uiText(tab.label), fontSize = 10.sp, lineHeight = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = contentColor)
    }
}

@Composable
private fun PlaceholderTab(
    glyph: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MaterialSymbol(
            glyph = glyph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            size = 56.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(hint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
