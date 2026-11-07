package com.example.idnliverecorder.recording.source

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.example.idnliverecorder.recording.RecordingConfig

/**
 * ScreenCaptureProvider
 *
 * Implements full-screen device mirroring via Android MediaProjection.
 *
 * IMPORTANT:
 * - This mode records the entire visible Android screen.
 * - Any other applications, notifications, keyboards, or system dialogs
 *   displayed on the screen will be recorded into the video file.
 * - This mode is NOT true live-only stream recording.
 */
class ScreenCaptureProvider : LiveSourceProvider {

    companion object {
        private const val TAG = "ScreenCaptureProvider"
    }

    override val type: LiveSourceType = LiveSourceType.FullScreenRecording

    override val capabilities: LiveSourceCapabilities = LiveSourceCapabilities.forFullScreen()

    private var virtualDisplay: VirtualDisplay? = null
    private var _isVideoActive: Boolean = false
    private var _isAudioActive: Boolean = false

    override val isVideoSourceActive: Boolean get() = _isVideoActive
    override val isAudioSourceActive: Boolean get() = _isAudioActive

    override fun prepare(
        context: Context,
        streamerName: String?,
        streamerId: String?
    ): LiveSourceResult {
        Log.d(TAG, "Preparing ScreenCaptureProvider (Full Screen Recording). Streamer: $streamerName")
        if (!capabilities.isAvailableOnDevice) {
            return LiveSourceResult.DeviceNotSupported("Requires Android 10 (API 29) or higher.")
        }
        return LiveSourceResult.Ready(capabilities)
    }

    override fun startVideoSource(
        mediaProjection: MediaProjection?,
        config: RecordingConfig,
        targetSurface: Surface,
        mainHandler: Handler
    ): Boolean {
        if (mediaProjection == null) {
            Log.e(TAG, "Cannot start video: MediaProjection token is null.")
            return false
        }

        return try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "IDNLiveRecorderFullScreenVD",
                config.width,
                config.height,
                config.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                targetSurface,
                null,
                mainHandler
            )
            _isVideoActive = true
            Log.d(TAG, "ScreenCaptureProvider active: ${config.width}x${config.height} @ ${config.densityDpi}dpi")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay: ${e.message}", e)
            _isVideoActive = false
            false
        }
    }

    fun markAudioActive(active: Boolean) {
        _isAudioActive = active
    }

    override fun stop() {
        Log.d(TAG, "ScreenCaptureProvider stopping...")
        _isVideoActive = false
        _isAudioActive = false
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VirtualDisplay: ${e.message}")
        }
        virtualDisplay = null
    }

    override fun release() {
        stop()
        Log.d(TAG, "ScreenCaptureProvider released.")
    }
}

