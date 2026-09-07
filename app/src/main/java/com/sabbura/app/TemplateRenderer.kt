package com.sabbura.app

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/**
 * يرسم قوالب التلوين. كل القوالب مرسومة داخل مربّع 200×200 ثم تُقاس إلى الحجم المطلوب.
 */
object TemplateRenderer {

    private const val BOX = 200f
    private val cache = HashMap<String, Path>()

    private fun path(e: Elem): Path {
        val key = e.d + (e.m?.joinToString(",") ?: "")
        cache[key]?.let { return it }
        val p = SvgPath.parse(e.d)
        e.m?.let { v ->
            val m = Matrix()
            // ترتيب أندرويد: [a c e / b d f / 0 0 1]
            m.setValues(floatArrayOf(v[0], v[2], v[4], v[1], v[3], v[5], 0f, 0f, 1f))
            p.transform(m)
        }
        cache[key] = p
        return p
    }

    /** معامل التحجيم الذي يجعل الرسمة تملأ المساحة مع هامش. */
    fun scaleFor(w: Float, h: Float, margin: Float = 0.90f): Float =
        (minOf(w, h) * margin) / BOX

    /**
     * @param boldFactor يُستخدم لبناء قناع الحدود (خطوط أعرض لسدّ الفراغات)
     */
    fun draw(
        canvas: Canvas,
        tpl: Tpl,
        w: Float,
        h: Float,
        color: Int,
        boldFactor: Float = 1f,
        solidDashes: Boolean = false
    ) {
        val k = scaleFor(w, h)
        canvas.save()
        canvas.translate((w - BOX * k) / 2f, (h - BOX * k) / 2f)
        canvas.scale(k, k)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        for (e in tpl.elems) {
            val p = path(e)
            if (e.fill) {
                canvas.drawPath(p, fill)
            } else {
                stroke.strokeWidth = e.sw * boldFactor
                stroke.pathEffect =
                    if (e.dash && !solidDashes) DashPathEffect(floatArrayOf(7f, 8f), 0f) else null
                canvas.drawPath(p, stroke)
            }
        }
        canvas.restore()
    }
}
