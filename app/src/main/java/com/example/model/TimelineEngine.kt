package com.example.model

import kotlin.math.max
import kotlin.math.min

/**
 * Represents one scene segment in the merged timeline with voice-safe effective duration
 * and transition overlap metadata so no voiceover or shot is ever cut or lost.
 */
data class TimelineSegment(
    val index: Int,
    val scene: MotionScene,
    val startTime: Float,
    val endTime: Float,
    val effectiveDuration: Float,
    val transitionFromPrevDuration: Float,
    val transitionToNextDuration: Float
)

data class TimelineFrameState(
    val primaryIndex: Int,
    val primaryScene: MotionScene,
    val primaryProgress: Float,
    val incomingIndex: Int? = null,
    val incomingScene: MotionScene? = null,
    val incomingProgress: Float = 0f,
    val activeTransition: SceneTransitionType = SceneTransitionType.NONE,
    val transitionProgress: Float = 0f,
    val globalProgress: Float = 0f,
    val currentSegment: TimelineSegment? = null
)

object TimelineEngine {

    /**
     * Builds voice-safe, transition-linked segments for the entire project.
     * If a scene's synthesized Google Studio voiceover exceeds its nominal duration,
     * the segment automatically expands to (voiceDuration + 0.35s) so no spoken words are ever cut.
     */
    fun buildSegments(
        project: MotionProject,
        voiceDurationSecondsBySceneId: Map<String, Float> = emptyMap()
    ): List<TimelineSegment> {
        val scenes = project.scenes
        if (scenes.isEmpty()) return emptyList()

        val effectiveDurations = scenes.map { scene ->
            val nominal = scene.durationSeconds.coerceIn(1.5f, 15.0f)
            val voiceDur = if (project.voiceoverEnabled && scene.voiceoverEnabled) {
                voiceDurationSecondsBySceneId[scene.id] ?: 0f
            } else {
                0f
            }
            if (voiceDur > 0f) {
                max(nominal, voiceDur + 0.35f)
            } else {
                nominal
            }
        }

        val transitionDurations = scenes.indices.map { i ->
            if (i >= scenes.lastIndex) {
                0f
            } else {
                val scene = scenes[i]
                if (scene.transitionEnum == SceneTransitionType.NONE) {
                    0f
                } else {
                    val requested = scene.transitionDurationSeconds.coerceIn(0.25f, 1.0f)
                    val maxAllowed = min(effectiveDurations[i], effectiveDurations[i + 1]) * 0.40f
                    min(requested, maxAllowed).coerceAtLeast(0f)
                }
            }
        }

        var cursor = 0f
        return scenes.mapIndexed { i, scene ->
            val dur = effectiveDurations[i]
            val start = cursor
            val end = start + dur
            cursor = end
            TimelineSegment(
                index = i,
                scene = scene,
                startTime = start,
                endTime = end,
                effectiveDuration = dur,
                transitionFromPrevDuration = if (i > 0) transitionDurations[i - 1] else 0f,
                transitionToNextDuration = transitionDurations[i]
            )
        }
    }

    fun totalDuration(segments: List<TimelineSegment>): Float {
        return segments.lastOrNull()?.endTime?.coerceAtLeast(0.1f) ?: 0.1f
    }

    /**
     * Resolves both the primary scene and any active cross-scene transition layer at [timeSeconds],
     * preserving continuous C0/C1 animation progress across every cut so shots merge seamlessly.
     */
    fun resolveFrameAt(
        segments: List<TimelineSegment>,
        timeSeconds: Float
    ): TimelineFrameState {
        if (segments.isEmpty()) {
            val fallback = MotionScene(
                order = 1,
                badge = "ستوديو",
                headline = "أضف مشهداً للبدء",
                subheadline = "يمكنك التوليد بالذكاء الاصطناعي"
            )
            return TimelineFrameState(
                primaryIndex = 0,
                primaryScene = fallback,
                primaryProgress = 0.5f,
                globalProgress = 0f
            )
        }

        val total = totalDuration(segments)
        val t = timeSeconds.coerceIn(0f, total)
        val globalProgress = (t / total).coerceIn(0f, 1f)

        val segIdx = segments.indexOfFirst { t <= it.endTime }.let {
            if (it == -1) segments.lastIndex else it
        }
        val seg = segments[segIdx]

        // Continuous progress factoring in any incoming transition lead-in before seg.startTime
        val prevLead = seg.transitionFromPrevDuration
        val primaryProgress = ((t - seg.startTime + prevLead) / (seg.effectiveDuration + prevLead))
            .coerceIn(0f, 1f)

        val transDur = seg.transitionToNextDuration
        val transType = seg.scene.transitionEnum
        if (segIdx < segments.lastIndex && transDur > 0.01f && transType != SceneTransitionType.NONE) {
            val transStart = seg.endTime - transDur
            if (t >= transStart) {
                val nextSeg = segments[segIdx + 1]
                val transProgress = ((t - transStart) / transDur).coerceIn(0f, 1f)
                val incomingProgress = ((t - transStart) / (nextSeg.effectiveDuration + transDur))
                    .coerceIn(0f, 1f)
                return TimelineFrameState(
                    primaryIndex = segIdx,
                    primaryScene = seg.scene,
                    primaryProgress = primaryProgress,
                    incomingIndex = segIdx + 1,
                    incomingScene = nextSeg.scene,
                    incomingProgress = incomingProgress,
                    activeTransition = transType,
                    transitionProgress = transProgress,
                    globalProgress = globalProgress,
                    currentSegment = seg
                )
            }
        }

        return TimelineFrameState(
            primaryIndex = segIdx,
            primaryScene = seg.scene,
            primaryProgress = primaryProgress,
            globalProgress = globalProgress,
            currentSegment = seg
        )
    }
}
