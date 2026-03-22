package com.photoeditor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AI图像增强器
 * 实现美颜、去物体、天空替换、画质增强等功能
 */
class AIEnhancer(private val context: Context) {

    /**
     * 美颜参数
     */
    data class BeautyParams(
        val skinSmooth: Float = 0.5f,      // 磨皮程度 0-1
        val faceSlim: Float = 0.3f,        // 瘦脸程度 0-1
        val eyeEnlarge: Float = 0.2f,      // 大眼程度 0-1
        val skinWhiten: Float = 0.3f,      // 美白程度 0-1
        val acneRemove: Boolean = true,    // 祛痘
        val faceLift: Float = 0.2f         // 小脸程度 0-1
    )

    /**
     * 场景类型
     */
    enum class SceneType {
        PORTRAIT,       // 人像
        LANDSCAPE,      // 风景
        FOOD,           // 美食
        DOCUMENT,       // 文档
        NIGHT,          // 夜景
        BACKLIGHT,      // 逆光
        TEXTURE,        // 纹理
        DEFAULT         // 默认
    }

    /**
     * 天空类型
     */
    enum class SkyType {
        BLUE,           // 蓝天
        SUNSET,         // 日落
        STARRY,         // 星空
        CLOUDY,         // 多云
        RAINBOW,        // 彩虹
        NIGHT,          // 夜晚
        AURORA          // 极光
    }

    /**
     * 智能美颜
     */
    suspend fun applyBeauty(bitmap: Bitmap, params: BeautyParams): Bitmap =
        withContext(Dispatchers.Default) {
            var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            // 磨皮处理
            if (params.skinSmooth > 0) {
                result = applySkinSmoothing(result, params.skinSmooth)
            }

            // 美白处理
            if (params.skinWhiten > 0) {
                result = applySkinWhitening(result, params.skinWhiten)
            }

            // 锐化（补偿磨皮损失的清晰度）
            result = applySmartSharpen(result, 0.3f)

            result
        }

    /**
     * 磨皮处理
     * 使用双边滤波保持边缘的平滑算法
     */
    private fun applySkinSmoothing(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val radius = (10 * intensity).toInt().coerceIn(3, 15)
        val sigmaColor = 30f * intensity
        val sigmaSpace = 10f * intensity

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val resultPixels = IntArray(pixels.size)

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0
                var weightSum = 0.0

                val centerIdx = y * bitmap.width + x
                val centerColor = pixels[centerIdx]
                val centerR = Color.red(centerColor)
                val centerG = Color.green(centerColor)
                val centerB = Color.blue(centerColor)

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val ny = (y + dy).coerceIn(0, bitmap.height - 1)
                        val nx = (x + dx).coerceIn(0, bitmap.width - 1)
                        val idx = ny * bitmap.width + nx

                        val color = pixels[idx]
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)

                        // 空间权重
                        val spaceDist = sqrt((dx * dx + dy * dy).toDouble())
                        val spaceWeight = kotlin.math.exp(-spaceDist * spaceDist / (2 * sigmaSpace * sigmaSpace))

                        // 颜色权重
                        val colorDist = sqrt(
                            ((r - centerR) * (r - centerR) +
                             (g - centerG) * (g - centerG) +
                             (b - centerB) * (b - centerB)).toDouble()
                        )
                        val colorWeight = kotlin.math.exp(-colorDist * colorDist / (2 * sigmaColor * sigmaColor))

                        val weight = spaceWeight * colorWeight
                        sumR += r * weight
                        sumG += g * weight
                        sumB += b * weight
                        weightSum += weight
                    }
                }

                val newR = (sumR / weightSum).toInt().coerceIn(0, 255)
                val newG = (sumG / weightSum).toInt().coerceIn(0, 255)
                val newB = (sumB / weightSum).toInt().coerceIn(0, 255)
                resultPixels[centerIdx] = Color.rgb(newR, newG, newB)
            }
        }

        result.setPixels(resultPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    /**
     * 美白处理
     */
    private fun applySkinWhitening(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val alpha = (intensity * 40).toInt()
        val paint = Paint().apply {
            color = Color.argb(alpha, 255, 255, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)

        return result
    }

    /**
     * 智能锐化
     */
    private fun applySmartSharpen(bitmap: Bitmap, intensity: Float): Bitmap {
        // 使用拉普拉斯算子锐化
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val kernel = arrayOf(
            intArrayOf(0, -1, 0),
            intArrayOf(-1, 5, -1),
            intArrayOf(0, -1, 0)
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val resultPixels = IntArray(pixels.size)

        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                var sumR = 0
                var sumG = 0
                var sumB = 0

                for (ky in 0..2) {
                    for (kx in 0..2) {
                        val idx = (y + ky - 1) * bitmap.width + (x + kx - 1)
                        val color = pixels[idx]
                        val weight = kernel[ky][kx]
                        sumR += Color.red(color) * weight
                        sumG += Color.green(color) * weight
                        sumB += Color.blue(color) * weight
                    }
                }

                val idx = y * bitmap.width + x
                val origColor = pixels[idx]
                val newR = (Color.red(origColor) * (1 - intensity) + sumR * intensity).toInt().coerceIn(0, 255)
                val newG = (Color.green(origColor) * (1 - intensity) + sumG * intensity).toInt().coerceIn(0, 255)
                val newB = (Color.blue(origColor) * (1 - intensity) + sumB * intensity).toInt().coerceIn(0, 255)
                resultPixels[idx] = Color.rgb(newR, newG, newB)
            }
        }

        result.setPixels(resultPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    /**
     * 去路人/去杂物 - 智能修复
     */
    suspend fun removeObject(bitmap: Bitmap, maskBitmap: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            // 使用简单的邻近像素填充算法
            // 实际项目中应该使用更先进的深度学习模型
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val resultPixels = IntArray(result.width * result.height)
            result.getPixels(resultPixels, 0, result.width, 0, 0, result.width, result.height)

            val maskPixels = IntArray(maskBitmap.width * maskBitmap.height)
            maskBitmap.getPixels(maskPixels, 0, maskBitmap.width, 0, 0, maskBitmap.width, maskBitmap.height)

            // 标记需要修复的区域
            val repairMask = BooleanArray(resultPixels.size)
            for (i in maskPixels.indices) {
                repairMask[i] = Color.alpha(maskPixels[i]) > 128
            }

            // 多轮扩散填充
            repeat(5) {
                val newPixels = resultPixels.copyOf()
                for (y in 1 until result.height - 1) {
                    for (x in 1 until result.width - 1) {
                        val idx = y * result.width + x
                        if (repairMask[idx]) {
                            // 收集周围有效像素
                            var sumR = 0
                            var sumG = 0
                            var sumB = 0
                            var count = 0

                            for (dy in -1..1) {
                                for (dx in -1..1) {
                                    val nidx = (y + dy) * result.width + (x + dx)
                                    if (!repairMask[nidx]) {
                                        val color = resultPixels[nidx]
                                        sumR += Color.red(color)
                                        sumG += Color.green(color)
                                        sumB += Color.blue(color)
                                        count++
                                    }
                                }
                            }

                            if (count > 0) {
                                newPixels[idx] = Color.rgb(
                                    sumR / count,
                                    sumG / count,
                                    sumB / count
                                )
                            }
                        }
                    }
                }
                System.arraycopy(newPixels, 0, resultPixels, 0, resultPixels.size)
            }

            result.setPixels(resultPixels, 0, result.width, 0, 0, result.width, result.height)
            result
        }

    /**
     * 天空替换
     */
    suspend fun replaceSky(bitmap: Bitmap, skyType: SkyType): Bitmap =
        withContext(Dispatchers.Default) {
            // 简化版天空检测和替换
            // 实际项目中应该使用语义分割模型
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)

            // 生成天空渐变
            val skyGradient = createSkyGradient(bitmap.width, bitmap.height, skyType)

            // 粗略检测天空区域（上半部分且偏蓝色）
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ALPHA_8)
            val maskPixels = IntArray(mask.width * mask.height)

            for (y in 0 until bitmap.height / 2) {
                for (x in 0 until bitmap.width) {
                    val idx = y * bitmap.width + x
                    val color = pixels[idx]
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)

                    // 检测蓝色天空
                    val isSky = b > r && b > g && b > 150 && y < bitmap.height * 0.6
                    maskPixels[idx] = if (isSky) Color.alpha(200) else Color.alpha(0)
                }
            }

            mask.setPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)

            // 绘制天空
            val paint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(skyGradient, 0f, 0f, paint)

            mask.recycle()
            skyGradient.recycle()

            result
        }

    /**
     * 创建天空渐变
     */
    private fun createSkyGradient(width: Int, height: Int, type: SkyType): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = when (type) {
            SkyType.BLUE -> intArrayOf(Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"))
            SkyType.SUNSET -> intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FFE66D"))
            SkyType.STARRY -> intArrayOf(Color.parseColor("#0B1026"), Color.parseColor("#2B3266"))
            SkyType.CLOUDY -> intArrayOf(Color.parseColor("#708090"), Color.parseColor("#C0C0C0"))
            SkyType.RAINBOW -> intArrayOf(Color.parseColor("#667eea"), Color.parseColor("#764ba2"))
            SkyType.NIGHT -> intArrayOf(Color.parseColor("#0a0a2e"), Color.parseColor("#16213e"))
            SkyType.AURORA -> intArrayOf(Color.parseColor("#00d2ff"), Color.parseColor("#3a7bd5"))
        }

        val paint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                colors[0], colors[1],
                android.graphics.Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    /**
     * 画质增强
     */
    suspend fun enhanceQuality(bitmap: Bitmap, sceneType: SceneType = SceneType.DEFAULT): Bitmap =
        withContext(Dispatchers.Default) {
            var result = bitmap

            when (sceneType) {
                SceneType.PORTRAIT -> {
                    result = applyBeauty(result, BeautyParams(skinSmooth = 0.3f, skinWhiten = 0.2f))
                }
                SceneType.LANDSCAPE -> {
                    result = enhanceColors(result, 1.15f, 1.1f)
                    result = applySmartSharpen(result, 0.4f)
                }
                SceneType.FOOD -> {
                    result = enhanceColors(result, 1.25f, 1.15f)
                    result = adjustTemperature(result, 0.1f)
                }
                SceneType.NIGHT -> {
                    result = adjustBrightness(result, 0.15f)
                    result = reduceNoise(result)
                }
                SceneType.BACKLIGHT -> {
                    result = adjustShadows(result, 0.3f)
                    result = adjustBrightness(result, 0.1f)
                }
                else -> {
                    result = applySmartSharpen(result, 0.3f)
                    result = enhanceColors(result, 1.1f, 1.05f)
                }
            }

            result
        }

    /**
     * 智能场景识别
     */
    suspend fun detectScene(bitmap: Bitmap): SceneType = withContext(Dispatchers.Default) {
        // 简化版场景识别
        // 实际项目中应该使用CNN分类模型
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var totalBrightness = 0L
        var totalSaturation = 0.0
        var bluePixels = 0
        var skinPixels = 0

        for (color in pixels) {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            // 亮度
            val brightness = (r + g + b) / 3
            totalBrightness += brightness

            // 饱和度
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val saturation = if (max == 0) 0.0 else (max - min).toDouble() / max
            totalSaturation += saturation

            // 检测蓝色（天空/水）
            if (b > r + 20 && b > g + 20) {
                bluePixels++
            }

            // 检测肤色
            if (r > 60 && g > 40 && b > 20 &&
                r > g && g > b &&
                r - g > 15 && r - b > 15) {
                skinPixels++
            }
        }

        val avgBrightness = totalBrightness / pixels.size
        val avgSaturation = totalSaturation / pixels.size
        val blueRatio = bluePixels.toDouble() / pixels.size
        val skinRatio = skinPixels.toDouble() / pixels.size

        when {
            skinRatio > 0.15 -> SceneType.PORTRAIT
            blueRatio > 0.3 -> SceneType.LANDSCAPE
            avgSaturation > 0.5 -> SceneType.FOOD
            avgBrightness < 80 -> SceneType.NIGHT
            avgBrightness > 200 -> SceneType.BACKLIGHT
            else -> SceneType.DEFAULT
        }
    }

    /**
     * 智能调色（AI识别场景自动优化）
     */
    suspend fun smartColorGrading(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val sceneType = detectScene(bitmap)
        enhanceQuality(bitmap, sceneType)
    }

    // 辅助方法
    private fun enhanceColors(bitmap: Bitmap, saturation: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = android.graphics.ColorMatrix().apply {
            setSaturation(saturation)
            val scale = contrast
            val translate = (1 - contrast) * 0.5f * 255
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustTemperature(bitmap: Bitmap, warmth: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = android.graphics.ColorMatrix().apply {
            val r = 1 + warmth * 0.1f
            val b = 1 - warmth * 0.1f
            setScale(r, 1f, b, 1f)
        }

        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustBrightness(bitmap: Bitmap, delta: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = android.graphics.ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, delta * 255,
                0f, 1f, 0f, 0f, delta * 255,
                0f, 0f, 1f, 0f, delta * 255,
                0f, 0f, 0f, 1f, 0f
            ))
        }

        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustShadows(bitmap: Bitmap, lift: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val resultPixels = pixels.map { color ->
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val a = Color.alpha(color)

            // 提亮暗部
            val liftAmount = (1 - (r + g + b) / 765f) * lift * 50

            Color.argb(
                a,
                (r + liftAmount).toInt().coerceIn(0, 255),
                (g + liftAmount).toInt().coerceIn(0, 255),
                (b + liftAmount).toInt().coerceIn(0, 255)
            )
        }.toIntArray()

        result.setPixels(resultPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        // 使用中值滤波降噪
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val resultPixels = IntArray(pixels.size)

        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                val rValues = mutableListOf<Int>()
                val gValues = mutableListOf<Int>()
                val bValues = mutableListOf<Int>()

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val idx = (y + dy) * bitmap.width + (x + dx)
                        val color = pixels[idx]
                        rValues.add(Color.red(color))
                        gValues.add(Color.green(color))
                        bValues.add(Color.blue(color))
                    }
                }

                rValues.sort()
                gValues.sort()
                bValues.sort()

                val idx = y * bitmap.width + x
                resultPixels[idx] = Color.rgb(rValues[4], gValues[4], bValues[4])
            }
        }

        result.setPixels(resultPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
}