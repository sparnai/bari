package com.sparnai.bari.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparnai.bari.data.RefreshProgress

/** A plane circling a little globe – shown while prices are being checked. */
@Composable
fun PlaneLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "plane")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "orbit",
    )
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val glow by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sun",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val line = Color.White.copy(alpha = 0.45f)
    val sun = Color(0xFFFFB74D)

    Box(modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val orbit = r * 0.81f
            val globe = r * 0.46f
            val c = center
            // Orbit
            drawCircle(
                color = primary.copy(alpha = 0.25f),
                radius = orbit,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))),
            )
            // Trail behind the plane
            drawArc(
                color = primary.copy(alpha = 0.55f),
                startAngle = angle - 90f - 75f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(c.x - orbit, c.y - orbit),
                size = Size(orbit * 2, orbit * 2),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
            // Globe
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tertiary, primary),
                    center = Offset(c.x - globe * 0.35f, c.y - globe * 0.35f),
                    radius = globe * 1.5f,
                ),
                radius = globe,
                center = c,
            )
            val w = 1.5.dp.toPx()
            drawOval(line, Offset(c.x - globe * 0.45f, c.y - globe), Size(globe * 0.9f, globe * 2), style = Stroke(w))
            drawLine(line, Offset(c.x, c.y - globe), Offset(c.x, c.y + globe), strokeWidth = w)
            drawLine(line, Offset(c.x - globe, c.y), Offset(c.x + globe, c.y), strokeWidth = w)
            drawOval(line, Offset(c.x - globe * 0.87f, c.y - globe * 0.62f), Size(globe * 1.74f, globe * 0.5f), style = Stroke(w))
            drawOval(line, Offset(c.x - globe * 0.87f, c.y + globe * 0.12f), Size(globe * 1.74f, globe * 0.5f), style = Stroke(w))
            // Sun
            drawCircle(sun.copy(alpha = 0.35f), radius = globe * 0.36f * glow, center = Offset(c.x + globe * 0.95f, c.y - globe * 0.95f))
            drawCircle(sun, radius = globe * 0.24f, center = Offset(c.x + globe * 0.95f, c.y - globe * 0.95f))
        }
        // The plane rides on the orbit: rotate the whole layer, keep the plane at the top pointing along the path.
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) {
            Icon(
                Icons.Filled.Flight,
                contentDescription = null,
                tint = primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(34.dp)
                    .graphicsLayer {
                        rotationZ = 90f
                        translationX = bob
                    },
            )
        }
    }
}

/** Full-screen layer over the blurred app while prices are checked. */
@Composable
fun RefreshOverlay(progress: RefreshProgress?, onHide: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            // Swallow taps so nothing underneath changes while prices are being written.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlaneLoader()
                Text(
                    "Ieškome pigiausių bilietų…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (progress != null && progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Patikrinta ${progress.done} iš ${progress.total}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    progress?.current ?: "Ryanair · Google Flights · Trenitalia · FlixBus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onHide) { Text("Slėpti – tikrinti fone") }
            }
        }
    }
}

/** Refresh icon that spins while a check is running. */
@Composable
fun RefreshIcon(spinning: Boolean) {
    if (!spinning) {
        Icon(Icons.Filled.Refresh, contentDescription = "Atnaujinti")
        return
    }
    val transition = rememberInfiniteTransition(label = "refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin",
    )
    Icon(Icons.Filled.Refresh, contentDescription = "Tikrinama", modifier = Modifier.rotate(angle))
}
