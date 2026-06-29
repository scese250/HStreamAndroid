package com.hstream.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy

class VideoAdapter(
    private val items: MutableList<VideoItem>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagePoster: ImageView = view.findViewById(R.id.imagePoster)
        val textTitle: TextView = view.findViewById(R.id.textTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textTitle.text = item.title
        
        // Cargar imagen con Coil
        holder.imagePoster.load(item.posterUrl) {
            crossfade(true)
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
            // placeholder(R.drawable.placeholder) opcional
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(item.url)
        }
    }

    override fun getItemCount() = items.size

    fun addItems(newItems: List<VideoItem>) {
        val startPosition = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }
}
