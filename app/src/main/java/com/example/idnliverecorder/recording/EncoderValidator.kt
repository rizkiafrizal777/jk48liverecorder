package com.example.idnliverecorder.recording

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.idnliverecorder.core.Constants

object EncoderValidator {
    private const val TAG = "EncoderValidator"
    private const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC // "video/avc"

    data class ResolutionCheckResult(
        val config: RecordingConfig,
        val isSupported: Boolean,
        val fallbackApplied: Boolean,
        val reason: String? = null
    )

    fun resolveOptimalConfig(context: Context): ResolutionCheckResult {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (screenWidth, screenHeight, densityDpi) = getDisplayDimensions(windowManager)

        val isPortrait = screenHeight >= screenWidth
        val targetLonger = 1920
        val targetShorter = 1080

        val targetWidth: Int
        val targetHeight: Int

        if (isPortrait) {
            val aspectRatio = screenHeight.toFloat() / screenWidth.toFloat()
            targetWidth = 1080
            targetHeight = alignDimension((1080 * aspectRatio).toInt())
        } else {
            val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
            targetWidth = alignDimension((1080 * aspectRatio).toInt())
            targetHeight = 1080
        }

        val codecInfo = findAvcEncoder()
        if (codecInfo == null) {
            return ResolutionCheckResult(
                config = RecordingConfig(
                    width = alignDimension(targetWidth),
                    height = alignDimension(targetHeight),
                    densityDpi = densityDpi,
                    frameRate = Constants.DEFAULT_FRAME_RATE,
                    bitRate = Constants.DEFAULT_VIDEO_BITRATE,
                    isFallbackResolution = false
                ),
                isSupported = false,
                fallbackApplied = false,
                reason = "No H.264 (AVC) encoder found on device."
            )
        }

        val capabilities = codecInfo.getCapabilitiesForType(MIME_AVC)
        val videoCapabilities = capabilities.videoCapabilities

        if (videoCapabilities != null && videoCapabilities.isSizeSupported(targetWidth, targetHeight)) {
            val isRateSupported = videoCapabilities.areSizeAndRateSupported(
                targetWidth,
                targetHeight,
                Constants.DEFAULT_FRAME_RATE.toDouble()
            )
            Log.d(TAG, "Target 1080p ($targetWidth x $targetHeight) supported. Rate supported: $isRateSupported")

            return ResolutionCheckResult(
                config = RecordingConfig(
                    width = targetWidth,
                    height = targetHeight,
                    densityDpi = densityDpi,
                    frameRate = Constants.DEFAULT_FRAME_RATE,
                    bitRate = Constants.DEFAULT_VIDEO_BITRATE,
                    isFallbackResolution = false
                ),
                isSupported = true,
                fallbackApplied = false
            )
        }

        // Fallback to 720p
        Log.w(TAG, "Target 1080p ($targetWidth x $targetHeight) not supported by encoder ${codecInfo.name}. Attempting 720p fallback.")
        val fallbackWidth: Int
        val fallbackHeight: Int

        if (isPortrait) {
            val aspectRatio = screenHeight.toFloat() / screenWidth.toFloat()
            fallbackWidth = 720
            fallbackHeight = alignDimension((720 * aspectRatio).toInt())
        } else {
            val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
            fallbackWidth = alignDimension((720 * aspectRatio).toInt())
            fallbackHeight = 720
        }

        val isFallbackSupported = videoCapabilities?.isSizeSupported(fallbackWidth, fallbackHeight) ?: false

        return ResolutionCheckResult(
            config = RecordingConfig(
                width = fallbackWidth,
                height = fallbackHeight,
                densityDpi = densityDpi,
                frameRate = Constants.DEFAULT_FRAME_RATE,
                bitRate = 4_000_000,
                isFallbackResolution = true
            ),
            isSupported = isFallbackSupported,
            fallbackApplied = true,
            reason = if (isFallbackSupported) {
                "Target 1080p not supported by encoder. Downgraded to 720p."
            } else {
                "Neither 1080p nor 720p is supported by the video encoder."
            }
        )
    }

    private fun findAvcEncoder(): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(MIME_AVC, ignoreCase = true)) {
                    return info
                }
            }
        }
        return null
    }

    private fun getDisplayDimensions(windowManager: WindowManager): Triple<Int, Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private fun alignDimension(value: Int): Int {
        // Encoders require dimensions divisible by at least 16 (or 2)
        val remainder = value % 16
        return if (remainder == 0) value else value - remainder
    }
}

