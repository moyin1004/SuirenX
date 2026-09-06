package io.suirenx.feature.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.suirenx.core.ui.icon.MaterialSymbol

internal data class AssetIconOption(
    val key: String,
    val label: String,
    val glyph: String,
    // Soft pastel container + deeper on-container tint, tuned for the light theme.
    val containerColor: Color,
    val contentColor: Color,
)

// Persist language-neutral keys; glyphs and labels belong to the UI. Glyphs are
// Material Symbols Rounded codepoints (https://fonts.google.com/icons), rendered
// by the shared MaterialSymbol in core/ui.
internal val assetIconOptions = listOf(
    AssetIconOption("devices", "通用", "\uE326", Color(0xFFECEEF1), Color(0xFF51575F)), // devices
    AssetIconOption("laptop", "电脑", "\uE31E", Color(0xFFDCE8FA), Color(0xFF2C5BA6)), // laptop
    AssetIconOption("phone", "手机", "\uE7BA", Color(0xFFDBF0E1), Color(0xFF27714A)), // smartphone
    AssetIconOption("tablet", "平板", "\uE32F", Color(0xFFE4E1F7), Color(0xFF4E42A0)), // tablet
    AssetIconOption("headphones", "耳机", "\uF01F", Color(0xFFF2E0F5), Color(0xFF83389E)), // headphones
    AssetIconOption("watch", "手表", "\uE334", Color(0xFFFCEBD2), Color(0xFF96560F)), // watch
    AssetIconOption("camera", "相机", "\uE412", Color(0xFFFAE0E6), Color(0xFFA2364F)), // photo_camera
    AssetIconOption("gamepad", "游戏机", "\uEA28", Color(0xFFFBDFD9), Color(0xFFAC3E33)), // sports_esports
    AssetIconOption("book", "书籍", "\uEA19", Color(0xFFF1E7D6), Color(0xFF755629)), // menu_book
    AssetIconOption("keyboard", "键盘", "\uE312", Color(0xFFE0E6F0), Color(0xFF3F4D6B)), // keyboard
    AssetIconOption("bicycle", "自行车", "\uE52F", Color(0xFFEDF4DA), Color(0xFF5F7A1E)), // directions_bike
    AssetIconOption("home", "家居", "\uE9B2", Color(0xFFDCEFEA), Color(0xFF1F6E62)), // home
)

// Unknown keys from newer servers remain stored, but render with a safe fallback.
internal fun assetIconOption(key: String): AssetIconOption =
    assetIconOptions.firstOrNull { it.key == key } ?: assetIconOptions.first()

// Small rounded tile in the category's pastel color for list cards and chips.
@Composable
internal fun AssetCategoryIcon(
    option: AssetIconOption,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 28.dp,
    cornerRadius: Dp = 14.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(option.containerColor),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            glyph = option.glyph,
            tint = option.contentColor,
            size = glyphSize,
            contentDescription = contentDescription,
        )
    }
}

@Composable
internal fun AssetIconPicker(
    selectedKey: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("选择资产图标") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                assetIconOptions.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val isSelected = option.key == selectedKey
                            Surface(
                                onClick = { onSelected(option.key) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f).semantics {
                                    selected = isSelected
                                    role = Role.RadioButton
                                },
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    MaterialSymbol(glyph = option.glyph, size = 26.dp)
                                    Text(option.label, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
