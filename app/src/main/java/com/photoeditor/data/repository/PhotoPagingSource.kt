package com.photoeditor.data.repository

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片分页数据源
 */
class PhotoPagingSource(
    private val context: Context,
    private val pageSize: Int = 20,
    private val folderId: String? = null
) : PagingSource<Int, PhotoItem>() {

    override fun getRefreshKey(state: PagingState<Int, PhotoItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PhotoItem> {
        return try {
            val page = params.key ?: 0
            val offset = page * pageSize

            val allPhotos = withContext(Dispatchers.IO) {
                if (folderId != null) {
                    ImageUtils.getPhotosInFolder(context, folderId)
                } else {
                    ImageUtils.getPhotosInFolder(context)
                }
            }

            val photos = allPhotos.drop(offset).take(pageSize)
            val prevKey = if (page > 0) page - 1 else null
            val nextKey = if (offset + pageSize < allPhotos.size) page + 1 else null

            LoadResult.Page(
                data = photos,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
