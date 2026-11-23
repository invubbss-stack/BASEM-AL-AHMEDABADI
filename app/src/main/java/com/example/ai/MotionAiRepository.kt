package com.example.ai

import android.util.Log
import com.example.audio.GeneratedAudioClip
import com.example.model.AspectRatioOption
import com.example.model.BackgroundMusicTrack
import com.example.model.ColorHexUtils
import com.example.model.ContentSfxMatcher
import com.example.model.CustomColorSchemeData
import com.example.model.MotionAnimationType
import com.example.model.MotionFontStyle
import com.example.model.MotionProject
import com.example.model.MotionScene
import com.example.model.MotionTopicGenre
import com.example.model.SaudiVoiceGender
import com.example.model.SceneSoundEffect
import com.example.model.SceneSymbol
import com.example.model.SceneTransitionType
import com.example.model.SceneVisualEffect
import com.example.model.SmartColorPalette
import com.example.model.VisualMotif
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TAG = "MotionAiRepository"
private const val PRIMARY_MODEL = "gemini-4-neon"
private const val FALLBACK_MODEL = "gemini-3.8-flash"
private const val LITE_FALLBACK_MODEL = "gemini-3.5-flash-lite"
private const val TTS_MODEL = "gemini-3.1-flash-tts-preview"
private const val TTS_QUOTA_COOLDOWN_MS = 15L * 60L * 1000L

class TtsQuotaExceededException(message: String) : Exception(message)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val appliedChangesBadge: String? = null
)

data class AiChatEditResult(
    val assistantReplyAr: String,
    val updatedProject: MotionProject
)

private data class MotionResearchBrief(
    val summaryText: String,
    val citations: List<String>
)

interface MotionAiRepository {
    suspend fun generateMotionProject(
        idea: String,
        aspectRatio: AspectRatioOption,
        preferredGenre: MotionTopicGenre,
        creativeFreedomMode: Boolean,
        manualPalette: SmartColorPalette,
        manualFont: MotionFontStyle,
        manualMusic: BackgroundMusicTrack,
        musicVolume: Float,
        autoDuckingEnabled: Boolean,
        voiceGender: SaudiVoiceGender,
        voiceoverEnabled: Boolean,
        soundEffectsEnabled: Boolean
    ): Result<MotionProject>

    suspend fun chatEditProject(
        currentProject: MotionProject,
        chatHistory: List<AiChatMessage>,
        userInstruction: String
    ): Result<AiChatEditResult>

    suspend fun improveAdCopy(rawIdea: String): Result<String>

    suspend fun synthesizeSaudiVoiceover(
        script: String,
        gender: SaudiVoiceGender
    ): Result<GeneratedAudioClip>
}

class FirebaseMotionAiRepository : MotionAiRepository {

    private val customPaletteSchema = Schema.obj(
        properties = mapOf(
            "nameAr" to Schema.string(description = "Arabic artistic name for the bespoke color palette"),
            "moodAr" to Schema.string(description = "2-4 Arabic words describing the visual mood"),
            "backgroundHex" to Schema.string(description = "Deep rich dark canvas hex color like #070B19 or #100C18"),
            "surfaceHex" to Schema.string(description = "Elevated dark gradient hex color slightly brighter than backgroundHex"),
            "primaryHex" to Schema.string(description = "Vibrant high-contrast accent hex color"),
            "secondaryHex" to Schema.string(description = "Harmonious secondary accent hex color"),
            "textHex" to Schema.string(description = "Crisp light text hex color like #F8FAFC or #FFFBEB")
        )
    )

    private val sceneItemSchema = Schema.obj(
        properties = mapOf(
            "badge" to Schema.string(
                description = "Short 1-3 word Arabic tag or badge matching the subject"
            ),
            "headline" to Schema.string(
                description = "Punchy Arabic motion graphics headline, max 6 words"
            ),
            "subheadline" to Schema.string(
                description = "Supporting concise Arabic phrase, max 12 words"
            ),
            "voiceoverScript" to Schema.string(
                description = "Natural Saudi Gulf dialect spoken line for this scene (8-16 words)"
            ),
            "durationSeconds" to Schema.double(
                description = "Scene display duration in seconds, between 3.5 and 6.5"
            ),
            "animationType" to Schema.enumeration(
                values = MotionAnimationType.entries.map { it.id },
                description = "Visual animation style for the scene"
            ),
            "visualMotif" to Schema.enumeration(
                values = VisualMotif.entries.map { it.id },
                description = "Procedural background geometric motif"
            ),
            "sceneSymbol" to Schema.enumeration(
                values = SceneSymbol.entries.map { it.id },
                description = "Topic-appropriate symbolic icon (e.g. PLANET for space, GAMEPAD/TROPHY for gaming, FILM/BOOK for documentary/story, SHOPPING/GIFT for ads)"
            ),
            "soundEffect" to Schema.enumeration(
                values = SceneSoundEffect.entries.map { it.id },
                description = "Categorized studio sound effect matched strictly to scene topic & genre"
            ),
            "transitionToNext" to Schema.enumeration(
                values = SceneTransitionType.entries.filter { it != SceneTransitionType.NONE }.map { it.id },
                description = "Smooth cinematic transition linking this scene to the next scene"
            ),
            "visualEffect" to Schema.enumeration(
                values = SceneVisualEffect.entries.map { it.id },
                description = "Visual FX layer activated per scene type (CYBER_GLOW_PARTICLES or NEON_GLOW for gaming/tech, FLOATING_PARTICLES for space/celebration, CINEMATIC_BLUR for documentary/story, AUTO)"
            )
        )
    )

    private val projectResponseSchema = Schema.obj(
        properties = mapOf(
            "projectTitle" to Schema.string(
                description = "Short catchy Arabic title for the motion graphics video"
            ),
            "detectedGenre" to Schema.enumeration(
                values = MotionTopicGenre.entries.filter { it != MotionTopicGenre.AUTO }.map { it.id },
                description = "Best matching topic genre for this video"
            ),
            "fontStyle" to Schema.enumeration(
                values = MotionFontStyle.entries.map { it.id },
                description = "Arabic typeface matching the subject (THMANYAH for documentary/editorial, EL_MESSIRI for luxury/story, CHANGA for gaming/ads, READEX_PRO/CAIRO/ALEXANDRIA/TAJAWAL)"
            ),
            "backgroundMusicTrack" to Schema.enumeration(
                values = BackgroundMusicTrack.entries.map { it.id },
                description = "Background music stem matched to video mood (CORP_PULSE/RETAIL_GROOVE for commercial, ARCADE_SURGE/EPIC_HORIZON for energetic/gaming, DOC_THMANYAH/COSMOS_DRIFT for calm/documentary/space)"
            ),
            "customPalette" to customPaletteSchema,
            "creativeDirectionAr" to Schema.string(
                description = "Concise Arabic art-direction summary explaining color psychology, pro references, typography, and audio pacing"
            ),
            "referenceSources" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 high-quality motion design studio/channel references matched to the topic"
            ),
            "scenes" to Schema.array(
                items = sceneItemSchema,
                description = "4 to 6 sequential Arabic motion graphics scenes tailored organically to the subject"
            )
        )
    )

    private val chatEditSchema = Schema.obj(
        properties = mapOf(
            "assistantReplyAr" to Schema.string(
                description = "Friendly, clear Arabic confirmation explaining the exact edits applied to the video"
            ),
            "projectTitle" to Schema.string(description = "Updated Arabic project title"),
            "aspectRatio" to Schema.enumeration(
                values = AspectRatioOption.entries.map { it.id },
                description = "Video aspect ratio (9:16 or 16:9)"
            ),
            "fontStyle" to Schema.enumeration(
                values = MotionFontStyle.entries.map { it.id },
                description = "Arabic font style"
            ),
            "detectedGenre" to Schema.enumeration(
                values = MotionTopicGenre.entries.map { it.id },
                description = "Topic genre"
            ),
            "voiceGender" to Schema.enumeration(
                values = SaudiVoiceGender.entries.map { it.id },
                description = "Narrator voice ID"
            ),
            "backgroundMusicTrack" to Schema.enumeration(
                values = BackgroundMusicTrack.entries.map { it.id },
                description = "Background music track"
            ),
            "backgroundMusicVolume" to Schema.double(
                description = "Background music volume 0.0 to 1.0"
            ),
            "autoDuckingEnabled" to Schema.boolean(
                description = "Whether background music automatically ducks while voiceover speaks"
            ),
            "customPalette" to customPaletteSchema,
            "creativeDirectionAr" to Schema.string(
                description = "Updated creative art-direction note"
            ),
            "scenes" to Schema.array(
                items = sceneItemSchema,
                description = "Complete updated list of motion scenes preserving untouched scenes"
            )
        )
    )

    private val directorSystemInstruction = content {
        text(
            """
            أنت مخرج موشن جرافيك ومصمم هوية بصرية عالمي المستوى تتقن اللغة العربية التحريرية واللهجة السعودية البيضاء المعاصرة.
            مهمتك: لا تستخدم قوالب مكررة أو ألواناً ثابتة أبداً! أبحِر من خيالك الإبداعي واستلهم من أرقى استوديوهات ومراجع الموشن جرافيك العالمية والعربية حسب موضوع الفيديو:
            - الإعلانات التجارية والمنتجات -> مراجع: Buck Studio, Giant Ant, Apple Keynote Reels
            - القصص والسرد الملهم -> مراجع: Ordinary Folk, Kurzgesagt Narrative Arcs
            - الوثائقي والتحقيق والاستقصاء -> مراجع: منصة ثمانية (Thmanyah), Vox Explainer, Johnny Harris
            - الألعاب والرياضة الإلكترونية -> مراجع: Riot Games VFX, Esports HUD Kinetic Motion
            - الفضاء والعلوم والمجرات -> مراجع: NASA Visuals, MelodySheep, Kurzgesagt Cosmos
            - التقنية والتطبيقات -> مراجع: Linear, Stripe, Vercel Launch Motion

            القواعد الإبداعية الإلزامية:
            1. اختر لوحة ألوان مخصصة (customPalette) بأكواد Hex دقيقة تعبّر عن نفسية الموضوع تماماً (خلفية داكنة غنية، لون سطحي متدرج، لون أساسي مشرق عالي التباين، ولون ثانوي متناغم).
            2. اختر الخط العربي الأنسب للموضوع (THMANYAH للوثائقي والتحريري، EL_MESSIRI للقصص والفخامة، CHANGA للألعاب والإعلانات العريضة، READEX_PRO/CAIRO/ALEXANDRIA/TAJAWAL).
            3. اختر موسيقى خلفية (backgroundMusicTrack) والمؤثرات الصوتية (soundEffect) وفق التصنيف المناسب للموضوع (تجارية، حماسية، هادئة).
            4. اختر أيقونات المشاهد (sceneSymbol) وحركاتها (animationType) والانتقالات (transitionToNext) وخلفياتها (visualMotif) والتأثير البصري (visualEffect: توهج في الألعاب والتقنية، جزيئات في الفضاء، ضبابية سينمائية في الوثائقي والقصص) بتنوع إبداعي يخدم القصة.
            5. اكتب تعليقاً صوتياً (voiceoverScript) باللهجة السعودية الطبيعية المتناغمة مع نبرة الموضوع (حماسي للإعلانات والألعاب، رزين وملهم للوثائقي والفضاء، دافئ للقصص)، واضبط المدة بين 3.5 و6.5 ثوانٍ لكل مشهد.
            """.trimIndent()
        )
    }

    private val chatEditorSystemInstruction = content {
        text(
            """
            أنت المخرج المساعد التفاعلي في «ستوديو الموشن».
            يتحدث معك المستخدم عبر الدردشة أو الصوت ليطلب تعديلات على الفيديو الحالي (الألوان، الخطوط، نصوص المشاهد، إضافة/حذف مشاهد، تغيير الانتقالات، التأثيرات البصرية مثل التوهج أو الجزيئات أو الضبابية، تغيير الموسيقى الخلفية أو المؤثرات أو صوت المعلق أو المقاس).
            التزم بما يلي:
            1. نفّذ طلب المستخدم بدقة وبذكاء إخراجي في JSON المشروع الكامل مع الحفاظ على المشاهد التي لم يطلب تغييرها.
            2. لا تضف أبداً أي إشارة لظهور المتكلم داخل شاشة الفيديو (الصوت فقط).
            3. إذا طلب المستخدم ألواناً أو طابعاً معيناً (مثل فضاء، ألعاب، وثائقي، فخم، إعلاني) غيّر customPalette والخط والموسيقى والانتقالات والأيقونات بما يناسب طلبه بسلاسة.
            4. اكتب رداً عربياً ودوداً وواضحاً في `assistantReplyAr` يشرح التعديلات التي نفذتها.
            """.trimIndent()
        )
    }

    private val copywriterSystemInstruction = content {
        text(
            """
            أنت كاتب نصوص إعلانية وقصصية (Copywriter) محترف متخصص في الموشن جرافيك العربي.
            أعد صياغة فكرة المستخدم بأسلوب بصري جذاب ومباشر يناسب تحويله لمشاهد موشن جرافيك مبهرة (بدون أي مقدمات أو شروحات أو علامات تنصيص زائدة).
            """.trimIndent()
        )
    }

    private fun buildResearchModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            tools = listOf(Tool.googleSearch())
        )

    private fun buildDirectorModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.85f
                responseMimeType = "application/json"
                responseSchema = projectResponseSchema
            },
            systemInstruction = directorSystemInstruction
        )

    private fun buildChatEditorModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.7f
                responseMimeType = "application/json"
                responseSchema = chatEditSchema
            },
            systemInstruction = chatEditorSystemInstruction
        )

    private fun buildCopywriterModel(modelName: String): GenerativeModel =
        Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.8f
            },
            systemInstruction = copywriterSystemInstruction
        )

    private val liteResearchModel by lazy { buildResearchModel(LITE_FALLBACK_MODEL) }
    private val fallbackResearchModel by lazy { buildResearchModel(FALLBACK_MODEL) }

    private val primaryDirectorModel by lazy { buildDirectorModel(PRIMARY_MODEL) }
    private val fallbackDirectorModel by lazy { buildDirectorModel(FALLBACK_MODEL) }
    private val liteDirectorModel by lazy { buildDirectorModel(LITE_FALLBACK_MODEL) }

    private val primaryChatEditorModel by lazy { buildChatEditorModel(PRIMARY_MODEL) }
    private val fallbackChatEditorModel by lazy { buildChatEditorModel(FALLBACK_MODEL) }
    private val liteChatEditorModel by lazy { buildChatEditorModel(LITE_FALLBACK_MODEL) }

    private val primaryCopywriterModel by lazy { buildCopywriterModel(PRIMARY_MODEL) }
    private val fallbackCopywriterModel by lazy { buildCopywriterModel(FALLBACK_MODEL) }
    private val liteCopywriterModel by lazy { buildCopywriterModel(LITE_FALLBACK_MODEL) }

    private val researchBriefCache = mutableMapOf<String, MotionResearchBrief>()

    @Volatile
    private var ttsQuotaCooldownUntilMs: Long = 0L

    /**
     * Searches high-quality professional motion graphics references via Google Search Grounding
     * using the high-volume flash-lite tier (preserving primary Director model quota) with caching.
     */
    private suspend fun researchProMotionReferences(
        idea: String,
        preferredGenre: MotionTopicGenre
    ): MotionResearchBrief {
        val cacheKey = "${preferredGenre.id}_${idea.trim().take(72).lowercase()}"
        researchBriefCache[cacheKey]?.let { return it }

        val searchPrompt = """
            ابحث بإيجاز عن أفضل المراجع العالمية والعربية في صناعة الموشن جرافيك (استوديوهات، أساليب ألوان، خط عربي مناسب، إيقاع انتقالات، وتصميم صوتي) لهذا الموضوع:
            الموضوع: "$idea"
            التصنيف المفضل: ${preferredGenre.labelAr} (${preferredGenre.referenceGuideEn})
            أعطني خلاصة إخراجية عملية في 4 نقاط تشمل: لوحة الألوان المقترحة، الخط، الانتقالات، والهوية الصوتية، مع ذكر أسماء مراجع محترفة عالية الجودة.
        """.trimIndent()

        val fetched = withTimeoutOrNull(14_000L) {
            runCatching {
                val response = try {
                    liteResearchModel.generateContent(searchPrompt)
                } catch (e: CancellationException) {
                    throw e
                } catch (err: Exception) {
                    if (!isFallbackEligibleError(err)) throw err
                    fallbackResearchModel.generateContent(searchPrompt)
                }
                val chunks = response.candidates.firstOrNull()
                    ?.groundingMetadata
                    ?.groundingChunks
                    ?.mapNotNull { it.web }
                    .orEmpty()
                    .distinctBy { it.domain ?: it.title ?: it.uri }
                    .take(4)

                val citations = chunks.mapNotNull { web ->
                    val title = web.title?.takeIf { it.isNotBlank() }
                    val domain = web.domain?.takeIf { it.isNotBlank() }
                    when {
                        title != null && domain != null -> "$title ($domain)"
                        title != null -> title
                        domain != null -> domain
                        else -> null
                    }
                }
                MotionResearchBrief(
                    summaryText = response.text.orEmpty().take(1200),
                    citations = citations
                )
            }.getOrNull()
        }

        val resolved = fetched ?: MotionResearchBrief(
            summaryText = preferredGenre.referenceGuideEn,
            citations = listOf(preferredGenre.proStudiosHintAr)
        )
        if (fetched != null) {
            researchBriefCache[cacheKey] = resolved
        }
        return resolved
    }

    override suspend fun generateMotionProject(
        idea: String,
        aspectRatio: AspectRatioOption,
        preferredGenre: MotionTopicGenre,
        creativeFreedomMode: Boolean,
        manualPalette: SmartColorPalette,
        manualFont: MotionFontStyle,
        manualMusic: BackgroundMusicTrack,
        musicVolume: Float,
        autoDuckingEnabled: Boolean,
        voiceGender: SaudiVoiceGender,
        voiceoverEnabled: Boolean,
        soundEffectsEnabled: Boolean
    ): Result<MotionProject> {
        return try {
            val brief = researchProMotionReferences(idea, preferredGenre)

            val creativeConstraintText = if (creativeFreedomMode) {
                """
                وضع الإبحار الإبداعي الحر مفعّل:
                - ابتكر لوحة ألوان جديدة تماماً (customPalette) مستوحاة من موضوع الفيديو وأبحاث المراجع (لا تتقيد بقوالب ثابتة).
                - اختر بنفسك الخط العربي الأنسب من القائمة والموسيقى الخلفية والأيقونات والانتقالات.
                """.trimIndent()
            } else {
                """
                تفضيلات المستخدم المختارة يدوياً:
                - لوحة الألوان: ${manualPalette.nameAr} (${manualPalette.moodAr})
                - الخط المطلوب: ${manualFont.id}
                - الموسيقى الخلفية: ${manualMusic.id}
                """.trimIndent()
            }

            val userPrompt = """
                فكرة الفيديو:
                $idea

                التصنيف المطلوب: ${preferredGenre.labelAr} (${preferredGenre.id})
                مقاس الفيديو: ${aspectRatio.id} (${aspectRatio.sublabelAr})
                المعلق الصوتي (صوت فقط بدون ظهور على الشاشة): ${voiceGender.labelAr}
                $creativeConstraintText

                مراجع وأبحاث المخرج المحترف للموضوع:
                ${brief.summaryText.ifBlank { preferredGenre.referenceGuideEn }}
                المصادر المستخلصة: ${brief.citations.joinToString(" | ").ifBlank { preferredGenre.proStudiosHintAr }}

                صمّم الآن مشروع موشن جرافيك متكامل، مترابط المشاهد، مبتكر بصرياً وصوتياً بدون تكرار القوالب.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                executeWithTransientFallback(
                    primary = primaryDirectorModel,
                    fallback = fallbackDirectorModel,
                    liteFallback = liteDirectorModel,
                    prompt = userPrompt
                )
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("لم يُرجع النموذج أي بيانات JSON."))

            val root = JSONObject(rawJson)
            val projectTitle = root.optString("projectTitle").ifBlank { "مشروع موشن مبتكر" }
            val detectedGenre = MotionTopicGenre.fromId(
                root.optString("detectedGenre").ifBlank {
                    if (preferredGenre != MotionTopicGenre.AUTO) preferredGenre.id else MotionTopicGenre.COMMERCIAL_AD.id
                }
            )
            val chosenFont = if (creativeFreedomMode) {
                MotionFontStyle.fromId(root.optString("fontStyle"))
            } else {
                manualFont
            }
            val chosenMusic = if (creativeFreedomMode) {
                val aiMusic = BackgroundMusicTrack.fromId(root.optString("backgroundMusicTrack"))
                if (aiMusic == BackgroundMusicTrack.NONE) BackgroundMusicTrack.defaultForMood(detectedGenre.defaultMood) else aiMusic
            } else {
                manualMusic
            }

            val customPaletteData = if (creativeFreedomMode) {
                parseCustomPalette(root.optJSONObject("customPalette"))
            } else {
                null
            }

            val creativeDirectionAr = root.optString("creativeDirectionAr")
            val refArray = root.optJSONArray("referenceSources")
            val modelRefs = buildList {
                if (refArray != null) {
                    for (i in 0 until refArray.length()) {
                        val s = refArray.optString(i).trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
            }
            val mergedRefs = (brief.citations + modelRefs)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(5)

            val scenesArray = root.optJSONArray("scenes")
                ?: return Result.failure(IllegalStateException("بيانات المشاهد مفقودة في الاستجابة."))

            val effectivePaletteId = if (customPaletteData != null) "ai_custom" else manualPalette.id
            val scenes = parseScenesArray(
                scenesArray = scenesArray,
                paletteId = effectivePaletteId,
                genre = detectedGenre,
                preserveIdsFrom = emptyList()
            )

            if (scenes.isEmpty()) {
                return Result.failure(IllegalStateException("لم يتم توليد أي مشاهد صالحة."))
            }

            Result.success(
                MotionProject(
                    projectTitle = projectTitle,
                    promptIdea = idea,
                    aspectRatio = aspectRatio.id,
                    paletteId = effectivePaletteId,
                    customPalette = customPaletteData,
                    fontStyle = chosenFont.id,
                    topicGenre = detectedGenre.id,
                    scenes = scenes,
                    voiceoverEnabled = voiceoverEnabled,
                    voiceGender = voiceGender.id,
                    soundEffectsEnabled = soundEffectsEnabled,
                    backgroundMusicTrack = chosenMusic.id,
                    backgroundMusicVolume = musicVolume.coerceIn(0f, 1f),
                    autoDuckingEnabled = autoDuckingEnabled,
                    creativeDirectionAr = creativeDirectionAr,
                    referenceSources = mergedRefs
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Motion generation timed out", e)
            Result.failure(Exception("انتهت مهلة الاتصال بعد 60 ثانية. يرجى المحاولة مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate motion scenes", e)
            Result.failure(Exception(formatUserFacingAiError(e, "تعذر توليد مشاهد الموشن جرافيك.")))
        }
    }

    override suspend fun chatEditProject(
        currentProject: MotionProject,
        chatHistory: List<AiChatMessage>,
        userInstruction: String
    ): Result<AiChatEditResult> {
        return try {
            val recentHistory = chatHistory.takeLast(6).joinToString("\n") { msg ->
                val role = if (msg.isUser) "المستخدم" else "المخرج الذكي"
                "$role: ${msg.text}"
            }

            val paletteHexSummary = currentProject.palette.let { p ->
                "bg=#${Integer.toHexString(p.backgroundInt)}, primary=#${Integer.toHexString(p.primaryInt)}, secondary=#${Integer.toHexString(p.secondaryInt)}"
            }

            val prompt = """
                حالة المشروع الحالية (JSON):
                ${currentProject.toPrettyJson()}
                ملخص الألوان الحالية: $paletteHexSummary

                سجل المحادثة السابق:
                ${recentHistory.ifBlank { "(بداية المحادثة)" }}

                طلب التعديل الجديد من المستخدم:
                "$userInstruction"

                نفّذ التعديل المطلوب على المشروع بالكامل (سواء الألوان أو الخط أو الموسيقى أو المشاهد أو النصوص أو الانتقالات أو المؤثرات أو الأيقونات أو المعلق الصوتي)، وأرجع المشروع المحدّث بصيغة JSON مع ردك في assistantReplyAr.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                executeWithTransientFallback(
                    primary = primaryChatEditorModel,
                    fallback = fallbackChatEditorModel,
                    liteFallback = liteChatEditorModel,
                    prompt = prompt
                )
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("لم يُرجع المساعد الذكي استجابة صالحة."))

            val root = JSONObject(rawJson)
            val replyAr = root.optString("assistantReplyAr")
                .ifBlank { "تم تنفيذ التعديلات المطلوبة على الفيديو وتحديث المعاينة بنجاح." }
            val updatedTitle = root.optString("projectTitle").ifBlank { currentProject.projectTitle }
            val updatedRatio = AspectRatioOption.fromId(
                root.optString("aspectRatio").ifBlank { currentProject.aspectRatio }
            )
            val updatedFont = MotionFontStyle.fromId(
                root.optString("fontStyle").ifBlank { currentProject.fontStyle }
            )
            val updatedGenre = MotionTopicGenre.fromId(
                root.optString("detectedGenre").ifBlank { currentProject.topicGenre }
            )
            val updatedVoice = SaudiVoiceGender.fromId(
                root.optString("voiceGender").ifBlank { currentProject.voiceGender }
            )
            val updatedMusic = BackgroundMusicTrack.fromId(
                root.optString("backgroundMusicTrack").ifBlank { currentProject.backgroundMusicTrack }
            )
            val updatedMusicVol = root.optDouble(
                "backgroundMusicVolume",
                currentProject.backgroundMusicVolume.toDouble()
            ).toFloat().coerceIn(0f, 1f)
            val updatedAutoDuck = root.optBoolean("autoDuckingEnabled", currentProject.autoDuckingEnabled)
            val updatedCustomPalette = parseCustomPalette(root.optJSONObject("customPalette"))
                ?: currentProject.customPalette
            val updatedDirection = root.optString("creativeDirectionAr")
                .ifBlank { currentProject.creativeDirectionAr }

            val scenesArray = root.optJSONArray("scenes")
            val updatedScenes = if (scenesArray != null && scenesArray.length() > 0) {
                parseScenesArray(
                    scenesArray = scenesArray,
                    paletteId = if (updatedCustomPalette != null) "ai_custom" else currentProject.paletteId,
                    genre = updatedGenre,
                    preserveIdsFrom = currentProject.scenes
                )
            } else {
                currentProject.scenes
            }

            val updatedProject = currentProject.copy(
                projectTitle = updatedTitle,
                aspectRatio = updatedRatio.id,
                paletteId = if (updatedCustomPalette != null) "ai_custom" else currentProject.paletteId,
                customPalette = updatedCustomPalette,
                fontStyle = updatedFont.id,
                topicGenre = updatedGenre.id,
                voiceGender = updatedVoice.id,
                backgroundMusicTrack = updatedMusic.id,
                backgroundMusicVolume = updatedMusicVol,
                autoDuckingEnabled = updatedAutoDuck,
                creativeDirectionAr = updatedDirection,
                scenes = updatedScenes
            )

            Result.success(
                AiChatEditResult(
                    assistantReplyAr = replyAr,
                    updatedProject = updatedProject
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat edit timed out", e)
            Result.failure(Exception("انتهت مهلة تطبيق التعديل. يرجى المحاولة مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat edit failed", e)
            Result.failure(Exception(formatUserFacingAiError(e, "تعذر تنفيذ طلب التعديل عبر الذكاء الاصطناعي.")))
        }
    }

    private fun parseCustomPalette(obj: JSONObject?): CustomColorSchemeData? {
        if (obj == null) return null
        val bgHex = obj.optString("backgroundHex")
        val primaryHex = obj.optString("primaryHex")
        if (bgHex.isBlank() || primaryHex.isBlank()) return null
        return ColorHexUtils.ensureReadablePalette(
            nameAr = obj.optString("nameAr").ifBlank { "ألوان مبتكرة للمشروع" },
            moodAr = obj.optString("moodAr").ifBlank { "مصممة حسب الموضوع" },
            bgHex = bgHex,
            surfaceHex = obj.optString("surfaceHex").ifBlank { bgHex },
            primaryHex = primaryHex,
            secondaryHex = obj.optString("secondaryHex").ifBlank { primaryHex },
            textHex = obj.optString("textHex").ifBlank { "#F8FAFC" }
        )
    }

    private fun parseScenesArray(
        scenesArray: JSONArray,
        paletteId: String,
        genre: MotionTopicGenre,
        preserveIdsFrom: List<MotionScene>
    ): List<MotionScene> {
        return buildList {
            for (i in 0 until scenesArray.length()) {
                val obj = scenesArray.getJSONObject(i)
                val duration = obj.optDouble("durationSeconds", 4.0)
                    .toFloat()
                    .coerceIn(1.5f, 9.0f)
                val badge = obj.optString("badge").ifBlank { "مشهد ${i + 1}" }
                val headline = obj.optString("headline").ifBlank { "عنوان المشهد" }
                val subheadline = obj.optString("subheadline")
                val voiceScript = obj.optString("voiceoverScript")
                val anim = MotionAnimationType.fromId(obj.optString("animationType"))
                val motif = VisualMotif.fromId(obj.optString("visualMotif"))
                val symbol = SceneSymbol.fromId(obj.optString("sceneSymbol"))
                val rawSfx = obj.optString("soundEffect")
                val transition = SceneTransitionType.fromId(
                    obj.optString("transitionToNext").ifBlank { SceneTransitionType.CROSS_FADE.id }
                )
                val vfx = SceneVisualEffect.fromId(
                    obj.optString("visualEffect").ifBlank { SceneVisualEffect.AUTO.id }
                )

                val deterministicMatch = ContentSfxMatcher.matchForTextAndSymbol(
                    text = "$badge $headline $subheadline $voiceScript",
                    symbol = symbol,
                    animation = anim,
                    paletteId = paletteId,
                    genre = genre
                )
                val parsedSfx = if (rawSfx.isBlank()) {
                    deterministicMatch
                } else {
                    val candidate = SceneSoundEffect.fromId(rawSfx)
                    if (candidate == SceneSoundEffect.NONE) deterministicMatch else candidate
                }

                val oldScene = preserveIdsFrom.getOrNull(i)
                add(
                    MotionScene(
                        id = oldScene?.id ?: UUID.randomUUID().toString(),
                        order = i + 1,
                        badge = badge,
                        headline = headline,
                        subheadline = subheadline,
                        durationSeconds = duration,
                        animationType = anim.id,
                        visualMotif = motif.id,
                        sceneSymbol = symbol.id,
                        soundEffect = parsedSfx.id,
                        voiceoverScript = voiceScript,
                        voiceoverEnabled = oldScene?.voiceoverEnabled ?: true,
                        transitionToNext = transition.id,
                        transitionDurationSeconds = oldScene?.transitionDurationSeconds ?: 0.55f,
                        visualEffect = vfx.id
                    )
                )
            }
        }
    }

    override suspend fun improveAdCopy(rawIdea: String): Result<String> {
        return try {
            val prompt = "حسّن هذه الفكرة لتصبح سيناريو أو نصاً بصرياً قوياً ومبتكراً لفيديو موشن جرافيك:\n$rawIdea"
            val response = withTimeout(45_000L) {
                executeWithTransientFallback(
                    primary = primaryCopywriterModel,
                    fallback = fallbackCopywriterModel,
                    liteFallback = liteCopywriterModel,
                    prompt = prompt
                )
            }
            val improved = response.text?.trim()
            if (improved.isNullOrBlank()) {
                Result.failure(IllegalStateException("لم يتم استلام نص محسّن من الذكاء الاصطناعي."))
            } else {
                Result.success(improved)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Improve copy timed out", e)
            Result.failure(Exception("انتهت مهلة تحسين النص. يرجى المحاولة مجدداً."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to improve ad copy", e)
            Result.failure(Exception(formatUserFacingAiError(e, "تعذر تحسين النص الإعلاني.")))
        }
    }

    @OptIn(PublicPreviewAPI::class)
    override suspend fun synthesizeSaudiVoiceover(
        script: String,
        gender: SaudiVoiceGender
    ): Result<GeneratedAudioClip> {
        val now = System.currentTimeMillis()
        if (ttsQuotaCooldownUntilMs > now) {
            return Result.failure(
                TtsQuotaExceededException("تم بلوغ الحصة المؤقتة لأصوات استوديو جوجل.")
            )
        }

        return try {
            val ttsModel = Firebase.ai.generativeModel(
                modelName = TTS_MODEL,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(
                        voice = Voice(gender.geminiVoiceName)
                    )
                }
            )

            val ttsPrompt = """
                Synthesize speech for the performance defined below.
                The profile, scene, and director's notes are performance direction ONLY. Do NOT speak them.
                Speak ONLY the exact Arabic words listed under the TRANSCRIPT section.

                # AUDIO PROFILE:
                - ${gender.directorStyleEn}
                - Language & Dialect: Natural contemporary Saudi Gulf Arabic (لهجة سعودية خليجية بيضاء طبيعية).

                # THE SCENE:
                - Studio-grade Arabic motion graphics narration, crisp vocal isolation, warm broadcast presence.

                # DIRECTOR'S NOTES:
                - Style: Authentic Saudi broadcast clarity, expressive, natural rhythm matching the topic tone.
                - Pace: Confident and natural cadence.

                #### TRANSCRIPT
                $script
            """.trimIndent()

            val response = withTimeout(45_000L) {
                try {
                    ttsModel.generateContent(ttsPrompt)
                } catch (e: CancellationException) {
                    throw e
                } catch (firstErr: Exception) {
                    if (!isTransientDemandError(firstErr) || isQuotaExceededError(firstErr)) {
                        throw firstErr
                    }
                    delay(900L)
                    ttsModel.generateContent(ttsPrompt)
                }
            }

            val audioPart = response.candidates.firstOrNull()
                ?.content?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) }

            if (audioPart != null) {
                ttsQuotaCooldownUntilMs = 0L
                Result.success(
                    GeneratedAudioClip(
                        bytes = audioPart.inlineData,
                        mimeType = audioPart.mimeType
                    )
                )
            } else {
                Result.failure(IllegalStateException("لم يُرجع نموذج استوديو جوجل ملفاً صوتياً."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google Studio TTS timed out", e)
            Result.failure(Exception("انتهت مهلة توليد صوت استوديو جوجل."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Studio TTS failed", e)
            if (isQuotaExceededError(e)) {
                ttsQuotaCooldownUntilMs = System.currentTimeMillis() + TTS_QUOTA_COOLDOWN_MS
                Result.failure(TtsQuotaExceededException(formatUserFacingAiError(e, "تم تجاوز الحصة المجانية للأصوات.")))
            } else {
                Result.failure(Exception(formatUserFacingAiError(e, "تعذر توليد الصوت من استوديو جوجل.")))
            }
        }
    }

    private suspend fun executeWithTransientFallback(
        primary: GenerativeModel,
        fallback: GenerativeModel,
        liteFallback: GenerativeModel,
        prompt: String
    ): GenerateContentResponse {
        try {
            return primary.generateContent(prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (primaryErr: Exception) {
            if (!isFallbackEligibleError(primaryErr)) {
                throw primaryErr
            }
            Log.w(TAG, "Primary model failed; switching to $FALLBACK_MODEL", primaryErr)
            delay(400L)
            try {
                return fallback.generateContent(prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (fallbackErr: Exception) {
                if (!isFallbackEligibleError(fallbackErr)) {
                    throw fallbackErr
                }
                Log.w(TAG, "Fallback model failed; switching to $LITE_FALLBACK_MODEL", fallbackErr)
                delay(500L)
                try {
                    return liteFallback.generateContent(prompt)
                } catch (e: CancellationException) {
                    throw e
                } catch (liteErr: Exception) {
                    if (!isTransientDemandError(liteErr) || isQuotaExceededError(liteErr)) {
                        throw liteErr
                    }
                    delay(1_100L)
                    return liteFallback.generateContent(prompt)
                }
            }
        }
    }

    private fun isFallbackEligibleError(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return isTransientDemandError(e) ||
            isQuotaExceededError(e) ||
            msg.contains("billing") ||
            msg.contains("not found") ||
            msg.contains("permission") ||
            msg.contains("404") ||
            msg.contains("403")
    }

    private fun isQuotaExceededError(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("quota") ||
            msg.contains("exceeded") ||
            msg.contains("rate-limit") ||
            msg.contains("rate limit") ||
            msg.contains("limit:") ||
            msg.contains("too many requests") ||
            msg.contains("429")
    }

    private fun isTransientDemandError(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("503") ||
            msg.contains("unavailable") ||
            msg.contains("try again") ||
            msg.contains("resource exhausted")
    }

    private fun formatUserFacingAiError(e: Exception, defaultMsg: String): String {
        return when {
            isQuotaExceededError(e) ->
                "تم تجاوز الحصة المجانية اليومية لنموذج Gemini. يمكنك المحاولة لاحقاً عند تجدد الحصة أو ترقية مشروع Firebase إلى باقة Blaze."
            isTransientDemandError(e) ->
                "تشهد خوادم استوديو جوجل ضغطاً مؤقتاً حالياً. يرجى المحاولة مرة أخرى بعد لحظات."
            else -> e.localizedMessage ?: defaultMsg
        }
    }
}
