package com.example.evokeraa

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("evoker_settings", Context.MODE_PRIVATE)

    // API Key
    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""
    fun saveApiKey(key: String) = prefs.edit().putString("gemini_api_key", key).apply()

    // Dark Mode
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        _isDarkMode.value = enabled
    }

    // NEW: Identity Management (Personas)
    private val _aliases = MutableStateFlow(loadAliases())
    val aliases = _aliases.asStateFlow()

    private fun loadAliases(): Set<String> {
        return prefs.getStringSet("my_aliases", emptySet()) ?: emptySet()
    }

    fun addAlias(name: String) {
        val current = _aliases.value.toMutableSet()
        current.add(name.trim())
        prefs.edit().putStringSet("my_aliases", current).apply()
        _aliases.value = current
    }

    fun removeAlias(name: String) {
        val current = _aliases.value.toMutableSet()
        current.remove(name)
        prefs.edit().putStringSet("my_aliases", current).apply()
        _aliases.value = current
    }
}