package io.suirenx.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small native vectors matching the OpenDesign outline style. */
object SuirenIcons {
    val AssetBox: ImageVector = ImageVector.Builder("AssetBox", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 8f); lineTo(12f, 4f); lineTo(20f, 8f); lineTo(12f, 12f); close()
            moveTo(4f, 8f); lineTo(4f, 16f); lineTo(12f, 20f); lineTo(20f, 16f); lineTo(20f, 8f)
            moveTo(12f, 12f); lineTo(12f, 20f)
        }
    }.build()

    val Phone: ImageVector = ImageVector.Builder("Phone", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(8f, 2.5f); lineTo(16f, 2.5f)
            curveTo(17.1f, 2.5f, 18f, 3.4f, 18f, 4.5f); lineTo(18f, 19.5f)
            curveTo(18f, 20.6f, 17.1f, 21.5f, 16f, 21.5f); lineTo(8f, 21.5f)
            curveTo(6.9f, 21.5f, 6f, 20.6f, 6f, 19.5f); lineTo(6f, 4.5f)
            curveTo(6f, 3.4f, 6.9f, 2.5f, 8f, 2.5f); close()
            moveTo(10f, 5f); lineTo(14f, 5f)
            moveTo(10.5f, 18.5f); lineTo(13.5f, 18.5f)
        }
    }.build()
}
