package io.suirenx.feature.assets

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.io.File
import java.time.LocalDate
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun englishNarrowLargeTextUsesPluralResourcesAndKeepsUserContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.ENGLISH))
        }
        val englishContext = context.createConfigurationContext(configuration)
        val userName = "copy_active"
        val asset = Asset("localization-test", userName, 12345, LocalDate.now(), AssetStatus.Active,
            "", 1, 12345, iconKey = "phone", salePriceCents = 0)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides englishContext, LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                SuirenXTheme {
                    AssetsScreen(state = AssetsUiState(assets = listOf(asset), isLoading = false),
                        onFilterSelected = {}, onAssetClick = {}, onRefresh = {}, modifier = Modifier.width(360.dp))
                }
            }
        }
        compose.onNodeWithText(userName).assertIsDisplayed()
        val heldDays = compose.onNodeWithText("1 day held", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1 days held", substring = true).assertDoesNotExist()
        val layout = mutableListOf<TextLayoutResult>()
        heldDays.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val result = layout.single()
        File(context.cacheDir, "asset-en-large.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        org.junit.Assert.assertFalse("Purchase price and held days must be fully visible: size=${result.size}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, lines=${result.lineCount}, font=${result.layoutInput.style.fontSize}", result.hasVisualOverflow)
        compose.onNodeWithText("Sold for", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Filter tags").assertIsDisplayed()
    }
}
