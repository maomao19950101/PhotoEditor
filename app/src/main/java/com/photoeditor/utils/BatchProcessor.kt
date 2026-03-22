package com.photoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photoeditor.ai.ImageProcessor
import com.photoeditor.data.model.PhotoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 批量处理器
 */
class BatchProcessor(private val context: Context) {

    private val imageProcessor = ImageProcessor(context)
    private val fileManager = FileManager(context)

    // 并发控制 - 最多同时处理4张
    private val semaphore = Semaphore(4)

    // 处理状态
    private val _processingState = MutableStateFlow<BatchProcessingState>(BatchProcessingState.Idle)
    val processingState: StateFlow<BatchProcessingState> = _processingState.asStateFlow()

    // 进度
    private val _progress = MutableStateFlow(BatchProgress())
    val progress: StateFlow<BatchProgress> = _progress.asStateFlow()

    // 取消标志
    @Volatile
    private var isCancelled = false

    /**
     * 批量处理配置
     */
    data class BatchConfig(
        val photoItems: List<PhotoItem>,
        val processOptions: ImageProcessor.ProcessOptions,
        val exportFormat: ExportFormat = ExportFormat.JPEG_HIGH,
        val namingRule: NamingRule = NamingRule.SEQUENTIAL,
        val customPrefix: String = "AI_",
        val outputDirectory: File? = null,
        val preserveOriginalMetadata: Boolean = true,
        val maxConcurrent: Int = 4
    )

    /**
     * 导出格式
     */
    enum class ExportFormat {
        JPEG_ORIGINAL,  // 原图质量
        JPEG_HIGH,      // 高质量 (95%)
        JPEG_MEDIUM,    // 中等质量 (80%)
        JPEG_LOW,       // 低质量 (60%)
        PNG             // PNG无损
    }

    /**
     * 命名规则
     */
    enum class NamingRule {
        SEQUENTIAL,     // 顺序编号: AI_001.jpg
        TIMESTAMP,      // 时间戳: AI_20240322_143052.jpg
        ORIGINAL,       // 保留原文件名: AI_original.jpg
        DATE_PREFIX     // 日期前缀: 20240322_AI_original.jpg
    }

    /**
     * 批量处理状态
     */
    sealed class BatchProcessingState {
        object Idle : BatchProcessingState()
        object Preparing : BatchProcessingState()
        data class Processing(val currentIndex: Int, val total: Int) : BatchProcessingState()
        object Finalizing : BatchProcessingState()
        data class Completed(val results: List<BatchResult>) : BatchProcessingState()
        data class Cancelled(val completedResults: List<BatchResult>) : BatchProcessingState()
        data class Error(val message: String, val exception: Throwable? = null) : BatchProcessingState()
    }

    /**
     * 批量进度
     */
    data class BatchProgress(
        val totalCount: Int = 0,
        val completedCount: Int = 0,
        val failedCount: Int = 0,
        val currentFileName: String = "",
        val percentage: Int = 0,
        val estimatedTimeRemaining: Long = 0
    )

    /**
     * 处理结果
     */
    data class BatchResult(
        val photoItem: PhotoItem,
        val success: Boolean,
        val outputUri: Uri? = null,
        val outputFile: File? = null,
        val errorMessage: String? = null,
        val processingTime: Long = 0
    )

    /**
     * 开始批量处理
     */
    suspend fun startBatchProcessing(config: BatchConfig): List<BatchResult> = withContext(Dispatchers.Default) {
        isCancelled = false
        val results = mutableListOf<BatchResult>()
        val startTime = System.currentTimeMillis()

        try {
            _processingState.value = BatchProcessingState.Preparing
            _progress.value = BatchProgress(
                totalCount = config.photoItems.size,
                completedCount = 0,
                failedCount = 0,
                percentage = 0
            )

            // 准备输出目录
            val outputDir = config.outputDirectory ?: fileManager.getDefaultOutputDirectory()
            outputDir.mkdirs()

            // 确定输出质量
            val outputQuality = when (config.exportFormat) {
                ExportFormat.JPEG_ORIGINAL -> 100
                ExportFormat.JPEG_HIGH -> 95
                ExportFormat.JPEG_MEDIUM -> 80
                ExportFormat.JPEG_LOW -> 60
                ExportFormat.PNG -> 100
            }

            val options = config.processOptions.copy(outputQuality = outputQuality)

            _processingState.value = BatchProcessingState.Processing(0, config.photoItems.size)

            // 并发处理照片
            coroutineScope {
                config.photoItems.mapIndexed { index, photoItem ->
                    async {
                        if (isCancelled || !isActive) {
                            return@async null
                        }

                        semaphore.withPermit {
                            processSinglePhoto(
                                photoItem = photoItem,
                                index = index,
                                totalCount = config.photoItems.size,
                                options = options,
                                namingRule = config.namingRule,
                                customPrefix = config.customPrefix,
                                outputDir = outputDir,
                                exportFormat = config.exportFormat,
                                startTime = startTime
                            )
                        }
                    }
                }.awaitAll().filterNotNull().let { batchResults ->
                    results.addAll(batchResults)
                }
            }

            if (isCancelled) {
                _processingState.value = BatchProcessingState.Cancelled(results)
            } else {
                _processingState.value = BatchProcessingState.Completed(results)
            }

        } catch (e: CancellationException) {
            _processingState.value = BatchProcessingState.Cancelled(results)
            throw e
        } catch (e: Exception) {
            _processingState.value = BatchProcessingState.Error(e.message ?: "处理失败", e)
        }

        results
    }

    /**
     * 处理单张照片
     */
    private suspend fun processSinglePhoto(
        photoItem: PhotoItem,
        index: Int,
        totalCount: Int,
        options: ImageProcessor.ProcessOptions,
        namingRule: NamingRule,
        customPrefix: String,
        outputDir: File,
        exportFormat: ExportFormat,
        startTime: Long
    ): BatchResult {
        val itemStartTime = System.currentTimeMillis()

        return try {
            // 更新进度
            _progress.value = _progress.value.copy(
                currentFileName = photoItem.name,
                completedCount = index
            )

            // 更新状态
            _processingState.value = BatchProcessingState.Processing(index + 1, totalCount)

            // 加载原图
            val bitmap = imageProcessor.loadBitmapFromUri(photoItem.uri)
                ?: return BatchResult(
                    photoItem = photoItem,
                    success = false,
                    errorMessage = "无法加载图片"
                )

            // 生成输出文件名
            val outputFileName = generateOutputFileName(
                photoItem, index, namingRule, customPrefix, exportFormat
            )
            val outputFile = File(outputDir, outputFileName)

            // 处理图片
            val result = imageProcessor.processImage(
                bitmap = bitmap,
                options = options,
                saveToFile = true,
                outputFile = outputFile
            )

            // 回收Bitmap
            imageProcessor.recycleBitmap(bitmap)

            // 更新进度
            val completed = _progress.value.completedCount + 1
            val percentage = (completed * 100 / totalCount)
            val elapsedTime = System.currentTimeMillis() - startTime
            val avgTimePerItem = if (completed > 0) elapsedTime / completed else 0
            val remainingItems = totalCount - completed
            val estimatedRemaining = avgTimePerItem * remainingItems

            _progress.value = _progress.value.copy(
                completedCount = completed,
                percentage = percentage,
                estimatedTimeRemaining = estimatedRemaining
            )

            when (result) {
                is ImageProcessor.ProcessResult.Success -> {
                    BatchResult(
                        photoItem = photoItem,
                        success = true,
                        outputUri = result.outputUri,
                        outputFile = outputFile,
                        processingTime = System.currentTimeMillis() - itemStartTime
                    )
                }
                is ImageProcessor.ProcessResult.Error -> {
                    _progress.value = _progress.value.copy(
                        failedCount = _progress.value.failedCount + 1
                    )
                    BatchResult(
                        photoItem = photoItem,
                        success = false,
                        errorMessage = result.message,
                        processingTime = System.currentTimeMillis() - itemStartTime
                    )
                }
                else -> {
                    _progress.value = _progress.value.copy(
                        failedCount = _progress.value.failedCount + 1
                    )
                    BatchResult(
                        photoItem = photoItem,
                        success = false,
                        errorMessage = "处理被取消",
                        processingTime = System.currentTimeMillis() - itemStartTime
                    )
                }
            }

        } catch (e: Exception) {
            _progress.value = _progress.value.copy(
                failedCount = _progress.value.failedCount + 1
            )
            BatchResult(
                photoItem = photoItem,
                success = false,
                errorMessage = e.message ?: "处理失败",
                processingTime = System.currentTimeMillis() - itemStartTime
            )
        }
    }

    /**
     * 生成输出文件名
     */
    private fun generateOutputFileName(
        photoItem: PhotoItem,
        index: Int,
        namingRule: NamingRule,
        customPrefix: String,
        exportFormat: ExportFormat
    ): String {
        val extension = if (exportFormat == ExportFormat.PNG) "png" else "jpg"
        val originalName = photoItem.name.substringBeforeLast(".")

        return when (namingRule) {
            NamingRule.SEQUENTIAL -> {
                String.format("${customPrefix}%04d.$extension", index + 1)
            }
            NamingRule.TIMESTAMP -> {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(java.util.Date())
                "${customPrefix}${timestamp}_$index.$extension"
            }
            NamingRule.ORIGINAL -> {
                "${customPrefix}${originalName}.$extension"
            }
            NamingRule.DATE_PREFIX -> {
                val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    .format(java.util.Date())
                "${datePrefix}_${customPrefix}${originalName}.$extension"
            }
        }
    }

    /**
     * 取消批量处理
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * 重置状态
     */
    fun reset() {
        isCancelled = false
        _processingState.value = BatchProcessingState.Idle
        _progress.value = BatchProgress()
    }

    /**
     * 预估处理时间（毫秒）
     */
    fun estimateProcessingTime(photoCount: Int): Long {
        // 假设平均每张照片处理需要 500ms
        return photoCount * 500L
    }

    /**
     * 检查是否有足够的存储空间
     */
    fun hasEnoughStorage(estimatedFileSize: Long = 5 * 1024 * 1024L): Boolean {
        return fileManager.getAvailableStorage() > estimatedFileSize
    }

    /**
     * 获取处理统计信息
     */
    fun getProcessingStats(results: List<BatchResult>): ProcessingStats {
        val totalTime = results.sumOf { it.processingTime }
        val successCount = results.count { it.success }
        val failedCount = results.size - successCount
        val avgTime = if (results.isNotEmpty()) totalTime / results.size else 0

        return ProcessingStats(
            totalCount = results.size,
            successCount = successCount,
            failedCount = failedCount,
            totalProcessingTime = totalTime,
            averageProcessingTime = avgTime,
            successRate = if (results.isNotEmpty()) successCount.toFloat() / results.size else 0f
        )
    }

    data class ProcessingStats(
        val totalCount: Int,
        val successCount: Int,
        val failedCount: Int,
        val totalProcessingTime: Long,
        val averageProcessingTime: Long,
        val successRate: Float
    )
}
