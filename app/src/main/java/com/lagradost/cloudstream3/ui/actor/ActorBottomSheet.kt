package com.lagradost.cloudstream3.ui.actor

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
        private const val TAG = "ActorBottomSheet"
    }

    fun newInstance(actorId: Int, actorName: String, actorImage: String?): ActorBottomSheet {
        val fragment = ActorBottomSheet()
        val args = Bundle()
        args.putInt(ARG_ACTOR_ID, actorId)
        args.putString(ARG_ACTOR_NAME, actorName)
        args.putString(ARG_ACTOR_IMAGE, actorImage)
        fragment.arguments = args
        return fragment
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
        
        Log.d(TAG, "Actor ID: $actorId")
        Log.d(TAG, "Actor Name: $actorName")
        
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
        val appLanguage = prefs.getString("locale_key", "en-US") ?: "en-US"
        
        Log.d(TAG, "App language from prefs: $appLanguage")
        
        return appLanguage
    }
    
    private fun loadActorDetails(actorId: Int) {
        ioSafe {
            try {
                val tmdbLanguage = getTmdbLanguageCode()
                
                val url = "https://api.themoviedb.org/3/person/$actorId"
                val params = mapOf(
                    "api_key" to TMDB_API_KEY,
                    "language" to tmdbLanguage
                )
                
                Log.d(TAG, "API URL: $url")
                Log.d(TAG, "Params: $params")
                
                val response = app.get(url, params = params)
                
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response text length: ${response.text.length}")
                
                val json = JSONObject(response.text)
                
                // Log completto della risposta (solo in debug)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Full JSON response: ${json.toString(2)}")
                }
                
                val birthday = json.optString("birthday", null)
                val deathday = json.optString("deathday", null)
                val placeOfBirth = json.optString("place_of_birth", null)
                val gender = json.optInt("gender", 0)
                val biography = json.optString("biography", "")
                val knownForDepartment = json.optString("known_for_department", "")
                
                Log.d(TAG, "Birthday: $birthday")
                Log.d(TAG, "Deathday: $deathday")
                Log.d(TAG, "Place of birth: $placeOfBirth")
                Log.d(TAG, "Gender: $gender")
                Log.d(TAG, "Biography length: ${biography.length}")
                Log.d(TAG, "Known for department: $knownForDepartment")
                
                // Primi 200 caratteri della biografia per vedere la lingua
                if (biography.length > 0) {
                    Log.d(TAG, "Biography preview: ${biography.substring(0, minOf(200, biography.length))}")
                }
                
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
                Log.e(TAG, "Error loading actor details", e)
                main {
                    binding.actorBio.text = getString(R.string.actor_error)
                }
            }
        }
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
