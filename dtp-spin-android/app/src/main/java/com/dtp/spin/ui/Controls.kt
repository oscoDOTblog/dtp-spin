package com.dtp.spin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** EQ range shared by knob UI and engine (dB). */
const val EQ_MIN_DB = -24f
const val EQ_MAX_DB = 6f

/**
 * Rotary EQ knob: vertical drag adjusts, double-tap resets to 0 dB
 * (plan gesture contract).
 */
@Composable
fun EQKnob(
    valueDb: Float,
    label: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val latest by rememberUpdatedState(valueDb)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .size(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onChange(0f) })
                }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = latest },
                        onVerticalDrag = { _, dy ->
                            acc = (acc - dy * 0.08f).coerceIn(EQ_MIN_DB, EQ_MAX_DB)
                            onChange(acc)
                        },
                    )
                },
        ) {
            val r = size.minDimension / 2 - 4.dp.toPx()
            val c = center
            // Track arc: 270°, from 135° to 45° (gap at bottom).
            drawArc(
                color = Color(0xFF2A323B),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            val frac = (valueDb - EQ_MIN_DB) / (EQ_MAX_DB - EQ_MIN_DB)
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * frac,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            val ang = (135f + 270f * frac) * PI.toFloat() / 180f
            drawLine(
                color = Color.White,
                start = c,
                end = Offset(c.x + cos(ang) * r * 0.7f, c.y + sin(ang) * r * 0.7f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (valueDb == 0f) "0" else "%+.0f".format(valueDb),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** Vertical channel fader, 0..1. */
@Composable
fun ChannelFader(
    value: Float,
    label: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val latest by rememberUpdatedState(value)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(120.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        onChange((latest - dy / h).coerceIn(0f, 1f))
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                drawLine(
                    color = Color(0xFF2A323B),
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val y = size.height * (1f - value.coerceIn(0f, 1f))
                drawLine(
                    color = accent,
                    start = Offset(cx, y),
                    end = Offset(cx, size.height),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cx - 14.dp.toPx(), y),
                    end = Offset(cx + 14.dp.toPx(), y),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Equal-power crossfader, -1 (A) .. +1 (B). Double-tap centers
 * (mirrors the EQ reset gesture).
 */
@Composable
fun Crossfader(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest by rememberUpdatedState(value)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onChange(0f) })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val next = latest + dx / w * 2f
                        onChange(
                            if (kotlin.math.abs(next) < 0.04f) 0f
                            else next.coerceIn(-1f, 1f),
                        )
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2
                drawLine(
                    color = Color(0xFF2A323B),
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val x = size.width * ((value.coerceIn(-1f, 1f) + 1f) / 2f)
                drawLine(
                    color = Color.White,
                    start = Offset(x, cy - 14.dp.toPx()),
                    end = Offset(x, cy + 14.dp.toPx()),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                // Center detent tick.
                drawLine(
                    color = Color(0xFF5A6672),
                    start = Offset(size.width / 2, cy - 6.dp.toPx()),
                    end = Offset(size.width / 2, cy + 6.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Text(
            "A ◄ ► B",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
