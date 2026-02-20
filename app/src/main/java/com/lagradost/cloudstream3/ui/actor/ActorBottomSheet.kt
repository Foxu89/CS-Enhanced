package com.lagradost.cloudstream3.ui.actor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.databinding.FragmentActorBottomSheetBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.TvType
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class ActorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentActorBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var isKnownForVisible = false

    companion object {
        private const val ARG_ACTOR_ID = "actor_id"
        private const val ARG_ACTOR_NAME = "actor_name"
        private const val ARG_ACTOR_IMAGE = "actor_image"
        private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"

        fun newInstance(actorId: Int, actorName: String, actorImage: String?): ActorBottomSheet {
            val fragment = ActorBottomSheet()
            val args = Bundle()
            args.putInt(ARG_ACTOR_ID, actorId)
            args.putString(ARG_ACTOR_NAME, actorName)
            args.putString(ARG_ACTOR_IMAGE, actorImage)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActorBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val actorId = arguments?.getInt(ARG_ACTOR_ID) ?: return
        val actorName = arguments?.getString(ARG_ACTOR_NAME) ?: ""
        val actorImage = arguments?.getString(ARG_ACTOR_IMAGE)
        
        // Setup header
        binding.actorNameHeader.text = actorName
        binding.actorName.text = actorName
        
        if (!actorImage.isNullOrEmpty()) {
            binding.actorImage.loadImage(actorImage)
        }
        
        // Menu button click
        binding.menuButton.setOnClickListener {
            toggleKnownForSection()
        }
        
        // Pulsante per cercare sul web
        binding.searchWebButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, actorName)
            }
            startActivity(intent)
            dismiss()
        }
        
        // Setup RecyclerView con GridLayoutManager (3 colonne)
        binding.knownForRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        
        loadActorDetails(actorId)
        loadKnownFor(actorId)
    }
    
    private fun toggleKnownForSection() {
        if (isKnownForVisible) {
            binding.knownForSection.visibility = View.GONE
            binding.mainContent.visibility = View.VISIBLE
        } else {
            binding.knownForSection.visibility = View.VISIBLE
            binding.mainContent.visibility = View.GONE
        }
        isKnownForVisible = !isKnownForVisible
    }
    
    private fun loadKnownFor(actorId: Int) {
        ioSafe {
            try {
                val response = app.get(
                    "https://api.themoviedb.org/3/person/$actorId/combined_credits",
                    params = mapOf(
                        "api_key" to TMDB_API_KEY,
                        "language" to "en"
                    )
                )
                
                val json = JSONObject(response.text)
                val cast = json.optJSONArray("cast") ?: JSONArray()
                
                val items = mutableListOf<KnownForItem>()
                val seenIds = mutableSetOf<Int>()
                
                // Prendi i primi 15 film/serie più popolari/noti
                for (i in 0 until cast.length()) {
                    if (items.size >= 15) break
                    
                    val item = cast.getJSONObject(i)
                    val id = item.optInt("id")
                    
                    if (seenIds.contains(id)) continue
                    seenIds.add(id)
                    
                    val mediaType = item.optString("media_type")
                    val title = if (mediaType == "movie") {
                        item.optString("title")
                    } else {
                        item.optString("name")
                    }
                    
                    val posterPath = item.optString("poster_path", null)
                    val popularity = item.optDouble("popularity", 0.0)
                    
                    // Filtra solo quelli con poster e titolo
                    if (title.isNotEmpty() && posterPath != null && popularity > 1.0) {
                        items.add(
                            KnownForItem(
                                title = title,
                                posterPath = posterPath,
                                mediaType = mediaType,
                                id = id
                            )
                        )
                    }
                }
                
                // Ordina per popolarità
                items.sortByDescending { it.title.length }
                
                main {
                    if (items.isNotEmpty()) {
                        binding.knownForRecyclerView.adapter = KnownForAdapter(items) { item ->
                            openMediaDetails(item)
                        }
                    }
                }
                
            } catch (e: Exception) {
                logError(e)
            }
        }
    }
    
    private fun openMediaDetails(item: KnownForItem) {
        val activity = activity ?: return
        
        val url = if (item.mediaType == "movie") {
            "https://www.themoviedb.org/movie/${item.id}"
        } else {
            "https://www.themoviedb.org/tv/${item.id}"
        }
        
        // Usa i metodi factory non deprecati
        val searchResponse = if (item.mediaType == "movie") {
            com.lagradost.cloudstream3.newMovieSearchResponse(
                name = item.title,
                url = url,
                type = TvType.Movie,
                fix = true
            ) {
                this.posterUrl = if (!item.posterPath.isNullOrEmpty()) {
                    "https://image.tmdb.org/t/p/w500${item.posterPath}"
                } else null
            }
        } else {
            com.lagradost.cloudstream3.newTvSeriesSearchResponse(
                name = item.title,
                url = url,
                type = TvType.TvSeries,
                fix = true
            ) {
                this.posterUrl = if (!item.posterPath.isNullOrEmpty()) {
                    "https://image.tmdb.org/t/p/w500${item.posterPath}"
                } else null
            }
        }
        
        activity.loadSearchResult(searchResponse)
        dismiss()
    }
    
    private fun loadActorDetails(actorId: Int) {
        ioSafe {
            try {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                val language = prefs.getString("language_key", "en") ?: "en"
                
                val response = app.get(
                    "https://api.themoviedb.org/3/person/$actorId",
                    params = mapOf(
                        "api_key" to TMDB_API_KEY,
                        "language" to language
                    )
                )
                
                val json = JSONObject(response.text)
                
                val birthday = json.optString("birthday", null)
                val deathday = json.optString("deathday", null)
                val placeOfBirth = json.optString("place_of_birth", null)
                val gender = json.optInt("gender", 0)
                val biography = cleanBiography(json.optString("biography", ""))
                val knownForDepartment = json.optString("known_for_department", "")
                
                main {
                    binding.actorDepartment.text = knownForDepartment
                    binding.actorGender.text = when (gender) {
                        1 -> "Female"
                        2 -> "Male"
                        else -> "Not specified"
                    }
                    
                    if (!birthday.isNullOrEmpty() && birthday != "null") {
                        val formattedBirthday = formatDate(birthday)
                        
                        if (!deathday.isNullOrEmpty() && deathday != "null") {
                            val formattedDeathday = formatDate(deathday)
                            binding.actorBirthday.text = "$formattedBirthday - $formattedDeathday"
                            
                            val age = calculateAge(birthday, deathday)
                            binding.actorAge.text = "$age years old"
                        } else {
                            binding.actorBirthday.text = formattedBirthday
                            
                            val age = calculateAge(birthday, null)
                            binding.actorAge.text = "$age years old"
                        }
                        binding.actorAge.visibility = View.VISIBLE
                    } else {
                        binding.actorBirthday.visibility = View.GONE
                        binding.actorAge.visibility = View.GONE
                    }
                    
                    if (!placeOfBirth.isNullOrEmpty() && placeOfBirth != "null") {
                        binding.actorBirthplace.text = placeOfBirth
                    } else {
                        binding.actorBirthplace.visibility = View.GONE
                    }
                    
                    if (biography.isNotEmpty()) {
                        binding.actorBio.text = biography
                    } else {
                        binding.actorBio.text = "No biography available"
                    }
                }
            } catch (e: Exception) {
                logError(e)
                main {
                    binding.actorBio.text = "Error loading actor details"
                }
            }
        }
    }
    
    private fun cleanBiography(bio: String): String {
        if (bio.isEmpty()) return bio
        
        val wikipediaPatterns = listOf(
            Regex("Description above from the Wikipedia article [^,]+, licensed under CC-BY-SA, full list of contributors on Wikipedia\\."),
            Regex("This article is licensed under the GNU Free Documentation License\\. It uses material from the Wikipedia article [^.]+\\.", RegexOption.IGNORE_CASE),
            Regex("From Wikipedia, the free encyclopedia"),
            Regex("Wikipedia®"),
            Regex("Wikimedia Foundation"),
            Regex("CC-BY-SA"),
            Regex("GNU Free Documentation License")
        )
        
        var cleanBio = bio
        for (pattern in wikipediaPatterns) {
            cleanBio = cleanBio.replace(pattern, "").trim()
        }
        
        cleanBio = cleanBio.replace(Regex("\\n\\s*\\n"), "\n\n")
        
        return cleanBio
    }
    
    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }
    
    private fun calculateAge(birthday: String, deathday: String?): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val birthDate = format.parse(birthday) ?: return 0
            val endDate = if (!deathday.isNullOrEmpty() && deathday != "null") {
                format.parse(deathday) ?: Date()
            } else {
                Date()
            }
            
            val birthCalendar = Calendar.getInstance().apply { time = birthDate }
            val endCalendar = Calendar.getInstance().apply { time = endDate }
            
            var age = endCalendar.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            
            if (endCalendar.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            
            age
        } catch (e: Exception) {
            0
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
