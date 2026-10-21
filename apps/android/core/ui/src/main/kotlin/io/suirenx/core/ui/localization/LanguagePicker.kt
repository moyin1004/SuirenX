package io.suirenx.core.ui.localization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.suirenx.core.ui.R
import io.suirenx.core.ui.theme.SuirenXTheme

@Composable
fun languagePreferenceLabel(): String {
    LocalConfiguration.current
    return languageLabel(LanguageSettings.selection(LocalContext.current))
}

@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    var selected by remember(context, configuration) { mutableStateOf(LanguageSettings.selection(context)) }
    val resolved = remember(context, configuration) { LanguageSettings.resolvedLanguage(context) }
    LanguageOptions(selected, resolved, { choice ->
        activity?.let {
            LanguageSettings.select(it, choice)
            // Selecting an explicit language equal to the system language may not
            // change the effective configuration, so refresh the radio state too.
            selected = LanguageSettings.selection(context)
        }
    }, modifier)
}

@Composable
private fun LanguageOptions(
    selected: AppLanguage,
    resolved: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppLanguage.entries.forEach { language ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = language == selected, role = Role.RadioButton, onClick = { onSelect(language) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = language == selected, onClick = null)
                Text(languageLabel(language), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.language_current, languageLabel(resolved)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.language_device_only),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(when (language) {
    AppLanguage.System -> R.string.language_follow_system
    AppLanguage.SimplifiedChinese -> R.string.language_simplified_chinese
    AppLanguage.English -> R.string.language_english
})

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.findActivity() else null
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun LanguagePickerPreview() {
    SuirenXTheme { LanguageOptions(AppLanguage.System, AppLanguage.SimplifiedChinese, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LanguagePickerDarkPreview() {
    SuirenXTheme { LanguageOptions(AppLanguage.English, AppLanguage.English, {}) }
}
