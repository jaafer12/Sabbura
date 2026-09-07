package com.sabbura.app

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * محلّل مبسّط لصيغة مسارات SVG (M L H V C S Q T A Z) يبني كائن Path لأندرويد.
 * مكتوب يدوياً حتى لا يعتمد التطبيق على أي مكتبة خارجية.
 */
object SvgPath {

    fun parse(d: String): Path {
        val p = Path()
        val t = Tokenizer(d)
        var cmd = ' '
        var cx = 0f; var cy = 0f          // النقطة الحالية
        var sx = 0f; var sy = 0f          // بداية المسار الفرعي
        var lastC1x = 0f; var lastC1y = 0f
        var lastQx = 0f; var lastQy = 0f
        var prevCmd = ' '

        while (true) {
            val c = t.nextCommandOrNull()
            if (c != null) cmd = c else if (!t.hasNumber()) break
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var first = true
                    do {
                        var x = t.num(); var y = t.num()
                        if (rel) { x += cx; y += cy }
                        if (first) { p.moveTo(x, y); sx = x; sy = y; first = false } else p.lineTo(x, y)
                        cx = x; cy = y
                    } while (t.hasNumber())
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> do {
                    var x = t.num(); var y = t.num()
                    if (rel) { x += cx; y += cy }
                    p.lineTo(x, y); cx = x; cy = y
                } while (t.hasNumber())
                'H' -> do {
                    var x = t.num(); if (rel) x += cx
                    p.lineTo(x, cy); cx = x
                } while (t.hasNumber())
                'V' -> do {
                    var y = t.num(); if (rel) y += cy
                    p.lineTo(cx, y); cy = y
                } while (t.hasNumber())
                'C' -> do {
                    var x1 = t.num(); var y1 = t.num(); var x2 = t.num(); var y2 = t.num()
                    var x = t.num(); var y = t.num()
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                    p.cubicTo(x1, y1, x2, y2, x, y)
                    lastC1x = x2; lastC1y = y2; cx = x; cy = y
                } while (t.hasNumber())
                'S' -> do {
                    val rx = if (prevCmd.uppercaseChar() == 'C' || prevCmd.uppercaseChar() == 'S') 2 * cx - lastC1x else cx
                    val ry = if (prevCmd.uppercaseChar() == 'C' || prevCmd.uppercaseChar() == 'S') 2 * cy - lastC1y else cy
                    var x2 = t.num(); var y2 = t.num(); var x = t.num(); var y = t.num()
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy }
                    p.cubicTo(rx, ry, x2, y2, x, y)
                    lastC1x = x2; lastC1y = y2; cx = x; cy = y
                    prevCmd = 'S'
                } while (t.hasNumber())
                'Q' -> do {
                    var x1 = t.num(); var y1 = t.num(); var x = t.num(); var y = t.num()
                    if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                    p.quadTo(x1, y1, x, y)
                    lastQx = x1; lastQy = y1; cx = x; cy = y
                } while (t.hasNumber())
                'T' -> do {
                    val qx = if (prevCmd.uppercaseChar() == 'Q' || prevCmd.uppercaseChar() == 'T') 2 * cx - lastQx else cx
                    val qy = if (prevCmd.uppercaseChar() == 'Q' || prevCmd.uppercaseChar() == 'T') 2 * cy - lastQy else cy
                    var x = t.num(); var y = t.num()
                    if (rel) { x += cx; y += cy }
                    p.quadTo(qx, qy, x, y)
                    lastQx = qx; lastQy = qy; cx = x; cy = y
                    prevCmd = 'T'
                } while (t.hasNumber())
                'A' -> do {
                    val rx = t.num(); val ry = t.num(); val rot = t.num()
                    val large = t.num() != 0f; val sweep = t.num() != 0f
                    var x = t.num(); var y = t.num()
                    if (rel) { x += cx; y += cy }
                    arc(p, cx, cy, rx, ry, rot, large, sweep, x, y)
                    cx = x; cy = y
                } while (t.hasNumber())
                'Z' -> { p.close(); cx = sx; cy = sy }
            }
            if (cmd.uppercaseChar() != 'S' && cmd.uppercaseChar() != 'T') prevCmd = cmd
        }
        return p
    }

    /** قوس SVG → سلسلة منحنيات تكعيبية (يدعم الدوران حول المحور). */
    private fun arc(
        p: Path, x0: Float, y0: Float, rxIn: Float, ryIn: Float,
        rotDeg: Float, large: Boolean, sweep: Boolean, x1: Float, y1: Float
    ) {
        if (rxIn == 0f || ryIn == 0f || (x0 == x1 && y0 == y1)) { p.lineTo(x1, y1); return }
        var rx = abs(rxIn); var ry = abs(ryIn)
        val phi = Math.toRadians(rotDeg.toDouble())
        val cosP = cos(phi); val sinP = sin(phi)

        val dx2 = (x0 - x1) / 2.0; val dy2 = (y0 - y1) / 2.0
        val x1p = cosP * dx2 + sinP * dy2
        val y1p = -sinP * dx2 + cosP * dy2

        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1.0) { val s = sqrt(lambda).toFloat(); rx *= s; ry *= s }

        val sign = if (large != sweep) 1.0 else -1.0
        var num = rx.toDouble() * rx * ry * ry - rx.toDouble() * rx * y1p * y1p - ry.toDouble() * ry * x1p * x1p
        val den = rx.toDouble() * rx * y1p * y1p + ry.toDouble() * ry * x1p * x1p
        if (num < 0.0) num = 0.0
        if (den == 0.0) { p.lineTo(x1, y1); return }
        val co = sign * sqrt(num / den)
        val cxp = co * (rx * y1p / ry)
        val cyp = co * -(ry * x1p / rx)

        val cx = cosP * cxp - sinP * cyp + (x0 + x1) / 2.0
        val cy = sinP * cxp + cosP * cyp + (y0 + y1) / 2.0

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = hypot(ux, uy) * hypot(vx, vy)
            var a = Math.acos((dot / len).coerceIn(-1.0, 1.0))
            if (ux * vy - uy * vx < 0) a = -a
            return a
        }

        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * Math.PI
        if (sweep && delta < 0) delta += 2 * Math.PI

        val segs = ceil(abs(delta) / (Math.PI / 2)).toInt().coerceAtLeast(1)
        val step = delta / segs
        val alpha = 4.0 / 3.0 * tan(step / 4.0)

        var t1 = theta1
        var px = x0.toDouble(); var py = y0.toDouble()
        for (i in 0 until segs) {
            val t2 = t1 + step
            val cosT1 = cos(t1); val sinT1 = sin(t1)
            val cosT2 = cos(t2); val sinT2 = sin(t2)

            val e2x = cx + rx * cosP * cosT2 - ry * sinP * sinT2
            val e2y = cy + rx * sinP * cosT2 + ry * cosP * sinT2

            val d1x = -rx * cosP * sinT1 - ry * sinP * cosT1
            val d1y = -rx * sinP * sinT1 + ry * cosP * cosT1
            val d2x = -rx * cosP * sinT2 - ry * sinP * cosT2
            val d2y = -rx * sinP * sinT2 + ry * cosP * cosT2

            p.cubicTo(
                (px + alpha * d1x).toFloat(), (py + alpha * d1y).toFloat(),
                (e2x - alpha * d2x).toFloat(), (e2y - alpha * d2y).toFloat(),
                e2x.toFloat(), e2y.toFloat()
            )
            px = e2x; py = e2y
            t1 = t2
        }
    }

    private class Tokenizer(val s: String) {
        var i = 0
        private fun skip() { while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\t' || s[i] == '\r')) i++ }
        fun nextCommandOrNull(): Char? {
            skip()
            if (i < s.length && s[i].isLetter()) { val c = s[i]; i++; return c }
            return null
        }
        fun hasNumber(): Boolean {
            skip()
            return i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.')
        }
        fun num(): Float {
            skip()
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            return if (i > start) s.substring(start, i).toFloatOrNull() ?: 0f else 0f
        }
    }
}
