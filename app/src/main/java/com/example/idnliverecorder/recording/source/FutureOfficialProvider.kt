package com.example.idnliverecorder.recording.source

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.Surface
import com.example.idnliverecorder.recording.RecordingConfig

/**
 * FutureOfficialProvider (Phase 5 Architecture)
 *
 * Dedicated stub for a future authorized direct SDK integration provided by IDN.
 * Currently locked until an official partner contract and SDK are supplied.
 */
class FutureOfficialProvider : LiveSourceProvider {
    override val type: LiveSourceType = LiveSourceType.OfficialLiveStream
    override val capabilities: LiveSourceCapabilities = LiveSourceCapabilities.forOfficialStream()
    override val isVideoSourceActive: Boolean = false
    override val isAudioSourceActive: Boolean = false

    override fun prepare(context: Context, streamerName: String?, streamerId: String?): LiveSourceResult {
        return LiveSourceResult.UnsupportedOfficialSource(
            code = "UNSUPPORTED_OFFICIAL_SOURCE",
            message = "WAITING FOR AUTHORIZATION — Fitur direct stream belum diaktifkan karena masih menunggu persetujuan akses dan ketentuan penggunaan."
        )
    }

    override fun startVideoSource(
        mediaProjection: MediaProjection?,
        config: RecordingConfig,
        targetSurface: Surface,
        mainHandler: Handler
    ): Boolean = false

    override fun stop() {}
    override fun release() {}
}
