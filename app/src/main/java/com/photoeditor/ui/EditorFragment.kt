package com.photoeditor.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.photoeditor.R
import com.photoeditor.ai.AIEnhancer
import com.photoeditor.data.model.FilterStyle
import com.photoeditor.databinding.FragmentEditorBinding
import com.photoeditor.ui.adapter.FilterAdapter
import com.photoeditor.ui.viewmodel.EditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 编辑界面Fragment
 */
@AndroidEntryPoint
class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditorViewModel by viewModels()
    private val args: EditorFragmentArgs by navArgs()
    
    private lateinit var filterAdapter: FilterAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupBottomSheet()
        setupFilterRecycler()
        setupTabLayout()
        setupSeekBars()
        setupButtons()
        observeData()
        
        // 加载图片
        val photoUri = Uri.parse(args.photoUri)
        viewModel.loadImage(photoUri)
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                findNavController().navigateUp()
            }
            
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_undo -> {
                        viewModel.undo()
                        true
                    }
                    R.id.action_redo -> {
                        viewModel.redo()
                        true
                    }
                    R.id.action_reset -> {
                        viewModel.reset()
                        true
                    }
                    R.id.action_save -> {
                        viewModel.saveImage()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            peekHeight = 200
        }
    }

    private fun setupFilterRecycler() {
        filterAdapter = FilterAdapter(
            onFilterClick = { filter ->
                viewModel.applyFilter(filter, binding.seekBarIntensity.progress / 100f)
            }
        )
        
        binding.recyclerFilters.adapter = filterAdapter
        filterAdapter.submitList(FilterStyle.getPresetFilters())
    }

    private fun setupTabLayout() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("滤镜"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("美颜"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("调整"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("AI功能"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("水印"))
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showFilterPanel()
                    1 -> showBeautyPanel()
                    2 -> showAdjustPanel()
                    3 -> showAIPanel()
                    4 -> showWatermarkPanel()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSeekBars() {
        // 滤镜强度
        binding.seekBarIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.setFilterIntensity(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 美颜参数
        binding.seekBarSkinSmooth.setOnSeekBarChangeListener(createBeautySeekBarListener { 
            viewModel.setBeautyParams(viewModel.beautyParams.value.copy(skinSmooth = it / 100f))
        })
        binding.seekBarFaceSlim.setOnSeekBarChangeListener(createBeautySeekBarListener { 
            viewModel.setBeautyParams(viewModel.beautyParams.value.copy(faceSlim = it / 100f))
        })
        binding.seekBarEyeEnlarge.setOnSeekBarChangeListener(createBeautySeekBarListener { 
            viewModel.setBeautyParams(viewModel.beautyParams.value.copy(eyeEnlarge = it / 100f))
        })
        binding.seekBarSkinWhiten.setOnSeekBarChangeListener(createBeautySeekBarListener { 
            viewModel.setBeautyParams(viewModel.beautyParams.value.copy(skinWhiten = it / 100f))
        })
    }

    private fun createBeautySeekBarListener(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun setupButtons() {
        binding.btnAiEnhance.setOnClickListener {
            viewModel.applyAutoEnhance()
        }
        
        binding.btnAiColor.setOnClickListener {
            viewModel.applySmartColorGrading()
        }
        
        binding.btnRemoveObject.setOnClickListener {
            Toast.makeText(requireContext(), "去路人功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnReplaceSky.setOnClickListener {
            Toast.makeText(requireContext(), "换天空功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnAddWatermark.setOnClickListener {
            // 显示水印设置对话框
        }
        
        binding.btnCompare.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // 显示原图
                    binding.imagePreview.setImageBitmap(viewModel.originalBitmap.value)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // 显示效果图
                    binding.imagePreview.setImageBitmap(viewModel.previewBitmap.value)
                    true
                }
                else -> false
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.previewBitmap.collect { bitmap ->
                        bitmap?.let {
                            binding.imagePreview.setImageBitmap(it)
                        }
                    }
                }
                
                launch {
                    viewModel.processingState.collect { state ->
                        binding.progressBar.isVisible = state is EditorViewModel.ProcessingState.Processing
                    }
                }
                
                launch {
                    viewModel.saveResult.collect { result ->
                        when (result) {
                            is EditorViewModel.SaveResult.Success -> {
                                Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
                            }
                            is EditorViewModel.SaveResult.Error -> {
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                            }
                            null -> {}
                        }
                    }
                }
                
                launch {
                    viewModel.currentFilter.collect { filter ->
                        filterAdapter.setSelectedFilter(filter?.id)
                    }
                }
                
                launch {
                    viewModel.canUndo.collect { canUndo ->
                        binding.toolbar.menu.findItem(R.id.action_undo)?.isEnabled = canUndo
                    }
                }
            }
        }
    }

    private fun showFilterPanel() {
        binding.panelFilters.isVisible = true
        binding.panelBeauty.isVisible = false
        binding.panelAdjust.isVisible = false
        binding.panelAi.isVisible = false
        binding.panelWatermark.isVisible = false
    }

    private fun showBeautyPanel() {
        binding.panelFilters.isVisible = false
        binding.panelBeauty.isVisible = true
        binding.panelAdjust.isVisible = false
        binding.panelAi.isVisible = false
        binding.panelWatermark.isVisible = false
    }

    private fun showAdjustPanel() {
        binding.panelFilters.isVisible = false
        binding.panelBeauty.isVisible = false
        binding.panelAdjust.isVisible = true
        binding.panelAi.isVisible = false
        binding.panelWatermark.isVisible = false
    }

    private fun showAIPanel() {
        binding.panelFilters.isVisible = false
        binding.panelBeauty.isVisible = false
        binding.panelAdjust.isVisible = false
        binding.panelAi.isVisible = true
        binding.panelWatermark.isVisible = false
    }

    private fun showWatermarkPanel() {
        binding.panelFilters.isVisible = false
        binding.panelBeauty.isVisible = false
        binding.panelAdjust.isVisible = false
        binding.panelAi.isVisible = false
        binding.panelWatermark.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
