package com.example.evokeraa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvokerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<Message>)

    @Query("SELECT * FROM contacts ORDER BY isPinned DESC, messageCount DESC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 100")
    suspend fun searchMessages(query: String): List<Message>

    @Query("SELECT * FROM messages WHERE chatId IN (:chatIds) ORDER BY timestamp ASC")
    fun getMessagesForIds(chatIds: List<String>): Flow<List<Message>>

    @Query("SELECT tags FROM contacts WHERE tags != ''")
    suspend fun getAllTags(): List<String>

    // --- STATISTICS QUERIES ---
    @Query("SELECT COUNT(*) FROM messages")
    fun getTotalMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts")
    fun getTotalContactCount(): Flow<Int>

    @Query("SELECT * FROM contacts ORDER BY messageCount DESC LIMIT 5")
    fun getTop5Contacts(): Flow<List<Contact>>

    // Updates
    @Query("UPDATE contacts SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: String, tags: String)

    @Query("UPDATE contacts SET nickname = :nickname WHERE id = :id")
    suspend fun updateNickname(id: String, nickname: String)

    @Query("UPDATE contacts SET isHidden = :isHidden WHERE id = :id")
    suspend fun setHidden(id: String, isHidden: Boolean)

    @Query("UPDATE contacts SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("UPDATE contacts SET mergedWith = :mergedIds WHERE id = :targetId")
    suspend fun linkContacts(targetId: String, mergedIds: String)
}