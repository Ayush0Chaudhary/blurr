package com.blurr.voice.utilities

import android.content.Context
import android.util.Patterns

class UserProfileManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "user_profile_prefs"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isProfileComplete(): Boolean {
        val name = getName()
        val email = getEmail()
        return !name.isNullOrBlank() && !email.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun saveProfile(name: String, email: String) {
        prefs.edit().putString(KEY_NAME, name.trim()).putString(KEY_EMAIL, email.trim()).apply()
    }

    fun getName(): String? = prefs.getString(KEY_NAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
}


