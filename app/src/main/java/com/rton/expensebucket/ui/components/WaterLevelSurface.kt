package com.rton.expensebucket.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.ui.theme.BerryYogurtWaterBerryDark
import com.rton.expensebucket.ui.theme.BerryYogurtWaterBerryLight
import com.rton.expensebucket.ui.theme.BerryYogurtWaterCoralDark
import com.rton.expensebucket.ui.theme.BerryYogurtWaterCoralLight
import com.rton.expensebucket.ui.theme.BerryYogurtWaterGoldenDark
import com.rton.expensebucket.ui.theme.BerryYogurtWaterGoldenLight
import com.rton.expensebucket.ui.theme.BerryYogurtWaterMatchaDark
import com.rton.expensebucket.ui.theme.BerryYogurtWaterMatchaLight
import com.rton.expensebucket.ui.theme.LatteCaramelDark
import com.rton.expensebucket.ui.theme.LatteCaramelLight
import com.rton.expensebucket.ui.theme.LatteHojichaLight
import com.rton.expensebucket.ui.theme.LatteOatDark
import com.rton.expensebucket.ui.theme.LatteSageDark
import com.rton.expensebucket.ui.theme.LatteSageLight
import com.rton.expensebucket.ui.theme.LatteTerracottaDark
import com.rton.expensebucket.ui.theme.LatteTerracottaLight
import com.rton.expensebucket.ui.theme.StrawberryWaterBerryDark
import com.rton.expensebucket.ui.theme.StrawberryWaterBerryLight
import com.rton.expensebucket.ui.theme.StrawberryWaterApricotDark
import com.rton.expensebucket.ui.theme.StrawberryWaterApricotLight
import com.rton.expensebucket.ui.theme.StrawberryWaterHoneyDark
import com.rton.expensebucket.ui.theme.StrawberryWaterHoneyLight
import com.rton.expensebucket.ui.theme.StrawberryWaterMatchaDark
import com.rton.expensebucket.ui.theme.StrawberryWaterMatchaLight
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

@Stable
class WaterMotionState internal constructor() {
    var targetAngle by mutableFloatStateOf(0f)
    var currentAngle by mutableFloatStateOf(0f)
    var angularVelocity by mutableFloatStateOf(0f)
    var sloshOffset by mutableFloatStateOf(0f)
    var sloshVelocity by mutableFloatStateOf(0f)
}

@Composable
fun rememberWaterMotionState(): WaterMotionState {
    val context = LocalContext.current
    val state = remember { WaterMotionState() }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val ax = it.values[0]
                    val az = it.values[2]
                    state.targetAngle = -atan2(ax, az)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(state) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val dt = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameTime = frameTime

            val stiffness = 20f
            val damping = 4.5f
            val springForce = stiffness * (state.targetAngle - state.currentAngle)
            val dampingForce = -damping * state.angularVelocity
            val accel = springForce + dampingForce
            state.angularVelocity += accel * dt
            state.currentAngle += state.angularVelocity * dt

            val sloshStiffness = 35f
            val sloshDamping = 3f
            val sloshDrive = accel * 0.8f
            val sloshAccel =
                -sloshStiffness * state.sloshOffset - sloshDamping * state.sloshVelocity + sloshDrive
            state.sloshVelocity += sloshAccel * dt
            state.sloshOffset += state.sloshVelocity * dt
            state.sloshOffset = state.sloshOffset.coerceIn(-1f, 1f)
        }
    }

    return state
}

@Composable
fun WaterLevelSurface(
    level: Float,
    waterColor: Color,
    modifier: Modifier = Modifier,
    motionState: WaterMotionState = rememberWaterMotionState(),
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1200),
        label = "waterLevelSurfaceLevel"
    )
    val waveTransition = rememberInfiniteTransition(label = "waterLevelSurfaceWave")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waterLevelSurfacePhase"
    )
    val currentColor by rememberUpdatedState(waterColor)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        currentColor.copy(alpha = 0.05f),
                        currentColor.copy(alpha = 0.15f)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawWaterLayers(
                size = size,
                level = animatedLevel,
                waterColor = currentColor,
                currentAngle = motionState.currentAngle,
                angularVelocity = motionState.angularVelocity,
                sloshOffset = motionState.sloshOffset,
                wavePhase = wavePhase
            )
        }
        content()
    }
}

fun waterLevelColor(level: Double, palette: AppPalette, isDarkTheme: Boolean): Color {
    val normalizedLevel = level.toFloat().coerceIn(0f, 1f)
    when (palette) {
        AppPalette.LATTE -> {
            val (c1, c2, c3, c4) = if (isDarkTheme) {
                listOf(LatteSageDark, LatteOatDark, LatteCaramelDark, LatteTerracottaDark)
            } else {
                listOf(LatteSageLight, LatteHojichaLight, LatteCaramelLight, LatteTerracottaLight)
            }
            return interpolateWaterColors(normalizedLevel, c1, c2, c3, c4)
        }

        AppPalette.STRAWBERRY_MILK -> {
            val (c1, c2, c3, c4) = if (isDarkTheme) {
                listOf(
                    StrawberryWaterMatchaDark,
                    StrawberryWaterHoneyDark,
                    StrawberryWaterApricotDark,
                    StrawberryWaterBerryDark
                )
            } else {
                listOf(
                    StrawberryWaterMatchaLight,
                    StrawberryWaterHoneyLight,
                    StrawberryWaterApricotLight,
                    StrawberryWaterBerryLight
                )
            }
            return interpolateWaterColors(normalizedLevel, c1, c2, c3, c4)
        }

        AppPalette.BERRY_YOGURT -> {
            val (c1, c2, c3, c4) = if (isDarkTheme) {
                listOf(
                    BerryYogurtWaterMatchaDark,
                    BerryYogurtWaterGoldenDark,
                    BerryYogurtWaterCoralDark,
                    BerryYogurtWaterBerryDark
                )
            } else {
                listOf(
                    BerryYogurtWaterMatchaLight,
                    BerryYogurtWaterGoldenLight,
                    BerryYogurtWaterCoralLight,
                    BerryYogurtWaterBerryLight
                )
            }
            return interpolateWaterColors(normalizedLevel, c1, c2, c3, c4)
        }

        AppPalette.DEFAULT -> Unit
    }

    return interpolateWaterColors(
        level = normalizedLevel,
        c1 = Color(0xFF4ADE80),
        c2 = Color(0xFFFBBF24),
        c3 = Color(0xFFFB923C),
        c4 = Color(0xFFFB7185)
    )
}

private fun DrawScope.drawWaterLayers(
    size: Size,
    level: Float,
    waterColor: Color,
    currentAngle: Float,
    angularVelocity: Float,
    sloshOffset: Float,
    wavePhase: Float
) {
    val width = size.width
    val height = size.height
    val waterHeight = height * level * 0.95f

    val glowAlpha = 0.08f + level * 0.12f
    val glowBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            waterColor.copy(alpha = glowAlpha * 0.3f),
            waterColor.copy(alpha = glowAlpha)
        ),
        startY = height * 0.2f,
        endY = height
    )
    drawRect(brush = glowBrush)

    if (waterHeight < 1f) return

    val clampedAngle = currentAngle.coerceIn(
        -70f * PI.toFloat() / 180f,
        70f * PI.toFloat() / 180f
    )
    val rawTiltOffset = tan(clampedAngle) * (width / 2f)
    val maxDisplacement = min(waterHeight, height - (height - waterHeight))
    val tiltOffset = rawTiltOffset.coerceIn(-maxDisplacement, maxDisplacement)

    drawFluidLayer(
        width = width,
        height = height,
        waterHeight = waterHeight,
        tiltOffset = tiltOffset,
        sloshOffset = sloshOffset,
        angularVelocity = angularVelocity,
        wavePhase = wavePhase,
        baseAmplitude = 12f,
        frequency = 1.8f,
        layerYShift = 0f,
        tiltScale = 1f,
        color = waterColor.copy(alpha = 0.3f)
    )
    drawFluidLayer(
        width = width,
        height = height,
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
        width = width,
        height = height,
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
        val t = i.toFloat() / steps
        val x = width * t
        val tiltY = effectiveTilt * (1f - 2f * t)
        val sloshAmplitude = waterHeight * 0.5f
        val standingWave = sloshOffset * sloshAmplitude * sin(PI * t).toFloat()
        val surfaceY = baseY + tiltY + standingWave
        val localDepth = (height - surfaceY).coerceAtLeast(0f)
        val depthRatio = (localDepth / height).coerceIn(0f, 1f)
        val dynamicAmplitude = baseAmplitude * depthRatio * depthRatio
        val velocityBoost = min(kotlin.math.abs(angularVelocity) * 14f, 35f) * depthRatio
        val totalAmplitude = dynamicAmplitude + velocityBoost
        val ripple = totalAmplitude * sin(wavePhase + frequency * 2f * PI.toFloat() * t)
        val y = surfaceY + ripple
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()
    drawPath(path, color)
}

private fun interpolateWaterColors(level: Float, c1: Color, c2: Color, c3: Color, c4: Color): Color {
    return when {
        level >= 0.6f -> {
            val fraction = (level - 0.6f) / (1f - 0.6f)
            androidx.compose.ui.graphics.lerp(c2, c1, fraction)
        }
        level >= 0.35f -> {
            val fraction = (level - 0.35f) / (0.6f - 0.35f)
            androidx.compose.ui.graphics.lerp(c3, c2, fraction)
        }
        level >= 0.15f -> {
            val fraction = (level - 0.15f) / (0.35f - 0.15f)
            androidx.compose.ui.graphics.lerp(c4, c3, fraction)
        }
        else -> c4
    }
}
