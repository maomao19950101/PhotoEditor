package com.photoeditor.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoeditor.ai.ImageProcessor
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.data.repository.FilterRepository
import com.photoeditor.utils.BatchProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 批量处理ViewModel
 */
@HiltViewModel
class BatchViewModel @Inject constructor(
    private val batchProcessor: BatchProcessor,
    private val filterRepository: FilterRepository
) : ViewModel() {

    // 待处理照片
    private val _pendingPhotos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val pendingPhotos: StateFlow<List<PhotoItem>> = _pendingPhotos.asStateFlow()

    // 处理状态
    val processingState: StateFlow<BatchProcessor.BatchProcessingState> =
        batchProcessor.processingState

    // 进度
    val progress: StateFlow<BatchProcessor.BatchProgress> = batchProcessor.progress

    // 处理结果
    private val _results = MutableStateFlow<List<BatchProcessor.BatchResult>>(emptyList())
    val results: StateFlow<List<BatchProcessor.BatchResult>> = _results.asStateFlow()

    // 处理配置
    private val _batchConfig = MutableStateFlow<BatchProcessor.BatchConfig?>(null)
    val batchConfig: StateFlow<BatchProcessor.BatchConfig?> = _batchConfig.asStateFlow()

    // 导出格式
    private val _exportFormat = MutableStateFlow(BatchProcessor.ExportFormat.JPEG_HIGH)
    val exportFormat: StateFlow<BatchProcessor.ExportFormat> = _exportFormat.asStateFlow()

    // 命名规则
    private val _namingRule = MutableStateFlow(BatchProcessor.NamingRule.SEQUENTIAL)
    val namingRule: StateFlow<BatchProcessor.NamingRule> = _namingRule.asStateFlow()

    // 是否正在处理
    val isProcessing: Boolean
        get() = processingState.value is BatchProcessor.BatchProcessingState.Processing

    // 预估剩余时间
    private val _estimatedTimeRemaining = MutableStateFlow(0L)
    val estimatedTimeRemaining: StateFlow<Long> = _estimatedTimeRemaining.asStateFlow()

    init {
        viewModelScope.launch {
            progress.collect { p ->
                _estimatedTimeRemaining.value = p.estimatedTimeRemaining
            }
        }
    }

    /**
     * 设置待处理照片
     */
    fun setPendingPhotos(photos: List<PhotoItem>) {
        _pendingPhotos.value = photos
    }

    /**
     * 添加照片
     */
    fun addPhotos(photos: List<PhotoItem>) {
        val current = _pendingPhotos.value.toMutableList()
        current.addAll(photos.filter { newPhoto ->
            current.none { it.id == newPhoto.id }
        })
        _pendingPhotos.value = current
    }

    /**
     * 移除照片
     */
    fun removePhoto(photo: PhotoItem) {
        _pendingPhotos.value = _pendingPhotos.value.filter { it.id != photo.id }
    }

    /**
     * 清空照片
     */
    fun clearPhotos() {
        _pendingPhotos.value = emptyList()
    }

    /**
     * 设置导出格式
     */
    fun setExportFormat(format: BatchProcessor.ExportFormat) {
        _exportFormat.value = format
    }

    /**
     * 设置命名规则
     */
    fun setNamingRule(rule: BatchProcessor.NamingRule) {
        _namingRule.value = rule
    }

    /**
     * 设置处理配置
     */
    fun setBatchConfig(config: BatchProcessor.BatchConfig) {
        _batchConfig.value = config
    }

    /**
     * 开始批量处理
     */
    fun startBatchProcessing() {
        val photos = _pendingPhotos.value
        if (photos.isEmpty()) return

        val config = _batchConfig.value ?: createDefaultConfig(photos)

        viewModelScope.launch {
            val results = batchProcessor.startBatchProcessing(config)
            _results.value = results
        }
    }

    /**
     * 取消处理
     */
    fun cancelProcessing() {
        batchProcessor.cancel()
    }

    /**
     * 重置状态
     */
    fun reset() {
        batchProcessor.reset()
        _results.value = emptyList()
    }

    /**
     * 创建默认配置
     */
    private fun createDefaultConfig(photos: List<PhotoItem>): BatchProcessor.BatchConfig {
        return BatchProcessor.BatchConfig(
            photoItems = photos,
            processOptions = ImageProcessor.ProcessOptions(
                filter = filterRepository.currentFilter.value,
                filterIntensity = filterRepository.filterIntensity.value
            ),
            exportFormat = _exportFormat.value,
            namingRule = _namingRule.value,
            customPrefix = "AI_"
        )
    }

    /**
     * 获取处理统计
     */
    fun getProcessingStats(): BatchProcessor.ProcessingStats {
        return batchProcessor.getProcessingStats(_results.value)
    }

    /**
     * 获取成功结果
     */
    fun getSuccessfulResults(): List<BatchProcessor.BatchResult> {
        return _results.value.filter { it.success }
    }

    /**
     * 获取失败结果
     */
    fun getFailedResults(): List<BatchProcessor.BatchResult> {
        return _results.value.filter { !it.success }
    }

    /**
     * 检查存储空间
     */
    fun hasEnoughStorage(): Boolean {
        val photoCount = _pendingPhotos.value.size
        val estimatedSize = photoCount * 5 * 1024 * 1024L // 预估每张5MB
        return batchProcessor.hasEnoughStorage(estimatedSize)
    }

    /**
     * 获取预估处理时间
     */
    fun getEstimatedTime(): Long {
        return batchProcessor.estimateProcessingTime(_pendingPhotos.value.size)
    }

    /**
     * 获取照片数量
     */
    fun getPhotoCount(): Int = _pendingPhotos.value.size

    override fun onCleared() {
        super.onCleared()
        batchProcessor.reset()
    }
}
