package com.example.idnliverecorder.library.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.idnliverecorder.core.formatDate
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.library.RecordingLibraryItem
import com.example.idnliverecorder.library.RecordingLibraryRepository
import com.example.idnliverecorder.library.RecordingManagementController
import com.example.idnliverecorder.library.export.ExportProgressState
import com.example.idnliverecorder.library.organization.DurationFilter
import com.example.idnliverecorder.library.organization.GroupByOption
import com.example.idnliverecorder.library.organization.SizeFilter
import com.example.idnliverecorder.ui.theme.AccentAmber
import com.example.idnliverecorder.ui.theme.AccentCyan
import com.example.idnliverecorder.ui.theme.AccentGreen
import com.example.idnliverecorder.ui.theme.CardBorder
import com.example.idnliverecorder.ui.theme.DangerBg
import com.example.idnliverecorder.ui.theme.DarkBackground
import com.example.idnliverecorder.ui.theme.PrimaryRed
import com.example.idnliverecorder.ui.theme.SurfaceDark
import com.example.idnliverecorder.ui.theme.SurfaceVariantDark
import com.example.idnliverecorder.ui.theme.TextPrimary
import com.example.idnliverecorder.ui.theme.TextSecondary
import com.example.idnliverecorder.ui.theme.TextTertiary

/**
 * Recording Library Screen (Phase 6 & Phase 7).
 * Central hub for organizing, filtering, bookmarking, and managing IDN live recordings.
 */
@Composable
fun RecordingLibraryScreen(
    viewModel: RecordingLibraryViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.refreshLibrary()
    }

    val context = LocalContext.current

    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val groupedRecordings by viewModel.groupedRecordings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    val currentExportState by viewModel.currentExportState.collectAsStateWithLifecycle()
    val activeBookmarks by viewModel.activeBookmarks.collectAsStateWithLifecycle()

    val selectedDetailItem by viewModel.selectedItemForDetail.collectAsStateWithLifecycle()
    val selectedAdvancedMeta by viewModel.selectedItemAdvancedMetadata.collectAsStateWithLifecycle()
    val selectedPlaybackItem by viewModel.selectedItemForPlayback.collectAsStateWithLifecycle()
    val selectedRenameItem by viewModel.selectedItemForRename.collectAsStateWithLifecycle()
    val selectedDeleteItem by viewModel.selectedItemForDelete.collectAsStateWithLifecycle()
    val selectedRecoveryItem by viewModel.selectedItemForRecovery.collectAsStateWithLifecycle()
    val showMultiDeleteDialog by viewModel.showMultiDeleteDialog.collectAsStateWithLifecycle()

    // SAF Background Export Launcher
    var pendingExportSourceItem by remember { mutableStateOf<RecordingLibraryItem?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        if (destUri != null && pendingExportSourceItem != null) {
            val sourceItem = pendingExportSourceItem!!
            viewModel.startBackgroundExport(sourceItem, destUri) { result ->
                if (result.isSuccess) {
                    Toast.makeText(context, "Ekspor dimulai di background...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal memulai ekspor: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingExportSourceItem = null
    }

    // RecoverableSecurityException Launcher for MediaStore rename on Android 10+
    val recoverableSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.refreshLibrary()
            Toast.makeText(context, "Izin diberikan. Nama file diperbarui.", Toast.LENGTH_SHORT).show()
        }
    }

    // Active Fullscreen / In-App Video Player Overlay
    selectedPlaybackItem?.let { item ->
        RecordingPlayerScreen(
            item = item,
            bookmarks = activeBookmarks,
            onAddBookmark = { ts, title, note -> viewModel.addBookmark(ts, title, note) },
            onUpdateBookmark = { bm -> viewModel.updateBookmark(bm) },
            onDeleteBookmark = { bm -> viewModel.deleteBookmark(bm) },
            onClose = { viewModel.selectedItemForPlayback.value = null }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Multi-Select Action Bar OR Normal Header
        if (isMultiSelectMode) {
            MultiSelectHeader(
                selectedCount = selectedIds.size,
                onSelectAll = { viewModel.selectAll() },
                onShare = {
                    val shareIntent = viewModel.createMultiShareIntent()
                    if (shareIntent != null) context.startActivity(shareIntent)
                },
                onDelete = { viewModel.showMultiDeleteDialog.value = true },
                onCancel = { viewModel.clearSelection() }
            )
        } else {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recording Library",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${recordings.size} rekaman tersimpan",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setMultiSelectMode(true) }) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Pilih Multi Rekaman",
                            tint = AccentCyan
                        )
                    }
                    IconButton(onClick = { viewModel.refreshLibrary() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentCyan
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Background Export Banner (Phase 7)
        if (currentExportState.isRunning) {
            BackgroundExportBanner(
                state = currentExportState,
                onCancel = { viewModel.cancelActiveExport() }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Storage Analyzer Card
        StorageInfoCard(storageInfo = storageInfo)

        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = filters.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Cari nama member atau judul file...", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (filters.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Multi-Dimensional Organization Filters (Phase 7)
        OrganizationFilterChipsRow(
            filters = filters,
            sortOption = sortOption,
            onSetSort = { viewModel.setSortOption(it) },
            onSetGroup = { viewModel.setGroupBy(it) },
            onSetSize = { viewModel.setSizeFilter(it) },
            onSetDuration = { viewModel.setDurationFilter(it) },
            onToggleFavoritesOnly = { viewModel.setFavoritesOnly(!filters.favoritesOnly) },
            onResetFilters = { viewModel.resetFilters() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Loading, Error, Empty, or Grouped Video List
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && recordings.isEmpty() -> {
                    CircularProgressIndicator(
                        color = PrimaryRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null && recordings.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Error, null, tint = PrimaryRed, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage ?: "Terjadi kesalahan",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.refreshLibrary() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                        ) {
                            Text("Coba Lagi")
                        }
                    }
                }
                recordings.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Movie, null, tint = TextTertiary, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (filters.favoritesOnly) "Belum ada rekaman favorit." else "Tidak ada rekaman video yang cocok.",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Rekaman akan otomatis tersimpan di Movies/IDNLiveRecorder/",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        groupedRecordings.forEach { (groupTitle, groupItems) ->
                            if (filters.groupBy != GroupByOption.NONE) {
                                item(key = "header_$groupTitle") {
                                    Text(
                                        text = "$groupTitle (${groupItems.size})",
                                        color = AccentCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            items(groupItems, key = { it.id }) { item ->
                                val isSelected = selectedIds.contains(item.id)
                                val isFav = favoriteIds.contains(item.id)

                                RecordingItemCard(
                                    item = item,
                                    isFavorite = isFav,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onSelectToggle = { viewModel.toggleSelectItem(item.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    onPlay = {
                                        if (isMultiSelectMode) {
                                            viewModel.toggleSelectItem(item.id)
                                        } else if (item.isPlayable) {
                                            viewModel.selectedItemForPlayback.value = item
                                        } else {
                                            viewModel.selectedItemForRecovery.value = item
                                        }
                                    },
                                    onDetail = { viewModel.selectedItemForDetail.value = item },
                                    onRename = { viewModel.selectedItemForRename.value = item },
                                    onDelete = { viewModel.selectedItemForDelete.value = item },
                                    onShare = {
                                        val shareIntent = viewModel.createShareIntent(item)
                                        context.startActivity(shareIntent)
                                    },
                                    onExport = {
                                        pendingExportSourceItem = item
                                        exportLauncher.launch(item.displayName)
                                    },
                                    onInspectRecovery = { viewModel.selectedItemForRecovery.value = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Technical Detail Modal Dialog ---
    selectedDetailItem?.let { item ->
        RecordingDetailDialog(
            item = item,
            advancedMetadata = selectedAdvancedMeta,
            onDismiss = { viewModel.selectedItemForDetail.value = null }
        )
    }

    // --- Rename Dialog ---
    selectedRenameItem?.let { item ->
        RenameDialog(
            item = item,
            onDismiss = { viewModel.selectedItemForRename.value = null },
            onConfirm = { newName ->
                viewModel.renameRecording(item, newName) { result ->
                    when (result) {
                        is RecordingManagementController.RenameResult.Success -> {
                            Toast.makeText(context, "Nama file berhasil diubah.", Toast.LENGTH_SHORT).show()
                        }
                        is RecordingManagementController.RenameResult.PermissionRequired -> {
                            recoverableSenderLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(
                                    result.securityException.userAction.actionIntent.intentSender
                                ).build()
                            )
                        }
                        is RecordingManagementController.RenameResult.Failure -> {
                            Toast.makeText(context, result.errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    selectedDeleteItem?.let { item ->
        DeleteConfirmationDialog(
            item = item,
            onDismiss = { viewModel.selectedItemForDelete.value = null },
            onConfirm = {
                viewModel.deleteRecording(item) { success ->
                    if (success) {
                        Toast.makeText(context, "Rekaman berhasil dihapus.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal menghapus file rekaman.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // --- Multi Delete Confirmation Dialog ---
    if (showMultiDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showMultiDeleteDialog.value = false },
            title = { Text("Hapus ${selectedIds.size} Rekaman?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus ${selectedIds.size} video rekaman yang dipilih? Operasi ini tidak dapat dibatalkan.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected { count ->
                            Toast.makeText(context, "$count rekaman berhasil dihapus.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerBg)
                ) {
                    Text("Hapus Semua Terpilih", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showMultiDeleteDialog.value = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // --- Recovery Dialog ---
    selectedRecoveryItem?.let { item ->
        RecoveryDialog(
            item = item,
            onDismiss = { viewModel.selectedItemForRecovery.value = null },
            onRecover = {
                viewModel.attemptRecovery(item) { success ->
                    if (success) {
                        Toast.makeText(context, "Pemulihan berhasil. File siap diputar.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Pemulihan gagal: data video tidak dapat diselamatkan.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDelete = {
                viewModel.deleteRecording(item) {
                    viewModel.selectedItemForRecovery.value = null
                }
            }
        )
    }
}

/**
 * Top action bar displayed during multi-selection mode (Phase 7).
 */
@Composable
private fun MultiSelectHeader(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Batal", tint = TextSecondary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$selectedCount dipilih",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSelectAll, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SelectAll, contentDescription = "Pilih Semua", tint = TextPrimary)
            }
            if (selectedCount > 0) {
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = AccentGreen)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = DangerBg)
                }
            }
        }
    }
}

/**
 * Real-time background export progress banner with ETA and cancel action (Phase 7).
 */
@Composable
private fun BackgroundExportBanner(
    state: ExportProgressState,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, AccentCyan, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = AccentCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mengekspor: ${state.displayName}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
            }
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Batalkan", color = DangerBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { state.progressPercent / 100f },
            color = AccentCyan,
            trackColor = Color.White.copy(alpha = 0.1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
        )

        Spacer(modifier = Modifier.height(4.dp))

        val etaText = if (state.etaSeconds >= 0) {
            if (state.etaSeconds < 60) "~${state.etaSeconds} dtk" else "~${state.etaSeconds / 60}m"
        } else "Menghitung..."

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${state.progressPercent}% (${formatFileSize(state.copiedBytes)} / ${formatFileSize(state.totalBytes)})",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Sisa: $etaText",
                color = AccentAmber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Filter chips row supporting multi-dimensional filtering simultaneously (Phase 7).
 */
@Composable
private fun OrganizationFilterChipsRow(
    filters: com.example.idnliverecorder.library.organization.OrganizationFilters,
    sortOption: RecordingLibraryRepository.SortOption,
    onSetSort: (RecordingLibraryRepository.SortOption) -> Unit,
    onSetGroup: (GroupByOption) -> Unit,
    onSetSize: (SizeFilter) -> Unit,
    onSetDuration: (DurationFilter) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onResetFilters: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showSizeMenu by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Favorites Toggle Chip
        item {
            FilterChip(
                selected = filters.favoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = { Text("Favorit", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        if (filters.favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (filters.favoritesOnly) AccentAmber else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentAmber.copy(alpha = 0.2f),
                    selectedLabelColor = AccentAmber
                )
            )
        }

        // Group By Dropdown Chip
        item {
            Box {
                FilterChip(
                    selected = filters.groupBy != GroupByOption.NONE,
                    onClick = { showGroupMenu = true },
                    label = { Text(filters.groupBy.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AccentCyan
                    )
                )
                DropdownMenu(
                    expanded = showGroupMenu,
                    onDismissRequest = { showGroupMenu = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    GroupByOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, color = TextPrimary, fontSize = 12.sp) },
                            onClick = {
                                onSetGroup(option)
                                showGroupMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Sort By Dropdown Chip
        item {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { showSortMenu = true },
                    label = {
                        val sortLabel = when (sortOption) {
                            RecordingLibraryRepository.SortOption.NEWEST_FIRST -> "Terbaru"
                            RecordingLibraryRepository.SortOption.OLDEST_FIRST -> "Terlama"
                            RecordingLibraryRepository.SortOption.MEMBER_NAME_ASC -> "Nama Member"
                            RecordingLibraryRepository.SortOption.SIZE_DESC -> "Ukuran File"
                        }
                        Text("Urut: $sortLabel", fontSize = 11.sp)
                    }
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    RecordingLibraryRepository.SortOption.values().forEach { opt ->
                        val label = when (opt) {
                            RecordingLibraryRepository.SortOption.NEWEST_FIRST -> "Terbaru"
                            RecordingLibraryRepository.SortOption.OLDEST_FIRST -> "Terlama"
                            RecordingLibraryRepository.SortOption.MEMBER_NAME_ASC -> "Nama Member"
                            RecordingLibraryRepository.SortOption.SIZE_DESC -> "Ukuran Terbesar"
                        }
                        DropdownMenuItem(
                            text = { Text(label, color = TextPrimary, fontSize = 12.sp) },
                            onClick = {
                                onSetSort(opt)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Size Filter Dropdown Chip
        item {
            Box {
                FilterChip(
                    selected = filters.sizeFilter != SizeFilter.ALL,
                    onClick = { showSizeMenu = true },
                    label = { Text(filters.sizeFilter.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AccentCyan
                    )
                )
                DropdownMenu(
                    expanded = showSizeMenu,
                    onDismissRequest = { showSizeMenu = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    SizeFilter.values().forEach { sizeOpt ->
                        DropdownMenuItem(
                            text = { Text(sizeOpt.label, color = TextPrimary, fontSize = 12.sp) },
                            onClick = {
                                onSetSize(sizeOpt)
                                showSizeMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Duration Filter Dropdown Chip
        item {
            Box {
                FilterChip(
                    selected = filters.durationFilter != DurationFilter.ALL,
                    onClick = { showDurationMenu = true },
                    label = { Text(filters.durationFilter.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AccentCyan
                    )
                )
                DropdownMenu(
                    expanded = showDurationMenu,
                    onDismissRequest = { showDurationMenu = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    DurationFilter.values().forEach { durOpt ->
                        DropdownMenuItem(
                            text = { Text(durOpt.label, color = TextPrimary, fontSize = 12.sp) },
                            onClick = {
                                onSetDuration(durOpt)
                                showDurationMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Reset Filters Chip
        if (filters.searchQuery.isNotBlank() || filters.favoritesOnly || filters.sizeFilter != SizeFilter.ALL || filters.durationFilter != DurationFilter.ALL || filters.groupBy != GroupByOption.NONE) {
            item {
                TextButton(onClick = onResetFilters) {
                    Text("Reset Filter", color = PrimaryRed, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Individual recorded video card with favorite star, selection checkbox, and overflow menu (Phase 7).
 */
@Composable
private fun RecordingItemCard(
    item: RecordingLibraryItem,
    isFavorite: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onInspectRecovery: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (isSelected) AccentCyan else if (item.isPlayable) CardBorder else PrimaryRed.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox in Multi-Select Mode
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentCyan,
                    uncheckedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Thumbnail / Play Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (item.isPlayable) PrimaryRed.copy(alpha = 0.15f) else DangerBg.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isPlayable) Icons.Default.PlayArrow else Icons.Default.Build,
                contentDescription = if (item.isPlayable) "Putar" else "Perlu Pemulihan",
                tint = if (item.isPlayable) PrimaryRed else AccentAmber,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Video Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.memberName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Favorite Star Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) AccentAmber else TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.displayName,
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.durationMs.formatDuration(),
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = " • ", color = TextTertiary, fontSize = 10.sp)
                Text(
                    text = item.sizeBytes.formatFileSize(),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(text = " • ", color = TextTertiary, fontSize = 10.sp)
                Text(
                    text = item.resolution,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Overflow Actions Menu
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu Aksi",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Detail Teknis", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = AccentCyan) },
                    onClick = { showMenu = false; onDetail() }
                )
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Star, null, tint = AccentAmber) },
                    onClick = { showMenu = false; onToggleFavorite() }
                )
                if (item.isPlayable) {
                    DropdownMenuItem(
                        text = { Text("Bagikan (Share)", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = AccentGreen) },
                        onClick = { showMenu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Ekspor File (SAF Background)", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = AccentCyan) },
                        onClick = { showMenu = false; onExport() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Ubah Nama (Rename)", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextSecondary) },
                    onClick = { showMenu = false; onRename() }
                )
                if (!item.isPlayable) {
                    DropdownMenuItem(
                        text = { Text("Coba Pulihkan", color = AccentAmber) },
                        leadingIcon = { Icon(Icons.Default.Build, null, tint = AccentAmber) },
                        onClick = { showMenu = false; onInspectRecovery() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Hapus Video", color = DangerBg) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerBg) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
