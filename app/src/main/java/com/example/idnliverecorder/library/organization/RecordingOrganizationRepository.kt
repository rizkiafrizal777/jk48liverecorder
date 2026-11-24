package com.example.idnliverecorder.library.organization

import android.content.Context
import com.example.idnliverecorder.database.IDNRecorderDatabase
import com.example.idnliverecorder.database.RecordingFavorite
import com.example.idnliverecorder.library.RecordingLibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GroupByOption(val label: String) {
    NONE("Semua (Tanpa Group)"),
    MEMBER("Group per Member"),
    DATE("Group per Tanggal"),
    MONTH("Group per Bulan")
}

enum class SizeFilter(val label: String) {
    ALL("Semua Ukuran"),
    UNDER_100MB("< 100 MB"),
    FROM_100_TO_500MB("100 – 500 MB"),
    OVER_500MB("> 500 MB")
}

enum class DurationFilter(val label: String) {
    ALL("Semua Durasi"),
    UNDER_15MIN("< 15 Menit"),
    FROM_15_TO_60MIN("15 – 60 Menit"),
    OVER_60MIN("> 60 Menit")
}

/**
 * Filter configuration model allowing simultaneous multi-dimensional filtering (Phase 7).
 */
data class OrganizationFilters(
    val searchQuery: String = "",
    val dateFilterMs: Long? = null,
    val sizeFilter: SizeFilter = SizeFilter.ALL,
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val favoritesOnly: Boolean = false,
    val groupBy: GroupByOption = GroupByOption.NONE
)

class RecordingOrganizationRepository(context: Context) {

    private val favoriteDao = IDNRecorderDatabase.getInstance(context).favoriteDao()

    val favoriteIdsFlow: Flow<Set<Long>> = favoriteDao.getAllFavoritesFlow().map { list ->
        list.map { it.recordingId }.toSet()
    }

    suspend fun isFavorite(recordingId: Long): Boolean = withContext(Dispatchers.IO) {
        favoriteDao.isFavorite(recordingId) == true
    }

    suspend fun toggleFavorite(item: RecordingLibraryItem): Boolean = withContext(Dispatchers.IO) {
        val current = favoriteDao.isFavorite(item.id) == true
        if (current) {
            favoriteDao.removeFavorite(item.id)
            false
        } else {
            favoriteDao.setFavorite(
                RecordingFavorite(
                    recordingId = item.id,
                    recordingUri = item.uri.toString(),
                    displayName = item.displayName,
                    isFavorite = true
                )
            )
            true
        }
    }

    suspend fun getAllFavoriteIds(): Set<Long> = withContext(Dispatchers.IO) {
        favoriteDao.getAllFavorites().map { it.recordingId }.toSet()
    }

    companion object {
        /**
         * Applies all combined filters simultaneously to the loaded recordings list.
         */
        fun applyFilters(
            items: List<RecordingLibraryItem>,
            filters: OrganizationFilters,
            favoriteIds: Set<Long>
        ): List<RecordingLibraryItem> {
            return items.filter { item ->
                // 1. Search Query (Member Name OR File Name)
                val matchesQuery = if (filters.searchQuery.isBlank()) true else {
                    val q = filters.searchQuery.trim().lowercase()
                    item.memberName.lowercase().contains(q) || item.displayName.lowercase().contains(q)
                }

                // 2. Date Filter (Specific day interval)
                val matchesDate = if (filters.dateFilterMs == null || filters.dateFilterMs <= 0L) true else {
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    item.dateAddedMs in filters.dateFilterMs until (filters.dateFilterMs + oneDayMs)
                }

                // 3. Size Filter
                val matchesSize = when (filters.sizeFilter) {
                    SizeFilter.ALL -> true
                    SizeFilter.UNDER_100MB -> item.sizeBytes < 100 * 1024 * 1024L
                    SizeFilter.FROM_100_TO_500MB -> item.sizeBytes in (100 * 1024 * 1024L)..(500 * 1024 * 1024L)
                    SizeFilter.OVER_500MB -> item.sizeBytes > 500 * 1024 * 1024L
                }

                // 4. Duration Filter
                val matchesDuration = when (filters.durationFilter) {
                    DurationFilter.ALL -> true
                    DurationFilter.UNDER_15MIN -> item.durationMs < 15 * 60 * 1000L
                    DurationFilter.FROM_15_TO_60MIN -> item.durationMs in (15 * 60 * 1000L)..(60 * 60 * 1000L)
                    DurationFilter.OVER_60MIN -> item.durationMs > 60 * 60 * 1000L
                }

                // 5. Favorites Filter
                val matchesFavorites = if (!filters.favoritesOnly) true else {
                    favoriteIds.contains(item.id)
                }

                matchesQuery && matchesDate && matchesSize && matchesDuration && matchesFavorites
            }
        }

        /**
         * Groups filtered recordings by member, date, or month.
         */
        fun groupRecordings(
            items: List<RecordingLibraryItem>,
            groupBy: GroupByOption
        ): Map<String, List<RecordingLibraryItem>> {
            return when (groupBy) {
                GroupByOption.NONE -> mapOf("Semua Rekaman" to items)
                GroupByOption.MEMBER -> items.groupBy { it.memberName }
                GroupByOption.DATE -> {
                    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                    items.groupBy { item ->
                        if (item.dateAddedMs > 0) sdf.format(Date(item.dateAddedMs)) else "Tanggal Tidak Diketahui"
                    }
                }
                GroupByOption.MONTH -> {
                    val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
                    items.groupBy { item ->
                        if (item.dateAddedMs > 0) sdf.format(Date(item.dateAddedMs)) else "Bulan Tidak Diketahui"
                    }
                }
            }
        }
    }
}

