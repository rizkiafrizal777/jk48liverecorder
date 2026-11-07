package com.example.idnliverecorder.ui.dashboard

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idnliverecorder.data.AppInfo
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.data.DetectionStatus
import com.example.idnliverecorder.data.RecordedVideoItem
import com.example.idnliverecorder.monitoring.LiveDetectionManager
import com.example.idnliverecorder.monitoring.LiveDetectionRepository
import com.example.idnliverecorder.monitoring.LiveDetectionSettings
import com.example.idnliverecorder.monitoring.LiveDetectionSettingsData
import com.example.idnliverecorder.recording.EncoderValidator
import com.example.idnliverecorder.recording.RecordingConfig
import com.example.idnliverecorder.recording.RecordingManager
import com.example.idnliverecorder.recording.RecordingState
import com.example.idnliverecorder.recording.ScreenRecordingService
import com.example.idnliverecorder.recording.source.LiveSourceCapabilities
import com.example.idnliverecorder.recording.source.LiveSourceType
import com.example.idnliverecorder.storage.MediaStoreRecorderStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val recordingManager = RecordingManager(application)
    private val detectionManager = LiveDetectionManager.getInstance(application)
    private val detectionSettings = LiveDetectionSettings.getInstance(application)
    private val detectionRepository = LiveDetectionRepository.getInstance(application)

    // Phase 1 Recording States
    val recordingState: StateFlow<RecordingState> = ScreenRecordingService.recordingState

    private val _recentRecordings = MutableStateFlow<List<RecordedVideoItem>>(emptyList())
    val recentRecordings: StateFlow<List<RecordedVideoItem>> = _recentRecordings.asStateFlow()

    private val _optimalConfig = MutableStateFlow<RecordingConfig?>(null)
    val optimalConfig: StateFlow<RecordingConfig?> = _optimalConfig.asStateFlow()

    // Phase 2 Detection States
    val settingsFlow: StateFlow<LiveDetectionSettingsData> = detectionSettings.settingsFlow
    val isNotificationAccessGranted: StateFlow<Boolean> = detectionManager.isPermissionGranted
    val recentDetections: StateFlow<List<DetectedLiveItem>> = detectionRepository.recentDetections
    val activeConfirmation: StateFlow<DetectedLiveItem?> = detectionRepository.activeConfirmation
    val todayDetectionCount: StateFlow<Int> = detectionRepository.todayDetectionCount

    val isLiveDetectionActive: StateFlow<Boolean> = combine(
        settingsFlow,
        isNotificationAccessGranted
    ) { settings, permissionGranted ->
        settings.isEnabled && !settings.targetPackageName.isNullOrBlank() && permissionGranted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    init {
        loadEngineConfig()
        refreshRecordings()
        refreshPermissions()

        // Auto-refresh recordings when recording completes
        viewModelScope.launch {
            recordingState.collect { state ->
                if (state is RecordingState.Saved) {
                    refreshRecordings()
                    // If we had an active recording session for a live streamer, update detection status
                    activeConfirmation.value?.let { active ->
                        detectionRepository.updateDetectionStatus(active.id, DetectionStatus.Recording)
                    }
                }
            }
        }
    }

    private fun loadEngineConfig() {
        viewModelScope.launch(Dispatchers.Default) {
            val result = EncoderValidator.resolveOptimalConfig(getApplication())
            _optimalConfig.value = result.config
        }
    }

    // Phase 4 Source Selection States
    val availableSources: List<LiveSourceCapabilities> = listOf(
        LiveSourceCapabilities.forFullScreen(),
        LiveSourceCapabilities.forOfficialStream()
    )
    private val _selectedSource = MutableStateFlow<LiveSourceType>(LiveSourceType.FullScreenRecording)
    val selectedSource: StateFlow<LiveSourceType> = _selectedSource.asStateFlow()

    fun selectSource(type: LiveSourceType) {
        _selectedSource.value = type
    }

    // Recording Controls (Phase 1, 3 & 4)
    fun startRecording(resultCode: Int, resultData: Intent) {
        val streamerName = activeConfirmation.value?.streamerName
        activeConfirmation.value?.let { active ->
            detectionRepository.updateDetectionStatus(active.id, DetectionStatus.Recording)
        }
        recordingManager.startRecording(resultCode, resultData, streamerName)
    }

    fun stopRecording() {
        recordingManager.stopRecording()
    }

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = MediaStoreRecorderStorage.fetchRecentRecordings(getApplication())
            _recentRecordings.value = list
        }
    }

    fun dismissStatus() {
        if (recordingState.value is RecordingState.Saved || recordingState.value is RecordingState.Error) {
            ScreenRecordingService.updateState(RecordingState.Idle)
        }
    }

    // Detection & Settings Controls (Phase 2)
    fun refreshPermissions() {
        detectionManager.refreshPermissionStatus()
    }

    fun setDetectionEnabled(enabled: Boolean) {
        detectionSettings.setEnabled(enabled)
    }

    fun setTargetApplication(packageName: String?, appName: String?) {
        detectionSettings.setTargetApplication(packageName, appName)
    }

    fun setKeywords(keywords: List<String>) {
        detectionSettings.setKeywords(keywords)
    }

    fun setCooldownMinutes(minutes: Int) {
        detectionSettings.setCooldownMinutes(minutes)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        detectionSettings.setNotificationsEnabled(enabled)
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return

        _isLoadingApps.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = pm.queryIntentActivities(launcherIntent, 0)
            val myPackage = getApplication<Application>().packageName

            val list = activities.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == myPackage) return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                val isCandidate = pkg.contains("idn", ignoreCase = true) ||
                        label.contains("idn", ignoreCase = true) ||
                        label.contains("live", ignoreCase = true)
                AppInfo(
                    packageName = pkg,
                    appName = label,
                    isIdnCandidate = isCandidate
                )
            }.distinctBy { it.packageName }
                .sortedWith(
                    compareByDescending<AppInfo> { it.isIdnCandidate }
                        .thenBy { it.appName.lowercase() }
                )

            _installedApps.value = list
            _isLoadingApps.value = false
        }
    }

    fun handleViewDetectionIntent(detectionId: String) {
        val match = recentDetections.value.firstOrNull { it.id == detectionId }
        if (match != null) {
            detectionRepository.setActiveConfirmation(match)
        }
        detectionManager.dismissDetectionNotification()
    }

    fun confirmLiveRecording(detectionItem: DetectedLiveItem, onLaunchConsent: () -> Unit) {
        detectionManager.dismissDetectionNotification()
        onLaunchConsent()
    }

    fun dismissLiveConfirmation(detectionItem: DetectedLiveItem) {
        detectionRepository.updateDetectionStatus(detectionItem.id, DetectionStatus.Ignored)
        detectionManager.dismissDetectionNotification()
    }

    fun simulateTestDetection(streamerName: String = "Freya") {
        detectionManager.simulateTestDetection(streamerName)
    }
}
