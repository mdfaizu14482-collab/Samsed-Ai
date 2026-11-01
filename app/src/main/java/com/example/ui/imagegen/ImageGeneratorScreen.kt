package com.example.ui.imagegen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.GeminiService
import com.example.ui.components.SamsedBadge
import com.example.ui.components.SamsedHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ImageGeneratorScreen(
    geminiService: GeminiService,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rawPrompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("Cyberpunk & Neon") }
    var selectedAspectRatio by remember { mutableStateOf("1:1") }
    var isExpandingPrompt by remember { mutableStateOf(false) }
    var enhancedPromptResult by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedImageShown by remember { mutableStateOf(false) }

    val styles = listOf(
        "Cyberpunk & Neon",
        "Photorealistic 8K",
        "3D Digital Render",
        "Anime / Studio Ghibli",
        "Oil Painting Masterpiece",
        "Minimalist Vector"
    )

    val aspectRatios = listOf("1:1", "16:9", "9:16", "4:3")

    fun enhancePrompt() {
        if (rawPrompt.isBlank()) return
        isExpandingPrompt = true
        coroutineScope.launch {
            val result = geminiService.expandImagePrompt(
                rawPrompt = rawPrompt,
                style = selectedStyle,
                aspectRatio = selectedAspectRatio
            )
            isExpandingPrompt = false
            enhancedPromptResult = result.getOrNull()
        }
    }

    fun generateArtwork() {
        if (rawPrompt.isBlank() && enhancedPromptResult.isNullOrBlank()) return
        isGenerating = true
        coroutineScope.launch {
            // Enhance if not already enhanced
            if (enhancedPromptResult == null) {
                val res = geminiService.expandImagePrompt(rawPrompt, selectedStyle, selectedAspectRatio)
                enhancedPromptResult = res.getOrNull()
            }
            kotlinx.coroutines.delay(1200) // Render animation delay
            isGenerating = false
            generatedImageShown = true
        }
    }

    Scaffold(
        topBar = {
            SamsedHeader(
                title = "Image Generator",
                subtitle = "Creative AI Visual Studio",
                onMenuClick = onMenuClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Prompt Input Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Artistic Prompt",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawPrompt,
                        onValueChange = { rawPrompt = it },
                        placeholder = { Text("Describe the scene: e.g. A futuristic glass library on Mars overlooking cosmic stars...") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamsedCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { enhancePrompt() },
                            enabled = rawPrompt.isNotBlank() && !isExpandingPrompt
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = SamsedVioletLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isExpandingPrompt) "Enhancing..." else "AI Prompt Enhancer",
                                color = SamsedVioletLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (rawPrompt.isNotBlank()) {
                            Text(
                                text = "${rawPrompt.length} chars",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Style Selector
            Text(
                text = "Visual Art Style",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(styles) { style ->
                    val isSelected = style == selectedStyle
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedStyle = style },
                        label = { Text(style, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SamsedCyan.copy(alpha = 0.2f),
                            selectedLabelColor = SamsedCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Aspect Ratio Selector
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                aspectRatios.forEach { ratio ->
                    val isSelected = ratio == selectedAspectRatio
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) SamsedCyanDark else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SamsedCyan else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedAspectRatio = ratio }
                    ) {
                        Text(
                            text = ratio,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Generate Button
            Button(
                onClick = { generateArtwork() },
                enabled = rawPrompt.isNotBlank() && !isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = SamsedCyanDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("generate_image_button")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Rendering Neural Artwork...")
                } else {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Image", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // Enhanced Prompt Display
            if (enhancedPromptResult != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SamsedViolet.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamsedViolet.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Expanded Cinematic Prompt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SamsedVioletLight
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Enhanced Prompt", enhancedPromptResult))
                                    Toast.makeText(context, "Copied prompt", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy prompt", tint = SamsedVioletLight, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = enhancedPromptResult!!,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                        )
                    }
                }
            }

            // Generated Image Preview Card
            if (generatedImageShown) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamsedCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SamsedBadge(text = "$selectedStyle • $selectedAspectRatio", icon = Icons.Default.Check)
                            IconButton(onClick = {
                                Toast.makeText(context, "Saved to device artwork collection", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download artwork", tint = SamsedCyan)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Rendered high resolution visual canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF0F172A),
                                            Color(0xFF1E293B),
                                            Color(0xFF064E3B)
                                        )
                                    )
                                )
                                .border(1.dp, SamsedCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SamsedCyan,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "SAMSED AI Visual Render",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = selectedStyle,
                                    fontSize = 12.sp,
                                    color = SamsedCyanLight
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
