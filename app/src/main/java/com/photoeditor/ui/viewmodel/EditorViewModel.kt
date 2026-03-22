package com.photoeditor.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoeditor.ai.AIEnhancer
import com.photoeditor.ai.ImageProcessor
import com.photoeditor.data.model.EditHistory
import com.photoeditor.data.model.EditOperation
import com.photoeditor.data.model.EditOperationType
import com.photoeditor.data.model.FilterStyle
import com.photoeditor.data.model.WatermarkConfig
import com.photoeditor.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 编辑界面ViewModel
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    // 原始图片
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    // 预览图片
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    // 处理状态
    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    // 当前滤镜
    private val _currentFilter = MutableStateFlow<FilterStyle?>(null)
    val currentFilter: StateFlow<FilterStyle?> = _currentFilter.asStateFlow()

    // 滤镜强度
    private val _filterIntensity = MutableStateFlow(1.0f)
    val filterIntensity: StateFlow<Float> = _filterIntensity.asStateFlow()

    // 美颜参数
    private val _beautyParams = MutableStateFlow(AIEnhancer.BeautyParams())
    val beautyParams: StateFlow<AIEnhancer.BeautyParams> = _beautyParams.asStateFlow()

    // 水印配置
    private val _watermarkConfig = MutableStateFlow<WatermarkConfig?>(null)
    val watermarkConfig: StateFlow<WatermarkConfig?> = _watermarkConfig.asStateFlow()

    // 编辑操作历史
    private val _editOperations = MutableStateFlow<List<EditOperation>>(emptyList())
    val editOperations: StateFlow<List<EditOperation>> = _editOperations.asStateFlow()

    // 是否可以撤销/重做
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    // 保存结果
    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private var currentJob: Job? = null
    private var undoStack = mutableListOf<EditState>()
    private var redoStack = mutableListOf<EditState>()

    data class EditState(
        val filter: FilterStyle?,
        val filterIntensity: Float,
        val beautyParams: AIEnhancer.BeautyParams,
        val watermarkConfig: WatermarkConfig?
    )

    sealed class ProcessingState {
        object Idle : ProcessingState()
        object Loading : ProcessingState()
        object Processing : ProcessingState()
        data class Error(val message: String) : ProcessingState()
    }

    sealed class SaveResult {
        data class Success(val uri: Uri, val file: File) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    /**
     * 加载图片
     */
    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading
            try {
                val bitmap = imageProcessor.loadBitmapFromUri(uri)
                _originalBitmap.value = bitmap
                _previewBitmap.value = bitmap
                _processingState.value = ProcessingState.Idle
                saveCurrentState()
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("加载图片失败: ${e.message}")
            }
        }
    }

    /**
     * 设置原始图片
     */
    fun setOriginalBitmap(bitmap: Bitmap) {
        _originalBitmap.value = bitmap
        _previewBitmap.value = bitmap
        saveCurrentState()
    }

    /**
     * 应用滤镜
     */
    fun applyFilter(filter: FilterStyle?, intensity: Float = _filterIntensity.value) {
        _currentFilter.value = filter
        _filterIntensity.value = intensity
        updatePreview()
    }

    /**
     * 设置滤镜强度
     */
    fun setFilterIntensity(intensity: Float) {
        _filterIntensity.value = intensity
        updatePreview()
    }

    /**
     * 设置美颜参数
     */
    fun setBeautyParams(params: AIEnhancer.BeautyParams) {
        _beautyParams.value = params
        updatePreview()
    }

    /**
     * 设置水印
     */
    fun setWatermark(config: WatermarkConfig?) {
        _watermarkConfig.value = config
        updatePreview()
    }

    /**
     * 更新预览
     */
    private fun updatePreview() {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            val original = _originalBitmap.value ?: return@launch
            _processingState.value = ProcessingState.Processing

            try {
                val options = ImageProcessor.ProcessOptions(
                    filter = _currentFilter.value,
                    filterIntensity = _filterIntensity.value,
                    applyBeauty = _beautyParams.value.skinSmooth > 0,
                    beautyParams = _beautyParams.value,
                    watermarkConfig = _watermarkConfig.value
                )

                val result = imageProcessor.processPreview(original, options)
                _previewBitmap.value = result
                _processingState.value = ProcessingState.Idle

                // 记录操作
                recordOperation(EditOperationType.FILTER)
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("处理失败: ${e.message}")
            }
        }
    }

    /**
     * AI智能调色
     */
    fun applySmartColorGrading() {
        viewModelScope.launch {
            val original = _originalBitmap.value ?: return@launch
            _processingState.value = ProcessingState.Processing

            val result = imageProcessor.smartColorGrading(original)
            when (result) {
                is ImageProcessor.ProcessResult.Success -> {
                    _previewBitmap.value = result.bitmap
                    _processingState.value = ProcessingState.Idle
                    recordOperation(EditOperationType.AI_ENHANCE)
                }
                is ImageProcessor.ProcessResult.Error -> {
                    _processingState.value = ProcessingState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    /**
     * AI自动增强
     */
    fun applyAutoEnhance() {
        viewModelScope.launch {
            val original = _originalBitmap.value ?: return@launch
            _processingState.value = ProcessingState.Processing

            val result = imageProcessor.autoEnhance(original)
            when (result) {
                is ImageProcessor.ProcessResult.Success -> {
                    _previewBitmap.value = result.bitmap
                    _processingState.value = ProcessingState.Idle
                    recordOperation(EditOperationType.AI_ENHANCE)
                }
                is ImageProcessor.ProcessResult.Error -> {
                    _processingState.value = ProcessingState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    /**
     * 保存图片
     */
    fun saveImage(outputFile: File? = null) {
        viewModelScope.launch {
            val original = _originalBitmap.value ?: return@launch
            _processingState.value = ProcessingState.Processing

            val file = outputFile ?: photoRepository.createOutputFile()
            val options = ImageProcessor.ProcessOptions(
                filter = _currentFilter.value,
                filterIntensity = _filterIntensity.value,
                applyBeauty = _beautyParams.value.skinSmooth > 0,
                beautyParams = _beautyParams.value,
                watermarkConfig = _watermarkConfig.value,
                outputQuality = 95
            )

            val result = imageProcessor.processImage(original, options, true, file)
            when (result) {
                is ImageProcessor.ProcessResult.Success -> {
                    _saveResult.value = SaveResult.Success(result.outputUri!!, file)
                    _processingState.value = ProcessingState.Idle
                    saveEditHistory(result.outputUri, file)
                }
                is ImageProcessor.ProcessResult.Error -> {
                    _saveResult.value = SaveResult.Error(result.message)
                    _processingState.value = ProcessingState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    /**
     * 保存编辑历史
     */
    private fun saveEditHistory(outputUri: Uri, outputFile: File) {
        viewModelScope.launch {
            val history = EditHistory(
                originalPhotoId = outputUri.toString(),
                originalPhotoUri = outputUri.toString(),
                originalPhotoName = outputFile.name,
                editedPhotoUri = outputUri.toString(),
                operations = _editOperations.value,
                appliedFilterId = _currentFilter.value?.id,
                filterIntensity = _filterIntensity.value,
                aiFeaturesUsed = listOfNotNull(
                    if (_beautyParams.value.skinSmooth > 0) "beauty" else null
                )
            )
            photoRepository.saveEditHistory(history)
        }
    }

    /**
     * 撤销
     */
    fun undo() {
        if (undoStack.size > 1) {
            redoStack.add(undoStack.removeAt(undoStack.size - 1))
            val state = undoStack.last()
            restoreState(state)
            _canUndo.value = undoStack.size > 1
        }
    }

    /**
     * 重做
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val state = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(state)
            restoreState(state)
            _canUndo.value = undoStack.size > 1
        }
    }

    /**
     * 保存当前状态
     */
    private fun saveCurrentState() {
        val state = EditState(
            filter = _currentFilter.value,
            filterIntensity = _filterIntensity.value,
            beautyParams = _beautyParams.value,
            watermarkConfig = _watermarkConfig.value
        )
        undoStack.add(state)
        redoStack.clear()
        _canUndo.value = undoStack.size > 1
    }

    /**
     * 恢复状态
     */
    private fun restoreState(state: EditState) {
        _currentFilter.value = state.filter
        _filterIntensity.value = state.filterIntensity
        _beautyParams.value = state.beautyParams
        _watermarkConfig.value = state.watermarkConfig
        updatePreview()
    }

    /**
     * 记录操作
     */
    private fun recordOperation(type: EditOperationType) {
        val operation = EditOperation(type = type)
        _editOperations.value = _editOperations.value + operation
        saveCurrentState()
    }

    /**
     * 重置
     */
    fun reset() {
        _currentFilter.value = null
        _filterIntensity.value = 1.0f
        _beautyParams.value = AIEnhancer.BeautyParams()
        _watermarkConfig.value = null
        _editOperations.value = emptyList()
        _originalBitmap.value?.let {
            _previewBitmap.value = it
        }
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _saveResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
