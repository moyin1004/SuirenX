package io.suirenx.feature.assets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.onNodeWithText("购买日期").performClick()
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

    private fun setScreen(
        state: AssetsUiState,
        onRefresh: () -> Unit = {},
        onSortSelected: (AssetSort) -> Unit = {},
    ) {
        composeRule.setContent {
            val query = remember { mutableStateOf("") }
            val searching = remember { mutableStateOf(false) }
            SuirenXTheme {
                AssetsScreen(
                    state = state,
                    onFilterSelected = {},
                    onRefresh = onRefresh,
                    onAssetClick = {},
                    onSortSelected = onSortSelected,
                    query = query.value,
                    searching = searching.value,
                    onQueryChange = { query.value = it },
                    onSearch = { searching.value = !searching.value },
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
