package com.photoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.photoeditor.data.model.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件管理器
 */
class FileManager(private val context: Context) {

    /**
     * 获取应用私有输出目录
     */
    fun getAppOutputDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "PhotoEditor/Output")
        dir.mkdirs()
        return dir
    }

    /**
     * 获取默认输出目录
     */
    fun getDefaultOutputDirectory(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getAppOutputDirectory()
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "AI修图大师"
            )
            dir.mkdirs()
            dir
        }
    }

    /**
     * 获取临时目录
     */
    fun getTempDirectory(): File {
        val dir = File(context.cacheDir, "photo_editor_temp")
        dir.mkdirs()
        return dir
    }

    /**
     * 创建临时文件
     */
    fun createTempFile(prefix: String = "temp", extension: String = ".jpg"): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(getTempDirectory(), "${prefix}_${timestamp}$extension")
    }

    /**
     * 创建输出文件
     */
    fun createOutputFile(name: String? = null, format: ExportFormat = ExportFormat.JPEG_HIGH): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = name ?: "AI_EDIT_${timestamp}"
        val extension = if (format == ExportFormat.PNG) "png" else "jpg"
        return File(getDefaultOutputDirectory(), "${fileName}.$extension")
    }

    /**
     * 保存到相册（公共目录）
     */
    suspend fun saveToGallery(sourceFile: File, displayName: String? = null): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = displayName ?: sourceFile.name
                val mimeType = if (fileName.endsWith(".png")) "image/png" else "image/jpeg"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI修图大师")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: return@withContext null

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)

                    uri
                } else {
                    // Android 9及以下，直接复制到公共目录
                    val destDir = File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "AI修图大师"
                    )
                    destDir.mkdirs()
                    val destFile = File(destDir, fileName)
                    sourceFile.copyTo(destFile, overwrite = true)

                    // 通知媒体库
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    }
                    context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 批量保存到相册
     */
    suspend fun batchSaveToGallery(sourceFiles: List<File>): List<Pair<File, Uri?>> =
        withContext(Dispatchers.IO) {
            sourceFiles.map { file ->
                file to saveToGallery(file)
            }
        }

    /**
     * 获取文件大小
     */
    fun getFileSize(file: File): Long {
        return if (file.exists()) file.length() else 0
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 获取可用存储空间
     */
    fun getAvailableStorage(): Long {
        return try {
            val stat = android.os.StatFs(getDefaultOutputDirectory().path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            blockSize * availableBlocks
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 格式化存储空间
     */
    fun formatStorageSize(size: Long): String {
        return formatFileSize(size)
    }

    /**
     * 检查存储空间是否足够
     */
    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        return getAvailableStorage() > requiredBytes
    }

    /**
     * 清理临时文件
     */
    suspend fun clearTempFiles(): Int = withContext(Dispatchers.IO) {
        val tempDir = getTempDirectory()
        var deletedCount = 0

        tempDir.listFiles()?.forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }

        deletedCount
    }

    /**
     * 清理过期临时文件
     */
    suspend fun clearExpiredTempFiles(maxAgeMillis: Long = 24 * 60 * 60 * 1000): Int =
        withContext(Dispatchers.IO) {
            val tempDir = getTempDirectory()
            val now = System.currentTimeMillis()
            var deletedCount = 0

            tempDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMillis) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            deletedCount
        }

    /**
     * 获取缓存大小
     */
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        context.cacheDir.listFiles()?.forEach { file ->
            size += getFolderSize(file)
        }
        size
    }

    /**
     * 计算文件夹大小
     */
    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                size += getFolderSize(it)
            }
        } else {
            size += file.length()
        }
        return size
    }

    /**
     * 清理缓存
     */
    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                deleteFileOrFolder(file)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 递归删除文件或文件夹
     */
    private fun deleteFileOrFolder(file: File): Boolean {
        return if (file.isDirectory) {
            file.listFiles()?.forEach { deleteFileOrFolder(it) }
            file.delete()
        } else {
            file.delete()
        }
    }

    /**
     * 复制文件
     */
    suspend fun copyFile(source: File, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 移动文件
     */
    suspend fun moveFile(source: File, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            source.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 重命名文件
     */
    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val newFile = File(file.parent, newName)
            file.renameTo(newFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取文件URI
     */
    fun getFileUri(file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * 生成唯一文件名
     */
    fun generateUniqueFileName(prefix: String = "IMG", extension: String = "jpg"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        return "${prefix}_${timestamp}.$extension"
    }

    companion object {
        const val MIN_STORAGE_THRESHOLD = 100 * 1024 * 1024L // 100MB
    }
}
