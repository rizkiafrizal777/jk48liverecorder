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
import com.example.idnliverecorder.recording.validation.StreamSourceValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

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
    val recordingSessions = DirectRecordingService.recordingSessions
    private var fetchJob: Job? = null

    fun startDirectRecording(member: Jkt48LiveMember) {
        val streamUrl = member.streamingUrls.firstOrNull() ?: ""

        val intent = Intent(getApplication(), DirectRecordingService::class.java).apply {
            action = DirectRecordingService.ACTION_START_DIRECT
            putExtra(DirectRecordingService.EXTRA_SESSION_ID, member.sessionKey)
            putExtra(DirectRecordingService.EXTRA_STREAM_URL, streamUrl)
            putExtra(DirectRecordingService.EXTRA_MEMBER_NAME, member.memberName)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopDirectRecording(sessionId: String? = null) {
        val intent = Intent(getApplication(), DirectRecordingService::class.java).apply {
            action = DirectRecordingService.ACTION_STOP_DIRECT
            sessionId?.let { putExtra(DirectRecordingService.EXTRA_SESSION_ID, it) }
        }
        getApplication<Application>().startService(intent)
    }

    suspend fun validateMemberStream(member: Jkt48LiveMember): StreamSourceValidator.ValidationResult {
        val url = member.streamingUrls.firstOrNull()
        return validator.validate(url = url)
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

        val previousState = _uiState.value
        if (previousState is Jkt48LiveUiState.Success) {
            _uiState.value = previousState.copy(isRefreshing = true, refreshError = null)
        } else {
            _uiState.value = Jkt48LiveUiState.Loading
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            var result = repository.fetchIdnLiveStreams(apiKey = currentKey)
            var attempt = 1
            while (result.isFailure && attempt < MAX_FETCH_ATTEMPTS) {
                val error = result.exceptionOrNull() ?: break
                if (!error.isTransientNetworkFailure()) break
                delay(1000L * (1 shl (attempt - 1)))
                if (!isActive) throw CancellationException("Live refresh superseded")
                attempt++
                result = repository.fetchIdnLiveStreams(apiKey = currentKey)
            }
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
                val message = error.message ?: "Gagal terhubung ke JKT48Connect."
                val currentState = _uiState.value
                _uiState.value = if (currentState is Jkt48LiveUiState.Success) {
                    currentState.copy(isRefreshing = false, refreshError = message)
                } else {
                    Jkt48LiveUiState.Error(
                        errorMessage = message,
                        isApiKeyMissing = false,
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean =
        this is IOException ||
            message.orEmpty().contains(Regex("HTTP (429|5[0-9]{2})"))

    companion object {
        private const val MAX_FETCH_ATTEMPTS = 3
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
        if (preferences.setApiKey(newKey)) {
            refresh()
        } else {
            _uiState.value = Jkt48LiveUiState.Error(
                errorMessage = "API Key tidak dapat disimpan dengan aman di perangkat ini.",
                isApiKeyMissing = true,
                canRetry = false
            )
        }
    }

    fun setNotifyFavorites(enabled: Boolean) {
        preferences.setNotifyFavorites(enabled)
    }
}

