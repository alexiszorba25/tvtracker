package com.alexis.tvtracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ApiKeyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
    private val _apiKey = MutableStateFlow(currentApiKey())
    val apiKey: StateFlow<String> = _apiKey

    fun currentApiKey(): String {
        return prefs.getString(KEY_TMDB, null).orEmpty()
    }

    fun hasApiKey(): Boolean = currentApiKey().isNotBlank()

    fun saveTmdbApiKey(value: String) {
        prefs.edit().putString(KEY_TMDB, value.trim()).apply()
        _apiKey.value = currentApiKey()
    }

    private companion object {
        const val KEY_TMDB = "tmdb_api_key"
    }
}
