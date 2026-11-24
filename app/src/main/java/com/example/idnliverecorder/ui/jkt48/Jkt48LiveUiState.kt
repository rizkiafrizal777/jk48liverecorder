package com.example.idnliverecorder.ui.jkt48

import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember

/**
 * UI State for JKT48 Live Monitor screen.
 */
sealed interface Jkt48LiveUiState {
    object Idle : Jkt48LiveUiState
    object Loading : Jkt48LiveUiState

    data class Success(
        val members: List<Jkt48LiveMember>,
        val lastUpdatedMs: Long = System.currentTimeMillis()
    ) : Jkt48LiveUiState

    data class Empty(
        val message: String = "Tidak ada member JKT48 yang sedang live di IDN saat ini.",
        val lastCheckedMs: Long = System.currentTimeMillis()
    ) : Jkt48LiveUiState

    data class Error(
        val errorMessage: String,
        val isApiKeyMissing: Boolean = false,
        val canRetry: Boolean = true
    ) : Jkt48LiveUiState
}

