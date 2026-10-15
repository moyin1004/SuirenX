package io.suirenx.feature.assets

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.suirenx.core.ui.theme.SuirenXTheme
import io.suirenx.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AssetIconPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionIsCommittedOnlyOnConfirmAndCategoriesKeepDraft() {
        val selectedKey = mutableStateOf("item")
        val isOpen = mutableStateOf(true)
        composeRule.setContent {
            SuirenXTheme {
                Text("原图标：${selectedKey.value}")
                if (isOpen.value) {
                    AssetIconPicker(
                        selectedKey = selectedKey.value,
                        onSelected = { selectedKey.value = it; isOpen.value = false },
                        onDismiss = { isOpen.value = false },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("物品").assertIsSelected()
        val itemBounds = composeRule.onNodeWithContentDescription("物品").fetchSemanticsNode().boundsInRoot
        val desktopBounds = composeRule.onNodeWithContentDescription("台式电脑").fetchSemanticsNode().boundsInRoot
        assertEquals(itemBounds.top, desktopBounds.top, 0.5f)
        assertTrue(itemBounds.left < desktopBounds.left)
        capture("asset-icons-default-item-light.png")
        composeRule.onNodeWithContentDescription("耳机").performClick()
        composeRule.onNodeWithContentDescription("耳机").assertIsSelected()
        composeRule.runOnIdle { assertEquals("item", selectedKey.value) }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals("item", selectedKey.value); isOpen.value = true }
        composeRule.onNodeWithContentDescription("物品").assertIsSelected()
        composeRule.onNodeWithText("生活").performClick()
        composeRule.onNodeWithContentDescription("物品").assertIsSelected()
        val lifeItem = composeRule.onNodeWithContentDescription("物品").fetchSemanticsNode().boundsInRoot
        val lifeBook = composeRule.onNodeWithContentDescription("书籍").fetchSemanticsNode().boundsInRoot
        assertEquals(lifeItem.top, lifeBook.top, 0.5f)
        assertTrue(lifeItem.left < lifeBook.left)
        composeRule.onNodeWithContentDescription("笔记本").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("书籍").performClick()
        composeRule.onNodeWithText("全部").performClick()
        composeRule.onNodeWithContentDescription("书籍").assertIsSelected()
        capture("asset-icons-compact-light.png")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle { assertEquals("book", selectedKey.value) }
        composeRule.onNodeWithText("原图标：book").assertIsDisplayed()
    }

    @Test
    fun tabletHasItsOwnImageAndNewIconsSurviveCategorySwitchesAndConfirm() {
        assertNotEquals(assetIconOption("phone").image, assetIconOption("tablet").image)
        val selectedKey = mutableStateOf("tablet")
        val isOpen = mutableStateOf(true)
        composeRule.setContent {
            SuirenXTheme {
                Text("原图标：${selectedKey.value}")
                if (isOpen.value) {
                    AssetIconPicker(
                        selectedKey = selectedKey.value,
                        onSelected = { selectedKey.value = it; isOpen.value = false },
                        onDismiss = { isOpen.value = false },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("平板").assertIsSelected()
        capture("asset-icons-expanded-light.png")
        composeRule.onNodeWithText("数码").performClick()
        composeRule.onNodeWithContentDescription("打印机").performClick()
        composeRule.onNodeWithText("生活").performClick()
        composeRule.onNodeWithContentDescription("扳手").performClick()
        composeRule.onNodeWithText("出行").performClick()
        composeRule.onNodeWithContentDescription("摩托车").performClick()
        composeRule.onNodeWithText("全部").performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("摩托车"))
        composeRule.onNodeWithContentDescription("摩托车").assertIsSelected()
        composeRule.runOnIdle { assertEquals("tablet", selectedKey.value) }
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle { assertEquals("motorcycle", selectedKey.value); isOpen.value = true }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("摩托车"))
        composeRule.onNodeWithContentDescription("摩托车").assertIsSelected()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("原图标：motorcycle").assertIsDisplayed()
    }

    @Test
    fun electronicsIconsAreReachableAndConfirmationSurvivesScrollingAndReopen() {
        val selectedKey = mutableStateOf("item")
        val isOpen = mutableStateOf(true)
        composeRule.setContent {
            SuirenXTheme {
                Text("原图标：${selectedKey.value}")
                if (isOpen.value) {
                    AssetIconPicker(
                        selectedKey = selectedKey.value,
                        onSelected = { selectedKey.value = it; isOpen.value = false },
                        onDismiss = { isOpen.value = false },
                    )
                }
            }
        }
        composeRule.onNodeWithText("数码").performClick()
        for (label in listOf("显示器", "电源", "硬盘", "路由器", "充电器", "充电宝", "插线板", "投影仪", "NAS", "迷你主机", "扩展坞")) {
            composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(label))
            composeRule.onNodeWithContentDescription(label).performClick().assertIsSelected()
        }
        capture("asset-icons-electronics-digital.png")
        composeRule.onNodeWithText("生活").performClick()
        for (label in listOf("空气净化器", "扫地机器人", "风扇", "加湿器", "吹风机")) {
            composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(label))
            composeRule.onNodeWithContentDescription(label).performClick().assertIsSelected()
        }
        composeRule.onNodeWithContentDescription("扫地机器人").performClick()
        capture("asset-icons-electronics-appliances.png")
        composeRule.onNodeWithText("全部").performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("扫地机器人"))
        composeRule.onNodeWithContentDescription("扫地机器人").assertIsSelected()
        composeRule.runOnIdle { assertEquals("item", selectedKey.value) }
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle { assertEquals("robot_vacuum", selectedKey.value); isOpen.value = true }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("扫地机器人"))
        composeRule.onNodeWithContentDescription("扫地机器人").assertIsSelected()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("原图标：robot_vacuum").assertIsDisplayed()
    }

    @Test
    fun unknownKeySurvivesConfirmAndBackDismissesWithoutClosingForm() {
        val selectedKey = mutableStateOf("future-icon")
        val isOpen = mutableStateOf(true)
        var formBacks = 0
        composeRule.setContent {
            SuirenXTheme(themeMode = ThemeMode.Dark) {
                BackHandler { formBacks++ }
                AssetCategoryIcon(assetIconOption(selectedKey.value), Modifier.size(78.dp), glyphSize = 64.dp)
                if (isOpen.value) {
                    AssetIconPicker(
                        selectedKey = selectedKey.value,
                        onSelected = { selectedKey.value = it; isOpen.value = false },
                        onDismiss = { isOpen.value = false },
                    )
                }
            }
        }
        capture("asset-icons-expanded-dark.png")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle { assertEquals("future-icon", selectedKey.value); isOpen.value = true }
        composeRule.onNodeWithContentDescription("手机").performClick()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("future-icon", selectedKey.value)
            assertEquals(false, isOpen.value)
            assertEquals(0, formBacks)
        }
    }

    private fun capture(name: String) {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, name).outputStream().use {
            composeRule.onNodeWithText("选择资产图标").onParent().onParent()
                .captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
