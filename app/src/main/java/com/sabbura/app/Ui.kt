package com.sabbura.app

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// ---------------- الألوان ----------------
val BgTop = Color(0xFFEAEFFA)
val BgBottom = Color(0xFFD9E2F4)
val Ink = Color(0xFF3A4363)
val InkSoft = Color(0xFF9AA2BF)
val Accent = Color(0xFF4C5FC4)
val Chip = Color(0xFFF2F4FB)
val Danger = Color(0xFFE9575F)
val Good = Color(0xFF4CAF7D)

val Palette = listOf(
    0xFF2F3548, 0xFFE9575F, 0xFFF4913E, 0xFFF6C945, 0xFF5BC47F,
    0xFF3FBFC4, 0xFF4A8DF0, 0xFF9B6BD6, 0xFFF07CB0, 0xFFA97355
).map { Color(it) }

private val BRUSH_DP = listOf(6f, 14f, 28f)

@Composable
fun SabburaApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent, background = BgTop, surface = Color.White, onSurface = Ink
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val density = LocalDensity.current
    var board by remember { mutableStateOf<BoardView?>(null) }
    var tool by remember { mutableStateOf(BoardView.Tool.PEN) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var sizeIndex by remember { mutableIntStateOf(1) }
    var templateId by remember { mutableStateOf<String?>(null) }
    var canUndo by remember { mutableStateOf(false) }
    var hasContent by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun askThen(action: () -> Unit) {
        if (!hasContent) action() else confirmAction = action
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .safeDrawingPadding()
            .padding(12.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight && maxWidth > 700.dp

            val boardBlock: @Composable (Modifier) -> Unit = { mod ->
                Surface(
                    modifier = mod,
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 10.dp
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            BoardView(ctx).also { v ->
                                v.onHistoryChanged = { u, c -> canUndo = u; hasContent = c }
                                board = v
                            }
                        },
                        update = { v ->
                            v.tool = tool
                            v.brushColor = Palette[colorIndex].toArgb()
                            v.brushPx = with(density) { BRUSH_DP[sizeIndex].dp.toPx() }
                            v.setTemplate(Art.byId(templateId))
                        }
                    )
                }
            }

            val barBlock: @Composable (Modifier) -> Unit = { mod ->
                Toolbar(
                    modifier = mod,
                    vertical = wide,
                    tool = tool,
                    colorIndex = colorIndex,
                    sizeIndex = sizeIndex,
                    canUndo = canUndo,
                    onTool = { tool = it },
                    onColor = { colorIndex = it; if (tool == BoardView.Tool.ERASER) tool = BoardView.Tool.PEN },
                    onSize = { sizeIndex = it },
                    onPictures = { pickerOpen = true },
                    onUndo = { board?.undo() },
                    onClear = { askThen { board?.clearAll() } }
                )
            }

            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    barBlock(Modifier.width(240.dp).fillMaxHeight())
                    boardBlock(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    boardBlock(Modifier.weight(1f).fillMaxWidth())
                    barBlock(Modifier.fillMaxWidth())
                }
            }
        }

        if (pickerOpen) {
            TemplatePicker(
                onPick = { id ->
                    pickerOpen = false
                    askThen { board?.clearAll(); templateId = id }
                },
                onClose = { pickerOpen = false }
            )
        }

        confirmAction?.let { action ->
            ConfirmSheet(
                onYes = { confirmAction = null; action() },
                onNo = { confirmAction = null }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*                              الأدوات                                */
/* ------------------------------------------------------------------ */

@Composable
private fun Toolbar(
    modifier: Modifier,
    vertical: Boolean,
    tool: BoardView.Tool,
    colorIndex: Int,
    sizeIndex: Int,
    canUndo: Boolean,
    onTool: (BoardView.Tool) -> Unit,
    onColor: (Int) -> Unit,
    onSize: (Int) -> Unit,
    onPictures: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .then(if (vertical) Modifier.fillMaxSize() else Modifier.fillMaxWidth()),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            val perRow = if (vertical) 4 else 5
            Palette.chunked(perRow).forEachIndexed { rowIdx, row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEachIndexed { j, c ->
                        val i = rowIdx * perRow + j
                        ColorDot(
                            color = c,
                            selected = i == colorIndex,
                            modifier = Modifier.weight(1f),
                            onClick = { onColor(i) }
                        )
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(2.dp))

            if (vertical) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolBtn(Ic.PEN, tool == BoardView.Tool.PEN, Modifier.weight(1f)) { onTool(BoardView.Tool.PEN) }
                    ToolBtn(Ic.ERASER, tool == BoardView.Tool.ERASER, Modifier.weight(1f)) { onTool(BoardView.Tool.ERASER) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolBtn(Ic.DROP, tool == BoardView.Tool.FILL, Modifier.weight(1f), filled = true) { onTool(BoardView.Tool.FILL) }
                    ToolBtn(Ic.IMAGE, false, Modifier.weight(1f), onClick = onPictures)
                }
                SizePicker(sizeIndex, Modifier.fillMaxWidth(), onSize)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolBtn(Ic.UNDO, false, Modifier.weight(1f), enabled = canUndo, onClick = onUndo)
                    ToolBtn(Ic.TRASH, false, Modifier.weight(1f), onClick = onClear)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolBtn(Ic.PEN, tool == BoardView.Tool.PEN, Modifier.weight(1f)) { onTool(BoardView.Tool.PEN) }
                    ToolBtn(Ic.ERASER, tool == BoardView.Tool.ERASER, Modifier.weight(1f)) { onTool(BoardView.Tool.ERASER) }
                    ToolBtn(Ic.DROP, tool == BoardView.Tool.FILL, Modifier.weight(1f), filled = true) { onTool(BoardView.Tool.FILL) }
                    SizePicker(sizeIndex, Modifier.weight(1.6f), onSize)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolBtn(Ic.IMAGE, false, Modifier.weight(1f), onClick = onPictures)
                    ToolBtn(Ic.UNDO, false, Modifier.weight(1f), enabled = canUndo, onClick = onUndo)
                    ToolBtn(Ic.TRASH, false, Modifier.weight(1f), onClick = onClear)
                }
            }
        }
    }
}

@Composable
private fun ToolBtn(
    icon: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(), label = "btn")
    Surface(
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Accent else Chip
    ) {
        Box(contentAlignment = Alignment.Center) {
            VIcon(
                data = icon,
                color = if (!enabled) InkSoft.copy(alpha = 0.5f) else if (selected) Color.White else Ink,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2f,
                filled = filled
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(), label = "dot")
    Box(
        modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(CircleShape)
            .background(if (selected) Accent else Color.Transparent)
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(if (selected) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(color)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
    )
}

@Composable
private fun SizePicker(selected: Int, modifier: Modifier, onSize: (Int) -> Unit) {
    val view = LocalView.current
    Surface(modifier = modifier.height(54.dp), shape = RoundedCornerShape(18.dp), color = Chip) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BRUSH_DP.forEachIndexed { i, d ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSize(i)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size((d * 0.85f).dp.coerceAtLeast(8.dp))
                            .clip(CircleShape)
                            .background(if (on) Accent else InkSoft)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*                          قائمة الرسومات                             */
/* ------------------------------------------------------------------ */

@Composable
private fun TemplatePicker(onPick: (String?) -> Unit, onClose: () -> Unit) {
    var cat by remember { mutableStateOf(Art.CATEGORIES.first()) }
    Box(
        Modifier
            .fillMaxSize()
            .background(BgTop.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabBtn(Ic.IMAGE, cat == "draw", Modifier.weight(1f)) { cat = "draw" }
                    TabBtn(Ic.SHAPES, cat == "shape", Modifier.weight(1f)) { cat = "shape" }
                    TabBtn(Ic.LINES, cat == "line", Modifier.weight(1f)) { cat = "line" }
                    TabBtn(Ic.PAGE, false, Modifier.weight(1f)) { onPick(null) }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(Art.of(cat)) { t ->
                        TemplateCard(t) { onPick(t.id) }
                    }
                }

                ToolBtn(Ic.CLOSE, true, Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
    }
}

@Composable
private fun TabBtn(icon: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ToolBtn(icon, selected, modifier, onClick = onClick)
}

@Composable
private fun TemplateCard(t: Tpl, onClick: () -> Unit) {
    val view = LocalView.current
    val lineArgb = Ink.toArgb()
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        color = Chip
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            drawIntoCanvas { c ->
                TemplateRenderer.draw(
                    c.nativeCanvas, t, size.width, size.height, lineArgb, boldFactor = 1.35f
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*                            تأكيد المسح                              */
/* ------------------------------------------------------------------ */

@Composable
private fun ConfirmSheet(onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgTop.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 12.dp) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                VIcon(Ic.TRASH, InkSoft, Modifier.size(46.dp), strokeWidth = 2f)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BigBtn(Ic.CLOSE, Chip, Ink, onNo)
                    BigBtn(Ic.CHECK, Danger, Color.White, onYes)
                }
            }
        }
    }
}

@Composable
private fun BigBtn(icon: String, bg: Color, fg: Color, onClick: () -> Unit) {
    val view = LocalView.current
    Surface(
        modifier = Modifier
            .size(width = 84.dp, height = 66.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(22.dp),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            VIcon(icon, fg, Modifier.size(32.dp), strokeWidth = 2.6f)
        }
    }
}
