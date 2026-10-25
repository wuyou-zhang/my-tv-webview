package com.lizongying.mytv0

import MainViewModel
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.lizongying.mytv0.databinding.SettingBinding
import com.lizongying.mytv0.models.TVModel

class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingBinding.inflate(inflater, container, false)
        val mainActivity = (activity as MainActivity)

        binding.switchBootStartup.isChecked = SP.bootStartup
        binding.switchBootStartup.setOnCheckedChangeListener { _, isChecked ->
            SP.bootStartup = isChecked
            mainActivity.settingActive()
        }

        binding.switchChannelReversal.isChecked = SP.channelReversal
        binding.switchChannelReversal.setOnCheckedChangeListener { _, isChecked ->
            SP.channelReversal = isChecked
            mainActivity.settingActive()
        }

        binding.switchTime.isChecked = SP.time
        binding.switchTime.setOnCheckedChangeListener { _, isChecked ->
            SP.time = isChecked
            mainActivity.showTimeFragment()
            mainActivity.settingActive()
        }

        binding.btnExit.setOnClickListener {
            requireActivity().finish()
        }

        binding.setting.setOnClickListener {
            mainActivity.hideSettingFragment()
        }
        binding.container.setOnClickListener {
            mainActivity.settingActive()
        }
        binding.content.setOnClickListener {
            mainActivity.settingActive()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireActivity()
        viewModel = ViewModelProvider(context)[MainViewModel::class.java]
    }

    fun updateCurrentChannel(tvModel: TVModel?) {
        if (_binding == null || tvModel == null) return
        val context = context ?: return
        val mainActivity = activity as? MainActivity ?: return

        binding.channelTitle.text = tvModel.tv.title
        binding.channelGroup.text = "${tvModel.tv.group} · 台号 ${tvModel.tv.id + 1}"

        val isLocal = tvModel.isLocalVideo()
        val uris = tvModel.tv.uris
        val currentIdx = tvModel.videoIndexValue

        binding.sourceSectionTitle.text = if (isLocal) {
            "选集列表 (${uris.size}集)"
        } else {
            "播放线路 (${uris.size}条线路)"
        }

        binding.sourceListContainer.removeAllViews()

        for (i in uris.indices) {
            val uri = uris[i]
            val isCurrent = (i == currentIdx)

            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                isFocusableInTouchMode = false

                val dp8 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                val dp12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                val dp4 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
                setPadding(dp12, dp8, dp12, dp8)

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp4, 0, dp4)
                }
                layoutParams = lp

                val defaultBg = GradientDrawable().apply {
                    cornerRadius = dp8.toFloat()
                    setColor(if (isCurrent) Color.parseColor("#3300E5FF") else Color.parseColor("#1FFFFFFF"))
                    if (isCurrent) {
                        setStroke(2, Color.parseColor("#00E5FF"))
                    }
                }
                val focusBg = GradientDrawable().apply {
                    cornerRadius = dp8.toFloat()
                    setColor(Color.parseColor("#0096A6"))
                }

                background = defaultBg
                setOnFocusChangeListener { _, hasFocus ->
                    background = if (hasFocus) focusBg else defaultBg
                    if (hasFocus) {
                        mainActivity.settingActive()
                    }
                }

                setOnClickListener {
                    mainActivity.settingActive()
                    tvModel.setVideoIndex(i)
                    if (isLocal) {
                        val key = tvModel.getLocalProgressKey()
                        SP.setLocalEpisode(key, i)
                        SP.setLocalPosition(key, 0L)
                    } else {
                        SP.setChannelSourceIndex(tvModel.tv.id, i)
                    }
                    mainActivity.playTvModel(tvModel)
                    updateCurrentChannel(tvModel)
                    this@SettingFragment.handler.postDelayed({
                        mainActivity.hideFragment(this@SettingFragment)
                    }, 500)
                }
            }

            val titleText = when {
                isLocal -> {
                    val name = uri.substringAfterLast("/").substringBeforeLast(".")
                    if (name.isNotBlank()) "第 ${i + 1} 集：$name" else "第 ${i + 1} 集"
                }
                uri.contains("yangshipin.cn") -> "线路 ${i + 1}：央视频（高清/4K）"
                uri.contains("cctv.com") -> "线路 ${i + 1}：央视网（官方备用）"
                else -> "线路 ${i + 1}"
            }

            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = if (isCurrent) "✓ $titleText" else "   $titleText"
                textSize = 14f
                setTextColor(if (isCurrent) Color.parseColor("#00E5FF") else Color.parseColor("#E0E0E0"))
                isClickable = false
                isFocusable = false
            }
            itemLayout.addView(textView)

            binding.sourceListContainer.addView(itemLayout)

            if (isCurrent) {
                itemLayout.post {
                    itemLayout.requestFocus()
                }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::viewModel.isInitialized) {
            val tvModel = (activity as? MainActivity)?.getPlayingTvModel()
                ?: viewModel.groupModel.getCurrent()
            updateCurrentChannel(tvModel)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
    }
}
