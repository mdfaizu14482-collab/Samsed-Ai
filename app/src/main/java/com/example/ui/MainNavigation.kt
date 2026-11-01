package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ai.GeminiService
import com.example.data.models.AiFeatureMode
import com.example.data.repository.FirestoreRepository
import com.example.ui.admin.AdminPanelScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.history.ChatHistoryDrawer
import com.example.ui.imagegen.ImageGeneratorScreen
import com.example.ui.search.SearchScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tools.StudyHelpScreen
import com.example.ui.tools.WritingAssistantScreen
import com.example.ui.vision.VisionAnalyzerScreen
import com.example.ui.voice.VoiceAssistantScreen
import com.example.voice.VoiceAssistantManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    currentUserId: String,
    repository: FirestoreRepository,
    geminiService: GeminiService
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentMode by remember { mutableStateOf(AiFeatureMode.CHAT) }
    var activeConversationId by remember { mutableStateOf("") }
    var isVoiceOverlayOpen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    // Voice assistant manager instance
    val voiceManager = remember {
        VoiceAssistantManager(context = context.applicationContext) { transcript ->
            // Triggered when final speech recognized
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }

    // Ensure user has at least one active conversation
    var isCreatingDefaultChat by remember { mutableStateOf(false) }
    val conversations by repository.observeConversations(currentUserId).collectAsState(initial = emptyList())
    LaunchedEffect(conversations) {
        if (activeConversationId.isBlank() && conversations.isNotEmpty()) {
            activeConversationId = conversations.first().id
        } else if (conversations.isEmpty() && activeConversationId.isBlank() && !isCreatingDefaultChat) {
            isCreatingDefaultChat = true
            coroutineScope.launch {
                try {
                    val newId = repository.createConversation(currentUserId, "Welcome to SAMSED AI", "chat").getOrNull()
                    if (newId != null) {
                        activeConversationId = newId
                    }
                } finally {
                    isCreatingDefaultChat = false
                }
            }
        }
    }

    // Sync user profile upon entering
    val authUser = FirebaseAuth.getInstance().currentUser
    LaunchedEffect(currentUserId) {
        if (authUser != null) {
            repository.syncUserProfile(
                userId = currentUserId,
                email = authUser.email.orEmpty(),
                displayName = authUser.displayName.orEmpty(),
                photoUrl = authUser.photoUrl?.toString().orEmpty()
            )
        }
    }

    // Hardware Back Button Handling
    BackHandler(enabled = drawerState.isOpen || isVoiceOverlayOpen || showSettingsScreen || currentMode != AiFeatureMode.CHAT) {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            isVoiceOverlayOpen -> isVoiceOverlayOpen = false
            showSettingsScreen -> showSettingsScreen = false
            currentMode != AiFeatureMode.CHAT -> currentMode = AiFeatureMode.CHAT
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawer(
                currentUserId = currentUserId,
                activeConversationId = activeConversationId,
                currentMode = currentMode,
                repository = repository,
                onSelectConversation = { convId ->
                    activeConversationId = convId
                    currentMode = AiFeatureMode.CHAT
                    showSettingsScreen = false
                },
                onNewChat = {
                    coroutineScope.launch {
                        val newId = repository.createConversation(currentUserId, "New Chat", "chat").getOrNull()
                        if (newId != null) {
                            activeConversationId = newId
                            currentMode = AiFeatureMode.CHAT
                            showSettingsScreen = false
                        }
                    }
                },
                onSelectMode = { mode ->
                    currentMode = mode
                    showSettingsScreen = false
                },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                if (!isVoiceOverlayOpen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentMode == AiFeatureMode.CHAT && !showSettingsScreen,
                            onClick = {
                                currentMode = AiFeatureMode.CHAT
                                showSettingsScreen = false
                            },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                            label = { Text("Chat", style = MaterialTheme.typography.labelSmall) }
                        )
                        NavigationBarItem(
                            selected = currentMode == AiFeatureMode.VISION && !showSettingsScreen,
                            onClick = {
                                currentMode = AiFeatureMode.VISION
                                showSettingsScreen = false
                            },
                            icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Vision") },
                            label = { Text("Vision", style = MaterialTheme.typography.labelSmall) }
                        )
                        NavigationBarItem(
                            selected = isVoiceOverlayOpen,
                            onClick = { isVoiceOverlayOpen = true },
                            icon = { Icon(Icons.Default.Mic, contentDescription = "Voice") },
                            label = { Text("Voice", style = MaterialTheme.typography.labelSmall) }
                        )
                        NavigationBarItem(
                            selected = currentMode == AiFeatureMode.IMAGE_GEN && !showSettingsScreen,
                            onClick = {
                                currentMode = AiFeatureMode.IMAGE_GEN
                                showSettingsScreen = false
                            },
                            icon = { Icon(Icons.Default.Palette, contentDescription = "Images") },
                            label = { Text("Studio", style = MaterialTheme.typography.labelSmall) }
                        )
                        NavigationBarItem(
                            selected = showSettingsScreen,
                            onClick = { showSettingsScreen = true },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (showSettingsScreen) {
                    SettingsScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } },
                        onSignOut = {
                            // Handled by Auth state flow in MainActivity
                        }
                    )
                } else {
                    when (currentMode) {
                        AiFeatureMode.CHAT -> {
                            ChatScreen(
                                currentUserId = currentUserId,
                                conversationId = activeConversationId,
                                repository = repository,
                                geminiService = geminiService,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } },
                                onOpenVoice = { isVoiceOverlayOpen = true },
                                onSpeakMessage = { text -> voiceManager.speak(text) }
                            )
                        }
                        AiFeatureMode.VOICE -> {
                            VoiceAssistantScreen(
                                voiceManager = voiceManager,
                                geminiService = geminiService,
                                onClose = { currentMode = AiFeatureMode.CHAT }
                            )
                        }
                        AiFeatureMode.VISION -> {
                            VisionAnalyzerScreen(
                                geminiService = geminiService,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } }
                            )
                        }
                        AiFeatureMode.IMAGE_GEN -> {
                            ImageGeneratorScreen(
                                geminiService = geminiService,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } }
                            )
                        }
                        AiFeatureMode.SEARCH -> {
                            SearchScreen(
                                geminiService = geminiService,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } }
                            )
                        }
                        AiFeatureMode.WRITING -> {
                            WritingAssistantScreen(
                                geminiService = geminiService,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } }
                            )
                        }
                        AiFeatureMode.STUDY -> {
                            StudyHelpScreen(
                                geminiService = geminiService,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } }
                            )
                        }
                        AiFeatureMode.ADMIN -> {
                            AdminPanelScreen(
                                currentUserId = currentUserId,
                                repository = repository,
                                onMenuClick = { coroutineScope.launch { drawerState.open() } }
                            )
                        }
                    }
                }

                // Voice Assistant Modal Overlay
                if (isVoiceOverlayOpen) {
                    VoiceAssistantScreen(
                        voiceManager = voiceManager,
                        geminiService = geminiService,
                        onClose = { isVoiceOverlayOpen = false }
                    )
                }
            }
        }
    }
}
