package com.hstream.android

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy

class EpisodeAdapter(
    private val items: MutableList<VideoItem>,
    private val onItemClick: (VideoItem, Int) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

    fun getItems(): List<VideoItem> = items

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageThumbnail: ImageView = view.findViewById(R.id.imgEpisodeThumbnail)
        val textTitle: TextView = view.findViewById(R.id.txtEpisodeTitle)
        var cycleHandler: Handler? = null
        var isCycling = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return ViewHolder(view)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cycleHandler?.removeCallbacksAndMessages(null)
        holder.cycleHandler = null
        holder.isCycling = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textTitle.text = item.title

        holder.cycleHandler?.removeCallbacksAndMessages(null)
        holder.isCycling = false

        holder.imageThumbnail.load(item.posterUrl) {
            crossfade(true)
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item, holder.adapterPosition)
        }

        val hasGallery = item.posterUrl.contains("-thumbnail.webp")
        if (hasGallery) {
            holder.itemView.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val h = Handler(Looper.getMainLooper())
                        holder.cycleHandler = h
                        var frame = 0
                        val r = object : Runnable {
                            override fun run() {
                                holder.isCycling = true
                                val url = item.posterUrl.replace(
                                    Regex("-\\d+-thumbnail\\.webp$"),
                                    "-$frame-thumbnail.webp"
                                )
                                holder.imageThumbnail.load(url) { crossfade(false) }
                                frame = (frame + 1) % 20
                                h.postDelayed(this, 600)
                            }
                        }
                        h.postDelayed(r, 400)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holder.cycleHandler?.removeCallbacksAndMessages(null)
                        holder.cycleHandler = null
                        val wasCycling = holder.isCycling
                        if (holder.isCycling) {
                            holder.isCycling = false
                            holder.imageThumbnail.load(item.posterUrl) { crossfade(false) }
                        }
                        if (!wasCycling && event.actionMasked == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun addItems(newItems: List<VideoItem>) {
        val startPosition = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }
}
