package com.photoeditor.data.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 编辑历史数据访问对象
 */
@Dao
interface EditHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: EditHistory): Long

    @Update
    suspend fun update(history: EditHistory)

    @Delete
    suspend fun delete(history: EditHistory)

    @Query("DELETE FROM edit_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM edit_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM edit_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<EditHistory>>

    @Query("SELECT * FROM edit_history ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<EditHistory>>

    @Query("SELECT * FROM edit_history WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteHistory(): Flow<List<EditHistory>>

    @Query("SELECT * FROM edit_history WHERE originalPhotoId = :photoId ORDER BY createdAt DESC")
    fun getHistoryByPhotoId(photoId: String): Flow<List<EditHistory>>

    @Query("SELECT * FROM edit_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): EditHistory?

    @Query("SELECT * FROM edit_history WHERE description LIKE :query OR appliedFilterId LIKE :query")
    fun searchHistory(query: String): Flow<List<EditHistory>>

    @Query("SELECT COUNT(*) FROM edit_history")
    suspend fun getCount(): Int

    @Query("UPDATE edit_history SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT * FROM edit_history WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun getHistoryByTimeRange(startTime: Long, endTime: Long): Flow<List<EditHistory>>

    @Query("DELETE FROM edit_history WHERE createdAt < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)
}
