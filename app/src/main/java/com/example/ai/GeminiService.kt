package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiService"

class GeminiService {

    private fun getModel(modelName: String = "gemini-3.8-flash", systemInstruction: String? = null) =
        Firebase.ai.generativeModel(
            modelName = modelName,
            systemInstruction = systemInstruction?.let { content { text(it) } }
        )

    /**
     * Stream response chunks for general chat conversations.
     */
    fun streamChat(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        modelName: String = "gemini-3.8-flash",
        customSystemInstruction: String = "You are SAMSED AI, a state-of-the-art intelligent assistant inspired by ChatGPT. You provide precise, insightful, beautifully formatted, and helpful answers across coding, writing, reasoning, and analysis. Use markdown formatting with code blocks, bold text, and bullet points where helpful."
    ): Flow<String> = flow {
        val model = getModel(modelName, customSystemInstruction)
        try {
            // Build chat session with prior conversation history
            val formattedHistory = history.map { (role, msg) ->
                val author = if (role == "user") "user" else "model"
                content(author) { text(msg) }
            }
            val chat = model.startChat(history = formattedHistory)

            withTimeout(60_000L) {
                chat.sendMessageStream(prompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat streaming timed out", e)
            emit("\n\n*[Request timed out after 60s. Please try again.]*")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat generation failed", e)
            emit("\n\n*[Error: ${e.localizedMessage ?: "Failed to generate AI response"}]*")
        }
    }

    /**
     * Vision / Photo Analyzer using Gemini multimodal capabilities.
     */
    suspend fun analyzeImage(
        bitmap: Bitmap,
        userPrompt: String,
        modelName: String = "gemini-3.8-flash"
    ): Result<String> = runCatching {
        val model = getModel(
            modelName = modelName,
            systemInstruction = "You are SAMSED AI Vision Analyzer. Inspect user photos thoroughly. Identify objects, solve math problems, transcribe handwritten notes, explain diagrams, or decode product labels with crystal clarity."
        )
        val promptText = userPrompt.ifBlank { "Thoroughly analyze this image and explain what is visible, highlighting key details, text, and insights." }

        withTimeout(60_000L) {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(promptText)
                }
            )
            response.text ?: throw IllegalStateException("Model returned empty text.")
        }
    }

    /**
     * Smart Internet Search AI assistant. Formats web query, simulates real-time search synthesis,
     * and returns structured citations and key facts.
     */
    fun streamSearch(
        query: String,
        modelName: String = "gemini-3.8-flash"
    ): Flow<String> = flow {
        val searchInstruction = """
            You are SAMSED AI Search, an advanced web-grounded research assistant. 
            When given a query, synthesize a deep, factual, up-to-date answer.
            Structure your response with:
            1. **Direct Answer / Overview**
            2. **Key Insights & Current Developments**
            3. **Verified Sources & Citations** (list reputable publication domains like reuters.com, arxiv.org, nature.com, github.com where applicable).
        """.trimIndent()

        val model = getModel(modelName, searchInstruction)
        try {
            withTimeout(60_000L) {
                model.generateContentStream("Search query: $query").collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            emit("\n\n*[Search timed out. Please retry.]*")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit("\n\n*[Search error: ${e.localizedMessage}]*")
        }
    }

    /**
     * AI Writing Assistant with tailored tone and format.
     */
    fun streamWritingAssistant(
        format: String,
        tone: String,
        instructions: String,
        modelName: String = "gemini-3.8-flash"
    ): Flow<String> = flow {
        val systemInstruction = """
            You are SAMSED AI Writing Assistant. You craft exceptional, publication-ready text.
            Target Format: $format
            Tone: $tone
            Follow the user's instructions meticulously, producing polished, coherent, and engaging prose.
        """.trimIndent()

        val model = getModel(modelName, systemInstruction)
        try {
            withTimeout(60_000L) {
                model.generateContentStream(instructions).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            emit("\n\n*[Writing generation timed out.]*")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit("\n\n*[Writing error: ${e.localizedMessage}]*")
        }
    }

    /**
     * Study Help & Homework Solver with step-by-step reasoning.
     */
    fun streamStudyHelper(
        subject: String,
        question: String,
        modelName: String = "gemini-3.8-flash"
    ): Flow<String> = flow {
        val systemInstruction = """
            You are SAMSED AI Study Tutor & Academic Mentor.
            Subject: $subject
            Provide pedagogical, step-by-step explanations rather than simply handing over the final answer.
            Include:
            - **Core Concept Explained**
            - **Step-by-Step Derivation / Solution**
            - **Common Pitfalls to Avoid**
            - **Practice Question & Self-Check**
        """.trimIndent()

        val model = getModel(modelName, systemInstruction)
        try {
            withTimeout(60_000L) {
                model.generateContentStream(question).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            emit("\n\n*[Study solver timed out.]*")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit("\n\n*[Tutor error: ${e.localizedMessage}]*")
        }
    }

    /**
     * AI Image Prompt Enhancer & Concept Generator.
     * Takes user's raw prompt and expands it into an ultra-detailed artistic prompt and concept breakdown.
     */
    suspend fun expandImagePrompt(
        rawPrompt: String,
        style: String,
        aspectRatio: String,
        modelName: String = "gemini-3.8-flash"
    ): Result<String> = runCatching {
        val systemInstruction = """
            You are SAMSED AI Creative Art Director.
            Expand the user's brief into an ultra-detailed generative image prompt.
            Style: $style
            Aspect Ratio: $aspectRatio
            Return a rich visual description specifying lighting, camera angle, textures, atmospheric mood, and fine details.
        """.trimIndent()

        val model = getModel(modelName, systemInstruction)
        withTimeout(30_000L) {
            val response = model.generateContent(rawPrompt)
            response.text ?: rawPrompt
        }
    }
}
