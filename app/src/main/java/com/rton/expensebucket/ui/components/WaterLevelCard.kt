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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
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
import com.rton.expanses.ui.viewmodel.CompareMode
import com.rton.expanses.ui.viewmodel.PeriodSummary
import com.rton.expanses.ui.viewmodel.TimePeriod
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.*
import kotlin.math.*

/**
 * MIUI-style water level card with swipeable time periods and compare mode toggle.
 */
@Composable
fun WaterLevelCard(
    periodData: Map<TimePeriod, PeriodSummary>,
    monthlyBudget: Double,
    selectedPage: Int = 2,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val periods = TimePeriod.entries
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { periods.size }
    )
    val currentPeriod = periods[pagerState.currentPage]

    // Sync: external selectedPage -> pager
    LaunchedEffect(selectedPage) {
        if (pagerState.currentPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    // Sync: pager swipe -> external callback
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChanged(page)
        }
    }

    // Compare mode state — force EXPENSE_INCOME on ALL period
    var compareMode by remember { mutableStateOf(CompareMode.EXPENSE_INCOME) }
    val effectiveMode = if (currentPeriod == TimePeriod.ALL) {
        CompareMode.EXPENSE_INCOME
    } else {
        compareMode
    }

    val summary = periodData[currentPeriod] ?: PeriodSummary()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("zh", "TW")) }

    // ─── Water level + color calculation ──────────────────────────
    val displayData = computeDisplayData(effectiveMode, summary, monthlyBudget, currentPeriod)

    val animatedLevel by animateFloatAsState(
        targetValue = displayData.level.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "waterLevel"
    )

    val waterColor by animateColorAsState(
        targetValue = when {
            displayData.level > 0.6 -> Color(0xFF4ADE80)
            displayData.level > 0.35 -> Color(0xFFFBBF24)
            displayData.level > 0.15 -> Color(0xFFFB923C)
            else -> Color(0xFFFB7185)
        },
        animationSpec = tween(800),
        label = "waterColor"
    )

    // ─── Accelerometer ───────────────────────────────────────────
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

    // ─── Spring-physics fluid simulation ─────────────────────────
    var currentAngle by remember { mutableFloatStateOf(0f) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }
    var sloshOffset by remember { mutableFloatStateOf(0f) }
    var sloshVelocity by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            val frameTime = withFrameNanos { it }
            val dt = ((frameTime - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameTime = frameTime

            val stiffness = 20f
            val damping = 4.5f
            val springForce = stiffness * (targetAngle - currentAngle)
            val dampingForce = -damping * angularVelocity
            val accel = springForce + dampingForce
            angularVelocity += accel * dt
            currentAngle += angularVelocity * dt

            val sloshStiffness = 35f
            val sloshDamping = 3.0f
            val sloshDrive = accel * 0.8f
            val sloshAccel = -sloshStiffness * sloshOffset - sloshDamping * sloshVelocity + sloshDrive
            sloshVelocity += sloshAccel * dt
            sloshOffset += sloshVelocity * dt
            sloshOffset = sloshOffset.coerceIn(-1f, 1f)
        }
    }

    // ─── Wave phase ──────────────────────────────────────────────
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

    // ─── Card background ─────────────────────────────────────────
    val cardBgTop = waterColor.copy(alpha = 0.05f)
    val cardBgBottom = waterColor.copy(alpha = 0.15f)

    // ─── Layout: HorizontalPager is the root swipe container ─────
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        cardBgTop,
                        cardBgBottom
                    )
                )
            ),
        // Keep adjacent pages alive so swipe animation shows background
        beyondViewportPageCount = 1,
        key = { periods[it].name }
    ) { pageIndex ->
        // Each page: same layout, data changes based on pageIndex
        val pagePeriod = periods[pageIndex]
        val pageMode = if (pagePeriod == TimePeriod.ALL) CompareMode.EXPENSE_INCOME else compareMode
        val pageSummary = periodData[pagePeriod] ?: PeriodSummary()
        val pageDisplay = computeDisplayData(pageMode, pageSummary, monthlyBudget, pagePeriod)

        val pageBalance = when (pageMode) {
            CompareMode.EXPENSE_INCOME -> pageSummary.income - pageSummary.expense
            CompareMode.EXPENSE_BUDGET -> {
                val periodBudget = pageDisplay.secondaryValue
                if (periodBudget > 0) periodBudget - pageSummary.expense else 0.0
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // ─── Water Canvas (shared physics, per-page level) ───
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Use the animated level for the current page, raw level for others
                val level = if (pageIndex == pagerState.currentPage) animatedLevel
                            else pageDisplay.level.toFloat()
                val waterHeight = h * level * 0.75f

                val color = when {
                    pageDisplay.level > 0.6 -> Color(0xFF4ADE80)
                    pageDisplay.level > 0.35 -> Color(0xFFFBBF24)
                    pageDisplay.level > 0.15 -> Color(0xFFFB923C)
                    else -> Color(0xFFFB7185)
                }

                val glowAlpha = 0.08f + level * 0.12f
                val glowBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = glowAlpha * 0.3f),
                        color.copy(alpha = glowAlpha)
                    ),
                    startY = h * 0.2f,
                    endY = h
                )
                drawRect(brush = glowBrush)

                if (waterHeight < 1f) return@Canvas

                val clampedAngle = currentAngle.coerceIn(
                    -70f * PI.toFloat() / 180f,
                    70f * PI.toFloat() / 180f
                )
                val rawTiltOffset = tan(clampedAngle) * (w / 2f)
                val maxDisplacement = min(waterHeight, h - (h - waterHeight))
                val tiltOffset = rawTiltOffset.coerceIn(-maxDisplacement, maxDisplacement)

                drawFluidLayer(w, h, waterHeight, tiltOffset, sloshOffset, angularVelocity, wavePhase,
                    12f, 1.8f, 0f, 1.0f, color.copy(alpha = 0.3f))
                drawFluidLayer(w, h, waterHeight, tiltOffset, sloshOffset, angularVelocity, wavePhase + 1.8f,
                    9f, 2.5f, 5f, 0.88f, color.copy(alpha = 0.45f))
                drawFluidLayer(w, h, waterHeight, tiltOffset, sloshOffset, angularVelocity, wavePhase + 3.5f,
                    7f, 3.2f, 10f, 0.75f, color.copy(alpha = 0.6f))
            }

            // ─── Text Content Overlay ────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top row: Period label + Mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pagePeriod.label}${if (pageMode == CompareMode.EXPENSE_BUDGET) "預算" else ""}餘額",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    // Mode toggle button (hidden for ALL period)
                    if (pagePeriod != TimePeriod.ALL) {
                        FilledTonalButton(
                            onClick = {
                                compareMode = if (compareMode == CompareMode.EXPENSE_INCOME)
                                    CompareMode.EXPENSE_BUDGET
                                else
                                    CompareMode.EXPENSE_INCOME
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = "切換模式",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pageMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Main value (balance)
                Text(
                    text = if (pageMode == CompareMode.EXPENSE_BUDGET && monthlyBudget <= 0) {
                        "未設定預算"
                    } else {
                        currencyFormat.format(pageBalance)
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Bottom: summary items + page indicator
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                ) {
                    // Summary items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val secondaryColor = if (pageMode == CompareMode.EXPENSE_BUDGET)
                            Color(0xFF60A5FA) else Color(0xFF4ADE80)

                        WaterSummaryItem(
                            label = pageDisplay.secondaryLabel,
                            amount = currencyFormat.format(pageDisplay.secondaryValue),
                            color = secondaryColor
                        )
                        WaterSummaryItem(
                            label = pageDisplay.primaryLabel,
                            amount = currencyFormat.format(pageDisplay.primaryValue),
                            color = Color(0xFFFB7185)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Page indicator dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        periods.forEachIndexed { index, _ ->
                            val isSelected = index == pagerState.currentPage
                            val dotColor = when {
                                !isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                displayData.level > 0.6 -> Color(0xFF4ADE80)
                                displayData.level > 0.35 -> Color(0xFFFBBF24)
                                displayData.level > 0.15 -> Color(0xFFFB923C)
                                else -> Color(0xFFFB7185)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (isSelected) 18.dp else 6.dp, 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(dotColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compute display data based on compare mode and period.
 */
private data class DisplayData(
    val level: Double,
    val primaryValue: Double,
    val secondaryValue: Double,
    val primaryLabel: String,
    val secondaryLabel: String
)

private fun computeDisplayData(
    mode: CompareMode,
    summary: PeriodSummary,
    budget: Double,
    period: TimePeriod
): DisplayData {
    return when (mode) {
        CompareMode.EXPENSE_INCOME -> {
            val level = if (summary.income > 0) {
                ((summary.income - summary.expense) / summary.income).coerceIn(0.0, 1.0)
            } else if (summary.expense > 0) {
                0.0
            } else {
                0.5
            }
            DisplayData(
                level = level,
                primaryValue = summary.expense,
                secondaryValue = summary.income,
                primaryLabel = "支出",
                secondaryLabel = "收入"
            )
        }
        CompareMode.EXPENSE_BUDGET -> {
            // Prorate monthly budget: 30 days per month
            val periodBudget = when (period) {
                TimePeriod.TODAY -> budget / 30.0
                TimePeriod.WEEK  -> budget * 7.0 / 30.0
                TimePeriod.MONTH -> budget
                TimePeriod.YEAR  -> budget * 12.0
                TimePeriod.ALL   -> budget // won't reach here (ALL forces EXPENSE_INCOME)
            }
            val level = if (periodBudget > 0) {
                ((periodBudget - summary.expense) / periodBudget).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            DisplayData(
                level = level,
                primaryValue = summary.expense,
                secondaryValue = periodBudget,
                primaryLabel = "支出",
                secondaryLabel = "預算"
            )
        }
    }
}

/**
 * Draws a single fluid layer with physically-correct behavior.
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
        val t = i.toFloat() / steps
        val x = width * t
        val tiltY = effectiveTilt * (1f - 2f * t)
        val sloshAmplitude = waterHeight * 0.5f
        val standingWave = sloshOffset * sloshAmplitude * sin(PI * t).toFloat()
        val surfaceY = baseY + tiltY + standingWave
        val localDepth = (height - surfaceY).coerceAtLeast(0f)
        val depthRatio = (localDepth / height).coerceIn(0f, 1f)
        val dynamicAmplitude = baseAmplitude * depthRatio * depthRatio
        val velocityBoost = min(abs(angularVelocity) * 14f, 35f) * depthRatio
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
