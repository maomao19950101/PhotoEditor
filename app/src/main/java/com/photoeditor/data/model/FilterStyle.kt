package com.photoeditor.data.model

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

/**
 * 滤镜风格配置数据类
 */
@Parcelize
data class FilterStyle(
    val id: String,
    val name: String,
    val description: String = "",
    @DrawableRes val previewResId: Int = 0,
    
    // 基础参数
    val brightness: Float = 1.0f,        // 亮度
    val contrast: Float = 1.0f,          // 对比度
    val saturation: Float = 1.0f,        // 饱和度
    
    // 色温色调
    val temperature: Float = 0.0f,       // 色温 (-1.0 冷色 ~ 1.0 暖色)
    val tint: Float = 0.0f,              // 色调 (-1.0 绿色 ~ 1.0 洋红)
    val warmth: Float = 0.0f,            // 暖色调
    
    // 高级参数
    val highlights: Float = 0.0f,        // 高光 (-1.0 ~ 1.0)
    val shadows: Float = 0.0f,           // 阴影 (-1.0 ~ 1.0)
    val fade: Float = 0.0f,              // 褪色/柔光效果
    val grain: Float = 0.0f,             // 颗粒感 (0.0 ~ 1.0)
    val vignette: Float = 0.0f,          // 暗角 (0.0 ~ 1.0)
    val sharpen: Float = 0.0f,           // 锐化 (0.0 ~ 1.0)
    
    // 颜色查找表 (可选)
    val lutPath: String? = null,
    
    // 是否属于AI智能滤镜
    val isAiFilter: Boolean = false,
    val aiSceneType: String? = null
) : Parcelable {
    
    companion object {
        /**
         * 预设滤镜库
         */
        fun getPresetFilters(): List<FilterStyle> = listOf(
            // 1. 清透感 - 高亮度、低对比、冷白皮
            FilterStyle(
                id = "qingtou",
                name = "清透感",
                description = "高亮度、低对比、冷白皮",
                brightness = 1.2f,
                contrast = 0.9f,
                saturation = 0.95f,
                temperature = -0.1f,
                highlights = 0.1f,
                shadows = 0.15f,
                fade = 0.05f
            ),
            
            // 2. 胶片复古 - 暖色调、颗粒感、暗角
            FilterStyle(
                id = "jiaopian",
                name = "胶片复古",
                description = "暖色调、颗粒感、暗角",
                brightness = 0.95f,
                contrast = 1.1f,
                saturation = 0.85f,
                temperature = 0.15f,
                grain = 0.3f,
                vignette = 0.4f,
                fade = 0.15f
            ),
            
            // 3. 日系清新 - 低饱和、偏绿、柔和
            FilterStyle(
                id = "rixi",
                name = "日系清新",
                description = "低饱和、偏绿、柔和",
                brightness = 1.1f,
                contrast = 0.85f,
                saturation = 0.8f,
                tint = 0.05f,
                highlights = 0.2f,
                shadows = 0.1f,
                fade = 0.1f
            ),
            
            // 4. 法式浪漫 - 暖黄、柔光、朦胧
            FilterStyle(
                id = "fashi",
                name = "法式浪漫",
                description = "暖黄、柔光、朦胧",
                brightness = 1.05f,
                contrast = 0.9f,
                saturation = 0.9f,
                warmth = 0.15f,
                temperature = 0.1f,
                fade = 0.2f,
                highlights = 0.05f
            ),
            
            // 5. 夜景霓虹 - 青橙对比、高饱和
            FilterStyle(
                id = "yejing",
                name = "夜景霓虹",
                description = "青橙对比、高饱和",
                brightness = 0.9f,
                contrast = 1.2f,
                saturation = 1.3f,
                highlights = -0.2f,
                shadows = 0.2f,
                temperature = -0.15f,
                sharpen = 0.3f
            ),
            
            // 6. ins风 - 低饱和、偏灰、高级感
            FilterStyle(
                id = "ins",
                name = "ins风",
                description = "低饱和、偏灰、高级感",
                brightness = 1.0f,
                contrast = 0.95f,
                saturation = 0.7f,
                fade = 0.15f,
                highlights = 0.1f,
                shadows = -0.05f
            ),
            
            // 7. 黑白经典
            FilterStyle(
                id = "heibai",
                name = "黑白经典",
                description = "经典黑白胶片风格",
                saturation = 0.0f,
                contrast = 1.1f,
                brightness = 1.05f,
                grain = 0.15f,
                vignette = 0.2f
            ),
            
            // 8. 美食鲜艳
            FilterStyle(
                id = "meishi",
                name = "美食鲜艳",
                description = "增强食物色彩",
                saturation = 1.25f,
                contrast = 1.1f,
                brightness = 1.1f,
                warmth = 0.1f,
                sharpen = 0.25f
            ),
            
            // 9. 人像美颜
            FilterStyle(
                id = "renxiang",
                name = "人像美颜",
                description = "优化肤色和质感",
                brightness = 1.05f,
                contrast = 0.95f,
                saturation = 0.9f,
                warmth = 0.05f,
                fade = 0.08f,
                sharpen = 0.15f
            ),
            
            // 10. 风景大片
            FilterStyle(
                id = "fengjing",
                name = "风景大片",
                description = "增强风景层次感",
                saturation = 1.15f,
                contrast = 1.15f,
                highlights = -0.15f,
                shadows = 0.15f,
                sharpen = 0.3f,
                vibrance = 0.2f
            )
        )
        
        // 扩展属性：鲜艳度
        private var FilterStyle.vibrance: Float
            get() = 0f
            set(_) {}
        
        /**
         * 根据ID获取滤镜
         */
        fun getFilterById(id: String): FilterStyle? {
            return getPresetFilters().find { it.id == id }
        }
        
        /**
         * 创建自定义滤镜
         */
        fun createCustomFilter(
            name: String,
            brightness: Float = 1.0f,
            contrast: Float = 1.0f,
            saturation: Float = 1.0f
        ): FilterStyle {
            return FilterStyle(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation
            )
        }
    }
}
