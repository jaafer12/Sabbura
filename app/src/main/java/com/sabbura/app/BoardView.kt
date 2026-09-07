package com.sabbura.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * لوحة الرسم. كل الإحداثيات تُخزَّن مُطبَّعة (مقسومة على العرض) حتى يبقى الرسم
 * صحيحاً عند تدوير الجهاز أو تغيّر حجم النافذة.
 */
class BoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    enum class Tool { PEN, ERASER, FILL }

    // ---------- الحالة العامة ----------
    var tool: Tool = Tool.PEN
    var brushColor: Int = 0xFF2F3548.toInt()
    var brushPx: Float = 14f                       // عرض القلم بالبكسل
    var onHistoryChanged: ((canUndo: Boolean, hasContent: Boolean) -> Unit)? = null

    private var tpl: Tpl? = null
    private var lineColor: Int = 0xFF3A4363.toInt()

    // ---------- الطبقات ----------
    private var layer: Bitmap? = null              // تلوين الطفل
    private var lc: Canvas? = null
    private var base: Bitmap? = null               // الأوامر القديمة بعد التسطيح
    private var wall: BooleanArray? = null         // حدود الرسمة (لدلو التعبئة)
    private var wallDirty = true

    // ---------- سجل الأوامر ----------
    private abstract class Cmd
    private class Stroke(
        val color: Int, val width: Float, val eraser: Boolean, val vary: Boolean
    ) : Cmd() {
        val xs = ArrayList<Float>(64)
        val ys = ArrayList<Float>(64)
        val ps = ArrayList<Float>(64)
    }
    private class FillCmd(val x: Float, val y: Float, val color: Int) : Cmd()

    private val cmds = ArrayList<Cmd>()
    private var cur: Stroke? = null
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var activeId = -1
    private var lastStylusAt = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val dirty = RectF()

    companion object {
        private const val KEEP = 25          // أوامر تبقى قابلة للتراجع بعد التسطيح
        private const val FLATTEN_AT = 80
        private const val TOL = 42           // تسامح ألوان دلو التعبئة
    }

    init { setWillNotDraw(false) }

    // ---------- واجهة عامة ----------
    fun setTemplate(t: Tpl?) {
        if (t?.id == tpl?.id) return
        tpl = t
        wallDirty = true
        invalidate()
    }

    fun setLineColor(c: Int) { lineColor = c; wallDirty = true; invalidate() }

    fun canUndo() = cmds.isNotEmpty()
    fun hasContent() = cmds.isNotEmpty() || base != null

    fun undo() {
        if (cmds.isEmpty()) return
        cmds.removeAt(cmds.size - 1)
        rebuild()
        invalidate()
        notifyHistory()
    }

    fun clearAll() {
        cmds.clear()
        base?.recycle(); base = null
        cur = null; activeId = -1
        rebuild()
        invalidate()
        notifyHistory()
    }

    private fun notifyHistory() = onHistoryChanged?.invoke(canUndo(), hasContent())

    // ---------- الحجم ----------
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        layer?.recycle()
        layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        lc = Canvas(layer!!)
        wallDirty = true
        rebuild()
    }

    // ---------- الرسم على الشاشة ----------
    override fun onDraw(canvas: Canvas) {
        layer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        tpl?.let {
            TemplateRenderer.draw(canvas, it, width.toFloat(), height.toFloat(), lineColor)
        }
    }

    // ---------- محرك الخطوط ----------
    private fun pxX(nx: Float) = nx * width
    private fun pxY(ny: Float) = ny * width          // القسمة على العرض للحفاظ على النِّسَب

    private fun widthAt(s: Stroke, p: Float) =
        if (s.vary) max(1f, s.width * width * (0.4f + 0.85f * p)) else s.width * width

    private fun setupPaint(s: Stroke, p: Float) {
        paint.strokeWidth = widthAt(s, p)
        if (s.eraser) {
            paint.xfermode = clearMode
            paint.color = Color.BLACK
        } else {
            paint.xfermode = null
            paint.color = s.color
        }
    }

    private fun drawDot(s: Stroke, i: Int) {
        val c = lc ?: return
        setupPaint(s, s.ps[i])
        val old = paint.style
        paint.style = Paint.Style.FILL
        c.drawCircle(pxX(s.xs[i]), pxY(s.ys[i]), widthAt(s, s.ps[i]) / 2f, paint)
        paint.style = old
    }

    private fun drawSeg(s: Stroke, fx: Float, fy: Float, cxp: Float, cyp: Float, tx: Float, ty: Float, pr: Float) {
        val c = lc ?: return
        setupPaint(s, pr)
        val p = android.graphics.Path()
        p.moveTo(fx, fy)
        p.quadTo(cxp, cyp, tx, ty)
        c.drawPath(p, paint)
    }

    private fun replayStroke(s: Stroke) {
        val n = s.xs.size
        if (n == 0) return
        if (n == 1) { drawDot(s, 0); return }
        var fx = pxX(s.xs[0]); var fy = pxY(s.ys[0])
        for (i in 1 until n) {
            val cxp = pxX(s.xs[i - 1]); val cyp = pxY(s.ys[i - 1])
            val tx: Float; val ty: Float
            if (i == n - 1) { tx = pxX(s.xs[i]); ty = pxY(s.ys[i]) }
            else { tx = (cxp + pxX(s.xs[i])) / 2f; ty = (cyp + pxY(s.ys[i])) / 2f }
            drawSeg(s, fx, fy, cxp, cyp, tx, ty, s.ps[i])
            fx = tx; fy = ty
        }
    }

    private fun rebuild() {
        val c = lc ?: return
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        base?.let { c.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
        for (cmd in cmds) when (cmd) {
            is Stroke -> replayStroke(cmd)
            is FillCmd -> flood(cmd.x, cmd.y, cmd.color)
        }
    }

    private fun flatten() {
        val w = width; val h = height
        if (w <= 0 || h <= 0 || cmds.size <= KEEP) return
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val nc = Canvas(nb)
        base?.let { nc.drawBitmap(it, 0f, 0f, null) }
        val savedLayer = layer; val savedCanvas = lc
        layer = nb; lc = nc
        for (i in 0 until cmds.size - KEEP) when (val cmd = cmds[i]) {
            is Stroke -> replayStroke(cmd)
            is FillCmd -> flood(cmd.x, cmd.y, cmd.color)
        }
        layer = savedLayer; lc = savedCanvas
        base?.recycle()
        base = nb
        while (cmds.size > KEEP) cmds.removeAt(0)
    }

    // ---------- دلو التعبئة ----------
    private fun buildWall() {
        wallDirty = false
        val w = width; val h = height
        val t = tpl
        if (t == null || w <= 0 || h <= 0) { wall = null; return }
        val mb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        TemplateRenderer.draw(
            Canvas(mb), t, w.toFloat(), h.toFloat(), Color.BLACK,
            boldFactor = 1.7f, solidDashes = true
        )
        val px = IntArray(w * h)
        mb.getPixels(px, 0, w, 0, 0, w, h)
        mb.recycle()
        wall = BooleanArray(w * h) { (px[it] ushr 24) > 80 }
    }

    private fun near(a: Int, b: Int): Boolean {
        val aa = a ushr 24 and 0xFF; val ba = b ushr 24 and 0xFF
        if (aa < 10 && ba < 10) return true
        if (abs(aa - ba) > TOL) return false
        return abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= TOL &&
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= TOL &&
                abs((a and 0xFF) - (b and 0xFF)) <= TOL
    }

    private fun flood(nx: Float, ny: Float, color: Int) {
        val bmp = layer ?: return
        if (wallDirty) buildWall()
        val w = bmp.width; val h = bmp.height
        val sx = (nx * w).toInt(); val sy = (ny * w).toInt()
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return

        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val block = wall
        val seedIndex = sy * w + sx
        if (block != null && block[seedIndex]) return
        val target = px[seedIndex]
        if (near(target, color)) return

        fun match(i: Int) = (block == null || !block[i]) && near(px[i], target)

        var stack = IntArray(4096)
        var top = 0
        fun push(v: Int) {
            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = v
        }
        push(seedIndex)

        while (top > 0) {
            val idx = stack[--top]
            if (!match(idx)) continue
            val y = idx / w
            val rowStart = y * w
            var xl = idx - rowStart
            var xr = xl
            while (xl > 0 && match(rowStart + xl - 1)) xl--
            while (xr < w - 1 && match(rowStart + xr + 1)) xr++
            for (k in xl..xr) px[rowStart + k] = color
            for (row in intArrayOf(y - 1, y + 1)) {
                if (row < 0 || row >= h) continue
                val rs = row * w
                var k = xl
                while (k <= xr) {
                    if (match(rs + k)) {
                        push(rs + k)
                        while (k <= xr && match(rs + k)) k++
                    } else k++
                }
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    // ---------- اللمس ----------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val type = event.getToolType(idx)
        val stylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        val now = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (stylus) lastStylusAt = now
                // رفض راحة اليد أثناء استخدام القلم
                if (type == MotionEvent.TOOL_TYPE_FINGER && now - lastStylusAt < 1200) return true
                if (activeId != -1) return true                     // إصبع واحد فقط
                parent?.requestDisallowInterceptTouchEvent(true)

                val nx = event.getX(idx) / width
                val ny = event.getY(idx) / width

                if (tool == Tool.FILL) {
                    flood(nx, ny, brushColor)
                    cmds.add(FillCmd(nx, ny, brushColor))
                    if (cmds.size > FLATTEN_AT) flatten()
                    invalidate()
                    notifyHistory()
                    return true
                }

                activeId = event.getPointerId(idx)
                val eraser = tool == Tool.ERASER || type == MotionEvent.TOOL_TYPE_ERASER
                val s = Stroke(
                    color = brushColor,
                    width = (if (eraser) brushPx * 2.4f else brushPx) / width,
                    eraser = eraser,
                    vary = stylus
                )
                s.xs.add(nx); s.ys.add(ny); s.ps.add(if (stylus) event.getPressure(idx).coerceIn(0.05f, 1f) else 0.5f)
                cur = s
                lastMidX = pxX(nx); lastMidY = pxY(ny)
                drawDot(s, 0)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val s = cur ?: return true
                val pi = event.findPointerIndex(activeId)
                if (pi < 0) return true
                if (stylus) lastStylusAt = now
                dirty.setEmpty()
                for (hi in 0 until event.historySize) {
                    addPoint(s, event.getHistoricalX(pi, hi), event.getHistoricalY(pi, hi),
                        event.getHistoricalPressure(pi, hi))
                }
                addPoint(s, event.getX(pi), event.getY(pi), event.getPressure(pi))
                if (!dirty.isEmpty) {
                    val pad = widthAt(s, 1f) + 4f
                    invalidate(
                        (dirty.left - pad).toInt(), (dirty.top - pad).toInt(),
                        (dirty.right + pad).toInt(), (dirty.bottom + pad).toInt()
                    )
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.getPointerId(idx) != activeId) return true
                val s = cur
                if (s != null) {
                    val n = s.xs.size
                    if (n > 1) {
                        val lx = pxX(s.xs[n - 1]); val ly = pxY(s.ys[n - 1])
                        drawSeg(s, lastMidX, lastMidY, lx, ly, lx, ly, s.ps[n - 1])
                    }
                    cmds.add(s)
                    if (cmds.size > FLATTEN_AT) flatten()
                    notifyHistory()
                }
                cur = null
                activeId = -1
                invalidate()
            }
        }
        return true
    }

    private fun addPoint(s: Stroke, x: Float, y: Float, pressure: Float) {
        val nx = x / width; val ny = y / width
        val lastX = pxX(s.xs[s.xs.size - 1]); val lastY = pxY(s.ys[s.ys.size - 1])
        val dx = x - lastX; val dy = y - lastY
        if (dx * dx + dy * dy < 1.2f) return
        val pr = if (s.vary) pressure.coerceIn(0.05f, 1f) else 0.5f
        s.xs.add(nx); s.ys.add(ny); s.ps.add(pr)

        val mx = (lastX + x) / 2f; val my = (lastY + y) / 2f
        drawSeg(s, lastMidX, lastMidY, lastX, lastY, mx, my, pr)

        dirty.union(min(min(lastMidX, lastX), mx), min(min(lastMidY, lastY), my))
        dirty.union(max(max(lastMidX, lastX), mx), max(max(lastMidY, lastY), my))
        lastMidX = mx; lastMidY = my
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
