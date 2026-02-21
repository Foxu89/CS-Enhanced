package com.lagradost.cloudstream3.ui.actor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.databinding.FragmentActorBottomSheetBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ActorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentActorBottomSheetBinding? = null
    private val binding get() = _binding!!

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
        
        binding.actorName.text = actorName
        if (!actorImage.isNullOrEmpty()) {
            binding.actorImage.loadImage(actorImage)
        }
        
        binding.searchWebButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, actorName)
            }
            startActivity(intent)
            dismiss()
        }
        
        loadActorDetails(actorId)
    }
    
    private fun getTmdbLanguageCode(): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val appLanguage = prefs.getString("language_key", "en") ?: "en"
        
        // Mappa i codici lingua dell'app ai codici TMDB
        return when (appLanguage) {
            "it" -> "it-IT"
            "en" -> "en-US"
            "es" -> "es-ES"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "pt" -> "pt-PT"
            "pt-rBR" -> "pt-BR"
            "zh" -> "zh-CN"
            "zh-rTW" -> "zh-TW"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "ru" -> "ru-RU"
            "ar" -> "ar-SA"
            "hi" -> "hi-IN"
            else -> "en-US"
        }
    }
    
    private fun loadActorDetails(actorId: Int) {
        ioSafe {
            try {
                val tmdbLanguage = getTmdbLanguageCode()
                
                val response = app.get(
                    "https://api.themoviedb.org/3/person/$actorId",
                    params = mapOf(
                        "api_key" to TMDB_API_KEY,
                        "language" to tmdbLanguage
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
                    
                    val yearsOld = getString(R.string.actor_years_old)
                    val genderText = when (gender) {
                        1 -> "${getString(R.string.actor_female)} ♀️"
                        2 -> "${getString(R.string.actor_male)} ♂️"
                        else -> getString(R.string.actor_not_specified)
                    }
                    binding.actorGender.text = genderText
                    
                    if (!birthday.isNullOrEmpty()) {
                        val formattedBirthday = formatDate(birthday)
                        binding.actorBirthday.text = formattedBirthday
                        
                        if (!deathday.isNullOrEmpty() && deathday != "null") {
                            val formattedDeathday = formatDate(deathday)
                            binding.actorDeathday.text = formattedDeathday
                            binding.actorDeathLayout.visibility = View.VISIBLE
                            
                            val age = calculateAge(birthday, deathday)
                            binding.actorAgeValue.text = "$age $yearsOld"
                        } else {
                            binding.actorDeathLayout.visibility = View.GONE
                            
                            val age = calculateAge(birthday, null)
                            binding.actorAgeValue.text = "$age $yearsOld"
                        }
                        binding.actorAgeLabel.visibility = View.VISIBLE
                        binding.actorAgeValue.visibility = View.VISIBLE
                    } else {
                        binding.actorBirthday.visibility = View.GONE
                        binding.actorDeathLayout.visibility = View.GONE
                        binding.actorAgeLabel.visibility = View.GONE
                        binding.actorAgeValue.visibility = View.GONE
                    }
                    
                    if (!placeOfBirth.isNullOrEmpty() && placeOfBirth != "null") {
                        binding.actorBirthplace.text = placeOfBirth
                    } else {
                        binding.actorBirthplace.text = getString(R.string.actor_unknown)
                    }
                    
                    if (biography.isNotEmpty()) {
                        binding.actorBio.text = biography
                    } else {
                        binding.actorBio.text = getString(R.string.actor_no_biography)
                    }
                }
            } catch (e: Exception) {
                logError(e)
                main {
                    binding.actorBio.text = getString(R.string.actor_error)
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
