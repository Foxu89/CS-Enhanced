package com.lagradost.cloudstream3.ui.actor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ItemKnownForGridBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class KnownForAdapter(
    private val items: List<KnownForItem>,
    private val onItemClick: (KnownForItem) -> Unit
) : RecyclerView.Adapter<KnownForAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemKnownForGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KnownForItem, onClick: (KnownForItem) -> Unit) {
            binding.title.text = item.title
            
            if (!item.posterPath.isNullOrEmpty()) {
                binding.poster.loadImage("https://image.tmdb.org/t/p/w200${item.posterPath}")
            } else {
                binding.poster.setImageResource(R.drawable.ic_baseline_movie_24)
            }
            
            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKnownForGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size
}
