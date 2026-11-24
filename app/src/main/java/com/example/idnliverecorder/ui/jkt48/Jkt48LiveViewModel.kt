package com.example.idnliverecorder.ui.jkt48

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idnliverecorder.monitoring.jkt48.Jkt48ConnectRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48FavoritesRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LivePreferences
import com.example.idnliverecorder.recording.direct.DirectRecordingService
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import com.example.idnliverecorder.recording.validation.StreamSourceValidator
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

    // Phase 5 Direct Stream Recording States & Validator
    val validator = StreamSourceValidator()
    val directRecordingStatus: StateFlow<DirectStreamStatus> = DirectRecordingService.engineStatus
    val directRecordingDurationMs: StateFlow<Long> = DirectRecordingService.recordingDurationMs
    val activeRecordingMember: StateFlow<String?> = DirectRecordingService.currentMemberName

    fun startDirectRecording(member: Jkt48LiveMember) {
        val streamUrl = member.streamingUrls.firstOrNull() ?: ""

        val intent = Intent(getApplication(), DirectRecordingService::class.java).apply {
            action = DirectRecordingService.ACTION_START_DIRECT
            putExtra(DirectRecordingService.EXTRA_STREAM_URL, streamUrl)
            putExtra(DirectRecordingService.EXTRA_MEMBER_NAME, member.memberName)
            putExtra(DirectRecordingService.EXTRA_IS_AUTHORIZED, true)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopDirectRecording() {
        val intent = Intent(getApplication(), DirectRecordingService::class.java).apply {
            action = DirectRecordingService.ACTION_STOP_DIRECT
        }
        getApplication<Application>().startService(intent)
    }

    suspend fun validateMemberStream(member: Jkt48LiveMember): StreamSourceValidator.ValidationResult {
        val url = member.streamingUrls.firstOrNull()
        return validator.validate(url = url, isRecordingAuthorized = true)
    }

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
                errorMessage = "API Key JKT48Connect belum diatur.",
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
                    _uiState.value = Jkt48LiveUiState.Empty(
                        message = "Tidak ada member JKT48 yang sedang live di IDN saat ini.",
                        lastCheckedMs = System.currentTimeMillis()
                    )
                } else {
                    _uiState.value = Jkt48LiveUiState.Success(
                        members = members,
                        lastUpdatedMs = System.currentTimeMillis()
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

