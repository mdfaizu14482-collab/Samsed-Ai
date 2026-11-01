package com.example.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.GeminiService
import com.example.ui.theme.*
import com.example.voice.VoiceAssistantManager
import com.example.voice.VoiceState
import kotlinx.coroutines.launch

@Composable
fun VoiceAssistantScreen(
    voiceManager: VoiceAssistantManager,
    geminiService: GeminiService,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val voiceState by voiceManager.voiceState.collectAsState()
    val rmsLevel by voiceManager.rmsLevel.collectAsState()
    val partialTranscript by voiceManager.partialTranscript.collectAsState()

    var aiAnswerText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    // Audio recording permission launcher
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceManager.startListening()
        }
    }

    // Orb breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Dynamic scale driven by audio RMS level
    val dynamicOrbScale = if (voiceState is VoiceState.Listening) {
        1.0f + (rmsLevel * 0.4f)
    } else if (voiceState is VoiceState.Speaking) {
        pulseScale
    } else {
        1.0f
    }

    fun handleVoiceInput(recognized: String) {
        if (recognized.isBlank()) return
        isThinking = true
        coroutineScope.launch {
            val responseBuilder = StringBuilder()
            geminiService.streamChat(
                prompt = recognized,
                customSystemInstruction = "You are SAMSED AI Voice Companion. Answer concisely, naturally, and conversationally in 2-4 sentences so it is pleasant and clear when read aloud over voice."
            ).collect { chunk ->
                responseBuilder.append(chunk)
            }
            val reply = responseBuilder.toString().trim()
            aiAnswerText = reply
            isThinking = false
            voiceManager.speak(reply)
        }
    }

    Scaffold(
        containerColor = ObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        voiceManager.stopListening()
                        voiceManager.stopSpeaking()
                        onClose()
                    },
                    modifier = Modifier.testTag("close_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Voice Assistant",
                        tint = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SamsedCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamsedCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "SAMSED LIVE VOICE",
                        color = SamsedCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = { voiceManager.stopSpeaking() },
                    modifier = Modifier.testTag("mute_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = "Mute Voice",
                        tint = TextSecondary
                    )
                }
            }

            // Center: Glowing Holographic Neural Orb
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    // Outer Ripple
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(dynamicOrbScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SamsedCyan.copy(alpha = 0.35f),
                                        SamsedViolet.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Middle Core Orb
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .scale(if (voiceState is VoiceState.Listening) 1.05f else 1.0f)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(SamsedCyan, SamsedVioletDark, Color(0xFF0F172A))
                                )
                            )
                            .border(2.dp, SamsedCyan.copy(alpha = 0.8f), CircleShape)
                            .clickable {
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (voiceState is VoiceState.Listening) {
                                    voiceManager.stopListening()
                                } else {
                                    voiceManager.startListening()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (voiceState) {
                                is VoiceState.Listening -> Icons.Default.Mic
                                is VoiceState.Speaking -> Icons.Default.VolumeUp
                                else -> Icons.Default.GraphicEq
                            },
                            contentDescription = "Voice Status",
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Voice State Status Label
                Text(
                    text = when {
                        isThinking -> "SAMSED AI is thinking..."
                        voiceState is VoiceState.Listening -> "Listening... speak now"
                        voiceState is VoiceState.Processing -> "Processing speech..."
                        voiceState is VoiceState.Speaking -> "Speaking response..."
                        else -> "Tap the orb to start speaking"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SamsedCyanLight,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time transcription or response
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayText = when {
                            partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                            aiAnswerText.isNotBlank() -> aiAnswerText
                            else -> "Ask anything: \"Tell me about artificial neural networks\" or \"Draft an outline for my startup pitch\""
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (partialTranscript.isNotBlank() || aiAnswerText.isNotBlank()) Color.White else TextMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }

            // Bottom Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                voiceManager.startListening()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (voiceState is VoiceState.Listening) SamsedRose else SamsedCyanDark
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("toggle_voice_btn")
                    ) {
                        Icon(
                            imageVector = if (voiceState is VoiceState.Listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (voiceState is VoiceState.Listening) "Stop Listening" else "Hold / Tap to Speak",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
