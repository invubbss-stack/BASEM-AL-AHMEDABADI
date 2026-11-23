package com.example.model

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class AspectRatioOption(
    val id: String,
    val labelAr: String,
    val sublabelAr: String,
    val widthRatio: Float,
    val heightRatio: Float,
    val exportWidth: Int,
    val exportHeight: Int
) {
    PORTRAIT_9_16(
        id = "9:16",
        labelAr = "9:16 طولي",
        sublabelAr = "ريلز • تيك توك • شورتس",
        widthRatio = 9f,
        heightRatio = 16f,
        exportWidth = 720,
        exportHeight = 1280
    ),
    LANDSCAPE_16_9(
        id = "16:9",
        labelAr = "16:9 عرضي",
        sublabelAr = "يوتيوب • عروض تقديمية",
        widthRatio = 16f,
        heightRatio = 9f,
        exportWidth = 1280,
        exportHeight = 720
    );

    companion object {
        fun fromId(id: String): AspectRatioOption =
            entries.firstOrNull { it.id == id } ?: PORTRAIT_9_16
    }
}

enum class AudioMoodCategory(
    val id: String,
    val labelAr: String,
    val descriptionAr: String
) {
    COMMERCIAL("COMMERCIAL", "تجارية", "للإعلانات، العروض، المتاجر، والضيافة"),
    ENERGETIC("ENERGETIC", "حماسية", "للألعاب، الرياضة، التقنية، والانطلاقات"),
    CALM("CALM", "هادئة", "للوثائقيات، القصص، الفضاء، والفخامة");

    companion object {
        fun fromId(id: String): AudioMoodCategory =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: COMMERCIAL
    }
}

enum class MotionTopicGenre(
    val id: String,
    val labelAr: String,
    val defaultMood: AudioMoodCategory,
    val referenceGuideEn: String,
    val proStudiosHintAr: String
) {
    AUTO(
        id = "AUTO",
        labelAr = "تلقائي حسب الموضوع",
        defaultMood = AudioMoodCategory.COMMERCIAL,
        referenceGuideEn = "Analyze topic intent first (Ad vs Story vs Documentary vs Gaming vs Space vs Tech) and adopt world-class motion design benchmarks.",
        proStudiosHintAr = "تحليل ذكي واختيار المراجع الأنسب للموضوع"
    ),
    COMMERCIAL_AD(
        id = "COMMERCIAL_AD",
        labelAr = "إعلان تجاري",
        defaultMood = AudioMoodCategory.COMMERCIAL,
        referenceGuideEn = "Apple Keynote crisp typography, Buck & Giant Ant snappy rhythmic cuts, bold product hooks, high-contrast retail accents.",
        proStudiosHintAr = "مراجع: Apple Ads • Buck Studio • Giant Ant"
    ),
    STORYTELLING(
        id = "STORYTELLING",
        labelAr = "قصص وسرد",
        defaultMood = AudioMoodCategory.CALM,
        referenceGuideEn = "Ordinary Folk & Kurzgesagt emotive storytelling arcs, warm editorial palettes, lyrical pacing, fluid morphing transitions.",
        proStudiosHintAr = "مراجع: Ordinary Folk • Kurzgesagt Narrative"
    ),
    DOCUMENTARY(
        id = "DOCUMENTARY",
        labelAr = "وثائقي واستقصاء",
        defaultMood = AudioMoodCategory.CALM,
        referenceGuideEn = "Thmanyah (ثمانية) & Vox Explainer editorial rigor, clean grid lines, journalistic IBM Plex/Readex typography, measured cross-fades & wipes.",
        proStudiosHintAr = "مراجع: منصة ثمانية • Vox • Johnny Harris"
    ),
    GAMING(
        id = "GAMING",
        labelAr = "ألعاب ورياضة إلكترونية",
        defaultMood = AudioMoodCategory.ENERGETIC,
        referenceGuideEn = "Riot Games & Overwatch esports HUD motion graphics, electric cyber-neon highlights, glitch/swipe kinetic energy, punchy SFX.",
        proStudiosHintAr = "مراجع: Riot Games VFX • Esports HUD Motion"
    ),
    SPACE_SCIENCE(
        id = "SPACE_SCIENCE",
        labelAr = "فضاء وعلوم",
        defaultMood = AudioMoodCategory.CALM,
        referenceGuideEn = "NASA Eyes, Kurzgesagt & MelodySheep cosmic motion design: deep orbital indigo/obsidian, aurora cyan & solar amber accents, awe-inspiring zoom transitions.",
        proStudiosHintAr = "مراجع: NASA Visuals • MelodySheep • Kurzgesagt"
    ),
    TECH_LAUNCH(
        id = "TECH_LAUNCH",
        labelAr = "تقنية وتطبيقات",
        defaultMood = AudioMoodCategory.ENERGETIC,
        referenceGuideEn = "Linear, Stripe & Vercel product launch reels: sleek dark obsidian, crisp emerald/violet glow, modular UI motion, spring physics.",
        proStudiosHintAr = "مراجع: Linear • Stripe • Vercel Product Reels"
    );

    companion object {
        fun fromId(id: String): MotionTopicGenre =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}

enum class BackgroundMusicTrack(
    val id: String,
    val labelAr: String,
    val category: AudioMoodCategory,
    val descriptionAr: String,
    val bpm: Int
) {
    NONE(
        id = "NONE",
        labelAr = "بدون موسيقى",
        category = AudioMoodCategory.CALM,
        descriptionAr = "تركيز تام على الصوت البشري والمؤثرات",
        bpm = 0
    ),
    CORP_PULSE(
        id = "CORP_PULSE",
        labelAr = "نبض تجاري عصري",
        category = AudioMoodCategory.COMMERCIAL,
        descriptionAr = "إيقاع ماريمبا ونبض دافئ لإعلانات المنتجات والتطبيقات",
        bpm = 112
    ),
    RETAIL_GROOVE(
        id = "RETAIL_GROOVE",
        labelAr = "إيقاع تسويقي مشرق",
        category = AudioMoodCategory.COMMERCIAL,
        descriptionAr = "نغمات إعلانية حيوية مثالية للعروض والمتاجر",
        bpm = 118
    ),
    ARCADE_SURGE(
        id = "ARCADE_SURGE",
        labelAr = "طاقة ألعاب وتقنية",
        category = AudioMoodCategory.ENERGETIC,
        descriptionAr = "أربيجيو إلكتروني سريع للألعاب والانطلاقات القوية",
        bpm = 128
    ),
    EPIC_HORIZON(
        id = "EPIC_HORIZON",
        labelAr = "زخم سينمائي حماسي",
        category = AudioMoodCategory.ENERGETIC,
        descriptionAr = "تصاعد موسيقي قوي لإعلانات التحدي والإنجاز",
        bpm = 120
    ),
    DOC_THMANYAH(
        id = "DOC_THMANYAH",
        labelAr = "أجواء وثائقية عميقة",
        category = AudioMoodCategory.CALM,
        descriptionAr = "بيانو دافئ ووتريات تحريرية للوثائقيات والسرد",
        bpm = 76
    ),
    COSMOS_DRIFT(
        id = "COSMOS_DRIFT",
        labelAr = "تأمل فضائي وقصصي",
        category = AudioMoodCategory.CALM,
        descriptionAr = "موجات أثيرية عذبة للفضاء والعلوم والقصص الملهمة",
        bpm = 68
    );

    companion object {
        fun fromId(id: String): BackgroundMusicTrack =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CORP_PULSE

        fun defaultForMood(mood: AudioMoodCategory): BackgroundMusicTrack = when (mood) {
            AudioMoodCategory.COMMERCIAL -> CORP_PULSE
            AudioMoodCategory.ENERGETIC -> ARCADE_SURGE
            AudioMoodCategory.CALM -> DOC_THMANYAH
        }
    }
}

enum class MotionFontStyle(
    val id: String,
    val labelAr: String,
    val sublabelAr: String
) {
    THMANYAH(
        id = "THMANYAH",
        labelAr = "خط ثمانية",
        sublabelAr = "تحريري هندسي للوثائقيات"
    ),
    READEX_PRO(
        id = "READEX_PRO",
        labelAr = "خط ريديكس برو",
        sublabelAr = "عصري نقي فائق الوضوح"
    ),
    CHANGA(
        id = "CHANGA",
        labelAr = "خط تشانجا",
        sublabelAr = "هندسي عريض للإعلانات والألعاب"
    ),
    EL_MESSIRI(
        id = "EL_MESSIRI",
        labelAr = "خط المسيري",
        sublabelAr = "أنيق للقصص والفخامة"
    ),
    ALEXANDRIA(
        id = "ALEXANDRIA",
        labelAr = "خط الإسكندرية",
        sublabelAr = "هندسي عريض حديث"
    ),
    CAIRO(
        id = "CAIRO",
        labelAr = "خط كايرو",
        sublabelAr = "عصري متوازن للتقنية"
    ),
    TAJAWAL(
        id = "TAJAWAL",
        labelAr = "خط تجوال",
        sublabelAr = "انسيابي ناعم للريلز"
    );

    companion object {
        fun fromId(id: String): MotionFontStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: THMANYAH
    }
}

enum class SceneTransitionType(
    val id: String,
    val labelAr: String,
    val shortLabelAr: String,
    val descriptionAr: String
) {
    NONE("NONE", "قطع مباشر", "قطع", "انتقال فوري حاد بدون دمج بصري"),
    CROSS_FADE("CROSS_FADE", "تلاشي ناعم", "تلاشي", "تداخل وتلاشي انسيابي بين اللقطتين"),
    SLIDE_LEFT("SLIDE_LEFT", "انزلاق أفقي", "انزلاق ◀", "دفع جانبي سلس يكشف المشهد التالي"),
    SLIDE_UP("SLIDE_UP", "انزلاق للأعلى", "انزلاق ▲", "صعود متدرج من الأسفل للقطة التالية"),
    ZOOM_IN("ZOOM_IN", "زوم داخلي", "زوم +", "تقريب سينمائي يدمج المشهد للداخل"),
    ZOOM_OUT("ZOOM_OUT", "زوم للخارج", "زوم -", "توسيع ديناميكي يكشف المشهد التالي"),
    WIPE_REVEAL("WIPE_REVEAL", "ستارة ضوئية", "ستارة", "مسحة أفقية بحد مضيء بين المشاهد"),
    ROTATE_MORPH("ROTATE_MORPH", "التفاف مرن", "التفاف", "دوران وتلاشي احترافي بين اللقطات");

    companion object {
        fun fromId(id: String): SceneTransitionType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CROSS_FADE
    }
}

enum class MotionAnimationType(
    val id: String,
    val labelAr: String,
    val hintAr: String
) {
    SLIDE_UP_SPRING("SLIDE_UP_SPRING", "انزلاق مرن", "دخول سلس من الأسفل مع ارتداد ناعم"),
    ZOOM_POP("ZOOM_POP", "تكبير سينمائي", "إبراز قوي بتأثير التكبير المتدرج"),
    TYPEWRITER_REVEAL("TYPEWRITER_REVEAL", "كشف متزامن", "ظهور متتابع للنص متزامن مع الإيقاع"),
    KINETIC_SWIPE("KINETIC_SWIPE", "مسحة حركية", "دخول جانبي سريع مناسب للإعلانات"),
    FLOAT_PULSE("FLOAT_PULSE", "طفو نابض", "حركة عائمة متناغمة مع نبض خفيف"),
    SPLIT_CARD("SPLIT_CARD", "انقسام إطاري", "انبثاق هندسي متعاكس للعنوان والوصف"),
    BLUR_FOCUS("BLUR_FOCUS", "تركيز تدريجي", "ظهور ضبابي يتضح تدريجياً بدقة عالية"),
    FLIP_3D("FLIP_3D", "انقلاب ثلاثي", "دوران ثلاثي الأبعاد بعمق سينمائي"),
    GLITCH_RGB("GLITCH_RGB", "ومضة رقمية", "اهتزاز رقمي سريع بألوان متداخلة"),
    BOUNCE_DROP("BOUNCE_DROP", "سقوط ارتدادي", "هبوط حيوي من الأعلى مع ارتداد مرن"),
    SPIN_ORBIT("SPIN_ORBIT", "التفاف محوري", "تدوير زاوي سريع يستقر في المنتصف"),
    CINEMA_BARS("CINEMA_BARS", "إطار سينمائي", "فتح سينمائي بأشرطة عرض احترافية");

    companion object {
        fun fromId(id: String): MotionAnimationType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SLIDE_UP_SPRING
    }
}

enum class VisualMotif(
    val id: String,
    val labelAr: String
) {
    ORBIT_RINGS("ORBIT_RINGS", "مدارات ضوئية"),
    KINETIC_STRIPES("KINETIC_STRIPES", "خطوط حركية"),
    PARTICLE_GLOW("PARTICLE_GLOW", "جزيئات متوهجة"),
    GRID_PULSE("GRID_PULSE", "شبكة رقمية"),
    WAVE_RIBBON("WAVE_RIBBON", "أمواج انسيابية"),
    RADIAL_BURST("RADIAL_BURST", "إشعاع مركزي");

    companion object {
        fun fromId(id: String): VisualMotif =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ORBIT_RINGS
    }
}

enum class SceneSymbol(
    val id: String,
    val labelAr: String
) {
    NONE("NONE", "بدون رمز"),
    SPARKLES("SPARKLES", "تألق"),
    FLASH("FLASH", "خاطف"),
    ROCKET("ROCKET", "انطلاق"),
    SHOPPING("SHOPPING", "تسوق"),
    GIFT("GIFT", "هدية"),
    STAR("STAR", "جودة"),
    TIMER("TIMER", "وقت"),
    SHIELD("SHIELD", "أمان"),
    TRENDING("TRENDING", "نمو"),
    CAFE("CAFE", "ضيافة"),
    CELEBRATE("CELEBRATE", "مناسبة"),
    PLANET("PLANET", "فضاء"),
    GAMEPAD("GAMEPAD", "ألعاب"),
    FILM("FILM", "وثائقي"),
    BOOK("BOOK", "قصة"),
    CAMERA("CAMERA", "تصوير"),
    MUSIC("MUSIC", "صوتيات"),
    BRAIN("BRAIN", "ذكاء"),
    TROPHY("TROPHY", "بطولة"),
    COMPASS("COMPASS", "استكشاف"),
    ECO("ECO", "طبيعة"),
    CODE("CODE", "برمجة"),
    MICROPHONE("MICROPHONE", "سرد");

    companion object {
        fun fromId(id: String): SceneSymbol =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

enum class SaudiVoiceGender(
    val id: String,
    val labelAr: String,
    val shortNameAr: String,
    val sublabelAr: String,
    val isMale: Boolean,
    val geminiVoiceName: String,
    val directorStyleEn: String
) {
    PUCK(
        id = "PUCK",
        labelAr = "ريّان (Puck) • شاب حماسي",
        shortNameAr = "ريّان (Puck)",
        sublabelAr = "صوت شبابي مشرق للإعلانات",
        isMale = true,
        geminiVoiceName = "Puck",
        directorStyleEn = "Young Saudi male presenter, upbeat, enthusiastic, vibrant commercial delivery"
    ),
    CHARON(
        id = "CHARON",
        labelAr = "فيصل (Charon) • شاب رزين",
        shortNameAr = "فيصل (Charon)",
        sublabelAr = "نبرة وثائقية وإخبارية احترافية",
        isMale = true,
        geminiVoiceName = "Charon",
        directorStyleEn = "Saudi male narrator, calm, authoritative, documentary editorial clarity"
    ),
    FENRIR(
        id = "FENRIR",
        labelAr = "سلمان (Fenrir) • شاب فخم",
        shortNameAr = "سلمان (Fenrir)",
        sublabelAr = "صوت عميق وقوي للإطلاقات",
        isMale = true,
        geminiVoiceName = "Fenrir",
        directorStyleEn = "Confident Saudi male voice, deep, warm, impactful brand launch tone"
    ),
    AOEDE(
        id = "AOEDE",
        labelAr = "نورة (Aoede) • فتاة عصرية",
        shortNameAr = "نورة (Aoede)",
        sublabelAr = "أسلوب ناعم وطبيعي للريلز",
        isMale = false,
        geminiVoiceName = "Aoede",
        directorStyleEn = "Young Saudi female presenter, breezy, elegant, modern lifestyle warmth"
    ),
    KORE(
        id = "KORE",
        labelAr = "سارة (Kore) • فتاة واضحة",
        shortNameAr = "سارة (Kore)",
        sublabelAr = "نبرة دقيقة للشروحات والمنتجات",
        isMale = false,
        geminiVoiceName = "Kore",
        directorStyleEn = "Saudi female narrator, firm, crisp, articulate studio voiceover"
    ),
    SULAFAT(
        id = "SULAFAT",
        labelAr = "العنود (Sulafat) • فتاة دافئة",
        shortNameAr = "العنود (Sulafat)",
        sublabelAr = "أسلوب قصصي دافئ وجذاب",
        isMale = false,
        geminiVoiceName = "Sulafat",
        directorStyleEn = "Warm Saudi female storyteller, friendly, inviting, natural conversational cadence"
    );

    companion object {
        fun fromId(id: String): SaudiVoiceGender = when (id.uppercase()) {
            "MALE_SAUDI", "PUCK" -> PUCK
            "FEMALE_SAUDI", "AOEDE" -> AOEDE
            else -> entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PUCK
        }
    }
}

enum class SceneSoundEffect(
    val id: String,
    val labelAr: String,
    val contextHintAr: String,
    val category: AudioMoodCategory
) {
    NONE("NONE", "بدون صوت", "مشهد هادئ بدون مؤثر", AudioMoodCategory.CALM),
    COMMERCE_CASH_DING("COMMERCE_CASH_DING", "رنين عرض وخصم", "للعروض، الخصومات، الهدايا، والتسوق", AudioMoodCategory.COMMERCIAL),
    HOSPITALITY_CLINK("HOSPITALITY_CLINK", "ضيافة وفنجان", "للمقاهي، القهوة، المطاعم، والضيافة", AudioMoodCategory.COMMERCIAL),
    TECH_UI_LOCK("TECH_UI_LOCK", "نقرة تقنية ذكية", "للتطبيقات، الذكاء الاصطناعي، والأمان", AudioMoodCategory.COMMERCIAL),
    LAUNCH_RISER_HIT("LAUNCH_RISER_HIT", "انطلاقة سريعة", "للانطلاق، التوصيل السريع، والسرعة", AudioMoodCategory.ENERGETIC),
    GAME_POWER_UP("GAME_POWER_UP", "قفزة ألعاب حماسية", "للألعاب، التحديات، والرياضة الإلكترونية", AudioMoodCategory.ENERGETIC),
    TIME_TICK_PULSE("TIME_TICK_PULSE", "نبضة وقت محدود", "للمؤقتات، الفرص العاجلة، والتحديات", AudioMoodCategory.ENERGETIC),
    DOC_SOFT_SWELL("DOC_SOFT_SWELL", "نسمة وثائقية", "للانتقالات الوثائقية والقصص الهادئة", AudioMoodCategory.CALM),
    COSMIC_CHIME("COSMIC_CHIME", "صدى فضاء وقصص", "للفضاء والعلوم والمشاهد التأملية", AudioMoodCategory.CALM),
    LUXURY_SHIMMER("LUXURY_SHIMMER", "توهج عطري فاخر", "للعطور، الأزياء، والمنتجات الراقية", AudioMoodCategory.CALM),
    CELEBRATION_CHORD("CELEBRATION_CHORD", "نغمة نجاح وختام", "للمشاهد الختامية والنمو والنتائج", AudioMoodCategory.CALM);

    companion object {
        fun fromId(id: String): SceneSoundEffect = when (id.uppercase()) {
            "WHOOSH" -> LAUNCH_RISER_HIT
            "POP_SPARKLE" -> CELEBRATION_CHORD
            "CHIME_LUXURY" -> LUXURY_SHIMMER
            "CYBER_BEEP" -> TECH_UI_LOCK
            "IMPACT_HIT" -> LAUNCH_RISER_HIT
            else -> entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TECH_UI_LOCK
        }
    }
}

object ContentSfxMatcher {

    fun matchForScene(
        scene: MotionScene,
        paletteId: String = "",
        genre: MotionTopicGenre = MotionTopicGenre.AUTO
    ): SceneSoundEffect {
        val text = "${scene.badge} ${scene.headline} ${scene.subheadline} ${scene.voiceoverScript}"
        return matchForTextAndSymbol(
            text = text,
            symbol = scene.symbolEnum,
            animation = scene.animationEnum,
            paletteId = paletteId,
            genre = genre
        )
    }

    fun matchForTextAndSymbol(
        text: String,
        symbol: SceneSymbol,
        animation: MotionAnimationType = MotionAnimationType.SLIDE_UP_SPRING,
        paletteId: String = "",
        genre: MotionTopicGenre = MotionTopicGenre.AUTO
    ): SceneSoundEffect {
        val normalized = text.lowercase()

        if (containsAny(normalized, "فضاء", "كوكب", "مجرة", "نجوم", "كون", "صاروخ", "تلسكوب", "قمر", "مدار")) {
            return SceneSoundEffect.COSMIC_CHIME
        }
        if (containsAny(normalized, "لعبة", "ألعاب", "لاعب", "بطولة", "تحدي", "مستوى", "فوز", "جيمنج", "بلايستيشن")) {
            return SceneSoundEffect.GAME_POWER_UP
        }
        if (containsAny(normalized, "وثائقي", "حكاية", "تاريخ", "تحقيق", "حقيقة", "قصة", "مجهول", "سر")) {
            return SceneSoundEffect.DOC_SOFT_SWELL
        }
        if (containsAny(normalized, "قهوة", "كافيه", "فنجان", "اسبريسو", "مختصة", "مطعم", "ضيافة", "شاي", "مذاق", "طعم")) {
            return SceneSoundEffect.HOSPITALITY_CLINK
        }
        if (containsAny(normalized, "عطر", "عود", "عنبر", "فخامة", "فاخر", "أزياء", "أناقة", "ذهب", "إطلالة", "مسائي")) {
            return SceneSoundEffect.LUXURY_SHIMMER
        }
        if (containsAny(normalized, "خصم", "تخفيض", "عرض", "سعر", "باقة", "هدية", "تسوق", "متجر", "توفير", "مجاناً", "%", "٪", "شراء")) {
            return SceneSoundEffect.COMMERCE_CASH_DING
        }
        if (containsAny(normalized, "ثانية", "ساعة", "دقيقة", "ينتهي", "محدود", "قبل انتهائه", "مؤقت", "فرصة")) {
            return SceneSoundEffect.TIME_TICK_PULSE
        }
        if (containsAny(normalized, "تطبيق", "منصة", "ذكي", "تقني", "أمان", "حماية", "سحابي", "أتمتة", "تحكم", "لوحة", "نظام", "برمجة", "ذكاء")) {
            return SceneSoundEffect.TECH_UI_LOCK
        }
        if (containsAny(normalized, "انطلق", "سرعة", "البرق", "توصيل", "يصل", "بابك", "إطلاق", "فوري")) {
            return SceneSoundEffect.LAUNCH_RISER_HIT
        }
        if (containsAny(normalized, "نجاح", "نمو", "إنتاجية", "ضاعف", "إنجاز", "مبروك", "ابدأ", "جرب", "حمل", "احتراف")) {
            return SceneSoundEffect.CELEBRATION_CHORD
        }

        return when (symbol) {
            SceneSymbol.PLANET, SceneSymbol.COMPASS -> SceneSoundEffect.COSMIC_CHIME
            SceneSymbol.GAMEPAD, SceneSymbol.TROPHY -> SceneSoundEffect.GAME_POWER_UP
            SceneSymbol.FILM, SceneSymbol.BOOK, SceneSymbol.MICROPHONE -> SceneSoundEffect.DOC_SOFT_SWELL
            SceneSymbol.CAFE -> SceneSoundEffect.HOSPITALITY_CLINK
            SceneSymbol.SHIELD, SceneSymbol.CODE, SceneSymbol.BRAIN -> SceneSoundEffect.TECH_UI_LOCK
            SceneSymbol.SHOPPING, SceneSymbol.GIFT -> SceneSoundEffect.COMMERCE_CASH_DING
            SceneSymbol.TIMER -> SceneSoundEffect.TIME_TICK_PULSE
            SceneSymbol.ROCKET, SceneSymbol.FLASH -> SceneSoundEffect.LAUNCH_RISER_HIT
            SceneSymbol.STAR -> if (paletteId == PresetPalettes.LuxuryGold.id) SceneSoundEffect.LUXURY_SHIMMER else SceneSoundEffect.CELEBRATION_CHORD
            SceneSymbol.TRENDING, SceneSymbol.CELEBRATE, SceneSymbol.SPARKLES, SceneSymbol.ECO -> SceneSoundEffect.CELEBRATION_CHORD
            SceneSymbol.CAMERA, SceneSymbol.MUSIC -> SceneSoundEffect.DOC_SOFT_SWELL
            SceneSymbol.NONE -> when {
                genre == MotionTopicGenre.GAMING -> SceneSoundEffect.GAME_POWER_UP
                genre == MotionTopicGenre.SPACE_SCIENCE -> SceneSoundEffect.COSMIC_CHIME
                genre == MotionTopicGenre.DOCUMENTARY || genre == MotionTopicGenre.STORYTELLING -> SceneSoundEffect.DOC_SOFT_SWELL
                paletteId == PresetPalettes.LuxuryGold.id -> SceneSoundEffect.LUXURY_SHIMMER
                animation == MotionAnimationType.GLITCH_RGB -> SceneSoundEffect.TECH_UI_LOCK
                animation == MotionAnimationType.BOUNCE_DROP -> SceneSoundEffect.LAUNCH_RISER_HIT
                else -> SceneSoundEffect.TECH_UI_LOCK
            }
        }
    }

    fun explainMatch(scene: MotionScene): String {
        return when (scene.soundEffectEnum) {
            SceneSoundEffect.NONE -> "صامت حسب رغبتك"
            SceneSoundEffect.HOSPITALITY_CLINK -> "تجاري • متطابق مع الضيافة والقهوة"
            SceneSoundEffect.TECH_UI_LOCK -> "تجاري • متطابق مع التطبيقات والتقنية"
            SceneSoundEffect.COMMERCE_CASH_DING -> "تجاري • متطابق مع العروض والمبيعات"
            SceneSoundEffect.LAUNCH_RISER_HIT -> "حماسي • متطابق مع السرعة والانطلاق"
            SceneSoundEffect.GAME_POWER_UP -> "حماسي • متطابق مع الألعاب والتحدي"
            SceneSoundEffect.TIME_TICK_PULSE -> "حماسي • متطابق مع التوقيت والإلحاح"
            SceneSoundEffect.DOC_SOFT_SWELL -> "هادئ • متطابق مع الوثائقيات والسرد"
            SceneSoundEffect.COSMIC_CHIME -> "هادئ • متطابق مع الفضاء والعلوم"
            SceneSoundEffect.LUXURY_SHIMMER -> "هادئ • متطابق مع الفخامة والعطور"
            SceneSoundEffect.CELEBRATION_CHORD -> "هادئ • متطابق مع الإنجاز والختام"
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean =
        keywords.any { text.contains(it) }
}

data class SmartColorPalette(
    val id: String,
    val nameAr: String,
    val moodAr: String,
    val backgroundArgb: Long,
    val surfaceArgb: Long,
    val primaryArgb: Long,
    val secondaryArgb: Long,
    val textArgb: Long
) {
    val backgroundColor: Color get() = Color(backgroundArgb)
    val surfaceColor: Color get() = Color(surfaceArgb)
    val primaryColor: Color get() = Color(primaryArgb)
    val secondaryColor: Color get() = Color(secondaryArgb)
    val textColor: Color get() = Color(textArgb)

    val backgroundInt: Int get() = backgroundArgb.toInt()
    val surfaceInt: Int get() = surfaceArgb.toInt()
    val primaryInt: Int get() = primaryArgb.toInt()
    val secondaryInt: Int get() = secondaryArgb.toInt()
    val textInt: Int get() = textArgb.toInt()
}

@JsonClass(generateAdapter = true)
data class CustomColorSchemeData(
    val nameAr: String,
    val moodAr: String,
    val backgroundArgb: Long,
    val surfaceArgb: Long,
    val primaryArgb: Long,
    val secondaryArgb: Long,
    val textArgb: Long
) {
    fun toSmartPalette(): SmartColorPalette = SmartColorPalette(
        id = "ai_custom",
        nameAr = nameAr.ifBlank { "ألوان مبتكرة بالذكاء الاصطناعي" },
        moodAr = moodAr.ifBlank { "مصممة خصيصاً للموضوع" },
        backgroundArgb = backgroundArgb,
        surfaceArgb = surfaceArgb,
        primaryArgb = primaryArgb,
        secondaryArgb = secondaryArgb,
        textArgb = textArgb
    )
}

object ColorHexUtils {
    fun parseHexArgb(hex: String, fallback: Long): Long {
        val clean = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        val normalized = when (clean.length) {
            6 -> "FF$clean"
            8 -> clean
            else -> return fallback
        }
        return normalized.toLongOrNull(16) ?: fallback
    }

    fun ensureReadablePalette(
        nameAr: String,
        moodAr: String,
        bgHex: String,
        surfaceHex: String,
        primaryHex: String,
        secondaryHex: String,
        textHex: String
    ): CustomColorSchemeData {
        var bg = parseHexArgb(bgHex, 0xFF0A0E1C)
        var surface = parseHexArgb(surfaceHex, 0xFF141C33)
        var primary = parseHexArgb(primaryHex, 0xFF00E5FF)
        var secondary = parseHexArgb(secondaryHex, 0xFF8B5CF6)
        var text = parseHexArgb(textHex, 0xFFF8FAFC)

        // Ensure background luminance stays dark enough for rich cinema legibility (< 0.25)
        if (relativeLuminance(bg) > 0.28f) {
            bg = darkenArgb(bg, 0.28f)
            surface = darkenArgb(surface, 0.36f)
        }
        // Guarantee high contrast text against background (ratio >= 6.0)
        if (contrastRatio(bg, text) < 5.5f) {
            text = 0xFFF8FAFC
        }
        // Guarantee primary pop against background
        if (contrastRatio(bg, primary) < 3.2f) {
            primary = lightenArgb(primary, 0.45f)
        }
        if (contrastRatio(bg, secondary) < 2.5f) {
            secondary = lightenArgb(secondary, 0.35f)
        }
        return CustomColorSchemeData(
            nameAr = nameAr,
            moodAr = moodAr,
            backgroundArgb = bg,
            surfaceArgb = surface,
            primaryArgb = primary,
            secondaryArgb = secondary,
            textArgb = text
        )
    }

    private fun relativeLuminance(argb: Long): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun contrastRatio(a: Long, b: Long): Float {
        val l1 = relativeLuminance(a) + 0.05f
        val l2 = relativeLuminance(b) + 0.05f
        return max(l1, l2) / min(l1, l2)
    }

    private fun darkenArgb(argb: Long, factor: Float): Long {
        val r = (((argb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    private fun lightenArgb(argb: Long, mix: Float): Long {
        val r = (((argb shr 16) and 0xFF) + (255 - ((argb shr 16) and 0xFF)) * mix).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) + (255 - ((argb shr 8) and 0xFF)) * mix).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) + (255 - (argb and 0xFF)) * mix).toInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}

object PresetPalettes {
    val CyberNeon = SmartColorPalette(
        id = "cyber_neon",
        nameAr = "سايبر نيون",
        moodAr = "تقني عصري",
        backgroundArgb = 0xFF070B19,
        surfaceArgb = 0xFF111936,
        primaryArgb = 0xFF00E5FF,
        secondaryArgb = 0xFF8B5CF6,
        textArgb = 0xFFF8FAFC
    )

    val LuxuryGold = SmartColorPalette(
        id = "luxury_gold",
        nameAr = "فخامة ذهبية",
        moodAr = "عطور وماركات",
        backgroundArgb = 0xFF120F0B,
        surfaceArgb = 0xFF221B12,
        primaryArgb = 0xFFFBBF24,
        secondaryArgb = 0xFFD97706,
        textArgb = 0xFFFFFBEB
    )

    val CoralSunset = SmartColorPalette(
        id = "coral_sunset",
        nameAr = "غروب مرجاني",
        moodAr = "إعلانات حماسية",
        backgroundArgb = 0xFF190A18,
        surfaceArgb = 0xFF2E122B,
        primaryArgb = 0xFFFF4D6D,
        secondaryArgb = 0xFFFF9E00,
        textArgb = 0xFFFFF1F2
    )

    val EmeraldTech = SmartColorPalette(
        id = "emerald_tech",
        nameAr = "زمرد رقمي",
        moodAr = "مالي وتطبيقات",
        backgroundArgb = 0xFF051411,
        surfaceArgb = 0xFF0C2923,
        primaryArgb = 0xFF10B981,
        secondaryArgb = 0xFF38BDF8,
        textArgb = 0xFFECFDF5
    )

    val all: List<SmartColorPalette> = listOf(
        CyberNeon,
        LuxuryGold,
        CoralSunset,
        EmeraldTech
    )

    fun fromId(id: String): SmartColorPalette =
        all.firstOrNull { it.id == id } ?: CyberNeon
}

@JsonClass(generateAdapter = true)
data class MotionScene(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val badge: String,
    val headline: String,
    val subheadline: String,
    val durationSeconds: Float = 3.8f,
    val animationType: String = MotionAnimationType.SLIDE_UP_SPRING.id,
    val visualMotif: String = VisualMotif.ORBIT_RINGS.id,
    val sceneSymbol: String = SceneSymbol.SPARKLES.id,
    val soundEffect: String = SceneSoundEffect.TECH_UI_LOCK.id,
    val voiceoverScript: String = "",
    val voiceoverEnabled: Boolean = true,
    val transitionToNext: String = SceneTransitionType.CROSS_FADE.id,
    val transitionDurationSeconds: Float = 0.55f
) {
    val animationEnum: MotionAnimationType get() = MotionAnimationType.fromId(animationType)
    val motifEnum: VisualMotif get() = VisualMotif.fromId(visualMotif)
    val symbolEnum: SceneSymbol get() = SceneSymbol.fromId(sceneSymbol)
    val soundEffectEnum: SceneSoundEffect get() = SceneSoundEffect.fromId(soundEffect)
    val transitionEnum: SceneTransitionType get() = SceneTransitionType.fromId(transitionToNext)

    val effectiveVoiceScript: String
        get() = voiceoverScript.trim().ifBlank {
            listOf(headline, subheadline)
                .filter { it.isNotBlank() }
                .joinToString("، ")
        }
}

@JsonClass(generateAdapter = true)
data class MotionProject(
    val projectTitle: String,
    val promptIdea: String,
    val aspectRatio: String = AspectRatioOption.PORTRAIT_9_16.id,
    val paletteId: String = PresetPalettes.CyberNeon.id,
    val customPalette: CustomColorSchemeData? = null,
    val fontStyle: String = MotionFontStyle.THMANYAH.id,
    val topicGenre: String = MotionTopicGenre.AUTO.id,
    val scenes: List<MotionScene>,
    val voiceoverEnabled: Boolean = true,
    val voiceGender: String = SaudiVoiceGender.PUCK.id,
    val soundEffectsEnabled: Boolean = true,
    val backgroundMusicTrack: String = BackgroundMusicTrack.CORP_PULSE.id,
    val backgroundMusicVolume: Float = 0.42f,
    val autoDuckingEnabled: Boolean = true,
    val creativeDirectionAr: String = "",
    val referenceSources: List<String> = emptyList(),
    val version: Int = 4
) {
    val aspectRatioEnum: AspectRatioOption get() = AspectRatioOption.fromId(aspectRatio)
    val palette: SmartColorPalette
        get() = customPalette?.toSmartPalette() ?: PresetPalettes.fromId(paletteId)
    val fontStyleEnum: MotionFontStyle get() = MotionFontStyle.fromId(fontStyle)
    val topicGenreEnum: MotionTopicGenre get() = MotionTopicGenre.fromId(topicGenre)
    val voiceGenderEnum: SaudiVoiceGender get() = SaudiVoiceGender.fromId(voiceGender)
    val backgroundMusicEnum: BackgroundMusicTrack get() = BackgroundMusicTrack.fromId(backgroundMusicTrack)
    val totalDurationSeconds: Float
        get() = scenes.sumOf { it.durationSeconds.toDouble() }.toFloat().coerceAtLeast(0.1f)

    fun toPrettyJson(): String = MotionProjectJson.toPrettyJson(this)
}

object MotionProjectJson {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val adapter by lazy {
        moshi.adapter(MotionProject::class.java).indent("  ")
    }

    fun toPrettyJson(project: MotionProject): String = adapter.toJson(project)

    fun fromJson(json: String): MotionProject? = runCatching {
        adapter.fromJson(json)
    }.getOrNull()
}

object SampleProjects {
    val defaultPrompt = "رحلة بصرية مذهلة عن تلسكوب جيمس ويب وكيف كشف أعمق أسرار المجرات البعيدة في الكون."

    val quickPrompts = listOf(
        "وثائقي قصير عن تلسكوب جيمس ويب وأسرار المجرات الأولى في الفضاء",
        "إعلان حماسي لبطولة ألعاب إلكترونية بجوائز مليونية وبث مباشر",
        "قصة ملهمة عن رحلة رائد أعمال سعودي حوّل شغفه بالقهوة لعلامة عالمية",
        "إعلان إطلاق تطبيق ذكي لإدارة المصاريف بالذكاء الاصطناعي في دقائق"
    )

    val starterProject = MotionProject(
        projectTitle = "نافذة على أعماق الكون",
        promptIdea = defaultPrompt,
        aspectRatio = AspectRatioOption.PORTRAIT_9_16.id,
        paletteId = "ai_custom",
        customPalette = CustomColorSchemeData(
            nameAr = "سديم المجرة العميق",
            moodAr = "فضائي وثائقي ملهم",
            backgroundArgb = 0xFF060918,
            surfaceArgb = 0xFF101935,
            primaryArgb = 0xFF38BDF8,
            secondaryArgb = 0xFFF59E0B,
            textArgb = 0xFFF8FAFC
        ),
        fontStyle = MotionFontStyle.THMANYAH.id,
        topicGenre = MotionTopicGenre.SPACE_SCIENCE.id,
        voiceoverEnabled = true,
        voiceGender = SaudiVoiceGender.CHARON.id,
        soundEffectsEnabled = true,
        backgroundMusicTrack = BackgroundMusicTrack.COSMOS_DRIFT.id,
        backgroundMusicVolume = 0.42f,
        autoDuckingEnabled = true,
        creativeDirectionAr = "ألوان سديم عميقة مستوحاة من صور ناسا وجماليات Kurzgesagt مع خط ثمانية التحريري وانتقالات زوم تدريجية وموسيقى فضائية تنخفض تلقائياً عند السرد.",
        referenceSources = listOf(
            "NASA Visual Studio — Deep Cosmos Color Language",
            "Kurzgesagt — Kinetic Science Storytelling",
            "منصة ثمانية — التوازن التحريري والوضوح البصري"
        ),
        scenes = listOf(
            MotionScene(
                id = "scene_1",
                order = 1,
                badge = "الفضاء العميق",
                headline = "أبعد نظرة للبشرية في الكون",
                subheadline = "على بُعد مليون ونصف كيلومتر تبدأ الحكاية",
                durationSeconds = 4.2f,
                animationType = MotionAnimationType.ZOOM_POP.id,
                visualMotif = VisualMotif.ORBIT_RINGS.id,
                sceneSymbol = SceneSymbol.PLANET.id,
                soundEffect = SceneSoundEffect.COSMIC_CHIME.id,
                voiceoverScript = "على بُعد أكثر من مليون ونصف كيلومتر، ينظر الإنسان لأعماق الكون كما لم يره من قبل.",
                transitionToNext = SceneTransitionType.ZOOM_IN.id,
                transitionDurationSeconds = 0.65f
            ),
            MotionScene(
                id = "scene_2",
                order = 2,
                badge = "تلسكوب جيمس ويب",
                headline = "مرآة ذهبية تلتقط ضوء الماضي",
                subheadline = "ترصد مجرات تشكّلت بعد الانفجار العظيم بملايين السنين",
                durationSeconds = 4.4f,
                animationType = MotionAnimationType.BLUR_FOCUS.id,
                visualMotif = VisualMotif.PARTICLE_GLOW.id,
                sceneSymbol = SceneSymbol.COMPASS.id,
                soundEffect = SceneSoundEffect.DOC_SOFT_SWELL.id,
                voiceoverScript = "بمرآته الذهبية، يلتقط جيمس ويب ضوءاً سافر مليارات السنين من فجر المجرات الأولى.",
                transitionToNext = SceneTransitionType.WIPE_REVEAL.id,
                transitionDurationSeconds = 0.6f
            ),
            MotionScene(
                id = "scene_3",
                order = 3,
                badge = "اكتشاف لا ينتهي",
                headline = "كل نقطة ضوء تروي قصة كوكب",
                subheadline = "العلوم تكشف عوالم جديدة في كل لقطة فضائية",
                durationSeconds = 4.2f,
                animationType = MotionAnimationType.FLOAT_PULSE.id,
                visualMotif = VisualMotif.WAVE_RIBBON.id,
                sceneSymbol = SceneSymbol.SPARKLES.id,
                soundEffect = SceneSoundEffect.COSMIC_CHIME.id,
                voiceoverScript = "وما زال الكون يفتح لنا أبوابه في كل صورة، ليكشف أن الرحلة ما زالت في بدايتها.",
                transitionToNext = SceneTransitionType.CROSS_FADE.id,
                transitionDurationSeconds = 0.55f
            )
        )
    )
}
