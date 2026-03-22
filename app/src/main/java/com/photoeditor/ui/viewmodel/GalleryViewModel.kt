package com.photoeditor.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.photoeditor.data.model.PhotoFolder
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.data.model.PhotoSortOrder
import com.photoeditor.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 相册ViewModel
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    // 照片列表
    val photos: Flow<PagingData<PhotoItem>> = photoRepository.getAllPhotosPaged()
        .cachedIn(viewModelScope)

    // 文件夹列表
    private val _folders = MutableStateFlow<List<PhotoFolder>>(emptyList())
    val folders: StateFlow<List<PhotoFolder>> = _folders.asStateFlow()

    // 当前选中的照片
    private val _selectedPhotos = MutableStateFlow<Set<PhotoItem>>(emptySet())
    val selectedPhotos: StateFlow<Set<PhotoItem>> = _selectedPhotos.asStateFlow()

    // 当前选中的文件夹
    private val _selectedFolder = MutableStateFlow<PhotoFolder?>(null)
    val selectedFolder: StateFlow<PhotoFolder?> = _selectedFolder.asStateFlow()

    // 排序方式
    private val _sortOrder = MutableStateFlow(PhotoSortOrder.DATE_DESC)
    val sortOrder: StateFlow<PhotoSortOrder> = _sortOrder.asStateFlow()

    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 搜索结果
    private val _searchResults = MutableStateFlow<List<PhotoItem>>(emptyList())
    val searchResults: StateFlow<List<PhotoItem>> = _searchResults.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 选择模式
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    init {
        loadFolders()
    }

    /**
     * 加载文件夹
     */
    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            photoRepository.getPhotoFolders()
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    e.printStackTrace()
                }
                .collect { folders ->
                    _folders.value = folders
                    _isLoading.value = false
                }
        }
    }

    /**
     * 加载文件夹中的照片
     */
    fun loadPhotosInFolder(folderId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            if (folderId == null) {
                photoRepository.getAllPhotos()
                    .flowOn(Dispatchers.IO)
                    .catch { e ->
                        e.printStackTrace()
                    }
                    .collect { photos ->
                        _isLoading.value = false
                    }
            } else {
                photoRepository.getPhotosInFolder(folderId)
                    .flowOn(Dispatchers.IO)
                    .catch { e ->
                        e.printStackTrace()
                    }
                    .collect { photos ->
                        _isLoading.value = false
                    }
            }
        }
    }

    /**
     * 搜索照片
     */
    fun searchPhotos(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            photoRepository.searchPhotos(query)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    e.printStackTrace()
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }

    /**
     * 切换照片选择
     */
    fun togglePhotoSelection(photo: PhotoItem) {
        val current = _selectedPhotos.value.toMutableSet()
        if (current.contains(photo)) {
            current.remove(photo)
        } else {
            current.add(photo)
        }
        _selectedPhotos.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    /**
     * 选择照片
     */
    fun selectPhoto(photo: PhotoItem) {
        val current = _selectedPhotos.value.toMutableSet()
        current.add(photo)
        _selectedPhotos.value = current
    }

    /**
     * 取消选择照片
     */
    fun deselectPhoto(photo: PhotoItem) {
        val current = _selectedPhotos.value.toMutableSet()
        current.remove(photo)
        _selectedPhotos.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    /**
     * 全选
     */
    fun selectAll(photos: List<PhotoItem>) {
        _selectedPhotos.value = photos.toSet()
        _isSelectionMode.value = true
    }

    /**
     * 清除选择
     */
    fun clearSelection() {
        _selectedPhotos.value = emptySet()
        _isSelectionMode.value = false
    }

    /**
     * 进入选择模式
     */
    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }

    /**
     * 退出选择模式
     */
    fun exitSelectionMode() {
        clearSelection()
    }

    /**
     * 设置当前文件夹
     */
    fun setSelectedFolder(folder: PhotoFolder?) {
        _selectedFolder.value = folder
    }

    /**
     * 设置排序方式
     */
    fun setSortOrder(order: PhotoSortOrder) {
        _sortOrder.value = order
    }

    /**
     * 获取选中数量
     */
    fun getSelectedCount(): Int = _selectedPhotos.value.size

    /**
     * 获取选中照片列表
     */
    fun getSelectedPhotosList(): List<PhotoItem> = _selectedPhotos.value.toList()

    /**
     * 检查照片是否被选中
     */
    fun isPhotoSelected(photo: PhotoItem): Boolean = _selectedPhotos.value.contains(photo)

    /**
     * 删除选中的照片
     */
    fun deleteSelectedPhotos() {
        // 实现删除逻辑
        clearSelection()
    }
}
