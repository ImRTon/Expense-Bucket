package com.rton.expensebucket.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.R
import com.rton.expensebucket.ui.theme.semanticColors
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenshotOcrGuideSheet(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.screenshot_ocr_guide_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.screenshot_recognition),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = null)
                }
            }
            Text(
                text = stringResource(R.string.screenshot_ocr_guide_instructions, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ScreenshotOcrExample()
        }
    }
}

private enum class ExampleStage(
    val start: Float,
    val end: Float,
    @param:StringRes val description: Int
) {
    Payment(0.00f, 0.28f, R.string.screenshot_ocr_example_payment_step),
    Share(0.28f, 0.52f, R.string.screenshot_ocr_example_share_step),
    Scan(0.52f, 0.76f, R.string.screenshot_ocr_example_scan_step),
    Review(0.76f, 1.00f, R.string.screenshot_ocr_example_review_step)
}

private const val LOOP_DURATION_MS = 8800
private const val EXAMPLE_AMOUNT = "NT$180"

@Composable
private fun ScreenshotOcrExample() {
    val progress = remember { Animatable(0f) }
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val animationsEnabled = motionScale?.scaleFactor != 0f
    var jumpStart by remember { mutableIntStateOf(0) }
    var jumpStage by remember { mutableIntStateOf(0) }

    val stage by remember {
        derivedStateOf {
            val t = progress.value
            when {
                t < ExampleStage.Share.start -> ExampleStage.Payment
                t < ExampleStage.Scan.start -> ExampleStage.Share
                t < ExampleStage.Review.start -> ExampleStage.Scan
                else -> ExampleStage.Review
            }
        }
    }

    LaunchedEffect(animationsEnabled, jumpStart) {
        val initialTarget = ExampleStage.entries[jumpStage].start
        if (!animationsEnabled) {
            // Static snapshot at mid-point of selected stage when system animations are off
            val s = ExampleStage.entries[jumpStage]
            progress.snapTo((s.start + s.end) * 0.5f)
        } else {
            var startFrom = initialTarget
            while (isActive) {
                progress.snapTo(startFrom)
                val remainingMs = ((1f - startFrom) * LOOP_DURATION_MS).roundToInt().coerceAtLeast(1)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing)
                )
                startFrom = 0f
            }
        }
    }

    val merchant = stringResource(R.string.screenshot_ocr_example_merchant)
    val exampleDescription = stringResource(R.string.screenshot_ocr_example_description, merchant, EXAMPLE_AMOUNT)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Miniature Android phone viewport hosting the continuous 4-stage animation
        PhoneDemoViewport(
            progress = progress.value,
            merchant = merchant,
            modifier = Modifier
                .widthIn(max = 276.dp)
                .fillMaxWidth()
                .height(316.dp)
                .clearAndSetSemantics { contentDescription = exampleDescription }
        )

        // 4-segment interactive timeline bar
        Row(
            modifier = Modifier
                .widthIn(max = 276.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExampleStage.entries.forEachIndexed { index, step ->
                val fill = ((progress.value - step.start) / (step.end - step.start)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clickable {
                            jumpStage = index
                            jumpStart++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        // Current stage caption with stable crossfade
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "StageCaption"
        ) { currentStage ->
            Text(
                text = stringResource(currentStage.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                modifier = Modifier
                    .widthIn(max = 276.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PhoneDemoViewport(
    progress: Float,
    merchant: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val outerShape = RoundedCornerShape(26.dp)
    val screenShape = RoundedCornerShape(22.dp)

    // Smooth crossfade between payment/share world (0.00..0.52) and Expense Bucket app world (0.48..1.00)
    val appLayerAlpha = smoothRange(progress, 0.47f, 0.53f) * (1f - smoothRange(progress, 0.96f, 1.00f))
    val paymentLayerAlpha = (1f - smoothRange(progress, 0.48f, 0.54f))
        .coerceAtLeast(smoothRange(progress, 0.96f, 1.00f))

    // Phase 1: Screenshot flash (0.08..0.14) and shrink into bottom-left preview card (0.12..0.28)
    val flashIn = smoothRange(progress, 0.08f, 0.10f)
    val flashOut = 1f - smoothRange(progress, 0.10f, 0.145f)
    val flashAlpha = (flashIn * flashOut).coerceIn(0f, 1f)

    val shrinkRaw = smoothRange(progress, 0.115f, 0.205f)
    val shrinkEased = FastOutSlowInEasing.transform(shrinkRaw)
    val showThumbCard = progress in 0.115f..0.32f
    val thumbCardFadeOut = 1f - smoothRange(progress, 0.27f, 0.32f)

    val shareChipAppear = FastOutSlowInEasing.transform(smoothRange(progress, 0.175f, 0.22f))
    val shareChipTap = pulse(progress, center = 0.245f, halfWidth = 0.03f)

    // Phase 2: Android share sheet slide-up (0.28..0.52) and tap on Expense Bucket (0.39..0.46)
    val sheetEnter = FastOutSlowInEasing.transform(smoothRange(progress, 0.28f, 0.36f))
    val sheetExit = FastOutSlowInEasing.transform(smoothRange(progress, 0.47f, 0.52f))
    val sheetVisibility = (sheetEnter * (1f - sheetExit)).coerceIn(0f, 1f)
    val appIconTap = pulse(progress, center = 0.425f, halfWidth = 0.035f)

    Surface(
        modifier = modifier,
        shape = outerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(screenShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }

            // ── Layer 1: Payment Result Screen ──────────────────────────────────────────
            if (paymentLayerAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = paymentLayerAlpha }
                ) {
                    PaymentResultMockScreen(merchant = merchant)
                }
            }

            // ── Screenshot Shutter Flash + Frame Border ─────────────────────────────────
            if (flashAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha * 0.88f))
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = flashAlpha),
                            shape = screenShape
                        )
                )
            }

            // ── Android Floating Screenshot Preview + Share Chip (bottom-left) ──────────
            if (showThumbCard && thumbCardFadeOut > 0.01f) {
                val thumbScale = lerp(1f, 0.30f, shrinkEased)
                val targetOffsetX = with(density) { 10.dp.toPx() }
                val targetOffsetY = viewportHeightPx * (1f - 0.30f) - with(density) { 12.dp.toPx() }
                val tx = lerp(0f, targetOffsetX, shrinkEased)
                val ty = lerp(0f, targetOffsetY, shrinkEased)

                // Shrinking miniature screenshot
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = thumbScale
                            scaleY = thumbScale
                            translationX = tx
                            translationY = ty
                            alpha = thumbCardFadeOut
                            shadowElevation = with(density) { (8.dp * shrinkEased).toPx() }
                            shape = RoundedCornerShape(18.dp)
                            clip = true
                        }
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    PaymentResultMockScreen(merchant = merchant)
                }

                // Floating "Share" pill beside the thumbnail
                if (shareChipAppear > 0.01f) {
                    val chipScale = 1f - 0.08f * shareChipTap
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 94.dp, bottom = 18.dp)
                            .graphicsLayer {
                                alpha = shareChipAppear * thumbCardFadeOut
                                translationX = with(density) { (-10.dp * (1f - shareChipAppear)).toPx() }
                                scaleX = chipScale
                                scaleY = chipScale
                            }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.5f * shareChipTap)
                            ),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.screenshot_ocr_example_share),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }

                        if (shareChipTap > 0.05f) {
                            TapRippleIndicator(
                                intensity = shareChipTap,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // ── Layer 2: Android Bottom Share Sheet ─────────────────────────────────────
            if (sheetVisibility > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.34f * sheetVisibility))
                )
                AndroidShareSheetOverlay(
                    merchant = merchant,
                    appIconTap = appIconTap,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = viewportHeightPx * 0.56f * (1f - sheetVisibility)
                            alpha = sheetVisibility
                        }
                )
            }

            // ── Layer 3: Expense Bucket OCR Scan & Record Screen ────────────────────────
            if (appLayerAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = appLayerAlpha
                            val enterScale = lerp(0.96f, 1f, smoothRange(progress, 0.47f, 0.54f))
                            scaleX = enterScale
                            scaleY = enterScale
                        }
                ) {
                    ExpenseBucketOcrMockScreen(
                        progress = progress,
                        merchant = merchant
                    )
                }
            }

            // Subtle phone status bar chrome (keeps frame anchored throughout loop)
            PhoneStatusBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PhoneStatusBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "04:09",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 6.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(2.dp)
                    )
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.75f)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                )
            }
        }
    }
}

@Composable
private fun PaymentResultMockScreen(merchant: String) {
    val semantic = MaterialTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Green success check with soft outer halo (inspired by TW mobile payment result pages)
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(semantic.incomeContainer.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(semantic.incomeContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = semantic.onIncomeContainer
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.screenshot_ocr_example_payment_success),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = semantic.onIncomeContainer
        )
        Spacer(Modifier.height(10.dp))

        // Elevated amount card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.screenshot_ocr_example_payment_amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = EXAMPLE_AMOUNT,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Payee info row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.screenshot_ocr_example_payment_payee),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = merchant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.weight(1f))

        // Subtle payment-app bottom bar skeleton for authentic screen silhouette
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@Composable
private fun AndroidShareSheetOverlay(
    merchant: String,
    appIconTap: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 28.dp, height = 3.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        CircleShape
                    )
            )

            // Shared image preview strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mini thumbnail icon of the payment screenshot
                Surface(
                    modifier = Modifier.size(width = 30.dp, height = 38.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MaterialTheme.semanticColors.incomeContainer, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.screenshot_ocr_example_share),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "$merchant · $EXAMPLE_AMOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // App share targets row — Expense Bucket is highlighted and tapped
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                MutedShareTarget()

                // Expense Bucket target with tap press + ripple
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(64.dp)
                ) {
                    val iconScale = 1f - 0.10f * appIconTap
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .border(
                                    width = if (appIconTap > 0.05f) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = appIconTap),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_notification_expense),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        if (appIconTap > 0.05f) {
                            TapRippleIndicator(
                                intensity = appIconTap,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                MutedShareTarget()
                MutedShareTarget()
            }
        }
    }
}

@Composable
private fun MutedShareTarget() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.width(44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 5.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    CircleShape
                )
        )
    }
}

@Composable
private fun ExpenseBucketOcrMockScreen(
    progress: Float,
    merchant: String
) {
    val scanFraction = smoothRange(progress, 0.53f, 0.70f)
    val isScanning = progress in 0.52f..0.72f
    val amountDetected = smoothRange(progress, 0.58f, 0.63f)
    val merchantDetected = smoothRange(progress, 0.65f, 0.70f)
    val confirmReady = smoothRange(progress, 0.72f, 0.78f)
    val confirmTap = pulse(progress, center = 0.835f, halfWidth = 0.032f)
    val recorded = smoothRange(progress, 0.855f, 0.90f)

    val primaryColor = MaterialTheme.colorScheme.primary
    val semantic = MaterialTheme.semanticColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 24.dp, start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // App header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = if (isScanning) {
                    stringResource(R.string.screenshot_ocr_example_scanning)
                } else {
                    stringResource(R.string.screenshot_ocr)
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Captured screenshot preview card WITH laser scan beam & OCR bounding boxes
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                width = 1.dp,
                color = if (isScanning) primaryColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        if (isScanning) {
                            val beamY = size.height * scanFraction
                            val trailHeight = 28.dp.toPx()
                            val trailTop = (beamY - trailHeight).coerceAtLeast(0f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        primaryColor.copy(alpha = 0.24f)
                                    ),
                                    startY = trailTop,
                                    endY = beamY
                                ),
                                topLeft = Offset(0f, trailTop),
                                size = Size(size.width, beamY - trailTop)
                            )
                            drawLine(
                                color = primaryColor,
                                start = Offset(0f, beamY),
                                end = Offset(size.width, beamY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(semantic.incomeContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(9.dp),
                                tint = semantic.onIncomeContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.screenshot_ocr_example_payment_success),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = semantic.onIncomeContainer
                        )
                    }

                    // Amount region with OCR bounding-box highlight
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(primaryColor.copy(alpha = 0.14f * amountDetected))
                            .border(
                                width = 1.dp,
                                color = primaryColor.copy(alpha = 0.85f * amountDetected),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = EXAMPLE_AMOUNT,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Payee region with OCR bounding-box highlight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.screenshot_ocr_example_payment_payee),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(primaryColor.copy(alpha = 0.14f * merchantDetected))
                                .border(
                                    width = 1.dp,
                                    color = primaryColor.copy(alpha = 0.85f * merchantDetected),
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = merchant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Extracted fields that populate as OCR detects each bounding box
        ExtractedFieldRow(
            label = stringResource(R.string.total_amount),
            value = EXAMPLE_AMOUNT,
            reveal = amountDetected
        )
        ExtractedFieldRow(
            label = stringResource(R.string.merchant_note),
            value = merchant,
            reveal = merchantDetected
        )

        Spacer(Modifier.weight(1f))

        // Confirm / Booked primary button
        val buttonScale = 1f - 0.05f * confirmTap
        val isBooked = recorded > 0.5f
        val buttonContainer = if (isBooked) {
            semantic.incomeContainer
        } else {
            MaterialTheme.colorScheme.primary
        }
        val buttonContent = if (isBooked) {
            semantic.onIncomeContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = lerp(0.35f, 1f, confirmReady)
                    scaleX = buttonScale
                    scaleY = buttonScale
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = buttonContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBooked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = buttonContent
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = stringResource(R.string.confirm_record),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = buttonContent
                    )
                }
            }
            if (confirmTap > 0.05f) {
                TapRippleIndicator(
                    intensity = confirmTap,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ExtractedFieldRow(
    label: String,
    value: String,
    reveal: Float
) {
    val density = LocalDensity.current
    val eased = FastOutSlowInEasing.transform(reveal)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (eased > 0.02f) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        alpha = eased
                        translationY = with(density) { (6.dp * (1f - eased)).toPx() }
                    }
                )
            } else {
                // Subtle placeholder pill before OCR finishes reading this line
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun TapRippleIndicator(
    intensity: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(34.dp)) {
        val radius = size.minDimension * (0.28f + 0.34f * intensity)
        drawCircle(
            color = color.copy(alpha = 0.28f * intensity),
            radius = radius
        )
        drawCircle(
            color = color.copy(alpha = 0.7f * intensity),
            radius = radius * 0.42f
        )
    }
}

private fun smoothRange(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private fun pulse(value: Float, center: Float, halfWidth: Float): Float {
    val dist = kotlin.math.abs(value - center)
    if (dist >= halfWidth) return 0f
    val normalized = 1f - (dist / halfWidth)
    return FastOutSlowInEasing.transform(normalized)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
