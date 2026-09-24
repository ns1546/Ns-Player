package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

enum class VisualUniverse(val title: String) {
    NONE("Normal Background"),
    GALAXY("Galaxy"),
    OCEAN("Ocean"),
    CYBERPUNK("Cyberpunk City"),
    SPACE_STATION("Space Station"),
    FOREST("Forest")
}

@Composable
fun VisualUniverseBackground(universe: VisualUniverse, isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "universe")
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 500 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "time"
    )

    when (universe) {
        VisualUniverse.GALAXY -> GalaxyUniverse(pulse, time)
        VisualUniverse.OCEAN -> OceanUniverse(pulse, time)
        VisualUniverse.CYBERPUNK -> CyberpunkUniverse(pulse, time)
        VisualUniverse.SPACE_STATION -> SpaceStationUniverse(pulse, time)
        VisualUniverse.FOREST -> ForestUniverse(pulse, time)
        VisualUniverse.NONE -> { }
    }
}

@Composable
fun GalaxyUniverse(pulse: Float, time: Float) {
    val stars = remember { List(150) { Offset(Random.nextFloat(), Random.nextFloat()) to Random.nextFloat() * 4f } }
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0D17))) {
        val w = size.width
        val h = size.height
        stars.forEachIndexed { index, (pos, radius) ->
            val x = (pos.x * w + time * 10f * (index % 3)) % w
            val y = (pos.y * h + time * 5f * (index % 2)) % h
            drawCircle(
                color = Color.White.copy(alpha = (0.3f + (pulse - 0.9f) * 2f).coerceIn(0f, 1f)),
                radius = radius * pulse,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun OceanUniverse(pulse: Float, time: Float) {
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF001F3F))) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        for (i in 0..2) {
            val waveHeight = 35f * pulse + (i * 15f)
            val yOffset = h / 2f + (i * 80f) - 60f
            var x = 0f
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, yOffset)
            while (x <= w + 30f) {
                val y = yOffset + sin((x / 120f) + time + i) * waveHeight
                path.lineTo(x, y)
                x += 32f
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            drawPath(
                path = path,
                color = Color(0xFF0074D9).copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
fun CyberpunkUniverse(pulse: Float, time: Float) {
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF111111))) {
        val w = size.width
        val h = size.height
        
        // Sun
        drawCircle(
            color = Color(0xFFFF2A6D),
            radius = 150f * pulse,
            center = Offset(w / 2, h / 2 - 100f)
        )
        
        // Grid
        val gridY = h / 2
        for (i in 0..20) {
            val y = gridY + (i * 30f * pulse) % (h / 2)
            drawLine(
                color = Color(0xFF05D9E8),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 2f
            )
        }
        for (i in 0..10) {
            val x = (i * w / 10)
            drawLine(
                color = Color(0xFF05D9E8),
                start = Offset(w / 2, gridY),
                end = Offset(x, h),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun SpaceStationUniverse(pulse: Float, time: Float) {
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF090A0F))) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        
        drawCircle(
            color = Color(0xFF39FF14).copy(alpha = 0.2f),
            radius = 200f * pulse,
            center = Offset(cx, cy),
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = Color(0xFF39FF14).copy(alpha = 0.1f),
            radius = 300f * pulse,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        
        val angle = time * 20f
        val rad = angle * PI / 180f
        val ex = cx + cos(rad).toFloat() * 300f
        val ey = cy + sin(rad).toFloat() * 300f
        drawLine(
            color = Color(0xFF39FF14),
            start = Offset(cx, cy),
            end = Offset(ex, ey),
            strokeWidth = 4f
        )
    }
}

@Composable
fun ForestUniverse(pulse: Float, time: Float) {
    val leaves = remember { List(80) { Offset(Random.nextFloat(), Random.nextFloat()) to Random.nextFloat() * 15f } }
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0B2404))) {
        val w = size.width
        val h = size.height
        leaves.forEachIndexed { index, (pos, radius) ->
            val x = (pos.x * w + sin(time + index) * 50f) % w
            var y = (pos.y * h + time * 20f * (index % 3 + 1)) % h
            if (y < 0) y += h
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = (0.5f + (pulse - 0.9f)).coerceIn(0f, 1f)),
                radius = radius * pulse,
                center = Offset(x, y)
            )
        }
    }
}
