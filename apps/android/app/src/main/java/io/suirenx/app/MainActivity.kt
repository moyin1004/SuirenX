package io.suirenx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import io.suirenx.core.ui.theme.SuirenXTheme
import io.suirenx.feature.assets.AssetDetailRoute
import io.suirenx.feature.assets.AssetsRoute
import io.suirenx.feature.settings.BackendGate

// Navigation is owned by the app module; feature modules never reference each other.
private object Routes {
    const val ASSETS = "assets"
    const val ASSET_DETAIL = "assets/{id}"

    fun assetDetail(id: String) = "assets/$id"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuirenXTheme {
                SuirenXNavHost()
            }
        }
    }
}

@Composable
private fun SuirenXNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.ASSETS) {
        composable(Routes.ASSETS) {
            BackendGate(content = { onOpenSettings ->
                AssetsRoute(
                    onOpenSettings = onOpenSettings,
                    onAssetClick = { id -> navController.navigate(Routes.assetDetail(id)) },
                )
            })
        }
        composable(
            route = Routes.ASSET_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            AssetDetailRoute(
                assetId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
