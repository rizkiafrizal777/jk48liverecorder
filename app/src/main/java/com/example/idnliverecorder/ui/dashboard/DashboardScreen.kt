package com.example.idnliverecorder.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.data.AppInfo
import com.example.idnliverecorder.library.ui.RecordingLibraryScreen
import com.example.idnliverecorder.library.ui.RecordingLibraryViewModel
import com.example.idnliverecorder.monitoring.LiveDetectionSettingsData
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import com.example.idnliverecorder.ui.jkt48.Jkt48LiveMonitorScreen
import com.example.idnliverecorder.ui.jkt48.Jkt48LiveUiState
import com.example.idnliverecorder.ui.settings.AppSelectionDialog
import com.example.idnliverecorder.ui.settings.FullSettingsSection
import com.example.idnliverecorder.ui.theme.AccentAmber
import com.example.idnliverecorder.ui.theme.AccentCyan
import com.example.idnliverecorder.ui.theme.AccentGreen
import com.example.idnliverecorder.ui.theme.CardBorder
import com.example.idnliverecorder.ui.theme.DangerBg
import com.example.idnliverecorder.ui.theme.DarkBackground
import com.example.idnliverecorder.ui.theme.PrimaryRed
import com.example.idnliverecorder.ui.theme.SuccessBg
import com.example.idnliverecorder.ui.theme.SurfaceDark
import com.example.idnliverecorder.ui.theme.SurfaceVariantDark
import com.example.idnliverecorder.ui.theme.TextPrimary
import com.example.idnliverecorder.ui.theme.TextSecondary
import com.example.idnliverecorder.ui.theme.TextTertiary

@Composable
fun DashboardScreen(
    isPermissionGranted: Boolean,
    settings: LiveDetectionSettingsData,
    installedApps: List<AppInfo>,
    isLoadingApps: Boolean,
    jkt48UiState: Jkt48LiveUiState,
    jkt48ApiKey: String,
    onRefreshJkt48: () -> Unit,
    onToggleFavoriteJkt48: (String) -> Unit,
    onSaveJkt48ApiKey: (String) -> Unit,
    directRecordingStatus: DirectStreamStatus = DirectStreamStatus.SOURCE_UNAVAILABLE,
    directRecordingDurationMs: Long = 0L,
    activeRecordingMember: String? = null,
    onStartDirectRecording: (Jkt48LiveMember) -> Unit = {},
    onStopDirectRecording: () -> Unit = {},
    libraryViewModel: RecordingLibraryViewModel? = null,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    onRefreshPermission: () -> Unit,
    onOpenAppPicker: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onSetDetectionEnabled: (Boolean) -> Unit,
    onSetKeywords: (List<String>) -> Unit,
    onSetCooldown: (Int) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showAppPickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 || selectedTab == 2) {
            libraryViewModel?.refreshLibrary()
        }
    }

    val libRecordings by (libraryViewModel?.rawRecordings?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) })
    val libFavoriteIds by (libraryViewModel?.favoriteIds?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptySet()) })
    val libRecentExports by (libraryViewModel?.recentExports?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) })
    val isLoadingLibrary by (libraryViewModel?.isLoading?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) })
    val libraryError by (libraryViewModel?.errorMessage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) })

    // App Picker Dialog
    if (showAppPickerDialog) {
        AppSelectionDialog(
            installedApps = installedApps,
            isLoading = isLoadingApps,
            currentSelectedPackage = settings.targetPackageName,
            onAppSelected = { app ->
                onAppSelected(app)
                showAppPickerDialog = false
            },
            onDismiss = { showAppPickerDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            AppHeader(onRefresh = {
                libraryViewModel?.refreshLibrary()
                onRefreshPermission()
                onRefreshJkt48()
            })
            Spacer(modifier = Modifier.height(16.dp))
        when (selectedTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    DashboardAnalyticsSection(
                        recordings = libRecordings,
                        favoriteCount = libFavoriteIds.size,
                        recentExports = libRecentExports,
                        onOpenLibrary = { onTabSelected(2) },
                        isLoading = isLoadingLibrary,
                        loadError = libraryError,
                        onRetry = { libraryViewModel?.refreshLibrary() }
                    )

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            1 -> {
                // TAB 1: JKT48 LIVE MONITOR (Phase 4A & 5)
                Jkt48LiveMonitorScreen(
                    uiState = jkt48UiState,
                    apiKey = jkt48ApiKey,
                    directRecordingStatus = directRecordingStatus,
                    directRecordingDurationMs = directRecordingDurationMs,
                    activeRecordingMember = activeRecordingMember,
                    onRefresh = onRefreshJkt48,
                    onToggleFavorite = onToggleFavoriteJkt48,
                    onSaveApiKey = onSaveJkt48ApiKey,
                    onStartDirectRecording = onStartDirectRecording,
                    onStopDirectRecording = onStopDirectRecording,
                    modifier = Modifier.weight(1f)
                )
            }

            2 -> {
                // TAB 2: RECORDING LIBRARY & PLAYBACK (Phase 6)
                if (libraryViewModel != null) {
                    RecordingLibraryScreen(
                        viewModel = libraryViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            3 -> {
                // TAB 3: PENGATURAN & PERMISSIONS (Phase 2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    FullSettingsSection(
                        isPermissionGranted = isPermissionGranted,
                        settings = settings,
                        onRefreshPermission = onRefreshPermission,
                        onOpenAppPicker = {
                            onOpenAppPicker()
                            showAppPickerDialog = true
                        },
                        onSetDetectionEnabled = onSetDetectionEnabled,
                        onSetKeywords = onSetKeywords,
                        onSetCooldown = onSetCooldown,
                        onSetNotificationsEnabled = onSetNotificationsEnabled
                    )

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        }
        MainBottomNavigation(selectedTab = selectedTab, onTabSelected = onTabSelected)
    }
}

@Composable
private fun MainBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = PrimaryRed,
            indicatorColor = PrimaryRed.copy(alpha = 0.18f),
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
        )

        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Beranda") },
            alwaysShowLabel = true,
            colors = itemColors
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
            label = { Text("Live") },
            alwaysShowLabel = true,
            colors = itemColors
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
            label = { Text("Rekaman") },
            alwaysShowLabel = true,
            colors = itemColors
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Setelan") },
            alwaysShowLabel = true,
            colors = itemColors
        )
    }
}
@Composable
private fun AppHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(PrimaryRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IDN Live Recorder",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Pantau live dan rekam siaran",
                color = TextTertiary,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Muat ulang data",
                tint = TextSecondary
            )
        }
    }
}

