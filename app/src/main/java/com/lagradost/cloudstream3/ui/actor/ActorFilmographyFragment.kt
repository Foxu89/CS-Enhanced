package com.lagradost.cloudstream3.ui.actor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.databinding.FragmentActorFilmographyBinding
import com.lagradost.cloudstream3.databinding.ItemFilmographyGridBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ActorFilmographyFragment : Fragment() {
    private var _binding: FragmentActorFilmographyBinding? = null
    private val binding get() = _binding!!

    private lateinit var actorId: Int
    private lateinit var actorName: String
    private var actorImage: String? = null
    private var currentFilter = "all" // all, movies, tv
    private var currentSort = "popularity" // popularity, latest, upcoming

    private val filmographyList = mutableListOf<FilmographyItem>()
    private val filteredList = mutableListOf<FilmographyItem>()
    private val adapter = FilmographyAdapter { item ->
        onFilmographyItemClick(item)
    }

    data class FilmographyItem(
        val id: Int,
        val title: String,
        val posterPath: String?,
        val releaseDate: String?,
        val character: String?,
        val mediaType: String, // "movie" or "tv"
        val popularity: Double,
        val voteAverage: Double,
        val isUpcoming: Boolean
    )

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

        // Get arguments
        actorId = arguments?.getInt("actor_id") ?: return
        actorName = arguments?.getString("actor_name") ?: ""
        actorImage = arguments?.getString("actor_image")

        // Setup views
        setupViews()
        setupRecyclerView()
        setupClickListeners()

        // Load data
        loadFilmography()
    }

    private fun setupViews() {
        binding.actorName.text = actorName
        if (!actorImage.isNullOrEmpty()) {
            binding.actorImage.loadImage(actorImage)
        }
        binding.filmographyCount.text = "0 ${getString(R.string.actor_filmography)}"
    }

    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(requireContext(), 3)
        binding.filmographyRecyclerView.layoutManager = gridLayoutManager
        binding.filmographyRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            popCurrentPage()
        }

        // Filter buttons
        binding.filterAll.setOnClickListener {
            updateFilter("all")
        }
        binding.filterMovies.setOnClickListener {
            updateFilter("movies")
        }
        binding.filterTv.setOnClickListener {
            updateFilter("tv")
        }

        // Sort buttons
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
        // Filter
        filteredList.clear()
        filteredList.addAll(when (currentFilter) {
            "movies" -> filmographyList.filter { it.mediaType == "movie" }
            "tv" -> filmographyList.filter { it.mediaType == "tv" }
            else -> filmographyList
        })

        // Sort
        when (currentSort) {
            "popularity" -> filteredList.sortByDescending { it.popularity }
            "latest" -> filteredList.sortByDescending { it.releaseDate }
            "upcoming" -> {
                filteredList.sortBy { if (it.isUpcoming) 0 else 1 }
                filteredList.sortByDescending { it.releaseDate }
            }
        }

        // Update adapter
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
        binding.emptyView.root.visibility = View.GONE

        lifecycleScope.launch {
            ioSafe {
                try {
                    val language = getTmdbLanguageCode()
                    
                    val response = app.get(
                        "https://api.themoviedb.org/3/person/$actorId/combined_credits",
                        params = mapOf(
                            "api_key" to ActorBottomSheet.TMDB_API_KEY,
                            "language" to language
                        )
                    )

                    val json = JSONObject(response.text)
                    val cast = json.optJSONArray("cast") ?: JSONArray()

                    filmographyList.clear()

                    val currentDate = Date()

                    for (i in 0 until cast.length()) {
                        try {
                            val item = cast.getJSONObject(i)
                            
                            // Skip talk shows and variety shows
                            val title = item.optString("title", item.optString("name", ""))
                            val overview = item.optString("overview", "")
                            if (isTalkShow(title, overview)) continue

                            val mediaType = item.optString("media_type", "")
                            val releaseDateStr = item.optString("release_date", item.optString("first_air_date", ""))
                            val releaseDate = parseDate(releaseDateStr)
                            val isUpcoming = releaseDate != null && releaseDate.after(currentDate)

                            filmographyList.add(
                                FilmographyItem(
                                    id = item.optInt("id"),
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
                            binding.emptyView.root.visibility = View.VISIBLE
                            binding.filmographyRecyclerView.visibility = View.GONE
                            binding.emptyView.emptyTitle.setText(R.string.actor_no_content)
                            binding.emptyView.emptyMessage.setText(R.string.actor_no_content)
                        } else {
                            binding.emptyView.root.visibility = View.GONE
                            binding.filmographyRecyclerView.visibility = View.VISIBLE
                        }
                    }

                } catch (e: Exception) {
                    logError(e)
                    main {
                        binding.loadingIndicator.visibility = View.GONE
                        binding.emptyView.root.visibility = View.VISIBLE
                        binding.emptyView.emptyTitle.setText(R.string.actor_error)
                        binding.emptyView.emptyMessage.setText(e.message ?: getString(R.string.actor_error))
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
        // Navigate to metadata screen
        val bundle = Bundle().apply {
            putString("id", item.id.toString())
            putString("type", if (item.mediaType == "movie") "movie" else "tv")
            putString("title", item.title)
        }
        // Navigate to metadata screen (you'll need to add this destination)
        // navigate(R.id.action_to_metadata, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class FilmographyAdapter(
        private val onItemClick: (FilmographyItem) -> Unit
    ) : NoStateAdapter<FilmographyItem>(diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.id == b.id },
        contentsSame = { a, b -> a.title == b.title && a.releaseDate == b.releaseDate }
    )) {

        override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
            return ViewHolderState(
                ItemFilmographyGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindContent(holder: ViewHolderState<Any>, item: FilmographyItem, position: Int) {
            val binding = holder.view as ItemFilmographyGridBinding
            val context = binding.root.context

            // Load poster
            if (!item.posterPath.isNullOrEmpty()) {
                binding.posterImage.loadImage("https://image.tmdb.org/t/p/w500${item.posterPath}")
                binding.posterImage.visibility = View.VISIBLE
                binding.posterPlaceholder.visibility = View.GONE
            } else {
                binding.posterImage.visibility = View.GONE
                binding.posterPlaceholder.visibility = View.VISIBLE
            }

            // Upcoming badge
            binding.upcomingBadge.isVisible = item.isUpcoming

            // Rating badge
            if (item.voteAverage > 0) {
                binding.ratingBadge.isVisible = true
                binding.ratingText.text = String.format("%.1f", item.voteAverage)
            } else {
                binding.ratingBadge.isVisible = false
            }

            // Title
            binding.title.text = item.title

            // Character
            if (!item.character.isNullOrEmpty()) {
                binding.character.isVisible = true
                binding.character.text = context.getString(R.string.actor_as_character, item.character)
            } else {
                binding.character.isVisible = false
            }

            // Year
            if (!item.releaseDate.isNullOrEmpty()) {
                val year = try {
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val date = format.parse(item.releaseDate)
                    SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    ""
                }
                binding.year.text = year
            } else {
                binding.year.text = ""
            }

            // Upcoming indicator
            if (item.isUpcoming) {
                binding.upcomingIndicator.isVisible = true
                binding.upcomingIndicator.text = context.getString(R.string.actor_coming_soon)
            } else {
                binding.upcomingIndicator.isVisible = false
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
