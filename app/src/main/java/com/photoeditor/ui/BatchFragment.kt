package com.photoeditor.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.databinding.FragmentBatchBinding
import com.photoeditor.ui.adapter.BatchPhotoAdapter
import com.photoeditor.ui.viewmodel.BatchViewModel
import com.photoeditor.utils.BatchProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 批量处理Fragment
 */
@AndroidEntryPoint
class BatchFragment : Fragment() {

    private var _binding: FragmentBatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by viewModels()
    private val args: BatchFragmentArgs by navArgs()

    private lateinit var batchPhotoAdapter: BatchPhotoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilters()
        setupButtons()
        observeData()

        // 加载传入的照片
        val photoUris = args.photoUris?.toList() ?: emptyList()
        if (photoUris.isNotEmpty()) {
            loadPhotosFromUris(photoUris)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                findNavController().navigateUp()
            }

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_add -> {
                        // 添加更多照片
                        true
                    }
                    R.id.action_settings -> {
                        showBatchSettings()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupRecyclerView() {
        batchPhotoAdapter = BatchPhotoAdapter(
            onRemoveClick = { photo ->
                viewModel.removePhoto(photo)
            }
        )

        binding.recyclerPhotos.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = batchPhotoAdapter
        }
    }

    private fun setupFilters() {
        // 设置滤镜选择
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            // 根据选中的滤镜设置
        }
    }

    private fun setupButtons() {
        binding.btnStartProcessing.setOnClickListener {
            if (viewModel.hasEnoughStorage()) {
                viewModel.startBatchProcessing()
            } else {
                Toast.makeText(requireContext(), "存储空间不足", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancelProcessing.setOnClickListener {
            viewModel.cancelProcessing()
        }

        binding.btnViewResults.setOnClickListener {
            // 导航到结果界面
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pendingPhotos.collect { photos ->
                        batchPhotoAdapter.submitList(photos)
                        binding.tvPhotoCount.text = "${photos.size} 张照片"
                        binding.btnStartProcessing.isEnabled = photos.isNotEmpty()
                    }
                }

                launch {
                    viewModel.processingState.collect { state ->
                        updateUIForState(state)
                    }
                }

                launch {
                    viewModel.progress.collect { progress ->
                        updateProgress(progress)
                    }
                }

                launch {
                    viewModel.results.collect { results ->
                        if (results.isNotEmpty()) {
                            val successCount = results.count { it.success }
                            val failedCount = results.size - successCount
                            binding.tvResultSummary.text = "成功: $successCount, 失败: $failedCount"
                        }
                    }
                }
            }
        }
    }

    private fun updateUIForState(state: BatchProcessor.BatchProcessingState) {
        when (state) {
            is BatchProcessor.BatchProcessingState.Idle -> {
                binding.panelControls.isVisible = true
                binding.panelProgress.isVisible = false
                binding.panelResults.isVisible = false
            }
            is BatchProcessor.BatchProcessingState.Preparing,
            is BatchProcessor.BatchProcessingState.Processing -> {
                binding.panelControls.isVisible = false
                binding.panelProgress.isVisible = true
                binding.panelResults.isVisible = false
            }
            is BatchProcessor.BatchProcessingState.Completed,
            is BatchProcessor.BatchProcessingState.Cancelled -> {
                binding.panelControls.isVisible = false
                binding.panelProgress.isVisible = false
                binding.panelResults.isVisible = true
            }
            is BatchProcessor.BatchProcessingState.Error -> {
                binding.panelControls.isVisible = true
                binding.panelProgress.isVisible = false
                binding.panelResults.isVisible = false
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    private fun updateProgress(progress: BatchProcessor.BatchProgress) {
        binding.progressBar.max = progress.totalCount
        binding.progressBar.progress = progress.completedCount
        binding.tvProgress.text = "${progress.completedCount}/${progress.totalCount}"
        binding.tvPercentage.text = "${progress.percentage}%"
        binding.tvCurrentFile.text = progress.currentFileName

        // 格式化剩余时间
        val remaining = progress.estimatedTimeRemaining
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        binding.tvTimeRemaining.text = "预计剩余: ${minutes}分${seconds}秒"
    }

    private fun loadPhotosFromUris(uris: List<String>) {
        val photos = uris.mapIndexed { index, uriString ->
            val uri = Uri.parse(uriString)
            PhotoItem(
                id = index.toString(),
                uri = uri,
                name = "Photo_${index + 1}"
            )
        }
        viewModel.setPendingPhotos(photos)
    }

    private fun showBatchSettings() {
        // 显示批量设置对话框
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}