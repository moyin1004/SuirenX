package io.suirenx.feature.assets

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.suirenx.core.ui.theme.SuirenXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetFormScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveFailureKeepsDraftVisibleAndShowsRetryableError() {
        val state = mutableStateOf(
            AssetFormUiState(name = "Kindle Paperwhite", price = "899.50", iconKey = "book"),
        )
        composeRule.setContent {
            SuirenXTheme {
                AssetFormScreen(
                    state = state.value,
                    onNameChanged = {},
                    onPriceChanged = {},
                    onPurchaseDateChanged = {},
                    onPurchaseChannelChanged = {},
                    onWarrantyEndDateChanged = {},
                    onNotesChanged = {},
                    onTagsChanged = {},
                    onSave = {
                        state.value = state.value.copy(
                            errorMessage = "保存失败，请检查网络和服务后重试",
                        )
                    },
                    onClose = {},
                    onOpenIconPicker = {},
                    onCloseIconPicker = {},
                    onIconSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("保存").performClick()

        composeRule.onNodeWithText("Kindle Paperwhite").assertIsDisplayed()
        composeRule.onNodeWithText("899.50").assertIsDisplayed()
        composeRule.onNodeWithText("保存失败，请检查网络和服务后重试").performScrollTo()
        composeRule.onNodeWithText("保存失败，请检查网络和服务后重试").assertIsDisplayed()
    }
}
