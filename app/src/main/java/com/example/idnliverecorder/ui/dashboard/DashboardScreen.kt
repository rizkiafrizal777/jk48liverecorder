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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.data.AppInfo
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.data.RecordedVideoItem
import com.example.idnliverecorder.monitoring.LiveDetectionSettingsData
import com.example.idnliverecorder.recording.source.LiveSourceCapabilities
import com.example.idnliverecorder.recording.source.LiveSourceType
import com.example.idnliverecorder.recording.AudioCaptureStatus
import com.example.idnliverecorder.recording.RecordingConfig
import com.example.idnliverecorder.recording.RecordingState
import com.example.idnliverecorder.ui.history.HistoryPlaceholderSection
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
    state: RecordingState,
    recentRecordings: List<RecordedVideoItem>,
    optimalConfig: RecordingConfig?,
    isLiveDetectionActive: Boolean,
    isPermissionGranted: Boolean,
    settings: LiveDetectionSettingsData,
    recentDetections: List<DetectedLiveItem>,
    activeConfirmation: DetectedLiveItem?,
    todayCount: Int,
    installedApps: List<AppInfo>,
    isLoadingApps: Boolean,
    availableSources: List<LiveSourceCapabilities>,
    selectedSource: LiveSourceType,
    onSelectSource: (LiveSourceType) -> Unit,
    jkt48UiState: Jkt48LiveUiState,
    jkt48ApiKey: String,
    onRefreshJkt48: () -> Unit,
    onToggleFavoriteJkt48: (String) -> Unit,
    onSaveJkt48ApiKey: (String) -> Unit,
    onLoadDemoDataJkt48: () -> Unit = {},
    onStartRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onDismissStatus: () -> Unit,
    onRefreshRecordings: () -> Unit,
    onRefreshPermission: () -> Unit,
    onOpenAppPicker: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onSetDetectionEnabled: (Boolean) -> Unit,
    onSetKeywords: (List<String>) -> Unit,
    onSetCooldown: (Int) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onConfirmLiveRecording: (DetectedLiveItem) -> Unit,
    onDismissLiveConfirmation: (DetectedLiveItem) -> Unit,
    onSimulateTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Dashboard, 1 = Pengaturan
    var showAppPickerDialog by remember { mutableStateOf(false) }

    // Live Confirmation Dialog when a live stream is detected
    if (activeConfirmation != null) {
        LiveConfirmationDialog(
            item = activeConfirmation,
            onConfirmRecording = { onConfirmLiveRecording(activeConfirmation) },
            onDismiss = { onDismissLiveConfirmation(activeConfirmation) }
        )
    }

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
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // App Header
        AppHeader(onRefresh = {
            onRefreshRecordings()
            onRefreshPermission()
            onRefreshJkt48()
        })

        Spacer(modifier = Modifier.height(14.dp))

        // Navigation Tabs (Dashboard / JKT48 Live / Pengaturan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            // Tab 0: Dashboard
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 0) PrimaryRed else Color.Transparent)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = if (selectedTab == 0) Color.White else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Dashboard",
                        color = if (selectedTab == 0) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Tab 1: JKT48 Live
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 1) PrimaryRed else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = if (selectedTab == 1) Color.White else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "JKT48 Live",
                        color = if (selectedTab == 1) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Tab 2: Pengaturan
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == 2) PrimaryRed else Color.Transparent)
                    .clickable { selectedTab = 2 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (selectedTab == 2) Color.White else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pengaturan",
                        color = if (selectedTab == 2) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Feedback / Error / Saved Banner
        StatusAlertBanner(state = state, onDismiss = onDismissStatus)

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                // TAB 0: DASHBOARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // 1. Live Detection Status Card (Phase 2)
                    LiveDetectionStatusCard(
                        isDetectionActive = isLiveDetectionActive,
                        isPermissionGranted = isPermissionGranted,
                        settings = settings,
                        todayCount = todayCount,
                        latestDetection = recentDetections.firstOrNull(),
                        onOpenSettings = { selectedTab = 2 },
                        onSimulateTest = onSimulateTest
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Recording Source Selection (Phase 4)
                    SourceSelectionCard(
                        availableSources = availableSources,
                        selectedSource = selectedSource,
                        onSelectSource = onSelectSource
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Main Recording Controller Card (Phase 1-4 Preserved)
                    RecordingControlCard(
                        state = state,
                        optimalConfig = optimalConfig,
                        selectedSource = selectedSource,
                        onStartRecording = onStartRecordingClick,
                        onStopRecording = onStopRecordingClick
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 3. Recent Live Detections Section (Phase 2)
                    RecentDetectionsSection(
                        detections = recentDetections,
                        onConfirmClick = { item -> onConfirmLiveRecording(item) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. Recording History (Phase 1 Preserved)
                    HistoryPlaceholderSection(recordings = recentRecordings)

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            1 -> {
                // TAB 1: JKT48 LIVE MONITOR (Phase 4A)
                Jkt48LiveMonitorScreen(
                    uiState = jkt48UiState,
                    apiKey = jkt48ApiKey,
                    onRefresh = onRefreshJkt48,
                    onToggleFavorite = onToggleFavoriteJkt48,
                    onSaveApiKey = onSaveJkt48ApiKey,
                    onLoadDemoData = onLoadDemoDataJkt48,
                    modifier = Modifier.weight(1f)
                )
            }

            2 -> {
                // TAB 2: PENGATURAN & PERMISSIONS (Phase 2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    FullSettingsSection(
                        isPermissionGranted = isPermissionGranted,
                        settings = settings,
                        activeConfig = optimalConfig,
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
                text = "Screen Recording & Live Monitoring Engine • JKT48Connect Review",
                color = TextTertiary,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Status",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun StatusAlertBanner(
    state: RecordingState,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = state is RecordingState.Saved || state is RecordingState.Error,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        when (state) {
            is RecordingState.Saved -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SuccessBg)
                        .border(1.dp, AccentGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Recording Saved & Validated",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${state.displayName} (${state.sizeBytes.formatFileSize()})",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            is RecordingState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DangerBg)
                        .border(1.dp, PrimaryRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = PrimaryRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Recording Error",
                                    color = PrimaryRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = state.message,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun RecordingControlCard(
    state: RecordingState,
    optimalConfig: RecordingConfig?,
    selectedSource: LiveSourceType,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val isRecording = state is RecordingState.Recording
    val isStarting = state is RecordingState.Starting
    val isStopping = state is RecordingState.Stopping

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isRecording) PrimaryRed else CardBorder,
        label = "borderAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Badge Chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceVariantDark)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (state) {
                is RecordingState.Recording -> PrimaryRed
                is RecordingState.Starting -> AccentAmber
                is RecordingState.Stopping -> AccentAmber
                is RecordingState.Saved -> AccentGreen
                is RecordingState.Error -> PrimaryRed
                is RecordingState.Idle -> AccentCyan
            }

            val statusText = when (state) {
                is RecordingState.Recording -> "RECORDING LIVE"
                is RecordingState.Starting -> "INITIALIZING..."
                is RecordingState.Stopping -> "STOPPING & SAVING..."
                is RecordingState.Saved -> "SAVED"
                is RecordingState.Error -> "ERROR"
                is RecordingState.Idle -> "STANDBY READY"
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
                    .alpha(if (isRecording) pulseAlpha else 1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Timer Display
        val durationFormatted = if (state is RecordingState.Recording) {
            state.durationMs.formatDuration()
        } else {
            "00:00"
        }

        Text(
            text = durationFormatted,
            color = if (isRecording) TextPrimary else TextSecondary,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Resolution & Codec Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141722))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HighQuality,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = optimalConfig?.let {
                    "${it.width}x${it.height} • ${it.frameRate} FPS • ${it.codecLabel}"
                } ?: "Target: 1080p • 30 FPS • H.264 / AAC",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Source Mode Tag (Phase 4)
        Text(
            text = "Mode: ${selectedSource.displayName}",
            color = AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Audio capture status (shown during active recording)
        if (state is RecordingState.Recording) {
            val audioStatus = state.audioStatus
            if (audioStatus != AudioCaptureStatus.NotStarted) {
                Spacer(modifier = Modifier.height(8.dp))
                val (audioText, audioColor) = when (audioStatus) {
                    AudioCaptureStatus.Active -> Pair("🔊 Internal audio: Active", AccentGreen)
                    AudioCaptureStatus.VideoOnly -> Pair("🔇 Internal audio unavailable — Video only", AccentAmber)
                    AudioCaptureStatus.PermissionDenied -> Pair("🚫 RECORD_AUDIO permission denied", PrimaryRed)
                    AudioCaptureStatus.AppRestrictsCapture -> Pair("🔒 App restricts audio capture", AccentAmber)
                    AudioCaptureStatus.InitFailed -> Pair("⚠ Audio init failed — Video only", AccentAmber)
                    AudioCaptureStatus.NoEligiblePlayback -> Pair("🔇 No eligible playback detected", AccentAmber)
                    AudioCaptureStatus.Initializing -> Pair("⏳ Initializing audio capture...", AccentCyan)
                    else -> Pair("", TextSecondary)
                }
                if (audioText.isNotBlank()) {
                    Text(
                        text = audioText,
                        color = audioColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Primary Start / Stop Action Buttons
        if (isRecording || isStopping) {
            Button(
                onClick = onStopRecording,
                enabled = !isStopping,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = Color.White,
                    disabledContainerColor = SurfaceVariantDark,
                    disabledContentColor = TextTertiary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isStopping) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Finalizing File...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Stop Recording", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Button(
                onClick = onStartRecording,
                enabled = !isStarting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = Color.White,
                    disabledContainerColor = SurfaceVariantDark,
                    disabledContentColor = TextTertiary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Starting Engine...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Start Recording", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
