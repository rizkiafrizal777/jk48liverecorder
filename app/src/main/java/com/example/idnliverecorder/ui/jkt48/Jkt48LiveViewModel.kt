package com.example.idnliverecorder.ui.jkt48

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idnliverecorder.monitoring.jkt48.Jkt48ConnectRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48FavoritesRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
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
            // Requirement: Jika API belum aktif, gunakan sample data yang jelas diberi label DEMO
            loadDemoData()
            return
        }

        _uiState.value = Jkt48LiveUiState.Loading

        viewModelScope.launch {
            val result = repository.fetchIdnLiveStreams(apiKey = currentKey)
            result.onSuccess { members ->
                if (members.isEmpty()) {
                    _uiState.value = Jkt48LiveUiState.Empty(
                        message = "Tidak ada member JKT48 yang sedang live di IDN saat ini.",
                        lastCheckedMs = System.currentTimeMillis(),
                        isDemoData = false
                    )
                } else {
                    _uiState.value = Jkt48LiveUiState.Success(
                        members = members,
                        lastUpdatedMs = System.currentTimeMillis(),
                        isDemoData = false
                    )
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

    /**
     * Loads clean sample demo members for Free Developer Program UI documentation & review.
     */
    fun loadDemoData() {
        val sampleMembers = listOf(
            Jkt48LiveMember(
                roomId = "484801",
                memberName = "Freya Jayawardana",
                slug = "freya-jayawardana",
                title = "Malam mingguan bareng Freya!",
                thumbnail = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Freya_Jayawardana_at_Jak-Japan_Matsuri_2024.jpg/480px-Freya_Jayawardana_at_Jak-Japan_Matsuri_2024.jpg",
                startedAt = "20:00 WIB",
                startedAtMs = System.currentTimeMillis() - 1800000L,
                type = "idn",
                platform = "idn",
                streamingUrls = emptyList(),
                viewers = 15420,
                isFavorite = favoritesRepository.isFavorite("Freya Jayawardana")
            ),
            Jkt48LiveMember(
                roomId = "484802",
                memberName = "Angelina Christy",
                slug = "angelina-christy",
                title = "Ngobrol santai malam ini",
                thumbnail = null,
                startedAt = "20:15 WIB",
                startedAtMs = System.currentTimeMillis() - 900000L,
                type = "idn",
                platform = "idn",
                streamingUrls = emptyList(),
                viewers = 12350,
                isFavorite = favoritesRepository.isFavorite("Angelina Christy")
            ),
            Jkt48LiveMember(
                roomId = "484803",
                memberName = "Gita Sekar Andarini",
                slug = "gita-sekar-andarini",
                title = "Live IDN Santai",
                thumbnail = null,
                startedAt = "20:30 WIB",
                startedAtMs = System.currentTimeMillis() - 300000L,
                type = "idn",
                platform = "idn",
                streamingUrls = emptyList(),
                viewers = 9400,
                isFavorite = favoritesRepository.isFavorite("Gita Sekar Andarini")
            )
        )

        _uiState.value = Jkt48LiveUiState.Success(
            members = sampleMembers,
            lastUpdatedMs = System.currentTimeMillis(),
            isDemoData = true
        )
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

