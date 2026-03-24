package com.lagradost.cloudstream3.ui.actor

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.databinding.FragmentActorFilmographyBinding
import com.lagradost.cloudstream3.databinding.ItemFilmographyGridBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ActorFilmographyFragment : Fragment() {
    private var _binding: FragmentActorFilmographyBinding? = null
    private val binding get() = _binding!!

    private var actorId: Int = 0
    private lateinit var actorName: String
    private var actorImage: String? = null
    private var currentFilter = "all"
    private var currentSort = "popularity"

    private val filmographyList = mutableListOf<FilmographyItem>()
    private val filteredList = mutableListOf<FilmographyItem>()
    
    // IL CALLBACK PER LA RICERCA - COME HA FATTO LUI!
    private val searchCallback: (String) -> Unit = { title ->
        QuickSearchFragment.pushSearch(requireActivity(), title)
    }
    
    private val adapter = FilmographyAdapter(
        onItemClick = { item ->
            onFilmographyItemClick(item)
        },
        searchCallback = searchCallback
    )

    data class FilmographyItem(
        val id: Int,
        val title: String,
        val posterPath: String?,
        val releaseDate: String?,
        val character: String?,
        val mediaType: String,
        val popularity: Double,
        val voteAverage: Double,
        val isUpcoming: Boolean
    )

    companion object {
        private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
        private const val TAG = "ActorFilmography"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActorFilmographyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actorId = arguments?.getInt("actor_id") ?: 0
        actorName = arguments?.getString("actor_name") ?: ""
        actorImage = arguments?.getString("actor_image")

        if (actorId == 0) {
            requireActivity().onBackPressed()
            return
        }

        setupViews()
        setupRecyclerView()
        setupClickListeners()
        loadFilmography()
    }

    private fun setupViews() {
        binding.actorName.text = actorName
        if (!actorImage.isNullOrEmpty()) {
            binding.actorImage.loadImage(actorImage)
        }
        binding.filmographyCount.text = "0 ${getString(R.string.actor_filmography)}"
        
        updateFilterButtonStyles()
        updateSortButtonStyles()
    }

    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(requireContext(), 3)
        binding.filmographyRecyclerView.layoutManager = gridLayoutManager
        binding.filmographyRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.filterAll.setOnClickListener {
            updateFilter("all")
        }
        binding.filterMovies.setOnClickListener {
            updateFilter("movies")
        }
        binding.filterTv.setOnClickListener {
            updateFilter("tv")
        }

        binding.sortPopularity.setOnClickListener {
            updateSort("popularity")
        }
        binding.sortLatest.setOnClickListener {
            updateSort("latest")
        }
        binding.sortUpcoming.setOnClickListener {
            updateSort("upcoming")
        }
    }

    private fun updateFilter(filter: String) {
        currentFilter = filter
        updateFilterButtonStyles()
        applyFiltersAndSort()
    }

    private fun updateSort(sort: String) {
        currentSort = sort
        updateSortButtonStyles()
        applyFiltersAndSort()
    }

    private fun updateFilterButtonStyles() {
        val buttons = listOf(
            binding.filterAll to "all",
            binding.filterMovies to "movies",
            binding.filterTv to "tv"
        )

        buttons.forEach { (button, filter) ->
            button.isSelected = currentFilter == filter
        }
    }

    private fun updateSortButtonStyles() {
        val buttons = listOf(
            binding.sortPopularity to "popularity",
            binding.sortLatest to "latest",
            binding.sortUpcoming to "upcoming"
        )

        buttons.forEach { (button, sort) ->
            button.isSelected = currentSort == sort
        }
    }

    private fun applyFiltersAndSort() {
        filteredList.clear()
        filteredList.addAll(when (currentFilter) {
            "movies" -> filmographyList.filter { it.mediaType == "movie" }
            "tv" -> filmographyList.filter { it.mediaType == "tv" }
            else -> filmographyList
        })

        when (currentSort) {
            "popularity" -> filteredList.sortByDescending { it.popularity }
            "latest" -> filteredList.sortByDescending { it.releaseDate }
            "upcoming" -> {
                filteredList.sortBy { if (it.isUpcoming) 0 else 1 }
                filteredList.sortByDescending { it.releaseDate }
            }
        }

        adapter.submitList(filteredList.toList())
        updateCount()
    }

    private fun updateCount() {
        val movieCount = filmographyList.count { it.mediaType == "movie" }
        val tvCount = filmographyList.count { it.mediaType == "tv" }
        val totalCount = filmographyList.size

        binding.filterAll.text = "${getString(R.string.actor_all)} ($totalCount)"
        binding.filterMovies.text = "${getString(R.string.actor_movies)} ($movieCount)"
        binding.filterTv.text = "${getString(R.string.actor_tv_shows)} ($tvCount)"
        binding.filmographyCount.text = "$totalCount ${getString(R.string.actor_filmography)}"
    }

    private fun loadFilmography() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.filmographyRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            ioSafe {
                try {
                    val language = getTmdbLanguageCode()
                    
                    val response = app.get(
                        "https://api.themoviedb.org/3/person/$actorId/combined_credits",
                        params = mapOf(
                            "api_key" to TMDB_API_KEY,
                            "language" to language
                        )
                    )

                    val json = JSONObject(response.text)
                    val cast = json.optJSONArray("cast") ?: JSONArray()

                    filmographyList.clear()
                    
                    val seenIds = mutableSetOf<Int>()
                    val currentDate = Date()

                    for (i in 0 until cast.length()) {
                        try {
                            val item = cast.getJSONObject(i)
                            
                            val id = item.optInt("id")
                            if (seenIds.contains(id)) continue
                            
                            val title = item.optString("title", item.optString("name", ""))
                            val overview = item.optString("overview", "")
                            if (isTalkShow(title, overview)) continue

                            val mediaType = item.optString("media_type", "")
                            val releaseDateStr = item.optString("release_date", item.optString("first_air_date", ""))
                            val releaseDate = parseDate(releaseDateStr)
                            val isUpcoming = releaseDate != null && releaseDate.after(currentDate)

                            seenIds.add(id)
                            
                            filmographyList.add(
                                FilmographyItem(
                                    id = id,
                                    title = title,
                                    posterPath = item.optString("poster_path", null),
                                    releaseDate = releaseDateStr,
                                    character = item.optString("character", null),
                                    mediaType = mediaType,
                                    popularity = item.optDouble("popularity", 0.0),
                                    voteAverage = item.optDouble("vote_average", 0.0),
                                    isUpcoming = isUpcoming
                                )
                            )
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }

                    main {
                        applyFiltersAndSort()
                        binding.loadingIndicator.visibility = View.GONE
                        
                        if (filmographyList.isEmpty()) {
                            binding.filmographyRecyclerView.visibility = View.GONE
                        } else {
                            binding.filmographyRecyclerView.visibility = View.VISIBLE
                        }
                    }

                } catch (e: Exception) {
                    logError(e)
                    main {
                        binding.loadingIndicator.visibility = View.GONE
                        binding.filmographyRecyclerView.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun isTalkShow(title: String, overview: String): Boolean {
        val talkShowKeywords = listOf(
            "talk", "show", "late night", "tonight show", "jimmy fallon", "snl", "saturday night live",
            "variety", "sketch comedy", "stand-up", "standup", "comedy central", "daily show",
            "colbert", "kimmel", "conan", "ellen", "oprah", "view", "today show", "good morning",
            "interview", "panel", "roundtable", "discussion", "news", "current events", "politics",
            "reality", "competition", "game show", "quiz", "trivia", "awards", "ceremony",
            "red carpet", "premiere", "after party", "behind the scenes", "making of", "documentary",
            "special", "concert", "live performance", "mtv", "vh1", "bet", "comedy", "roast"
        )

        val lowerTitle = title.lowercase(Locale.getDefault())
        val lowerOverview = overview.lowercase(Locale.getDefault())

        return talkShowKeywords.any { keyword ->
            lowerTitle.contains(keyword) || lowerOverview.contains(keyword)
        }
    }

    private fun parseDate(dateStr: String): Date? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            format.parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun getTmdbLanguageCode(): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        return prefs.getString("locale_key", "en-US") ?: "en-US"
    }

    private fun onFilmographyItemClick(item: FilmographyItem) {
        try {
            Log.d(TAG, "Clicked: ${item.title} (${item.mediaType})")
            // Il callback fa già la ricerca, questo è solo per log
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ADAPTER CON CALLBACK - COME HA FATTO LUI!
    class FilmographyAdapter(
        private val onItemClick: (FilmographyItem) -> Unit,
        private val searchCallback: (String) -> Unit
    ) : RecyclerView.Adapter<FilmographyAdapter.ViewHolder>() {
        
        private var items = listOf<FilmographyItem>()
        
        fun submitList(newList: List<FilmographyItem>) {
            items = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFilmographyGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }
        
        override fun getItemCount() = items.size
        
        inner class ViewHolder(private val binding: ItemFilmographyGridBinding) : 
            RecyclerView.ViewHolder(binding.root) {
            
            fun bind(item: FilmographyItem) {
                if (!item.posterPath.isNullOrEmpty()) {
                    binding.posterImage.loadImage("https://image.tmdb.org/t/p/w500${item.posterPath}")
                    binding.posterImage.visibility = View.VISIBLE
                    binding.posterPlaceholder.visibility = View.GONE
                } else {
                    binding.posterImage.visibility = View.GONE
                    binding.posterPlaceholder.visibility = View.VISIBLE
                }

                binding.title.text = item.title
                
                binding.root.setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        val scaleAnim = ScaleAnimation(
                            1.0f, 1.05f,
                            1.0f, 1.05f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                        )
                        scaleAnim.duration = 150
                        scaleAnim.fillAfter = true
                        view.startAnimation(scaleAnim)
                    } else {
                        val scaleAnim = ScaleAnimation(
                            1.05f, 1.0f,
                            1.05f, 1.0f,
                            Animation.RELATIVE_TO_SELF, 0.5f,
                            Animation.RELATIVE_TO_SELF, 0.5f
                        )
                        scaleAnim.duration = 150
                        scaleAnim.fillAfter = true
                        view.startAnimation(scaleAnim)
                    }
                }
                
                binding.root.setOnClickListener {
                    searchCallback(item.title)
                    onItemClick(item)
                }
            }
        }
    }
}
