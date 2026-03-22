package com.photoeditor.data.repository

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.photoeditor.data.model.EditHistory
import com.photoeditor.data.model.EditHistoryDao
import com.photoeditor.data.model.PhotoFolder
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.data.model.PhotoSortOrder
import com.photoeditor.utils.FileManager
import com.photoeditor.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 照片仓库
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val context: Context,
    private val editHistoryDao: EditHistoryDao,
    private val fileManager: FileManager
) {

    /**
     * 获取所有照片（分页）
     */
    fun getAllPhotosPaged(pageSize: Int = 20): Flow<PagingData<PhotoItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { PhotoPagingSource(context, pageSize) }
        ).flow
    }

    /**
     * 获取所有照片
     */
    fun getAllPhotos(): Flow<List<PhotoItem>> = flow {
        emit(ImageUtils.getPhotosInFolder(context))
    }.flowOn(Dispatchers.IO)

    /**
     * 获取相册文件夹
     */
    fun getPhotoFolders(): Flow<List<PhotoFolder>> = flow {
        emit(ImageUtils.getPhotoFolders(context))
    }.flowOn(Dispatchers.IO)

    /**
     * 获取文件夹中的照片
     */
    fun getPhotosInFolder(folderId: String): Flow<List<PhotoItem>> = flow {
        emit(ImageUtils.getPhotosInFolder(context, folderId))
    }.flowOn(Dispatchers.IO)

    /**
     * 搜索照片
     */
    fun searchPhotos(query: String): Flow<List<PhotoItem>> = flow {
        val allPhotos = ImageUtils.getPhotosInFolder(context)
        val filtered = allPhotos.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.folderName.contains(query, ignoreCase = true)
        }
        emit(filtered)
    }.flowOn(Dispatchers.IO)

    /**
     * 按日期范围获取照片
     */
    fun getPhotosByDateRange(startDate: Long, endDate: Long): Flow<List<PhotoItem>> = flow {
        val allPhotos = ImageUtils.getPhotosInFolder(context)
        val filtered = allPhotos.filter {
            it.dateAdded in startDate..endDate
        }
        emit(filtered)
    }.flowOn(Dispatchers.IO)

    /**
     * 按排序方式获取照片
     */
    fun getPhotosSorted(sortOrder: PhotoSortOrder): Flow<List<PhotoItem>> = flow {
        val photos = ImageUtils.getPhotosInFolder(context)
        val sorted = when (sortOrder) {
            PhotoSortOrder.DATE_DESC -> photos.sortedByDescending { it.dateAdded }
            PhotoSortOrder.DATE_ASC -> photos.sortedBy { it.dateAdded }
            PhotoSortOrder.NAME_ASC -> photos.sortedBy { it.name }
            PhotoSortOrder.NAME_DESC -> photos.sortedByDescending { it.name }
            PhotoSortOrder.SIZE_DESC -> photos.sortedByDescending { it.size }
            PhotoSortOrder.SIZE_ASC -> photos.sortedBy { it.size }
        }
        emit(sorted)
    }.flowOn(Dispatchers.IO)

    /**
     * 获取单张照片详情
     */
    suspend fun getPhotoDetails(uri: Uri): PhotoItem? = withContext(Dispatchers.IO) {
        try {
            val info = ImageUtils.getImageInfo(context, uri)
            info?.let {
                PhotoItem(
                    id = uri.toString(),
                    uri = uri,
                    name = uri.lastPathSegment ?: "unknown",
                    width = it.width,
                    height = it.height,
                    size = it.size,
                    mimeType = it.mimeType
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查照片是否存在
     */
    suspend fun photoExists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ============== 编辑历史相关 ==============

    /**
     * 获取所有编辑历史
     */
    fun getAllEditHistory(): Flow<List<EditHistory>> {
        return editHistoryDao.getAllHistory()
    }

    /**
     * 获取最近的编辑历史
     */
    fun getRecentEditHistory(limit: Int = 50): Flow<List<EditHistory>> {
        return editHistoryDao.getRecentHistory(limit)
    }

    /**
     * 获取收藏的历史
     */
    fun getFavoriteHistory(): Flow<List<EditHistory>> {
        return editHistoryDao.getFavoriteHistory()
    }

    /**
     * 根据照片ID获取编辑历史
     */
    fun getEditHistoryByPhotoId(photoId: String): Flow<List<EditHistory>> {
        return editHistoryDao.getHistoryByPhotoId(photoId)
    }

    /**
     * 保存编辑历史
     */
    suspend fun saveEditHistory(history: EditHistory): Long = withContext(Dispatchers.IO) {
        editHistoryDao.insert(history)
    }

    /**
     * 更新编辑历史
     */
    suspend fun updateEditHistory(history: EditHistory) = withContext(Dispatchers.IO) {
        editHistoryDao.update(history)
    }

    /**
     * 删除编辑历史
     */
    suspend fun deleteEditHistory(history: EditHistory) = withContext(Dispatchers.IO) {
        editHistoryDao.delete(history)
    }

    /**
     * 根据ID删除编辑历史
     */
    suspend fun deleteEditHistoryById(id: Long) = withContext(Dispatchers.IO) {
        editHistoryDao.deleteById(id)
    }

    /**
     * 清空编辑历史
     */
    suspend fun clearAllEditHistory() = withContext(Dispatchers.IO) {
        editHistoryDao.deleteAll()
    }

    /**
     * 切换收藏状态
     */
    suspend fun toggleFavorite(historyId: Long) = withContext(Dispatchers.IO) {
        editHistoryDao.toggleFavorite(historyId)
    }

    /**
     * 获取编辑历史数量
     */
    suspend fun getEditHistoryCount(): Int = withContext(Dispatchers.IO) {
        editHistoryDao.getCount()
    }

    /**
     * 搜索编辑历史
     */
    fun searchEditHistory(query: String): Flow<List<EditHistory>> {
        return editHistoryDao.searchHistory("%$query%")
    }

    // ============== 文件操作 ==============

    /**
     * 保存到相册
     */
    suspend fun saveToGallery(sourceFile: java.io.File): Uri? {
        return fileManager.saveToGallery(sourceFile)
    }

    /**
     * 创建输出文件
     */
    fun createOutputFile(): java.io.File {
        return fileManager.createOutputFile()
    }

    /**
     * 获取可用存储空间
     */
    fun getAvailableStorage(): Long {
        return fileManager.getAvailableStorage()
    }

    /**
     * 清理临时文件
     */
    suspend fun clearTempFiles() {
        fileManager.clearTempFiles()
    }
}