package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ProfileManager {
    private lateinit var prefs: SharedPreferences

    private val _currentProfileId = MutableStateFlow(1L)
    val currentProfileId: StateFlow<Long> = _currentProfileId
    
    val currentProfileIdValue: Long get() = _currentProfileId.value
    
    private val _isKidsMode = MutableStateFlow(false)
    val isKidsMode: StateFlow<Boolean> = _isKidsMode

    fun init(context: Context) {
        prefs = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        _currentProfileId.value = prefs.getLong("current_profile_id", 1L)
        _isKidsMode.value = prefs.getBoolean("current_profile_kids", false)
    }

    fun setCurrentProfile(profileId: Long, isKidsMode: Boolean) {
        prefs.edit()
            .putLong("current_profile_id", profileId)
            .putBoolean("current_profile_kids", isKidsMode)
            .apply()
        _currentProfileId.value = profileId
        _isKidsMode.value = isKidsMode
    }
}
