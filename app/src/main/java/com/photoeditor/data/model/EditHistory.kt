package com.photoeditor.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * 编辑历史实体 - Room数据库表
 */
@Entity(tableName = "edit_history")
@TypeConverters(EditHistoryConverters::class)
data class EditHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 原始照片信息
    val originalPhotoId: String,
    val originalPhotoUri: String,
    val originalPhotoName: String,
    
    // 编辑后的照片
    val editedPhotoUri: String? = null,
    
    // 编辑操作记录
    val operations: List<EditOperation> = emptyList(),
    
    // 应用的滤镜
    val appliedFilterId: String? = null,
    val filterIntensity: Float = 1.0f,
    
    // AI功能使用记录
    val aiFeaturesUsed: List<String> = emptyList(),
    
    // 编辑参数JSON
    val editParamsJson: String? = null,
    
    // 时间戳
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // 是否收藏
    val isFavorite: Boolean = false,
    
    // 编辑描述/备注
    val description: String? = null
) {
    /**
     * 获取创建时间
     */
    fun getCreatedDate(): Date = Date(createdAt)
    
    /**
     * 获取更新时间
     */
    fun getUpdatedDate(): Date = Date(updatedAt)
    
    /**
     * 获取原始照片URI
     */
    fun getOriginalUri(): Uri = Uri.parse(originalPhotoUri)
    
    /**
     * 获取编辑后照片URI
     */
    fun getEditedUri(): Uri? = editedPhotoUri?.let { Uri.parse(it) }
}

/**
 * 编辑操作类型
 */
enum class EditOperationType {
    FILTER,             // 滤镜
    ADJUST_BRIGHTNESS,  // 调整亮度
    ADJUST_CONTRAST,    // 调整对比度
    ADJUST_SATURATION,  // 调整饱和度
    CROP,               // 裁剪
    ROTATE,             // 旋转
    FLIP,               // 翻转
    AI_BEAUTY,          // AI美颜
    AI_REMOVE_OBJECT,   // AI去物体
    AI_SKY_REPLACE,     // AI换天空
    AI_ENHANCE,         // AI增强
    WATERMARK,          // 水印
    DRAW,               // 涂鸦
    TEXT,               // 文字
    STICKER,            // 贴纸
    MOSAIC,             // 马赛克
    BLUR,               // 模糊
    UNDO,               // 撤销
    REDO                // 重做
}

/**
 * 单次编辑操作
 */
data class EditOperation(
    val type: EditOperationType,
    val timestamp: Long = System.currentTimeMillis(),
    val params: Map<String, Any> = emptyMap(),
    val beforeState: String? = null,  // 操作前的状态快照
    val afterState: String? = null    // 操作后的状态快照
) {
    /**
     * 获取操作名称
     */
    fun getOperationName(): String {
        return when (type) {
            EditOperationType.FILTER -> "应用滤镜"
            EditOperationType.ADJUST_BRIGHTNESS -> "调整亮度"
            EditOperationType.ADJUST_CONTRAST -> "调整对比度"
            EditOperationType.ADJUST_SATURATION -> "调整饱和度"
            EditOperationType.CROP -> "裁剪"
            EditOperationType.ROTATE -> "旋转"
            EditOperationType.FLIP -> "翻转"
            EditOperationType.AI_BEAUTY -> "AI美颜"
            EditOperationType.AI_REMOVE_OBJECT -> "AI去路人"
            EditOperationType.AI_SKY_REPLACE -> "AI换天空"
            EditOperationType.AI_ENHANCE -> "AI画质增强"
            EditOperationType.WATERMARK -> "添加水印"
            EditOperationType.DRAW -> "涂鸦"
            EditOperationType.TEXT -> "添加文字"
            EditOperationType.STICKER -> "添加贴纸"
            EditOperationType.MOSAIC -> "马赛克"
            EditOperationType.BLUR -> "模糊"
            EditOperationType.UNDO -> "撤销"
            EditOperationType.REDO -> "重做"
        }
    }
}

/**
 * Room类型转换器
 */
class EditHistoryConverters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromOperationsList(operations: List<EditOperation>): String {
        return gson.toJson(operations)
    }
    
    @TypeConverter
    fun toOperationsList(json: String): List<EditOperation> {
        val type = object : TypeToken<List<EditOperation>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return gson.toJson(list)
    }
    
    @TypeConverter
    fun toStringList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    @TypeConverter
    fun fromEditOperationType(type: EditOperationType): String {
        return type.name
    }
    
    @TypeConverter
    fun toEditOperationType(name: String): EditOperationType {
        return try {
            EditOperationType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            EditOperationType.FILTER
        }
    }
}

/**
 * 编辑会话 - 用于批量记录一次完整的编辑过程
 */
data class EditSession(
    val sessionId: String = System.currentTimeMillis().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val photoItems: MutableList<PhotoItem> = mutableListOf(),
    val operations: MutableList<EditOperation> = mutableListOf(),
    var isBatchMode: Boolean = false,
    var totalPhotos: Int = 0,
    var processedPhotos: Int = 0
) {
    /**
     * 添加操作
     */
    fun addOperation(operation: EditOperation) {
        operations.add(operation)
    }
    
    /**
     * 完成会话
     */
    fun finish() {
        endTime = System.currentTimeMillis()
    }
    
    /**
     * 获取编辑时长（毫秒）
     */
    fun getDuration(): Long {
        val end = endTime ?: System.currentTimeMillis()
        return end - startTime
    }
    
    /**
     * 撤销最后一次操作
     */
    fun undo(): Boolean {
        if (operations.isNotEmpty()) {
            operations.removeAt(operations.size - 1)
            return true
        }
        return false
    }
}
