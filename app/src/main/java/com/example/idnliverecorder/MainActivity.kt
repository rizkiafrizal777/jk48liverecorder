package com.example.idnliverecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.recording.RecordingState
import com.example.idnliverecorder.recording.ScreenRecordingService
import com.example.idnliverecorder.ui.dashboard.DashboardScreen
import com.example.idnliverecorder.ui.dashboard.DashboardViewModel
import com.example.idnliverecorder.ui.jkt48.Jkt48LiveViewModel
import com.example.idnliverecorder.ui.theme.DarkBackground
import com.example.idnliverecorder.ui.theme.IDNLiveRecorderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val jkt48ViewModel: Jkt48LiveViewModel by viewModels()
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startRecording(result.resultCode, result.data!!)
        } else {
            ScreenRecordingService.updateState(
                RecordingState.Error("Media projection permission was cancelled or denied by user.")
            )
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkAudioPermissionAndStart()
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchScreenCaptureIntent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        handleIncomingIntent(intent)

        setContent {
            IDNLiveRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
                    val recentRecordings by viewModel.recentRecordings.collectAsStateWithLifecycle()
                    val optimalConfig by viewModel.optimalConfig.collectAsStateWithLifecycle()

                    // Phase 2 StateFlows
                    val isLiveDetectionActive by viewModel.isLiveDetectionActive.collectAsStateWithLifecycle()
                    val isPermissionGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
                    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
                    val recentDetections by viewModel.recentDetections.collectAsStateWithLifecycle()
                    val activeConfirmation by viewModel.activeConfirmation.collectAsStateWithLifecycle()
                    val todayCount by viewModel.todayDetectionCount.collectAsStateWithLifecycle()
                    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
                    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
                    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()

                    // Phase 4A JKT48 Live Monitor states
                    val jkt48UiState by jkt48ViewModel.uiState.collectAsStateWithLifecycle()
                    val jkt48ApiKey by jkt48ViewModel.apiKey.collectAsStateWithLifecycle()

                    DashboardScreen(
                        state = recordingState,
                        recentRecordings = recentRecordings,
                        optimalConfig = optimalConfig,
                        isLiveDetectionActive = isLiveDetectionActive,
                        isPermissionGranted = isPermissionGranted,
                        settings = settings,
                        recentDetections = recentDetections,
                        activeConfirmation = activeConfirmation,
                        todayCount = todayCount,
                        installedApps = installedApps,
                        isLoadingApps = isLoadingApps,
                        availableSources = viewModel.availableSources,
                        selectedSource = selectedSource,
                        onSelectSource = { type -> viewModel.selectSource(type) },
                        jkt48UiState = jkt48UiState,
                        jkt48ApiKey = jkt48ApiKey,
                        onRefreshJkt48 = { jkt48ViewModel.refresh() },
                        onToggleFavoriteJkt48 = { name -> jkt48ViewModel.toggleFavorite(name) },
                        onSaveJkt48ApiKey = { key -> jkt48ViewModel.saveApiKey(key) },
                        onLoadDemoDataJkt48 = { jkt48ViewModel.loadDemoData() },
                        onStartRecordingClick = { checkPermissionsAndStart() },
                        onStopRecordingClick = { viewModel.stopRecording() },
                        onDismissStatus = { viewModel.dismissStatus() },
                        onRefreshRecordings = { viewModel.refreshRecordings() },
                        onRefreshPermission = { viewModel.refreshPermissions() },
                        onOpenAppPicker = { viewModel.loadInstalledApps() },
                        onAppSelected = { app ->
                            viewModel.setTargetApplication(app.packageName, app.appName)
                        },
                        onSetDetectionEnabled = { enabled -> viewModel.setDetectionEnabled(enabled) },
                        onSetKeywords = { kws -> viewModel.setKeywords(kws) },
                        onSetCooldown = { mins -> viewModel.setCooldownMinutes(mins) },
                        onSetNotificationsEnabled = { enabled -> viewModel.setNotificationsEnabled(enabled) },
                        onConfirmLiveRecording = { item ->
                            viewModel.confirmLiveRecording(item) {
                                checkPermissionsAndStart()
                            }
                        },
                        onDismissLiveConfirmation = { item ->
                            viewModel.dismissLiveConfirmation(item)
                        },
                        onSimulateTest = { viewModel.simulateTestDetection() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Constants.ACTION_VIEW_DETECTION) {
            val detectionId = intent.getStringExtra(Constants.EXTRA_DETECTION_ID)
            if (!detectionId.isNullOrBlank()) {
                viewModel.handleViewDetectionIntent(detectionId)
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        checkAudioPermissionAndStart()
    }

    private fun checkAudioPermissionAndStart() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        launchScreenCaptureIntent()
    }

    private fun launchScreenCaptureIntent() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }
}
