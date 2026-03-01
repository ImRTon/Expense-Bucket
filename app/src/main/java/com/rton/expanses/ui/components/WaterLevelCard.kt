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
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.*
import kotlin.math.*

/**
 * MIUI-style water level balance card with realistic fluid simulation.
 *
 * - Water level = balance ratio (income vs expense)
 * - Color shifts: green (healthy) → yellow → orange → red (deficit)
 * - Accelerometer tilts water with spring-physics momentum
 * - Waves are depth-dependent: taller where water is deep, absent where shallow
 * - Standing-wave sloshing creates visible momentum when device moves
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
    // Simulates a damped spring so the water "sloshes" with inertia.
    var currentAngle by remember { mutableFloatStateOf(0f) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }
    // Separate sloshing oscillator: models the fundamental standing wave
    // in the container (water rocking back and forth as a single hump)
    var sloshOffset by remember { mutableFloatStateOf(0f) }
    var sloshVelocity by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val dt = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameTime = frameTime

            // ── Tilt spring ──
            val stiffness = 20f
            val damping = 4.5f
            val springForce = stiffness * (targetAngle - currentAngle)
            val dampingForce = -damping * angularVelocity
            val accel = springForce + dampingForce
            angularVelocity += accel * dt
            currentAngle += angularVelocity * dt

            // ── Slosh spring (driven by angular acceleration) ──
            // Angular acceleration pumps energy into a standing wave.
            // This oscillator has its own natural frequency and damping,
            // giving water that characteristic "rock back and forth" motion.
            val sloshStiffness = 35f   // natural frequency of the slosh
            val sloshDamping = 3.0f    // how quickly sloshing dies
            val sloshDrive = accel * 0.8f  // acceleration drives the sloshing
            val sloshAccel = -sloshStiffness * sloshOffset - sloshDamping * sloshVelocity + sloshDrive
            sloshVelocity += sloshAccel * dt
            sloshOffset += sloshVelocity * dt
            // Clamp to prevent runaway
            sloshOffset = sloshOffset.coerceIn(-1f, 1f)
        }
    }

    // ─── Wave animation (subtle ambient ripple) ──────────────
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // ─── Card background: tinted so state shows even with no water ─
    val cardBgTop = waterColor.copy(alpha = 0.05f)
    val cardBgBottom = waterColor.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        cardBgTop,
                        cardBgBottom
                    )
                )
            )
    ) {
        // ─── Water Canvas ───────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Total water height in pixels (0 = empty, 0.75*h = full)
            val waterHeight = h * animatedLevel * 0.75f

            // ── Ambient glow: water emits light so the card never goes dark ──
            // Always draw a soft glow from the bottom, tinted with waterColor.
            // Intensity scales with water level but has a minimum so even empty cards glow.
            val glowAlpha = 0.08f + animatedLevel * 0.12f  // 0.08 when empty, 0.20 when full
            val glowBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    waterColor.copy(alpha = glowAlpha * 0.3f),
                    waterColor.copy(alpha = glowAlpha)
                ),
                startY = h * 0.2f,
                endY = h
            )
            drawRect(brush = glowBrush)

            if (waterHeight < 1f) return@Canvas // no fluid to simulate

            // ── Tilt calculation ──
            val clampedAngle = currentAngle.coerceIn(
                -70f * PI.toFloat() / 180f,
                70f * PI.toFloat() / 180f
            )
            val rawTiltOffset = tan(clampedAngle) * (w / 2f)
            // Limit tilt to available water volume
            val maxDisplacement = min(waterHeight, h - (h - waterHeight))
            val tiltOffset = rawTiltOffset.coerceIn(-maxDisplacement, maxDisplacement)

            // ── Draw 3 wave layers with depth-aware rendering ──
            drawFluidLayer(
                width = w, height = h,
                waterHeight = waterHeight,
                tiltOffset = tiltOffset,
                sloshOffset = sloshOffset,
                angularVelocity = angularVelocity,
                wavePhase = wavePhase,
                baseAmplitude = 12f,
                frequency = 1.8f,
                layerYShift = 0f,
                tiltScale = 1.0f,
                color = waterColor.copy(alpha = 0.3f)
            )

            drawFluidLayer(
                width = w, height = h,
                waterHeight = waterHeight,
                tiltOffset = tiltOffset,
                sloshOffset = sloshOffset,
                angularVelocity = angularVelocity,
                wavePhase = wavePhase + 1.8f,
                baseAmplitude = 9f,
                frequency = 2.5f,
                layerYShift = 5f,
                tiltScale = 0.88f,
                color = waterColor.copy(alpha = 0.45f)
            )

            drawFluidLayer(
                width = w, height = h,
                waterHeight = waterHeight,
                tiltOffset = tiltOffset,
                sloshOffset = sloshOffset,
                angularVelocity = angularVelocity,
                wavePhase = wavePhase + 3.5f,
                baseAmplitude = 7f,
                frequency = 3.2f,
                layerYShift = 10f,
                tiltScale = 0.75f,
                color = waterColor.copy(alpha = 0.6f)
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
 * Draws a single fluid layer with physically-correct behavior:
 *
 * 1. The water surface tilts based on device angle (tiltOffset)
 * 2. Wave amplitude is proportional to local water depth (no waves where there's no water)
 * 3. A standing-wave sloshing component makes the water rock side-to-side with momentum
 * 4. Small ambient ripples add surface texture
 */
private fun DrawScope.drawFluidLayer(
    width: Float,
    height: Float,
    waterHeight: Float,
    tiltOffset: Float,
    sloshOffset: Float,
    angularVelocity: Float,
    wavePhase: Float,
    baseAmplitude: Float,
    frequency: Float,
    layerYShift: Float,
    tiltScale: Float,
    color: Color
) {
    val path = Path()
    val steps = 80
    val baseY = height - waterHeight + layerYShift
    val effectiveTilt = tiltOffset * tiltScale

    for (i in 0..steps) {
        val t = i.toFloat() / steps    // 0.0 (left edge) → 1.0 (right edge)
        val x = width * t

        // ── 1. Tilt: tilted water surface (linear, like real gravity) ──
        // At t=0 (left): +tilt, at t=1 (right): -tilt
        // This means positive tiltOffset raises the left edge.
        val tiltY = effectiveTilt * (1f - 2f * t)

        // ── 2. Standing wave sloshing (momentum) ──
        // Fundamental sloshing mode: sin(π·t) creates a single hump
        // that oscillates based on the slosh spring state.
        // This is what makes water visibly "rush" to one side.
        val sloshAmplitude = waterHeight * 0.5f // up to 50% of water height
        val standingWave = sloshOffset * sloshAmplitude * sin(PI * t).toFloat()

        // ── 3. Calculate local water depth at this x position ──
        // Water depth = how far from this y-point to the bottom
        val surfaceY = baseY + tiltY + standingWave
        val localDepth = (height - surfaceY).coerceAtLeast(0f)
        // Normalize: 0 = dry, 1 = max depth
        val depthRatio = (localDepth / height).coerceIn(0f, 1f)

        // ── 4. Depth-proportional ripple waves ──
        // Waves are only visible where there's water.
        // Deeper water = taller waves. Shallow/dry = flat surface.
        val dynamicAmplitude = baseAmplitude * depthRatio * depthRatio
        // Velocity-boosted waves: sloshing makes waves more chaotic
        val velocityBoost = min(abs(angularVelocity) * 14f, 35f) * depthRatio
        val totalAmplitude = dynamicAmplitude + velocityBoost

        val ripple = totalAmplitude * sin(
            wavePhase + frequency * 2f * PI.toFloat() * t
        )

        val y = surfaceY + ripple
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    // Close the path along the bottom edge
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
