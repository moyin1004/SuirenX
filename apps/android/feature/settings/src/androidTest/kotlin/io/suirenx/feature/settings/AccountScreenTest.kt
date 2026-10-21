package io.suirenx.feature.settings

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.model.BackendServer
import io.suirenx.core.model.BackendSettings
import io.suirenx.core.model.ThemeMode
import io.suirenx.core.ui.theme.SuirenXTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun developmentTraceIsHiddenForReleasePackages() {
        val info = mutableStateOf(InstalledBuildInfo("development", "a1b2c3d4567890", true, "2026-10-18T08:30:00Z"))
        composeRule.setContent {
            SuirenXTheme {
                androidx.compose.foundation.layout.Column { DevelopmentBuildRows(info.value) }
            }
        }
        composeRule.onNodeWithTag("about.code").assertTextEquals("a1b2c3d-dirty")
        composeRule.onNodeWithTag("about.builtAt").assertTextEquals(info.value.localTime)
        composeRule.runOnIdle { info.value = info.value.copy(channel = "release", dirty = false) }
        composeRule.onNodeWithTag("about.code").assertDoesNotExist()
        composeRule.onNodeWithTag("about.builtAt").assertDoesNotExist()
        composeRule.onNodeWithText("代码").assertDoesNotExist()
        composeRule.onNodeWithText("打包时间").assertDoesNotExist()
    }

    @Test fun aboutShowsInstalledPackageVersionAndBuild() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val theme = mutableStateOf(ThemeMode.Light)
        composeRule.setContent {
            SuirenXTheme(theme.value) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    BackendSettingsScreen(state = BackendUiState(),
                        onAddressChanged = {}, onNameChanged = {}, onSave = {}, onSelect = {},
                        onRetry = {}, onUseLocal = {}, onUseRemote = {})
                }
            }
        }
        composeRule.onNodeWithText("关于").performScrollTo().performClick()
        composeRule.onNodeWithText("版本号").assertIsDisplayed()
        composeRule.onNodeWithText("Build 号").assertIsDisplayed()
        composeRule.onNodeWithTag("about.version").assertTextEquals(installed.versionName ?: "未知")
        @Suppress("DEPRECATION")
        val build = if (android.os.Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        composeRule.onNodeWithTag("about.build").assertTextEquals(build.toString())
        capture("about-light.png")
        composeRule.runOnIdle { theme.value = ThemeMode.Dark }
        capture("about-dark.png")
    }

    @Test fun passwordToggleAndRegistrationRulesPreserveInput() {
        val url = "https://example.test/"
        val state = mutableStateOf(BackendUiState(
            settings = BackendSettings(listOf(BackendServer(url, "测试服务器")), url),
            inspectedServerUrl = url,
        ))
        var keyboard: SoftwareKeyboardController? = null
        val theme = mutableStateOf(ThemeMode.Light)
        composeRule.setContent {
            keyboard = LocalSoftwareKeyboardController.current
            SuirenXTheme(theme.value) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    BackendSettingsScreen(state = state.value,
                        onAddressChanged = {}, onNameChanged = {}, onSave = {}, onSelect = {},
                        onRetry = {}, onUseLocal = {}, onUseRemote = {},
                        onAuthUsernameChanged = { state.value = state.value.copy(authUsername = it) },
                        onAuthPasswordChanged = { state.value = state.value.copy(authPassword = it) },
                        onAuthConfirmationChanged = { state.value = state.value.copy(authConfirmation = it) },
                        onResetAuthForm = { state.value = state.value.copy(authPassword = "", authConfirmation = "") },
                    )
                }
            }
        }
        capture("settings-home-light.png")
        composeRule.runOnIdle { theme.value = ThemeMode.Dark }
        capture("settings-home-dark.png")
        composeRule.runOnIdle { theme.value = ThemeMode.Light }
        composeRule.onAllNodesWithText("账号").onLast().performClick()
        composeRule.onNodeWithText("登录 / 注册").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("账号")).performTextInput("tester")
        val password = hasSetTextAction() and hasText("密码")
        composeRule.onNode(password).performTextInput("旧密码")
        composeRule.onAllNodesWithText("登录").onLast().assertIsEnabled()
        composeRule.onNodeWithContentDescription("显示密码").performClick()
        composeRule.onNode(password).assertTextContains("旧密码")
        composeRule.onNodeWithContentDescription("隐藏密码").performClick()
        composeRule.onNode(password).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithText("注册").performClick()
        composeRule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(
            io.suirenx.core.ui.R.string.error_auth_password_rules,
        )).assertIsDisplayed()
        composeRule.onNode(password).performTextInput("12345678")
        composeRule.onNode(hasSetTextAction() and hasText("确认密码")).performTextInput("12345678")
        composeRule.onAllNodesWithText("注册").onLast().assertIsEnabled()
        composeRule.onAllNodesWithContentDescription("显示密码").onFirst().performClick()
        composeRule.onNode(password).assertTextContains("12345678")
        composeRule.onNode(hasSetTextAction() and hasText("确认密码")).assertTextContains("12345678")
        composeRule.onAllNodesWithContentDescription("隐藏密码").onLast().performClick()
        composeRule.onNode(password).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNode(password).performTextReplacement("中文密码abcd")
        composeRule.onAllNodesWithText("注册").onLast().assertIsNotEnabled()
        composeRule.onNode(password).performTextReplacement("12345678")
        composeRule.onAllNodesWithText("注册").onLast().assertIsEnabled()
        composeRule.runOnIdle { keyboard?.hide() }
        composeRule.waitForIdle()
        // Let the platform IME resize finish before capturing the complete form.
        Thread.sleep(600)
        capture("account-registration.png")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
