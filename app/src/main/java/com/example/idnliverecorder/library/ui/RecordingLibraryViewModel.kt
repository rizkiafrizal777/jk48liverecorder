package com.example.idnliverecorder.library.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idnliverecorder.database.ExportTask
import com.example.idnliverecorder.database.RecordingBookmark
import com.example.idnliverecorder.library.RecordingIntegrityValidator
import com.example.idnliverecorder.library.RecordingLibraryItem
import com.example.idnliverecorder.library.RecordingLibraryRepository
import com.example.idnliverecorder.library.RecordingManagementController
import com.example.idnliverecorder.library.RecordingStorageAnalyzer
import com.example.idnliverecorder.library.bookmark.RecordingBookmarkRepository
import com.example.idnliverecorder.library.export.ExportProgressState
import com.example.idnliverecorder.library.export.RecordingExportManager
import com.example.idnliverecorder.library.export.RecordingExportService
import com.example.idnliverecorder.library.metadata.AdvancedRecordingMetadata
import com.example.idnliverecorder.library.metadata.AdvancedVideoMetadataRetriever
import com.example.idnliverecorder.library.organization.DurationFilter
import com.example.idnliverecorder.library.organization.GroupByOption
import com.example.idnliverecorder.library.organization.OrganizationFilters
import com.example.idnliverecorder.library.organization.RecordingOrganizationRepository
import com.example.idnliverecorder.library.organization.SizeFilter
import com.example.idnliverecorder.library.reliability.RecordingFileLockManager
import com.example.idnliverecorder.library.reliability.RecordingReliabilityManager
import com.example.idnliverecorder.recording.RecordingEventBridge
import com.example.idnliverecorder.recording.direct.DirectRecordingService
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Recording Library screen, organization, bookmarks, and background exports (Phase 7).
 */
class RecordingLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingLibraryRepository(application)
    val controller = RecordingManagementController(application)
    val bookmarkRepo = RecordingBookmarkRepository(application)
    val exportManager = RecordingExportManager(application)

    private var refreshJob: Job? = null
    val orgRepo = RecordingOrganizationRepository(application)
    val reliabilityManager = RecordingReliabilityManager(application)

    // Raw loaded recordings from MediaStore
    private val _rawRecordings = MutableStateFlow<List<RecordingLibraryItem>>(emptyList())
    val rawRecordings: StateFlow<List<RecordingLibraryItem>> = _rawRecordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Sort & Filters
    private val _sortOption = MutableStateFlow(RecordingLibraryRepository.SortOption.NEWEST_FIRST)
    val sortOption: StateFlow<RecordingLibraryRepository.SortOption> = _sortOption.asStateFlow()

    private val _filters = MutableStateFlow(OrganizationFilters())
    val filters: StateFlow<OrganizationFilters> = _filters.asStateFlow()

    // Favorites set from Room database
    val favoriteIds: StateFlow<Set<Long>> = orgRepo.favoriteIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Filtered recordings combining Search, Date, Size, Duration, and Favorites
    val recordings: StateFlow<List<RecordingLibraryItem>> = combine(
        _rawRecordings,
        _filters,
        favoriteIds
    ) { raw, flt, favs ->
        RecordingOrganizationRepository.applyFilters(raw, flt, favs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouped recordings mapping
    val groupedRecordings: StateFlow<Map<String, List<RecordingLibraryItem>>> = combine(
        recordings,
        _filters
    ) { filtered, flt ->
        RecordingOrganizationRepository.groupRecordings(filtered, flt.groupBy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Storage Usage
    private val _storageInfo = MutableStateFlow(
        RecordingStorageAnalyzer.StorageUsageInfo(0L, 0, 0L, 0L, 0f)
    )
    val storageInfo: StateFlow<RecordingStorageAnalyzer.StorageUsageInfo> = _storageInfo.asStateFlow()

    // Multi-Select State
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // Modals / Dialog selection states
    val selectedItemForDetail = MutableStateFlow<RecordingLibraryItem?>(null)
    val selectedItemAdvancedMetadata = MutableStateFlow<AdvancedRecordingMetadata?>(null)
    val selectedItemForPlayback = MutableStateFlow<RecordingLibraryItem?>(null)
    val selectedItemForRename = MutableStateFlow<RecordingLibraryItem?>(null)
    val selectedItemForDelete = MutableStateFlow<RecordingLibraryItem?>(null)
    val selectedItemForRecovery = MutableStateFlow<RecordingLibraryItem?>(null)
    val showMultiDeleteDialog = MutableStateFlow(false)

    // Active bookmarks for currently playing video
    private val _activeBookmarks = MutableStateFlow<List<RecordingBookmark>>(emptyList())
    val activeBookmarks: StateFlow<List<RecordingBookmark>> = _activeBookmarks.asStateFlow()

    // Background Export State & History
    val currentExportState: StateFlow<ExportProgressState> = exportManager.currentExportState
    val recentExports: StateFlow<List<ExportTask>> = exportManager.recentExportsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshLibrary()

        // Auto-refresh when direct stream recording completes (Phase 7-8 Reliability)
        viewModelScope.launch {
            RecordingEventBridge.recordingCompletedEvents.collect { event ->
                Log.d("RecordingLibraryVM", "RecordingEventBridge received: ${event.displayName}, refreshing library with retry")
                refreshLibraryWithRetry(event.displayName)
            }
        }

        viewModelScope.launch {
            DirectRecordingService.engineStatus.collect { status ->
                if (status == DirectStreamStatus.COMPLETED) {
                    refreshLibraryWithRetry()
                }
            }
        }

        // Auto-refresh bookmarks when active video changes
        viewModelScope.launch {
            selectedItemForPlayback.collect { item ->
                if (item != null) {
                    loadBookmarksFor(item.id)
                } else {
                    _activeBookmarks.value = emptyList()
                }
            }
        }

        // Auto-load advanced metadata when detail is requested
        viewModelScope.launch {
            selectedItemForDetail.collect { item ->
                if (item != null) {
                    loadAdvancedMetadata(item)
                } else {
                    selectedItemAdvancedMetadata.value = null
                }
            }
        }
    }

    fun refreshLibrary() {
        refreshLibraryWithRetry(null, maxRetries = 1)
    }

    fun refreshLibraryWithRetry(expectedDisplayName: String? = null, maxRetries: Int = 3) {
        _isLoading.value = true
        _errorMessage.value = null

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            var attempt = 0
            var found = false
            while (isActive && attempt < maxRetries && !found) {
                try {
                    val list = repository.loadRecordings(
                        sortOption = _sortOption.value,
                        searchQuery = "", // Filtering handled by StateFlow pipeline
                        dateFilterMs = null
                    )
                    _rawRecordings.value = list
                    _storageInfo.value = RecordingStorageAnalyzer.analyze(list)

                    if (expectedDisplayName == null || list.any { it.displayName == expectedDisplayName }) {
                        found = true
                        Log.d("RecordingLibraryVM", "refreshLibraryWithRetry: Loaded ${list.size} recordings (found expected: $expectedDisplayName on attempt ${attempt + 1})")
                    } else {
                        Log.d("RecordingLibraryVM", "refreshLibraryWithRetry: Expected $expectedDisplayName not yet present in MediaStore, waiting... (attempt ${attempt + 1}/$maxRetries)")
                    }
                } catch (e: Exception) {
                    Log.w("RecordingLibraryVM", "refreshLibraryWithRetry error on attempt ${attempt + 1}: ${e.message}")
                    if (attempt == maxRetries - 1) {
                        _errorMessage.value = "Gagal memuat daftar rekaman: ${e.message}"
                    }
                }

                if (!found && attempt < maxRetries - 1 && isActive) {
                    delay(1000L * (attempt + 1))
                }
                attempt++
            }
            _isLoading.value = false
        }
    }

    // --- Filter Setters ---
    fun setSearchQuery(query: String) {
        _filters.value = _filters.value.copy(searchQuery = query)
    }

    fun setSortOption(option: RecordingLibraryRepository.SortOption) {
        _sortOption.value = option
        refreshLibrary()
    }

    fun setDateFilter(dateMs: Long?) {
        _filters.value = _filters.value.copy(dateFilterMs = dateMs)
    }

    fun setSizeFilter(sizeFilter: SizeFilter) {
        _filters.value = _filters.value.copy(sizeFilter = sizeFilter)
    }

    fun setDurationFilter(durationFilter: DurationFilter) {
        _filters.value = _filters.value.copy(durationFilter = durationFilter)
    }

    fun setFavoritesOnly(favoritesOnly: Boolean) {
        _filters.value = _filters.value.copy(favoritesOnly = favoritesOnly)
    }

    fun setGroupBy(groupBy: GroupByOption) {
        _filters.value = _filters.value.copy(groupBy = groupBy)
    }

    fun resetFilters() {
        _filters.value = OrganizationFilters()
    }

    // --- Favorites ---
    fun toggleFavorite(item: RecordingLibraryItem) {
        viewModelScope.launch {
            orgRepo.toggleFavorite(item)
        }
    }

    // --- Multi-Select ---
    fun setMultiSelectMode(enabled: Boolean) {
        _isMultiSelectMode.value = enabled
        if (!enabled) {
            _selectedIds.value = emptySet()
        }
    }

    fun toggleSelectItem(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
        if (current.isEmpty()) {
            _isMultiSelectMode.value = false
        }
    }

    fun selectAll() {
        _selectedIds.value = recordings.value.map { it.id }.toSet()
        _isMultiSelectMode.value = true
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun deleteSelected(onComplete: (Int) -> Unit) {
        val selectedItems = recordings.value.filter { _selectedIds.value.contains(it.id) }
        viewModelScope.launch {
            val result = controller.deleteMultipleRecordings(selectedItems)
            clearSelection()
            refreshLibrary()
            showMultiDeleteDialog.value = false
            onComplete(result.getOrDefault(0))
        }
    }

    fun createMultiShareIntent(): Intent? {
        val selectedItems = recordings.value.filter { _selectedIds.value.contains(it.id) }
        return if (selectedItems.isNotEmpty()) {
            controller.createMultiShareIntent(selectedItems)
        } else null
    }

    // --- Bookmarks ---
    fun loadBookmarksFor(recordingId: Long) {
        viewModelScope.launch {
            bookmarkRepo.getBookmarksForRecordingFlow(recordingId).collect {
                _activeBookmarks.value = it
            }
        }
    }

    fun addBookmark(timestampMs: Long, title: String, note: String? = null) {
        val activeItem = selectedItemForPlayback.value ?: return
        viewModelScope.launch {
            bookmarkRepo.addBookmark(
                recordingId = activeItem.id,
                recordingUri = activeItem.uri.toString(),
                timestampMs = timestampMs,
                title = title,
                note = note
            )
        }
    }

    fun updateBookmark(bookmark: RecordingBookmark) {
        viewModelScope.launch {
            bookmarkRepo.updateBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmark: RecordingBookmark) {
        viewModelScope.launch {
            bookmarkRepo.deleteBookmark(bookmark)
        }
    }

    // --- Background Export ---
    fun startBackgroundExport(item: RecordingLibraryItem, destUri: Uri, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = exportManager.startExport(
                sourceUri = item.uri,
                destUri = destUri,
                displayName = item.displayName,
                totalBytes = item.sizeBytes
            )
            onResult(result)
        }
    }

    fun cancelActiveExport() {
        exportManager.cancelActiveExport()
    }

    fun retryExport(task: ExportTask) {
        viewModelScope.launch {
            exportManager.retryExport(task)
        }
    }

    // --- Advanced Metadata ---
    private fun loadAdvancedMetadata(item: RecordingLibraryItem) {
        viewModelScope.launch {
            val meta = AdvancedVideoMetadataRetriever.getMetadata(getApplication(), item)
            selectedItemAdvancedMetadata.value = meta
        }
    }

    // --- Single Item Operations ---
    fun renameRecording(
        item: RecordingLibraryItem,
        newName: String,
        onComplete: (RecordingManagementController.RenameResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = controller.renameRecording(item, newName)
            if (result is RecordingManagementController.RenameResult.Success) {
                refreshLibrary()
                selectedItemForRename.value = null
            }
            onComplete(result)
        }
    }

    fun deleteRecording(item: RecordingLibraryItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = controller.deleteRecording(item)
            if (result.isSuccess) {
                selectedItemForDelete.value = null
                refreshLibrary()
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun createShareIntent(item: RecordingLibraryItem): Intent {
        return controller.createShareIntent(item)
    }

    fun attemptRecovery(item: RecordingLibraryItem, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = reliabilityManager.attemptRecovery(item)
            if (result.isSuccess) {
                refreshLibrary()
                selectedItemForRecovery.value = null
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }
}
