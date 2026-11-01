package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.GeminiService
import com.example.data.models.ChatMessage
import com.example.data.models.Conversation
import com.example.data.repository.FirestoreRepository
import com.example.ui.components.CodeBlockView
import com.example.ui.components.SamsedHeader
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    currentUserId: String,
    conversationId: String,
    repository: FirestoreRepository,
    geminiService: GeminiService,
    onMenuClick: () -> Unit,
    onOpenVoice: () -> Unit,
    onSpeakMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val messages by repository.observeMessages(currentUserId, conversationId)
        .collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }
    var streamingResponse by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Modern Android Photo Picker (zero broad storage permissions)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = bitmap
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-scroll on new message or during streaming
    LaunchedEffect(messages.size, streamingResponse) {
        if (messages.isNotEmpty() || streamingResponse.isNotEmpty()) {
            val targetIndex = (messages.size + (if (streamingResponse.isNotEmpty()) 1 else 0)) - 1
            if (targetIndex >= 0) {
                try {
                    listState.animateScrollToItem(targetIndex)
                } catch (e: Throwable) {
                    // Prevent scroll animation exception during rapid recomposition
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && selectedBitmap == null) return

        val promptToSend = trimmed
        val bitmapToSend = selectedBitmap
        inputText = ""
        selectedImageUri = null
        selectedBitmap = null
        focusManager.clearFocus()

        coroutineScope.launch {
            // Save user message in Firestore
            repository.addMessage(
                userId = currentUserId,
                conversationId = conversationId,
                role = "user",
                content = promptToSend,
                hasImage = bitmapToSend != null
            )

            isStreaming = true
            streamingResponse = ""

            if (bitmapToSend != null) {
                // Multimodal vision request
                val result = geminiService.analyzeImage(
                    bitmap = bitmapToSend,
                    userPrompt = promptToSend
                )
                val aiText = result.getOrElse { "Vision analysis failed: ${it.localizedMessage}" }
                repository.addMessage(
                    userId = currentUserId,
                    conversationId = conversationId,
                    role = "model",
                    content = aiText,
                    toolUsed = "vision"
                )
                isStreaming = false
                streamingResponse = ""
            } else {
                // Multi-turn chat streaming
                val history = messages.takeLast(10).map { it.role to it.content }
                val accumulated = StringBuilder()

                geminiService.streamChat(
                    prompt = promptToSend,
                    history = history
                ).collect { chunk ->
                    accumulated.append(chunk)
                    streamingResponse = accumulated.toString()
                }

                val finalAiText = accumulated.toString().trim()
                if (finalAiText.isNotEmpty()) {
                    repository.addMessage(
                        userId = currentUserId,
                        conversationId = conversationId,
                        role = "model",
                        content = finalAiText,
                        toolUsed = "chat"
                    )
                }
                isStreaming = false
                streamingResponse = ""
            }
        }
    }

    Scaffold(
        topBar = {
            SamsedHeader(
                title = "SAMSED AI Chat",
                subtitle = "Gemini 3.8 Flash • Zero-Latency",
                onMenuClick = onMenuClick,
                trailingAction = {
                    IconButton(
                        onClick = onOpenVoice,
                        modifier = Modifier.testTag("voice_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Assistant",
                            tint = SamsedCyan
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Messages Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && streamingResponse.isEmpty() && !isStreaming) {
                    EmptyChatGreeting(onPromptSelected = { sendMessage(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(messages, key = { index, msg -> if (msg.id.isNotBlank()) msg.id else "msg_$index" }) { _, msg ->
                            MessageBubble(
                                message = msg,
                                onSpeak = { onSpeakMessage(msg.content) }
                            )
                        }

                        if (streamingResponse.isNotEmpty()) {
                            item(key = "streaming_response") {
                                StreamingBubble(text = streamingResponse)
                            }
                        } else if (isStreaming) {
                            item(key = "loading_indicator") {
                                ThinkingIndicator()
                            }
                        }
                    }
                }
            }

            // Attached Image Thumbnail Preview
            if (selectedBitmap != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Attached photo",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, SamsedCyan, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Image Attached",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Photo Analyzer ready for prompt",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = {
                            selectedBitmap = null
                            selectedImageUri = null
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove photo", tint = SamsedRose)
                        }
                    }
                }
            }

            // Quick Prompt Starter Chips
            if (messages.size <= 2) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        "🔍 Research latest AI trends",
                        "💻 Code a Kotlin coroutine flow",
                        "✍️ Draft an executive email",
                        "🧮 Explain quantum computing"
                    )
                    items(chips) { chip ->
                        SuggestionChip(
                            onClick = { sendMessage(chip.substring(3)) },
                            label = { Text(chip, fontSize = 12.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo Picker Button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach Photo",
                            tint = if (selectedBitmap != null) SamsedCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Text Input Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (selectedBitmap != null) "Ask about this photo..." else "Message SAMSED AI...",
                                fontSize = 14.sp
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamsedCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(inputText) }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("chat_input_field"),
                        maxLines = 4
                    )

                    // Voice / Send Action
                    if (inputText.isBlank() && selectedBitmap == null) {
                        IconButton(
                            onClick = onOpenVoice,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SamsedCyan.copy(alpha = 0.15f))
                                .testTag("mic_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Mode",
                                tint = SamsedCyan
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { sendMessage(inputText) },
                            enabled = !isStreaming,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SamsedCyan)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatGreeting(
    onPromptSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SamsedCyan.copy(alpha = 0.15f))
                .border(1.5.dp, SamsedCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = SamsedCyan,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What can I help you with today?",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ask questions, analyze photos, draft code, or explore ideas with SAMSED AI.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        val prompts = listOf(
            "Write a clean Kotlin Room database implementation",
            "Explain the difference between SQL and NoSQL in depth",
            "Help me prepare for an Android developer interview",
            "Summarize best practices for clean software architecture"
        )

        prompts.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onPromptSelected(prompt) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = SamsedCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit
) {
    val context = LocalContext.current
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SamsedCyan.copy(alpha = 0.2f))
                    .border(1.dp, SamsedCyan.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "SAMSED AI",
                    tint = SamsedCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) ObsidianUserBubble else MaterialTheme.colorScheme.surfaceVariant,
                border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (message.hasImage) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = SamsedCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Attached Photo Analysis", fontSize = 11.sp, color = SamsedCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Render code blocks or formatted text
                    RenderFormattedContent(message.content)
                }
            }

            // Message action tools for AI responses
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SAMSED AI Response", message.content))
                            Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Read aloud", tint = TextMuted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RenderFormattedContent(content: String) {
    // Check if contains code fence ```
    if (content.contains("```")) {
        val parts = content.split("```")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Code block
                val lines = part.trim().lines()
                val lang = lines.firstOrNull()?.trim() ?: "code"
                val codeBody = if (lines.size > 1) lines.drop(1).joinToString("\n") else part
                CodeBlockView(code = codeBody, language = lang)
            } else if (part.isNotBlank()) {
                Text(
                    text = part.trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                )
            }
        }
    } else {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        )
    }
}

@Composable
fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SamsedCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SamsedCyan, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, SamsedCyan.copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                RenderFormattedContent(text)
                Spacer(modifier = Modifier.height(4.dp))
                // Glowing typing cursor indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SamsedCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generating...", fontSize = 10.sp, color = SamsedCyan)
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SamsedCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SamsedCyan, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = SamsedCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SAMSED AI is thinking...",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
    }
}
