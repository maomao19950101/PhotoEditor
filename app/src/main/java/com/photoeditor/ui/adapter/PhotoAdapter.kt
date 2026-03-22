package com.photoeditor.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.databinding.ItemPhotoBinding

/**
 * 照片适配器（分页）
 */
class PhotoAdapter(
    private val onPhotoClick: (PhotoItem) -> Unit,
    private val onPhotoLongClick: ((PhotoItem) -> Boolean)? = null
) : PagingDataAdapter<PhotoItem, PhotoAdapter.PhotoViewHolder>(PHOTO_DIFF_CALLBACK) {

    private var selectedPhotos: Set<PhotoItem> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = getItem(position)
        photo?.let {
            holder.bind(it, selectedPhotos.contains(it))
        }
    }

    fun updateSelection(selected: Set<PhotoItem>) {
        selectedPhotos = selected
        notifyDataSetChanged()
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoItem, isSelected: Boolean) {
            Glide.with(binding.root.context)
                .load(photo.uri)
                .centerCrop()
                .into(binding.imagePhoto)

            binding.checkSelected.isVisible = isSelected
            binding.viewOverlay.isVisible = isSelected

            binding.root.setOnClickListener {
                onPhotoClick(photo)
            }

            binding.root.setOnLongClickListener {
                onPhotoLongClick?.invoke(photo) ?: false
            }
        }
    }

    companion object {
        private val PHOTO_DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoItem>() {
            override fun areItemsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
