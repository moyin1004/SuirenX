package io.suirenx.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.model.BackendServer
import io.suirenx.core.model.BackendSettings
import io.suirenx.core.model.ThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerSettingsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val url = "https://one.test/"
    private val state = mutableStateOf(BackendUiState())
    private val theme = mutableStateOf(ThemeMode.Light)
    private var saves = 0
    private var selections = 0
    private var deletions = 0

    private fun launch(saved: Boolean = true) {
        state.value = BackendUiState(settings = if (saved) BackendSettings(listOf(BackendServer(url, "家庭服务器")), url) else BackendSettings())
        rule.setContent {
            io.suirenx.core.ui.theme.SuirenXTheme(theme.value) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    BackendSettingsScreen(state.value,
                        onAddressChanged = { state.value = state.value.copy(address = it, connectionTestAddress = null, connectionTest = null) },
                        onNameChanged = { state.value = state.value.copy(name = it) }, onSave = { saves++ },
                        onSelect = { selections++; state.value = state.value.copy(settings = state.value.settings?.copy(activeUrl = it)) },
                        onRetry = {}, onUseLocal = {}, onUseRemote = {},
                        onOpenServer = { val s = state.value.settings!!.servers.first { s -> s.url == it }; state.value = state.value.copy(inspectedServerUrl = it, editingServerUrl = it, address = it, name = s.name) },
                        onEditServer = { state.value = state.value.copy(address = "", name = "", editingServerUrl = null, connectionTestAddress = null, connectionTest = null, error = null) },
                        onTestConnection = { state.value = state.value.copy(testingConnection = true) },
                        onDeleteServer = { deletions++; state.value = state.value.copy(settings = BackendSettings()) },
                    )
                }
            }
        }
        rule.onNodeWithText("服务器地址").performScrollTo().performClick()
    }

    @Test fun listAndAddSheetSupportCancelWithoutSaving() {
        launch()
        rule.onNodeWithText("当前选择").assertIsDisplayed()
        rule.onNodeWithTag("server.address").assertDoesNotExist()
        capture("server-list-light.png")
        rule.runOnIdle { theme.value = ThemeMode.Dark }
        capture("server-list-dark.png")
        rule.onNodeWithText("添加服务器").performClick()
        rule.onNodeWithText("保存").assertIsNotEnabled()
        capture("server-add-dark.png")
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithTag("server.address").assertDoesNotExist()
        assertEquals(0, saves)
        assertEquals(0, selections)
    }

    @Test fun addRequiresExactTestAndKeepsFailedSaveOpen() {
        launch(false)
        rule.onNodeWithText("尚无服务器").assertIsDisplayed()
        rule.onNodeWithText("添加服务器").performClick()
        rule.onNodeWithTag("server.address").performTextInput("https://new.test/")
        rule.onNodeWithText("保存").assertIsNotEnabled()
        rule.onNodeWithText("测试连接").performScrollTo().performClick()
        rule.onNodeWithText("保存").assertIsNotEnabled()
        rule.onNodeWithText("取消").assertIsNotEnabled()
        rule.onNodeWithTag("server.address").assertIsNotEnabled()
        rule.runOnIdle { state.value = state.value.copy(testingConnection = false, connectionTestAddress = state.value.address, connectionTest = "连接成功，尚未保存或开启同步。") }
        rule.onNodeWithText("保存").assertIsEnabled()
        rule.onNodeWithTag("server.address").performTextReplacement("https://changed.test/")
        rule.onNodeWithText("保存").assertIsNotEnabled()
        rule.runOnIdle { state.value = state.value.copy(connectionTestFailed = true, connectionTest = "连接测试失败") }
        rule.onNodeWithText("连接测试失败").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("测试连接").performClick()
        rule.runOnIdle { state.value = state.value.copy(testingConnection = false, connectionTestFailed = false, connectionTestAddress = state.value.address) }
        rule.onNodeWithText("保存").performScrollTo().performClick()
        rule.runOnIdle { state.value = state.value.copy(error = "保存失败，请重试") }
        rule.onNodeWithText("保存失败，请重试").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("server.address").assertTextContains("https://changed.test/")
        rule.onNodeWithText("保存").performScrollTo().performClick()
        rule.runOnIdle { state.value = state.value.copy(settings = BackendSettings(listOf(BackendServer(state.value.address, "新地址"))), serverSavedVersion = 1, error = null) }
        rule.onNodeWithTag("server.address").assertDoesNotExist()
        rule.onNodeWithText("新地址").assertIsDisplayed()
        rule.onNodeWithText("当前选择").assertDoesNotExist()
        assertEquals(2, saves)
        assertEquals(0, selections)
    }

    @Test fun detailRenameReturnsToListAndDeleteRequiresConfirmation() {
        launch()
        rule.onNodeWithText("家庭服务器").performClick()
        rule.onNodeWithText("保存").assertIsNotEnabled()
        rule.onNodeWithText("已选择此服务器").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("server.name").performScrollTo().performTextReplacement("新名称")
        rule.onNodeWithText("保存").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { state.value = state.value.copy(settings = BackendSettings(listOf(BackendServer(url, "新名称")), url), serverSavedVersion = 1) }
        rule.onNodeWithTag("server.name").assertDoesNotExist()
        rule.onNodeWithText("新名称").performClick()
        rule.onNodeWithText("删除地址").performScrollTo().performClick()
        rule.onNodeWithText("删除此地址？").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        assertEquals(0, deletions)
        rule.onNodeWithText("删除地址").performClick()
        rule.onNodeWithText("确认删除地址").performClick()
        rule.onNodeWithText("尚无服务器").assertIsDisplayed()
        assertEquals(1, deletions)
    }

    @Test fun selectSavedServerDirectlyFromDetail() {
        launch()
        rule.runOnIdle { state.value = state.value.copy(settings = state.value.settings?.copy(activeUrl = null)) }
        rule.onNodeWithText("家庭服务器").performClick()
        capture("server-detail-light.png")
        rule.runOnIdle { theme.value = ThemeMode.Dark }
        capture("server-detail-dark.png")
        rule.onNodeWithText("选择此服务器").performScrollTo().performClick()
        rule.onNodeWithText("已选择此服务器").assertIsNotEnabled()
        assertEquals(1, selections)
        assertEquals(0, saves)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val node = if (name.startsWith("server-add")) rule.onNodeWithTag("server.add") else rule.onRoot()
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
