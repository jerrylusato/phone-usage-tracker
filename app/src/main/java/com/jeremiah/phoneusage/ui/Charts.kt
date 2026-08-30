package com.jeremiah.phoneusage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.jeremiah.phoneusage.util.Format
import java.time.LocalDate

/** Ring used for the "today" hero number. [progress] is clamped to 0..1. */
@Composable
fun UsageRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val indicator = MaterialTheme.colorScheme.primary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = indicator,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Compact bar chart of per-day totals. Days with no data render as an empty track so gaps in
 * collection stay visible instead of silently reading as "no usage".
 */
@Composable
fun DailyBarChart(
    data: List<Pair<LocalDate, Long>>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 26.dp,
    height: Dp = 120.dp,
    selected: LocalDate? = null,
    /** Drawn in the accent colour when nothing is selected — used to mark today. */
    highlight: LocalDate? = null,
    /** Days filled in from Android's daily totals rather than measured, drawn faded. */
    estimated: Set<LocalDate> = emptySet(),
    labels: Boolean = true,
    onSelect: (LocalDate) -> Unit = {},
) {
    if (data.isEmpty()) return
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val faded = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { (day, value) ->
            val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(barWidth).clickable { onSelect(day) },
            ) {
                Box(
                    modifier = Modifier.height(height).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(track),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (value > 0L) fraction.coerceAtLeast(0.04f) else 0f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    day == selected -> primary
                                    selected == null && day == highlight -> primary
                                    day in estimated -> faded
                                    else -> muted
                                },
                            ),
                    )
                }
                if (labels) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = Format.weekday(day).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = if (value > 0L) Format.durationShort(value) else "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Thin bar used inside app rows. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(color),
    )
}
