package com.alexis.tvtracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexis.tvtracker.data.ApiKeyRepository
import com.alexis.tvtracker.data.LibraryRepository
import com.alexis.tvtracker.data.TmdbRepository
import com.alexis.tvtracker.model.SearchItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchSection {
    Popular,
    ComingSoon,
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchItem> = emptyList(),
    val suggestions: List<SearchItem> = emptyList(),
    val upcoming: List<SearchItem> = emptyList(),
    val selectedSection: SearchSection = SearchSection.Popular,
    val addedIds: Set<String> = emptySet(),
    val addedMessage: String? = null,
    val loading: Boolean = false,
    val loadingSuggestions: Boolean = false,
    val refreshingSuggestions: Boolean = false,
    val error: String? = null,
    val missingApiKey: Boolean = true,
)

class SearchViewModel(
    private val tmdbRepository: TmdbRepository,
    private val libraryRepository: LibraryRepository,
    private val apiKeyRepository: ApiKeyRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            apiKeyRepository.apiKey.collect { apiKey ->
                _uiState.update { it.copy(missingApiKey = apiKey.isBlank()) }
                if (apiKey.isNotBlank()) {
                    loadSuggestions(forceRefresh = false)
                }
            }
        }
        loadSuggestions(forceRefresh = false)
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            search(query)
        }
    }

    fun addToLibrary(item: SearchItem) {
        viewModelScope.launch {
            libraryRepository.add(item)
            _uiState.update {
                it.copy(
                    addedIds = it.addedIds + item.key,
                    addedMessage = "${item.title} added to library",
                )
            }
        }
    }

    fun clearAddedMessage() {
        _uiState.update { it.copy(addedMessage = null) }
    }

    fun setSection(section: SearchSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun refreshSuggestions() {
        loadSuggestions(forceRefresh = true)
    }

    private fun loadSuggestions(forceRefresh: Boolean) {
        if (!tmdbRepository.hasApiKey()) return
        viewModelScope.launch {
            val (cachedSuggestions, cachedUpcoming) = tmdbRepository.cachedDiscovery()
            val hasCached = cachedSuggestions.isNotEmpty() || cachedUpcoming.isNotEmpty()
            if (hasCached && !forceRefresh) {
                _uiState.update {
                    it.copy(
                        suggestions = cachedSuggestions,
                        upcoming = cachedUpcoming,
                        loadingSuggestions = false,
                    )
                }
            }

            _uiState.update {
                it.copy(
                    loadingSuggestions = !hasCached,
                    refreshingSuggestions = forceRefresh,
                )
            }
            val suggestions = runCatching { tmdbRepository.suggestions(forceRefresh = forceRefresh || !hasCached) }
                .getOrDefault(cachedSuggestions)
            val upcoming = runCatching { tmdbRepository.upcoming(forceRefresh = forceRefresh || !hasCached) }
                .getOrDefault(cachedUpcoming)
            _uiState.update {
                it.copy(
                    suggestions = suggestions,
                    upcoming = upcoming,
                    loadingSuggestions = false,
                    refreshingSuggestions = false,
                )
            }
        }
    }

    private suspend fun search(query: String) {
        if (query.isBlank() || !tmdbRepository.hasApiKey()) {
            _uiState.update { it.copy(results = emptyList(), loading = false, error = null) }
            return
        }

        _uiState.update { it.copy(loading = true, error = null) }
        runCatching { tmdbRepository.search(query) }
            .onSuccess { results ->
                _uiState.update { it.copy(results = results, loading = false) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = throwable.message ?: "Search failed",
                    )
                }
            }
    }

    companion object {
        fun factory(
            tmdbRepository: TmdbRepository,
            libraryRepository: LibraryRepository,
            apiKeyRepository: ApiKeyRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SearchViewModel(tmdbRepository, libraryRepository, apiKeyRepository) as T
                }
            }
        }
    }
}

val SearchItem.key: String
    get() = "${mediaType.name}-$id"
