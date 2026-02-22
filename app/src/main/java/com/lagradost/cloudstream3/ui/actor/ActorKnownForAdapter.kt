package com.lagradost.cloudstream3.ui.actor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ItemActorKnownForBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class ActorKnownForAdapter(
    private val items: List<ActorKnownForItem>,
    private val onItemClick: (ActorKnownForItem) -> Unit
) : RecyclerView.Adapter<ActorKnownForAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemActorKnownForBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActorKnownForBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            movieTitle.text = item.title
            movieYear.text = item.year
            
            // Versione corretta di loadImage
            if (!item.posterPath.isNullOrEmpty()) {
                moviePoster.loadImage(item.posterPath) {
                    crossfade(true)
                    placeholder(R.drawable.ic_baseline_movie_24)
                    error(R.drawable.ic_baseline_movie_24)
                }
            } else {
                moviePoster.setImageResource(R.drawable.ic_baseline_movie_24)
            }
            
            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount() = items.size
}
