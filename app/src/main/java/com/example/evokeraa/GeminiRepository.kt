package com.example.evokeraa

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiRepository(private val apiKey: String) {

    suspend fun generatePersona(chatLog: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Error: API Key is missing. Please add it in Settings."

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // The System Prompt (Adapted from your web version)
            val systemPrompt = """
                You are a psychologist and character writer. Analyze the following chat transcript. 
                Create a 'Character Card' for the person who is NOT 'Me'. Include:
                1. **Persona Snapshot**: A one-sentence summary.
                2. **Key Personality Traits**: 5 distinct traits with evidence.
                3. **Core Wounds**: What deep insecurity drives them?
                4. **Coping Mechanisms**: How do they handle stress?
                5. **Writing Style**: Dialect, slang, punctuation, emoji usage.
                
                Be deep, specific, and psychological. Output in Markdown.
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", "$systemPrompt\n\nTRANSCRIPT:\n$chatLog")
                    ))
                ))
            }

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody.toString())
            writer.flush()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                // Extract text from: candidates[0].content.parts[0].text
                return@withContext jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            } else {
                return@withContext "Error: Gemini API returned code $responseCode"
            }

        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error generating persona", e)
            return@withContext "Error: ${e.localizedMessage}"
        }
    }
}