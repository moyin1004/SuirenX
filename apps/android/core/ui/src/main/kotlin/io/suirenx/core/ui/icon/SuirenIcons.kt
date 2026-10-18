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
    val Visibility: ImageVector = visibilityIcon(false)
    val VisibilityOff: ImageVector = visibilityIcon(true)

    val Filter: ImageVector = ImageVector.Builder("Filter", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(7f, 12f); lineTo(17f, 12f)
            moveTo(10f, 18f); lineTo(14f, 18f)
        }
    }.build()

    val Backup: ImageVector = ImageVector.Builder("Backup", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 4f); lineTo(19f, 4f); lineTo(22f, 14f); lineTo(22f, 20f); lineTo(2f, 20f); lineTo(2f, 14f); close()
            moveTo(2f, 14f); lineTo(22f, 14f)
            moveTo(15f, 17f); lineTo(18f, 17f)
        }
    }.build()

    val Appearance: ImageVector = ImageVector.Builder("Appearance", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f)
            arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, false, false, 12f, 21f); close()
        }
    }.build()

    val Server: ImageVector = ImageVector.Builder("Server", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f)
            arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(3f, 12f); lineTo(21f, 12f)
            moveTo(12f, 3f); curveTo(6f, 8f, 6f, 16f, 12f, 21f)
            curveTo(18f, 16f, 18f, 8f, 12f, 3f)
        }
    }.build()

    val Sort: ImageVector = ImageVector.Builder("Sort", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(8f, 19f); lineTo(8f, 5f)
            moveTo(4f, 9f); lineTo(8f, 5f); lineTo(12f, 9f)
            moveTo(16f, 5f); lineTo(16f, 19f)
            moveTo(12f, 15f); lineTo(16f, 19f); lineTo(20f, 15f)
        }
    }.build()

    private fun visibilityIcon(hidden: Boolean): ImageVector = ImageVector.Builder(
        if (hidden) "VisibilityOff" else "Visibility", 24.dp, 24.dp, 24f, 24f,
    ).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(2f, 12f); quadTo(12f, -2f, 22f, 12f); quadTo(12f, 26f, 2f, 12f); close()
            moveTo(15f, 12f)
            arcTo(3f, 3f, 0f, true, true, 9f, 12f)
            arcTo(3f, 3f, 0f, true, true, 15f, 12f)
            if (hidden) { moveTo(3f, 3f); lineTo(21f, 21f) }
        }
    }.build()

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
