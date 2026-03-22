package com.photoeditor.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.photoeditor.ai.FilterEngine
import com.photoeditor.data.model.FilterStyle
import com.photoeditor.data.model.WatermarkConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 滤镜仓库
 */
@Singleton
class FilterRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val filterEngine: FilterEngine by lazy { FilterEngine(context) }

    // 当前选中的滤镜
    private val _currentFilter = MutableStateFlow<FilterStyle?>(null)
    val currentFilter: StateFlow<FilterStyle?> = _currentFilter.asStateFlow()

    // 滤镜强度
    private val _filterIntensity = MutableStateFlow(1.0f)
    val filterIntensity: StateFlow<Float> = _filterIntensity.asStateFlow()

    // 预览图缓存
    private val previewCache = mutableMapOf<String, Bitmap>()

    /**
     * 获取所有预设滤镜
     */
    fun getPresetFilters(): List<FilterStyle> {
        return FilterStyle.getPresetFilters()
    }

    /**
     * 根据ID获取滤镜
     */
    fun getFilterById(id: String): FilterStyle? {
        return FilterStyle.getFilterById(id)
    }

    /**
     * 设置当前滤镜
     */
    fun setCurrentFilter(filter: FilterStyle?) {
        _currentFilter.value = filter
    }

    /**
     * 设置滤镜强度
     */
    fun setFilterIntensity(intensity: Float) {
        _filterIntensity.value = intensity.coerceIn(0f, 1f)
    }

    /**
     * 获取滤镜预览
     */
    suspend fun getFilterPreview(
        bitmap: Bitmap,
        filter: FilterStyle,
        previewSize: Int = 200
    ): Bitmap = withContext(Dispatchers.Default) {
        filterEngine.getFilterPreview(bitmap, filter, previewSize)
    }

    /**
     * 应用滤镜
     */
    suspend fun applyFilter(bitmap: Bitmap, filter: FilterStyle): Bitmap =
        withContext(Dispatchers.Default) {
            filterEngine.applyFilter(bitmap, filter)
        }

    /**
     * 应用滤镜（带强度）
     */
    suspend fun applyFilter(bitmap: Bitmap, filter: FilterStyle, intensity: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val adjustedFilter = filterEngine.adjustFilterIntensity(filter, intensity)
            filterEngine.applyFilter(bitmap, adjustedFilter)
        }

    /**
     * 批量获取滤镜预览
     */
    fun getAllFilterPreviews(bitmap: Bitmap, previewSize: Int = 200): Flow<List<Pair<FilterStyle, Bitmap>>> = flow {
        val filters = getPresetFilters()
        val previews = filters.map { filter ->
            val preview = filterEngine.getFilterPreview(bitmap, filter, previewSize)
            filter to preview
        }
        emit(previews)
    }.flowOn(Dispatchers.Default)

    /**
     * 混合两个滤镜
     */
    suspend fun blendFilters(
        filter1: FilterStyle,
        filter2: FilterStyle,
        ratio: Float = 0.5f
    ): FilterStyle = withContext(Dispatchers.Default) {
        filterEngine.blendFilters(filter1, filter2, ratio)
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
        return FilterStyle.createCustomFilter(name, brightness, contrast, saturation)
    }

    /**
     * 清除预览缓存
     */
    fun clearPreviewCache() {
        previewCache.values.forEach { it.recycle() }
        previewCache.clear()
    }

    // ============== 水印相关 ==============

    private val _currentWatermark = MutableStateFlow<WatermarkConfig?>(null)
    val currentWatermark: StateFlow<WatermarkConfig?> = _currentWatermark.asStateFlow()

    /**
     * 设置当前水印
     */
    fun setCurrentWatermark(config: WatermarkConfig?) {
        _currentWatermark.value = config
    }

    /**
     * 获取所有水印预设
     */
    fun getWatermarkPresets(): List<WatermarkConfig> {
        return WatermarkConfig.getPresetStyles()
    }

    /**
     * 创建文字水印
     */
    fun createTextWatermark(text: String): WatermarkConfig {
        return WatermarkConfig.createDefaultText(text)
    }

    /**
     * 创建日期水印
     */
    fun createDateWatermark(): WatermarkConfig {
        return WatermarkConfig.createDateWatermark()
    }

    /**
     * 创建地点水印
     */
    fun createLocationWatermark(location: String): WatermarkConfig {
        return WatermarkConfig.createLocationWatermark(location)
    }

    /**
     * 创建图片水印
     */
    fun createImageWatermark(imageUri: String): WatermarkConfig {
        return WatermarkConfig.createImageWatermark(imageUri)
    }
}
