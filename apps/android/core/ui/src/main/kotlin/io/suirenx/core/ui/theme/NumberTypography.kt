package io.suirenx.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.suirenx.core.ui.R

// Android alternative specified by the OpenDesign brand spec. Bundled rather
// than system monospace so money keeps the same glyphs on every device.
// Source: google/fonts/ofl/robotomono (OFL, in assets/licenses).
val SuirenNumberFont = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
)
