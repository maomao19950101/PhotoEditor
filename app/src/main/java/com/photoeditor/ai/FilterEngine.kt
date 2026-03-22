package com.photoeditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.core.graphics.withSave
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageRGBFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import com.photoeditor.data.model.FilterStyle
import kotlin.math.max
import kotlin.math.min

/**
 * GPU图像滤镜引擎
 */
class FilterEngine(private val context: Context) {
    
    private val gpuImage: GPUImage = GPUImage(context)
    
    /**
     * 应用滤镜到Bitmap
     */
    fun applyFilter(bitmap: Bitmap, filter: FilterStyle): Bitmap {
        gpuImage.setImage(bitmap)
        
        val filters = mutableListOf<jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter>()
        
        // 亮度
        if (filter.brightness != 1.0f) {
            filters.add(GPUImageBrightnessFilter(filter.brightness - 1.0f))
        }
        
        // 对比度
        if (filter.contrast != 1.0f) {
            filters.add(GPUImageContrastFilter(filter.contrast))
        }
        
        // 饱和度
        if (filter.saturation != 1.0f) {
            filters.add(GPUImageSaturationFilter(filter.saturation))
        }
        
        // 色温
        if (filter.temperature != 0.0f) {
            filters.add(GPUImageWhiteBalanceFilter(5000f + filter.temperature * 3000f, 0f))
        }
        
        // 高光阴影
        if (filter.highlights != 0.0f || filter.shadows != 0.0f) {
            filters.add(GPUImageHighlightShadowFilter(
                max(0f, 1.0f + filter.highlights),
                max(0f, 1.0f + filter.shadows)
            ))
        }
        
        // 锐化
        if (filter.sharpen > 0f) {
            filters.add(GPUImageSharpenFilter(filter.sharpen))
        }
        
        // 组合滤镜
        val groupFilter = jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup(filters)
        gpuImage.setFilter(groupFilter)
        
        var result = gpuImage.bitmapWithFilterApplied
        
        // 应用额外效果（GPUImage不支持的效果）
        result = applyAdditionalEffects(result, filter)
        
        return result
    }
    
    /**
     * 应用额外效果
     */
    private fun applyAdditionalEffects(bitmap: Bitmap, filter: FilterStyle): Bitmap {
        if (filter.grain <= 0f && filter.vignette <= 0f && filter.fade <= 0f && filter.tint == 0.0f) {
            return bitmap
        }
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        canvas.withSave {
            // 应用褪色效果（降低对比度并添加灰调）
            if (filter.fade > 0f) {
                applyFadeEffect(canvas, result, filter.fade)
            }
            
            // 应用色调调整
            if (filter.tint != 0.0f) {
                applyTintEffect(canvas, result, filter.tint)
            }
            
            // 应用颗粒效果
            if (filter.grain > 0f) {
                applyGrainEffect(canvas, result, filter.grain)
            }
            
            // 应用暗角效果
            if (filter.vignette > 0f) {
                applyVignetteEffect(canvas, result, filter.vignette)
            }
            
            // 应用暖色调
            if (filter.warmth > 0f) {
                applyWarmthEffect(canvas, result, filter.warmth)
            }
        }
        
        return result
    }
    
    /**
     * 应用褪色效果
     */
    private fun applyFadeEffect(canvas: Canvas, bitmap: Bitmap, intensity: Float) {
        val paint = Paint().apply {
            color = android.graphics.Color.argb((intensity * 80).toInt(), 200, 200, 200)
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.SCREEN
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    }
    
    /**
     * 应用色调效果
     */
    private fun applyTintEffect(canvas: Canvas, bitmap: Bitmap, tint: Float) {
        val color = if (tint > 0) {
            android.graphics.Color.argb((tint * 50).toInt(), 255, 0, 255) // 洋红
        } else {
            android.graphics.Color.argb((-tint * 50).toInt(), 0, 255, 0) // 绿色
        }
        
        val paint = Paint().apply {
            color = color
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.OVERLAY
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    }
    
    /**
     * 应用颗粒效果
     */
    private fun applyGrainEffect(canvas: Canvas, bitmap: Bitmap, intensity: Float) {
        val random = java.util.Random(12345) // 固定种子保证一致性
        val grainBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val grainCanvas = Canvas(grainBitmap)
        
        val paint = Paint().apply {
            alpha = (intensity * 40).toInt()
        }
        
        for (i in 0 until bitmap.width step 2) {
            for (j in 0 until bitmap.height step 2) {
                val gray = random.nextInt(256)
                paint.color = android.graphics.Color.argb(
                    (intensity * 30).toInt(), gray, gray, gray
                )
                grainCanvas.drawPoint(i.toFloat(), j.toFloat(), paint)
            }
        }
        
        canvas.drawBitmap(grainBitmap, 0f, 0f, Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.OVERLAY
            )
        })
        grainBitmap.recycle()
    }
    
    /**
     * 应用暗角效果
     */
    private fun applyVignetteEffect(canvas: Canvas, bitmap: Bitmap, intensity: Float) {
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        val maxRadius = kotlin.math.hypot(centerX, centerY)
        
        val gradient = RadialGradient(
            centerX, centerY,
            maxRadius,
            intArrayOf(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.argb((intensity * 180).toInt(), 0, 0, 0)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        
        val paint = Paint().apply {
            isAntiAlias = true
            shader = gradient
        }
        
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    }
    
    /**
     * 应用暖色调效果
     */
    private fun applyWarmthEffect(canvas: Canvas, bitmap: Bitmap, warmth: Float) {
        val paint = Paint().apply {
            color = android.graphics.Color.argb((warmth * 60).toInt(), 255, 200, 100)
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.OVERLAY
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
    }
    
    /**
     * 获取滤镜预览（低分辨率）
     */
    fun getFilterPreview(bitmap: Bitmap, filter: FilterStyle, previewSize: Int = 200): Bitmap {
        // 缩放原图以提高处理速度
        val scale = previewSize.toFloat() / max(bitmap.width, bitmap.height)
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return applyFilter(scaledBitmap, filter)
    }
    
    /**
     * 调整滤镜强度
     */
    fun adjustFilterIntensity(baseFilter: FilterStyle, intensity: Float): FilterStyle {
        val normalizedIntensity = intensity.coerceIn(0f, 1f)
        return baseFilter.copy(
            brightness = 1.0f + (baseFilter.brightness - 1.0f) * normalizedIntensity,
            contrast = 1.0f + (baseFilter.contrast - 1.0f) * normalizedIntensity,
            saturation = 1.0f + (baseFilter.saturation - 1.0f) * normalizedIntensity,
            temperature = baseFilter.temperature * normalizedIntensity,
            tint = baseFilter.tint * normalizedIntensity,
            warmth = baseFilter.warmth * normalizedIntensity,
            highlights = baseFilter.highlights * normalizedIntensity,
            shadows = baseFilter.shadows * normalizedIntensity,
            fade = baseFilter.fade * normalizedIntensity,
            grain = baseFilter.grain * normalizedIntensity,
            vignette = baseFilter.vignette * normalizedIntensity,
            sharpen = baseFilter.sharpen * normalizedIntensity
        )
    }
    
    /**
     * 混合两个滤镜
     */
    fun blendFilters(filter1: FilterStyle, filter2: FilterStyle, ratio: Float = 0.5f): FilterStyle {
        val r = ratio.coerceIn(0f, 1f)
        return FilterStyle(
            id = "blended_${filter1.id}_${filter2.id}",
            name = "${filter1.name}+${filter2.name}",
            brightness = filter1.brightness * (1 - r) + filter2.brightness * r,
            contrast = filter1.contrast * (1 - r) + filter2.contrast * r,
            saturation = filter1.saturation * (1 - r) + filter2.saturation * r,
            temperature = filter1.temperature * (1 - r) + filter2.temperature * r,
            tint = filter1.tint * (1 - r) + filter2.tint * r,
            warmth = filter1.warmth * (1 - r) + filter2.warmth * r,
            highlights = filter1.highlights * (1 - r) + filter2.highlights * r,
            shadows = filter1.shadows * (1 - r) + filter2.shadows * r,
            fade = filter1.fade * (1 - r) + filter2.fade * r,
            grain = filter1.grain * (1 - r) + filter2.grain * r,
            vignette = filter1.vignette * (1 - r) + filter2.vignette * r,
            sharpen = filter1.sharpen * (1 - r) + filter2.sharpen * r
        )
    }
    
    companion object {
        /**
         * 使用RenderScript进行高斯模糊
         */
        fun applyBlur(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, bitmap)
            val output = Allocation.createTyped(rs, input.type)
            
            val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius.coerceIn(0f, 25f))
            script.setInput(input)
            script.forEach(output)
            
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
            output.copyTo(result)
            
            input.destroy()
            output.destroy()
            script.destroy()
            rs.destroy()
            
            return result
        }
    }
}
