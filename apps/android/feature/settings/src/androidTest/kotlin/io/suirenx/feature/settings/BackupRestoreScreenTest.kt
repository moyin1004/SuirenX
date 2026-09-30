package io.suirenx.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.suirenx.core.domain.BackupSummary
import io.suirenx.core.model.ThemeMode
import io.suirenx.core.ui.theme.SuirenXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidBackupShowsErrorOnBackupPageAndImportCanBeRetried() {
        val error = "不是 SuirenX 本地备份文件"
        var importRequests = 0
        composeRule.setContent {
            SuirenXTheme(ThemeMode.Light) {
                BackendSettingsScreen(
                    state = BackendUiState(backupError = error),
                    onAddressChanged = {},
                    onNameChanged = {},
                    onSave = {},
                    onSelect = {},
                    onRetry = {},
                    onUseLocal = {},
                    onUseRemote = {},
                    onImportBackup = { importRequests++ },
                )
            }
        }

        composeRule.onNodeWithText("备份与恢复").performClick()
        composeRule.onNodeWithText("从文件恢复").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, importRequests) }
    }

    @Test
    fun restoreFailureRemainsVisibleInPreviewAndRequiresExplicitConfirmation() {
        val failure = "恢复失败，原数据未改变"
        var confirmedRestores = 0
        composeRule.setContent {
            SuirenXTheme(ThemeMode.Light) {
                BackendSettingsScreen(
                    state = BackendUiState(
                        backupSummary = BackupSummary(assetCount = 2, expiryItemCount = 1),
                        backupError = failure,
                    ),
                    onAddressChanged = {},
                    onNameChanged = {},
                    onSave = {},
                    onSelect = {},
                    onRetry = {},
                    onUseLocal = {},
                    onUseRemote = {},
                    onConfirmRestore = { confirmedRestores++ },
                )
            }
        }

        composeRule.onNodeWithText("恢复预览").assertIsDisplayed()
        composeRule.onNodeWithText("备份中的资产").assertIsDisplayed()
        composeRule.onNodeWithText("2 项").assertIsDisplayed()
        composeRule.onNodeWithText(failure).assertIsDisplayed()
        composeRule.onNodeWithText("继续恢复").performClick()
        composeRule.onNodeWithText("替换本机数据？").assertIsDisplayed()
        composeRule.onNodeWithText("确认").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmedRestores) }
    }

    @Test
    fun dismissingRestorePreviewCancelsWithoutConfirmingReplacement() {
        var cancelled = 0
        var confirmed = 0
        composeRule.setContent {
            SuirenXTheme(ThemeMode.Light) {
                BackendSettingsScreen(
                    state = BackendUiState(backupSummary = BackupSummary(assetCount = 1)),
                    onAddressChanged = {},
                    onNameChanged = {},
                    onSave = {},
                    onSelect = {},
                    onRetry = {},
                    onUseLocal = {},
                    onUseRemote = {},
                    onConfirmRestore = { confirmed++ },
                    onCancelRestore = { cancelled++ },
                )
            }
        }

        composeRule.onNodeWithText("恢复预览").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(0, confirmed)
        }
    }
}
