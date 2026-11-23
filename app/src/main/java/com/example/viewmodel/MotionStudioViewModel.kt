package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiChatMessage
import com.example.ai.FirebaseMotionAiRepository
import com.example.ai.MotionAiRepository
import com.example.ai.TtsQuotaExceededException
import com.example.audio.AudioSyncState
import com.example.audio.MotionAudioEngine
import com.example.export.MotionVideoExporter
import com.example.model.AspectRatioOption
import com.example.model.AudioMoodCategory
import com.example.model.BackgroundMusicTrack
import com.example.model.ContentSfxMatcher
import com.example.model.MotionAnimationType
import com.example.model.MotionFontStyle
import com.example.model.MotionProject
import com.example.model.MotionProjectJson
import com.example.model.MotionScene
import com.example.model.MotionTopicGenre
import com.example.model.SampleProjects
import com.example.model.SaudiVoiceGender
import com.example.model.SceneSoundEffect
import com.example.model.SceneSymbol
import com.example.model.SceneTransitionType
import com.example.model.SmartColorPalette
import com.example.model.TimelineEngine
import com.example.model.TimelineFrameState
import com.example.model.TimelineSegment
import com.example.model.VisualMotif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "MotionStudioViewModel"
private const val AUTOSAVE_FILENAME = "autosave_motion_project.json"

private val starterChatMessages = listOf(
    AiChatMessage(
        isUser = false,
        text = "أهلاً بك! أنا مخرجك الذكي. تقدر تكلمني أو تكتب لي أي تعديل تريده على الفيديو الحالي — مثل تغيير الألوان، نوع الخط، الموسيقى، الانتقالات، أو إعادة صياغة أي مشهد."
    )
)

data class MotionStudioUiState(
    val project: MotionProject = SampleProjects.starterProject,
    val promptInput: String = SampleProjects.defaultPrompt,
    val previousPromptForUndo: String? = null,
    val selectedSceneIndex: Int = 0,
    val selectedTransitionCutIndex: Int? = null,
    val isPlaying: Boolean = true,
    val currentTimeSeconds: Float = 0f,
    val isGeneratingScenes: Boolean = false,
    val isImprovingCopy: Boolean = false,
    val isSynthesizingVoice: Boolean = false,
    val synthesizingSceneId: String? = null,
    val voiceSynthesisProgress: Float = 0f,
    val neuralVoicesReadyCount: Int = 0,
    val voiceDurationsBySceneId: Map<String, Float> = emptyMap(),
    val audioSync: AudioSyncState = AudioSyncState(),
    val isExportingVideo: Boolean = false,
    val exportProgress: Float = 0f,
    val lastExportedMp4: File? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val showJsonDialog: Boolean = false,
    val creativeFreedomMode: Boolean = true,
    val selectedTopicGenre: MotionTopicGenre = MotionTopicGenre.AUTO,
    val selectedSfxCategoryFilter: AudioMoodCategory? = null,
    val showAiChatSheet: Boolean = false,
    val chatMessages: List<AiChatMessage> = starterChatMessages,
    val isApplyingChatEdit: Boolean = false
) {
    val timelineSegments: List<TimelineSegment>
        get() = TimelineEngine.buildSegments(project, voiceDurationsBySceneId)

    val effectiveTotalDurationSeconds: Float
        get() = TimelineEngine.totalDuration(timelineSegments)

    val activeFrame: TimelineFrameState
        get() = TimelineEngine.resolveFrameAt(timelineSegments, currentTimeSeconds)
}

class MotionStudioViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val aiRepository: MotionAiRepository = FirebaseMotionAiRepository()
    private val audioEngine = MotionAudioEngine(application)

    private val _uiState = MutableStateFlow(MotionStudioUiState())
    val uiState: StateFlow<MotionStudioUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var voiceSynthesisJob: Job? = null
    private var lastTriggeredSceneKey: String? = null
    private var autoCloudTtsAllowed: Boolean = false

    init {
        observeAudioSync()
        observeVoiceDurations()
        loadAutosavedProject()
        startPlaybackLoop()
        syncBackgroundMusic()
    }

    private fun observeAudioSync() {
        viewModelScope.launch {
            audioEngine.syncState.collect { sync ->
                _uiState.update { it.copy(audioSync = sync) }
            }
        }
    }

    private fun observeVoiceDurations() {
        viewModelScope.launch {
            audioEngine.clipDurationsBySceneId.collect { durations ->
                _uiState.update { it.copy(voiceDurationsBySceneId = durations) }
            }
        }
    }

    private fun loadAutosavedProject() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val file = File(getApplication<Application>().filesDir, AUTOSAVE_FILENAME)
                if (file.exists()) {
                    MotionProjectJson.fromJson(file.readText(Charsets.UTF_8))
                } else {
                    null
                }
            }
            if (saved != null && saved.scenes.isNotEmpty()) {
                audioEngine.refreshDurationsForProject(saved.scenes, saved.voiceGenderEnum)
                val readyCount = saved.scenes.count {
                    audioEngine.hasAnyVoiceClip(it, saved.voiceGenderEnum)
                }
                _uiState.update {
                    it.copy(
                        project = saved,
                        promptInput = saved.promptIdea.ifBlank { SampleProjects.defaultPrompt },
                        selectedTopicGenre = saved.topicGenreEnum,
                        selectedSceneIndex = 0,
                        currentTimeSeconds = 0f,
                        neuralVoicesReadyCount = readyCount
                    )
                }
                syncBackgroundMusic()
            }
        }
    }

    private fun persistProject(project: MotionProject) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = File(getApplication<Application>().filesDir, AUTOSAVE_FILENAME)
                file.writeText(project.toPrettyJson(), Charsets.UTF_8)
            }.onFailure {
                Log.w(TAG, "Failed to autosave project", it)
            }
        }
    }

    private fun syncBackgroundMusic() {
        val state = _uiState.value
        val shouldPlayMusic = state.isPlaying && !state.isExportingVideo
        audioEngine.updateBackgroundMusic(
            track = state.project.backgroundMusicEnum,
            volume = state.project.backgroundMusicVolume,
            autoDucking = state.project.autoDuckingEnabled,
            isPlaying = shouldPlayMusic
        )
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (isActive) {
                delay(16L)
                val now = System.currentTimeMillis()
                val deltaSec = ((now - lastTickMs).coerceAtMost(100L)) / 1000f
                lastTickMs = now

                val state = _uiState.value
                if (state.isPlaying && state.project.scenes.isNotEmpty()) {
                    val segments = state.timelineSegments
                    val total = TimelineEngine.totalDuration(segments).coerceAtLeast(0.1f)
                    val safeCurrent = state.currentTimeSeconds.coerceIn(0f, total)
                    val frame = TimelineEngine.resolveFrameAt(segments, safeCurrent)
                    val activeScene = frame.primaryScene
                    val activeSeg = frame.currentSegment

                    val waitingForCurrentVoiceSynthesis = state.project.voiceoverEnabled &&
                        activeScene.voiceoverEnabled &&
                        state.synthesizingSceneId == activeScene.id &&
                        !audioEngine.hasAnyVoiceClip(activeScene, state.project.voiceGenderEnum)

                    val isStillSpeakingActive = state.project.voiceoverEnabled &&
                        activeScene.voiceoverEnabled &&
                        audioEngine.isActivelyPlayingScene(activeScene.id)

                    val candidateTime = safeCurrent + deltaSec
                    val nextTime = when {
                        waitingForCurrentVoiceSynthesis && activeSeg != null -> {
                            safeCurrent.coerceAtMost((activeSeg.startTime + 0.25f).coerceAtMost(total))
                        }
                        isStillSpeakingActive && activeSeg != null && candidateTime >= activeSeg.endTime -> {
                            (activeSeg.endTime - 0.04f).coerceIn(activeSeg.startTime, (total - 0.01f).coerceAtLeast(0f))
                        }
                        candidateTime >= total -> 0f
                        else -> candidateTime.coerceIn(0f, total)
                    }

                    if (nextTime == 0f && safeCurrent > 0.5f) {
                        lastTriggeredSceneKey = null
                    }

                    _uiState.update {
                        it.copy(currentTimeSeconds = nextTime)
                    }
                    syncAudioWithActiveFrame(_uiState.value)
                }
            }
        }
    }

    private fun syncAudioWithActiveFrame(state: MotionStudioUiState, forceTrigger: Boolean = false) {
        if (!state.isPlaying || state.project.scenes.isEmpty()) return
        val active = state.activeFrame
        val sceneKey = "${active.primaryIndex}_${active.primaryScene.id}"
        if (forceTrigger || sceneKey != lastTriggeredSceneKey) {
            lastTriggeredSceneKey = sceneKey
            triggerSceneAudio(state.project, active.primaryScene, active.primaryIndex)
        }
    }

    private fun triggerSceneAudio(project: MotionProject, scene: MotionScene, sceneIndex: Int) {
        if (project.soundEffectsEnabled && scene.soundEffectEnum != SceneSoundEffect.NONE) {
            audioEngine.playSoundEffect(scene.soundEffectEnum)
        }
        if (project.voiceoverEnabled && scene.voiceoverEnabled) {
            val started = audioEngine.speakSceneWithGoogleStudio(scene, project.voiceGenderEnum)
            if (!started && !_uiState.value.isSynthesizingVoice) {
                synthesizeVoices(
                    startSceneIndex = sceneIndex,
                    playCurrentWhenReady = true,
                    allowCloudTts = autoCloudTtsAllowed,
                    explicitUserAction = false
                )
            }
        } else {
            audioEngine.stopVoiceover()
        }
    }

    fun updatePrompt(newText: String) {
        _uiState.update { it.copy(promptInput = newText, errorMessage = null) }
    }

    fun applyQuickPrompt(template: String) {
        _uiState.update {
            it.copy(
                promptInput = template,
                previousPromptForUndo = null,
                errorMessage = null
            )
        }
    }

    fun selectTopicGenre(genre: MotionTopicGenre) {
        _uiState.update { state ->
            val updated = state.project.copy(topicGenre = genre.id)
            persistProject(updated)
            state.copy(
                selectedTopicGenre = genre,
                selectedSfxCategoryFilter = if (genre == MotionTopicGenre.AUTO) null else genre.defaultMood,
                project = updated
            )
        }
    }

    fun toggleCreativeFreedomMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                creativeFreedomMode = enabled,
                statusMessage = if (enabled) {
                    "وضع الإبحار الإبداعي مفعّل: سيبتكر الذكاء الاصطناعي ألواناً وخطوطاً ومراجع لكل موضوع"
                } else {
                    "تم تفعيل التخصيص اليدوي للألوان والخطوط"
                }
            )
        }
    }

    fun selectBackgroundMusic(track: BackgroundMusicTrack) {
        _uiState.update { state ->
            val updated = state.project.copy(backgroundMusicTrack = track.id)
            persistProject(updated)
            state.copy(
                project = updated,
                lastExportedMp4 = null,
                statusMessage = if (track == BackgroundMusicTrack.NONE) {
                    "تم إيقاف موسيقى الخلفية"
                } else {
                    "تم اختيار موسيقى: ${track.labelAr}"
                }
            )
        }
        if (_uiState.value.isPlaying) {
            syncBackgroundMusic()
        } else {
            audioEngine.previewMusicTrack(track, _uiState.value.project.backgroundMusicVolume)
        }
    }

    fun updateBackgroundMusicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { state ->
            val updated = state.project.copy(backgroundMusicVolume = clamped)
            persistProject(updated)
            state.copy(project = updated, lastExportedMp4 = null)
        }
        syncBackgroundMusic()
    }

    fun toggleAutoDucking(enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.project.copy(autoDuckingEnabled = enabled)
            persistProject(updated)
            state.copy(
                project = updated,
                lastExportedMp4 = null,
                statusMessage = if (enabled) {
                    "تم تفعيل خفض الموسيقى تلقائياً أثناء نطق النص"
                } else {
                    "تم إيقاف الموازنة التلقائية للموسيقى"
                }
            )
        }
        syncBackgroundMusic()
    }

    fun selectSfxCategoryFilter(category: AudioMoodCategory?) {
        _uiState.update { it.copy(selectedSfxCategoryFilter = category) }
    }

    fun selectAspectRatio(ratio: AspectRatioOption) {
        _uiState.update { state ->
            val updated = state.project.copy(aspectRatio = ratio.id)
            persistProject(updated)
            state.copy(project = updated, lastExportedMp4 = null)
        }
    }

    fun selectPalette(palette: SmartColorPalette) {
        _uiState.update { state ->
            val updated = state.project.copy(
                paletteId = palette.id,
                customPalette = null
            )
            persistProject(updated)
            state.copy(
                project = updated,
                creativeFreedomMode = false,
                lastExportedMp4 = null,
                statusMessage = "تم تطبيق تنسيق ألوان «${palette.nameAr}»"
            )
        }
    }

    fun selectFontStyle(fontStyle: MotionFontStyle) {
        _uiState.update { state ->
            val updated = state.project.copy(fontStyle = fontStyle.id)
            persistProject(updated)
            state.copy(
                project = updated,
                lastExportedMp4 = null,
                statusMessage = "تم تطبيق ${fontStyle.labelAr} على مشاهد الموشن"
            )
        }
    }

    fun selectTransitionCut(cutIndex: Int?) {
        _uiState.update { state ->
            val toggled = if (state.selectedTransitionCutIndex == cutIndex) null else cutIndex
            state.copy(selectedTransitionCutIndex = toggled)
        }
    }

    fun updateSceneTransition(
        sceneIndex: Int,
        transitionType: SceneTransitionType? = null,
        durationSeconds: Float? = null
    ) {
        _uiState.update { state ->
            val scenes = state.project.scenes.toMutableList()
            val scene = scenes.getOrNull(sceneIndex) ?: return@update state
            scenes[sceneIndex] = scene.copy(
                transitionToNext = transitionType?.id ?: scene.transitionToNext,
                transitionDurationSeconds = durationSeconds?.coerceIn(0.25f, 1.0f)
                    ?: scene.transitionDurationSeconds
            )
            val updated = state.project.copy(scenes = scenes)
            persistProject(updated)
            state.copy(
                project = updated,
                lastExportedMp4 = null
            )
        }
    }

    fun applyTransitionToAll(
        transitionType: SceneTransitionType,
        durationSeconds: Float? = null
    ) {
        _uiState.update { state ->
            val updatedScenes = state.project.scenes.map { scene ->
                scene.copy(
                    transitionToNext = transitionType.id,
                    transitionDurationSeconds = durationSeconds?.coerceIn(0.25f, 1.0f)
                        ?: scene.transitionDurationSeconds
                )
            }
            val updated = state.project.copy(scenes = updatedScenes)
            persistProject(updated)
            state.copy(
                project = updated,
                lastExportedMp4 = null,
                statusMessage = "تم تعميم انتقال «${transitionType.labelAr}» بين جميع المشاهد"
            )
        }
    }

    fun previewTransitionCut(cutIndex: Int) {
        val state = _uiState.value
        val segments = state.timelineSegments
        val seg = segments.getOrNull(cutIndex) ?: return
        val leadBeforeCut = (seg.transitionToNextDuration + 0.45f).coerceAtLeast(0.55f)
        val previewTime = (seg.endTime - leadBeforeCut).coerceAtLeast(seg.startTime)
        lastTriggeredSceneKey = "${cutIndex}_${seg.scene.id}"
        audioEngine.stopVoiceover()
        _uiState.update {
            it.copy(
                selectedSceneIndex = cutIndex,
                currentTimeSeconds = previewTime,
                isPlaying = true
            )
        }
        syncBackgroundMusic()
    }

    fun toggleVoiceoverGlobal(enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.project.copy(voiceoverEnabled = enabled)
            persistProject(updated)
            state.copy(
                project = updated,
                statusMessage = if (enabled) "تم تفعيل أصوات استوديو جوجل" else "تم كتم التعليق الصوتي"
            )
        }
        if (!enabled) {
            voiceSynthesisJob?.cancel()
            audioEngine.stopVoiceover()
        } else if (_uiState.value.isPlaying) {
            syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
        }
    }

    fun selectVoiceGender(gender: SaudiVoiceGender) {
        voiceSynthesisJob?.cancel()
        audioEngine.stopVoiceover()
        val projectScenes = _uiState.value.project.scenes
        audioEngine.refreshDurationsForProject(projectScenes, gender)
        autoCloudTtsAllowed = true
        _uiState.update { state ->
            val updated = state.project.copy(voiceGender = gender.id)
            persistProject(updated)
            val ready = updated.scenes.count { audioEngine.hasAnyVoiceClip(it, gender) }
            state.copy(
                project = updated,
                isSynthesizingVoice = false,
                synthesizingSceneId = null,
                neuralVoicesReadyCount = ready,
                statusMessage = "تم اختيار صوت استوديو جوجل: ${gender.shortNameAr}"
            )
        }
        if (_uiState.value.project.voiceoverEnabled && _uiState.value.isPlaying) {
            val activeIdx = _uiState.value.activeFrame.primaryIndex
            val activeScene = _uiState.value.activeFrame.primaryScene
            if (audioEngine.hasAnyVoiceClip(activeScene, gender)) {
                syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
            } else {
                synthesizeVoices(
                    startSceneIndex = activeIdx,
                    playCurrentWhenReady = true,
                    allowCloudTts = true,
                    explicitUserAction = false
                )
            }
        }
    }

    fun toggleSoundEffectsGlobal(enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.project.copy(soundEffectsEnabled = enabled)
            persistProject(updated)
            state.copy(
                project = updated,
                statusMessage = if (enabled) "تم تفعيل المؤثرات التصنيفية للمحتوى" else "تم إيقاف المؤثرات الصوتية"
            )
        }
        if (enabled) {
            val currentSfx = _uiState.value.activeFrame.primaryScene.soundEffectEnum
            if (currentSfx != SceneSoundEffect.NONE) {
                audioEngine.playSoundEffect(currentSfx)
            }
        }
    }

    fun previewSoundEffect(effect: SceneSoundEffect) {
        if (effect != SceneSoundEffect.NONE) {
            audioEngine.playSoundEffect(effect)
        }
    }

    fun autoMatchSceneSfx(index: Int) {
        _uiState.update { state ->
            val scenes = state.project.scenes.toMutableList()
            val scene = scenes.getOrNull(index) ?: return@update state
            val matched = ContentSfxMatcher.matchForScene(
                scene = scene,
                paletteId = state.project.paletteId,
                genre = state.project.topicGenreEnum
            )
            scenes[index] = scene.copy(soundEffect = matched.id)
            val updated = state.project.copy(scenes = scenes)
            persistProject(updated)
            audioEngine.playSoundEffect(matched)
            state.copy(
                project = updated,
                statusMessage = "تم اختيار «${matched.labelAr}» لملاءمة محتوى المشهد ${index + 1}"
            )
        }
    }

    fun previewSceneVoiceover(index: Int) {
        val state = _uiState.value
        val scene = state.project.scenes.getOrNull(index) ?: return
        autoCloudTtsAllowed = true
        jumpToScene(index)
        _uiState.update { it.copy(isPlaying = true) }
        syncBackgroundMusic()
        if (state.project.soundEffectsEnabled && scene.soundEffectEnum != SceneSoundEffect.NONE) {
            audioEngine.playSoundEffect(scene.soundEffectEnum)
        }
        val voice = state.project.voiceGenderEnum
        val started = audioEngine.speakSceneWithGoogleStudio(scene, voice)
        if (!started) {
            synthesizeVoices(
                startSceneIndex = index,
                playCurrentWhenReady = true,
                allowCloudTts = true,
                explicitUserAction = true
            )
        }
    }

    fun synthesizeSaudiNeuralVoices() {
        autoCloudTtsAllowed = true
        val activeIdx = _uiState.value.activeFrame.primaryIndex
        synthesizeVoices(
            startSceneIndex = activeIdx,
            playCurrentWhenReady = true,
            allowCloudTts = true,
            explicitUserAction = true
        )
    }

    private fun synthesizeVoices(
        startSceneIndex: Int = 0,
        playCurrentWhenReady: Boolean = true,
        allowCloudTts: Boolean = true,
        explicitUserAction: Boolean = false
    ) {
        val state = _uiState.value
        val scenes = state.project.scenes
        if (scenes.isEmpty()) return
        val voice = state.project.voiceGenderEnum

        voiceSynthesisJob?.cancel()
        voiceSynthesisJob = viewModelScope.launch {
            synthesizeMissingScenesInternal(
                scenes = scenes,
                voice = voice,
                startSceneIndex = startSceneIndex,
                playCurrentWhenReady = playCurrentWhenReady,
                allowCloudTts = allowCloudTts,
                explicitUserAction = explicitUserAction
            )
        }
    }

    private suspend fun synthesizeMissingScenesInternal(
        scenes: List<MotionScene>,
        voice: SaudiVoiceGender,
        startSceneIndex: Int,
        playCurrentWhenReady: Boolean,
        allowCloudTts: Boolean = true,
        explicitUserAction: Boolean = false
    ): Boolean {
        if (scenes.isEmpty()) return true
        val orderedIndices = buildList {
            val safeStart = startSceneIndex.coerceIn(0, scenes.lastIndex)
            add(safeStart)
            for (i in scenes.indices) {
                if (i != safeStart) add(i)
            }
        }

        val pendingCount = orderedIndices.count { idx ->
            val s = scenes[idx]
            s.voiceoverEnabled &&
                s.effectiveVoiceScript.isNotBlank() &&
                !audioEngine.hasAnyVoiceClip(s, voice)
        }

        if (pendingCount == 0) {
            audioEngine.refreshDurationsForProject(scenes, voice)
            val readyCount = scenes.count { audioEngine.hasAnyVoiceClip(it, voice) }
            _uiState.update {
                it.copy(
                    isSynthesizingVoice = false,
                    synthesizingSceneId = null,
                    neuralVoicesReadyCount = readyCount
                )
            }
            if (playCurrentWhenReady && _uiState.value.isPlaying && _uiState.value.project.voiceoverEnabled) {
                val curScene = _uiState.value.activeFrame.primaryScene
                audioEngine.speakSceneWithGoogleStudio(curScene, voice)
            }
            return true
        }

        _uiState.update {
            it.copy(
                isSynthesizingVoice = true,
                voiceSynthesisProgress = 0.05f,
                statusMessage = if (explicitUserAction && allowCloudTts) {
                    "جاري تجهيز صوت ${voice.shortNameAr}..."
                } else {
                    it.statusMessage
                }
            )
        }

        var processed = 0
        var useLocalFallbackOnly = !allowCloudTts
        var usedLocalFallback = false

        for (idx in orderedIndices) {
            val scene = _uiState.value.project.scenes.getOrNull(idx) ?: continue
            val script = scene.effectiveVoiceScript
            if (!scene.voiceoverEnabled || script.isBlank()) continue

            if (!audioEngine.hasAnyVoiceClip(scene, voice)) {
                _uiState.update { it.copy(synthesizingSceneId = scene.id) }
                var clipReady = false

                if (!useLocalFallbackOnly) {
                    val res = aiRepository.synthesizeSaudiVoiceover(script, voice)
                    res.fold(
                        onSuccess = { clip ->
                            audioEngine.cacheGoogleStudioClip(scene.id, voice, script, clip)
                            clipReady = true
                        },
                        onFailure = { err ->
                            if (err is TtsQuotaExceededException) {
                                // Circuit-break Cloud TTS so we don't spam exhausted quota across remaining scenes
                                useLocalFallbackOnly = true
                            }
                            Log.w(TAG, "Cloud TTS unavailable (${err.message}); falling back to on-device Arabic TTS")
                        }
                    )
                }

                if (!clipReady) {
                    val localOk = audioEngine.synthesizeDeviceArabicClip(scene, voice)
                    if (localOk) {
                        clipReady = true
                        usedLocalFallback = true
                    }
                }

                if (clipReady) {
                    val currentState = _uiState.value
                    if (playCurrentWhenReady &&
                        currentState.isPlaying &&
                        currentState.project.voiceoverEnabled &&
                        currentState.activeFrame.primaryScene.id == scene.id
                    ) {
                        audioEngine.speakSceneWithGoogleStudio(scene, voice)
                    }
                }
                processed++
            }

            val readyCount = _uiState.value.project.scenes.count {
                audioEngine.hasAnyVoiceClip(it, voice)
            }
            _uiState.update {
                it.copy(
                    voiceSynthesisProgress = (processed.toFloat() / pendingCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                    neuralVoicesReadyCount = readyCount
                )
            }
        }

        val finalReady = _uiState.value.project.scenes.count {
            audioEngine.hasAnyVoiceClip(it, voice)
        }
        _uiState.update { current ->
            current.copy(
                isSynthesizingVoice = false,
                synthesizingSceneId = null,
                voiceSynthesisProgress = 0f,
                neuralVoicesReadyCount = finalReady,
                statusMessage = when {
                    explicitUserAction && usedLocalFallback && finalReady > 0 ->
                        "تم تجهيز $finalReady مشاهد بالصوت العربي المحلي (لانتهاء حصة السحابة اليومية)"
                    explicitUserAction && finalReady > 0 ->
                        "تم تجهيز الصوت لـ $finalReady مشاهد بنجاح"
                    else -> current.statusMessage
                }
            )
        }
        return finalReady > 0
    }

    fun toggleAiChatSheet(show: Boolean) {
        _uiState.update { it.copy(showAiChatSheet = show) }
    }

    fun sendChatEditMessage(message: String, onEditedJumpToPreview: () -> Unit = {}) {
        val trimmed = message.trim()
        if (trimmed.isBlank() || _uiState.value.isApplyingChatEdit) return

        val userMsg = AiChatMessage(isUser = true, text = trimmed)
        val snapshotHistory = _uiState.value.chatMessages
        val currentProject = _uiState.value.project

        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                isApplyingChatEdit = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val res = aiRepository.chatEditProject(
                currentProject = currentProject,
                chatHistory = snapshotHistory,
                userInstruction = trimmed
            )
            res.fold(
                onSuccess = { editResult ->
                    val updated = editResult.updatedProject
                    persistProject(updated)
                    autoCloudTtsAllowed = true
                    lastTriggeredSceneKey = null
                    audioEngine.refreshDurationsForProject(updated.scenes, updated.voiceGenderEnum)
                    val readyCount = updated.scenes.count {
                        audioEngine.hasAnyVoiceClip(it, updated.voiceGenderEnum)
                    }
                    val aiMsg = AiChatMessage(
                        isUser = false,
                        text = editResult.assistantReplyAr,
                        appliedChangesBadge = "${updated.scenes.size} مشاهد • ${updated.palette.nameAr}"
                    )
                    _uiState.update {
                        it.copy(
                            project = updated,
                            selectedTopicGenre = updated.topicGenreEnum,
                            selectedSceneIndex = it.selectedSceneIndex.coerceIn(0, updated.scenes.lastIndex),
                            selectedTransitionCutIndex = null,
                            currentTimeSeconds = 0f,
                            isPlaying = true,
                            isApplyingChatEdit = false,
                            neuralVoicesReadyCount = readyCount,
                            lastExportedMp4 = null,
                            chatMessages = it.chatMessages + aiMsg,
                            statusMessage = "تم تطبيق تعديلك على الفيديو"
                        )
                    }
                    syncBackgroundMusic()
                    onEditedJumpToPreview()
                    if (updated.voiceoverEnabled) {
                        synthesizeVoices(
                            startSceneIndex = 0,
                            playCurrentWhenReady = true,
                            allowCloudTts = true,
                            explicitUserAction = false
                        )
                    } else {
                        syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
                    }
                },
                onFailure = { err ->
                    val errText = err.message ?: "تعذر تطبيق التعديل."
                    val failMsg = AiChatMessage(
                        isUser = false,
                        text = "عذراً، واجهت مشكلة أثناء تنفيذ التعديل: $errText"
                    )
                    _uiState.update {
                        it.copy(
                            isApplyingChatEdit = false,
                            chatMessages = it.chatMessages + failMsg,
                            errorMessage = errText
                        )
                    }
                }
            )
        }
    }

    fun improveAdCopy() {
        val raw = _uiState.value.promptInput.trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(errorMessage = "يرجى كتابة فكرة الفيديو أولاً لتحسين النص.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImprovingCopy = true, errorMessage = null, statusMessage = null) }
            val result = aiRepository.improveAdCopy(raw)
            result.fold(
                onSuccess = { improved ->
                    _uiState.update {
                        it.copy(
                            isImprovingCopy = false,
                            previousPromptForUndo = raw,
                            promptInput = improved,
                            statusMessage = "تم تحسين الفكرة بأسلوب إبداعي عبر Gemini 4 Neon"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isImprovingCopy = false,
                            errorMessage = err.message ?: "تعذر تحسين النص."
                        )
                    }
                }
            )
        }
    }

    fun undoImprovedCopy() {
        _uiState.update { state ->
            val prev = state.previousPromptForUndo ?: return@update state
            state.copy(
                promptInput = prev,
                previousPromptForUndo = null,
                statusMessage = "تمت استعادة النص السابق"
            )
        }
    }

    fun generateMotionGraphics(onSuccessJumpToPreview: () -> Unit = {}) {
        val idea = _uiState.value.promptInput.trim()
        if (idea.isBlank()) {
            _uiState.update { it.copy(errorMessage = "يرجى إدخال فكرة الفيديو قبل التوليد.") }
            return
        }
        val stateSnapshot = _uiState.value
        val currentProject = stateSnapshot.project
        voiceSynthesisJob?.cancel()
        audioEngine.stopVoiceover()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingScenes = true,
                    errorMessage = null,
                    statusMessage = "جاري البحث عن مراجع محترفة وتصميم هوية الفيديو..."
                )
            }
            val result = aiRepository.generateMotionProject(
                idea = idea,
                aspectRatio = currentProject.aspectRatioEnum,
                preferredGenre = stateSnapshot.selectedTopicGenre,
                creativeFreedomMode = stateSnapshot.creativeFreedomMode,
                manualPalette = currentProject.palette,
                manualFont = currentProject.fontStyleEnum,
                manualMusic = currentProject.backgroundMusicEnum,
                musicVolume = currentProject.backgroundMusicVolume,
                autoDuckingEnabled = currentProject.autoDuckingEnabled,
                voiceGender = currentProject.voiceGenderEnum,
                voiceoverEnabled = currentProject.voiceoverEnabled,
                soundEffectsEnabled = currentProject.soundEffectsEnabled
            )
            result.fold(
                onSuccess = { generated ->
                    persistProject(generated)
                    autoCloudTtsAllowed = true
                    lastTriggeredSceneKey = null
                    _uiState.update {
                        it.copy(
                            project = generated,
                            selectedTopicGenre = generated.topicGenreEnum,
                            selectedSceneIndex = 0,
                            selectedTransitionCutIndex = null,
                            currentTimeSeconds = 0f,
                            isPlaying = true,
                            isGeneratingScenes = false,
                            neuralVoicesReadyCount = 0,
                            voiceDurationsBySceneId = emptyMap(),
                            lastExportedMp4 = null,
                            statusMessage = "تم تصميم ${generated.scenes.size} مشاهد بهوية «${generated.palette.nameAr}» وموسيقى متناغمة"
                        )
                    }
                    syncBackgroundMusic()
                    onSuccessJumpToPreview()
                    if (generated.voiceoverEnabled) {
                        synthesizeVoices(
                            startSceneIndex = 0,
                            playCurrentWhenReady = true,
                            allowCloudTts = true,
                            explicitUserAction = false
                        )
                    } else {
                        syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isGeneratingScenes = false,
                            errorMessage = err.message ?: "فشل توليد المشاهد عبر الذكاء الاصطناعي."
                        )
                    }
                }
            )
        }
    }

    fun togglePlayPause() {
        val nextPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = nextPlaying) }
        syncBackgroundMusic()
        if (!nextPlaying) {
            audioEngine.stopVoiceover()
        } else {
            syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
        }
    }

    fun replayFromStart() {
        lastTriggeredSceneKey = null
        _uiState.update { it.copy(currentTimeSeconds = 0f, isPlaying = true) }
        syncBackgroundMusic()
        syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
    }

    fun jumpToScene(index: Int) {
        val segments = _uiState.value.timelineSegments
        val targetSeg = segments.getOrNull(index) ?: return
        lastTriggeredSceneKey = null
        _uiState.update {
            it.copy(
                selectedSceneIndex = index,
                currentTimeSeconds = targetSeg.startTime
            )
        }
        if (_uiState.value.isPlaying) {
            syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
        }
    }

    fun nextScene() {
        val scenes = _uiState.value.project.scenes
        if (scenes.isEmpty()) return
        val activeIdx = _uiState.value.activeFrame.primaryIndex
        jumpToScene((activeIdx + 1) % scenes.size)
    }

    fun previousScene() {
        val scenes = _uiState.value.project.scenes
        if (scenes.isEmpty()) return
        val activeIdx = _uiState.value.activeFrame.primaryIndex
        val prev = if (activeIdx - 1 < 0) scenes.lastIndex else activeIdx - 1
        jumpToScene(prev)
    }

    fun selectSceneForEdit(index: Int) {
        val scenes = _uiState.value.project.scenes
        if (index in scenes.indices) {
            jumpToScene(index)
        }
    }

    fun updateScene(
        index: Int,
        badge: String? = null,
        headline: String? = null,
        subheadline: String? = null,
        durationSeconds: Float? = null,
        animationType: MotionAnimationType? = null,
        visualMotif: VisualMotif? = null,
        sceneSymbol: SceneSymbol? = null,
        soundEffect: SceneSoundEffect? = null,
        voiceoverScript: String? = null,
        voiceoverEnabled: Boolean? = null,
        transitionToNext: SceneTransitionType? = null
    ) {
        _uiState.update { state ->
            val scenes = state.project.scenes.toMutableList()
            if (index !in scenes.indices) return@update state
            val old = scenes[index]
            val draft = old.copy(
                badge = badge ?: old.badge,
                headline = headline ?: old.headline,
                subheadline = subheadline ?: old.subheadline,
                durationSeconds = durationSeconds?.coerceIn(1.5f, 12.0f) ?: old.durationSeconds,
                animationType = animationType?.id ?: old.animationType,
                visualMotif = visualMotif?.id ?: old.visualMotif,
                sceneSymbol = sceneSymbol?.id ?: old.sceneSymbol,
                soundEffect = soundEffect?.id ?: old.soundEffect,
                voiceoverScript = voiceoverScript ?: old.voiceoverScript,
                voiceoverEnabled = voiceoverEnabled ?: old.voiceoverEnabled,
                transitionToNext = transitionToNext?.id ?: old.transitionToNext
            )
            val finalScene = if (soundEffect == null && (headline != null || subheadline != null || sceneSymbol != null) && old.soundEffectEnum != SceneSoundEffect.NONE) {
                val matched = ContentSfxMatcher.matchForScene(
                    scene = draft,
                    paletteId = state.project.paletteId,
                    genre = state.project.topicGenreEnum
                )
                draft.copy(soundEffect = matched.id)
            } else {
                draft
            }
            scenes[index] = finalScene
            val updated = state.project.copy(scenes = scenes)
            persistProject(updated)
            audioEngine.refreshDurationsForProject(scenes, updated.voiceGenderEnum)
            val readyCount = updated.scenes.count {
                audioEngine.hasGoogleStudioClip(it, updated.voiceGenderEnum)
            }
            state.copy(
                project = updated,
                neuralVoicesReadyCount = readyCount,
                lastExportedMp4 = null
            )
        }
    }

    fun addNewScene() {
        _uiState.update { state ->
            val nextNum = state.project.scenes.size + 1
            val symbol = SceneSymbol.entries[1 + (nextNum % (SceneSymbol.entries.size - 1))]
            val draftScene = MotionScene(
                id = UUID.randomUUID().toString(),
                order = nextNum,
                badge = "مشهد $nextNum",
                headline = "رسالة إبداعية جديدة",
                subheadline = "أضف وصفاً قصيراً يوضح الفكرة للمشاهد",
                durationSeconds = 4.0f,
                animationType = MotionAnimationType.entries[nextNum % MotionAnimationType.entries.size].id,
                visualMotif = VisualMotif.entries[nextNum % VisualMotif.entries.size].id,
                sceneSymbol = symbol.id,
                voiceoverScript = "رسالة إبداعية جديدة توصل فكرتك بأفضل صورة!",
                transitionToNext = SceneTransitionType.CROSS_FADE.id
            )
            val matchedSfx = ContentSfxMatcher.matchForScene(
                scene = draftScene,
                paletteId = state.project.paletteId,
                genre = state.project.topicGenreEnum
            )
            val newScene = draftScene.copy(soundEffect = matchedSfx.id)
            val updatedScenes = state.project.scenes + newScene
            val updated = state.project.copy(scenes = updatedScenes)
            persistProject(updated)
            state.copy(
                project = updated,
                selectedSceneIndex = updatedScenes.lastIndex,
                lastExportedMp4 = null,
                statusMessage = "تمت إضافة المشهد رقم $nextNum"
            )
        }
        jumpToScene(_uiState.value.project.scenes.lastIndex)
    }

    fun deleteScene(index: Int) {
        _uiState.update { state ->
            if (state.project.scenes.size <= 1) {
                return@update state.copy(errorMessage = "يجب أن يحتوي المشروع على مشهد واحد على الأقل.")
            }
            val updatedScenes = state.project.scenes
                .filterIndexed { i, _ -> i != index }
                .mapIndexed { i, scene -> scene.copy(order = i + 1) }
            val updated = state.project.copy(scenes = updatedScenes)
            persistProject(updated)
            state.copy(
                project = updated,
                selectedSceneIndex = state.selectedSceneIndex.coerceIn(0, updatedScenes.lastIndex),
                selectedTransitionCutIndex = null,
                currentTimeSeconds = 0f,
                lastExportedMp4 = null,
                statusMessage = "تم حذف المشهد"
            )
        }
    }

    fun moveScene(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = index + delta
            val list = state.project.scenes.toMutableList()
            if (index !in list.indices || target !in list.indices) return@update state
            val item = list.removeAt(index)
            list.add(target, item)
            val reindexed = list.mapIndexed { i, scene -> scene.copy(order = i + 1) }
            val updated = state.project.copy(scenes = reindexed)
            persistProject(updated)
            state.copy(
                project = updated,
                selectedSceneIndex = target,
                selectedTransitionCutIndex = null,
                lastExportedMp4 = null
            )
        }
    }

    fun renderAndExportVideo(onReadyToSaveOrShare: (File) -> Unit = {}) {
        val initialState = _uiState.value
        if (initialState.isExportingVideo) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExportingVideo = true,
                    exportProgress = 0.02f,
                    errorMessage = null,
                    statusMessage = "جاري تجهيز الأصوات والموسيقى المتوازنة للدمج..."
                )
            }
            syncBackgroundMusic()

            val project = _uiState.value.project
            if (project.voiceoverEnabled) {
                val missingAny = project.scenes.any {
                    it.voiceoverEnabled &&
                        it.effectiveVoiceScript.isNotBlank() &&
                        !audioEngine.hasAnyVoiceClip(it, project.voiceGenderEnum)
                }
                if (missingAny) {
                    voiceSynthesisJob?.cancel()
                    synthesizeMissingScenesInternal(
                        scenes = project.scenes,
                        voice = project.voiceGenderEnum,
                        startSceneIndex = 0,
                        playCurrentWhenReady = false,
                        allowCloudTts = true,
                        explicitUserAction = false
                    )
                }
            }

            val finalProject = _uiState.value.project
            audioEngine.refreshDurationsForProject(finalProject.scenes, finalProject.voiceGenderEnum)

            val voiceFiles = mutableMapOf<String, File>()
            val voiceDurations = mutableMapOf<String, Float>()
            for (scene in finalProject.scenes) {
                val file = audioEngine.getClipFile(scene, finalProject.voiceGenderEnum)
                if (file != null) {
                    voiceFiles[scene.id] = file
                }
                val dur = audioEngine.getClipDurationSeconds(scene, finalProject.voiceGenderEnum)
                if (dur > 0f) {
                    voiceDurations[scene.id] = dur
                }
            }

            _uiState.update {
                it.copy(
                    voiceDurationsBySceneId = voiceDurations,
                    statusMessage = "جاري تصدير ${finalProject.scenes.size} لقطات مع الموسيقى والانتقالات..."
                )
            }

            val result = MotionVideoExporter.exportProjectToMp4(
                context = getApplication(),
                project = finalProject,
                voiceFilesBySceneId = voiceFiles,
                voiceDurationsBySceneId = voiceDurations,
                onProgress = { progress ->
                    _uiState.update { it.copy(exportProgress = progress) }
                }
            )
            result.fold(
                onSuccess = { mp4File ->
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            exportProgress = 1f,
                            lastExportedMp4 = mp4File,
                            statusMessage = "تم دمج الفيديو والموسيقى الخلفية والتعليق في ملف MP4 بنجاح!"
                        )
                    }
                    syncBackgroundMusic()
                    onReadyToSaveOrShare(mp4File)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isExportingVideo = false,
                            exportProgress = 0f,
                            errorMessage = err.message ?: "فشل تصدير الفيديو."
                        )
                    }
                    syncBackgroundMusic()
                }
            )
        }
    }

    fun saveMp4ToUri(uri: Uri) {
        val file = _uiState.value.lastExportedMp4 ?: return
        viewModelScope.launch {
            val res = MotionVideoExporter.copyFileToUri(getApplication(), file, uri)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم حفظ ملف الفيديو MP4 بنجاح") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "تعذر حفظ الفيديو") }
                }
            )
        }
    }

    fun saveJsonToUri(uri: Uri) {
        val json = _uiState.value.project.toPrettyJson()
        viewModelScope.launch {
            val res = MotionVideoExporter.writeTextToUri(getApplication(), uri, json)
            res.fold(
                onSuccess = {
                    _uiState.update { it.copy(statusMessage = "تم حفظ ملف المشروع JSON بنجاح") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "تعذر حفظ ملف JSON") }
                }
            )
        }
    }

    fun importJsonFromUri(uri: Uri) {
        viewModelScope.launch {
            val readRes = MotionVideoExporter.readTextFromUri(getApplication(), uri)
            readRes.fold(
                onSuccess = { raw ->
                    val parsed = MotionProjectJson.fromJson(raw)
                    if (parsed == null || parsed.scenes.isEmpty()) {
                        _uiState.update {
                            it.copy(errorMessage = "ملف JSON غير صالح أو لا يحتوي على مشاهد.")
                        }
                    } else {
                        persistProject(parsed)
                        lastTriggeredSceneKey = null
                        audioEngine.refreshDurationsForProject(parsed.scenes, parsed.voiceGenderEnum)
                        _uiState.update {
                            it.copy(
                                project = parsed,
                                promptInput = parsed.promptIdea.ifBlank { it.promptInput },
                                selectedTopicGenre = parsed.topicGenreEnum,
                                selectedSceneIndex = 0,
                                selectedTransitionCutIndex = null,
                                currentTimeSeconds = 0f,
                                isPlaying = true,
                                lastExportedMp4 = null,
                                statusMessage = "تم استيراد المشروع «${parsed.projectTitle}»"
                            )
                        }
                        syncBackgroundMusic()
                        syncAudioWithActiveFrame(_uiState.value, forceTrigger = true)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "تعذر فتح الملف.") }
                }
            )
        }
    }

    fun toggleJsonDialog(show: Boolean) {
        _uiState.update { it.copy(showJsonDialog = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceSynthesisJob?.cancel()
        audioEngine.release()
    }
}
