package com.example.idnliverecorder.ui.jkt48

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idnliverecorder.monitoring.jkt48.Jkt48ConnectRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48FavoritesRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LivePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class Jkt48LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = Jkt48LivePreferences(application)
    private val favoritesRepository = Jkt48FavoritesRepository(application)
    private val repository = Jkt48ConnectRepository(
        context = application,
        favoritesRepository = favoritesRepository,
        preferences = preferences
    )

    private val _uiState = MutableStateFlow<Jkt48LiveUiState>(Jkt48LiveUiState.Idle)
    val uiState: StateFlow<Jkt48LiveUiState> = _uiState.asStateFlow()

    val apiKey: StateFlow<String> = preferences.apiKey
    val favorites: StateFlow<Set<String>> = favoritesRepository.favorites
    val notifyFavorites: StateFlow<Boolean> = preferences.notifyFavorites

    init {
        fetchLiveStreams()
    }

    fun refresh() {
        fetchLiveStreams()
    }

    private fun fetchLiveStreams() {
        val currentKey = preferences.apiKey.value.trim()
        if (currentKey.isBlank()) {
            _uiState.value = Jkt48LiveUiState.Error(
                errorMessage = "API Key JKT48Connect belum dikonfigurasi.",
                isApiKeyMissing = true,
                canRetry = false
            )
            return
        }

        _uiState.value = Jkt48LiveUiState.Loading

        viewModelScope.launch {
            val result = repository.fetchIdnLiveStreams(apiKey = currentKey)
            result.onSuccess { members ->
                if (members.isEmpty()) {
                    _uiState.value = Jkt48LiveUiState.Empty()
                } else {
                    _uiState.value = Jkt48LiveUiState.Success(members)
                }
            }.onFailure { error ->
                _uiState.value = Jkt48LiveUiState.Error(
                    errorMessage = error.message ?: "Gagal terhubung ke JKT48Connect.",
                    isApiKeyMissing = false,
                    canRetry = true
                )
            }
        }
    }

    fun toggleFavorite(memberName: String) {
        favoritesRepository.toggleFavorite(memberName)
        // Refresh favorite state in current success list
        val current = _uiState.value
        if (current is Jkt48LiveUiState.Success) {
            val updated = current.members.map { m ->
                if (m.memberName.equals(memberName, ignoreCase = true)) {
                    m.copy(isFavorite = favoritesRepository.isFavorite(memberName))
                } else {
                    m
                }
            }
            _uiState.value = current.copy(members = updated)
        }
    }

    fun saveApiKey(newKey: String) {
        preferences.setApiKey(newKey)
        refresh()
    }

    fun setNotifyFavorites(enabled: Boolean) {
        preferences.setNotifyFavorites(enabled)
    }
}

