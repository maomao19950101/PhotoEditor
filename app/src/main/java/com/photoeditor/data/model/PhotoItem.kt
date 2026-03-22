package com.photoeditor.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * 照片数据类
 */
@Parcelize
data class PhotoItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val path: String? = null,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val mimeType: String = "image/jpeg",
    val orientation: Int = 0,
    val folderName: String = "",
    var isSelected: Boolean = false,
    var isProcessing: Boolean = false,
    var processingProgress: Int = 0
) : Parcelable {
    
    /**
     * 获取添加日期
     */
    fun getAddedDate(): Date = Date(dateAdded)
    
    /**
     * 获取修改日期
     */
    fun getModifiedDate(): Date = Date(dateModified)
    
    /**
     * 获取文件大小字符串
     */
    fun getSizeString(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 获取分辨率字符串
     */
    fun getResolutionString(): String {
        return if (width > 0 && height > 0) {
            "${width}x${height}"
        } else {
            "未知"
        }
    }
    
    /**
     * 是否为横屏照片
     */
    fun isLandscape(): Boolean = width > height
    
    /**
     * 获取宽高比
     */
    fun getAspectRatio(): Float {
        return if (height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            1.0f
        }
    }
    
    companion object {
        /**
         * 从媒体库游标创建PhotoItem
         */
        fun fromMediaStore(
            id: Long,
            uri: Uri,
            name: String,
            path: String?,
            size: Long,
            width: Int,
            height: Int,
            dateAdded: Long,
            dateModified: Long,
            mimeType: String,
            orientation: Int,
            folderName: String
        ): PhotoItem {
            return PhotoItem(
                id = id.toString(),
                uri = uri,
                name = name,
                path = path,
                size = size,
                width = width,
                height = height,
                dateAdded = dateAdded,
                dateModified = dateModified,
                mimeType = mimeType,
                orientation = orientation,
                folderName = folderName
            )
        }
    }
}

/**
 * 照片选择状态
 */
enum class PhotoSelectionState {
    NONE,           // 未选择
    SINGLE,         // 单选
    MULTIPLE,       // 多选
    BATCH_PROCESS   // 批量处理
}

/**
 * 照片排序方式
 */
enum class PhotoSortOrder {
    DATE_DESC,      // 日期降序（最新优先）
    DATE_ASC,       // 日期升序
    NAME_ASC,       // 名称升序
    NAME_DESC,      // 名称降序
    SIZE_DESC,      // 大小降序
    SIZE_ASC        // 大小升序
}

/**
 * 照片文件夹
 */
@Parcelize
data class PhotoFolder(
    val id: String,
    val name: String,
    val path: String,
    val coverUri: Uri?,
    var photoCount: Int = 0,
    var photos: List<PhotoItem> = emptyList()
) : Parcelable
