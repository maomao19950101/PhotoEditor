package com.photoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import com.photoeditor.data.model.PhotoFolder
import com.photoeditor.data.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 图片工具类
 */
object ImageUtils {

    /**
     * 从URI加载Bitmap（带采样）
     */
    suspend fun loadBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int = 2048,
        maxHeight: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 首先获取图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // 计算采样率
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            // 加载图片
            var bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            // 处理旋转
            val rotation = getImageRotation(context, uri)
            if (rotation != 0) {
                bitmap = rotateBitmap(bitmap, rotation)
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 计算采样率
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * 获取图片旋转角度
     */
    fun getImageRotation(context: Context, uri: Uri): Int {
        return try {
            when (uri.scheme) {
                "content" -> {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getInt(0)
                        } else 0
                    } ?: 0
                }
                "file" -> {
                    val path = uri.path ?: return 0
                    val exif = ExifInterface(path)
                    when (exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 旋转Bitmap
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }

        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        ).also {
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    }

    /**
     * 翻转Bitmap
     */
    fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        if (!horizontal && !vertical) return bitmap

        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            if (vertical) postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
        }

        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    /**
     * 裁剪Bitmap
     */
    fun cropBitmap(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    /**
     * 缩放Bitmap（保持比例）
     */
    fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (ratio >= 1f) return bitmap

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 保存Bitmap到文件
     */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 95
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 复制文件
     */
    suspend fun copyFile(context: Context, sourceUri: Uri, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    /**
     * 获取图片信息
     */
    fun getImageInfo(context: Context, uri: Uri): ImageInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)

                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    mimeType = options.outMimeType ?: "image/jpeg",
                    size = getFileSize(context, uri)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            when (uri.scheme) {
                "file" -> File(uri.path!!).length()
                "content" -> {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                            if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0
                        } else 0
                    } ?: 0
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取所有相册文件夹
     */
    suspend fun getPhotoFolders(context: Context): List<PhotoFolder> =
        withContext(Dispatchers.IO) {
            val folders = mutableMapOf<String, PhotoFolder>()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val bucketId = cursor.getString(bucketIdColumn)
                    val bucketName = cursor.getString(bucketNameColumn)
                    val path = cursor.getString(dataColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    val folder = folders[bucketId] ?: PhotoFolder(
                        id = bucketId,
                        name = bucketName,
                        path = File(path).parent ?: "",
                        coverUri = uri
                    )

                    folders[bucketId] = folder.copy(photoCount = folder.photoCount + 1)
                }
            }

            folders.values.toList()
        }

    /**
     * 获取文件夹中的照片
     */
    suspend fun getPhotosInFolder(context: Context, folderId: String? = null): List<PhotoItem> =
        withContext(Dispatchers.IO) {
            val photos = mutableListOf<PhotoItem>()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.ORIENTATION,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

            val selection = if (folderId != null) {
                "${MediaStore.Images.Media.BUCKET_ID} = ?"
            } else null
            val selectionArgs = if (folderId != null) arrayOf(folderId) else null

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
                val folderNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    val photoItem = PhotoItem.fromMediaStore(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameColumn),
                        path = cursor.getString(pathColumn),
                        size = cursor.getLong(sizeColumn),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        dateAdded = cursor.getLong(dateAddedColumn) * 1000,
                        dateModified = cursor.getLong(dateModifiedColumn) * 1000,
                        mimeType = cursor.getString(mimeTypeColumn) ?: "image/jpeg",
                        orientation = cursor.getInt(orientationColumn),
                        folderName = cursor.getString(folderNameColumn) ?: ""
                    )

                    photos.add(photoItem)
                }
            }

            photos
        }

    /**
     * 回收Bitmap
     */
    fun recycleBitmap(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
    }

    data class ImageInfo(
        val width: Int,
        val height: Int,
        val mimeType: String,
        val size: Long = 0
    )
}
