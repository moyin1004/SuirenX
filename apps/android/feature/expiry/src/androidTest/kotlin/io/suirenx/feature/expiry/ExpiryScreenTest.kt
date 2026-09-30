package io.suirenx.feature.expiry

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.suirenx.core.model.ExpiryBucket
import io.suirenx.core.model.ExpiryItem
import io.suirenx.core.model.ExpiryItemStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpiryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchIsCollapsedUntilRequestedAndFiltersByLocation() {
        showScreen(
            ExpiryUiState(
                loading = false,
                hasLoaded = true,
                items = listOf(
                    item("milk", "Milk", "Kitchen"),
                    item("soap", "Soap", "Bathroom"),
                ),
            ),
        )

        composeRule.onAllNodesWithText("搜索名称、分类或位置").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("搜索用品").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("搜索名称、分类或位置").assertIsDisplayed().performTextInput("Bathroom")
        composeRule.onNodeWithText("Soap").assertIsDisplayed()
        composeRule.onAllNodesWithText("Milk").assertCountEquals(0)
    }

    @Test
    fun selectingExpiryFilterCallsExpectedCallback() {
        var selected: ExpiryBucket? = null
        showScreen(
            ExpiryUiState(loading = false, items = listOf(item("milk", "Milk", "Kitchen"))),
            onFilter = { selected = it },
        )

        composeRule.onAllNodesWithText("已过期").assertCountEquals(2)[1].performClick()
        composeRule.runOnIdle { assertEquals(ExpiryBucket.Expired, selected) }
    }

    @Test
    fun localReadErrorCanBeRetried() {
        var retries = 0
        showScreen(
            ExpiryUiState(loading = false, error = "本机数据读取失败"),
            onRetry = { retries++ },
        )

        composeRule.onNodeWithText("本机数据读取失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    private fun showScreen(
        initialState: ExpiryUiState,
        onRetry: () -> Unit = {},
        onFilter: (ExpiryBucket?) -> Unit = {},
    ) {
        composeRule.setContent {
            val state = remember { mutableStateOf(initialState) }
            val searching = remember { mutableStateOf(false) }
            SuirenXTheme {
                ExpiryScreen(
                    state = state.value,
                    onBack = {},
                    onAdd = {},
                    onRetry = onRetry,
                    onFilter = onFilter,
                    onArchived = {},
                    onQueryChanged = { state.value = state.value.copy(query = it) },
                    onOpen = {},
                    onCloseDetail = {},
                    onEdit = {},
                    onStatus = {},
                    onArchive = {},
                    onCloseEditor = {},
                    onSaveEditor = {},
                    onEditorName = {},
                    onEditorCategory = {},
                    onEditorPackageExpiry = {},
                    onEditorOpenedDate = {},
                    onEditorOpenedDays = {},
                    onEditorLocation = {},
                    onEditorNotes = {},
                    searching = searching.value,
                    onSearch = { searching.value = !searching.value },
                )
            }
        }
    }

    private fun item(id: String, name: String, location: String) = ExpiryItem(
        id = id,
        name = name,
        category = "Household",
        packageExpiryDate = LocalDate.now().plusDays(30),
        location = location,
        status = ExpiryItemStatus.InUse,
    )
}
