package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.export.MotionVideoExporter
import com.example.ui.components.AiChatDirectorSheet
import com.example.ui.components.GeneratorControlPanel
import com.example.ui.components.JsonPreviewDialog
import com.example.ui.components.PreviewPlayerSection
import com.example.ui.components.SceneEditorSection
import com.example.viewmodel.MotionStudioUiState
import com.example.viewmodel.MotionStudioViewModel
import java.io.File

private enum class StudioTab(
    val labelAr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    STUDIO("الاستوديو", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    PREVIEW("المعاينة", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    SCENES("المشاهد", Icons.Filled.Tune, Icons.Outlined.Tune)
}

@Composable
fun MotionStudioScreen(
    viewModel: MotionStudioViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var activeTab by rememberSaveable { mutableStateOf(StudioTab.STUDIO) }
    var expandedEditorMode by rememberSaveable { mutableStateOf(0) } // 0 = AI Studio, 1 = Scenes
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF File Pickers & Savers (zero broad storage permissions required)
    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.saveJsonToUri(uri)
    }

    val saveMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null) viewModel.saveMp4ToUri(uri)
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importJsonFromUri(uri)
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    // BackHandler to return to Studio tab from secondary tabs on phone layout
    BackHandler(enabled = activeTab != StudioTab.STUDIO) {
        activeTab = StudioTab.STUDIO
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        val isExpandedLayout = maxWidth >= 840.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                StudioTopBar(
                    sceneCount = uiState.project.scenes.size,
                    isExpandedLayout = isExpandedLayout,
                    onQuickPreviewClick = { activeTab = StudioTab.PREVIEW },
                    onOpenAiChat = { viewModel.toggleAiChatSheet(true) }
                )
            },
            bottomBar = {
                if (!isExpandedLayout) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.labelAr
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.labelAr,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Error Banner (Always visible when AI or Export errors occur)
                AnimatedVisibility(visible = !uiState.errorMessage.isNullOrBlank()) {
                    ErrorNoticeBanner(
                        message = uiState.errorMessage.orEmpty(),
                        onDismiss = viewModel::clearError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (isExpandedLayout) {
                    // Tablet / Foldable 2-pane Pro Studio Layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Control & Scene Editor Pane
                        Column(
                            modifier = Modifier
                                .weight(0.52f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = expandedEditorMode == 0,
                                    onClick = { expandedEditorMode = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("المولد الذكي والألوان")
                                }
                                SegmentedButton(
                                    selected = expandedEditorMode == 1,
                                    onClick = { expandedEditorMode = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("محرر المشاهد (${uiState.project.scenes.size})")
                                }
                            }

                            if (expandedEditorMode == 0) {
                                BuildGeneratorPanel(viewModel = viewModel, uiState = uiState)
                            } else {
                                BuildSceneEditor(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    onPreviewScene = { idx -> viewModel.jumpToScene(idx) }
                                )
                            }
                        }

                        // Live Preview Player Pane
                        Column(
                            modifier = Modifier
                                .weight(0.48f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            BuildPreviewSection(
                                viewModel = viewModel,
                                uiState = uiState,
                                onSaveJson = {
                                    saveJsonLauncher.launch("motion_project_${System.currentTimeMillis()}.json")
                                },
                                onShareJson = {
                                    MotionVideoExporter.shareJsonContent(
                                        context = context,
                                        jsonText = uiState.project.toPrettyJson(),
                                        title = uiState.project.projectTitle
                                    )
                                },
                                onImportJson = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onSaveMp4 = {
                                    saveMp4Launcher.launch("motion_video_${System.currentTimeMillis()}.mp4")
                                },
                                onShareMp4 = { file ->
                                    MotionVideoExporter.shareVideoFile(
                                        context = context,
                                        mp4File = file,
                                        title = uiState.project.projectTitle
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // Phone Single-Column Tabbed Studio
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 600.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (activeTab) {
                                StudioTab.STUDIO -> {
                                    BuildGeneratorPanel(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onGenerated = { activeTab = StudioTab.PREVIEW }
                                    )
                                }

                                StudioTab.PREVIEW -> {
                                    BuildPreviewSection(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onSaveJson = {
                                            saveJsonLauncher.launch("motion_project_${System.currentTimeMillis()}.json")
                                        },
                                        onShareJson = {
                                            MotionVideoExporter.shareJsonContent(
                                                context = context,
                                                jsonText = uiState.project.toPrettyJson(),
                                                title = uiState.project.projectTitle
                                            )
                                        },
                                        onImportJson = {
                                            importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
                                        },
                                        onSaveMp4 = {
                                            saveMp4Launcher.launch("motion_video_${System.currentTimeMillis()}.mp4")
                                        },
                                        onShareMp4 = { file ->
                                            MotionVideoExporter.shareVideoFile(
                                                context = context,
                                                mp4File = file,
                                                title = uiState.project.projectTitle
                                            )
                                        }
                                    )
                                }

                                StudioTab.SCENES -> {
                                    BuildSceneEditor(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onPreviewScene = { idx ->
                                            viewModel.jumpToScene(idx)
                                            activeTab = StudioTab.PREVIEW
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        if (uiState.showAiChatSheet) {
            AiChatDirectorSheet(
                messages = uiState.chatMessages,
                isApplyingEdit = uiState.isApplyingChatEdit,
                projectTitle = uiState.project.projectTitle,
                onDismiss = { viewModel.toggleAiChatSheet(false) },
                onSendMessage = { message ->
                    viewModel.sendChatEditMessage(message) {
                        if (!isExpandedLayout) {
                            activeTab = StudioTab.PREVIEW
                        }
                    }
                }
            )
        }
    }

    if (uiState.showJsonDialog) {
        JsonPreviewDialog(
            jsonText = uiState.project.toPrettyJson(),
            onDismiss = { viewModel.toggleJsonDialog(false) },
            onSaveJson = {
                viewModel.toggleJsonDialog(false)
                saveJsonLauncher.launch("motion_project_${System.currentTimeMillis()}.json")
            },
            onShareJson = {
                MotionVideoExporter.shareJsonContent(
                    context = context,
                    jsonText = uiState.project.toPrettyJson(),
                    title = uiState.project.projectTitle
                )
            },
            onCopied = {
                viewModel.toggleJsonDialog(false)
            }
        )
    }
}

@Composable
private fun StudioTopBar(
    sceneCount: Int,
    isExpandedLayout: Boolean,
    onQuickPreviewClick: () -> Unit,
    onOpenAiChat: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_motion_logo_1795466221145),
                    contentDescription = "شعار ستوديو الموشن",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column {
                    Text(
                        text = "ستوديو الموشن",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "مخرج الفيديوهات بالذكاء الاصطناعي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenAiChat)
                        .testTag("topbar_ai_chat_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubbleOutline,
                            contentDescription = "تحدث مع الذكاء لتعديل الفيديو",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "دردشة AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isExpandedLayout, onClick = onQuickPreviewClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$sceneCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorNoticeBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "إغلاق التنبيه",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun BuildGeneratorPanel(
    viewModel: MotionStudioViewModel,
    uiState: MotionStudioUiState,
    onGenerated: () -> Unit = {}
) {
    GeneratorControlPanel(
        uiState = uiState,
        onPromptChange = viewModel::updatePrompt,
        onApplyQuickPrompt = viewModel::applyQuickPrompt,
        onImproveCopy = viewModel::improveAdCopy,
        onUndoCopy = viewModel::undoImprovedCopy,
        onSelectTopicGenre = viewModel::selectTopicGenre,
        onToggleCreativeFreedom = viewModel::toggleCreativeFreedomMode,
        onSelectAspectRatio = viewModel::selectAspectRatio,
        onSelectPalette = viewModel::selectPalette,
        onSelectFontStyle = viewModel::selectFontStyle,
        onSelectSfxCategory = viewModel::selectSfxCategoryFilter,
        onSelectBackgroundMusic = viewModel::selectBackgroundMusic,
        onUpdateMusicVolume = viewModel::updateBackgroundMusicVolume,
        onToggleAutoDucking = viewModel::toggleAutoDucking,
        onToggleVoiceover = viewModel::toggleVoiceoverGlobal,
        onSelectVoiceGender = viewModel::selectVoiceGender,
        onToggleSoundEffects = viewModel::toggleSoundEffectsGlobal,
        onOpenAiChat = { viewModel.toggleAiChatSheet(true) },
        onGenerateMotion = { viewModel.generateMotionGraphics(onSuccessJumpToPreview = onGenerated) }
    )
}

@Composable
private fun BuildPreviewSection(
    viewModel: MotionStudioViewModel,
    uiState: MotionStudioUiState,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onImportJson: () -> Unit,
    onSaveMp4: () -> Unit,
    onShareMp4: (File) -> Unit
) {
    PreviewPlayerSection(
        uiState = uiState,
        onTogglePlay = viewModel::togglePlayPause,
        onReplay = viewModel::replayFromStart,
        onPreviousScene = viewModel::previousScene,
        onNextScene = viewModel::nextScene,
        onJumpToScene = viewModel::jumpToScene,
        onSelectTransitionCut = viewModel::selectTransitionCut,
        onUpdateSceneTransition = viewModel::updateSceneTransition,
        onApplyTransitionToAll = viewModel::applyTransitionToAll,
        onPreviewTransitionCut = viewModel::previewTransitionCut,
        onToggleVoiceover = viewModel::toggleVoiceoverGlobal,
        onSelectVoiceGender = viewModel::selectVoiceGender,
        onToggleSoundEffects = viewModel::toggleSoundEffectsGlobal,
        onToggleAutoDucking = viewModel::toggleAutoDucking,
        onSynthesizeSaudiVoices = viewModel::synthesizeSaudiNeuralVoices,
        onOpenAiChat = { viewModel.toggleAiChatSheet(true) },
        onExportMp4 = { viewModel.renderAndExportVideo() },
        onSaveMp4ToDevice = { onSaveMp4() },
        onShareMp4 = onShareMp4,
        onSaveJson = onSaveJson,
        onShareJson = onShareJson,
        onViewJson = { viewModel.toggleJsonDialog(true) },
        onImportJson = onImportJson
    )
}

@Composable
private fun BuildSceneEditor(
    viewModel: MotionStudioViewModel,
    uiState: MotionStudioUiState,
    onPreviewScene: (Int) -> Unit
) {
    SceneEditorSection(
        scenes = uiState.project.scenes,
        selectedIndex = uiState.selectedSceneIndex,
        totalDurationSeconds = uiState.effectiveTotalDurationSeconds,
        topicGenre = uiState.project.topicGenreEnum,
        selectedSfxCategoryFilter = uiState.selectedSfxCategoryFilter,
        onSelectSfxCategory = viewModel::selectSfxCategoryFilter,
        onSelectScene = viewModel::selectSceneForEdit,
        onUpdateScene = viewModel::updateScene,
        onAddScene = viewModel::addNewScene,
        onDeleteScene = viewModel::deleteScene,
        onMoveScene = viewModel::moveScene,
        onPreviewScene = onPreviewScene,
        onPreviewSoundEffect = viewModel::previewSoundEffect,
        onAutoMatchSfx = viewModel::autoMatchSceneSfx,
        onPreviewVoiceover = viewModel::previewSceneVoiceover
    )
}
