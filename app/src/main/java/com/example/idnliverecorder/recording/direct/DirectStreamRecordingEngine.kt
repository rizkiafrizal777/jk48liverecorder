package com.example.idnliverecorder.recording.direct

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.idnliverecorder.storage.MediaStoreRecorderStorage
import com.example.idnliverecorder.storage.RecordingFileValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DirectStreamRecordingEngine (Phase 5)
 *
 * Dedicated live stream recording engine capturing HLS live streams directly
 * without MediaProjection, UI capture, or microphone input.
 *
 * SAFETY & POLICY:
 * - Only starts if [isRecordingAuthorized] is verified.
 * - Does not access undocumented endpoints or bypass DRM.
 * - Audio comes purely from the stream track (AAC-LC); no microphone fallback.
 * - MediaStore output: Movies/IDNLiveRecorder/JKT48_{MemberName}_{DateTime}.mp4
 */
class DirectStreamRecordingEngine(
    private val context: Context,
    private val streamUrl: String,
    private val memberName: String,
    private val isRecordingAuthorized: Boolean,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val onStatusChanged: (DirectStreamStatus, String) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "DirectStreamEngine"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer for demuxing
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private val isRecordingRunning = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)

    private var outputSession: MediaStoreRecorderStorage.OutputSession? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    private var lastVideoPtsUs = 0L
    private var lastAudioPtsUs = 0L
    private var videoPtsOffsetUs = 0L
    private var audioPtsOffsetUs = 0L

    private val processedSegments = Collections.synchronizedSet(mutableSetOf<String>())
    private val cacheDir = File(context.cacheDir, "hls_direct_recording_${System.currentTimeMillis()}")

    val isRunning: Boolean get() = isRecordingRunning.get()

    /**
     * Starts direct stream recording for the configured stream URL.
     */
    fun start(): Boolean {
        if (!isRecordingAuthorized) {
            val msg = "WAITING FOR AUTHORIZATION — Perekaman direct stream terkunci tanpa otorisasi resmi."
            Log.w(TAG, msg)
            onStatusChanged(DirectStreamStatus.AUTHORIZATION_REQUIRED, msg)
            return false
        }

        if (streamUrl.isBlank()) {
            val msg = "URL stream tidak valid atau kosong."
            Log.e(TAG, msg)
            onStatusChanged(DirectStreamStatus.SOURCE_UNAVAILABLE, msg)
            return false
        }

        if (isRecordingRunning.getAndSet(true)) {
            Log.w(TAG, "Recording already running.")
            return true
        }

        isStopping.set(false)
        cacheDir.mkdirs()

        onStatusChanged(DirectStreamStatus.RECORDING, "Memulai perekaman direct stream untuk $memberName...")

        recordingJob = engineScope.launch {
            runRecordingPipeline()
        }

        return true
    }

    /**
     * Main recording coroutine: polls HLS playlist, downloads segments, and muxes them.
     */
    private suspend fun runRecordingPipeline() = withContext(Dispatchers.IO) {
        try {
            // 1. Create MediaStore pending file
            outputSession = MediaStoreRecorderStorage.createPendingDirectVideo(context, memberName)
            val pfd = outputSession!!.parcelFileDescriptor

            // Initialize MediaMuxer writing to the scoped storage descriptor
            mediaMuxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var consecutiveFailures = 0
            val baseUrl = streamUrl.substringBeforeLast("/") + "/"

            while (isActive && isRecordingRunning.get() && !isStopping.get()) {
                val playlistText = fetchPlaylistText(streamUrl)
                if (playlistText == null) {
                    consecutiveFailures++
                    Log.w(TAG, "Failed to fetch HLS playlist ($consecutiveFailures/5)")
                    if (consecutiveFailures >= 5) {
                        Log.e(TAG, "Exceeded maximum playlist fetch retries. Finalizing recording.")
                        break
                    }
                    delay(2000)
                    continue
                }

                consecutiveFailures = 0
                val segments = parsePlaylistSegments(playlistText, baseUrl)
                val targetDurationMs = parseTargetDuration(playlistText) * 1000L

                // Process new segments
                var newSegmentsCount = 0
                for (segmentUrl in segments) {
                    if (!isActive || !isRecordingRunning.get() || isStopping.get()) break
                    if (processedSegments.contains(segmentUrl)) continue

                    val segmentFile = downloadSegment(segmentUrl)
                    if (segmentFile != null) {
                        muxSegment(segmentFile)
                        segmentFile.delete() // Cleanup temporary file immediately
                        processedSegments.add(segmentUrl)
                        newSegmentsCount++
                    }
                }

                // Check for stream completion tag
                if (playlistText.contains("#EXT-X-ENDLIST")) {
                    Log.d(TAG, "Stream ended: #EXT-X-ENDLIST reached.")
                    break
                }

                // Delay according to target duration (or fallback 2.5s)
                val sleepTime = if (targetDurationMs > 0) targetDurationMs / 2 else 2500L
                delay(sleepTime)
            }

            finalizeRecordingInternal()

        } catch (e: Exception) {
            Log.e(TAG, "Error in DirectStreamRecordingEngine: ${e.message}", e)
            handleRecordingError(e.message ?: "Unknown recording error")
        }
    }

    /**
     * Downloads an HLS segment to an isolated temporary cache file.
     */
    private fun downloadSegment(segmentUrl: String): File? {
        val fileName = "seg_${System.currentTimeMillis()}_${segmentUrl.substringAfterLast("/").substringBefore("?")}"
        val targetFile = File(cacheDir, fileName)

        for (attempt in 1..3) {
            try {
                val request = Request.Builder()
                    .url(segmentUrl)
                    .header("User-Agent", "IDNLiveRecorder/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return targetFile
                }
            } catch (e: Exception) {
                Log.w(TAG, "Segment download attempt $attempt failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * Demuxes an MPEG-TS or fMP4 segment using MediaExtractor and appends tracks to MediaMuxer.
     */
    private fun muxSegment(segmentFile: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(segmentFile.absolutePath)
            val numTracks = extractor.trackCount

            var localVideoTrack = -1
            var localAudioTrack = -1

            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && localVideoTrack == -1) {
                    localVideoTrack = i
                    if (videoTrackIndex == -1 && mediaMuxer != null && !isMuxerStarted) {
                        videoTrackIndex = mediaMuxer!!.addTrack(format)
                        Log.d(TAG, "Added direct video track: $mime (index: $videoTrackIndex)")
                    }
                } else if (mime.startsWith("audio/") && localAudioTrack == -1) {
                    localAudioTrack = i
                    if (audioTrackIndex == -1 && mediaMuxer != null && !isMuxerStarted) {
                        audioTrackIndex = mediaMuxer!!.addTrack(format)
                        Log.d(TAG, "Added direct audio track: $mime (index: $audioTrackIndex)")
                    }
                }
            }

            // Start muxer once header tracks are detected
            if (!isMuxerStarted && mediaMuxer != null && videoTrackIndex != -1) {
                mediaMuxer!!.start()
                isMuxerStarted = true
                Log.d(TAG, "MediaMuxer started successfully.")
            }

            if (!isMuxerStarted) {
                Log.w(TAG, "Muxer not started yet, skipping segment data write.")
                return
            }

            // Process video samples
            if (localVideoTrack != -1 && videoTrackIndex != -1) {
                extractor.selectTrack(localVideoTrack)
                writeSamplesForTrack(extractor, localVideoTrack, videoTrackIndex, isVideo = true)
                extractor.unselectTrack(localVideoTrack)
            }

            // Process audio samples
            if (localAudioTrack != -1 && audioTrackIndex != -1) {
                extractor.selectTrack(localAudioTrack)
                writeSamplesForTrack(extractor, localAudioTrack, audioTrackIndex, isVideo = false)
                extractor.unselectTrack(localAudioTrack)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error demuxing segment ${segmentFile.name}: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Reads samples from MediaExtractor, applies monotonic timestamp adjustment, and writes to MediaMuxer.
     */
    private fun writeSamplesForTrack(
        extractor: MediaExtractor,
        extractorTrack: Int,
        muxerTrack: Int,
        isVideo: Boolean
    ) {
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()

        var firstSamplePtsInSegment = -1L

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTimeUs = extractor.sampleTime
            val sampleFlags = extractor.sampleFlags

            if (firstSamplePtsInSegment == -1L) {
                firstSamplePtsInSegment = sampleTimeUs
                if (isVideo) {
                    if (sampleTimeUs <= lastVideoPtsUs) {
                        videoPtsOffsetUs = lastVideoPtsUs + 33_333L - sampleTimeUs
                    }
                } else {
                    if (sampleTimeUs <= lastAudioPtsUs) {
                        audioPtsOffsetUs = lastAudioPtsUs + 23_220L - sampleTimeUs
                    }
                }
            }

            val adjustedPts = if (isVideo) {
                sampleTimeUs + videoPtsOffsetUs
            } else {
                sampleTimeUs + audioPtsOffsetUs
            }

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = adjustedPts
            bufferInfo.flags = sampleFlags

            if (mediaMuxer != null && isMuxerStarted) {
                synchronized(mediaMuxer!!) {
                    mediaMuxer!!.writeSampleData(muxerTrack, buffer, bufferInfo)
                }
            }

            if (isVideo) {
                lastVideoPtsUs = adjustedPts
            } else {
                lastAudioPtsUs = adjustedPts
            }

            extractor.advance()
        }
    }

    /**
     * Fetches raw playlist text via OkHttp.
     */
    private fun fetchPlaylistText(url: String): String? {
        // Special case for mock test streams during unit testing
        if (url.startsWith("test://") || url.startsWith("mock://")) {
            return """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:2
                #EXTINF:2.0,
                test_segment_1.ts
                #EXT-X-ENDLIST
            """.trimIndent()
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IDNLiveRecorder/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HLS playlist: ${e.message}")
            null
        }
    }

    private fun parsePlaylistSegments(playlistText: String, baseUrl: String): List<String> {
        val list = mutableListOf<String>()
        val lines = playlistText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val fullUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    trimmed
                } else {
                    baseUrl + trimmed
                }
                list.add(fullUrl)
            }
        }
        return list
    }

    private fun parseTargetDuration(playlistText: String): Long {
        val match = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(playlistText)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 2L
    }

    /**
     * Stops the direct stream recording gracefully.
     */
    fun stop() {
        if (!isRecordingRunning.get()) return
        Log.d(TAG, "DirectStreamRecordingEngine.stop() requested.")
        isStopping.set(true)
        onStatusChanged(DirectStreamStatus.FINALIZING, "Memfinalisasi file MP4...")
        recordingJob?.cancel()
        engineScope.launch {
            finalizeRecordingInternal()
        }
    }

    /**
     * Finalizes MediaMuxer and validates the generated file with MediaStore.
     */
    private fun finalizeRecordingInternal() {
        if (!isRecordingRunning.getAndSet(false)) return

        Log.d(TAG, "Finalizing direct stream recording...")
        try {
            if (mediaMuxer != null && isMuxerStarted) {
                mediaMuxer!!.stop()
                mediaMuxer!!.release()
                mediaMuxer = null
                isMuxerStarted = false
            }

            outputSession?.let { session ->
                session.parcelFileDescriptor.close()
                val result = MediaStoreRecorderStorage.finalizeVideo(context, session.uri, session.displayName)
                if (result.isSuccess) {
                    val info = result.getOrNull()
                    Log.d(TAG, "Direct stream recorded successfully: ${session.displayName} (${info?.sizeBytes} bytes)")
                    onStatusChanged(DirectStreamStatus.COMPLETED, "Rekaman tersimpan: ${session.displayName}")
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Gagal memvalidasi file video."
                    Log.e(TAG, "Recording finalization error: $errMsg")
                    onStatusChanged(DirectStreamStatus.ERROR, errMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording: ${e.message}", e)
            onStatusChanged(DirectStreamStatus.ERROR, "Gagal finalisasi: ${e.message}")
        } finally {
            cleanupCache()
        }
    }

    private fun handleRecordingError(error: String) {
        isRecordingRunning.set(false)
        onStatusChanged(DirectStreamStatus.ERROR, error)
        try {
            if (mediaMuxer != null && isMuxerStarted) {
                mediaMuxer!!.stop()
                mediaMuxer!!.release()
            }
        } catch (_: Exception) {}
        mediaMuxer = null
        outputSession?.let { MediaStoreRecorderStorage.discardVideo(context, it.uri) }
        cleanupCache()
    }

    private fun cleanupCache() {
        try {
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning cache dir: ${e.message}")
        }
    }

    fun release() {
        stop()
        cleanupCache()
    }
}
