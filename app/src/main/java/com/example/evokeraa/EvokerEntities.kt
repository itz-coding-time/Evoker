package com.example.evokeraa
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val displayName: String,
    val nickname: String? = null,
    val messageCount: Int = 0,
    val platforms: String = "",
    val tags: String = "",
    val isHidden: Boolean = false,
    val isPinned: Boolean = false,
    val mergedWith: String = "" // Stores "id1,id2" of linked accounts
)

@Entity(
    tableName = "messages",
    // UNIQUE INDEX: Prevents duplicates automatically
    indices = [
        Index(value = ["chatId", "timestamp", "senderName"], unique = true),
        Index(value = ["chatId"]),
        Index(value = ["timestamp"])
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val platform: String,
    val isFromMe: Boolean
)