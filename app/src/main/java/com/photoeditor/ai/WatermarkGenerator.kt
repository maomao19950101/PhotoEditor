package com.photoeditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.photoeditor.data.model.WatermarkConfig
import com.photoeditor.data.model.WatermarkPosition
import com.photoeditor.data.model.WatermarkType
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * 水印生成器
 */
class WatermarkGenerator(private val context: Context) {

    /**
     * 生成水印并叠加到图片上
     */
    fun applyWatermark(bitmap: Bitmap, config: WatermarkConfig, index: Int = 0): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        when (config.type) {
            WatermarkType.TEXT -> drawTextWatermark(canvas, bitmap, config, index)
            WatermarkType.IMAGE -> drawImageWatermark(canvas, bitmap, config)
            WatermarkType.AUTO_DATE -> drawAutoDateWatermark(canvas, bitmap, config, index)
            WatermarkType.AUTO_LOCATION -> drawLocationWatermark(canvas, bitmap, config, index)
            WatermarkType.AUTO_DEVICE -> drawDeviceWatermark(canvas, bitmap, config, index)
            WatermarkType.QR_CODE -> drawQRCodeWatermark(canvas, bitmap, config)
            WatermarkType.SIGNATURE -> drawSignatureWatermark(canvas, bitmap, config, index)
        }

        return result
    }

    /**
     * 绘制文字水印
     */
    private fun drawTextWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig, index: Int) {
        val text = config.getFullText(index)
        if (text.isEmpty()) return

        val paint = createTextPaint(config)
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val (x, y) = calculatePosition(
            bitmap.width, bitmap.height,
            bounds.width(), bounds.height(),
            config.position, config.marginX, config.marginY
        )

        // 绘制背景
        if (config.hasBackground) {
            drawBackground(canvas, x, y, bounds, config, paint)
        }

        // 绘制阴影
        if (config.hasShadow) {
            drawShadow(canvas, text, x, y, paint, config)
        }

        // 绘制文字
        canvas.drawText(text, x, y, paint)
    }

    /**
     * 绘制图片水印
     */
    private fun drawImageWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig) {
        config.imageUri ?: return

        // 加载水印图片
        val watermarkBitmap = loadImageFromUri(config.imageUri) ?: return

        // 计算缩放
        val scale = config.imageScale
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (watermarkBitmap.height * newWidth / watermarkBitmap.width)

        val scaledWatermark = Bitmap.createScaledBitmap(watermarkBitmap, newWidth, newHeight, true)

        val (x, y) = calculatePosition(
            bitmap.width, bitmap.height,
            newWidth, newHeight,
            config.position, config.marginX, config.marginY
        )

        // 设置透明度
        val paint = Paint().apply {
            alpha = config.getAlpha()
        }

        // 应用旋转
        canvas.save()
        canvas.rotate(config.rotation, x + newWidth / 2f, y + newHeight / 2f)
        canvas.drawBitmap(scaledWatermark, x, y, paint)
        canvas.restore()

        if (scaledWatermark != watermarkBitmap) {
            scaledWatermark.recycle()
        }
    }

    /**
     * 绘制自动日期水印
     */
    private fun drawAutoDateWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig, index: Int) {
        val dateFormat = SimpleDateFormat(config.dateFormat, Locale.getDefault())
        val dateText = dateFormat.format(java.util.Date())

        val fullConfig = config.copy(
            text = dateText,
            autoAddDate = false
        )
        drawTextWatermark(canvas, bitmap, fullConfig, index)
    }

    /**
     * 绘制地点水印
     */
    private fun drawLocationWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig, index: Int) {
        val locationText = config.locationText
        if (locationText.isEmpty()) return

        val fullConfig = config.copy(
            text = "\uD83D\uDCCD $locationText",
            autoAddLocation = false
        )
        drawTextWatermark(canvas, bitmap, fullConfig, index)
    }

    /**
     * 绘制设备水印
     */
    private fun drawDeviceWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig, index: Int) {
        val deviceText = config.deviceText.ifEmpty {
            android.os.Build.MODEL
        }

        val fullConfig = config.copy(
            text = "Shot on $deviceText",
            autoAddDevice = false
        )
        drawTextWatermark(canvas, bitmap, fullConfig, index)
    }

    /**
     * 绘制二维码水印
     */
    private fun drawQRCodeWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig) {
        // 简化实现：绘制一个模拟的二维码图案
        val size = (minOf(bitmap.width, bitmap.height) * 0.15f).toInt()
        val (x, y) = calculatePosition(
            bitmap.width, bitmap.height,
            size, size,
            config.position, config.marginX, config.marginY
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellSize = size / 25f

        val random = java.util.Random(config.text.hashCode().toLong())

        for (row in 0 until 25) {
            for (col in 0 until 25) {
                if (random.nextBoolean()) {
                    paint.color = Color.BLACK
                } else {
                    paint.color = Color.WHITE
                }
                canvas.drawRect(
                    x + col * cellSize,
                    y + row * cellSize,
                    x + (col + 1) * cellSize,
                    y + (row + 1) * cellSize,
                    paint
                )
            }
        }

        // 绘制定位点
        paint.color = Color.BLACK
        drawQRPositionPattern(canvas, x, y, cellSize)
        drawQRPositionPattern(canvas, x + size - 7 * cellSize, y, cellSize)
        drawQRPositionPattern(canvas, x, y + size - 7 * cellSize, cellSize)
    }

    /**
     * 绘制二维码定位图案
     */
    private fun drawQRPositionPattern(canvas: Canvas, x: Float, y: Float, cellSize: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 外框
        paint.color = Color.BLACK
        canvas.drawRect(x, y, x + 7 * cellSize, y + 7 * cellSize, paint)

        // 白边
        paint.color = Color.WHITE
        canvas.drawRect(
            x + cellSize, y + cellSize,
            x + 6 * cellSize, y + 6 * cellSize, paint
        )

        // 中心点
        paint.color = Color.BLACK
        canvas.drawRect(
            x + 2 * cellSize, y + 2 * cellSize,
            x + 5 * cellSize, y + 5 * cellSize, paint
        )
    }

    /**
     * 绘制签名水印
     */
    private fun drawSignatureWatermark(canvas: Canvas, bitmap: Bitmap, config: WatermarkConfig, index: Int) {
        // 模拟手写签名效果
        val signatureConfig = config.copy(
            typeface = com.photoeditor.data.model.WatermarkFont.CURSIVE,
            isItalic = true,
            textSize = config.textSize * 1.2f
        )
        drawTextWatermark(canvas, bitmap, signatureConfig, index)
    }

    /**
     * 创建文字画笔
     */
    private fun createTextPaint(config: WatermarkConfig): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            color = config.getTextColorWithAlpha()
            textSize = config.textSize
            isFakeBoldText = config.isBold
            textSkewX = if (config.isItalic) -0.25f else 0f

            // 设置字体
            typeface = when (config.typeface) {
                com.photoeditor.data.model.WatermarkFont.SANS_SERIF -> Typeface.SANS_SERIF
                com.photoeditor.data.model.WatermarkFont.SERIF -> Typeface.SERIF
                com.photoeditor.data.model.WatermarkFont.MONOSPACE -> Typeface.MONOSPACE
                com.photoeditor.data.model.WatermarkFont.CURSIVE ->
                    Typeface.create("cursive", if (config.isBold) Typeface.BOLD else Typeface.NORMAL)
                else -> Typeface.DEFAULT
            }
        }
    }

    /**
     * 绘制背景
     */
    private fun drawBackground(canvas: Canvas, x: Float, y: Float, bounds: Rect, config: WatermarkConfig, paint: Paint) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.getBackgroundColorWithAlpha()
        }

        val padding = config.backgroundPadding
        val rect = RectF(
            x - padding,
            y + bounds.top - padding,
            x + bounds.width() + padding,
            y + bounds.bottom + padding
        )

        canvas.drawRoundRect(rect, config.backgroundCornerRadius, config.backgroundCornerRadius, bgPaint)
    }

    /**
     * 绘制阴影
     */
    private fun drawShadow(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, config: WatermarkConfig) {
        val shadowPaint = Paint(paint).apply {
            color = config.getShadowColorWithAlpha()
        }
        canvas.drawText(text, x + config.shadowDx, y + config.shadowDy, shadowPaint)
    }

    /**
     * 计算位置
     */
    private fun calculatePosition(
        bitmapWidth: Int, bitmapHeight: Int,
        contentWidth: Int, contentHeight: Int,
        position: WatermarkPosition,
        marginX: Float, marginY: Float
    ): Pair<Float, Float> {
        return when (position) {
            WatermarkPosition.TOP_LEFT ->
                marginX to marginY + contentHeight
            WatermarkPosition.TOP_CENTER ->
                (bitmapWidth - contentWidth) / 2f to marginY + contentHeight
            WatermarkPosition.TOP_RIGHT ->
                (bitmapWidth - contentWidth - marginX) to marginY + contentHeight
            WatermarkPosition.CENTER_LEFT ->
                marginX to (bitmapHeight + contentHeight) / 2f
            WatermarkPosition.CENTER ->
                (bitmapWidth - contentWidth) / 2f to (bitmapHeight + contentHeight) / 2f
            WatermarkPosition.CENTER_RIGHT ->
                (bitmapWidth - contentWidth - marginX) to (bitmapHeight + contentHeight) / 2f
            WatermarkPosition.BOTTOM_LEFT ->
                marginX to (bitmapHeight - marginY)
            WatermarkPosition.BOTTOM_CENTER ->
                (bitmapWidth - contentWidth) / 2f to (bitmapHeight - marginY)
            WatermarkPosition.BOTTOM_RIGHT ->
                (bitmapWidth - contentWidth - marginX) to (bitmapHeight - marginY)
            WatermarkPosition.CUSTOM ->
                marginX to marginY
        }
    }

    /**
     * 从URI加载图片
     */
    private fun loadImageFromUri(uriString: String): Bitmap? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 批量添加水印
     */
    fun batchApplyWatermarks(bitmap: Bitmap, configs: List<WatermarkConfig>): Bitmap {
        var result = bitmap
        configs.filter { it.enabled }.forEachIndexed { index, config ->
            result = applyWatermark(result, config, index)
        }
        return result
    }

    /**
     * 生成带水印的预览图
     */
    fun generatePreview(bitmap: Bitmap, config: WatermarkConfig): Bitmap {
        // 缩放以加快预览生成
        val maxPreviewSize = 400
        val scale = if (bitmap.width > maxPreviewSize || bitmap.height > maxPreviewSize) {
            maxPreviewSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        } else {
            1f
        }

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

        // 调整配置以适应预览尺寸
        val scaledConfig = config.copy(
            textSize = config.textSize * scale,
            marginX = config.marginX * scale,
            marginY = config.marginY * scale
        )

        return applyWatermark(scaledBitmap, scaledConfig)
    }
}