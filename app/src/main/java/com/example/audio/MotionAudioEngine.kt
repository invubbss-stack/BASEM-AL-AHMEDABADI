package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.BackgroundMusicTrack
import com.example.model.MotionScene
import com.example.model.SaudiVoiceGender
import com.example.model.SceneSoundEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "MotionAudioEngine"
private const val SFX_SAMPLE_RATE = 44100

data class GeneratedAudioClip(
    val bytes: ByteArray,
    val mimeType: String,
    val sampleRateHz: Int = 24000
)

data class AudioSyncState(
    val isSpeaking: Boolean = false,
    val activeSceneId: String? = null,
    val spokenFraction: Float = 0f,
    val isUsingGoogleStudioVoice: Boolean = false
)

class MotionAudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _syncState = MutableStateFlow(AudioSyncState())
    val syncState: StateFlow<AudioSyncState> = _syncState.asStateFlow()

    private var activeMediaPlayer: MediaPlayer? = null
    private var activeSpeechJob: Job? = null

    private var musicMediaPlayer: MediaPlayer? = null
    private var activeMusicTrack: BackgroundMusicTrack = BackgroundMusicTrack.NONE
    private var musicBaseVolume: Float = 0.42f
    private var musicAutoDucking: Boolean = true
    private var musicPlaybackDesired: Boolean = false
    private var currentAppliedMusicVolume: Float = 0f
    private var musicJob: Job? = null
    private var duckMonitorJob: Job? = null

    private val studioClipFiles = mutableMapOf<String, File>()
    private val localTtsClipFiles = mutableMapOf<String, File>()
    private val clipDurationSecondsMap = mutableMapOf<String, Float>()
    private val sfxBufferCache = mutableMapOf<SceneSoundEffect, ShortArray>()
    private val musicWavCache = mutableMapOf<BackgroundMusicTrack, File>()

    private val deviceTtsMutex = Mutex()
    private var deviceTtsEngine: TextToSpeech? = null
    private var deviceTtsReady: Boolean = false

    private val _clipDurationsBySceneId = MutableStateFlow<Map<String, Float>>(emptyMap())
    val clipDurationsBySceneId: StateFlow<Map<String, Float>> = _clipDurationsBySceneId.asStateFlow()

    private val ttsCacheDir: File by lazy {
        File(context.cacheDir, "google_studio_tts").apply { mkdirs() }
    }

    private val musicCacheDir: File by lazy {
        File(context.cacheDir, "motion_bg_music").apply { mkdirs() }
    }

    init {
        // Pre-warm high-fidelity 44.1kHz studio SFX buffers
        scope.launch {
            SceneSoundEffect.entries.forEach { effect ->
                if (effect != SceneSoundEffect.NONE) {
                    sfxBufferCache[effect] = synthesizeStudioSfxPcm(effect)
                }
            }
        }
        startDuckingMonitor()
    }

    private fun startDuckingMonitor() {
        duckMonitorJob?.cancel()
        duckMonitorJob = scope.launch {
            while (isActive) {
                delay(35L)
                val player = musicMediaPlayer
                if (player != null && musicPlaybackDesired && activeMusicTrack != BackgroundMusicTrack.NONE) {
                    val isSpeakingNow = _syncState.value.isSpeaking
                    val target = if (musicAutoDucking && isSpeakingNow) {
                        (musicBaseVolume * 0.24f).coerceIn(0.03f, 1f)
                    } else {
                        musicBaseVolume.coerceIn(0f, 1f)
                    }
                    // Smooth attack/release gain slewing
                    val slew = if (target < currentAppliedMusicVolume) 0.28f else 0.14f
                    currentAppliedMusicVolume += (target - currentAppliedMusicVolume) * slew
                    runCatching {
                        if (player.isPlaying) {
                            player.setVolume(currentAppliedMusicVolume, currentAppliedMusicVolume)
                        }
                    }
                }
            }
        }
    }

    fun updateBackgroundMusic(
        track: BackgroundMusicTrack,
        volume: Float,
        autoDucking: Boolean,
        isPlaying: Boolean
    ) {
        musicBaseVolume = volume.coerceIn(0f, 1f)
        musicAutoDucking = autoDucking
        musicPlaybackDesired = isPlaying && track != BackgroundMusicTrack.NONE

        if (!musicPlaybackDesired) {
            pauseBackgroundMusic()
            activeMusicTrack = track
            return
        }

        if (track != activeMusicTrack || musicMediaPlayer == null) {
            activeMusicTrack = track
            launchMusicTrackLoop(track)
        } else {
            runCatching {
                musicMediaPlayer?.let { p ->
                    if (!p.isPlaying) p.start()
                }
            }
        }
    }

    fun previewMusicTrack(track: BackgroundMusicTrack, volume: Float = 0.55f) {
        if (track == BackgroundMusicTrack.NONE) {
            stopBackgroundMusic()
            return
        }
        musicBaseVolume = volume.coerceIn(0f, 1f)
        musicPlaybackDesired = true
        activeMusicTrack = track
        launchMusicTrackLoop(track)
    }

    private fun launchMusicTrackLoop(track: BackgroundMusicTrack) {
        musicJob?.cancel()
        musicJob = scope.launch {
            val wavFile = withContext(Dispatchers.Default) {
                getOrCreateMusicWav(track)
            } ?: return@launch
            if (!isActive || !musicPlaybackDesired || activeMusicTrack != track) return@launch

            runCatching {
                musicMediaPlayer?.stop()
                musicMediaPlayer?.release()
            }
            musicMediaPlayer = null

            runCatching {
                val initVol = if (musicAutoDucking && _syncState.value.isSpeaking) {
                    musicBaseVolume * 0.24f
                } else {
                    musicBaseVolume
                }
                currentAppliedMusicVolume = initVol
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(wavFile.absolutePath)
                    isLooping = true
                    setVolume(initVol, initVol)
                    prepare()
                    start()
                }
                musicMediaPlayer = player
            }.onFailure { err ->
                Log.w(TAG, "Background music playback error", err)
            }
        }
    }

    private fun pauseBackgroundMusic() {
        runCatching {
            musicMediaPlayer?.let {
                if (it.isPlaying) it.pause()
            }
        }
    }

    fun stopBackgroundMusic() {
        musicJob?.cancel()
        musicPlaybackDesired = false
        runCatching {
            musicMediaPlayer?.stop()
            musicMediaPlayer?.release()
        }
        musicMediaPlayer = null
    }

    private fun getOrCreateMusicWav(track: BackgroundMusicTrack): File? {
        if (track == BackgroundMusicTrack.NONE) return null
        musicWavCache[track]?.takeIf { it.exists() && it.length() > 44L }?.let { return it }

        val target = File(musicCacheDir, "${track.id.lowercase()}_v2.wav")
        if (target.exists() && target.length() > 44L) {
            musicWavCache[track] = target
            return target
        }

        val pcm = synthesizeBackgroundMusicLoopPcm(track)
        if (pcm.isEmpty()) return null
        val byteBuf = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) byteBuf.putShort(s)
        val wavBytes = wrapPcmAsWav(byteBuf.array(), SFX_SAMPLE_RATE, channels = 1, bitsPerSample = 16)
        target.writeBytes(wavBytes)
        musicWavCache[track] = target
        return target
    }

    fun cacheGoogleStudioClip(
        sceneId: String,
        voice: SaudiVoiceGender,
        script: String,
        clip: GeneratedAudioClip
    ) {
        val key = clipKey(sceneId, voice, script)
        val mime = clip.mimeType.lowercase()
        val file = when {
            mime.contains("l16") || mime.contains("pcm") -> {
                val sampleRate = parseSampleRate(mime) ?: clip.sampleRateHz
                val wavBytes = wrapPcmAsWav(clip.bytes, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
                File(ttsCacheDir, "${key.hashCode()}.wav").apply { writeBytes(wavBytes) }
            }
            mime.contains("wav") -> {
                File(ttsCacheDir, "${key.hashCode()}.wav").apply { writeBytes(clip.bytes) }
            }
            mime.contains("ogg") -> {
                File(ttsCacheDir, "${key.hashCode()}.ogg").apply { writeBytes(clip.bytes) }
            }
            else -> {
                File(ttsCacheDir, "${key.hashCode()}.mp3").apply { writeBytes(clip.bytes) }
            }
        }
        studioClipFiles[key] = file
        val durationSec = measureClipDurationSeconds(file)
        if (durationSec > 0f) {
            clipDurationSecondsMap[key] = durationSec
            _clipDurationsBySceneId.update { it + (sceneId to durationSec) }
        }
    }

    fun getClipFile(scene: MotionScene, voice: SaudiVoiceGender): File? {
        val key = clipKey(scene.id, voice, scene.effectiveVoiceScript)
        val cached = studioClipFiles[key]
        if (cached != null && cached.exists() && cached.length() > 44L) return cached

        val cloudCandidates = listOf(
            File(ttsCacheDir, "${key.hashCode()}.wav"),
            File(ttsCacheDir, "${key.hashCode()}.mp3"),
            File(ttsCacheDir, "${key.hashCode()}.ogg")
        )
        val cloudDisk = cloudCandidates.firstOrNull { it.exists() && it.length() > 44L }
        if (cloudDisk != null) {
            studioClipFiles[key] = cloudDisk
            val durationSec = clipDurationSecondsMap.getOrPut(key) { measureClipDurationSeconds(cloudDisk) }
            if (durationSec > 0f) {
                _clipDurationsBySceneId.update { it + (scene.id to durationSec) }
            }
            return cloudDisk
        }

        val localCached = localTtsClipFiles[key]
        if (localCached != null && localCached.exists() && localCached.length() > 44L) return localCached

        val localDisk = File(ttsCacheDir, "${key.hashCode()}_local.wav")
        if (localDisk.exists() && localDisk.length() > 44L) {
            localTtsClipFiles[key] = localDisk
            val durationSec = clipDurationSecondsMap.getOrPut("${key}_local") { measureClipDurationSeconds(localDisk) }
            if (durationSec > 0f) {
                _clipDurationsBySceneId.update { it + (scene.id to durationSec) }
            }
            return localDisk
        }
        return null
    }

    fun getClipDurationSeconds(scene: MotionScene, voice: SaudiVoiceGender): Float {
        val key = clipKey(scene.id, voice, scene.effectiveVoiceScript)
        clipDurationSecondsMap[key]?.let { if (it > 0f) return it }
        clipDurationSecondsMap["${key}_local"]?.let { if (it > 0f) return it }
        val file = getClipFile(scene, voice) ?: return 0f
        return measureClipDurationSeconds(file).also {
            if (it > 0f) {
                val mapKey = if (file.name.endsWith("_local.wav")) "${key}_local" else key
                clipDurationSecondsMap[mapKey] = it
            }
        }
    }

    fun isActivelyPlayingScene(sceneId: String): Boolean {
        val s = _syncState.value
        return s.isSpeaking && s.activeSceneId == sceneId && (activeMediaPlayer?.isPlaying == true)
    }

    fun hasGoogleStudioClip(scene: MotionScene, voice: SaudiVoiceGender): Boolean {
        val key = clipKey(scene.id, voice, scene.effectiveVoiceScript)
        val cached = studioClipFiles[key]
        if (cached != null && cached.exists() && cached.length() > 44L) return true
        return listOf(
            File(ttsCacheDir, "${key.hashCode()}.wav"),
            File(ttsCacheDir, "${key.hashCode()}.mp3"),
            File(ttsCacheDir, "${key.hashCode()}.ogg")
        ).any { it.exists() && it.length() > 44L }
    }

    fun hasAnyVoiceClip(scene: MotionScene, voice: SaudiVoiceGender): Boolean {
        return getClipFile(scene, voice) != null
    }

    suspend fun synthesizeDeviceArabicClip(
        scene: MotionScene,
        voice: SaudiVoiceGender
    ): Boolean = deviceTtsMutex.withLock {
        val script = scene.effectiveVoiceScript
        if (script.isBlank()) return@withLock true
        if (getClipFile(scene, voice) != null) return@withLock true

        val key = clipKey(scene.id, voice, script)
        val outFile = File(ttsCacheDir, "${key.hashCode()}_local.wav")
        if (outFile.exists() && outFile.length() > 44L) {
            localTtsClipFiles[key] = outFile
            val dur = measureClipDurationSeconds(outFile)
            if (dur > 0f) {
                clipDurationSecondsMap["${key}_local"] = dur
                _clipDurationsBySceneId.update { it + (scene.id to dur) }
            }
            return@withLock true
        }

        val tts = ensureDeviceTtsReady() ?: return@withLock false
        val completion = CompletableDeferred<Boolean>()
        val utteranceId = "motion_tts_${key.hashCode()}_${System.nanoTime()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId) completion.complete(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) completion.complete(false)
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) completion.complete(false)
            }
        })

        withContext(Dispatchers.Main) {
            runCatching {
                val saLocale = Locale("ar", "SA")
                val res = tts.setLanguage(saLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale("ar"))
                }
                tts.setPitch(if (voice.isMale) 0.90f else 1.12f)
                tts.setSpeechRate(1.02f)
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                val queued = tts.synthesizeToFile(script, params, outFile, utteranceId)
                if (queued != TextToSpeech.SUCCESS) {
                    completion.complete(false)
                }
            }.onFailure {
                completion.complete(false)
            }
        }

        val ok = withTimeoutOrNull(12_000L) { completion.await() } ?: false
        if (ok && outFile.exists() && outFile.length() > 44L) {
            localTtsClipFiles[key] = outFile
            val dur = measureClipDurationSeconds(outFile)
            if (dur > 0f) {
                clipDurationSecondsMap["${key}_local"] = dur
                _clipDurationsBySceneId.update { it + (scene.id to dur) }
            }
            return@withLock true
        }
        return@withLock false
    }

    private suspend fun ensureDeviceTtsReady(): TextToSpeech? {
        if (deviceTtsReady && deviceTtsEngine != null) return deviceTtsEngine
        val readyDeferred = CompletableDeferred<Boolean>()
        val engine = withContext(Dispatchers.Main) {
            TextToSpeech(context.applicationContext) { status ->
                readyDeferred.complete(status == TextToSpeech.SUCCESS)
            }
        }
        val initialized = withTimeoutOrNull(5_000L) { readyDeferred.await() } ?: false
        return if (initialized) {
            deviceTtsEngine = engine
            deviceTtsReady = true
            engine
        } else {
            runCatching { engine.shutdown() }
            null
        }
    }

    fun refreshDurationsForProject(scenes: List<MotionScene>, voice: SaudiVoiceGender) {
        val map = mutableMapOf<String, Float>()
        scenes.forEach { scene ->
            val dur = getClipDurationSeconds(scene, voice)
            if (dur > 0f) map[scene.id] = dur
        }
        _clipDurationsBySceneId.value = map
    }

    fun playSoundEffect(effect: SceneSoundEffect) {
        if (effect == SceneSoundEffect.NONE) return
        scope.launch {
            val pcm = sfxBufferCache.getOrPut(effect) { synthesizeStudioSfxPcm(effect) }
            playPcmArrayOnce(pcm, SFX_SAMPLE_RATE)
        }
    }

    fun speakSceneWithGoogleStudio(
        scene: MotionScene,
        voice: SaudiVoiceGender
    ): Boolean {
        val text = scene.effectiveVoiceScript
        if (text.isBlank()) return true

        stopVoiceover()

        val audioFile = getClipFile(scene, voice) ?: return false
        playAudioFile(scene.id, audioFile, scene.durationSeconds)
        return true
    }

    fun stopVoiceover() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null
        runCatching {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        }
        _syncState.update {
            it.copy(
                isSpeaking = false,
                activeSceneId = null,
                spokenFraction = 0f
            )
        }
    }

    private fun playAudioFile(
        sceneId: String,
        file: File,
        sceneDurationSeconds: Float
    ) {
        activeSpeechJob = scope.launch {
            var player: MediaPlayer? = null
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                }
                activeMediaPlayer = player
                _syncState.update {
                    it.copy(
                        isSpeaking = true,
                        activeSceneId = sceneId,
                        spokenFraction = 0.05f,
                        isUsingGoogleStudioVoice = true
                    )
                }
                player.start()
                val totalMs = player.duration.takeIf { it > 0 } ?: (sceneDurationSeconds * 1000).toInt()
                while (player.isPlaying && isActive) {
                    val frac = (player.currentPosition.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    _syncState.update { it.copy(spokenFraction = frac) }
                    delay(40L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Studio audio playback error", e)
            } finally {
                runCatching { player?.release() }
                if (activeMediaPlayer == player) activeMediaPlayer = null
                _syncState.update {
                    if (it.activeSceneId == sceneId) {
                        it.copy(isSpeaking = false, spokenFraction = 1f)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun playPcmArrayOnce(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(samples.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            scope.launch {
                val durationMs = (samples.size * 1000L / sampleRate) + 140L
                delay(durationMs)
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SFX playback error", e)
        }
    }

    private fun measureClipDurationSeconds(file: File): Float {
        if (!file.exists() || file.length() <= 44L) return 0f
        if (file.extension.equals("wav", ignoreCase = true)) {
            runCatching {
                file.inputStream().use { ins ->
                    val header = ByteArray(44)
                    if (ins.read(header) == 44) {
                        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        val byteRate = bb.getInt(28)
                        val dataSize = (file.length() - 44L).coerceAtLeast(0L)
                        if (byteRate > 0) {
                            return dataSize.toFloat() / byteRate.toFloat()
                        }
                    }
                }
            }
        }
        return runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            ms / 1000f
        }.getOrDefault(0f)
    }

    companion object {
        const val SFX_SAMPLE_RATE_HZ = SFX_SAMPLE_RATE

        /**
         * Synthesizes warm, seamless-loop 44.1kHz background music stems tailored per genre/mood.
         */
        fun synthesizeBackgroundMusicLoopPcm(track: BackgroundMusicTrack): ShortArray {
            if (track == BackgroundMusicTrack.NONE) return ShortArray(0)
            val sr = SFX_SAMPLE_RATE
            val loopSeconds = 8.0f
            val totalSamples = (sr * loopSeconds).toInt()
            val out = ShortArray(totalSamples)

            when (track) {
                BackgroundMusicTrack.NONE -> Unit

                BackgroundMusicTrack.CORP_PULSE -> {
                    // Warm upbeat corporate pluck + smooth sub bass (C -> G -> Am -> F)
                    val chords = arrayOf(
                        doubleArrayOf(261.63, 329.63, 392.00),
                        doubleArrayOf(196.00, 293.66, 392.00),
                        doubleArrayOf(220.00, 261.63, 329.63),
                        doubleArrayOf(174.61, 261.63, 349.23)
                    )
                    val basses = doubleArrayOf(130.81, 98.00, 110.00, 87.31)
                    val arp = intArrayOf(0, 1, 2, 1, 0, 2, 1, 2)
                    val stepSec = 0.25f
                    for (i in 0 until totalSamples) {
                        val t = i.toFloat() / sr
                        val barIdx = (t / 2.0f).toInt().coerceIn(0, 3)
                        val barT = t % 2.0f
                        val stepIdx = (t / stepSec).toInt()
                        val stepT = t % stepSec

                        val bassEnv = (1.0 - exp(-barT * 35.0)) * exp(-barT * 1.6)
                        val bass = sin(2.0 * PI * basses[barIdx] * t) * bassEnv * 0.42

                        val chord = chords[barIdx]
                        val noteFreq = chord[arp[stepIdx % arp.size]]
                        val pluckEnv = (1.0 - exp(-stepT * 220.0)) * exp(-stepT * 11.0)
                        val pluck = (sin(2.0 * PI * noteFreq * stepT) * 0.75 +
                            sin(2.0 * PI * noteFreq * 2.0 * stepT) * 0.22) * pluckEnv * 0.45

                        val pad = chord.sumOf { sin(2.0 * PI * it * 0.5 * t) } / 3.0 * 0.16
                        val mix = bass + pluck + pad
                        out[i] = (mix * 15500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                BackgroundMusicTrack.RETAIL_GROOVE -> {
                    // Bright upbeat retail bounce (D -> A -> Bm -> G)
                    val chords = arrayOf(
                        doubleArrayOf(293.66, 369.99, 440.00),
                        doubleArrayOf(220.00, 277.18, 329.63),
                        doubleArrayOf(246.94, 293.66, 369.99),
                        doubleArrayOf(196.00, 246.94, 293.66)
                    )
                    val stepSec = 0.25f
                    for (i in 0 until totalSamples) {
                        val t = i.toFloat() / sr
                        val barIdx = (t / 2.0f).toInt().coerceIn(0, 3)
                        val beatT = t % 0.5f
                        val stepT = t % stepSec
                        val stepIdx = (t / stepSec).toInt()

                        val kickEnv = exp(-beatT * 26.0)
                        val kick = sin(2.0 * PI * (48.0 + 72.0 * exp(-beatT * 42.0)) * beatT) * kickEnv * 0.40

                        val chord = chords[barIdx]
                        val stabEnv = (1.0 - exp(-stepT * 180.0)) * exp(-stepT * 14.0)
                        val note = chord[stepIdx % 3] * (if (stepIdx % 4 == 3) 2.0 else 1.0)
                        val melody = sin(2.0 * PI * note * stepT) * stabEnv * 0.38

                        val chordBed = chord.sumOf { sin(2.0 * PI * it * 0.5 * t) } / 3.0 * 0.18
                        val mix = kick + melody + chordBed
                        out[i] = (mix * 15000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                BackgroundMusicTrack.ARCADE_SURGE -> {
                    // Energetic synth pulse for gaming & esports (Em -> C -> D -> Bm)
                    val roots = doubleArrayOf(164.81, 130.81, 146.83, 123.47)
                    val ratios = doubleArrayOf(1.0, 1.5, 2.0, 1.5, 1.2, 2.0, 1.5, 2.4)
                    val stepSec = 0.125f
                    for (i in 0 until totalSamples) {
                        val t = i.toFloat() / sr
                        val barIdx = (t / 2.0f).toInt().coerceIn(0, 3)
                        val root = roots[barIdx]
                        val stepIdx = (t / stepSec).toInt()
                        val stepT = t % stepSec
                        val beatT = t % 0.5f

                        val arpFreq = root * 2.0 * ratios[stepIdx % ratios.size]
                        val arpEnv = (1.0 - exp(-stepT * 280.0)) * exp(-stepT * 16.0)
                        val arpTone = (sin(2.0 * PI * arpFreq * stepT) * 0.65 +
                            sin(2.0 * PI * arpFreq * 2.0 * stepT) * 0.25) * arpEnv * 0.42

                        val subKick = sin(2.0 * PI * (45.0 + 85.0 * exp(-beatT * 38.0)) * beatT) * exp(-beatT * 18.0) * 0.50
                        val bassPulse = sin(2.0 * PI * (root * 0.5) * t) * 0.26

                        val mix = arpTone + subKick + bassPulse
                        out[i] = (mix * 15000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                BackgroundMusicTrack.EPIC_HORIZON -> {
                    // Driving cinematic launch progression (Am -> F -> C -> G)
                    val chords = arrayOf(
                        doubleArrayOf(220.00, 261.63, 329.63),
                        doubleArrayOf(174.61, 220.00, 261.63),
                        doubleArrayOf(261.63, 329.63, 392.00),
                        doubleArrayOf(196.00, 246.94, 392.00)
                    )
                    for (i in 0 until totalSamples) {
                        val t = i.toFloat() / sr
                        val barIdx = (t / 2.0f).toInt().coerceIn(0, 3)
                        val beatT = t % 0.5f
                        val pulse8 = t % 0.25f

                        val chord = chords[barIdx]
                        val warmStrings = chord.sumOf {
                            sin(2.0 * PI * it * t) * 0.75 + sin(2.0 * PI * it * 0.5 * t) * 0.35
                        } / 3.0 * (0.28 + 0.14 * sin(2.0 * PI * 2.0 * pulse8))

                        val tomHit = sin(2.0 * PI * (55.0 + 50.0 * exp(-beatT * 24.0)) * beatT) *
                            exp(-beatT * 12.0) * 0.44
                        val mix = warmStrings + tomHit
                        out[i] = (mix * 15500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                BackgroundMusicTrack.DOC_THMANYAH -> {
                    // Felt piano & warm documentary ambient pad (Dm -> Bb -> F -> C)
                    val chords = arrayOf(
                        doubleArrayOf(293.66, 349.23, 440.00),
                        doubleArrayOf(233.08, 293.66, 349.23),
                        doubleArrayOf(261.63, 349.23, 440.00),
                        doubleArrayOf(261.63, 329.63, 392.00)
                    )
                    val noteStep = 0.5f
                    for (i in 0 until totalSamples) {
                        val t = i.toFloat() / sr
                        val barIdx = (t / 2.0f).toInt().coerceIn(0, 3)
                        val stepIdx = (t / noteStep).toInt()
                        val stepT = t % noteStep
                        val chord = chords[barIdx]

                        // Felt piano gentle arpeggiated notes
                        val pianoNote = chord[stepIdx % 3]
                        val pianoEnv = (1.0 - exp(-stepT * 90.0)) * exp(-stepT * 2.8)
                        val feltPiano = (sin(2.0 * PI * pianoNote * stepT) * 0.85 +
                            sin(2.0 * PI * pianoNote * 2.0 * stepT) * 0.12) * pianoEnv * 0.50

                        val warmPad = chord.sumOf { sin(2.0 * PI * it * 0.5 * t) } / 3.0 * 0.25
                        val mix = feltPiano + warmPad
                        out[i] = (mix * 15500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }

                BackgroundMusicTrack.COSMOS_DRIFT -> {
                    // Ethereal space & science shimmer pad (Fm9 -> DbMaj7 -> Ab -> Eb)
                    val chords = arrayOf(
                        doubleArrayOf(174.61, 261.63, 311.13, 392.00),
                        doubleArrayOf(138.59, 207.65, 261.63, 329.63),
                        doubleArrayOf(207.65, 261.63, 311.13, 415.30),
                        doubleArrayOf(155.56, 233.08, 311.13, 392.00)
                    )
                    for (i in 0 until totalSamples) {
                        val t = i.toFloat() / sr
                        val barIdx = (t / 2.0f).toInt().coerceIn(0, 3)
                        val barT = t % 2.0f
                        val chord = chords[barIdx]

                        val shimmer = sin(2.0 * PI * 0.25 * t) * 0.12 + 0.88
                        val pad = chord.sumOf { f ->
                            sin(2.0 * PI * f * t) * 0.6 + sin(2.0 * PI * f * 2.0 * t) * 0.14
                        } / chord.size * shimmer * 0.45

                        val subDrone = sin(2.0 * PI * (chord[0] * 0.5) * t) * 0.25
                        val starChimeEnv = (1.0 - exp(-barT * 30.0)) * exp(-barT * 2.1)
                        val starChime = sin(2.0 * PI * chord[3] * 2.0 * barT) * starChimeEnv * 0.22

                        val mix = pad + subDrone + starChime
                        out[i] = (mix * 15500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
            }

            // Apply subtle 30ms cross-fade at ends of the loop to guarantee zero loop click
            val edgeSamples = minOf((sr * 0.03f).toInt(), totalSamples / 10)
            for (i in 0 until edgeSamples) {
                val g = i.toFloat() / edgeSamples.toFloat()
                out[i] = (out[i] * g).toInt().toShort()
                val tailIdx = totalSamples - 1 - i
                out[tailIdx] = (out[tailIdx] * g).toInt().toShort()
            }
            return out
        }

        /**
         * Synthesizes warm, realistic 44.1kHz acoustic studio sound effects tailored to scene content,
         * across all 3 categories (Commercial, Energetic, Calm).
         */
        fun synthesizeStudioSfxPcm(effect: SceneSoundEffect): ShortArray {
            val sr = SFX_SAMPLE_RATE
            return when (effect) {
                SceneSoundEffect.NONE -> ShortArray(0)

                SceneSoundEffect.HOSPITALITY_CLINK -> {
                    val dur = 0.42f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val partials = doubleArrayOf(1174.66, 1760.0, 2489.02, 3135.96)
                    val amps = doubleArrayOf(0.45, 0.30, 0.16, 0.09)
                    val decays = doubleArrayOf(11.0, 16.0, 24.0, 32.0)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        val attack = (1f - exp(-t * 320f))
                        var sample = 0.0
                        for (p in partials.indices) {
                            sample += amps[p] * sin(2.0 * PI * partials[p] * t) * exp(-t * decays[p])
                        }
                        if (t > 0.085f) {
                            val t2 = t - 0.085f
                            sample += 0.35 * sin(2.0 * PI * 1480.0 * t2) * exp(-t2 * 18.0)
                        }
                        out[i] = (sample * attack * 19000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.TECH_UI_LOCK -> {
                    val dur = 0.22f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        val tap1 = sin(2.0 * PI * 520.0 * t) * exp(-t * 48.0) * 0.6
                        val sub1 = sin(2.0 * PI * 180.0 * t) * exp(-t * 35.0) * 0.4
                        val tap2 = if (t > 0.065f) {
                            val t2 = t - 0.065f
                            (sin(2.0 * PI * 780.0 * t2) * 0.65 + sin(2.0 * PI * 1170.0 * t2) * 0.25) *
                                exp(-t2 * 26.0)
                        } else 0.0
                        val s = tap1 + sub1 + tap2
                        out[i] = (s * 18500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.LUXURY_SHIMMER -> {
                    val dur = 0.72f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val notes = doubleArrayOf(293.66, 440.0, 554.37, 659.25, 880.0)
                    val delays = floatArrayOf(0f, 0.04f, 0.08f, 0.12f, 0.16f)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        var sum = 0.0
                        for (n in notes.indices) {
                            val dt = t - delays[n]
                            if (dt > 0f) {
                                val env = (1.0 - exp(-dt * 60.0)) * exp(-dt * 4.2)
                                sum += (sin(2.0 * PI * notes[n] * dt) * 0.8 +
                                    sin(2.0 * PI * notes[n] * 2.0 * dt) * 0.2) * env
                            }
                        }
                        out[i] = ((sum / notes.size) * 26000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.COMMERCE_CASH_DING -> {
                    val dur = 0.46f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        val note1 = (sin(2.0 * PI * 987.77 * t) + 0.3 * sin(2.0 * PI * 1975.53 * t)) *
                            (1.0 - exp(-t * 250.0)) * exp(-t * 16.0)
                        val note2 = if (t > 0.095f) {
                            val t2 = t - 0.095f
                            (sin(2.0 * PI * 1318.51 * t2) + 0.25 * sin(2.0 * PI * 2637.02 * t2)) *
                                (1.0 - exp(-t2 * 250.0)) * exp(-t2 * 7.5)
                        } else 0.0
                        out[i] = ((note1 * 0.5 + note2 * 0.7) * 16500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.LAUNCH_RISER_HIT -> {
                    val dur = 0.42f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val rng = Random(108)
                    var lp1 = 0f
                    var lp2 = 0f
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        val norm = t / dur
                        val whooshEnv = sin(norm.coerceIn(0f, 1f) * PI).pow(1.6).toFloat()
                        val noise = rng.nextFloat() * 2f - 1f
                        val cutoffAlpha = 0.03f + 0.14f * sin(norm * PI).toFloat()
                        lp1 += cutoffAlpha * (noise - lp1)
                        lp2 += cutoffAlpha * (lp1 - lp2)
                        val sub = if (t >= 0.14f) {
                            val th = t - 0.14f
                            val hitFreq = 48.0 + 92.0 * exp(-th * 30.0)
                            sin(2.0 * PI * hitFreq * th) * (1.0 - exp(-th * 160.0)) * exp(-th * 10.5)
                        } else 0.0
                        val mix = lp2 * whooshEnv * 0.55 + sub * 0.85
                        out[i] = (mix * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.GAME_POWER_UP -> {
                    // Energetic gaming synth sweep + punchy triumph octave
                    val dur = 0.44f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val notes = doubleArrayOf(392.00, 493.88, 587.33, 783.99, 987.77)
                    val starts = floatArrayOf(0f, 0.055f, 0.11f, 0.165f, 0.22f)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        var sum = 0.0
                        for (idx in notes.indices) {
                            val dt = t - starts[idx]
                            if (dt > 0f) {
                                val env = (1.0 - exp(-dt * 240.0)) * exp(-dt * 12.0)
                                sum += (sin(2.0 * PI * notes[idx] * dt) * 0.72 +
                                    sin(2.0 * PI * notes[idx] * 2.0 * dt) * 0.22) * env
                            }
                        }
                        out[i] = ((sum / 2.1) * 18500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.TIME_TICK_PULSE -> {
                    val dur = 0.30f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        val tick1 = (sin(2.0 * PI * 1400.0 * t) * 0.5 + sin(2.0 * PI * 420.0 * t) * 0.5) *
                            exp(-t * 65.0)
                        val tick2 = if (t > 0.14f) {
                            val t2 = t - 0.14f
                            (sin(2.0 * PI * 1650.0 * t2) * 0.55 + sin(2.0 * PI * 480.0 * t2) * 0.45) *
                                exp(-t2 * 60.0)
                        } else 0.0
                        out[i] = ((tick1 + tick2) * 19500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.DOC_SOFT_SWELL -> {
                    // Calm editorial air breath + warm felt fifth
                    val dur = 0.64f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val rng = Random(42)
                    var lp = 0f
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        val norm = t / dur
                        val swell = sin(norm.coerceIn(0f, 1f) * PI).pow(1.4).toFloat()
                        val air = rng.nextFloat() * 2f - 1f
                        lp += 0.045f * (air - lp)
                        val chord = (sin(2.0 * PI * 220.0 * t) * 0.6 + sin(2.0 * PI * 329.63 * t) * 0.4) *
                            (1.0 - exp(-t * 18.0)) * exp(-t * 3.2)
                        val mix = lp * swell * 0.35 + chord * 0.65
                        out[i] = (mix * 17500.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.COSMIC_CHIME -> {
                    // Ethereal space resonance chime
                    val dur = 0.78f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val partials = doubleArrayOf(349.23, 523.25, 698.46, 1046.50)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        var sum = 0.0
                        for (p in partials.indices) {
                            val dt = t - p * 0.055f
                            if (dt > 0f) {
                                val env = (1.0 - exp(-dt * 42.0)) * exp(-dt * 3.5)
                                sum += sin(2.0 * PI * partials[p] * dt) * env
                            }
                        }
                        val sub = sin(2.0 * PI * 87.31 * t) * (1.0 - exp(-t * 12.0)) * exp(-t * 3.0) * 0.4
                        out[i] = ((sum / 3.0 + sub) * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }

                SceneSoundEffect.CELEBRATION_CHORD -> {
                    val dur = 0.56f
                    val count = (sr * dur).toInt()
                    val out = ShortArray(count)
                    val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
                    val starts = floatArrayOf(0f, 0.065f, 0.13f, 0.195f)
                    for (i in 0 until count) {
                        val t = i.toFloat() / sr
                        var sum = 0.0
                        for (idx in freqs.indices) {
                            val dt = t - starts[idx]
                            if (dt > 0f) {
                                val env = (1.0 - exp(-dt * 180.0)) * exp(-dt * 7.5)
                                sum += (sin(2.0 * PI * freqs[idx] * dt) * 0.78 +
                                    sin(2.0 * PI * freqs[idx] * 2.0 * dt) * 0.18) * env
                            }
                        }
                        out[i] = ((sum / 2.2) * 19000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    out
                }
            }
        }
    }

    private fun wrapPcmAsWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return ByteArrayOutputStream(44 + pcmData.size).apply {
            write(header)
            write(pcmData)
        }.toByteArray()
    }

    private fun parseSampleRate(mimeType: String): Int? {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun clipKey(sceneId: String, voice: SaudiVoiceGender, script: String): String {
        return "${sceneId}_${voice.id}_${script.trim().hashCode()}"
    }

    fun release() {
        duckMonitorJob?.cancel()
        stopBackgroundMusic()
        stopVoiceover()
        runCatching { deviceTtsEngine?.shutdown() }
        deviceTtsEngine = null
        deviceTtsReady = false
        scope.cancel()
    }
}
