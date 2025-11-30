package com.example.evokeraa
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Contact::class, Message::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EvokerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "evoker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}