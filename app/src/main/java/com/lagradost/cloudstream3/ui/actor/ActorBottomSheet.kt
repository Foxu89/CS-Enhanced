package com.lagradost.cloudstream3.ui.actor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentActorBottomSheetBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppContextUtils.loadImage
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: FragmentActorBottomSheetBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_ACTOR_ID = "actor_id"
        private const val ARG_ACTOR_NAME = "actor_name"
        private const val ARG_ACTOR_IMAGE = "actor_image"
        private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb" // La tua API key corta

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
        
        // Show basic info immediately
        binding.actorName.text = actorName
        if (!actorImage.isNullOrEmpty()) {
            binding.actorImage.loadImage(actorImage)
        }
        
        // Load detailed info from TMDB
        loadActorDetails(actorId)
    }
    
    private fun loadActorDetails(actorId: Int) {
        ioSafe {
            try {
                // Get language from settings
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                val language = prefs.getString("language_key", "en") ?: "en"
                
                // Call TMDB API with short API key in params
                val response = app.get(
                    "https://api.themoviedb.org/3/person/$actorId",
                    params = mapOf(
                        "api_key" to TMDB_API_KEY,
                        "language" to language
                    )
                )
                
                val json = response.jsonObject
                
                val birthday = json.optString("birthday", null)
                val deathday = json.optString("deathday", null)
                val placeOfBirth = json.optString("place_of_birth", null)
                val gender = json.optInt("gender", 0)
                val biography = json.optString("biography", "")
                val knownForDepartment = json.optString("known_for_department", "")
                
                main {
                    binding.actorGender.text = when (gender) {
                        1 -> "Female"
                        2 -> "Male"
                        else -> "Not specified"
                    }
                    
                    binding.actorDepartment.text = knownForDepartment
                    
                    if (!birthday.isNullOrEmpty()) {
                        val age = calculateAge(birthday, deathday)
                        binding.actorBirthday.text = if (deathday.isNullOrEmpty()) {
                            "$birthday ($age years old)"
                        } else {
                            "$birthday - $deathday"
                        }
                    } else {
                        binding.actorBirthday.visibility = View.GONE
                    }
                    
                    if (!placeOfBirth.isNullOrEmpty()) {
                        binding.actorBirthplace.text = placeOfBirth
                    } else {
                        binding.actorBirthplace.visibility = View.GONE
                    }
                    
                    if (!biography.isEmpty()) {
                        binding.actorBio.text = biography
                    } else {
                        binding.actorBio.text = "No biography available in selected language"
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
    
    private fun calculateAge(birthday: String, deathday: String?): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val birthDate = format.parse(birthday) ?: return ""
            val endDate = if (!deathday.isNullOrEmpty()) format.parse(deathday) else Date()
            
            val age = (endDate.time - birthDate.time) / (1000L * 60 * 60 * 24 * 365)
            age.toString()
        } catch (e: Exception) {
            ""
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
