package io.suirenx.feature.assets

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsSelected
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun salePricesOnlyRenderWhenPresentIncludingZero() {
        setScreen(AssetsUiState(isLoading = false, selectedFilter = AssetFilter.All, assets = listOf(
            asset("sold", "已售设备").copy(archivedAt = java.time.Instant.now(), salePriceCents = 80050),
            asset("free", "零元转让").copy(archivedAt = java.time.Instant.now(), salePriceCents = 0),
            asset("empty", "未填写卖出价"),
        )), modifier = Modifier.width(360.dp))
        composeRule.onNodeWithText("卖出 ¥800.50").assertIsDisplayed()
        composeRule.onNodeWithText("卖出 ¥0.00").assertIsDisplayed()
        composeRule.onAllNodesWithText("卖出 ¥", substring = true).assertCountEquals(2)
        fun cardHeight(name: String): Float {
            var node = composeRule.onNodeWithText(name).fetchSemanticsNode()
            while (node.parent != null && !node.config.contains(SemanticsActions.OnClick)) node = node.parent!!
            return node.boundsInRoot.height
        }
        assertEquals(cardHeight("未填写卖出价"), cardHeight("已售设备"), 1f)
        assertEquals(cardHeight("未填写卖出价"), cardHeight("零元转让"), 1f)
        captureDisplay("asset-sale-card.png")
    }

    @Test
    fun allFilterIncludesArchivedAssetsInListAndSummary() {
        setScreen(
            AssetsUiState(
                isLoading = false,
                selectedFilter = AssetFilter.All,
                assets = listOf(
                    asset("active", "Active"),
                    asset("archived", "Archived laptop").copy(archivedAt = java.time.Instant.now()),
                ),
            ),
        )
        composeRule.onNodeWithText("2/2").assertIsDisplayed()
        composeRule.onNodeWithText("¥2,000").assertIsDisplayed()
        composeRule.onNodeWithText("¥100").assertIsDisplayed()
        composeRule.onNodeWithText("Archived laptop").assertIsDisplayed()
        composeRule.onAllNodesWithText("服役中数量").assertCountEquals(0)
        composeRule.onAllNodesWithText("已退役数量").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("搜索资产").performClick()
        composeRule.onNodeWithText("搜索名称、类型或标签").performTextInput("Archived")
        composeRule.onNodeWithText("1/2").assertIsDisplayed()
        composeRule.onNodeWithText("¥1,000").assertIsDisplayed()
        composeRule.onNodeWithText("Archived laptop").assertIsDisplayed()
        composeRule.onAllNodesWithText("Active").assertCountEquals(0)
    }

    @Test
    fun narrowScreenKeepsLargeAmountAndLongAssetNameOnOneLine() {
        val longName = "窄屏测试 MacBook Pro 14 M4 Pro 48GB 1TB 银色工作电脑"
        setScreen(
            AssetsUiState(isLoading = false, assets = listOf(asset("long", longName).copy(priceCents = 1_234_567_890))),
            modifier = Modifier.width(320.dp),
        )
        val amount = composeRule.onNodeWithText("¥12,345,678.9").assertIsDisplayed()
        val name = composeRule.onNodeWithText(longName, useUnmergedTree = true).assertIsDisplayed()
        val amountLayouts = mutableListOf<TextLayoutResult>()
        val nameLayouts = mutableListOf<TextLayoutResult>()
        amount.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(amountLayouts) }
        name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nameLayouts) }
        assertEquals(1, amountLayouts.single().lineCount)
        assertTrue("Amount must fit without truncation", !amountLayouts.single().hasVisualOverflow)
        assertEquals(1, nameLayouts.single().lineCount)
    }

    @Test
    fun fittingMixedLanguageNameStaysWithinCardWhileLongNameOverflows() {
        val fittingName = "天虹QN10 迷你主机"
        val longName = "窄屏测试 MacBook Pro 14 M4 Pro 48GB 1TB 银色工作电脑"
        setScreen(
            AssetsUiState(isLoading = false, assets = listOf(asset("fit", fittingName), asset("long", longName))),
            modifier = Modifier.width(359.dp),
        )
        val fittingLayouts = mutableListOf<TextLayoutResult>()
        val longLayouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(fittingName, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(fittingLayouts) }
        composeRule.onNodeWithText(longName, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(longLayouts) }
        val availableWidth = with(composeRule.density) { (((359.dp - 32.dp - 10.dp) / 2) - 28.dp).roundToPx() }
        val fittingLayout = fittingLayouts.single()
        assertTrue(
            "Fitting name width ${fittingLayout.size.width} exceeds $availableWidth px; letter spacing ${fittingLayout.layoutInput.style.letterSpacing}",
            fittingLayout.size.width <= availableWidth,
        )
        assertTrue("Long names must still overflow to trigger marquee", longLayouts.single().size.width > availableWidth)
    }

    @Test
    fun filteredOverviewAlsoDrivesTheCompactSummary() {
        val retired = (1..20).map {
            asset("retired-$it", "Retired $it").copy(status = AssetStatus.Retired, priceCents = 10_000)
        }
        setScreen(
            AssetsUiState(
                isLoading = false,
                selectedFilter = AssetFilter.Retired,
                assets = retired + asset("active", "Active outside filter"),
            ),
        )
        composeRule.onNodeWithText("20/21").assertIsDisplayed()
        composeRule.onNodeWithText("¥2,000").assertIsDisplayed()
        composeRule.onNode(
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).performScrollToIndex(6)
        composeRule.onNodeWithText("筛选购入额 · 20 项 · 最长 10 天").assertIsDisplayed()
        composeRule.onNodeWithText("¥2,000.00").assertIsDisplayed()
    }

    @Test
    fun searchIsCollapsedUntilRequestedAndFiltersVisibleAssets() {
        setScreen(
            AssetsUiState(
                isLoading = false,
                assets = listOf(asset("mac", "MacBook Pro"), asset("kindle", "Kindle")),
            ),
        )

        composeRule.onAllNodesWithText("搜索名称、类型或标签").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("搜索资产").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("搜索名称、类型或标签").assertIsDisplayed().performTextInput("Mac")
        composeRule.onNodeWithText("MacBook Pro").assertIsDisplayed()
        composeRule.onAllNodesWithText("Kindle").assertCountEquals(0)
    }

    @Test
    fun sortMenuReportsSelectedField() {
        var selected: AssetSort? = null
        setScreen(
            AssetsUiState(isLoading = false, assets = listOf(asset("mac", "MacBook Pro"))),
            onSortSelected = { selected = it },
        )

        composeRule.onNodeWithContentDescription("资产排序").performClick()
        composeRule.onNodeWithText("购买金额").performClick()
        composeRule.runOnIdle { assertEquals(AssetSort.Price, selected) }
    }

    @Test
    fun localReadFailureOffersRetryCallback() {
        var retries = 0
        setScreen(
            AssetsUiState(isLoading = false, errorMessage = "数据库读取失败"),
            onRefresh = { retries++ },
        )

        composeRule.onNodeWithText("无法读取本机数据").assertIsDisplayed()
        composeRule.onNodeWithText("重试读取").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun narrowControlsStayOnOneRowAndDirectionSelectionIsExplicit() {
        var directionChanges = 0
        setScreen(
            AssetsUiState(isLoading = false, assets = listOf(
                asset("mac", "MacBook Pro").copy(iconKey = "laptop"),
                asset("kindle", "Kindle").copy(iconKey = "book"),
            )),
            modifier = Modifier.width(359.dp),
            onDirectionToggle = { directionChanges++ },
        )
        composeRule.onAllNodesWithText("我的资产").assertCountEquals(0)
        val search = composeRule.onNodeWithContentDescription("搜索资产").assertIsDisplayed()
        val sort = composeRule.onNodeWithContentDescription("资产排序").assertIsDisplayed()
        val tags = composeRule.onNodeWithContentDescription("标签筛选").assertIsDisplayed()
        val rowCenter = search.fetchSemanticsNode().boundsInRoot.center.y
        assertTrue(abs(sort.fetchSemanticsNode().boundsInRoot.center.y - rowCenter) < 2f)
        assertTrue(abs(tags.fetchSemanticsNode().boundsInRoot.center.y - rowCenter) < 2f)
        AssetFilter.entries.forEach { filter ->
            val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(when (filter) {
                AssetFilter.All -> io.suirenx.core.ui.R.string.copy_all
                AssetFilter.Active -> io.suirenx.core.ui.R.string.copy_active
                AssetFilter.Retired -> io.suirenx.core.ui.R.string.copy_retired
                AssetFilter.Archived -> io.suirenx.core.ui.R.string.copy_archived
            })
            val chip = composeRule.onNode(
                hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
            ).performScrollTo().assertIsDisplayed()
            assertTrue("${filter.label} must share the controls row", abs(chip.fetchSemanticsNode().boundsInRoot.center.y - rowCenter) < 2f)
        }
        captureScreen("asset-controls-one-row.png")
        sort.performClick()
        composeRule.onNodeWithText("升序").performClick()
        composeRule.runOnIdle { assertEquals(1, directionChanges) }
        composeRule.onNodeWithContentDescription("资产排序").performClick()
        composeRule.onNodeWithText("升序").assertIsSelected().performClick()
        composeRule.runOnIdle { assertEquals("Selecting the current direction must not toggle it", 1, directionChanges) }
        search.performClick()
        composeRule.onNodeWithText("搜索名称、类型或标签").performTextInput("Mac")
        composeRule.onNodeWithContentDescription("返回资产列表").performClick()
        composeRule.onNodeWithContentDescription("资产排序").assertIsDisplayed()
        assertEquals("搜索条件：Mac", composeRule.onNodeWithContentDescription("搜索资产").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        composeRule.onNodeWithText("MacBook Pro").assertIsDisplayed()
        composeRule.onAllNodesWithText("Kindle").assertCountEquals(0)
    }

    @Test
    fun tagMenuKeepsIntersectionAndSearchWhenReopenedOrCleared() {
        setScreen(AssetsUiState(isLoading = false, assets = listOf(
            asset("mac", "MacBook Pro").copy(tags = listOf("工作", "常用")),
            asset("kindle", "Kindle").copy(tags = listOf("工作")),
        )), modifier = Modifier.width(320.dp))
        composeRule.onAllNodesWithText("工作").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("标签筛选").performClick()
        composeRule.onNodeWithText("工作").performClick()
        composeRule.onNodeWithText("常用").performClick()
        composeRule.onNodeWithText("常用").assertIsSelected()
        captureDisplay("asset-tag-menu.png")
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onNodeWithText("MacBook Pro").assertIsDisplayed()
        composeRule.onAllNodesWithText("Kindle").assertCountEquals(0)
        assertEquals("已选 2 个标签", composeRule.onNodeWithContentDescription("标签筛选")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        captureScreen("asset-tag-selected.png")
        composeRule.onNodeWithContentDescription("搜索资产").performClick()
        composeRule.onNodeWithText("搜索名称、类型或标签").performTextInput("Mac")
        composeRule.onNodeWithContentDescription("返回资产列表").performClick()
        composeRule.onNodeWithContentDescription("标签筛选").performClick()
        composeRule.onNodeWithText("工作").assertIsSelected()
        composeRule.onNodeWithText("常用").assertIsSelected()
        composeRule.onNodeWithText("全部标签").performClick()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onAllNodesWithText("Kindle").assertCountEquals(0)
        assertEquals("搜索条件：Mac", composeRule.onNodeWithContentDescription("搜索资产")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("全部标签", composeRule.onNodeWithContentDescription("标签筛选")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    private fun captureDisplay(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir(null)!!
        File(directory, name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun captureScreen(name: String) {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, name).outputStream().use {
            composeRule.onNode(
                hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            ).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun setScreen(
        state: AssetsUiState,
        modifier: Modifier = Modifier,
        onRefresh: () -> Unit = {},
        onSortSelected: (AssetSort) -> Unit = {},
        onDirectionToggle: () -> Unit = {},
    ) {
        composeRule.setContent {
            val screenState = remember { mutableStateOf(state) }
            val query = remember { mutableStateOf("") }
            val searching = remember { mutableStateOf(false) }
            SuirenXTheme {
                AssetsScreen(
                    state = screenState.value,
                    modifier = modifier,
                    onFilterSelected = { screenState.value = screenState.value.copy(selectedFilter = it) },
                    onRefresh = onRefresh,
                    onAssetClick = {},
                    onSortSelected = { screenState.value = screenState.value.copy(sort = it); onSortSelected(it) },
                    onSortDirectionToggle = {
                        screenState.value = screenState.value.copy(sortDescending = !screenState.value.sortDescending)
                        onDirectionToggle()
                    },
                    query = query.value,
                    searching = searching.value,
                    onQueryChange = { query.value = it },
                    onSearch = { searching.value = !searching.value },
                    onTagToggle = { tag -> screenState.value = screenState.value.copy(selectedTags =
                        if (tag in screenState.value.selectedTags) screenState.value.selectedTags - tag else screenState.value.selectedTags + tag) },
                    onClearTags = { screenState.value = screenState.value.copy(selectedTags = emptySet()) },
                )
            }
        }
    }

    private fun asset(id: String, name: String) = Asset(
        id = id,
        name = name,
        priceCents = 100_000,
        purchaseDate = LocalDate.of(2025, 1, 1),
        status = AssetStatus.Active,
        imageUrl = "",
        heldDays = 10,
        dailyCostCents = 10_000,
    )
}
