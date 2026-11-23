package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatioOption
import com.example.model.ContentSfxMatcher
import com.example.model.SaudiVoiceGender
import com.example.model.SceneTransitionType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.MotionStudioUiState
import java.io.File
import java.util.Locale

fun SceneTransitionType.toIcon(): ImageVector = when (this) {
    SceneTransitionType.NONE -> Icons.Filled.ContentCut
    SceneTransitionType.CROSS_FADE -> Icons.Filled.BlurOn
    SceneTransitionType.SLIDE_LEFT -> Icons.Filled.SwapHoriz
    SceneTransitionType.SLIDE_UP -> Icons.Filled.VerticalAlignTop
    SceneTransitionType.ZOOM_IN -> Icons.Filled.ZoomIn
    SceneTransitionType.ZOOM_OUT -> Icons.Filled.ZoomOut
    SceneTransitionType.WIPE_REVEAL -> Icons.Filled.AutoAwesomeMotion
    SceneTransitionType.ROTATE_MORPH -> Icons.Filled.Cached
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewPlayerSection(
    uiState: MotionStudioUiState,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onPreviousScene: () -> Unit,
    onNextScene: () -> Unit,
    onJumpToScene: (Int) -> Unit,
    onSelectTransitionCut: (Int?) -> Unit,
    onUpdateSceneTransition: (Int, SceneTransitionType?, Float?) -> Unit,
    onApplyTransitionToAll: (SceneTransitionType, Float?) -> Unit,
    onPreviewTransitionCut: (Int) -> Unit,
    onToggleVoiceover: (Boolean) -> Unit,
    onSelectVoiceGender: (SaudiVoiceGender) -> Unit,
    onToggleSoundEffects: (Boolean) -> Unit,
    onToggleAutoDucking: (Boolean) -> Unit,
    onSynthesizeSaudiVoices: () -> Unit,
    onOpenAiChat: () -> Unit,
    onExportMp4: () -> Unit,
    onSaveMp4ToDevice: (File) -> Unit,
    onShareMp4: (File) -> Unit,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onViewJson: () -> Unit,
    onImportJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val project = uiState.project
    val segments = uiState.timelineSegments
    val activeFrame = uiState.activeFrame
    val totalDuration = uiState.effectiveTotalDurationSeconds.coerceAtLeast(0.1f)
    val isPortrait = project.aspectRatioEnum == AspectRatioOption.PORTRAIT_9_16
    val isSpeakingActiveScene = uiState.audioSync.isSpeaking &&
        uiState.audioSync.activeSceneId == activeFrame.primaryScene.id

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Project Header Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.projectTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${project.scenes.size} مشاهد • ${project.aspectRatioEnum.labelAr} • ${project.fontStyleEnum.labelAr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "${formatSeconds(uiState.currentTimeSeconds.coerceIn(0f, totalDuration))} / ${formatSeconds(totalDuration)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Studio Monitor Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPortrait) 360.dp else 235.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            MotionStageCanvas(
                frameState = activeFrame,
                totalScenes = project.scenes.size,
                aspectRatio = project.aspectRatioEnum,
                palette = project.palette,
                fontStyle = project.fontStyleEnum,
                isPlaying = uiState.isPlaying,
                isSpeakingThisScene = isSpeakingActiveScene,
                spokenFraction = uiState.audioSync.spokenFraction,
                onTogglePlay = onTogglePlay,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Direct AI Chat Director Edit CTA + Creative References Card
        FilledTonalButton(
            onClick = onOpenAiChat,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("preview_open_ai_chat_button")
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "تحدث ودردش مع المخرج الذكي لتعديل الفيديو",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (project.creativeDirectionAr.isNotBlank() || project.referenceSources.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${project.topicGenreEnum.labelAr} • ${project.palette.nameAr}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (project.creativeDirectionAr.isNotBlank()) {
                        Text(
                            text = project.creativeDirectionAr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (project.referenceSources.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            project.referenceSources.take(4).forEach { source ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = source,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Interactive Segmented Timeline + Transition Selector Between Cuts
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المخطط الزمني والانتقالات",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "اضغط على أي فاصل بين مشهدين لتغيير حركة الانتقال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "لقطة ${activeFrame.primaryIndex + 1}/${project.scenes.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Multi-Shot Timeline Track with Inter-Scene Transition Connector Pills
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(segments) { index, seg ->
                        val scene = seg.scene
                        val isActive = index == activeFrame.primaryIndex
                        val segmentFill = when {
                            index < activeFrame.primaryIndex -> 1f
                            index == activeFrame.primaryIndex -> activeFrame.primaryProgress
                            else -> 0f
                        }
                        val voiceExtended = seg.effectiveDuration > scene.durationSeconds + 0.05f

                        // Scene Track Block
                        Box(
                            modifier = Modifier
                                .width((seg.effectiveDuration * 26f).coerceIn(84f, 165f).dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = if (isActive) 1.6.dp else 1.dp,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onJumpToScene(index) }
                                .testTag("timeline_segment_$index")
                        ) {
                            if (segmentFill > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(segmentFill)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
                                                )
                                            )
                                        )
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}. ${scene.badge.ifBlank { "مشهد" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", seg.effectiveDuration)}ث",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (voiceExtended) {
                                        Text(
                                            text = "• صوت كامل",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        // Transition Connector Button between Scene [index] and Scene [index + 1]
                        if (index < segments.lastIndex) {
                            val transType = scene.transitionEnum
                            val isCutSelected = uiState.selectedTransitionCutIndex == index
                            val isCurrentlyTransitioning = activeFrame.incomingIndex == index + 1 &&
                                activeFrame.transitionProgress > 0f

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isCutSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isCurrentlyTransitioning -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isCutSelected || isCurrentlyTransitioning) 1.5.dp else 1.dp,
                                    color = when {
                                        isCutSelected -> MaterialTheme.colorScheme.primary
                                        isCurrentlyTransitioning -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectTransitionCut(index) }
                                    .testTag("timeline_transition_btn_$index")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = transType.toIcon(),
                                        contentDescription = "انتقال ${transType.labelAr}",
                                        tint = if (isCutSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = transType.shortLabelAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isCutSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Transition Library Picker Panel for Selected Cut
                val cutIdx = uiState.selectedTransitionCutIndex
                AnimatedVisibility(visible = cutIdx != null && cutIdx in 0 until project.scenes.lastIndex) {
                    if (cutIdx != null && cutIdx in 0 until project.scenes.lastIndex) {
                        val cutScene = project.scenes[cutIdx]
                        val activeTrans = cutScene.transitionEnum

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "انتقال بين المشهد ${cutIdx + 1} والمشهد ${cutIdx + 2}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = activeTrans.descriptionAr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onSelectTransitionCut(null) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "إغلاق مكتبة الانتقالات",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Transition Library Chips (8 Transitions)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp)
                                ) {
                                    items(SceneTransitionType.entries) { transition ->
                                        val selected = activeTrans == transition
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                onUpdateSceneTransition(cutIdx, transition, null)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = transition.toIcon(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(transition.labelAr) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.testTag("transition_option_${transition.id}")
                                        )
                                    }
                                }

                                // Transition Duration Slider
                                if (activeTrans != SceneTransitionType.NONE) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "مدة الانتقال",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${String.format(Locale.US, "%.2f", cutScene.transitionDurationSeconds)} ث",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Slider(
                                            value = cutScene.transitionDurationSeconds.coerceIn(0.25f, 1.0f),
                                            onValueChange = { dur ->
                                                onUpdateSceneTransition(cutIdx, null, dur)
                                            },
                                            valueRange = 0.25f..1.0f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }

                                // Preview Cut & Apply to All Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onPreviewTransitionCut(cutIdx) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("preview_transition_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("معاينة الانتقال", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onApplyTransitionToAll(activeTrans, cutScene.transitionDurationSeconds)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("apply_transition_all_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تطبيق على الكل", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Playback Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousScene,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("prev_scene_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "المشهد السابق",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onReplay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("replay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay,
                            contentDescription = "إعادة من البداية",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    FilledIconButton(
                        onClick = onTogglePlay,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = onNextScene,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("next_scene_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "المشهد التالي",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Google AI Studio Voiceover & Content-Matched SFX Studio Bar
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "أصوات استوديو جوجل والمؤثرات",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.neuralVoicesReadyCount > 0) {
                                    "جاهز لـ ${uiState.neuralVoicesReadyCount} من ${project.scenes.size} مشاهد • دمج صوتي كامل دون قص"
                                } else {
                                    "يتم التوليد مباشرة عبر استوديو جوجل"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = project.voiceoverEnabled,
                        onCheckedChange = onToggleVoiceover,
                        modifier = Modifier.testTag("preview_voiceover_switch")
                    )
                }

                if (project.voiceoverEnabled && activeFrame.primaryScene.voiceoverEnabled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSpeakingActiveScene) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isSpeakingActiveScene) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = activeFrame.primaryScene.effectiveVoiceScript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (project.soundEffectsEnabled) {
                                Text(
                                    text = "المؤثر: ${activeFrame.primaryScene.soundEffectEnum.labelAr} (${ContentSfxMatcher.explainMatch(activeFrame.primaryScene)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SaudiVoiceGender.entries) { gender ->
                        val selected = project.voiceGenderEnum == gender
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectVoiceGender(gender) },
                            label = { Text(gender.shortNameAr) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("preview_voice_gender_${gender.id}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = project.soundEffectsEnabled,
                        onClick = { onToggleSoundEffects(!project.soundEffectsEnabled) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (project.soundEffectsEnabled) {
                                    Icons.Filled.VolumeUp
                                } else {
                                    Icons.Filled.VolumeOff
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("مؤثرات المحتوى") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("preview_sfx_chip")
                    )

                    FilledTonalButton(
                        onClick = onSynthesizeSaudiVoices,
                        enabled = !uiState.isSynthesizingVoice && project.scenes.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("synthesize_saudi_voice_button")
                    ) {
                        if (uiState.isSynthesizingVoice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "استوديو جوجل (${(uiState.voiceSynthesisProgress * 100).toInt()}٪)...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تجهيز كل المشاهد",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (uiState.isSynthesizingVoice) {
                    LinearProgressIndicator(
                        progress = { uiState.voiceSynthesisProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                    )
                }

                // Background Music & Auto-Ducking Status Strip (Audio plays strictly behind visuals)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LibraryMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "الموسيقى: ${project.backgroundMusicEnum.labelAr}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${project.backgroundMusicEnum.category.labelAr} • ${(project.backgroundMusicVolume * 100).toInt()}٪",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilterChip(
                            selected = project.autoDuckingEnabled,
                            onClick = { onToggleAutoDucking(!project.autoDuckingEnabled) },
                            label = { Text("خفض تلقائي") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("preview_auto_duck_chip")
                        )
                    }
                }
            }
        }

        // Export & Project Save Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = "التصدير وحفظ المشروع",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "يدمج جميع اللقطات والانتقالات مع الصوت والمؤثرات في ملف MP4 واحد",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onExportMp4,
                    enabled = !uiState.isExportingVideo && project.scenes.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("export_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isExportingVideo) {
                            "جاري دمج وتصيير الفيديو (${(uiState.exportProgress * 100).toInt()}٪)..."
                        } else {
                            "تصدير فيديو MP4 كامل بالصوت والانتقالات"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (uiState.isExportingVideo) {
                    LinearProgressIndicator(
                        progress = { uiState.exportProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }

                uiState.lastExportedMp4?.let { exportedFile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveMp4ToDevice(exportedFile) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_mp4_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ MP4", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onShareMp4(exportedFile) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_mp4_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة الفيديو", maxLines = 1)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveJson,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_json_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ JSON")
                    }

                    OutlinedButton(
                        onClick = onShareJson,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("share_json_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة JSON")
                    }

                    OutlinedButton(
                        onClick = onViewJson,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("view_json_button")
                    ) {
                        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("معاينة الكود")
                    }

                    OutlinedButton(
                        onClick = onImportJson,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_json_button")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استيراد JSON")
                    }
                }
            }
        }
    }
}

private fun formatSeconds(seconds: Float): String {
    val safe = seconds.coerceAtLeast(0f)
    val mins = (safe / 60f).toInt()
    val secs = safe % 60f
    return String.format(Locale.US, "%02d:%04.1f", mins, secs)
}
