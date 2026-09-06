package io.suirenx.core.ui.icon

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.suirenx.core.ui.R

// Material Symbols Rounded variable font, subset to the curated glyphs
// (default axes: wght 400, FILL 0). Regenerate with
// apps/android/scripts/subset-material-symbols.sh when adding glyphs.
private val MaterialSymbolsRounded = FontFamily(
    Font(R.font.material_symbols_rounded, weight = FontWeight.Normal),
)

// The same resource with the FILL axis pinned at 1, for selected states.
private val MaterialSymbolsRoundedFilled = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("FILL", 1f),
        ),
    ),
)

// Renders a Material Symbols glyph from the bundled variable font. Monochrome
// like an ImageVector icon: [tint] colors the glyph (defaults to
// LocalContentColor), [filled] selects the FILL 1 variant. Sized in dp like
// Icon, independent of font scale.
@Composable
fun MaterialSymbol(
    glyph: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
    filled: Boolean = false,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val color = if (tint != Color.Unspecified) tint else LocalContentColor.current
    val style = TextStyle(
        fontFamily = if (filled) MaterialSymbolsRoundedFilled else MaterialSymbolsRounded,
        fontSize = fontSize,
        lineHeight = fontSize,
        color = color,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    val accessibility = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    BasicText(
        text = glyph,
        style = style,
        modifier = modifier
            .size(size)
            .then(accessibility),
    )
}
