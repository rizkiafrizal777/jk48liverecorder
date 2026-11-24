package com.example.idnliverecorder.ui.dashboard

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idnliverecorder.data.AppInfo
import com.example.idnliverecorder.data.DetectedLiveItem
import com.example.idnliverecorder.data.RecordedVideoItem
import com.example.idnliverecorder.monitoring.LiveDetectionManager
import com.example.idnliverecorder.monitoring.LiveDetectionRepository
import com.example.idnliverecorder.monitoring.LiveDetectionSettings
import com.example.idnliverecorder.monitoring.LiveDetectionSettingsData
import com.example.idnliverecorder.recording.EncoderValidator
import com.example.idnliverecorder.recording.RecordingConfig
import com.example.idnliverecorder.recording.RecordingEventBridge
import com.example.idnliverecorder.recording.direct.DirectRecordingService
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
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

    private val detectionManager = LiveDetectionManager.getInstance(application)
    private val detectionSettings = LiveDetectionSettings.getInstance(application)
    private val detectionRepository = LiveDetectionRepository.getInstance(application)

    private val _recentRecordings = MutableStateFlow<List<RecordedVideoItem>>(emptyList())
    val recentRecordings: StateFlow<List<RecordedVideoItem>> = _recentRecordings.asStateFlow()

    private val _optimalConfig = MutableStateFlow<RecordingConfig?>(null)
    val optimalConfig: StateFlow<RecordingConfig?> = _optimalConfig.asStateFlow()

    // Phase 2 Detection States
    val settingsFlow: StateFlow<LiveDetectionSettingsData> = detectionSettings.settingsFlow
    val isNotificationAccessGranted: StateFlow<Boolean> = detectionManager.isPermissionGranted
    val recentDetections: StateFlow<List<DetectedLiveItem>> = detectionRepository.recentDetections
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

        // Auto-refresh recordings when direct stream recording completes (Phase 7-8 Reliability)
        viewModelScope.launch {
            RecordingEventBridge.recordingCompletedEvents.collect { event ->
                refreshRecordings()
            }
        }

        viewModelScope.launch {
            DirectRecordingService.engineStatus.collect { status ->
                if (status == DirectStreamStatus.COMPLETED) {
                    refreshRecordings()
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

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = MediaStoreRecorderStorage.fetchRecentRecordings(getApplication())
            _recentRecordings.value = list
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

}
