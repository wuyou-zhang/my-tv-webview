package com.lizongying.mytv0

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
) : RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

    private var labels: List<String> = emptyList()
    private var currentIndex: Int = -1

    var onEpisodeClicked: ((Int) -> Unit)? = null
    var onEpisodeFocused: (() -> Unit)? = null
    var onKey: ((Int) -> Boolean)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.episode_item, parent, false) as TextView
        val application = context.applicationContext as MyTVApplication
        view.textSize = application.px2PxFont(view.textSize)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.view
        view.text = labels[position]
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        paint(view, position, view.hasFocus())

        // 绑定阶段布局尚未完成，此时设选中不会启动跑马灯，需在布局完成后重新触发
        view.post {
            if (holder.bindingAdapterPosition == position) {
                view.isSelected = false
                view.isSelected = view.hasFocus() || position == currentIndex
            }
        }

        view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            paint(view, position, hasFocus)
            if (hasFocus) {
                onEpisodeFocused?.invoke()
            }
        }

        view.setOnClickListener {
            onEpisodeClicked?.invoke(position)
        }

        view.setOnTouchListener { v, event ->
            if (event?.action == MotionEvent.ACTION_UP) {
                v?.performClick()
                true
            } else {
                false
            }
        }

        view.setOnKeyListener { _, keyCode, event ->
            if (event?.action == KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener onKey?.invoke(keyCode) == true
            }
            false
        }
    }

    private fun paint(view: TextView, position: Int, focused: Boolean) {
        val active = focused || position == currentIndex
        view.setTextColor(
            ContextCompat.getColor(context, if (active) R.color.white else R.color.title_blur)
        )
        view.setBackgroundResource(if (active) R.color.menu_focus else R.color.menu_item)
        // 高亮行跑马灯滚动，便于看全文件名
        view.isSelected = active
    }

    override fun getItemCount() = labels.size

    fun setData(newLabels: List<String>, current: Int) {
        labels = newLabels
        currentIndex = current
        recyclerView.post { notifyDataSetChanged() }
    }

    fun clear() {
        labels = emptyList()
        currentIndex = -1
        notifyDataSetChanged()
    }

    fun focusInitial() {
        if (labels.isEmpty()) return
        val position = if (currentIndex in labels.indices) currentIndex else 0
        // 第三列刚从 GONE 变为 VISIBLE 时需要等待一次布局，否则拿不到 itemView
        recyclerView.postDelayed({
            (recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 0)
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }, 50)
    }

    class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)
}
