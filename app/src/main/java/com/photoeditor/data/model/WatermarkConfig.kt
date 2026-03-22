package com.photoeditor.data.model

import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 水印配置数据类
 */
@Parcelize
data class WatermarkConfig(
    // 基础设置
    val enabled: Boolean = true,
    val text: String = "",
    val type: WatermarkType = WatermarkType.TEXT,
    
    // 文字样式
    val textColor: Int = Color.WHITE,
    val textSize: Float = 48f,
    val typeface: WatermarkFont = WatermarkFont.DEFAULT,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val opacity: Float = 0.8f,
    
    // 位置
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val marginX: Float = 50f,
    val marginY: Float = 50f,
    
    // 背景
    val hasBackground: Boolean = false,
    val backgroundColor: Int = Color.BLACK,
    val backgroundOpacity: Float = 0.5f,
    val backgroundPadding: Float = 20f,
    val backgroundCornerRadius: Float = 10f,
    
    // 阴影
    val hasShadow: Boolean = true,
    val shadowColor: Int = Color.BLACK,
    val shadowOpacity: Float = 0.5f,
    val shadowRadius: Float = 5f,
    val shadowDx: Float = 2f,
    val shadowDy: Float = 2f,
    
    // 旋转
    val rotation: Float = 0f,
    
    // 图片水印特有
    val imageUri: String? = null,
    val imageScale: Float = 0.15f,
    
    // 自动内容
    val autoAddDate: Boolean = false,
    val dateFormat: String = "yyyy-MM-dd HH:mm",
    val autoAddLocation: Boolean = false,
    val locationText: String = "",
    val autoAddDevice: Boolean = false,
    val deviceText: String = "",
    
    // 批量处理时递增编号
    val autoNumbering: Boolean = false,
    val numberingStart: Int = 1,
    val numberingFormat: String = "%03d"
) : Parcelable {
    
    /**
     * 获取完整水印文本
     */
    fun getFullText(index: Int = 0): String {
        val parts = mutableListOf<String>()
        
        if (text.isNotEmpty()) {
            parts.add(text)
        }
        
        if (autoAddDate) {
            val date = java.text.SimpleDateFormat(dateFormat, java.util.Locale.getDefault())
                .format(java.util.Date())
            parts.add(date)
        }
        
        if (autoAddLocation && locationText.isNotEmpty()) {
            parts.add(locationText)
        }
        
        if (autoAddDevice && deviceText.isNotEmpty()) {
            parts.add(deviceText)
        }
        
        if (autoNumbering) {
            parts.add(String.format(numberingFormat, numberingStart + index))
        }
        
        return parts.joinToString(" | ")
    }
    
    /**
     * 获取实际不透明度（0-255）
     */
    fun getAlpha(): Int {
        return (opacity * 255).toInt().coerceIn(0, 255)
    }
    
    /**
     * 获取背景不透明度
     */
    fun getBackgroundAlpha(): Int {
        return (backgroundOpacity * 255).toInt().coerceIn(0, 255)
    }
    
    /**
     * 获取带透明度的文字颜色
     */
    fun getTextColorWithAlpha(): Int {
        return (textColor and 0x00FFFFFF) or (getAlpha() shl 24)
    }
    
    /**
     * 获取带透明度的背景颜色
     */
    fun getBackgroundColorWithAlpha(): Int {
        return (backgroundColor and 0x00FFFFFF) or (getBackgroundAlpha() shl 24)
    }
    
    /**
     * 获取带透明度的阴影颜色
     */
    fun getShadowColorWithAlpha(): Int {
        val shadowAlpha = (shadowOpacity * 255).toInt().coerceIn(0, 255)
        return (shadowColor and 0x00FFFFFF) or (shadowAlpha shl 24)
    }
    
    companion object {
        /**
         * 创建默认文字水印
         */
        fun createDefaultText(text: String): WatermarkConfig {
            return WatermarkConfig(
                text = text,
                type = WatermarkType.TEXT,
                position = WatermarkPosition.BOTTOM_RIGHT,
                opacity = 0.8f
            )
        }
        
        /**
         * 创建日期水印
         */
        fun createDateWatermark(): WatermarkConfig {
            return WatermarkConfig(
                type = WatermarkType.AUTO_DATE,
                autoAddDate = true,
                position = WatermarkPosition.BOTTOM_RIGHT,
                textSize = 36f,
                opacity = 0.7f
            )
        }
        
        /**
         * 创建地点水印
         */
        fun createLocationWatermark(location: String): WatermarkConfig {
            return WatermarkConfig(
                type = WatermarkType.AUTO_LOCATION,
                autoAddLocation = true,
                locationText = location,
                position = WatermarkPosition.BOTTOM_LEFT,
                textSize = 36f,
                opacity = 0.7f
            )
        }
        
        /**
         * 创建图片水印
         */
        fun createImageWatermark(imageUri: String): WatermarkConfig {
            return WatermarkConfig(
                type = WatermarkType.IMAGE,
                imageUri = imageUri,
                position = WatermarkPosition.CENTER,
                imageScale = 0.2f,
                opacity = 0.5f
            )
        }
        
        /**
         * 预设水印样式
         */
        fun getPresetStyles(): List<WatermarkConfig> = listOf(
            // 右下角白色文字
            WatermarkConfig(
                text = "AI修图大师",
                position = WatermarkPosition.BOTTOM_RIGHT,
                textColor = Color.WHITE,
                hasShadow = true,
                textSize = 48f
            ),
            // 底部居中
            WatermarkConfig(
                text = "Photo by AI",
                position = WatermarkPosition.BOTTOM_CENTER,
                textColor = Color.WHITE,
                hasBackground = true,
                backgroundOpacity = 0.3f,
                textSize = 40f
            ),
            // 日期水印
            WatermarkConfig(
                autoAddDate = true,
                dateFormat = "yyyy.MM.dd",
                position = WatermarkPosition.BOTTOM_LEFT,
                textColor = Color.WHITE,
                textSize = 32f,
                opacity = 0.6f
            ),
            // 全屏对角线
            WatermarkConfig(
                text = "SAMPLE",
                position = WatermarkPosition.CENTER,
                rotation = -45f,
                textSize = 120f,
                opacity = 0.15f,
                textColor = Color.GRAY
            )
        )
    }
}

/**
 * 水印类型
 */
enum class WatermarkType {
    TEXT,           // 文字水印
    IMAGE,          // 图片水印
    AUTO_DATE,      // 自动日期
    AUTO_LOCATION,  // 自动地点
    AUTO_DEVICE,    // 自动设备信息
    QR_CODE,        // 二维码
    SIGNATURE       // 签名
}

/**
 * 水印位置
 */
enum class WatermarkPosition {
    TOP_LEFT,       // 左上
    TOP_CENTER,     // 顶部居中
    TOP_RIGHT,      // 右上
    CENTER_LEFT,    // 左中
    CENTER,         // 居中
    CENTER_RIGHT,   // 右中
    BOTTOM_LEFT,    // 左下
    BOTTOM_CENTER,  // 底部居中
    BOTTOM_RIGHT,   // 右下
    CUSTOM          // 自定义位置
}

/**
 * 水印字体
 */
enum class WatermarkFont {
    DEFAULT,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    CASUAL,
    CURSIVE,
    SANS_SERIF_SMALLCAPS,
    SANS_SERIF_CONDENSED,
    SANS_SERIF_CONDENSED_LIGHT;
    
    fun toAndroidTypeface(): Typeface {
        return when (this) {
            DEFAULT -> Typeface.DEFAULT
            SANS_SERIF -> Typeface.SANS_SERIF
            SERIF -> Typeface.SERIF
            MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }
}

/**
 * 水印模板
 */
data class WatermarkTemplate(
    val id: String,
    val name: String,
    val config: WatermarkConfig,
    val previewResId: Int = 0,
    val isBuiltIn: Boolean = true
)
