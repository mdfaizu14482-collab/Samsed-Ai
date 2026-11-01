package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiService
import com.example.data.models.WritingFormat
import com.example.data.models.WritingTone
import com.example.ui.chat.RenderFormattedContent
import com.example.ui.components.SamsedHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun WritingAssistantScreen(
    geminiService: GeminiService,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(WritingFormat.EMAIL) }
    var selectedTone by remember { mutableStateOf(WritingTone.PROFESSIONAL) }
    var instructionsText by remember { mutableStateOf("") }
    var generatedContent by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    fun generateWriting() {
        if (instructionsText.isBlank()) return
        isGenerating = true
        generatedContent = ""

        coroutineScope.launch {
            val accumulated = StringBuilder()
            geminiService.streamWritingAssistant(
                format = selectedFormat.label,
                tone = selectedTone.label,
                instructions = instructionsText
            ).collect { chunk ->
                accumulated.append(chunk)
                generatedContent = accumulated.toString()
            }
            isGenerating = false
        }
    }

    Scaffold(
        topBar = {
            SamsedHeader(
                title = "AI Writing Assistant",
                subtitle = "Craft polished documents, emails & code",
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
                .padding(16.dp)
        ) {
            // Format Selector Chips
            Text(
                text = "Writing Format",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(WritingFormat.entries) { format ->
                    val isSelected = format == selectedFormat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = format },
                        label = { Text(format.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SamsedCyan.copy(alpha = 0.2f),
                            selectedLabelColor = SamsedCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tone Selector Chips
            Text(
                text = "Tone & Voice",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(WritingTone.entries) { tone ->
                    val isSelected = tone == selectedTone
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTone = tone },
                        label = { Text(tone.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SamsedViolet.copy(alpha = 0.2f),
                            selectedLabelColor = SamsedVioletLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions Input
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Brief or Key Points",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = instructionsText,
                        onValueChange = { instructionsText = it },
                        placeholder = { Text(selectedFormat.placeholder) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamsedCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { generateWriting() },
                        enabled = instructionsText.isNotBlank() && !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = SamsedCyanDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_writing_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Writing Draft...")
                        } else {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate ${selectedFormat.label}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Generated Output Area
            if (generatedContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SamsedCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Generated Draft",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                val words = generatedContent.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                                Text(
                                    text = "$words words • ${generatedContent.length} characters",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Draft", generatedContent))
                                Toast.makeText(context, "Copied draft to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy draft", tint = SamsedCyan)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
                        RenderFormattedContent(generatedContent)
                    }
                }
            }
        }
    }
}
