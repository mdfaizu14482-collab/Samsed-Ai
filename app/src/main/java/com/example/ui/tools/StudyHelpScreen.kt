package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import com.example.data.models.StudySubject
import com.example.ui.chat.RenderFormattedContent
import com.example.ui.components.SamsedHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun StudyHelpScreen(
    geminiService: GeminiService,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedSubject by remember { mutableStateOf(StudySubject.MATHEMATICS) }
    var questionText by remember { mutableStateOf("") }
    var tutorExplanation by remember { mutableStateOf("") }
    var isSolving by remember { mutableStateOf(false) }

    fun solveProblem() {
        if (questionText.isBlank()) return
        isSolving = true
        tutorExplanation = ""

        coroutineScope.launch {
            val accumulated = StringBuilder()
            geminiService.streamStudyHelper(
                subject = selectedSubject.label,
                question = questionText
            ).collect { chunk ->
                accumulated.append(chunk)
                tutorExplanation = accumulated.toString()
            }
            isSolving = false
        }
    }

    Scaffold(
        topBar = {
            SamsedHeader(
                title = "Study Help & Tutor",
                subtitle = "Step-by-step reasoning & explanations",
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
            // Subject Categories
            Text(
                text = "Academic Discipline",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StudySubject.entries) { subject ->
                    val isSelected = subject == selectedSubject
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSubject = subject },
                        label = { Text(subject.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SamsedCyan.copy(alpha = 0.2f),
                            selectedLabelColor = SamsedCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Problem Input Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Homework Problem or Concept",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        placeholder = { Text(selectedSubject.promptHint) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SamsedCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { solveProblem() },
                        enabled = questionText.isNotBlank() && !isSolving,
                        colors = ButtonDefaults.buttonColors(containerColor = SamsedCyanDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("solve_problem_button")
                    ) {
                        if (isSolving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Formulating Explanation...")
                        } else {
                            Icon(Icons.Default.School, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Solve & Explain Step-by-Step", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tutor Solution Output
            if (tutorExplanation.isNotBlank()) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SamsedEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Step-by-Step Breakdown",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Study Solution", tutorExplanation))
                                Toast.makeText(context, "Copied solution", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy solution", tint = SamsedCyan)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
                        RenderFormattedContent(tutorExplanation)
                    }
                }
            }
        }
    }
}
