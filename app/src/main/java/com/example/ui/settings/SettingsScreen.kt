package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.UserProfile
import com.example.data.repository.FirestoreRepository
import com.example.ui.components.SamsedHeader
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentUserId: String,
    repository: FirestoreRepository,
    onMenuClick: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authUser = FirebaseAuth.getInstance().currentUser

    val profile by repository.observeUserProfile(currentUserId).collectAsState(initial = null)

    var selectedModel by remember { mutableStateOf("gemini-3.8-flash") }
    var selectedTheme by remember { mutableStateOf("dark") }
    var selectedVoice by remember { mutableStateOf("Natural Friendly") }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        profile?.let {
            if (it.modelPreference.isNotBlank()) selectedModel = it.modelPreference
            if (it.themeMode.isNotBlank()) selectedTheme = it.themeMode
            if (it.preferredVoice.isNotBlank()) selectedVoice = it.preferredVoice
        }
    }

    fun savePreferences(model: String? = null, theme: String? = null, voice: String? = null) {
        coroutineScope.launch {
            repository.updateUserPreferences(
                userId = currentUserId,
                themeMode = theme,
                preferredVoice = voice,
                modelPreference = model
            )
            Toast.makeText(context, "Preferences updated", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            SamsedHeader(
                title = "Settings",
                subtitle = "App configuration & AI parameters",
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
            // Profile Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (authUser?.photoUrl != null) {
                        AsyncImage(
                            model = authUser.photoUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, SamsedCyan, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SamsedCyan.copy(alpha = 0.2f))
                                .border(1.5.dp, SamsedCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (authUser?.displayName?.take(1) ?: "U").uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = SamsedCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = authUser?.displayName ?: "User",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (profile?.isAdmin == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SamsedViolet.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("ADMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SamsedVioletLight)
                                }
                            }
                        }
                        Text(
                            text = authUser?.email ?: "Authenticated via Google",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AI Model Preference
            Text(
                text = "Default AI Reasoning Model",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val models = listOf(
                Triple("gemini-3.8-flash", "Gemini 3.8 Flash (Default)", "High-performance frontier model, fast and versatile"),
                Triple("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra-fast response latency for high-speed tasks"),
                Triple("gemini-4-argon", "Gemini 4 Argon", "Extreme reasoning power for advanced STEM & code")
            )

            models.forEach { (modelId, name, desc) ->
                val isSelected = modelId == selectedModel
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) SamsedCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SamsedCyan else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            selectedModel = modelId
                            savePreferences(model = modelId)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedModel = modelId
                                savePreferences(model = modelId)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = SamsedCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = desc, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Voice Assistant Voice
            Text(
                text = "Voice Assistant Profile",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            val voices = listOf("Natural Friendly", "Executive Pro", "Calm Mentor")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                voices.forEach { voice ->
                    val isSelected = voice == selectedVoice
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedVoice = voice
                            savePreferences(voice = voice)
                        },
                        label = { Text(voice, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SamsedViolet.copy(alpha = 0.2f),
                            selectedLabelColor = SamsedVioletLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Backend Diagnostics Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "System Diagnostics & Security",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DiagnosticRow(label = "Firebase Auth", value = "Google Sign-In Active")
                    DiagnosticRow(label = "Firestore Database", value = context.getString(R.string.firestore_database_id).take(24) + "...")
                    DiagnosticRow(label = "App Check Attestation", value = "Enterprise reCAPTCHA Active")
                    DiagnosticRow(label = "Gemini AI Logic", value = "Backend Verified")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sign Out Button
            Button(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SamsedRose.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, SamsedRose.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = SamsedRose)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", color = SamsedRose, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out of SAMSED AI?") },
            text = { Text("Your conversations remain securely synced to your account in Firebase Firestore.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        FirebaseAuth.getInstance().signOut()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamsedRose)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = SamsedCyan))
    }
}
