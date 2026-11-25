package com.example.idnliverecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.idnliverecorder.core.Constants
import com.example.idnliverecorder.library.ui.RecordingLibraryViewModel
import com.example.idnliverecorder.recording.direct.DirectRecordingService
import com.example.idnliverecorder.ui.dashboard.DashboardScreen
import com.example.idnliverecorder.ui.dashboard.DashboardViewModel
import com.example.idnliverecorder.ui.jkt48.Jkt48LiveViewModel
import com.example.idnliverecorder.ui.theme.DarkBackground
import com.example.idnliverecorder.ui.theme.IDNLiveRecorderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val jkt48ViewModel: Jkt48LiveViewModel by viewModels()
    private val libraryViewModel: RecordingLibraryViewModel by viewModels()
    private val selectedTabState = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            IDNLiveRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {

                    // Phase 2 StateFlows
                    val isPermissionGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
                    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
                    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
                    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()

                    // Phase 4A & 5 JKT48 Live Monitor & Direct Recording states
                    val jkt48UiState by jkt48ViewModel.uiState.collectAsStateWithLifecycle()
                    val jkt48ApiKey by jkt48ViewModel.apiKey.collectAsStateWithLifecycle()
                    val recordingSessions by jkt48ViewModel.recordingSessions.collectAsStateWithLifecycle()

                    DashboardScreen(
                        isPermissionGranted = isPermissionGranted,
                        settings = settings,
                        installedApps = installedApps,
                        isLoadingApps = isLoadingApps,
                        jkt48UiState = jkt48UiState,
                        jkt48ApiKey = jkt48ApiKey,
                        onRefreshJkt48 = { jkt48ViewModel.refresh() },
                        onToggleFavoriteJkt48 = { name -> jkt48ViewModel.toggleFavorite(name) },
                        onSaveJkt48ApiKey = { key -> jkt48ViewModel.saveApiKey(key) },
                        recordingSessions = recordingSessions,
                        onStartDirectRecording = { member -> jkt48ViewModel.startDirectRecording(member) },
                        onStopDirectRecording = { sessionId -> jkt48ViewModel.stopDirectRecording(sessionId) },
                        libraryViewModel = libraryViewModel,
                        selectedTab = selectedTabState.intValue,
                        onTabSelected = { selectedTabState.intValue = it },
                        onRefreshPermission = { viewModel.refreshPermissions() },
                        onOpenAppPicker = { viewModel.loadInstalledApps() },
                        onAppSelected = { app ->
                            viewModel.setTargetApplication(app.packageName, app.appName)
                        },
                        onSetDetectionEnabled = { enabled -> viewModel.setDetectionEnabled(enabled) },
                        onSetKeywords = { kws -> viewModel.setKeywords(kws) },
                        onSetCooldown = { mins -> viewModel.setCooldownMinutes(mins) },
                        onSetNotificationsEnabled = { enabled -> viewModel.setNotificationsEnabled(enabled) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onStart() {
        super.onStart()
        notifyRecordingServiceVisibility(visible = true)
    }

    override fun onStop() {
        notifyRecordingServiceVisibility(visible = false)
        super.onStop()
    }

    private fun notifyRecordingServiceVisibility(visible: Boolean) {
        if (DirectRecordingService.recordingSessions.value.isEmpty()) return
        val action = if (visible) {
            DirectRecordingService.ACTION_APP_ENTERED_FOREGROUND
        } else {
            DirectRecordingService.ACTION_APP_ENTERED_BACKGROUND
        }
        startService(Intent(this, DirectRecordingService::class.java).setAction(action))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Constants.ACTION_VIEW_DETECTION) {
            selectedTabState.intValue = 0
        }
        if (intent.getStringExtra("navigate_to") == "library") {
            selectedTabState.intValue = 2
            libraryViewModel.refreshLibraryWithRetry()
        }
    }

}
