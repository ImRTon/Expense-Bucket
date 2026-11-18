package com.rton.expensebucket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import com.rton.expensebucket.R
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun TransitReadMascot(cardIcon: String, progress: State<Float>, modifier: Modifier = Modifier) {
    val isIcoca = cardIcon == "icoca"
    // ICOCA cutout adapted from JR West:
    // https://www.jr-odekake.net/icoca/assets/img/area/mv.jpg
    val icocaArtwork = if (isIcoca) ImageBitmap.imageResource(R.drawable.icoca_read_mascot) else null
    Canvas(modifier) {
        if (icocaArtwork != null) {
            val unit = size.minDimension / icocaArtwork.width
            scale(unit, unit, pivot = Offset.Zero) { drawIcocaMascot(icocaArtwork, progress.value) }
        } else {
            val unit = size.minDimension / 236f
            scale(unit, unit, pivot = Offset.Zero) { drawSuicaMascot(progress.value) }
        }
    }
}

// The source cutout has an open bill. These paths follow its mouth in the 384px artwork.
private val icocaMouth = Path().apply {
    moveTo(137f, 109f)
    cubicTo(152f, 106f, 157f, 79f, 191f, 79f)
    cubicTo(225f, 79f, 230f, 106f, 245f, 109f)
    cubicTo(229f, 113f, 218f, 120f, 207f, 128f)
    cubicTo(198f, 135f, 184f, 135f, 175f, 128f)
    cubicTo(164f, 120f, 153f, 113f, 137f, 109f)
    close()
}

private val icocaClosedBill = Brush.verticalGradient(
    colors = listOf(Color(0xFFFC9F36), Color(0xFFF9901E)),
    startY = 79f,
    endY = 133f
)

private val icocaClosedBillLine = Path().apply {
    moveTo(143f, 109f)
    quadraticTo(191f, 122f, 239f, 109f)
}

private fun DrawScope.drawIcocaMascot(artwork: ImageBitmap, opening: Float) {
    drawImage(artwork)
    if (opening >= 1f) return

    drawPath(icocaMouth, brush = icocaClosedBill)
    drawPath(icocaMouth, brush = icocaClosedBill, style = Stroke(3f))
    if (opening > 0f) {
        scale(1f, opening, pivot = Offset(191f, 109f)) {
            clipPath(icocaMouth) { drawImage(artwork) }
        }
    }
    drawPath(icocaClosedBillLine, Color(0xFF27201F), alpha = 1f - opening, style = Stroke(3f, cap = StrokeCap.Round))
}

// Drawn in the reference image's coordinates so the original pose and markings stay aligned.
// Reference: https://www.jrtimes.tw/upload/control/032dcf44-d242-4b6f-b8e0-4219233601ad.jpg
private val suicaSilhouette = Path().apply {
    moveTo(619f, 304f)
    cubicTo(605f, 293f, 607f, 273f, 616f, 263f)
    cubicTo(626f, 251f, 647f, 252f, 657f, 263f)
    cubicTo(665f, 272f, 665f, 283f, 665f, 294f)
    cubicTo(675f, 309f, 694f, 324f, 710f, 338f)
    cubicTo(723f, 351f, 731f, 364f, 732f, 372f)
    cubicTo(722f, 366f, 714f, 361f, 709f, 358f)
    cubicTo(718f, 372f, 720f, 389f, 724f, 402f)
    cubicTo(728f, 407f, 737f, 406f, 737f, 413f)
    cubicTo(733f, 422f, 716f, 422f, 703f, 423f)
    cubicTo(690f, 431f, 671f, 435f, 653f, 438f)
    cubicTo(662f, 428f, 656f, 415f, 649f, 404f)
    cubicTo(634f, 382f, 619f, 365f, 617f, 348f)
    cubicTo(607f, 357f, 590f, 337f, 581f, 327f)
    cubicTo(568f, 309f, 584f, 313f, 592f, 321f)
    cubicTo(604f, 332f, 610f, 336f, 615f, 325f)
    cubicTo(618f, 317f, 619f, 311f, 619f, 304f)
    close()
}

private val suicaBelly = Path().apply {
    moveTo(624f, 306f)
    quadraticTo(638f, 309f, 649f, 305f)
    cubicTo(657f, 321f, 661f, 344f, 675f, 358f)
    cubicTo(689f, 374f, 704f, 396f, 701f, 412f)
    cubicTo(698f, 422f, 680f, 431f, 657f, 434f)
    cubicTo(664f, 423f, 649f, 402f, 640f, 391f)
    cubicTo(625f, 372f, 617f, 350f, 621f, 328f)
    quadraticTo(622f, 314f, 624f, 306f)
    close()
}

private val suicaFlipperMark = Path().apply {
    moveTo(678f, 330f)
    cubicTo(693f, 331f, 717f, 352f, 725f, 365f)
    cubicTo(712f, 352f, 687f, 346f, 678f, 334f)
    close()
}

private val suicaFoot = Path().apply {
    moveTo(0f, -5f)
    cubicTo(-3f, -3f, -8f, -10f, -13f, -8f)
    cubicTo(-20f, -5f, -12f, 2f, -6f, 3f)
    cubicTo(-3f, 4f, -1f, 7f, 3f, 4f)
    cubicTo(8f, 0f, 2f, -2f, 0f, -5f)
    close()
}

private fun DrawScope.drawSuicaMascot(progress: Float) {
    val ink = Color(0xFF241918)
    val white = Color(0xFFFCFAF8)
    val lift = sin(PI * progress).toFloat().coerceAtLeast(0f)
    val bodyX = 11f - 22f * progress
    val bodyY = -5f * lift
    val frontFoot = Offset(659f - 44f * progress, 444f - 21f * lift)
    val backFoot = Offset(704f, 444f)

    translate(left = -539f, top = -237f) {
        drawLine(ink, Offset(703f + bodyX, 419f + bodyY), backFoot, 4f, StrokeCap.Round)
        drawLine(ink, Offset(654f + bodyX, 427f + bodyY), frontFoot, 4f, StrokeCap.Round)
        translate(backFoot.x, backFoot.y) { drawPath(suicaFoot, ink) }
        translate(frontFoot.x, frontFoot.y) { drawPath(suicaFoot, ink) }

        translate(bodyX, bodyY) {
            drawPath(suicaSilhouette, ink)
            drawPath(suicaBelly, white)
            drawPath(suicaFlipperMark, white)
            drawCircle(white, 5f, Offset(621f, 280f))
            drawCircle(ink, 2.65f, Offset(621f, 280f))
            drawCircle(white, 5f, Offset(650f, 277f))
            drawCircle(ink, 2.65f, Offset(650f, 277f))
            drawOval(white, Offset(628f, 279f), Size(16f, 14f))
            drawLine(ink, Offset(631f, 286f), Offset(641f, 285f), 2f, StrokeCap.Round)
        }
    }
}
