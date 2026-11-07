package com.example.idnliverecorder.monitoring.jkt48

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user's favorite JKT48 members persisted via SharedPreferences.
 */
class Jkt48FavoritesRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "jkt48_favorites_prefs"
        private const val KEY_FAVORITES = "favorite_members_set"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow<Set<String>>(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private fun loadFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
    }

    fun isFavorite(memberName: String): Boolean {
        return _favorites.value.any { it.equals(memberName.trim(), ignoreCase = true) }
    }

    fun toggleFavorite(memberName: String): Boolean {
        val trimmed = memberName.trim()
        val current = _favorites.value.toMutableSet()
        val existing = current.firstOrNull { it.equals(trimmed, ignoreCase = true) }

        val isNowFavorite = if (existing != null) {
            current.remove(existing)
            false
        } else {
            current.add(trimmed)
            true
        }

        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        _favorites.value = current
        return isNowFavorite
    }

    fun addFavorite(memberName: String) {
        val trimmed = memberName.trim()
        val current = _favorites.value.toMutableSet()
        if (!current.any { it.equals(trimmed, ignoreCase = true) }) {
            current.add(trimmed)
            prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
            _favorites.value = current
        }
    }

    fun removeFavorite(memberName: String) {
        val trimmed = memberName.trim()
        val current = _favorites.value.toMutableSet()
        val match = current.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (match != null) {
            current.remove(match)
            prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
            _favorites.value = current
        }
    }
}

