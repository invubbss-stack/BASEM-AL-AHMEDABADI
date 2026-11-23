package com.example

import com.example.model.MotionFontStyle
import com.example.model.MotionProject
import com.example.model.MotionScene
import com.example.model.SceneTransitionType
import com.example.model.TimelineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEngineTest {

    @Test
    fun buildSegments_preservesAllScenes_andExpandsForLongVoiceover() {
        val project = MotionProject(
            projectTitle = "اختبار الدمج",
            promptIdea = "فكرة",
            fontStyle = MotionFontStyle.READEX_PRO.id,
            scenes = listOf(
                MotionScene(
                    id = "s1",
                    order = 1,
                    badge = "1",
                    headline = "الأول",
                    subheadline = "وصف 1",
                    durationSeconds = 3.0f,
                    transitionToNext = SceneTransitionType.SLIDE_LEFT.id,
                    transitionDurationSeconds = 0.6f
                ),
                MotionScene(
                    id = "s2",
                    order = 2,
                    badge = "2",
                    headline = "الثاني",
                    subheadline = "وصف 2",
                    durationSeconds = 3.5f,
                    transitionToNext = SceneTransitionType.ZOOM_IN.id,
                    transitionDurationSeconds = 0.5f
                ),
                MotionScene(
                    id = "s3",
                    order = 3,
                    badge = "3",
                    headline = "الثالث",
                    subheadline = "وصف 3",
                    durationSeconds = 4.0f
                )
            )
        )

        // Suppose s1 voiceover is 4.5s (longer than nominal 3.0s)
        val segments = TimelineEngine.buildSegments(
            project = project,
            voiceDurationSecondsBySceneId = mapOf("s1" to 4.5f, "s2" to 2.8f)
        )

        assertEquals(3, segments.size)
        // s1 must expand to 4.5 + 0.35 = 4.85s so voice is never cut
        assertEquals(4.85f, segments[0].effectiveDuration, 0.01f)
        // s2 stays at 3.5s because voice 2.8 + 0.35 < 3.5
        assertEquals(3.5f, segments[1].effectiveDuration, 0.01f)
        // Total duration equals sum of all segments
        assertEquals(4.85f + 3.5f + 4.0f, TimelineEngine.totalDuration(segments), 0.01f)
    }

    @Test
    fun resolveFrameAt_isContinuousAcrossTransitionBoundary() {
        val project = MotionProject(
            projectTitle = "اختبار الانتقال",
            promptIdea = "فكرة",
            scenes = listOf(
                MotionScene(
                    id = "s1",
                    order = 1,
                    badge = "1",
                    headline = "أول",
                    subheadline = "أول",
                    durationSeconds = 4.0f,
                    transitionToNext = SceneTransitionType.CROSS_FADE.id,
                    transitionDurationSeconds = 0.6f
                ),
                MotionScene(
                    id = "s2",
                    order = 2,
                    badge = "2",
                    headline = "ثاني",
                    subheadline = "ثاني",
                    durationSeconds = 4.0f,
                    transitionToNext = SceneTransitionType.NONE.id
                )
            )
        )

        val segments = TimelineEngine.buildSegments(project)

        // Mid-scene 1: no transition active
        val mid1 = TimelineEngine.resolveFrameAt(segments, 2.0f)
        assertEquals(0, mid1.primaryIndex)
        assertNull(mid1.incomingScene)
        assertEquals(0f, mid1.transitionProgress, 0.001f)

        // Just before cut (3.99s): transition active, incoming is s2
        val beforeCut = TimelineEngine.resolveFrameAt(segments, 3.999f)
        assertEquals(0, beforeCut.primaryIndex)
        assertNotNull(beforeCut.incomingScene)
        assertEquals(1, beforeCut.incomingIndex)
        assertTrue(beforeCut.transitionProgress > 0.99f)

        // Just after cut (4.001s): primary is s2, progress matches incomingProgress seamlessly
        val afterCut = TimelineEngine.resolveFrameAt(segments, 4.001f)
        assertEquals(1, afterCut.primaryIndex)
        assertEquals(beforeCut.incomingProgress, afterCut.primaryProgress, 0.01f)
    }

    @Test
    fun colorHexUtils_enforcesReadableContrastEvenForBrightInputs() {
        val scheme = com.example.model.ColorHexUtils.ensureReadablePalette(
            nameAr = "اختبار التباين",
            moodAr = "اختبار",
            bgHex = "#FFFFFF",
            surfaceHex = "#EEEEEE",
            primaryHex = "#111111",
            secondaryHex = "#222222",
            textHex = "#FFFFFF"
        )
        val palette = scheme.toSmartPalette()
        // White background must be darkened significantly and text stays light
        assertTrue((palette.backgroundArgb and 0xFF) < 0x80)
        assertEquals(0xFFF8FAFC, palette.textArgb)
    }

    @Test
    fun contentSfxMatcher_matchesTopicCategoriesAccurately() {
        val gamingScene = MotionScene(
            order = 1,
            badge = "بطولة",
            headline = "انطلاق بطولة الألعاب الكبرى",
            subheadline = "تحدي اللاعبين على اللقب",
            sceneSymbol = com.example.model.SceneSymbol.GAMEPAD.id
        )
        val gamingSfx = com.example.model.ContentSfxMatcher.matchForScene(
            gamingScene,
            genre = com.example.model.MotionTopicGenre.GAMING
        )
        assertEquals(com.example.model.SceneSoundEffect.GAME_POWER_UP, gamingSfx)
        assertEquals(com.example.model.AudioMoodCategory.ENERGETIC, gamingSfx.category)

        val spaceScene = MotionScene(
            order = 2,
            badge = "مجرة",
            headline = "استكشاف كواكب الفضاء العميق",
            subheadline = "رحلة تلسكوب نحو النجوم",
            sceneSymbol = com.example.model.SceneSymbol.PLANET.id
        )
        val spaceSfx = com.example.model.ContentSfxMatcher.matchForScene(
            spaceScene,
            genre = com.example.model.MotionTopicGenre.SPACE_SCIENCE
        )
        assertEquals(com.example.model.SceneSoundEffect.COSMIC_CHIME, spaceSfx)
        assertEquals(com.example.model.AudioMoodCategory.CALM, spaceSfx.category)

        val offerScene = MotionScene(
            order = 3,
            badge = "خصم 50٪",
            headline = "عرض متجر لفترة محدودة",
            subheadline = "باقة توفير وهدايا مجانية",
            sceneSymbol = com.example.model.SceneSymbol.SHOPPING.id
        )
        val offerSfx = com.example.model.ContentSfxMatcher.matchForScene(
            offerScene,
            genre = com.example.model.MotionTopicGenre.COMMERCIAL_AD
        )
        assertEquals(com.example.model.SceneSoundEffect.COMMERCE_CASH_DING, offerSfx)
        assertEquals(com.example.model.AudioMoodCategory.COMMERCIAL, offerSfx.category)
    }

    @Test
    fun backgroundMusicTrack_providesDefaultsForAllAudioMoods() {
        assertEquals(
            com.example.model.AudioMoodCategory.COMMERCIAL,
            com.example.model.BackgroundMusicTrack.defaultForMood(com.example.model.AudioMoodCategory.COMMERCIAL).category
        )
        assertEquals(
            com.example.model.AudioMoodCategory.ENERGETIC,
            com.example.model.BackgroundMusicTrack.defaultForMood(com.example.model.AudioMoodCategory.ENERGETIC).category
        )
        assertEquals(
            com.example.model.AudioMoodCategory.CALM,
            com.example.model.BackgroundMusicTrack.defaultForMood(com.example.model.AudioMoodCategory.CALM).category
        )
    }

    @Test
    fun sceneVfxMatcher_activatesEffectsBySceneTypeAndPreservesManualOverride() {
        val gamingScene = MotionScene(
            order = 1,
            badge = "بطولة",
            headline = "انطلاق بطولة الألعاب",
            subheadline = "مواجهات حماسية مباشرة",
            sceneSymbol = com.example.model.SceneSymbol.GAMEPAD.id
        )
        assertEquals(
            com.example.model.SceneVisualEffect.CYBER_GLOW_PARTICLES,
            com.example.model.SceneVfxMatcher.resolveEffectiveEffect(
                gamingScene,
                com.example.model.MotionTopicGenre.GAMING
            )
        )

        val spaceScene = MotionScene(
            order = 2,
            badge = "الفضاء",
            headline = "رحلة بين النجوم والمجرات",
            subheadline = "اكتشاف كوكب جديد",
            sceneSymbol = com.example.model.SceneSymbol.PLANET.id
        )
        assertEquals(
            com.example.model.SceneVisualEffect.FLOATING_PARTICLES,
            com.example.model.SceneVfxMatcher.resolveEffectiveEffect(
                spaceScene,
                com.example.model.MotionTopicGenre.SPACE_SCIENCE
            )
        )

        val docScene = MotionScene(
            order = 3,
            badge = "وثائقي",
            headline = "حكاية من قلب التاريخ",
            subheadline = "تحقيق بصري عميق",
            sceneSymbol = com.example.model.SceneSymbol.FILM.id
        )
        assertEquals(
            com.example.model.SceneVisualEffect.CINEMATIC_BLUR,
            com.example.model.SceneVfxMatcher.resolveEffectiveEffect(
                docScene,
                com.example.model.MotionTopicGenre.DOCUMENTARY
            )
        )

        val techScene = MotionScene(
            order = 4,
            badge = "إطلاق",
            headline = "منصة ذكية سريعة",
            subheadline = "أداء تقني متفوق",
            sceneSymbol = com.example.model.SceneSymbol.CODE.id
        )
        assertEquals(
            com.example.model.SceneVisualEffect.NEON_GLOW,
            com.example.model.SceneVfxMatcher.resolveEffectiveEffect(
                techScene,
                com.example.model.MotionTopicGenre.TECH_LAUNCH
            )
        )

        val manualOverride = gamingScene.copy(
            visualEffect = com.example.model.SceneVisualEffect.NONE.id
        )
        assertEquals(
            com.example.model.SceneVisualEffect.NONE,
            com.example.model.SceneVfxMatcher.resolveEffectiveEffect(
                manualOverride,
                com.example.model.MotionTopicGenre.GAMING
            )
        )
    }
}

