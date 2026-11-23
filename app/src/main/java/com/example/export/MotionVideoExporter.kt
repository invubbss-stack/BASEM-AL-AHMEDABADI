package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.audio.MotionAudioEngine
import com.example.model.BackgroundMusicTrack
import com.example.model.MotionAnimationType
import com.example.model.MotionFontStyle
import com.example.model.MotionProject
import com.example.model.MotionScene
import com.example.model.SceneSoundEffect
import com.example.model.SceneSymbol
import com.example.model.SceneTransitionType
import com.example.model.SmartColorPalette
import com.example.model.TimelineEngine
import com.example.model.TimelineFrameState
import com.example.model.TimelineSegment
import com.example.model.VisualMotif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

private const val TAG = "MotionVideoExporter"

object MotionVideoExporter {

    private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val FPS = 24
    private const val VIDEO_BIT_RATE = 3_500_000
    private const val AUDIO_SAMPLE_RATE = 44100
    private const val AUDIO_BIT_RATE = 128_000
    private const val I_FRAME_INTERVAL = 1
    private const val TIMEOUT_US = 10_000L

    private data class EncoderChoice(
        val codecName: String?,
        val colorFormat: Int
    )

    private data class PendingSample(
        val isVideo: Boolean,
        val bytes: ByteArray,
        val size: Int,
        val ptsUs: Long,
        val flags: Int
    )

    suspend fun exportProjectToMp4(
        context: Context,
        project: MotionProject,
        voiceFilesBySceneId: Map<String, File> = emptyMap(),
        voiceDurationsBySceneId: Map<String, Float> = emptyMap(),
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        val ratio = project.aspectRatioEnum
        val width = (ratio.exportWidth / 16) * 16
        val height = (ratio.exportHeight / 16) * 16
        val palette = project.palette

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "motion_studio_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val thmanyahFont = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic) ?: Typeface.DEFAULT_BOLD
        val readexFont = ResourcesCompat.getFont(context, R.font.readex_pro) ?: thmanyahFont
        val changaFont = ResourcesCompat.getFont(context, R.font.changa) ?: thmanyahFont
        val elMessiriFont = ResourcesCompat.getFont(context, R.font.el_messiri) ?: thmanyahFont
        val alexandriaFont = ResourcesCompat.getFont(context, R.font.alexandria) ?: thmanyahFont
        val cairoFont = ResourcesCompat.getFont(context, R.font.cairo) ?: thmanyahFont
        val tajawalFont = ResourcesCompat.getFont(context, R.font.tajawal) ?: Typeface.DEFAULT

        val activeHeadlineFont = when (project.fontStyleEnum) {
            MotionFontStyle.THMANYAH -> thmanyahFont
            MotionFontStyle.READEX_PRO -> readexFont
            MotionFontStyle.CHANGA -> changaFont
            MotionFontStyle.EL_MESSIRI -> elMessiriFont
            MotionFontStyle.ALEXANDRIA -> alexandriaFont
            MotionFontStyle.CAIRO -> cairoFont
            MotionFontStyle.TAJAWAL -> tajawalFont
        }
        val activeBodyFont = when (project.fontStyleEnum) {
            MotionFontStyle.THMANYAH -> thmanyahFont
            MotionFontStyle.READEX_PRO -> readexFont
            MotionFontStyle.CHANGA -> readexFont
            MotionFontStyle.EL_MESSIRI -> tajawalFont
            MotionFontStyle.ALEXANDRIA -> alexandriaFont
            MotionFontStyle.CAIRO -> tajawalFont
            MotionFontStyle.TAJAWAL -> tajawalFont
        }

        // Build voice-safe merged segments so no shot or spoken word is ever lost/cut
        val segments = TimelineEngine.buildSegments(project, voiceDurationsBySceneId)
        val totalDurationSec = TimelineEngine.totalDuration(segments)
        val totalFrames = (totalDurationSec * FPS).toInt().coerceAtLeast(1)

        // Pre-mix full master audio track (Google Studio Voices + Content-Matched SFX)
        val masterAudioPcm = buildMasterAudioTrack(
            project = project,
            segments = segments,
            totalDurationSec = totalDurationSec,
            voiceFilesBySceneId = voiceFilesBySceneId
        )
        val hasAudioTrack = masterAudioPcm.isNotEmpty()

        var videoCodec: MediaCodec? = null
        var audioCodec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var videoCodecStarted = false
        var audioCodecStarted = false
        var muxerStarted = false

        val mainBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val mainCanvas = Canvas(mainBitmap)
        val outgoingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outgoingCanvas = Canvas(outgoingBitmap)
        val incomingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val incomingCanvas = Canvas(incomingBitmap)
        val argbPixels = IntArray(width * height)

        try {
            // 1. Setup Video Encoder
            val choices = selectAvcEncoders(width, height)
            var selectedChoice: EncoderChoice? = null
            var lastConfigError: Exception? = null

            for (candidate in choices) {
                var candidateCodec: MediaCodec? = null
                try {
                    val format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, candidate.colorFormat)
                        setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                        setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                    }
                    candidateCodec = if (candidate.codecName != null) {
                        MediaCodec.createByCodecName(candidate.codecName)
                    } else {
                        MediaCodec.createEncoderByType(VIDEO_MIME)
                    }
                    candidateCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    candidateCodec.start()
                    videoCodec = candidateCodec
                    videoCodecStarted = true
                    selectedChoice = candidate
                    break
                } catch (e: Exception) {
                    lastConfigError = e
                    Log.w(TAG, "Encoder candidate ${candidate.codecName} failed", e)
                    runCatching { candidateCodec?.release() }
                }
            }

            val activeVideoCodec = videoCodec ?: throw (lastConfigError ?: IllegalStateException("لا يوجد مرمّز فيديو متاح"))
            val activeChoice = selectedChoice ?: EncoderChoice(null, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            videoCodec = activeVideoCodec

            // 2. Setup Audio AAC Encoder (if master audio PCM exists)
            var includeAudio = false
            if (hasAudioTrack) {
                runCatching {
                    val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, AUDIO_SAMPLE_RATE, 1).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                    }
                    val ac = MediaCodec.createEncoderByType(AUDIO_MIME)
                    ac.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    ac.start()
                    audioCodec = ac
                    audioCodecStarted = true
                    includeAudio = true
                }.onFailure { err ->
                    Log.w(TAG, "AAC audio encoder unavailable; exporting video track only", err)
                    runCatching { audioCodec?.release() }
                    audioCodec = null
                    audioCodecStarted = false
                }
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoOutputFormat: MediaFormat? = null
            var audioOutputFormat: MediaFormat? = null
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            val pendingSamples = mutableListOf<PendingSample>()
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = !includeAudio
            var videoSampleWritten = false

            fun tryStartMuxer() {
                if (muxerStarted) return
                val vf = videoOutputFormat ?: return
                if (includeAudio && audioOutputFormat == null) return
                videoTrackIndex = muxer.addTrack(vf)
                if (includeAudio && audioOutputFormat != null) {
                    audioTrackIndex = muxer.addTrack(audioOutputFormat!!)
                }
                muxer.start()
                muxerStarted = true
                pendingSamples.sortBy { it.ptsUs }
                for (sample in pendingSamples) {
                    val trackIdx = if (sample.isVideo) videoTrackIndex else audioTrackIndex
                    if (trackIdx >= 0 && sample.size > 0) {
                        val info = MediaCodec.BufferInfo().apply {
                            set(0, sample.size, sample.ptsUs, sample.flags)
                        }
                        muxer.writeSampleData(trackIdx, ByteBuffer.wrap(sample.bytes, 0, sample.size), info)
                        if (sample.isVideo) videoSampleWritten = true
                    }
                }
                pendingSamples.clear()
            }

            fun emitSample(isVideo: Boolean, data: ByteBuffer, info: MediaCodec.BufferInfo) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0
                }
                if (info.size <= 0) return
                if (!muxerStarted) {
                    val copy = ByteArray(info.size)
                    data.position(info.offset)
                    data.get(copy, 0, info.size)
                    pendingSamples.add(
                        PendingSample(
                            isVideo = isVideo,
                            bytes = copy,
                            size = info.size,
                            ptsUs = info.presentationTimeUs,
                            flags = info.flags
                        )
                    )
                    tryStartMuxer()
                } else {
                    val trackIdx = if (isVideo) videoTrackIndex else audioTrackIndex
                    if (trackIdx >= 0) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIdx, data, info)
                        if (isVideo) videoSampleWritten = true
                    }
                }
            }

            fun drainVideo(waitForEos: Boolean) {
                var emptyLoops = 0
                while (!videoDone) {
                    val outIdx = activeVideoCodec.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!waitForEos) break
                            emptyLoops++
                            if (emptyLoops > 350) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (videoOutputFormat == null) {
                                videoOutputFormat = activeVideoCodec.outputFormat
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            emptyLoops = 0
                            val buf = activeVideoCodec.getOutputBuffer(outIdx)
                                ?: throw IllegalStateException("Video output buffer was null")
                            emitSample(true, buf, videoBufferInfo)
                            activeVideoCodec.releaseOutputBuffer(outIdx, false)
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                videoDone = true
                                break
                            }
                        }
                    }
                }
            }

            fun drainAudio(waitForEos: Boolean) {
                val ac = audioCodec ?: return
                var emptyLoops = 0
                while (!audioDone) {
                    val outIdx = ac.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_US)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!waitForEos) break
                            emptyLoops++
                            if (emptyLoops > 350) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioOutputFormat == null) {
                                audioOutputFormat = ac.outputFormat
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            emptyLoops = 0
                            val buf = ac.getOutputBuffer(outIdx)
                                ?: throw IllegalStateException("Audio output buffer was null")
                            emitSample(false, buf, audioBufferInfo)
                            ac.releaseOutputBuffer(outIdx, false)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                audioDone = true
                                break
                            }
                        }
                    }
                }
            }

            fun dequeueVideoInput(): Int {
                var attempts = 0
                while (attempts < 400) {
                    val idx = activeVideoCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) return idx
                    drainVideo(false)
                    drainAudio(false)
                    attempts++
                }
                throw IllegalStateException("انتهت مهلة ذاكرة ترميز الفيديو.")
            }

            // Audio feeding cursor
            var audioSampleCursor = 0
            val audioChunkSamples = 1024
            var audioEosSent = !includeAudio

            fun feedAudioUpTo(targetPtsUs: Long) {
                val ac = audioCodec ?: return
                if (!includeAudio || audioEosSent) return
                while (audioSampleCursor < masterAudioPcm.size) {
                    val chunkPtsUs = audioSampleCursor * 1_000_000L / AUDIO_SAMPLE_RATE
                    if (chunkPtsUs > targetPtsUs + 40_000L) break

                    val inIdx = ac.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx < 0) {
                        drainAudio(false)
                        break
                    }
                    val buf = ac.getInputBuffer(inIdx) ?: break
                    buf.clear()
                    buf.order(ByteOrder.LITTLE_ENDIAN)
                    val remaining = masterAudioPcm.size - audioSampleCursor
                    val count = min(audioChunkSamples, remaining).coerceAtMost(buf.remaining() / 2)
                    for (k in 0 until count) {
                        buf.putShort(masterAudioPcm[audioSampleCursor + k])
                    }
                    ac.queueInputBuffer(inIdx, 0, count * 2, chunkPtsUs, 0)
                    audioSampleCursor += count
                    drainAudio(false)
                }
            }

            val yuvBufferSize = width * height * 3 / 2

            for (frameIdx in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val timeSec = (frameIdx.toFloat() / FPS.toFloat()).coerceIn(0f, totalDurationSec)
                val frameState = TimelineEngine.resolveFrameAt(segments, timeSec)

                drawMergedTimelineFrame(
                    canvas = mainCanvas,
                    outgoingCanvas = outgoingCanvas,
                    outgoingBitmap = outgoingBitmap,
                    incomingCanvas = incomingCanvas,
                    incomingBitmap = incomingBitmap,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    frameState = frameState,
                    totalScenes = segments.size,
                    palette = palette,
                    headlineFont = activeHeadlineFont,
                    bodyFont = activeBodyFont
                )

                mainBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

                val ptsUs = frameIdx * 1_000_000L / FPS
                val inputIndex = dequeueVideoInput()
                val inputImage = runCatching { activeVideoCodec.getInputImage(inputIndex) }.getOrNull()

                if (inputImage != null) {
                    encodeArgbToImage(argbPixels, width, height, inputImage)
                    activeVideoCodec.queueInputBuffer(inputIndex, 0, yuvBufferSize, ptsUs, 0)
                } else {
                    val inputBuffer = activeVideoCodec.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("Video encoder input buffer was null")
                    val bytesWritten = encodeArgbToBuffer(
                        argbPixels,
                        width,
                        height,
                        inputBuffer,
                        activeChoice.colorFormat
                    )
                    activeVideoCodec.queueInputBuffer(inputIndex, 0, bytesWritten, ptsUs, 0)
                }

                feedAudioUpTo(ptsUs)
                drainVideo(false)
                drainAudio(false)

                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    onProgress((frameIdx.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.96f))
                }
            }

            // Flush any remaining audio samples before EOS
            if (includeAudio) {
                feedAudioUpTo(Long.MAX_VALUE / 2)
                val ac = audioCodec
                if (ac != null && !audioEosSent) {
                    var eosAttempts = 0
                    while (eosAttempts < 200) {
                        val idx = ac.dequeueInputBuffer(TIMEOUT_US)
                        if (idx >= 0) {
                            val eosPts = audioSampleCursor * 1_000_000L / AUDIO_SAMPLE_RATE
                            ac.queueInputBuffer(idx, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            audioEosSent = true
                            break
                        }
                        drainAudio(false)
                        eosAttempts++
                    }
                }
            }

            // Signal Video EOS
            val eosIndex = dequeueVideoInput()
            val eosPtsUs = totalFrames * 1_000_000L / FPS
            activeVideoCodec.queueInputBuffer(eosIndex, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)

            drainVideo(true)
            if (includeAudio) {
                drainAudio(true)
            }

            if (!muxerStarted || !videoSampleWritten || !outputFile.exists() || outputFile.length() == 0L) {
                throw IllegalStateException("لم يتم إنتاج مسار فيديو صالح.")
            }

            onProgress(1f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "MP4 export failed", e)
            if (outputFile.exists()) outputFile.delete()
            Result.failure(Exception("تعذر تصدير الفيديو: ${e.localizedMessage ?: "خطأ في الترميز"}"))
        } finally {
            if (videoCodecStarted) runCatching { videoCodec?.stop() }
            runCatching { videoCodec?.release() }
            if (audioCodecStarted) runCatching { audioCodec?.stop() }
            runCatching { audioCodec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            mainBitmap.recycle()
            outgoingBitmap.recycle()
            incomingBitmap.recycle()
        }
    }

    private fun buildMasterAudioTrack(
        project: MotionProject,
        segments: List<TimelineSegment>,
        totalDurationSec: Float,
        voiceFilesBySceneId: Map<String, File>
    ): ShortArray {
        val hasBgMusic = project.backgroundMusicEnum != BackgroundMusicTrack.NONE && project.backgroundMusicVolume > 0.01f
        if (!project.voiceoverEnabled && !project.soundEffectsEnabled && !hasBgMusic) return ShortArray(0)

        val totalSamples = (totalDurationSec * AUDIO_SAMPLE_RATE).toInt().coerceAtLeast(1)
        val mixFloat = FloatArray(totalSamples)
        val voicePresence = BooleanArray(totalSamples)
        var anyAudioMixed = false

        for (seg in segments) {
            val scene = seg.scene
            val segStartSample = (seg.startTime * AUDIO_SAMPLE_RATE).toInt().coerceIn(0, totalSamples - 1)

            // 1. Mix content-matched studio SFX at start of scene
            if (project.soundEffectsEnabled && scene.soundEffectEnum != SceneSoundEffect.NONE) {
                val sfxPcm = MotionAudioEngine.synthesizeStudioSfxPcm(scene.soundEffectEnum)
                if (sfxPcm.isNotEmpty()) {
                    anyAudioMixed = true
                    val maxMix = min(sfxPcm.size, totalSamples - segStartSample)
                    for (i in 0 until maxMix) {
                        mixFloat[segStartSample + i] += (sfxPcm[i] / 32768f) * 0.52f
                    }
                }
            }

            // 2. Mix complete Google AI Studio Saudi voiceover (resampled to 44.1kHz, zero cutoff)
            if (project.voiceoverEnabled && scene.voiceoverEnabled) {
                val voiceFile = voiceFilesBySceneId[scene.id]
                if (voiceFile != null && voiceFile.exists()) {
                    val decoded = loadResampledMonoPcm(voiceFile, AUDIO_SAMPLE_RATE)
                    if (decoded.isNotEmpty()) {
                        anyAudioMixed = true
                        val offsetSamples = (0.10f * AUDIO_SAMPLE_RATE).toInt()
                        val voiceStart = (segStartSample + offsetSamples).coerceAtMost(totalSamples - 1)
                        val maxVoiceSamples = min(decoded.size, totalSamples - voiceStart)
                        val fadeSamples = min((0.015f * AUDIO_SAMPLE_RATE).toInt(), maxVoiceSamples / 4)

                        for (i in 0 until maxVoiceSamples) {
                            var env = 1.0f
                            if (fadeSamples > 0) {
                                if (i < fadeSamples) env = i.toFloat() / fadeSamples
                                else if (i > maxVoiceSamples - fadeSamples) {
                                    env = (maxVoiceSamples - i).toFloat() / fadeSamples
                                }
                            }
                            mixFloat[voiceStart + i] += (decoded[i] / 32768f) * 0.98f * env
                            voicePresence[voiceStart + i] = true
                        }
                    }
                }
            }
        }

        // 3. Mix looping Background Music with real-time smoothed Speech Auto-Ducking
        if (hasBgMusic) {
            val musicLoop = MotionAudioEngine.synthesizeBackgroundMusicLoopPcm(project.backgroundMusicEnum)
            if (musicLoop.isNotEmpty()) {
                anyAudioMixed = true
                val baseVol = project.backgroundMusicVolume.coerceIn(0f, 1f) * 0.65f
                val duckFactor = 0.24f
                val fadeInSamples = min((0.40f * AUDIO_SAMPLE_RATE).toInt(), totalSamples / 6)
                val fadeOutSamples = min((0.75f * AUDIO_SAMPLE_RATE).toInt(), totalSamples / 4)
                var currentDuckGain = if (project.autoDuckingEnabled && voicePresence[0]) duckFactor else 1.0f

                for (i in 0 until totalSamples) {
                    val targetDuck = if (project.autoDuckingEnabled && voicePresence[i]) duckFactor else 1.0f
                    val alpha = if (targetDuck < currentDuckGain) 0.00045f else 0.00018f
                    currentDuckGain += (targetDuck - currentDuckGain) * alpha

                    var masterEnv = 1.0f
                    if (fadeInSamples > 0 && i < fadeInSamples) {
                        masterEnv = i.toFloat() / fadeInSamples.toFloat()
                    } else if (fadeOutSamples > 0 && i > totalSamples - fadeOutSamples) {
                        masterEnv = (totalSamples - i).toFloat() / fadeOutSamples.toFloat()
                    }

                    val loopSample = musicLoop[i % musicLoop.size] / 32768f
                    mixFloat[i] += loopSample * baseVol * currentDuckGain * masterEnv
                }
            }
        }

        if (!anyAudioMixed) return ShortArray(0)

        return ShortArray(totalSamples) { i ->
            val v = mixFloat[i]
            val limited = if (v > 0.9f || v < -0.9f) tanh(v.toDouble()).toFloat() else v
            (limited * 32000f).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun loadResampledMonoPcm(file: File, targetRate: Int): ShortArray {
        val rawPair: Pair<ShortArray, Int> = if (file.extension.equals("wav", ignoreCase = true)) {
            readWavPcm(file)
        } else {
            decodeCompressedAudioToPcm(file)
        }
        val (srcSamples, srcRate) = rawPair
        if (srcSamples.isEmpty() || srcRate <= 0) return ShortArray(0)
        if (srcRate == targetRate) return srcSamples

        val ratio = srcRate.toDouble() / targetRate.toDouble()
        val outLen = ((srcSamples.size.toDouble() / srcRate.toDouble()) * targetRate).toInt().coerceAtLeast(1)
        return ShortArray(outLen) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcSamples.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, srcSamples.lastIndex)
            val frac = (srcPos - idx0).toFloat()
            val interpolated = srcSamples[idx0] * (1f - frac) + srcSamples[idx1] * frac
            interpolated.toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun readWavPcm(file: File): Pair<ShortArray, Int> {
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return@runCatching ShortArray(0) to 24000
            val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val channels = header.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = header.getInt(24).coerceAtLeast(8000)
            val dataLen = bytes.size - 44
            val frameCount = dataLen / (2 * channels)
            val bb = ByteBuffer.wrap(bytes, 44, dataLen).order(ByteOrder.LITTLE_ENDIAN)
            val out = ShortArray(frameCount)
            for (i in 0 until frameCount) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += bb.short.toInt()
                }
                out[i] = (sum / channels).coerceIn(-32767, 32767).toShort()
            }
            out to sampleRate
        }.getOrDefault(ShortArray(0) to 24000)
    }

    private fun decodeCompressedAudioToPcm(file: File): Pair<ShortArray, Int> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    break
                }
            }
            if (audioTrackIdx < 0 || format == null) return ShortArray(0) to 24000
            extractor.selectTrack(audioTrackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return ShortArray(0) to 24000
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmBytes = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFmt = decoder.outputFormat
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                } else if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)!!
                    if (info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk, 0, info.size)
                        pcmBytes.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            val raw = pcmBytes.toByteArray()
            val frames = raw.size / (2 * channels.coerceAtLeast(1))
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val mono = ShortArray(frames) {
                var sum = 0
                for (c in 0 until channels.coerceAtLeast(1)) {
                    sum += bb.short.toInt()
                }
                (sum / channels.coerceAtLeast(1)).coerceIn(-32767, 32767).toShort()
            }
            mono to sampleRate
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode audio file ${file.name}", e)
            ShortArray(0) to 24000
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun drawMergedTimelineFrame(
        canvas: Canvas,
        outgoingCanvas: Canvas,
        outgoingBitmap: Bitmap,
        incomingCanvas: Canvas,
        incomingBitmap: Bitmap,
        width: Float,
        height: Float,
        frameState: TimelineFrameState,
        totalScenes: Int,
        palette: SmartColorPalette,
        headlineFont: Typeface,
        bodyFont: Typeface
    ) {
        val incoming = frameState.incomingScene
        val t = easeInOutCubic(frameState.transitionProgress.coerceIn(0f, 1f))
        val transType = frameState.activeTransition

        if (incoming == null || transType == SceneTransitionType.NONE || t <= 0.001f) {
            drawSceneContent(
                canvas = canvas,
                width = width,
                height = height,
                scene = frameState.primaryScene,
                sceneIndex = frameState.primaryIndex,
                sceneProgress = frameState.primaryProgress,
                palette = palette,
                headlineFont = headlineFont,
                bodyFont = bodyFont
            )
        } else {
            // Composite dual-scene transition seamlessly
            drawSceneContent(
                canvas = outgoingCanvas,
                width = width,
                height = height,
                scene = frameState.primaryScene,
                sceneIndex = frameState.primaryIndex,
                sceneProgress = frameState.primaryProgress,
                palette = palette,
                headlineFont = headlineFont,
                bodyFont = bodyFont
            )
            drawSceneContent(
                canvas = incomingCanvas,
                width = width,
                height = height,
                scene = incoming,
                sceneIndex = frameState.incomingIndex ?: (frameState.primaryIndex + 1),
                sceneProgress = frameState.incomingProgress,
                palette = palette,
                headlineFont = headlineFont,
                bodyFont = bodyFont
            )

            canvas.drawColor(palette.backgroundInt)
            val paintOut = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val paintIn = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            when (transType) {
                SceneTransitionType.NONE -> {
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, null)
                }
                SceneTransitionType.CROSS_FADE -> {
                    paintOut.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    paintIn.alpha = (t * 255).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                }
                SceneTransitionType.SLIDE_LEFT -> {
                    canvas.save()
                    canvas.translate(-t * width, 0f)
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    canvas.restore()

                    canvas.save()
                    canvas.translate((1f - t) * width, 0f)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                    canvas.restore()
                }
                SceneTransitionType.SLIDE_UP -> {
                    canvas.save()
                    canvas.translate(0f, -t * height)
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(0f, (1f - t) * height)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                    canvas.restore()
                }
                SceneTransitionType.ZOOM_IN -> {
                    val outScale = 1f + 0.35f * t
                    paintOut.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.scale(outScale, outScale, width / 2f, height / 2f)
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    canvas.restore()

                    val inScale = 0.72f + 0.28f * t
                    paintIn.alpha = (t * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.scale(inScale, inScale, width / 2f, height / 2f)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                    canvas.restore()
                }
                SceneTransitionType.ZOOM_OUT -> {
                    val outScale = 1f - 0.28f * t
                    paintOut.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.scale(outScale, outScale, width / 2f, height / 2f)
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    canvas.restore()

                    val inScale = 1.28f - 0.28f * t
                    paintIn.alpha = (t * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.scale(inScale, inScale, width / 2f, height / 2f)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                    canvas.restore()
                }
                SceneTransitionType.WIPE_REVEAL -> {
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    val wipeRight = width * t
                    canvas.save()
                    canvas.clipRect(0f, 0f, wipeRight, height)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                    canvas.restore()

                    val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            (wipeRight - 14f).coerceAtLeast(0f),
                            0f,
                            (wipeRight + 14f).coerceAtMost(width),
                            0f,
                            Color.TRANSPARENT,
                            palette.primaryInt,
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect((wipeRight - 14f).coerceAtLeast(0f), 0f, (wipeRight + 6f).coerceAtMost(width), height, edgePaint)
                }
                SceneTransitionType.ROTATE_MORPH -> {
                    val outScale = 1f - 0.16f * t
                    paintOut.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.rotate(14f * t, width / 2f, height / 2f)
                    canvas.scale(outScale, outScale, width / 2f, height / 2f)
                    canvas.drawBitmap(outgoingBitmap, 0f, 0f, paintOut)
                    canvas.restore()

                    val inScale = 0.84f + 0.16f * t
                    paintIn.alpha = (t * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.rotate(-14f * (1f - t), width / 2f, height / 2f)
                    canvas.scale(inScale, inScale, width / 2f, height / 2f)
                    canvas.drawBitmap(incomingBitmap, 0f, 0f, paintIn)
                    canvas.restore()
                }
            }
        }

        // Draw persistent editorial HUD progress bars on top so transitions glide beneath them
        drawPersistentProgressHud(
            canvas = canvas,
            width = width,
            height = height,
            sceneIndex = frameState.primaryIndex,
            totalScenes = totalScenes,
            sceneProgress = frameState.primaryProgress,
            globalProgress = frameState.globalProgress,
            palette = palette
        )
    }

    private fun drawPersistentProgressHud(
        canvas: Canvas,
        width: Float,
        height: Float,
        sceneIndex: Int,
        totalScenes: Int,
        sceneProgress: Float,
        globalProgress: Float,
        palette: SmartColorPalette
    ) {
        val minDim = min(width, height)
        val barTop = height * 0.045f
        val barMargin = width * 0.06f
        val gap = width * 0.015f
        val count = totalScenes.coerceAtLeast(1)
        val barW = (width - barMargin * 2 - gap * (count - 1)) / count
        val barH = minDim * 0.008f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(palette.textInt, 65)
        }
        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryInt
        }
        for (i in 0 until count) {
            val left = barMargin + i * (barW + gap)
            canvas.drawRoundRect(RectF(left, barTop, left + barW, barTop + barH), barH, barH, trackPaint)
            val fill = when {
                i < sceneIndex -> 1f
                i == sceneIndex -> sceneProgress.coerceIn(0f, 1f)
                else -> 0f
            }
            if (fill > 0f) {
                canvas.drawRoundRect(
                    RectF(left, barTop, left + barW * fill, barTop + barH),
                    barH,
                    barH,
                    activePaint
                )
            }
        }

        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryInt
        }
        canvas.drawRect(0f, height - 6f, width * globalProgress.coerceIn(0f, 1f), height, bottomPaint)
    }

    private fun drawSceneContent(
        canvas: Canvas,
        width: Float,
        height: Float,
        scene: MotionScene,
        sceneIndex: Int,
        sceneProgress: Float,
        palette: SmartColorPalette,
        headlineFont: Typeface,
        bodyFont: Typeface
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width,
                height,
                intArrayOf(palette.backgroundInt, palette.surfaceInt, palette.backgroundInt),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Ambient glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val cx = width * (0.3f + 0.4f * sin(sceneProgress * PI.toFloat()))
            val cy = height * 0.35f
            shader = RadialGradient(
                cx,
                cy,
                min(width, height) * 0.65f,
                withAlpha(palette.primaryInt, 70),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.5f, height * 0.4f, min(width, height) * 0.7f, glowPaint)

        drawMotif(canvas, width, height, scene.motifEnum, sceneProgress, palette)

        if (scene.animationEnum == MotionAnimationType.CINEMA_BARS) {
            val barSlide = (sceneProgress / 0.3f).coerceIn(0f, 1f)
            val barH = ((1f - barSlide) * 48f + 22f) * (height / 720f)
            val barPaint = Paint().apply { color = Color.argb(185, 0, 0, 0) }
            canvas.drawRect(0f, 0f, width, barH, barPaint)
            canvas.drawRect(0f, height - barH, width, height, barPaint)
        }

        // Motion curves (keep visible until cross-transition handles exit seamlessly!)
        val enterT = (sceneProgress / 0.34f).coerceIn(0f, 1f)
        val easedEnter = easeOutBack(enterT)
        val alphaFactor = enterT.coerceIn(0f, 1f)

        var translateX = 0f
        var translateY = 0f
        var scaleX = 1f
        var scaleY = 1f
        var rotationDeg = 0f

        when (scene.animationEnum) {
            MotionAnimationType.SLIDE_UP_SPRING -> translateY = (1f - easedEnter) * height * 0.08f
            MotionAnimationType.ZOOM_POP -> {
                val s = 0.72f + 0.28f * easedEnter
                scaleX = s; scaleY = s
            }
            MotionAnimationType.TYPEWRITER_REVEAL -> translateY = (1f - enterT) * 16f
            MotionAnimationType.KINETIC_SWIPE -> translateX = -(1f - easedEnter) * width * 0.18f
            MotionAnimationType.FLOAT_PULSE -> {
                translateY = sin(sceneProgress * PI.toFloat() * 2f) * 16f
                val s = 0.96f + 0.04f * sin(sceneProgress * PI.toFloat())
                scaleX = s; scaleY = s
            }
            MotionAnimationType.SPLIT_CARD -> {
                translateY = (1f - easedEnter) * -32f
                val s = 0.9f + 0.1f * easedEnter
                scaleX = s; scaleY = s
            }
            MotionAnimationType.BLUR_FOCUS -> {
                val s = 1.15f - 0.15f * enterT
                scaleX = s; scaleY = s
            }
            MotionAnimationType.FLIP_3D -> {
                scaleX = (0.25f + 0.75f * easedEnter).coerceAtLeast(0.15f)
                scaleY = 0.86f + 0.14f * easedEnter
            }
            MotionAnimationType.GLITCH_RGB -> {
                if (enterT < 0.9f) {
                    translateX = sin(sceneProgress * 95f) * 18f * (1f - enterT)
                }
            }
            MotionAnimationType.BOUNCE_DROP -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                val b = when {
                    enterT < 1f / d1 -> n1 * enterT * enterT
                    enterT < 2f / d1 -> { val t = enterT - 1.5f / d1; n1 * t * t + 0.75f }
                    enterT < 2.5f / d1 -> { val t = enterT - 2.25f / d1; n1 * t * t + 0.9375f }
                    else -> { val t = enterT - 2.625f / d1; n1 * t * t + 0.984375f }
                }
                translateY = -(1f - b) * height * 0.12f
            }
            MotionAnimationType.SPIN_ORBIT -> {
                rotationDeg = (1f - easedEnter) * -28f
                val s = 0.65f + 0.35f * easedEnter
                scaleX = s; scaleY = s
            }
            MotionAnimationType.CINEMA_BARS -> {
                val s = 0.90f + 0.10f * easedEnter
                scaleX = s; scaleY = s
            }
        }

        canvas.save()
        canvas.translate(width / 2f + translateX, height / 2f + translateY)
        if (rotationDeg != 0f) canvas.rotate(rotationDeg)
        canvas.scale(scaleX, scaleY)
        canvas.translate(-width / 2f, -height / 2f)

        val contentWidth = (width * 0.82f).toInt()
        val horizontalMargin = (width - contentWidth) / 2f
        val minDim = min(width, height)

        val hasSymbol = scene.symbolEnum != SceneSymbol.NONE
        val pillTop = if (hasSymbol) {
            val emblemSize = minDim * 0.10f
            val emblemTop = height * 0.24f
            drawSymbolEmblem(
                canvas = canvas,
                cx = width / 2f,
                cy = emblemTop + emblemSize / 2f,
                size = emblemSize,
                symbol = scene.symbolEnum,
                palette = palette,
                alphaFactor = alphaFactor
            )
            emblemTop + emblemSize + minDim * 0.025f
        } else {
            height * 0.30f
        }

        val badgeText = scene.badge.ifBlank { "مشهد ${sceneIndex + 1}" }
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = headlineFont
            textSize = minDim * 0.036f
            color = palette.backgroundInt
        }
        val badgeTextW = badgeTextPaint.measureText(badgeText)
        val pillW = badgeTextW + minDim * 0.08f
        val pillH = minDim * 0.07f
        val pillLeft = (width - pillW) / 2f

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                pillLeft,
                pillTop,
                pillLeft + pillW,
                pillTop + pillH,
                palette.primaryInt,
                palette.secondaryInt,
                Shader.TileMode.CLAMP
            )
            alpha = (alphaFactor * 255).toInt()
        }
        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH),
            pillH / 2f,
            pillH / 2f,
            pillPaint
        )
        badgeTextPaint.alpha = (alphaFactor * 255).toInt()
        val badgeTextY = pillTop + pillH / 2f - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(badgeText, (width - badgeTextW) / 2f, badgeTextY, badgeTextPaint)

        val headlineText = if (scene.animationEnum == MotionAnimationType.TYPEWRITER_REVEAL) {
            val count = ((sceneProgress / 0.45f).coerceIn(0f, 1f) * scene.headline.length)
                .toInt()
                .coerceAtLeast(1)
            scene.headline.take(count)
        } else {
            scene.headline
        }

        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(headlineFont, Typeface.BOLD)
            textSize = minDim * 0.074f
            color = palette.textInt
            alpha = (alphaFactor * 255).toInt()
        }
        val headlineLayout = StaticLayout.Builder
            .obtain(headlineText, 0, headlineText.length, headlinePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()

        val headlineTop = pillTop + pillH + minDim * 0.045f
        canvas.save()
        canvas.translate(horizontalMargin, headlineTop)
        headlineLayout.draw(canvas)
        canvas.restore()

        val accentFrac = (sceneProgress / 0.4f).coerceIn(0f, 1f)
        val accentMaxW = minDim * 0.16f
        val accentW = accentMaxW * accentFrac
        val accentH = minDim * 0.009f
        val accentTop = headlineTop + headlineLayout.height + minDim * 0.024f
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                (width - accentW) / 2f,
                accentTop,
                (width + accentW) / 2f,
                accentTop,
                palette.primaryInt,
                palette.secondaryInt,
                Shader.TileMode.CLAMP
            )
            alpha = (alphaFactor * 255).toInt()
        }
        canvas.drawRoundRect(
            RectF((width - accentW) / 2f, accentTop, (width + accentW) / 2f, accentTop + accentH),
            accentH / 2f,
            accentH / 2f,
            accentPaint
        )

        if (scene.subheadline.isNotBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bodyFont
                textSize = minDim * 0.042f
                color = withAlpha(palette.textInt, (alphaFactor * 215).toInt())
            }
            val subLayout = StaticLayout.Builder
                .obtain(scene.subheadline, 0, scene.subheadline.length, subPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.25f)
                .build()

            val subTop = accentTop + accentH + minDim * 0.024f
            canvas.save()
            canvas.translate(horizontalMargin, subTop)
            subLayout.draw(canvas)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun selectAvcEncoders(width: Int, height: Int): List<EncoderChoice> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val candidates = codecList.codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(VIDEO_MIME, ignoreCase = true) }
        }
        val sorted = candidates.sortedBy { info ->
            val name = info.name.lowercase()
            when {
                name.contains("c2.android") || name.contains("omx.google") -> 0
                else -> 1
            }
        }
        val preferredFormats = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )
        val choices = mutableListOf<EncoderChoice>()
        for (info in sorted) {
            val caps = runCatching { info.getCapabilitiesForType(VIDEO_MIME) }.getOrNull() ?: continue
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null && !videoCaps.isSizeSupported(width, height)) continue
            for (fmt in preferredFormats) {
                if (caps.colorFormats.contains(fmt)) {
                    choices.add(EncoderChoice(info.name, fmt))
                    break
                }
            }
        }
        if (choices.isEmpty()) {
            choices.add(EncoderChoice(null, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible))
        }
        return choices
    }

    private fun encodeArgbToImage(argb: IntArray, width: Int, height: Int, image: Image) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            val rowOffset = j * width
            val yRowPos = j * yRowStride
            val uvRow = j shr 1
            val uRowPos = uvRow * uRowStride
            val vRowPos = uvRow * vRowStride

            for (i in 0 until width) {
                val c = argb[rowOffset + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yBuf.put(yRowPos + i * yPixelStride, yVal.toByte())
                if ((j and 1) == 0 && (i and 1) == 0) {
                    val uvCol = i shr 1
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    uBuf.put(uRowPos + uvCol * uPixelStride, uVal.toByte())
                    vBuf.put(vRowPos + uvCol * vPixelStride, vVal.toByte())
                }
            }
        }
    }

    private fun encodeArgbToBuffer(
        argb: IntArray,
        width: Int,
        height: Int,
        buffer: ByteBuffer,
        colorFormat: Int
    ): Int {
        buffer.clear()
        val frameSize = width * height
        val isSemiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4
        var uvIndex = frameSize

        for (j in 0 until height) {
            val rowOffset = j * width
            for (i in 0 until width) {
                val c = argb[rowOffset + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                buffer.put(yIndex++, yVal.toByte())
                if ((j and 1) == 0 && (i and 1) == 0) {
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    if (isSemiPlanar) {
                        buffer.put(uvIndex++, uVal.toByte())
                        buffer.put(uvIndex++, vVal.toByte())
                    } else {
                        buffer.put(uIndex++, uVal.toByte())
                        buffer.put(vIndex++, vVal.toByte())
                    }
                }
            }
        }
        return frameSize * 3 / 2
    }

    private fun drawMotif(
        canvas: Canvas,
        width: Float,
        height: Float,
        motif: VisualMotif,
        progress: Float,
        palette: SmartColorPalette
    ) {
        val minDim = min(width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (motif) {
            VisualMotif.ORBIT_RINGS -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                for (i in 1..3) {
                    paint.color = withAlpha(if (i % 2 == 0) palette.secondaryInt else palette.primaryInt, 75)
                    val radius = minDim * (0.22f + i * 0.12f) + sin(progress * PI.toFloat() * 2f + i) * 12f
                    canvas.drawCircle(width * 0.5f, height * 0.48f, radius, paint)
                }
            }
            VisualMotif.KINETIC_STRIPES -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                val offset = progress * width * 0.4f
                for (i in -3..5) {
                    paint.color = withAlpha(palette.primaryInt, 45)
                    val x = i * (width * 0.18f) + offset
                    canvas.drawLine(x, 0f, x - height * 0.35f, height, paint)
                }
            }
            VisualMotif.PARTICLE_GLOW -> {
                paint.style = Paint.Style.FILL
                for (i in 0..14) {
                    val seed = i * 1.37f
                    val px = ((sin(seed * 3.1f) * 0.5f + 0.5f) * width + progress * 40f * cos(seed)) % width
                    val py = ((cos(seed * 2.3f) * 0.5f + 0.5f) * height - progress * 90f)
                        .let { if (it < 0) it + height else it }
                    paint.color = withAlpha(if (i % 2 == 0) palette.primaryInt else palette.secondaryInt, 110)
                    canvas.drawCircle(px, py, 6f + (i % 4) * 4f, paint)
                }
            }
            VisualMotif.GRID_PULSE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = withAlpha(palette.primaryInt, 45)
                val step = minDim * 0.12f
                val drift = (progress * step) % step
                var x = drift
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, paint)
                    x += step
                }
                var y = drift
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, paint)
                    y += step
                }
            }
            VisualMotif.WAVE_RIBBON -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                for (wave in 0..2) {
                    paint.color = withAlpha(if (wave == 1) palette.secondaryInt else palette.primaryInt, 90)
                    val baseY = height * (0.25f + wave * 0.25f)
                    val path = android.graphics.Path()
                    path.moveTo(0f, baseY)
                    var x = 0f
                    while (x <= width) {
                        val y = baseY + sin((x / width) * PI.toFloat() * 3f + progress * PI.toFloat() * 2f + wave) * 32f
                        path.lineTo(x, y)
                        x += 24f
                    }
                    canvas.drawPath(path, paint)
                }
            }
            VisualMotif.RADIAL_BURST -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                val cx = width * 0.5f
                val cy = height * 0.5f
                val rays = 16
                for (i in 0 until rays) {
                    val angle = (i.toFloat() / rays) * 2f * PI.toFloat() + progress * 0.8f
                    paint.color = withAlpha(if (i % 2 == 0) palette.primaryInt else palette.secondaryInt, 85)
                    val inner = minDim * 0.2f
                    val outer = minDim * (0.45f + 0.1f * sin(progress * PI.toFloat() * 2f + i))
                    canvas.drawLine(
                        cx + cos(angle) * inner,
                        cy + sin(angle) * inner,
                        cx + cos(angle) * outer,
                        cy + sin(angle) * outer,
                        paint
                    )
                }
            }
        }
    }

    private fun drawSymbolEmblem(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        symbol: SceneSymbol,
        palette: SmartColorPalette,
        alphaFactor: Float
    ) {
        val alphaInt = (alphaFactor * 255).toInt().coerceIn(0, 255)
        val half = size / 2f
        val rect = RectF(cx - half, cy - half, cx + half, cy + half)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(palette.primaryInt, (alphaFactor * 55).toInt())
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.045f
            color = withAlpha(palette.primaryInt, alphaInt)
        }
        canvas.drawRoundRect(rect, size * 0.28f, size * 0.28f, bgPaint)
        canvas.drawRoundRect(rect, size * 0.28f, size * 0.28f, borderPaint)

        val glyph = when (symbol) {
            SceneSymbol.NONE -> ""
            SceneSymbol.SPARKLES -> "✦"
            SceneSymbol.FLASH -> "⚡"
            SceneSymbol.ROCKET -> "▲"
            SceneSymbol.SHOPPING -> "◆"
            SceneSymbol.GIFT -> "❖"
            SceneSymbol.STAR -> "★"
            SceneSymbol.TIMER -> "◔"
            SceneSymbol.SHIELD -> "◈"
            SceneSymbol.TRENDING -> "↗"
            SceneSymbol.CAFE -> "☕"
            SceneSymbol.CELEBRATE -> "✧"
            SceneSymbol.PLANET -> "◉"
            SceneSymbol.GAMEPAD -> "◆"
            SceneSymbol.FILM -> "▣"
            SceneSymbol.BOOK -> "▤"
            SceneSymbol.CAMERA -> "◎"
            SceneSymbol.MUSIC -> "♪"
            SceneSymbol.BRAIN -> "◈"
            SceneSymbol.TROPHY -> "★"
            SceneSymbol.COMPASS -> "✧"
            SceneSymbol.ECO -> "✿"
            SceneSymbol.CODE -> "◇"
            SceneSymbol.MICROPHONE -> "◉"
        }
        if (glyph.isNotEmpty()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = size * 0.52f
                color = withAlpha(palette.primaryInt, alphaInt)
            }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(glyph, cx, textY, textPaint)
        }
    }

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
    }

    private fun easeInOutCubic(x: Float): Float {
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    }

    private fun withAlpha(argb: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(argb),
            Color.green(argb),
            Color.blue(argb)
        )
    }

    suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("تعذر فتح مسار الحفظ")
                Unit
            }
        }

    suspend fun readTextFromUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("تعذر قراءة الملف المحدد")
            }
        }

    suspend fun copyFileToUri(context: Context, source: File, destUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    source.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: error("تعذر حفظ ملف الفيديو")
                Unit
            }
        }

    fun shareJsonContent(context: Context, jsonText: String, title: String) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val jsonFile = File(exportDir, "motion_project.json")
        jsonFile.writeText(jsonText, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            jsonFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة مشروع الموشن جرافيك"))
    }

    fun shareVideoFile(context: Context, mp4File: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            mp4File
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة فيديو الموشن جرافيك"))
    }
}
