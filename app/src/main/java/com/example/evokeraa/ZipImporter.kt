package com.example.evokeraa

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class ZipImporter(private val db: AppDatabase) {

    private fun fixEncoding(input: String): String {
        return try {
            String(input.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        } catch (e: Exception) { input }
    }

    suspend fun importData(
        context: Context,
        uri: Uri,
        myAliases: List<String>,
        platform: String,
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val type = contentResolver.getType(uri) ?: ""
            val fileName = uri.path ?: "unknown"

            if (type.contains("zip") || fileName.endsWith(".zip")) {
                importZipStream(context, uri, myAliases, platform, onProgress)
            } else if (type.contains("json") || fileName.endsWith(".json")) {
                importSingleJson(context, uri, myAliases, platform, onProgress)
            }
        } catch (e: Exception) {
            Log.e("EvokerImport", "Error", e)
            onProgress("Error: ${e.message}")
        }
    }

    private suspend fun importZipStream(context: Context, uri: Uri, myAliases: List<String>, platform: String, onProgress: (String) -> Unit) {
        val stream = context.contentResolver.openInputStream(uri) ?: return
        val zipStream = ZipInputStream(stream)
        var entry = zipStream.nextEntry

        // GLOBAL COUNTER
        var totalMessagesProcessed = 0

        while (entry != null) {
            val name = entry.name.lowercase()
            if (name.endsWith(".json")) {
                val pathParts = entry.name.split("/")
                // Instagram Logic
                if (pathParts.size >= 2) {
                    val fileName = pathParts.last()
                    val folderName = pathParts[pathParts.size - 2]
                    if (fileName.startsWith("message") && folderName.contains("_")) {
                        val username = folderName.substringBefore("_")
                        onProgress("Sorting Archive...\nImported: $totalMessagesProcessed\nCurrent: $username")

                        // Pass the counter by reference-ish logic (return the new count)
                        totalMessagesProcessed += parseInstagramEntry(zipStream, username, myAliases)
                    }
                }
            }
            if (name.contains("chat_history.json")) {
                onProgress("Found Snapchat History...")
                totalMessagesProcessed += parseSnapchatJson(JsonReader(InputStreamReader(zipStream, "UTF-8")), myAliases, onProgress, totalMessagesProcessed)
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()
        onProgress("Done! Processed $totalMessagesProcessed messages.")
    }

    private suspend fun importSingleJson(context: Context, uri: Uri, myAliases: List<String>, platform: String, onProgress: (String) -> Unit) {
        val stream = context.contentResolver.openInputStream(uri) ?: return
        val reader = JsonReader(InputStreamReader(stream, "UTF-8"))
        onProgress("Reading JSON...")
        val count = parseSnapchatJson(reader, myAliases, onProgress, 0)
        onProgress("Done! Processed $count messages.")
    }

    private suspend fun parseInstagramEntry(readerStream: Any, username: String, myAliases: List<String>): Int {
        val reader = JsonReader(InputStreamReader(readerStream as java.io.InputStream, "UTF-8"))
        val messages = mutableListOf<Message>()
        var displayName = username
        var count = 0
        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "participants") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "name") {
                                val pName = reader.nextString()
                                if (myAliases.none { it.equals(pName, true) }) displayName = fixEncoding(pName)
                            } else { reader.skipValue() }
                        }
                        reader.endObject()
                    }
                    reader.endArray()
                } else if (name == "messages") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val msg = parseSingleInstagramMessage(reader, username, myAliases)
                        if (msg != null) {
                            messages.add(msg)
                            count++
                        }
                    }
                    reader.endArray()
                } else { reader.skipValue() }
            }
            reader.endObject()
            if (messages.isNotEmpty()) {
                db.dao().insertContact(Contact(id = username, displayName = displayName, messageCount = messages.size, platforms = "Instagram"))
                db.dao().insertMessages(messages)
            }
        } catch (e: Exception) { Log.e("EvokerImport", "Fail IG", e) }
        return count
    }

    private fun parseSingleInstagramMessage(reader: JsonReader, chatId: String, myAliases: List<String>): Message? {
        var sender = ""
        var time: Long = 0
        var content = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "sender_name" -> sender = reader.nextString()
                "timestamp_ms" -> time = reader.nextLong()
                "content" -> { try { content = reader.nextString() } catch (e: Exception) { content = "[Media]" } }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (content.isEmpty() && sender.isNotEmpty()) return null

        val isMe = myAliases.any { it.equals(sender, true) }

        return Message(
            chatId = chatId,
            senderName = fixEncoding(sender),
            content = fixEncoding(content),
            timestamp = time,
            platform = "instagram",
            isFromMe = isMe
        )
    }

    private suspend fun parseSnapchatJson(reader: JsonReader, myAliases: List<String>, onProgress: (String) -> Unit, startCount: Int): Int {
        var localCount = 0
        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val friendUsername = reader.nextName()
                val messages = mutableListOf<Message>()
                reader.beginArray()
                while (reader.hasNext()) {
                    val msg = parseSingleSnapchatMessage(reader, friendUsername, myAliases)
                    if (msg != null) {
                        messages.add(msg)
                        localCount++
                    }
                    // Update UI every 100 messages for smoothness
                    if (localCount % 100 == 0) {
                        onProgress("Parsing Snapchat...\nImported: ${startCount + localCount}")
                    }
                }
                reader.endArray()
                if (messages.isNotEmpty()) {
                    db.dao().insertContact(Contact(id = friendUsername, displayName = friendUsername, messageCount = messages.size, platforms = "Snapchat"))
                    db.dao().insertMessages(messages)
                }
            }
            reader.endObject()
        } catch (e: Exception) { Log.e("EvokerImport", "Snap Fail", e) }
        return localCount
    }

    private fun parseSingleSnapchatMessage(reader: JsonReader, friendUsername: String, myAliases: List<String>): Message? {
        var sender = friendUsername
        var time: Long = 0
        var content: String? = null
        var isSender = false
        var mediaType = "TEXT"
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "From" -> reader.nextString()
                "Media Type" -> mediaType = reader.nextString()
                "Content" -> { try { content = reader.nextString() } catch (e: Exception) { reader.skipValue() } }
                "Created(microseconds)" -> {
                    val raw = reader.nextLong()
                    time = if (raw > 10_000_000_000_000L) raw / 1000 else raw
                }
                "IsSender" -> isSender = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val myDisplayName = if(myAliases.isNotEmpty()) myAliases[0] else "Me"
        sender = if (isSender) myDisplayName else friendUsername

        if (content == null) content = if (mediaType == "TEXT") "[Unsaved Chat]" else "[$mediaType]"

        return Message(
            chatId = friendUsername,
            senderName = sender,
            content = content ?: "",
            timestamp = time,
            platform = "snapchat",
            isFromMe = isSender
        )
    }
}