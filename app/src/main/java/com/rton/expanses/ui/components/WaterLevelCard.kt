package com.rton.expanses.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.*
import kotlin.math.*

/**
 * MIUI-style water level balance card.
 *
 * - Water level = balance ratio (income vs expense)
 * - Color shifts: green (healthy) → yellow (moderate) → red (low/deficit)
 * - Accelerometer tilts the water surface with fluid-like spring physics
 * - Card background tints with status color so state is visible even with no water
 */
@Composable
fun WaterLevelCard(
    expense: Double,
    income: Double,
    modifier: Modifier = Modifier
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("zh", "TW")) }
    val balance = income - expense

    // ─── Water level: 0.0 = empty, 1.0 = full ───────────────
    val rawLevel = if (income > 0) {
        ((income - expense) / income).coerceIn(0.0, 1.0)
    } else if (expense > 0) {
        0.0
    } else {
        0.5 // no data yet
    }
    val animatedLevel by animateFloatAsState(
        targetValue = rawLevel.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "waterLevel"
    )

    // ─── Color: green → yellow → orange → red ───────────────
    val waterColor by animateColorAsState(
        targetValue = when {
            rawLevel > 0.6 -> Color(0xFF4ADE80) // green
            rawLevel > 0.35 -> Color(0xFFFBBF24) // yellow
            rawLevel > 0.15 -> Color(0xFFFB923C) // orange
            else -> Color(0xFFFB7185)             // red
        },
        animationSpec = tween(800),
        label = "waterColor"
    )

    // ─── Accelerometer: raw target angle ─────────────────────
    val context = LocalContext.current
    var targetAngle by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val ax = it.values[0]
                    val az = it.values[2]
                    // Real tilt angle, negated so water stays level with ground
                    targetAngle = -atan2(ax, az)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // ─── Spring-physics fluid simulation ─────────────────────
    // Instead of snapping to targetAngle, we simulate a damped spring
    // so the water "sloshes" with momentum and settles naturally.
    var currentAngle by remember { mutableFloatStateOf(0f) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val dt = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameTime = frameTime

            // Spring: F = -k*(x - target) - damping*velocity
            val stiffness = 25f    // how quickly water reacts
            val damping = 4.5f     // how quickly oscillation dies (lower = more sloshing)
            val springForce = stiffness * (targetAngle - currentAngle)
            val dampingForce = -damping * angularVelocity
            val acceleration = springForce + dampingForce

            angularVelocity += acceleration * dt
            currentAngle += angularVelocity * dt
        }
    }

    // ─── Wave animation ─────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase2"
    )

    // ─── Card background: tinted so state shows even with no water ─
    val cardBgTop = MaterialTheme.colorScheme.surfaceContainerHighest
    val cardBgBottom = waterColor.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(cardBgTop, cardBgBottom)
                )
            )
    ) {
        // ─── Water Canvas ───────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Base water line
            val baseY = h * (1f - animatedLevel * 0.75f)

            // Real tilt with spring physics (clamped to ±40°)
            val clampedAngle = currentAngle.coerceIn(
                -40f * PI.toFloat() / 180f,
                40f * PI.toFloat() / 180f
            )
            val tiltOffset = tan(clampedAngle) * (w / 2f)

            // Layer 1: farthest, slowest, most transparent
            drawWaveLayer(
                width = w,
                height = h,
                baseY = baseY,
                tiltOffset = tiltOffset,
                phase = wavePhase,
                amplitude = 8f,
                frequency = 1.5f,
                color = waterColor.copy(alpha = 0.25f)
            )

            // Layer 2: middle
            drawWaveLayer(
                width = w,
                height = h,
                baseY = baseY + 4f,
                tiltOffset = tiltOffset * 0.85f,
                phase = wavePhase2 + 1.2f,
                amplitude = 6f,
                frequency = 2f,
                color = waterColor.copy(alpha = 0.4f)
            )

            // Layer 3: closest, densest
            drawWaveLayer(
                width = w,
                height = h,
                baseY = baseY + 8f,
                tiltOffset = tiltOffset * 0.7f,
                phase = wavePhase + 2.5f,
                amplitude = 5f,
                frequency = 2.5f,
                color = waterColor.copy(alpha = 0.55f)
            )
        }

        // ─── Text overlay ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: balance
            Column {
                Text(
                    text = "本月餘額",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currencyFormat.format(balance),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Bottom: income / expense
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WaterSummaryItem(
                    label = "收入",
                    amount = currencyFormat.format(income),
                    color = Color(0xFF4ADE80)
                )
                WaterSummaryItem(
                    label = "支出",
                    amount = currencyFormat.format(expense),
                    color = Color(0xFFFB7185)
                )
            }
        }
    }
}

/**
 * Draws a single sine-wave layer filling from wave top to bottom of canvas.
 */
private fun DrawScope.drawWaveLayer(
    width: Float,
    height: Float,
    baseY: Float,
    tiltOffset: Float,
    phase: Float,
    amplitude: Float,
    frequency: Float,
    color: Color
) {
    val path = Path()
    val steps = 80

    for (i in 0..steps) {
        val progress = i.toFloat() / steps
        val x = width * progress
        val tilt = tiltOffset * (1f - 2f * progress) // left→right tilt
        val y = baseY + tilt + amplitude * sin(phase + frequency * 2f * PI.toFloat() * progress)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()

    drawPath(path, color)
}

@Composable
private fun WaterSummaryItem(
    label: String,
    amount: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
