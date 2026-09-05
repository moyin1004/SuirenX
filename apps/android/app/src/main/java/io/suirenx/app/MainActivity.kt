package io.suirenx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import io.suirenx.core.ui.theme.SuirenXTheme
import io.suirenx.feature.assets.AssetDetailRoute
import io.suirenx.feature.assets.AssetFormRoute
import io.suirenx.feature.assets.AssetsRoute
import io.suirenx.feature.settings.BackendGate
import io.suirenx.feature.settings.SettingsRoute

// Navigation is owned by the app module; feature modules never reference each other.
private object Routes {
    const val MAIN = "main"
    const val ASSET_DETAIL = "assets/{id}"
    const val ASSET_CREATE = "new-asset"
    const val ASSET_EDIT = "assets/{id}/edit"

    fun assetDetail(id: String) = "assets/$id"
    fun assetEdit(id: String) = "assets/$id/edit"
}

// Detail and form pages slide up over the tab scaffold like the 有数 app;
// the scaffold underneath stays put and is revealed again on the way back.
private const val SLIDE_DURATION = 350

private fun slideUpEnter(): EnterTransition =
    slideInVertically(animationSpec = tween(SLIDE_DURATION)) { it } + fadeIn(animationSpec = tween(SLIDE_DURATION))

private fun slideDownExit(): ExitTransition =
    slideOutVertically(animationSpec = tween(SLIDE_DURATION)) { it } + fadeOut(animationSpec = tween(300))

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuirenXTheme {
                SuirenXApp()
            }
        }
    }
}

@Composable
private fun SuirenXApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
    ) {
        composable(Routes.MAIN) {
            BackendGate {
                MainScaffold(
                    onAssetClick = { id -> navController.navigate(Routes.assetDetail(id)) },
                    onAddAsset = { navController.navigate(Routes.ASSET_CREATE) },
                )
            }
        }
        composable(
            route = Routes.ASSET_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = { slideUpEnter() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideDownExit() },
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
            enterTransition = { slideUpEnter() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideDownExit() },
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
            enterTransition = { slideUpEnter() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { slideDownExit() },
        ) {
            AssetFormRoute(onClose = { navController.popBackStack() })
        }
    }
}

private enum class HomeTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Assets("tab/assets", "资产", Icons.Filled.Home),
    Wishes("tab/wishes", "心愿", Icons.Outlined.FavoriteBorder),
    Trends("tab/trends", "趋势", Icons.Outlined.AutoGraph),
    Settings("tab/settings", "设置", Icons.Outlined.Settings),
}

@Composable
private fun MainScaffold(
    onAssetClick: (String) -> Unit,
    onAddAsset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabNavController = rememberNavController()
    val currentBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Box(modifier = modifier.fillMaxSize()) {
        // Each tab is a separate navigation destination; saveState/restoreState
        // keeps every tab's ViewModel and scroll position alive across switches.
        NavHost(
            navController = tabNavController,
            startDestination = HomeTab.Assets.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(HomeTab.Assets.route) {
                AssetsRoute(onAssetClick = onAssetClick)
            }
            composable(HomeTab.Wishes.route) {
                PlaceholderTab(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "心愿清单",
                    hint = "想入手的东西，先记在这里",
                )
            }
            composable(HomeTab.Trends.route) {
                PlaceholderTab(
                    icon = Icons.Outlined.AutoGraph,
                    title = "趋势",
                    hint = "资产变化趋势，敬请期待",
                )
            }
            composable(HomeTab.Settings.route) {
                SettingsRoute()
            }
        }
        FloatingTabBar(
            currentRoute = currentRoute,
            onTabSelected = { tab ->
                tabNavController.navigate(tab.route) {
                    popUpTo(tabNavController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }
        Surface(
            onClick = onAddAsset,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 10.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新增资产",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun TabItem(tab: HomeTab, selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(text = tab.label, fontSize = 11.sp, color = contentColor)
    }
}

@Composable
private fun PlaceholderTab(
    icon: ImageVector,
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(hint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
