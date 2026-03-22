package com.photoeditor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.photoeditor.data.model.EditHistory
import com.photoeditor.data.model.EditHistoryConverters
import com.photoeditor.data.model.EditHistoryDao

/**
 * 应用数据库
 */
@Database(
    entities = [EditHistory::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(EditHistoryConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun editHistoryDao(): EditHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "photo_editor_database.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
