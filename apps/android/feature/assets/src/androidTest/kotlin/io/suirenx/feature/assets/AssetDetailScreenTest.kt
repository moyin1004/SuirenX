package io.suirenx.feature.assets

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.suirenx.core.model.Asset
import io.suirenx.core.model.AssetStatus
import io.suirenx.core.ui.theme.SuirenXTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun retirementCalendarLimitsDatesAndCommitsOnlyOnConfirmation() {
        val today = LocalDate.now()
        val purchase = today.withDayOfMonth(if (today.dayOfMonth > 2) 2 else 1)
        val choice = today.minusDays(1)
        val calendarDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
        var chosenDate = today.toString()
        var dateChanges = 0
        var saves = 0
        composeRule.setContent {
            val state = remember { mutableStateOf(AssetDetailUiState(
                isLoading = false,
                asset = Asset("calendar", "Calendar fixture", 10_000, purchase, AssetStatus.Active, "", 12, 833),
                statusTarget = AssetStatus.Retired,
                retiredDateInput = today.toString(),
            )) }
            SuirenXTheme {
                AssetDetailScreen(
                    state = state.value, onBack = {}, onRetry = {}, onEdit = {}, onChangeStatus = {},
                    onRetiredDateChange = {
                        chosenDate = it
                        dateChanges++
                        state.value = state.value.copy(retiredDateInput = it)
                    },
                    onDismissStatus = { state.value = state.value.copy(statusTarget = null) },
                    onSaveStatus = { saves++ }, onChangeArchive = {}, onDismissArchive = {}, onSaveArchive = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("选择退役日期").performClick()
        composeRule.onNodeWithText("确定").assertIsDisplayed()
        // A future day in the displayed month cannot be selected.
        if (today.dayOfMonth < today.lengthOfMonth()) {
            composeRule.onNodeWithText(today.plusDays(1).format(calendarDate)).assertIsNotEnabled()
        }
        if (purchase.dayOfMonth > 1) {
            composeRule.onNodeWithText(purchase.minusDays(1).format(calendarDate)).assertIsNotEnabled()
        }
        if (choice.month == today.month) {
            composeRule.onNodeWithText(choice.format(calendarDate)).performClick()
        }
        composeRule.onAllNodesWithText("取消").assertCountEquals(2)[1].performClick()
        composeRule.runOnIdle { assertEquals(0, dateChanges); assertEquals(0, saves) }
        composeRule.onNodeWithText(today.toString()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("选择退役日期").performClick()
        if (choice.month == today.month) {
            composeRule.onNodeWithText(choice.format(calendarDate)).performClick()
        }
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle {
            assertEquals(if (choice.month == today.month) choice.toString() else today.toString(), chosenDate)
            assertEquals(1, dateChanges)
            assertEquals(0, saves)
        }
        composeRule.onNodeWithText("确认").performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
    }
}
