package io.suirenx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.suirenx.core.ui.theme.SuirenXTheme
import io.suirenx.feature.assets.AssetsRoute
import io.suirenx.feature.settings.BackendGate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuirenXTheme {
                BackendGate(content = { onOpenSettings ->
                    AssetsRoute(onOpenSettings = onOpenSettings)
                })
            }
        }
    }
}

