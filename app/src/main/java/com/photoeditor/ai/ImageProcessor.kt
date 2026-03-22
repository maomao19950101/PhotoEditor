package com.photoeditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 图像处理引擎 - 统一处理接口
 */
class ImageProcessor(private val context: Context) {

    private val filterEngine: FilterEngine by lazy { FilterEngine(context) }
    private val aiEnhancer: AIEnhancer by lazy { AIEnhancer(context) }
    private val watermarkGenerator: WatermarkGenerator by lazy { WatermarkGenerator(context) }

    /**
     * 处理选项
     */
    data class ProcessOptions(
        val filter: com.photoeditor.data.model.FilterStyle? = null,
        val filterIntensity: Float = 1.0f,
        val applyBeauty: Boolean = false,
        val beautyParams: AIEnhancer.BeautyParams = AIEnhancer.BeautyParams(),
        val enhanceQuality: Boolean = false,
        val sceneType: AIEnhancer.SceneType = AIEnhancer.SceneType.DEFAULT,
        val watermarkConfig: com.photoeditor.data.model.WatermarkConfig? = null,
        val rotation: Float = 0f,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false,
        val cropRect: Rect? = null,
        val outputQuality: Int = 95
    )

    /**
     * 处理结果
     */
    sealed class ProcessResult {
        data class Success(val bitmap: Bitmap, val outputUri: Uri? = null) : ProcessResult()
        data class Error(val message: String, val exception: Exception? = null) : ProcessResult()
        object Cancelled : ProcessResult()
    }

    /**
     * 处理单张图片
     */
    suspend fun processImage(
        bitmap: Bitmap,
        options: ProcessOptions,
        saveToFile: Boolean = false,
        outputFile: File? = null
    ): ProcessResult = withContext(Dispatchers.Default) {
        try {
            var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            // 1. 裁剪
            options.cropRect?.let { rect ->
                result = cropBitmap(result, rect)
            }

            // 2. 旋转和翻转
            if (options.rotation != 0f || options.flipHorizontal || options.flipVertical) {
                result = rotateAndFlipBitmap(result, options.rotation, options.flipHorizontal, options.flipVertical)
            }

            // 3. 应用滤镜
            options.filter?.let { filter ->
                val adjustedFilter = if (options.filterIntensity != 1.0f) {
                    filterEngine.adjustFilterIntensity(filter, options.filterIntensity)
                } else {
                    filter
                }
                result = filterEngine.applyFilter(result, adjustedFilter)
            }

            // 4. AI美颜
            if (options.applyBeauty) {
                result = aiEnhancer.applyBeauty(result, options.beautyParams)
            }

            // 5. 画质增强
            if (options.enhanceQuality) {
                result = aiEnhancer.enhanceQuality(result, options.sceneType)
            }

            // 6. 添加水印
            options.watermarkConfig?.let { config ->
                result = watermarkGenerator.applyWatermark(result, config)
            }

            // 保存到文件
            val outputUri = if (saveToFile && outputFile != null) {
                saveBitmapToFile(result, outputFile, options.outputQuality)
                Uri.fromFile(outputFile)
            } else null

            ProcessResult.Success(result, outputUri)
        } catch (e: Exception) {
            ProcessResult.Error("图片处理失败: ${e.message}", e)
        }
    }

    /**
     * 快速预览处理
     */
    suspend fun processPreview(
        bitmap: Bitmap,
        options: ProcessOptions,
        previewSize: Int = 512
    ): Bitmap = withContext(Dispatchers.Default) {
        // 缩小图片以加快处理
        val scale = previewSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val result = processImage(scaledBitmap, options.copy(
            // 预览时降低美颜强度以提高速度
            beautyParams = options.beautyParams.copy(
                skinSmooth = options.beautyParams.skinSmooth * 0.5f
            )
        ))

        when (result) {
            is ProcessResult.Success -> result.bitmap
            else -> scaledBitmap
        }
    }

    /**
     * AI场景识别并自动优化
     */
    suspend fun autoEnhance(bitmap: Bitmap): ProcessResult = withContext(Dispatchers.Default) {
        try {
            val sceneType = aiEnhancer.detectScene(bitmap)
            val enhancedBitmap = aiEnhancer.enhanceQuality(bitmap, sceneType)
            ProcessResult.Success(enhancedBitmap)
        } catch (e: Exception) {
            ProcessResult.Error("自动优化失败: ${e.message}", e)
        }
    }

    /**
     * 智能调色
     */
    suspend fun smartColorGrading(bitmap: Bitmap): ProcessResult = withContext(Dispatchers.Default) {
        try {
            val gradedBitmap = aiEnhancer.smartColorGrading(bitmap)
            ProcessResult.Success(gradedBitmap)
        } catch (e: Exception) {
            ProcessResult.Error("智能调色失败: ${e.message}", e)
        }
    }

    /**
     * 天空替换
     */
    suspend fun replaceSky(bitmap: Bitmap, skyType: AIEnhancer.SkyType): ProcessResult =
        withContext(Dispatchers.Default) {
            try {
                val result = aiEnhancer.replaceSky(bitmap, skyType)
                ProcessResult.Success(result)
            } catch (e: Exception) {
                ProcessResult.Error("天空替换失败: ${e.message}", e)
            }
        }

    /**
     * 去物体/去路人
     */
    suspend fun removeObject(bitmap: Bitmap, maskBitmap: Bitmap): ProcessResult =
        withContext(Dispatchers.Default) {
            try {
                val result = aiEnhancer.removeObject(bitmap, maskBitmap)
                ProcessResult.Success(result)
            } catch (e: Exception) {
                ProcessResult.Error("去除物体失败: ${e.message}", e)
            }
        }

    /**
     * 批量处理
     */
    suspend fun batchProcess(
        bitmaps: List<Bitmap>,
        options: ProcessOptions,
        outputDir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<ProcessResult> = withContext(Dispatchers.Default) {
        bitmaps.mapIndexed { index, bitmap ->
            val outputFile = File(outputDir, "edited_${System.currentTimeMillis()}_$index.jpg")
            val result = processImage(bitmap, options, true, outputFile)
            onProgress(index + 1, bitmaps.size)
            result
        }
    }

    // ============== 基础图像操作 ==============

    /**
     * 裁剪图片
     */
    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height)
        )

        if (safeRect.width() <= 0 || safeRect.height() <= 0) {
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }

    /**
     * 旋转和翻转图片
     */
    fun rotateAndFlipBitmap(
        bitmap: Bitmap,
        degrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        val matrix = Matrix()

        // 旋转
        if (degrees != 0f) {
            matrix.postRotate(degrees, bitmap.width / 2f, bitmap.height / 2f)
        }

        // 翻转
        if (flipHorizontal) {
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        if (flipVertical) {
            matrix.postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 缩放图片
     */
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * 调整图片大小（保持比例）
     */
    fun resizeBitmapMaintainAspect(bitmap: Bitmap, maxSize: Int): Bitmap {
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (ratio >= 1f) return bitmap

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ============== 文件操作 ==============

    /**
     * 保存Bitmap到文件
     */
    fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int = 95): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 保存Bitmap到PNG（支持透明）
     */
    fun saveBitmapToPNG(bitmap: Bitmap, file: File): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从URI加载Bitmap
     */
    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取图片信息
     */
    fun getImageInfo(uri: Uri): ImageInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeStream(input, null, options)

                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    mimeType = options.outMimeType ?: "image/jpeg"
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    data class ImageInfo(
        val width: Int,
        val height: Int,
        val mimeType: String
    )

    /**
     * 释放资源
     */
    fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}