package com.sabbura.app

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** أيقونات بمساحة 24×24، مرسومة كخطوط حتى تبقى واضحة بأي حجم. */
object Ic {
    const val PEN = "M4 20h4L19.6 8.4a2.8 2.8 0 0 0-4-4L4 16v4z M14.5 5.5l4 4"
    const val ERASER = "M8.5 20.5H5.4a2 2 0 0 1-1.4-3.4L14 7a2 2 0 0 1 2.8 0l3.4 3.4a2 2 0 0 1 0 2.8l-7.3 7.3H8.5z M9 12.5l5.5 5.5"
    const val DROP = "M12 3.4c4.2 5.2 6.2 8 6.2 10.4a6.2 6.2 0 0 1-12.4 0c0-2.4 2-5.2 6.2-10.4z"
    const val IMAGE = "M3.5 8a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3z M3.5 16.5l4.5-4.5 4 4 3-3 5 4.5 M7.6 10a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z"
    const val UNDO = "M15 14l5-5-5-5 M20 9h-9a6 6 0 1 0 0 12h3"
    const val TRASH = "M4 7h16 M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2 M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12"
    const val CHECK = "M5 13l4 4L19 7"
    const val CLOSE = "M6 6l12 12 M18 6L6 18"
    const val SHAPES = "M7.5 3.2a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M14.5 13.5h7v7h-7z M7.5 12.8L12 20.8H3z"
    const val LINES = "M2.5 15c2-8 5 6 7.5 0s5.5-8 7.5 0 M18.5 12l3 3-3 3"
    const val PAGE = "M6.5 3.5h11v17h-11z M9.5 8h5 M9.5 12h5 M9.5 16h3"
}

private val cache = HashMap<String, Path>()
private fun iconPath(d: String): Path = cache.getOrPut(d) { SvgPath.parse(d) }

@Composable
fun VIcon(
    data: String,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    filled: Boolean = false
) {
    Canvas(modifier) {
        val k = size.minDimension / 24f
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            val save = nc.save()
            nc.translate((size.width - 24f * k) / 2f, (size.height - 24f * k) / 2f)
            nc.scale(k, k)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                if (filled) {
                    style = Paint.Style.FILL
                } else {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            }
            nc.drawPath(iconPath(data), p)
            nc.restoreToCount(save)
        }
    }
}
