package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioMoodCategory
import com.example.model.ContentSfxMatcher
import com.example.model.MotionAnimationType
import com.example.model.MotionScene
import com.example.model.MotionTopicGenre
import com.example.model.SceneSoundEffect
import com.example.model.SceneSymbol
import com.example.model.SceneTransitionType
import com.example.model.SceneVfxMatcher
import com.example.model.SceneVisualEffect
import com.example.model.VisualMotif
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SceneEditorSection(
    scenes: List<MotionScene>,
    selectedIndex: Int,
    totalDurationSeconds: Float,
    topicGenre: MotionTopicGenre,
    selectedSfxCategoryFilter: AudioMoodCategory?,
    onSelectSfxCategory: (AudioMoodCategory?) -> Unit,
    onSelectScene: (Int) -> Unit,
    onUpdateScene: (
        index: Int,
        badge: String?,
        headline: String?,
        subheadline: String?,
        durationSeconds: Float?,
        animationType: MotionAnimationType?,
        visualMotif: VisualMotif?,
        sceneSymbol: SceneSymbol?,
        soundEffect: SceneSoundEffect?,
        voiceoverScript: String?,
        voiceoverEnabled: Boolean?,
        transitionToNext: SceneTransitionType?,
        visualEffect: SceneVisualEffect?
    ) -> Unit,
    onAddScene: () -> Unit,
    onDeleteScene: (Int) -> Unit,
    onMoveScene: (Int, Int) -> Unit,
    onPreviewScene: (Int) -> Unit,
    onPreviewSoundEffect: (SceneSoundEffect) -> Unit,
    onAutoMatchSfx: (Int) -> Unit,
    onPreviewVoiceover: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
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
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "محرر المشاهد السريع",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${scenes.size} مشاهد • المدة الإجمالية ${String.format(Locale.US, "%.1f", totalDurationSeconds)} ثانية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onAddScene,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("add_scene_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة مشهد")
            }
        }

        // Scene cards
        scenes.forEachIndexed { index, scene ->
            val isExpanded = index == selectedIndex
            SceneEditorCard(
                scene = scene,
                index = index,
                totalCount = scenes.size,
                isExpanded = isExpanded,
                topicGenre = topicGenre,
                selectedSfxCategoryFilter = selectedSfxCategoryFilter,
                onSelectSfxCategory = onSelectSfxCategory,
                onToggleExpand = { onSelectScene(index) },
                onUpdate = { badge, headline, subheadline, duration, anim, motif, symbol, sfx, voiceScript, voiceEnabled, transition, vfx ->
                    onUpdateScene(
                        index,
                        badge,
                        headline,
                        subheadline,
                        duration,
                        anim,
                        motif,
                        symbol,
                        sfx,
                        voiceScript,
                        voiceEnabled,
                        transition,
                        vfx
                    )
                },
                onDelete = { onDeleteScene(index) },
                onMoveUp = { onMoveScene(index, -1) },
                onMoveDown = { onMoveScene(index, 1) },
                onPreview = { onPreviewScene(index) },
                onPreviewSoundEffect = onPreviewSoundEffect,
                onAutoMatchSfx = { onAutoMatchSfx(index) },
                onPreviewVoiceover = { onPreviewVoiceover(index) }
            )
        }
    }
}

@Composable
private fun SceneEditorCard(
    scene: MotionScene,
    index: Int,
    totalCount: Int,
    isExpanded: Boolean,
    topicGenre: MotionTopicGenre,
    selectedSfxCategoryFilter: AudioMoodCategory?,
    onSelectSfxCategory: (AudioMoodCategory?) -> Unit,
    onToggleExpand: () -> Unit,
    onUpdate: (
        badge: String?,
        headline: String?,
        subheadline: String?,
        durationSeconds: Float?,
        animationType: MotionAnimationType?,
        visualMotif: VisualMotif?,
        sceneSymbol: SceneSymbol?,
        soundEffect: SceneSoundEffect?,
        voiceoverScript: String?,
        voiceoverEnabled: Boolean?,
        transitionToNext: SceneTransitionType?,
        visualEffect: SceneVisualEffect?
    ) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPreview: () -> Unit,
    onPreviewSoundEffect: (SceneSoundEffect) -> Unit,
    onAutoMatchSfx: () -> Unit,
    onPreviewVoiceover: () -> Unit
) {
    val resolvedVfx = SceneVfxMatcher.resolveEffectiveEffect(scene, topicGenre)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_card_$index")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isExpanded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = "${index + 1}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isExpanded) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scene.headline.ifBlank { "مشهد بدون عنوان" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", scene.durationSeconds)} ث • ${scene.animationEnum.labelAr} • ${resolvedVfx.shortLabelAr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("preview_scene_btn_$index")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleOutline,
                        contentDescription = "معاينة المشهد",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "طي" else "توسيع",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable Quick Editor
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = scene.badge,
                        onValueChange = { onUpdate(it, null, null, null, null, null, null, null, null, null, null, null) },
                        label = { Text("الشارة (Badge)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scene_badge_input_$index")
                    )

                    OutlinedTextField(
                        value = scene.headline,
                        onValueChange = { onUpdate(null, it, null, null, null, null, null, null, null, null, null, null) },
                        label = { Text("النص الرئيسي للمشهد") },
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scene_headline_input_$index")
                    )

                    OutlinedTextField(
                        value = scene.subheadline,
                        onValueChange = { onUpdate(null, null, it, null, null, null, null, null, null, null, null, null) },
                        label = { Text("النص الفرعي التوضيحي") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scene_subheadline_input_$index")
                    )

                    // Per-scene Saudi Voiceover Script & Test Trigger
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "النطق السعودي للمشهد",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextButton(
                                        onClick = onPreviewVoiceover,
                                        enabled = scene.voiceoverEnabled,
                                        modifier = Modifier.testTag("test_voiceover_btn_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تجربة النطق")
                                    }
                                    Switch(
                                        checked = scene.voiceoverEnabled,
                                        onCheckedChange = {
                                            onUpdate(null, null, null, null, null, null, null, null, null, it, null, null)
                                        },
                                        modifier = Modifier.testTag("scene_voice_switch_$index")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = scene.voiceoverScript,
                                onValueChange = {
                                    onUpdate(null, null, null, null, null, null, null, null, it, null, null, null)
                                },
                                label = { Text("نص التعليق باللهجة الخليجية") },
                                placeholder = {
                                    Text(
                                        text = "تلقائي: ${scene.headline}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                maxLines = 3,
                                enabled = scene.voiceoverEnabled,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scene_voice_script_input_$index")
                            )
                        }
                    }

                    // Duration slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مدة المشهد بالثواني",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", scene.durationSeconds)} ثانية",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = scene.durationSeconds,
                            onValueChange = { raw ->
                                val snapped = (raw * 2f).roundToInt() / 2f
                                onUpdate(null, null, null, snapped, null, null, null, null, null, null, null, null)
                            },
                            valueRange = 1.5f..8.0f,
                            steps = 12,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("scene_duration_slider_$index")
                        )
                    }

                    // Animation type selector (12 animations)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "نوع الحركة (${MotionAnimationType.entries.size} خياراً)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = scene.animationEnum.hintAr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(MotionAnimationType.entries) { anim ->
                                val selected = scene.animationEnum == anim
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdate(null, null, null, null, anim, null, null, null, null, null, null, null)
                                    },
                                    label = { Text(anim.labelAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("anim_chip_${index}_${anim.id}")
                                )
                            }
                        }
                    }

                    // Visual Effects System (Particles / Blur / Glow — auto-activated by scene type or manual override)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "التأثير البصري (Particles / Blur / Glow)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = SceneVfxMatcher.explainMatch(scene, topicGenre),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(SceneVisualEffect.entries) { vfx ->
                                val selected = scene.visualEffectEnum == vfx
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdate(null, null, null, null, null, null, null, null, null, null, null, vfx)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    label = { Text(vfx.labelAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("vfx_chip_${index}_${vfx.id}")
                                )
                            }
                        }
                    }

                    // Transition to next scene selector
                    if (index < totalCount - 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "الانتقال إلى المشهد ${index + 2}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(SceneTransitionType.entries) { transition ->
                                    val selected = scene.transitionEnum == transition
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            onUpdate(null, null, null, null, null, null, null, null, null, null, transition, null)
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
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("scene_trans_chip_${index}_${transition.id}")
                                    )
                                }
                            }
                        }
                    }

                    // Scene Symbol selector (24 topic icons)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "رمز المشهد (${SceneSymbol.entries.size} رمزاً)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(SceneSymbol.entries) { symbol ->
                                val selected = scene.symbolEnum == symbol
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdate(null, null, null, null, null, null, symbol, null, null, null, null, null)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = symbol.toImageVector(),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(symbol.labelAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("symbol_chip_${index}_${symbol.id}")
                                )
                            }
                        }
                    }

                    // Categorized Sound Effect selector (Commercial / Energetic / Calm + live audio preview)
                    val filteredEffects = remember(selectedSfxCategoryFilter) {
                        if (selectedSfxCategoryFilter == null) {
                            SceneSoundEffect.entries
                        } else {
                            SceneSoundEffect.entries.filter {
                                it == SceneSoundEffect.NONE || it.category == selectedSfxCategoryFilter
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "مكتبة المؤثرات الصوتية التصنيفية",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = ContentSfxMatcher.explainMatch(scene),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onAutoMatchSfx,
                                modifier = Modifier.testTag("auto_match_sfx_btn_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مطابقة ذكية")
                            }
                        }

                        // Category Filter Tabs
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedSfxCategoryFilter == null,
                                    onClick = { onSelectSfxCategory(null) },
                                    label = { Text("الكل") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("sfx_cat_${index}_ALL")
                                )
                            }
                            items(AudioMoodCategory.entries) { category ->
                                FilterChip(
                                    selected = selectedSfxCategoryFilter == category,
                                    onClick = { onSelectSfxCategory(category) },
                                    label = { Text(category.labelAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("sfx_cat_${index}_${category.id}")
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(filteredEffects) { effect ->
                                val selected = scene.soundEffectEnum == effect
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdate(null, null, null, null, null, null, null, effect, null, null, null, null)
                                        onPreviewSoundEffect(effect)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    label = { Text(effect.labelAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("sfx_chip_${index}_${effect.id}")
                                )
                            }
                        }
                    }

                    // Visual motif selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "خلفية الموشن الهندسية",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(VisualMotif.entries) { motif ->
                                val selected = scene.motifEnum == motif
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdate(null, null, null, null, null, motif, null, null, null, null, null, null)
                                    },
                                    label = { Text(motif.labelAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("motif_chip_${index}_${motif.id}")
                                )
                            }
                        }
                    }

                    // Reorder & Delete row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = index > 0,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "تحريك للأعلى")
                            }
                            IconButton(
                                onClick = onMoveDown,
                                enabled = index < totalCount - 1,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "تحريك للأسفل")
                            }
                        }

                        TextButton(
                            onClick = onDelete,
                            enabled = totalCount > 1,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("delete_scene_button_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "حذف المشهد",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف المشهد")
                        }
                    }
                }
            }
        }
    }
}
