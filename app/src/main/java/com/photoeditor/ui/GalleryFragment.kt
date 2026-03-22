package com.photoeditor.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photoeditor.R
import com.photoeditor.data.model.PhotoItem
import com.photoeditor.databinding.FragmentGalleryBinding
import com.photoeditor.ui.adapter.PhotoAdapter
import com.photoeditor.ui.viewmodel.GalleryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 相册Fragment
 */
@AndroidEntryPoint
class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by activityViewModels()
    private lateinit var photoAdapter: PhotoAdapter

    private val pickMultipleImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            handleSelectedUris(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupToolbar()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo ->
                if (viewModel.isSelectionMode.value) {
                    viewModel.togglePhotoSelection(photo)
                    photoAdapter.updateSelection(viewModel.selectedPhotos.value)
                } else {
                    navigateToEditor(photo)
                }
            },
            onPhotoLongClick = { photo ->
                if (!viewModel.isSelectionMode.value) {
                    viewModel.enterSelectionMode()
                    viewModel.selectPhoto(photo)
                    photoAdapter.updateSelection(viewModel.selectedPhotos.value)
                    updateSelectionUI()
                }
                true
            }
        )

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = photoAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_import -> {
                        pickMultipleImages.launch("image/*")
                        true
                    }
                    R.id.action_sort -> {
                        showSortDialog()
                        true
                    }
                    R.id.action_select_all -> {
                        // 全选当前可见的照片
                        true
                    }
                    else -> false
                }
            }
        }

        binding.btnCloseSelection.setOnClickListener {
            viewModel.exitSelectionMode()
            photoAdapter.updateSelection(emptySet())
            updateSelectionUI()
        }

        binding.btnBatchEdit.setOnClickListener {
            navigateToBatchEdit()
        }
    }

    private fun setupFab() {
        binding.fabCamera.setOnClickListener {
            // 打开相机拍照
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.photos.collectLatest { pagingData ->
                        photoAdapter.submitData(pagingData)
                    }
                }

                launch {
                    viewModel.isSelectionMode.collect { isSelectionMode ->
                        binding.selectionToolbar.isVisible = isSelectionMode
                        updateSelectionUI()
                    }
                }

                launch {
                    viewModel.selectedPhotos.collect { selected ->
                        binding.tvSelectedCount.text = getString(
                            R.string.selected_count,
                            selected.size
                        )
                        photoAdapter.updateSelection(selected)
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }
            }
        }
    }

    private fun updateSelectionUI() {
        val isSelectionMode = viewModel.isSelectionMode.value
        binding.selectionToolbar.isVisible = isSelectionMode
        binding.tvSelectedCount.text = getString(
            R.string.selected_count,
            viewModel.selectedPhotos.value.size
        )
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "日期（最新优先）",
            "日期（最早优先）",
            "名称（A-Z）",
            "名称（Z-A）",
            "大小（大-小）",
            "大小（小-大）"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setItems(sortOptions) { _, which ->
                // 应用排序
            }
            .show()
    }

    private fun handleSelectedUris(uris: List<Uri>) {
        if (uris.size == 1) {
            navigateToEditorWithUri(uris[0])
        } else {
            // 批量选择
            val photoUris = uris.map { it.toString() }.toTypedArray()
            navigateToBatchWithUris(photoUris)
        }
    }

    private fun navigateToEditor(photo: PhotoItem) {
        val action = GalleryFragmentDirections.actionGalleryToEditor(
            photoUri = photo.uri.toString()
        )
        findNavController().navigate(action)
    }

    private fun navigateToEditorWithUri(uri: Uri) {
        val action = GalleryFragmentDirections.actionGalleryToEditor(
            photoUri = uri.toString()
        )
        findNavController().navigate(action)
    }

    private fun navigateToBatchEdit() {
        val selected = viewModel.getSelectedPhotosList()
        if (selected.size < 2) {
            Toast.makeText(requireContext(), "请至少选择2张照片", Toast.LENGTH_SHORT).show()
            return
        }

        val photoUris = selected.map { it.uri.toString() }.toTypedArray()
        navigateToBatchWithUris(photoUris)
    }

    private fun navigateToBatchWithUris(photoUris: Array<String>) {
        val action = GalleryFragmentDirections.actionGalleryToBatch(
            photoUris = photoUris
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}