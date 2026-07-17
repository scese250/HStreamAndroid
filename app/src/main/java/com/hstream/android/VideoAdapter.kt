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

class VideoAdapter(
    private val items: MutableList<VideoItem>,
    private val onItemClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagePoster: ImageView = view.findViewById(R.id.imagePoster)
        val textTitle: TextView = view.findViewById(R.id.textTitle)
        var cycleHandler: Handler? = null
        var isCycling = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
            
        val prefs = parent.context.getSharedPreferences("HStreamPrefs", android.content.Context.MODE_PRIVATE)
        val searchDesign = prefs.getString("searchDesign", "cover")
        
        val posterContainer = view.findViewById<android.widget.FrameLayout>(R.id.posterContainer)
        val params = posterContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.dimensionRatio = if (searchDesign == "thumbnail") "16:9" else "2:3"
        posterContainer.layoutParams = params
            
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

        // Cancelar ciclo si el ViewHolder viene de reciclaje
        holder.cycleHandler?.removeCallbacksAndMessages(null)
        holder.isCycling = false

        holder.imagePoster.load(item.posterUrl) {
            crossfade(true)
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }

        // Long-press: ciclar previews 0-19 si la URL sigue el patron gallery-ep-N-frame-thumbnail.webp
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
                                holder.imagePoster.load(url) { crossfade(false) }
                                frame = (frame + 1) % 20
                                h.postDelayed(this, 600)
                            }
                        }
                        h.postDelayed(r, 400) // inicia despues de 400ms = long press
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holder.cycleHandler?.removeCallbacksAndMessages(null)
                        holder.cycleHandler = null
                        val wasCycling = holder.isCycling
                        if (holder.isCycling) {
                            holder.isCycling = false
                            // Restaurar thumbnail original
                            holder.imagePoster.load(item.posterUrl) { crossfade(false) }
                        }
                        // Si no estaba ciclando y fue un UP normal -> click
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

    fun clearItems() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getItems(): List<VideoItem> = items
}
