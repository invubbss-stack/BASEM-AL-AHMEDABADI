package com.example.ui.components

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioOption
import com.example.model.MotionAnimationType
import com.example.model.MotionFontStyle
import com.example.model.MotionScene
import com.example.model.SceneSymbol
import com.example.model.SceneTransitionType
import com.example.model.SmartColorPalette
import com.example.model.TimelineFrameState
import com.example.model.VisualMotif
import com.example.ui.theme.AlexandriaFontFamily
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ChangaFontFamily
import com.example.ui.theme.ElMessiriFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ReadexProFontFamily
import com.example.ui.theme.TajawalFontFamily
import com.example.ui.theme.ThmanyahFontFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

fun MotionFontStyle.toHeadlineFontFamily(): FontFamily = when (this) {
    MotionFontStyle.THMANYAH -> ThmanyahFontFamily
    MotionFontStyle.READEX_PRO -> ReadexProFontFamily
    MotionFontStyle.CHANGA -> ChangaFontFamily
    MotionFontStyle.EL_MESSIRI -> ElMessiriFontFamily
    MotionFontStyle.ALEXANDRIA -> AlexandriaFontFamily
    MotionFontStyle.CAIRO -> CairoFontFamily
    MotionFontStyle.TAJAWAL -> TajawalFontFamily
}

fun MotionFontStyle.toBodyFontFamily(): FontFamily = when (this) {
    MotionFontStyle.THMANYAH -> ThmanyahFontFamily
    MotionFontStyle.READEX_PRO -> ReadexProFontFamily
    MotionFontStyle.CHANGA -> ReadexProFontFamily
    MotionFontStyle.EL_MESSIRI -> TajawalFontFamily
    MotionFontStyle.ALEXANDRIA -> AlexandriaFontFamily
    MotionFontStyle.CAIRO -> TajawalFontFamily
    MotionFontStyle.TAJAWAL -> TajawalFontFamily
}

@Composable
fun MotionStageCanvas(
    frameState: TimelineFrameState,
    totalScenes: Int,
    aspectRatio: AspectRatioOption,
    palette: SmartColorPalette,
    fontStyle: MotionFontStyle = MotionFontStyle.THMANYAH,
    isPlaying: Boolean,
    isSpeakingThisScene: Boolean,
    spokenFraction: Float,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeHeadlineFont = fontStyle.toHeadlineFontFamily()
    val activeBodyFont = fontStyle.toBodyFontFamily()
    val ratioValue = aspectRatio.widthRatio / aspectRatio.heightRatio
    val isPortrait = aspectRatio == AspectRatioOption.PORTRAIT_9_16

    val sceneIndex = frameState.primaryIndex
    val scene = frameState.primaryScene
    val sceneProgress = frameState.primaryProgress
    val globalProgress = frameState.globalProgress
    val incomingScene = frameState.incomingScene
    val transType = frameState.activeTransition
    val rawTransT = frameState.transitionProgress.coerceIn(0f, 1f)
    val easedTransT = easeInOutCubic(rawTransT)
    val isTransitioning = incomingScene != null &&
        transType != SceneTransitionType.NONE &&
        rawTransT > 0.001f

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val stageModifier = if (isPortrait) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(ratioValue, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratioValue, matchHeightConstraintsFirst = false)
        }

        Box(
            modifier = stageModifier
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = palette.primaryColor.copy(alpha = 0.45f),
                    spotColor = palette.secondaryColor.copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(palette.backgroundColor)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.primaryColor.copy(alpha = 0.55f),
                            palette.secondaryColor.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable(onClick = onTogglePlay)
                .testTag("motion_preview_stage")
        ) {
            // Layer 1: Primary (or Outgoing) Scene
            val outgoingModifier = if (!isTransitioning) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (transType) {
                            SceneTransitionType.NONE -> Unit
                            SceneTransitionType.CROSS_FADE -> alpha = 1f - easedTransT
                            SceneTransitionType.SLIDE_LEFT -> translationX = -easedTransT * size.width
                            SceneTransitionType.SLIDE_UP -> translationY = -easedTransT * size.height
                            SceneTransitionType.ZOOM_IN -> {
                                val s = 1f + 0.35f * easedTransT
                                scaleX = s
                                scaleY = s
                                alpha = 1f - easedTransT
                            }
                            SceneTransitionType.ZOOM_OUT -> {
                                val s = 1f - 0.28f * easedTransT
                                scaleX = s
                                scaleY = s
                                alpha = 1f - easedTransT
                            }
                            SceneTransitionType.WIPE_REVEAL -> Unit
                            SceneTransitionType.ROTATE_MORPH -> {
                                val s = 1f - 0.16f * easedTransT
                                scaleX = s
                                scaleY = s
                                rotationZ = 14f * easedTransT
                                alpha = 1f - easedTransT
                            }
                        }
                    }
            }

            SceneVisualLayer(
                scene = scene,
                sceneIndex = sceneIndex,
                sceneProgress = sceneProgress,
                isPortrait = isPortrait,
                palette = palette,
                headlineFont = activeHeadlineFont,
                bodyFont = activeBodyFont,
                isSpeakingThisScene = isSpeakingThisScene,
                spokenFraction = spokenFraction,
                modifier = outgoingModifier
            )

            // Layer 2: Incoming Scene during active Transition window
            if (isTransitioning && incomingScene != null) {
                val incomingModifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (transType == SceneTransitionType.WIPE_REVEAL) {
                            Modifier.drawWithContent {
                                val wipeRight = size.width * easedTransT
                                clipRect(left = 0f, top = 0f, right = wipeRight, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            palette.primaryColor.copy(alpha = 0.85f),
                                            Color.Transparent
                                        ),
                                        startX = (wipeRight - 14f).coerceAtLeast(0f),
                                        endX = (wipeRight + 10f).coerceAtMost(size.width)
                                    ),
                                    topLeft = Offset((wipeRight - 14f).coerceAtLeast(0f), 0f),
                                    size = androidx.compose.ui.geometry.Size(24f, size.height)
                                )
                            }
                        } else {
                            Modifier.graphicsLayer {
                                when (transType) {
                                    SceneTransitionType.NONE,
                                    SceneTransitionType.WIPE_REVEAL -> Unit
                                    SceneTransitionType.CROSS_FADE -> alpha = easedTransT
                                    SceneTransitionType.SLIDE_LEFT -> translationX = (1f - easedTransT) * size.width
                                    SceneTransitionType.SLIDE_UP -> translationY = (1f - easedTransT) * size.height
                                    SceneTransitionType.ZOOM_IN -> {
                                        val s = 0.72f + 0.28f * easedTransT
                                        scaleX = s
                                        scaleY = s
                                        alpha = easedTransT
                                    }
                                    SceneTransitionType.ZOOM_OUT -> {
                                        val s = 1.28f - 0.28f * easedTransT
                                        scaleX = s
                                        scaleY = s
                                        alpha = easedTransT
                                    }
                                    SceneTransitionType.ROTATE_MORPH -> {
                                        val s = 0.84f + 0.16f * easedTransT
                                        scaleX = s
                                        scaleY = s
                                        rotationZ = -14f * (1f - easedTransT)
                                        alpha = easedTransT
                                    }
                                }
                            }
                        }
                    )

                SceneVisualLayer(
                    scene = incomingScene,
                    sceneIndex = frameState.incomingIndex ?: (sceneIndex + 1),
                    sceneProgress = frameState.incomingProgress,
                    isPortrait = isPortrait,
                    palette = palette,
                    headlineFont = activeHeadlineFont,
                    bodyFont = activeBodyFont,
                    isSpeakingThisScene = false,
                    spokenFraction = 0f,
                    modifier = incomingModifier
                )
            }

            // Persistent Top Editorial Header: story progress bars + scene index tag + transition indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (i in 0 until totalScenes.coerceAtLeast(1)) {
                        val fill = when {
                            i < sceneIndex -> 1f
                            i == sceneIndex -> sceneProgress.coerceIn(0f, 1f)
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(palette.textColor.copy(alpha = 0.22f))
                        ) {
                            if (fill > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fill)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(palette.primaryColor)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(sceneIndex + 1).toString().padStart(2, '0')} / ${totalScenes.coerceAtLeast(1).toString().padStart(2, '0')}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = palette.primaryColor.copy(alpha = 0.85f)
                    )
                    Text(
                        text = if (isTransitioning) transType.labelAr else fontStyle.labelAr,
                        fontFamily = activeHeadlineFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = if (isTransitioning) palette.primaryColor else palette.textColor.copy(alpha = 0.48f)
                    )
                }
            }

            // Narrator audio plays cleanly without rendering any speaker badge inside the video stage

            // Play / Paused status badge overlay (Bottom-End)
            if (!isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.68f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = null,
                            tint = palette.primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "متوقف مؤقتاً",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textColor
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = palette.primaryColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(14.dp)
                    )
                }
            }

            // Bottom global progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(palette.surfaceColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(globalProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(palette.primaryColor, palette.secondaryColor)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun SceneVisualLayer(
    scene: MotionScene,
    sceneIndex: Int,
    sceneProgress: Float,
    isPortrait: Boolean,
    palette: SmartColorPalette,
    headlineFont: FontFamily,
    bodyFont: FontFamily,
    isSpeakingThisScene: Boolean,
    spokenFraction: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    palette.backgroundColor,
                    palette.surfaceColor,
                    palette.backgroundColor
                )
            )
        )
    ) {
        // Procedural animated background & motif canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minDim = min(w, h)

            val glowCx = w * (0.3f + 0.4f * sin(sceneProgress * PI.toFloat()))
            val glowCy = h * 0.36f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.primaryColor.copy(alpha = 0.28f),
                        palette.secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(glowCx, glowCy),
                    radius = minDim * 0.72f
                ),
                radius = minDim * 0.72f,
                center = Offset(glowCx, glowCy)
            )

            when (scene.motifEnum) {
                VisualMotif.ORBIT_RINGS -> {
                    for (i in 1..3) {
                        val ringColor = if (i % 2 == 0) palette.secondaryColor else palette.primaryColor
                        val radius = minDim * (0.22f + i * 0.12f) +
                            sin(sceneProgress * PI.toFloat() * 2f + i) * (minDim * 0.02f)
                        drawCircle(
                            color = ringColor.copy(alpha = 0.28f),
                            radius = radius,
                            center = Offset(w * 0.5f, h * 0.48f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                VisualMotif.KINETIC_STRIPES -> {
                    val offset = sceneProgress * w * 0.4f
                    for (i in -3..5) {
                        val x = i * (w * 0.18f) + offset
                        drawLine(
                            color = palette.primaryColor.copy(alpha = 0.18f),
                            start = Offset(x, 0f),
                            end = Offset(x - h * 0.35f, h),
                            strokeWidth = 5.dp.toPx()
                        )
                    }
                }

                VisualMotif.PARTICLE_GLOW -> {
                    for (i in 0..14) {
                        val seed = i * 1.37f
                        val px = ((sin(seed * 3.1f) * 0.5f + 0.5f) * w +
                            sceneProgress * 36f * cos(seed)) % w
                        val py = ((cos(seed * 2.3f) * 0.5f + 0.5f) * h -
                            sceneProgress * 84f).let { if (it < 0f) it + h else it }
                        val particleColor =
                            if (i % 2 == 0) palette.primaryColor else palette.secondaryColor
                        drawCircle(
                            color = particleColor.copy(alpha = 0.42f),
                            radius = (4 + (i % 4) * 2.5f).dp.toPx() * 0.45f,
                            center = Offset(px, py)
                        )
                    }
                }

                VisualMotif.GRID_PULSE -> {
                    val step = minDim * 0.12f
                    val drift = (sceneProgress * step) % step
                    var x = drift
                    while (x < w) {
                        drawLine(
                            color = palette.primaryColor.copy(alpha = 0.16f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        x += step
                    }
                    var y = drift
                    while (y < h) {
                        drawLine(
                            color = palette.primaryColor.copy(alpha = 0.16f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += step
                    }
                }

                VisualMotif.WAVE_RIBBON -> {
                    for (wave in 0..2) {
                        val waveColor =
                            if (wave == 1) palette.secondaryColor else palette.primaryColor
                        val baseY = h * (0.24f + wave * 0.25f)
                        val path = Path().apply {
                            moveTo(0f, baseY)
                            var x = 0f
                            while (x <= w) {
                                val y = baseY + sin(
                                    (x / w) * PI.toFloat() * 3f +
                                        sceneProgress * PI.toFloat() * 2f + wave
                                ) * (minDim * 0.045f)
                                lineTo(x, y)
                                x += 18f
                            }
                        }
                        drawPath(
                            path = path,
                            color = waveColor.copy(alpha = 0.34f),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                VisualMotif.RADIAL_BURST -> {
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val rays = 16
                    for (i in 0 until rays) {
                        val angle = (i.toFloat() / rays) * 2f * PI.toFloat() + sceneProgress * 0.8f
                        val rayColor =
                            if (i % 2 == 0) palette.primaryColor else palette.secondaryColor
                        val inner = minDim * 0.2f
                        val outer = minDim * (0.44f + 0.1f * sin(sceneProgress * PI.toFloat() * 2f + i))
                        drawLine(
                            color = rayColor.copy(alpha = 0.32f),
                            start = Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
                            end = Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }

        val animType = scene.animationEnum
        if (animType == MotionAnimationType.CINEMA_BARS) {
            val barSlide = (sceneProgress / 0.3f).coerceIn(0f, 1f)
            val barHeight = (1f - barSlide) * 34f + 16f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
            )
        }

        val enterT = (sceneProgress / 0.34f).coerceIn(0f, 1f)
        val enterSubT = ((sceneProgress - 0.10f) / 0.32f).coerceIn(0f, 1f)
        val easedEnter = easeOutBack(enterT)
        val easedSubEnter = easeOutExpo(enterSubT)
        val bounceEnter = easeOutBounce(enterT)
        val contentAlpha = enterT.coerceIn(0f, 1f)

        val revealFactor = if (isSpeakingThisScene && spokenFraction > 0.05f) {
            spokenFraction.coerceIn(0.15f, 1f)
        } else {
            (sceneProgress / 0.45f).coerceIn(0f, 1f)
        }

        val headlineDisplayed = if (animType == MotionAnimationType.TYPEWRITER_REVEAL) {
            val count = (revealFactor * scene.headline.length).toInt().coerceAtLeast(1)
            scene.headline.take(count)
        } else {
            scene.headline
        }

        val blurRadiusDp = if (animType == MotionAnimationType.BLUR_FOCUS) {
            ((1f - enterT) * 12f).dp
        } else {
            0.dp
        }

        val glitchOffset = if (animType == MotionAnimationType.GLITCH_RGB && enterT < 0.9f) {
            sin(sceneProgress * 95f) * 14f * (1f - enterT)
        } else {
            0f
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .then(if (blurRadiusDp > 0.2.dp) Modifier.blur(blurRadiusDp) else Modifier)
                .graphicsLayer {
                    alpha = contentAlpha
                    cameraDistance = 12f * density
                    when (animType) {
                        MotionAnimationType.SLIDE_UP_SPRING -> translationY = (1f - easedEnter) * 70f
                        MotionAnimationType.ZOOM_POP -> {
                            val s = 0.72f + 0.28f * easedEnter
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.TYPEWRITER_REVEAL -> translationY = (1f - enterT) * 18f
                        MotionAnimationType.KINETIC_SWIPE -> translationX = -(1f - easedEnter) * 140f
                        MotionAnimationType.FLOAT_PULSE -> {
                            translationY = sin(sceneProgress * PI.toFloat() * 2f) * 14f
                            val s = 0.96f + 0.04f * sin(sceneProgress * PI.toFloat())
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.SPLIT_CARD -> {
                            translationY = (1f - easedEnter) * -30f
                            val s = 0.9f + 0.1f * easedEnter
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.BLUR_FOCUS -> {
                            val s = 1.15f - 0.15f * enterT
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.FLIP_3D -> {
                            rotationY = (1f - easedEnter) * 85f
                            val s = 0.85f + 0.15f * easedEnter
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.GLITCH_RGB -> {
                            translationX = glitchOffset
                            val s = 0.94f + 0.06f * enterT
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.BOUNCE_DROP -> translationY = -(1f - bounceEnter) * 120f
                        MotionAnimationType.SPIN_ORBIT -> {
                            rotationZ = (1f - easedEnter) * -28f
                            val s = 0.65f + 0.35f * easedEnter
                            scaleX = s; scaleY = s
                        }
                        MotionAnimationType.CINEMA_BARS -> {
                            val s = 0.90f + 0.10f * easedEnter
                            scaleX = s; scaleY = s
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val symbol = scene.symbolEnum
            if (symbol != SceneSymbol.NONE) {
                val pulseScale = if (isSpeakingThisScene) {
                    1f + 0.08f * abs(sin(sceneProgress * PI.toFloat() * 6f))
                } else {
                    0.92f + 0.08f * easedEnter
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.primaryColor.copy(alpha = 0.18f),
                    modifier = Modifier
                        .size(if (isPortrait) 48.dp else 44.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(palette.primaryColor, palette.secondaryColor)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = symbol.toImageVector(),
                            contentDescription = symbol.labelAr,
                            tint = palette.primaryColor,
                            modifier = Modifier.size(if (isPortrait) 26.dp else 24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (isPortrait) 10.dp else 8.dp))
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(palette.primaryColor, palette.secondaryColor)
                        )
                    )
                    .padding(
                        horizontal = if (isPortrait) 14.dp else 16.dp,
                        vertical = 6.dp
                    )
            ) {
                Text(
                    text = scene.badge.ifBlank { "مشهد ${sceneIndex + 1}" },
                    color = palette.backgroundColor,
                    fontFamily = headlineFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isPortrait) 12.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(if (isPortrait) 14.dp else 10.dp))

            Box(contentAlignment = Alignment.Center) {
                if (animType == MotionAnimationType.GLITCH_RGB && enterT < 0.92f) {
                    Text(
                        text = headlineDisplayed,
                        color = palette.primaryColor.copy(alpha = 0.55f),
                        fontFamily = headlineFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isPortrait) 25.sp else 27.sp,
                        lineHeight = if (isPortrait) 35.sp else 36.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { translationX = -glitchOffset * 1.4f }
                    )
                }
                Text(
                    text = headlineDisplayed,
                    color = palette.textColor,
                    fontFamily = headlineFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isPortrait) 25.sp else 27.sp,
                    lineHeight = if (isPortrait) 35.sp else 36.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        if (animType == MotionAnimationType.SPLIT_CARD) {
                            translationX = (1f - easedEnter) * 50f
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(if (isPortrait) 10.dp else 7.dp))
            val barFraction = easeOutExpo((sceneProgress / 0.40f).coerceIn(0f, 1f))
            Box(
                modifier = Modifier
                    .width((72f * barFraction).dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(palette.primaryColor, palette.secondaryColor)
                        )
                    )
            )

            if (scene.subheadline.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (isPortrait) 10.dp else 7.dp))
                Text(
                    text = scene.subheadline,
                    color = palette.textColor.copy(alpha = 0.88f),
                    fontFamily = bodyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (isPortrait) 14.sp else 15.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = easedSubEnter
                        translationY = (1f - easedSubEnter) * 18f
                        if (animType == MotionAnimationType.SPLIT_CARD) {
                            translationX = -(1f - easedEnter) * 50f
                        }
                    }
                )
            }
        }
    }
}

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

private fun easeOutExpo(x: Float): Float {
    return if (x >= 1f) 1f else 1f - 2f.pow(-10f * x)
}

private fun easeInOutCubic(x: Float): Float {
    return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
}

private fun easeOutBounce(x: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> {
            val t = x - 1.5f / d1
            n1 * t * t + 0.75f
        }
        x < 2.5f / d1 -> {
            val t = x - 2.25f / d1
            n1 * t * t + 0.9375f
        }
        else -> {
            val t = x - 2.625f / d1
            n1 * t * t + 0.984375f
        }
    }
}
