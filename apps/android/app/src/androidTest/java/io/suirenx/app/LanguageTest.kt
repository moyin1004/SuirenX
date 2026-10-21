package io.suirenx.app

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises framework recreation against the real app, without saving business records. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LanguageTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var original = ""

    @Before fun startInEnglish() {
        compose.activityRule.scenario.onActivity {
            original = it.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        }
        waitForText("Settings")
    }

    @After fun restoreLanguage() {
        compose.activityRule.scenario.onActivity {
            it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(original)
        }
    }

    @Test fun nativePickerSwitchesBothWaysAndRetainsItsPage() {
        compose.onAllNodesWithText("Settings", useUnmergedTree = true).onLast().performClick()
        compose.onNodeWithText("Language").performScrollTo().performClick()
        compose.onNodeWithText("English").assertIsDisplayed()
        capture("language-en.png")
        compose.onNodeWithText("简体中文").performClick()
        waitForText("当前显示语言：简体中文")
        compose.onNodeWithText("语言").assertIsDisplayed()
        capture("language-zh.png")
        compose.onNodeWithText("English").performClick()
        waitForText("Current display language: English")
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Follow system").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            org.junit.Assert.assertTrue(it.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)
        }
    }

    @Test fun externalLanguageChangePreservesUnsubmittedAssetDraft() {
        compose.onAllNodesWithText("Assets", useUnmergedTree = true).onLast().performClick()
        compose.onNodeWithContentDescription("Add asset").performClick()
        compose.onNode(hasSetTextAction() and hasText("Asset name")).performTextInput("Travel laptop · error_auth_invalid_credentials")
        compose.onNode(hasSetTextAction() and hasText("Purchase price (CNY)")).performTextInput("123.40")
        capture("draft-en.png")
        compose.activityRule.scenario.onActivity {
            it.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("zh-Hans")
        }
        waitForText("资产名称")
        compose.onNode(hasSetTextAction() and hasText("资产名称")).assertTextContains("Travel laptop · error_auth_invalid_credentials")
        compose.onNode(hasSetTextAction() and hasText("购买金额（元）")).assertTextContains("123.40")
        capture("draft-zh.png")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) {
            try {
                compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            } catch (error: IllegalStateException) {
                // Framework locale recreation can temporarily remove the root on physical devices.
                if (error.message?.contains("No compose hierarchies found") == true) false else throw error
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, name).outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
