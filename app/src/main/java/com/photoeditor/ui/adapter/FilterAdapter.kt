package com.photoeditor.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photoeditor.data.model.FilterStyle
import com.photoeditor.databinding.ItemFilterBinding

/**
 * 滤镜适配器
 */
class FilterAdapter(
    private val onFilterClick: (FilterStyle) -> Unit
) : ListAdapter<FilterStyle, FilterAdapter.FilterViewHolder>(FILTER_DIFF_CALLBACK) {

    private var selectedFilterId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemFilterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = getItem(position)
        holder.bind(filter, filter.id == selectedFilterId)
    }

    fun setSelectedFilter(filterId: String?) {
        val oldId = selectedFilterId
        selectedFilterId = filterId
        // 更新选中和取消选中的项
        currentList.forEachIndexed { index, filter ->
            if (filter.id == oldId || filter.id == filterId) {
                notifyItemChanged(index)
            }
        }
    }

    inner class FilterViewHolder(
        private val binding: ItemFilterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filter: FilterStyle, isSelected: Boolean) {
            binding.tvFilterName.text = filter.name
            binding.tvFilterDesc.text = filter.description

            // 设置选中状态
            if (isSelected) {
                binding.cardFilter.strokeWidth = 4
                binding.cardFilter.strokeColor = ContextCompat.getColor(
                    binding.root.context,
                    android.R.color.holo_purple
                )
            } else {
                binding.cardFilter.strokeWidth = 0
            }

            // 加载预览图（如果有）
            if (filter.previewResId != 0) {
                Glide.with(binding.root.context)
                    .load(filter.previewResId)
                    .centerCrop()
                    .into(binding.imageFilterPreview)
            }

            binding.root.setOnClickListener {
                onFilterClick(filter)
            }
        }
    }

    companion object {
        private val FILTER_DIFF_CALLBACK = object : DiffUtil.ItemCallback<FilterStyle>() {
            override fun areItemsTheSame(oldItem: FilterStyle, newItem: FilterStyle): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: FilterStyle, newItem: FilterStyle): Boolean {
                return oldItem == newItem
            }
        }
    }
}
