package com.example.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isAdmin: Boolean = false,
    val themeMode: String = "dark",
    val preferredVoice: String = "Natural Friendly",
    val modelPreference: String = "gemini-3.8-flash",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val mode: String = "chat",
    val lastMessage: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val userId: String = "",
    val role: String = "user", // "user", "model", "system"
    val content: String = "",
    val imageUrl: String = "",
    val hasImage: Boolean = false,
    val toolUsed: String = "", // "search", "vision", "generate_image", "writing", "study"
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class AiFeatureMode(val key: String, val label: String, val description: String) {
    CHAT("chat", "AI Chat", "Deep conversational intelligence with real-time reasoning"),
    VOICE("voice", "Voice Assistant", "Hands-free continuous voice interaction with speech synthesis"),
    VISION("vision", "Photo Analyzer", "Inspect photos, solve handwritten problems, and describe scenes"),
    IMAGE_GEN("image_gen", "Image Generator", "Generate creative visual artwork from text prompts"),
    SEARCH("search", "Smart Search", "Grounded internet knowledge and structured answers"),
    WRITING("writing", "AI Writer", "Draft emails, essays, stories, resumes, and code"),
    STUDY("study", "Study Helper", "Step-by-step homework solver, explanations, and flashcards"),
    ADMIN("admin", "Admin Panel", "System health, usage analytics, models, and audit logs")
}

enum class WritingTone(val label: String) {
    PROFESSIONAL("Professional"),
    CREATIVE("Creative"),
    CASUAL("Casual"),
    ACADEMIC("Academic"),
    CONCISE("Concise")
}

enum class WritingFormat(val label: String, val placeholder: String) {
    EMAIL("Email", "Draft a polite follow-up email about the quarterly budget..."),
    ESSAY("Essay", "Write an analytical essay on renewable energy adoption..."),
    CODE("Code & Script", "Create a Python script to automate file backups..."),
    SUMMARY("Executive Summary", "Summarize the key points of the attached document..."),
    BLOG("Blog Post", "Write an engaging blog post about modern AI advancements..."),
    REWRITE("Rewrite & Polish", "Rewrite this paragraph to make it more compelling...")
}

enum class StudySubject(val label: String, val promptHint: String) {
    MATHEMATICS("Math", "Solve: 3x^2 - 12x + 9 = 0 step by step"),
    SCIENCE("Science", "Explain how CRISPR gene editing works in plain terms"),
    CODING("Programming", "Explain recursion with a clear binary tree traversal example"),
    HISTORY("History", "Compare the causes of the Industrial Revolution in Europe"),
    LANGUAGES("Languages", "Translate and explain grammatical nuances for this phrase")
}
