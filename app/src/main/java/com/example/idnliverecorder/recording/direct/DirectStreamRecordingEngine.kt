package com.example.idnliverecorder.recording.direct

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.idnliverecorder.recording.RecordingEventBridge
import com.example.idnliverecorder.storage.MediaStoreRecorderStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DirectStreamRecordingEngine for reliable direct live stream recording.
 *
 * Dedicated live stream recording engine capturing HLS live streams directly
 * without MediaProjection, UI capture, or microphone input.
 *
 * SAFETY & POLICY:
 * - Does not access undocumented endpoints or bypass DRM.
 * - Audio comes purely from the stream track (AAC-LC); no microphone fallback.
 * - MediaStore output: Movies/IDNLiveRecorder/JKT48_{MemberName}_{DateTime}.mp4
 * - Sanitizes all logged URLs to prevent leaking credentials or tokens.
 */
class DirectStreamRecordingEngine(
    private val context: Context,
    private val streamUrl: String,
    private val memberName: String,
    private val httpClient: OkHttpClient = sharedHttpClient,
    private val onStatusChanged: (DirectStreamStatus, String) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "DirectStreamEngine"
        private const val TAG_PLAYLIST = "IDN_HLS_PLAYLIST"
        private const val TAG_DOWNLOAD = "IDN_HLS_DOWNLOAD"
        private const val TAG_DETECTOR = "IDN_HLS_DETECTOR"
        private const val TAG_EXTRACTOR = "IDN_HLS_EXTRACTOR"
        private const val TAG_MUXER = "IDN_HLS_MUXER"
        private const val TAG_FINALIZE = "IDN_HLS_FINALIZE"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer for demuxing
        private const val MAX_TRACKED_SEGMENTS = 4096
        private const val MAX_SEGMENT_BYTES = 128L * 1024L * 1024L
        private val sharedHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        fun getFirstBytesHex(file: File, count: Int = 16): String {
            return try {
                if (!file.exists() || file.length() == 0L) "EMPTY_OR_NOT_FOUND"
                else file.inputStream().use { input ->
                    val bytes = input.readNBytes(count)
                    bytes.joinToString(" ") { "%02X".format(it) }
                }
            } catch (e: Exception) {
                "ERR_READING: ${e.message}"
            }
        }

        /**
         * Resolves file extension safely from the URL path only, ignoring query params and fragments.
         * Enforces strict character and length constraints to prevent path traversal or malformed extensions.
         */
        internal fun resolveExtension(segmentUrl: String, isInit: Boolean = false): String {
            val pathOnly = segmentUrl.substringBefore("?").substringBefore("#")
            val rawExt = pathOnly.substringAfterLast(".", "")
            return if (rawExt.length in 2..4 && rawExt.all { it.isLetterOrDigit() }) {
                ".$rawExt"
            } else {
                if (isInit) ".mp4" else ".tmp"
            }
        }

        /**
         * Generates short, deterministic, unique filenames to prevent ENAMETOOLONG errors.
         * Never includes full URLs, query strings, or sensitive tokens.
         */
        internal fun generateLocalFileName(index: Int, segmentUrl: String, isInit: Boolean = false): String {
            val ext = resolveExtension(segmentUrl, isInit)
            val prefix = if (isInit) "init" else "segment"
            return String.format(Locale.US, "%s_%06d%s", prefix, index, ext)
        }
    }

    data class FinalizationSummary(
        val isSuccess: Boolean,
        val uri: Uri? = null,
        val displayName: String = "",
        val sizeBytes: Long = 0L,
        val durationMs: Long = 0L,
        val warningMessage: String? = null,
        val errorMessage: String? = null
    )

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private val isRecordingRunning = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)
    private val isFinalized = AtomicBoolean(false)
    private var finalizationSummary: FinalizationSummary? = null
    private var pipelineWarning: String? = null

    private var outputSession: MediaStoreRecorderStorage.OutputSession? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    private var totalVideoSamplesWritten = 0L
    private var totalAudioSamplesWritten = 0L

    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L
    private var baseSessionPtsUs = -1L
    private var hasSeenFirstVideoKeyframe = false
    private var segmentVideoTimelineOffsetUs = 0L
    private var segmentAudioTimelineOffsetUs = 0L
    private var lastRawVideoPtsInSegment = -1L
    private var lastRawAudioPtsInSegment = -1L

    private val processedSegments = Collections.synchronizedSet(LinkedHashSet<String>())
    private data class PendingSegment(
        val downloaded: HlsSegmentDetector.DownloadedSegment,
        val initSegmentFile: File?
    )

    private val pendingSegmentsBeforeStart = Collections.synchronizedList(mutableListOf<PendingSegment>())
    private val cacheDir = File(context.cacheDir, "hls_direct_recording_${UUID.randomUUID()}")
    private var cachedInitSegmentFile: File? = null
    private var cachedAudioInitSegmentFile: File? = null
    private var audioPlaylistUrl: String? = null
    private var downloadCounter = 0

    val isRunning: Boolean get() = isRecordingRunning.get()
    val totalSamples: Long get() = totalVideoSamplesWritten + totalAudioSamplesWritten

    /**
     * Starts direct stream recording for the configured stream URL.
     */
    fun start(): Boolean {
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
        isFinalized.set(false)
        finalizationSummary = null
        pipelineWarning = null
        totalVideoSamplesWritten = 0L
        totalAudioSamplesWritten = 0L
        baseSessionPtsUs = -1L
        hasSeenFirstVideoKeyframe = false
        lastVideoPtsUs = -1L
        lastAudioPtsUs = -1L
        segmentVideoTimelineOffsetUs = 0L
        segmentAudioTimelineOffsetUs = 0L
        lastRawVideoPtsInSegment = -1L
        lastRawAudioPtsInSegment = -1L
        videoTrackIndex = -1
        audioTrackIndex = -1
        isMuxerStarted = false
        processedSegments.clear()
        pendingSegmentsBeforeStart.clear()
        cachedInitSegmentFile = null
        cachedAudioInitSegmentFile = null
        audioPlaylistUrl = null
        downloadCounter = 0
        cacheDir.mkdirs()

        val sanitized = HlsPlaylistResolver.sanitizeUrl(streamUrl)
        Log.d(TAG, "Starting DirectStreamRecordingEngine for $memberName (Stream: $sanitized)")
        onStatusChanged(DirectStreamStatus.RECORDING, "Memulai perekaman direct stream untuk $memberName...")

        recordingJob = engineScope.launch {
            runRecordingPipeline()
        }

        return true
    }

    /**
     * Main recording coroutine: detects playlist type, resolves variants, downloads segments, and muxes them.
     */
    private suspend fun runRecordingPipeline() = withContext(Dispatchers.IO) {
        val sessionId = "session_${System.currentTimeMillis()}"
        Log.d(TAG, "[$sessionId] Recording pipeline active. Initializing storage...")

        try {
            // 1. Create MediaStore pending file
            outputSession = MediaStoreRecorderStorage.createPendingDirectVideo(context, memberName)
            val pfd = outputSession!!.parcelFileDescriptor

            // Initialize MediaMuxer writing to the scoped storage descriptor
            mediaMuxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.d(TAG_MUXER, "[$sessionId] MediaMuxer initialized writing to descriptor for displayName=${outputSession!!.displayName}")

            // 2. Fetch and resolve stream URL (Master vs Media playlist)
            val initialPlaylistText = fetchPlaylistText(streamUrl)
            if (initialPlaylistText == null) {
                val errMsg = "Gagal mengunduh playlist stream awal."
                Log.e(TAG_PLAYLIST, "[$sessionId] $errMsg URL: ${HlsPlaylistResolver.sanitizeUrl(streamUrl)}")
                onStatusChanged(DirectStreamStatus.SOURCE_UNAVAILABLE, errMsg)
                finalizeRecordingInternal()
                return@withContext
            }

            var activeMediaPlaylistUrl = streamUrl
            if (HlsPlaylistResolver.isMasterPlaylist(initialPlaylistText)) {
                Log.d(TAG_PLAYLIST, "[$sessionId] Selected playlist type: MASTER_PLAYLIST (URL: ${HlsPlaylistResolver.sanitizeUrl(streamUrl)})")
                val variants = HlsPlaylistResolver.parseMasterPlaylist(initialPlaylistText, streamUrl)
                val optimalVariant = HlsPlaylistResolver.selectOptimalVariant(variants)

                if (optimalVariant != null) {
                    activeMediaPlaylistUrl = optimalVariant.url
                    Log.d(
                        TAG_PLAYLIST,
                        "[$sessionId] Selected variant: resolution=${optimalVariant.resolution ?: "auto"}, bandwidth=${optimalVariant.bandwidth} bps, url=${HlsPlaylistResolver.sanitizeUrl(activeMediaPlaylistUrl)}"
                    )

                    // Stage 3 (Fix B): Resolve audio rendition URL from EXT-X-MEDIA TYPE=AUDIO
                    val resolvedAudioUrl = HlsPlaylistResolver.resolveAudioRenditionUrl(
                        selectedVariant = optimalVariant,
                        masterPlaylistText = initialPlaylistText,
                        masterPlaylistUrl = streamUrl
                    )
                    if (resolvedAudioUrl != null) {
                        audioPlaylistUrl = resolvedAudioUrl
                        Log.d(TAG_PLAYLIST, "[$sessionId] Separate audio rendition exists: true (URL: ${HlsPlaylistResolver.sanitizeUrl(resolvedAudioUrl)})")
                    } else {
                        Log.d(TAG_PLAYLIST, "[$sessionId] Separate audio rendition exists: false. Assuming muxed audio+video in single stream.")
                    }
                } else {
                    Log.w(TAG_PLAYLIST, "[$sessionId] No compatible variant found in master playlist. Fallback to base URL.")
                }
            } else {
                Log.d(TAG_PLAYLIST, "[$sessionId] Selected playlist type: DIRECT_MEDIA_PLAYLIST (URL: ${HlsPlaylistResolver.sanitizeUrl(streamUrl)})")
            }

            var consecutiveFailures = 0
            var consecutiveSegmentFailures = 0
            var liveVideoWindowInitialized = false
            var liveAudioWindowInitialized = false

            // 3. Polling and segment processing loop
            while (isActive && isRecordingRunning.get() && !isStopping.get()) {
                val playlistText = fetchPlaylistText(activeMediaPlaylistUrl)
                if (playlistText == null) {
                    consecutiveFailures++
                    Log.w(
                        TAG_PLAYLIST,
                        "[$sessionId] Failed to fetch media playlist ($consecutiveFailures/5) from: " +
                            HlsPlaylistResolver.sanitizeUrl(activeMediaPlaylistUrl)
                    )
                    if (consecutiveFailures >= 5) {
                        Log.e(TAG_PLAYLIST, "[$sessionId] Exceeded maximum playlist fetch retries. Finalizing recording.")
                        pipelineWarning = "Koneksi siaran terputus berulang kali; file mungkin berisi rekaman sebagian."
                        break
                    }
                    delay(2000)
                    continue
                }

                consecutiveFailures = 0
                val resolvedPlaylist = HlsPlaylistResolver.parseMediaPlaylist(playlistText, activeMediaPlaylistUrl)
                val targetDurationMs = resolvedPlaylist.targetDurationSec * 1000L
                Log.d(
                    TAG_PLAYLIST,
                    "[$sessionId] Media playlist: targetDurationSec=${resolvedPlaylist.targetDurationSec}, " +
                        "EXT-X-MAP exists=${resolvedPlaylist.initSegmentUrl != null}, " +
                        "initSegmentUrl=${resolvedPlaylist.initSegmentUrl?.let { HlsPlaylistResolver.sanitizeUrl(it) } ?: "none"}, " +
                        "video/muxed segments discovered=${resolvedPlaylist.segments.size}, isEndList=${resolvedPlaylist.isEndList}"
                )

                // A live playlist is a sliding window and usually contains several
                // seconds of media from before the user pressed Record. Start at its
                // newest complete segment instead of writing that historical window.
                if (!liveVideoWindowInitialized && !resolvedPlaylist.isEndList && resolvedPlaylist.segments.isNotEmpty()) {
                    val staleSegments = resolvedPlaylist.segments.dropLast(1)
                    staleSegments.forEach { markSegmentProcessed(it.url) }
                    liveVideoWindowInitialized = true
                    Log.i(
                        TAG_PLAYLIST,
                        "[$sessionId] Initialized live capture window: skipped ${staleSegments.size} " +
                            "pre-recording segment(s), starting from newest available segment."
                    )
                }

                // Handle fMP4 initialization segment (#EXT-X-MAP)
                if (resolvedPlaylist.initSegmentUrl != null && (cachedInitSegmentFile == null || !cachedInitSegmentFile!!.exists())) {
                    Log.d(TAG_DETECTOR, "[$sessionId] Downloading fMP4 init segment: ${HlsPlaylistResolver.sanitizeUrl(resolvedPlaylist.initSegmentUrl)}")
                    val downloadedInit = downloadSegment(resolvedPlaylist.initSegmentUrl, isInit = true)
                    cachedInitSegmentFile = downloadedInit?.file
                    if (cachedInitSegmentFile != null) {
                        val initSize = cachedInitSegmentFile!!.length()
                        val initHex = getFirstBytesHex(cachedInitSegmentFile!!, 16)
                        val isValidBox = HlsSegmentDetector.isValidInitSegment(cachedInitSegmentFile!!)
                        Log.d(
                            TAG_DETECTOR,
                            "[$sessionId] Init segment downloaded: size=${initSize}B, " +
                                "isValidInitBox=$isValidBox, first16Hex=[$initHex], path=${cachedInitSegmentFile!!.absolutePath}"
                        )
                    } else {
                        Log.e(TAG_DETECTOR, "[$sessionId] Init segment download failed or file empty!")
                    }
                }

                // Process new media segments
                var newSegmentsCount = 0
                for (segment in resolvedPlaylist.segments) {
                    if (!isActive || !isRecordingRunning.get() || isStopping.get()) break
                    if (processedSegments.contains(segment.url)) continue

                    val downloaded = downloadSegment(segment.url, isInit = false)
                    if (downloaded != null) {
                        Log.d(
                            TAG,
                            "[$sessionId] Downloaded segment: format=${downloaded.format}, " +
                                "size=${downloaded.sizeBytes}B, type=${downloaded.contentType ?: "unknown"}"
                        )
                        val shouldDelete = muxSegment(downloaded)
                        if (shouldDelete) {
                            downloaded.file.delete()
                        }
                        markSegmentProcessed(segment.url)
                        newSegmentsCount++
                        consecutiveSegmentFailures = 0
                    } else {
                        consecutiveSegmentFailures++
                        Log.w(
                            TAG_DOWNLOAD,
                            "[$sessionId] Segment download failed for URL=${HlsPlaylistResolver.sanitizeUrl(segment.url)}. Skipping sample extraction for this segment."
                        )
                    }
                }

                // Stage 3 (Fix B): Check separate audio rendition segments if present
                val auUrl = audioPlaylistUrl
                if (auUrl != null && isActive && isRecordingRunning.get() && !isStopping.get()) {
                    val audioPlaylistText = fetchPlaylistText(auUrl)
                    if (audioPlaylistText != null) {
                        val audioPlaylist = HlsPlaylistResolver.parseMediaPlaylist(audioPlaylistText, auUrl)

                        if (!liveAudioWindowInitialized && !audioPlaylist.isEndList && audioPlaylist.segments.isNotEmpty()) {
                            val staleAudioSegments = audioPlaylist.segments.dropLast(1)
                            staleAudioSegments.forEach { markSegmentProcessed(it.url) }
                            liveAudioWindowInitialized = true
                            Log.i(
                                TAG_PLAYLIST,
                                "[$sessionId] Initialized live audio window: skipped ${staleAudioSegments.size} " +
                                    "pre-recording segment(s), starting from newest available segment."
                            )
                        }

                        // Audio init segment if present
                        if (audioPlaylist.initSegmentUrl != null &&
                            (cachedAudioInitSegmentFile == null || !cachedAudioInitSegmentFile!!.exists())
                        ) {
                            val audioInit = downloadSegment(audioPlaylist.initSegmentUrl, isInit = true)
                            cachedAudioInitSegmentFile = audioInit?.file
                        }

                        for (segment in audioPlaylist.segments) {
                            if (!isActive || !isRecordingRunning.get() || isStopping.get()) break
                            if (processedSegments.contains(segment.url)) continue

                            val audioDownloaded = downloadSegment(segment.url, isInit = false)
                            if (audioDownloaded != null) {
                                val shouldDelete = muxSegment(audioDownloaded, cachedAudioInitSegmentFile)
                                if (shouldDelete) {
                                    audioDownloaded.file.delete()
                                }
                                markSegmentProcessed(segment.url)
                                newSegmentsCount++
                                consecutiveSegmentFailures = 0
                            } else {
                                consecutiveSegmentFailures++
                                Log.w(
                                    TAG_DOWNLOAD,
                                    "[$sessionId] Audio segment download failed for URL=${HlsPlaylistResolver.sanitizeUrl(segment.url)}. Skipping sample extraction for this audio segment."
                                )
                            }
                        }
                    }
                }

                if (newSegmentsCount > 0) {
                    Log.d(
                        TAG,
                        "[$sessionId] Processed $newSegmentsCount segment(s). Total samples: " +
                            "video=$totalVideoSamplesWritten, audio=$totalAudioSamplesWritten"
                    )
                }

                if (consecutiveSegmentFailures >= 10) {
                    pipelineWarning = "Segmen siaran gagal diunduh berulang kali; file mungkin berisi rekaman sebagian."
                    Log.e(TAG_DOWNLOAD, "[$sessionId] Too many consecutive HLS segment download failures.")
                    break
                }

                // Check for stream completion tag (#EXT-X-ENDLIST)
                if (resolvedPlaylist.isEndList) {
                    Log.d(TAG, "[$sessionId] Stream ended: #EXT-X-ENDLIST reached.")
                    break
                }

                // Polling interval based on HLS target duration
                val sleepTime = if (targetDurationMs > 0) (targetDurationMs / 2).coerceIn(1000L, 4000L) else 2000L
                delay(sleepTime)
            }

            Log.d(TAG, "[$sessionId] Pipeline loop finished. Invoking finalization...")
            finalizeRecordingInternal()

        } catch (e: CancellationException) {
            Log.d(TAG, "[$sessionId] Pipeline coroutine cancelled by user/service. Finalizing gracefully...")
            finalizeRecordingInternal()
        } catch (e: Exception) {
            Log.e(TAG, "[$sessionId] Fatal error in DirectStreamRecordingEngine: ${e.message}", e)
            handleRecordingError(e.message ?: "Unknown recording error")
        }
    }

    /**
     * Downloads an HLS segment to an isolated temporary cache file and inspects metadata.
     * Uses short, deterministic, unique filenames to prevent ENAMETOOLONG errors.
     */
    private suspend fun downloadSegment(segmentUrl: String, isInit: Boolean = false): HlsSegmentDetector.DownloadedSegment? {
        val segmentIndex = ++downloadCounter
        val fileName = generateLocalFileName(segmentIndex, segmentUrl, isInit)
        val targetFile = File(cacheDir, fileName)

        for (attempt in 1..3) {
            try {
                // Ensure no partial or corrupted file from previous failed attempt remains
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val request = Request.Builder()
                    .url(segmentUrl)
                    .header("User-Agent", "IDNLiveRecorder/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val statusCode = response.code
                    val contentType = response.header("Content-Type")

                    if (!response.isSuccessful) {
                        Log.e(
                            TAG_DOWNLOAD,
                            "Segment download #$segmentIndex failed: HTTP $statusCode, URL=${HlsPlaylistResolver.sanitizeUrl(segmentUrl)}"
                        )
                        if (statusCode == 408 || statusCode == 429 || statusCode in 500..599) {
                            throw IOException("Transient HLS segment response: HTTP $statusCode")
                        }
                        return null
                    }

                    val body = response.body ?: return null
                    if (body.contentLength() > MAX_SEGMENT_BYTES) {
                        Log.e(TAG_DOWNLOAD, "Segment #$segmentIndex exceeded the configured size limit.")
                        return null
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytesRead = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytesRead += count
                                if (bytesRead > MAX_SEGMENT_BYTES) {
                                    throw IOException("HLS segment exceeded the configured size limit.")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }

                    val size = targetFile.length()
                    if (size <= 0L) {
                        Log.w(
                            TAG_DOWNLOAD,
                            "Segment #$segmentIndex downloaded 0 bytes from ${HlsPlaylistResolver.sanitizeUrl(segmentUrl)}"
                        )
                        targetFile.delete()
                        return null
                    }

                    val format = HlsSegmentDetector.detectFormat(targetFile, contentType)
                    val first16Hex = getFirstBytesHex(targetFile, 16)

                    Log.d(
                        TAG_DOWNLOAD,
                        "Segment #$segmentIndex (${if (isInit) "init" else "seg"}) downloaded: localFile=${targetFile.name}, " +
                            "URL=${HlsPlaylistResolver.sanitizeUrl(segmentUrl)}, HTTP status=$statusCode, " +
                            "Content-Type=${contentType ?: "null"}, sizeBytes=$size, first16Hex=[$first16Hex], " +
                            "detectedFormat=$format, fileExists=${targetFile.exists()}"
                    )

                    return HlsSegmentDetector.DownloadedSegment(
                        file = targetFile,
                        format = format,
                        sizeBytes = size,
                        contentType = contentType,
                        httpStatusCode = statusCode,
                        isInitSegment = isInit
                    )
                }
            } catch (e: Exception) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (e is CancellationException) throw e
                Log.e(
                    TAG_DOWNLOAD,
                    "Segment #$segmentIndex download attempt $attempt failed for ${HlsPlaylistResolver.sanitizeUrl(segmentUrl)}: ${e.message}",
                    e
                )
                if (attempt < 3) delay(250L * attempt)
            }
        }
        return null
    }

    private fun markSegmentProcessed(segmentUrl: String) {
        synchronized(processedSegments) {
            processedSegments.add(segmentUrl)
            while (processedSegments.size > MAX_TRACKED_SEGMENTS) {
                val oldest = processedSegments.iterator()
                if (!oldest.hasNext()) break
                oldest.next()
                oldest.remove()
            }
        }
    }

    /**
     * Demuxes an MPEG-TS or fMP4 segment using MediaExtractor and appends tracks to MediaMuxer.
     * Includes Stage 1 (content-based detection) and Stage 2 (robust MediaExtractor fallback).
     * Returns true if the segment was processed or discarded (caller should delete file),
     * or false if the segment was retained in pendingSegmentsBeforeStart while awaiting muxer start.
     */
    private fun muxSegment(
        downloaded: HlsSegmentDetector.DownloadedSegment,
        initSegmentFile: File? = cachedInitSegmentFile
    ): Boolean {
        val segmentFile = downloaded.file
        val isFmp4 = downloaded.format == HlsSegmentDetector.SegmentFormat.FRAGMENTED_MP4 ||
                     (initSegmentFile != null && initSegmentFile.exists() &&
                      (segmentFile.name.endsWith(".m4s") || segmentFile.name.endsWith(".mp4")))

        var fileToExtract = segmentFile
        var combinedFile: File? = null

        if (isFmp4 && initSegmentFile != null && initSegmentFile.exists()) {
            val combined = File(cacheDir, "combined_${segmentFile.nameWithoutExtension}.mp4")
            try {
                FileOutputStream(combined).use { fos ->
                    initSegmentFile.inputStream().use { it.copyTo(fos) }
                    segmentFile.inputStream().use { it.copyTo(fos) }
                }
                combinedFile = combined
                fileToExtract = combined
                Log.d(
                    TAG_DETECTOR,
                    "Combined init segment (${initSegmentFile.length()}B) with media fragment (${segmentFile.length()}B) -> " +
                        "combinedFile=${combined.name}, combinedSize=${combined.length()}B"
                )
            } catch (e: Exception) {
                Log.e(TAG_DETECTOR, "Failed to combine init segment with fragment: ${e.message}", e)
                fileToExtract = segmentFile
            }
        } else {
            Log.d(
                TAG_DETECTOR,
                "Segment processed without combination: isFmp4=$isFmp4, " +
                    "hasInit=${initSegmentFile != null && initSegmentFile.exists()}, " +
                    "file=${segmentFile.name}, size=${segmentFile.length()}B, format=${downloaded.format}"
            )
        }

        var extractor = MediaExtractor()

        try {
            Log.d(
                TAG_EXTRACTOR,
                "MediaExtractor.setDataSource on: path=${fileToExtract.absolutePath}, " +
                    "size=${fileToExtract.length()}B, first16Hex=[${getFirstBytesHex(fileToExtract, 16)}]"
            )
            try {
                extractor.setDataSource(fileToExtract.absolutePath)
                Log.d(TAG_EXTRACTOR, "MediaExtractor.setDataSource SUCCEEDED. trackCount=${extractor.trackCount}")
            } catch (e: Exception) {
                Log.e(TAG_EXTRACTOR, "MediaExtractor.setDataSource FAILED: ${e.javaClass.simpleName} - ${e.message}", e)
            }

            var numTracks = extractor.trackCount

            // Stage 2 (Fix C): Robust MediaExtractor Fallback
            // If 0 tracks found on raw segment, and we have an init segment and haven't tried combining yet:
            if (numTracks == 0 && combinedFile == null && initSegmentFile != null && initSegmentFile.exists()) {
                val magicFormat = HlsSegmentDetector.detectByMagicBytes(segmentFile)
                if (magicFormat == HlsSegmentDetector.SegmentFormat.FRAGMENTED_MP4 || magicFormat == HlsSegmentDetector.SegmentFormat.UNKNOWN) {
                    Log.d(TAG_DETECTOR, "0 tracks on initial extraction. Attempting fallback combination with init segment...")
                    try {
                        extractor.release()
                    } catch (_: Exception) {}

                    val fallbackCombined = File(cacheDir, "fallback_${segmentFile.nameWithoutExtension}.mp4")
                    FileOutputStream(fallbackCombined).use { fos ->
                        initSegmentFile.inputStream().use { it.copyTo(fos) }
                        segmentFile.inputStream().use { it.copyTo(fos) }
                    }
                    combinedFile = fallbackCombined
                    fileToExtract = fallbackCombined

                    extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(fallbackCombined.absolutePath)
                        numTracks = extractor.trackCount
                        Log.d(TAG_EXTRACTOR, "Fallback setDataSource SUCCEEDED. trackCount=$numTracks")
                    } catch (e: Exception) {
                        Log.e(TAG_EXTRACTOR, "Fallback setDataSource FAILED: ${e.javaClass.simpleName} - ${e.message}", e)
                    }
                }
            }

            if (numTracks == 0) {
                Log.w(
                    TAG_EXTRACTOR,
                    "MediaExtractor found 0 tracks in file ${fileToExtract.name} " +
                        "(size=${fileToExtract.length()}B, format=${downloaded.format}, contentType=${downloaded.contentType}, first16Hex=[${getFirstBytesHex(fileToExtract, 16)}])"
                )
                return true
            }

            var localVideoTrack = -1
            var localAudioTrack = -1

            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
                Log.d(
                    TAG_EXTRACTOR,
                    "Track #$i discovered: mime=$mime, durationUs=$durationUs, formatDetails=$format"
                )

                if (mime.startsWith("video/") && localVideoTrack == -1) {
                    localVideoTrack = i
                    if (videoTrackIndex == -1 && mediaMuxer != null && !isMuxerStarted) {
                        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) &&
                            !ensureAvcCodecSpecificData(extractor, i, format)
                        ) {
                            Log.w(
                                TAG_MUXER,
                                "Skipping segment until AVC SPS/PPS are available; " +
                                    "csd-0=${format.containsKey("csd-0")}, csd-1=${format.containsKey("csd-1")}"
                            )
                            return true
                        }
                        videoTrackIndex = mediaMuxer!!.addTrack(format)
                        Log.d(
                            TAG_MUXER,
                            "MediaMuxer.addTrack for VIDEO: mime=$mime, csd-0=${format.containsKey("csd-0")}, " +
                                "csd-1=${format.containsKey("csd-1")} -> assigned muxerTrackIndex=$videoTrackIndex"
                        )
                    }
                } else if (mime.startsWith("audio/") && localAudioTrack == -1) {
                    localAudioTrack = i
                    if (audioTrackIndex == -1 && mediaMuxer != null && !isMuxerStarted) {
                        audioTrackIndex = mediaMuxer!!.addTrack(format)
                        Log.d(TAG_MUXER, "MediaMuxer.addTrack for AUDIO: mime=$mime -> assigned muxerTrackIndex=$audioTrackIndex")
                    }
                }
            }

            // Start muxer once tracks are detected
            if (!isMuxerStarted && mediaMuxer != null) {
                val shouldStart = if (audioPlaylistUrl != null && audioTrackIndex == -1 && downloadCounter < 10) {
                    // Separate audio rendition declared: wait until both tracks are registered unless timeout fallback
                    videoTrackIndex != -1 && audioTrackIndex != -1
                } else {
                    // Muxed stream or audio not declared separately
                    videoTrackIndex != -1 || audioTrackIndex != -1
                }

                if (shouldStart) {
                    if (audioPlaylistUrl != null && audioTrackIndex == -1) {
                        Log.w(
                            TAG_MUXER,
                            "SINGLE-TRACK FALLBACK: Separate audio rendition declared but audio track not discovered after $downloadCounter attempts. Continuing with VIDEO ONLY."
                        )
                    }
                    try {
                        mediaMuxer!!.start()
                        isMuxerStarted = true
                        Log.d(
                            TAG_MUXER,
                            "MediaMuxer.start() SUCCEEDED. videoTrackIndex=$videoTrackIndex, audioTrackIndex=$audioTrackIndex"
                        )
                        drainPendingSegmentsBeforeStart()
                    } catch (e: Exception) {
                        Log.e(TAG_MUXER, "MediaMuxer.start() FAILED: ${e.message}", e)
                    }
                }
            }

            if (!isMuxerStarted) {
                Log.w(TAG_MUXER, "Muxer not started yet (waiting for all tracks). Buffering segment ${downloaded.file.name}.")
                pendingSegmentsBeforeStart.add(PendingSegment(downloaded, initSegmentFile))
                return false
            }

            // Select both tracks so MediaExtractor yields samples in timestamp order.
            // Writing a whole video segment followed by its audio segment makes timestamps
            // move backwards across tracks and can leave MediaMuxer in an invalid state.
            if (localVideoTrack != -1 && videoTrackIndex != -1) extractor.selectTrack(localVideoTrack)
            if (localAudioTrack != -1 && audioTrackIndex != -1) extractor.selectTrack(localAudioTrack)
            if ((localVideoTrack != -1 && videoTrackIndex != -1) ||
                (localAudioTrack != -1 && audioTrackIndex != -1)
            ) {
                writeSamplesInterleaved(extractor, localVideoTrack, localAudioTrack)
            }
            if (localVideoTrack != -1 && videoTrackIndex != -1) extractor.unselectTrack(localVideoTrack)
            if (localAudioTrack != -1 && audioTrackIndex != -1) extractor.unselectTrack(localAudioTrack)

        } catch (e: Exception) {
            Log.e(TAG_EXTRACTOR, "Error demuxing segment ${segmentFile.name}: ${e.message}", e)
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
            combinedFile?.delete()
        }
        return true
    }

    private fun drainPendingSegmentsBeforeStart() {
        val pending = synchronized(pendingSegmentsBeforeStart) {
            val copy = ArrayList(pendingSegmentsBeforeStart)
            pendingSegmentsBeforeStart.clear()
            copy
        }
        for (seg in pending) {
            Log.d(TAG_MUXER, "Draining pre-start buffered segment: ${seg.downloaded.file.name}")
            try {
                muxSegment(seg.downloaded, seg.initSegmentFile)
            } finally {
                seg.downloaded.file.delete()
            }
        }
    }

    /**
     * Android's MP4 writer requires both AVC SPS and PPS codec-specific data.
     * Some MPEG-TS extractors expose only csd-0 in MediaFormat, even though the
     * parameter sets are carried in the first keyframe access unit. Recover the
     * missing sets before addTrack(), then rewind the extractor for normal muxing.
     */
    private fun ensureAvcCodecSpecificData(
        extractor: MediaExtractor,
        videoTrack: Int,
        format: MediaFormat
    ): Boolean {
        var sps = format.getByteBuffer("csd-0")?.let { bufferBytes(it) }
        var pps = format.getByteBuffer("csd-1")?.let { bufferBytes(it) }
        if (containsNalType(sps, 7) && containsNalType(pps, 8)) return true

        var selected = false
        var samplesScanned = 0
        try {
            extractor.selectTrack(videoTrack)
            selected = true
            val sampleBuffer = ByteBuffer.allocate(BUFFER_SIZE)
            while (samplesScanned < 120 && (sps == null || pps == null)) {
                if (extractor.sampleTrackIndex < 0) break
                sampleBuffer.clear()
                val size = extractor.readSampleData(sampleBuffer, 0)
                if (size <= 0) break
                samplesScanned++
                val sample = ByteArray(size)
                sampleBuffer.position(0)
                sampleBuffer.get(sample, 0, size)
                for (nal in parseAvcNals(sample)) {
                    when (nal.firstOrNull()?.toInt()?.and(0x1f)) {
                        7 -> if (sps == null || !containsNalType(sps, 7)) sps = withAnnexBStartCode(nal)
                        8 -> if (pps == null || !containsNalType(pps, 8)) pps = withAnnexBStartCode(nal)
                    }
                }
                extractor.advance()
            }
        } catch (e: Exception) {
            Log.w(TAG_MUXER, "Could not recover AVC parameter sets from segment: ${e.message}")
        } finally {
            if (selected) {
                try { extractor.unselectTrack(videoTrack) } catch (_: Exception) {}
                try { extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC) } catch (e: Exception) {
                    Log.w(TAG_MUXER, "Could not rewind extractor after AVC config scan: ${e.message}")
                }
            }
        }

        if (!containsNalType(sps, 7) || !containsNalType(pps, 8)) {
            Log.e(TAG_MUXER, "AVC codec config incomplete after scan: SPS=${containsNalType(sps, 7)}, PPS=${containsNalType(pps, 8)}")
            return false
        }
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps!!))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps!!))
        Log.i(TAG_MUXER, "Recovered AVC codec config: SPS=${sps!!.size}B, PPS=${pps!!.size}B, samplesScanned=$samplesScanned")
        return true
    }

    private fun bufferBytes(source: ByteBuffer): ByteArray {
        val copy = source.duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun containsNalType(data: ByteArray?, expectedType: Int): Boolean =
        data != null && parseAvcNals(data).any { nal -> nal.isNotEmpty() && (nal[0].toInt() and 0x1f) == expectedType }

    private fun withAnnexBStartCode(nal: ByteArray): ByteArray =
        byteArrayOf(0, 0, 0, 1) + nal

    /** Supports Annex-B and 4-byte length-prefixed AVC sample payloads. */
    private fun parseAvcNals(data: ByteArray): List<ByteArray> {
        val nals = mutableListOf<ByteArray>()
        fun startCodeAt(index: Int): Int = when {
            index + 3 < data.size && data[index] == 0.toByte() && data[index + 1] == 0.toByte() &&
                data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte() -> 4
            index + 2 < data.size && data[index] == 0.toByte() && data[index + 1] == 0.toByte() &&
                data[index + 2] == 1.toByte() -> 3
            else -> 0
        }

        var i = 0
        while (i < data.size) {
            val prefix = startCodeAt(i)
            if (prefix != 0) {
                val nalStart = i + prefix
                var end = nalStart
                while (end < data.size && startCodeAt(end) == 0) end++
                if (end > nalStart) nals.add(data.copyOfRange(nalStart, end))
                i = end
            } else {
                i = data.size
            }
        }
        if (nals.isNotEmpty()) return nals

        i = 0
        while (i + 4 <= data.size) {
            val length = ((data[i].toInt() and 0xff) shl 24) or
                ((data[i + 1].toInt() and 0xff) shl 16) or
                ((data[i + 2].toInt() and 0xff) shl 8) or
                (data[i + 3].toInt() and 0xff)
            if (length <= 0 || length > data.size - i - 4) return emptyList()
            nals.add(data.copyOfRange(i + 4, i + 4 + length))
            i += 4 + length
        }
        return if (i == data.size) nals else emptyList()
    }

    /** Writes selected audio/video samples in presentation order and normalizes each track's timeline. */
    private fun writeSamplesInterleaved(
        extractor: MediaExtractor,
        videoExtractorTrack: Int,
        audioExtractorTrack: Int
    ) {
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()

        var firstVideoSamplePtsInSegment = -1L
        var firstAudioSamplePtsInSegment = -1L
        var videoReadCalls = 0
        var audioReadCalls = 0
        var videoSamplesWritten = 0
        var audioSamplesWritten = 0

        while (true) {
            val extractorTrack = extractor.sampleTrackIndex
            if (extractorTrack < 0) break

            val isVideo = extractorTrack == videoExtractorTrack
            val isAudio = extractorTrack == audioExtractorTrack
            if (!isVideo && !isAudio) {
                extractor.advance()
                continue
            }

            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            if (isVideo) videoReadCalls++ else audioReadCalls++

            val sampleTimeUs = extractor.sampleTime
            val sampleFlags = extractor.sampleFlags

            // Skip invalid or negative timestamps
            if (sampleTimeUs < 0L) {
                extractor.advance()
                continue
            }

            // FIX B: Initial Video Keyframe Gate — skip initial non-sync video samples
            if (isVideo && !hasSeenFirstVideoKeyframe) {
                val isSync = (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ||
                             (sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                if (!isSync) {
                    extractor.advance()
                    continue
                }
                hasSeenFirstVideoKeyframe = true
                Log.d(TAG_EXTRACTOR, "First video sync/keyframe found at rawPts=$sampleTimeUs, flags=$sampleFlags")
            }

            // FIX A: Session-Based PTS Normalization
            // Initialize shared session baseline on the very first valid sample written (shared source timeline)
            if (baseSessionPtsUs < 0L) {
                baseSessionPtsUs = sampleTimeUs
                Log.d(TAG_EXTRACTOR, "Initialized shared baseSessionPtsUs=$baseSessionPtsUs on ${if (isVideo) "video" else "audio"}")
            }

            // Handle segment timestamp resets if extractor evidence confirms reset:
            if (isVideo) {
                if (firstVideoSamplePtsInSegment == -1L) {
                    firstVideoSamplePtsInSegment = sampleTimeUs
                    if (lastVideoPtsUs >= 0L && lastRawVideoPtsInSegment >= 0L && sampleTimeUs < (lastRawVideoPtsInSegment - 500_000L)) {
                        segmentVideoTimelineOffsetUs = lastVideoPtsUs + 33_333L - (sampleTimeUs - baseSessionPtsUs)
                        Log.d(TAG_EXTRACTOR, "Video segment reset detected. New offset=$segmentVideoTimelineOffsetUs")
                    }
                }
                lastRawVideoPtsInSegment = sampleTimeUs
            } else if (isAudio) {
                if (firstAudioSamplePtsInSegment == -1L) {
                    firstAudioSamplePtsInSegment = sampleTimeUs
                    if (lastAudioPtsUs >= 0L && lastRawAudioPtsInSegment >= 0L && sampleTimeUs < (lastRawAudioPtsInSegment - 500_000L)) {
                        segmentAudioTimelineOffsetUs = lastAudioPtsUs + 23_220L - (sampleTimeUs - baseSessionPtsUs)
                        Log.d(TAG_EXTRACTOR, "Audio segment reset detected. New offset=$segmentAudioTimelineOffsetUs")
                    }
                }
                lastRawAudioPtsInSegment = sampleTimeUs
            }

            val timelineOffset = if (isVideo) segmentVideoTimelineOffsetUs else segmentAudioTimelineOffsetUs
            val normalizedPts = (sampleTimeUs - baseSessionPtsUs) + timelineOffset
            val safePts = if (normalizedPts < 0L) 0L else normalizedPts

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = safePts
            bufferInfo.flags = sampleFlags

            val muxerTrack = if (isVideo) videoTrackIndex else audioTrackIndex
            if (mediaMuxer != null && isMuxerStarted) {
                try {
                    synchronized(mediaMuxer!!) {
                        mediaMuxer!!.writeSampleData(muxerTrack, buffer, bufferInfo)
                    }
                    if (isVideo) {
                        totalVideoSamplesWritten++
                        lastVideoPtsUs = safePts
                        videoSamplesWritten++
                    } else {
                        totalAudioSamplesWritten++
                        lastAudioPtsUs = safePts
                        audioSamplesWritten++
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG_MUXER,
                        "MediaMuxer.writeSampleData FAILED: trackIndex=$muxerTrack, isVideo=$isVideo, " +
                            "size=$sampleSize, pts=$safePts, flags=$sampleFlags: ${e.message}",
                        e
                    )
                }
            }

            extractor.advance()
        }

        Log.d(TAG_EXTRACTOR, "Interleaved extraction finished: videoReads=$videoReadCalls, " +
            "videoWritten=$videoSamplesWritten, audioReads=$audioReadCalls, audioWritten=$audioSamplesWritten")
    }

    /**
     * Fetches raw playlist text via OkHttp.
     */
    private suspend fun fetchPlaylistText(url: String): String? {
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

        for (attempt in 1..3) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "IDNLiveRecorder/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return response.body?.string()
                    Log.w(TAG_PLAYLIST, "HLS playlist request failed with HTTP ${response.code}.")
                    if (response.code != 408 && response.code != 429 && response.code !in 500..599) return null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching HLS playlist (${HlsPlaylistResolver.sanitizeUrl(url)}): ${e.message}")
            }
            if (attempt < 3) delay(500L * attempt)
        }
        return null
    }

    /**
     * Stops the direct stream recording gracefully and suspends until finalization completes.
     */
    suspend fun stopAndAwait(): FinalizationSummary = withContext(NonCancellable) {
        if (!isRecordingRunning.get() && isFinalized.get()) {
            return@withContext finalizationSummary ?: FinalizationSummary(isSuccess = false, errorMessage = "Already finalized")
        }

        Log.d(TAG, "DirectStreamRecordingEngine.stopAndAwait() invoked.")
        isStopping.set(true)
        onStatusChanged(DirectStreamStatus.FINALIZING, "Memfinalisasi file MP4...")

        val job = recordingJob
        job?.cancel()
        try {
            job?.join()
        } catch (e: Exception) {
            Log.w(TAG, "Exception awaiting recordingJob completion: ${e.message}")
        }

        finalizeRecordingInternal()
    }

    /**
     * Non-suspending stop for legacy or external callers.
     */
    fun stop() {
        if (!isRecordingRunning.get() && isFinalized.get()) return
        Log.d(TAG, "DirectStreamRecordingEngine.stop() requested asynchronously.")
        isStopping.set(true)
        onStatusChanged(DirectStreamStatus.FINALIZING, "Memfinalisasi file MP4...")
        engineScope.launch {
            stopAndAwait()
        }
    }

    /**
     * Finalizes MediaMuxer and validates the generated file with MediaStore.
     * Idempotent: guaranteed to execute exactly once per recording session.
     */
    private fun finalizeRecordingInternal(): FinalizationSummary {
        if (!isFinalized.compareAndSet(false, true)) {
            Log.d(TAG_FINALIZE, "Finalization already executed; returning cached summary.")
            return finalizationSummary ?: FinalizationSummary(isSuccess = false, errorMessage = "Already finalized")
        }

        isRecordingRunning.set(false)
        val samplesCount = totalVideoSamplesWritten + totalAudioSamplesWritten
        Log.d(
            TAG_FINALIZE,
            "finalizeRecordingInternal started: totalVideoSamplesWritten=$totalVideoSamplesWritten, " +
                "totalAudioSamplesWritten=$totalAudioSamplesWritten, totalSamplesWritten=$samplesCount, muxerStarted=$isMuxerStarted"
        )

        // 1. Check if valid samples were written (must have started muxer and written valid video samples)
        val hasValidVideo = totalVideoSamplesWritten > 0L
        if (samplesCount == 0L || !isMuxerStarted || !hasValidVideo) {
            val errMsg = if (!hasValidVideo && samplesCount > 0L) {
                "Perekaman gagal menghasilkan frame video yang valid (0 video frame tertulis, keyframe tidak ditemukan)."
            } else {
                "Perekaman tidak menghasilkan sampel media yang valid (0 sampel tertulis)."
            }
            Log.e(
                TAG_FINALIZE,
                "Finalization REJECTED: video=$totalVideoSamplesWritten, audio=$totalAudioSamplesWritten, muxerStarted=$isMuxerStarted. Output file will be discarded."
            )
            try {
                if (mediaMuxer != null && isMuxerStarted) {
                    mediaMuxer!!.stop()
                }
                mediaMuxer?.release()
            } catch (e: Exception) {
                Log.w(TAG_MUXER, "Error cleaning up empty muxer: ${e.message}", e)
            }
            mediaMuxer = null
            isMuxerStarted = false

            outputSession?.let { session ->
                try { session.parcelFileDescriptor.close() } catch (_: Exception) {}
                MediaStoreRecorderStorage.discardVideo(context, session.uri)
            }
            outputSession = null
            cleanupCache()

            onStatusChanged(DirectStreamStatus.ERROR, errMsg)
            val summary = FinalizationSummary(isSuccess = false, errorMessage = errMsg)
            finalizationSummary = summary
            return summary
        }

        // 2. Safely stop and release MediaMuxer
        val muxerToFinalize = mediaMuxer
        if (muxerToFinalize != null && isMuxerStarted) {
            try {
                muxerToFinalize.stop()
            } catch (e: Exception) {
                Log.e(TAG_MUXER, "Error stopping MediaMuxer; refusing to publish the incomplete MP4: ${e.message}", e)
                try {
                    muxerToFinalize.release()
                } catch (releaseError: Exception) {
                    Log.w(TAG_MUXER, "Error releasing failed MediaMuxer: ${releaseError.message}", releaseError)
                }
                mediaMuxer = null
                isMuxerStarted = false

                outputSession?.let { session ->
                    try { session.parcelFileDescriptor.close() } catch (_: Exception) {}
                    MediaStoreRecorderStorage.discardVideo(context, session.uri)
                }
                outputSession = null
                cleanupCache()

                val errMsg = "MediaMuxer gagal menutup file MP4 dengan benar: ${e.message}"
                val summary = FinalizationSummary(isSuccess = false, errorMessage = errMsg)
                finalizationSummary = summary
                onStatusChanged(DirectStreamStatus.ERROR, errMsg)
                return summary
            }

            try {
                muxerToFinalize.release()
            } catch (e: Exception) {
                Log.w(TAG_MUXER, "Error releasing MediaMuxer: ${e.message}", e)
            }
            mediaMuxer = null
            isMuxerStarted = false
            Log.d(TAG_MUXER, "MediaMuxer stopped and released successfully.")
        }

        // 3. Finalize MediaStore entry and validate file integrity
        val session = outputSession
        if (session == null) {
            val errMsg = "Output session was null during finalization."
            Log.e(TAG_FINALIZE, errMsg)
            cleanupCache()
            onStatusChanged(DirectStreamStatus.ERROR, errMsg)
            val summary = FinalizationSummary(isSuccess = false, errorMessage = errMsg)
            finalizationSummary = summary
            return summary
        }

        try {
            try {
                session.parcelFileDescriptor.close()
            } catch (e: Exception) {
                Log.w(TAG_FINALIZE, "Error closing ParcelFileDescriptor: ${e.message}", e)
            }

            val result = MediaStoreRecorderStorage.finalizeVideo(context, session.uri, session.displayName)
            if (result.isSuccess) {
                val validation = result.getOrNull()
                val size = validation?.sizeBytes ?: 0L
                val duration = validation?.durationMs ?: 0L

                Log.d(
                    TAG_FINALIZE,
                    "MediaStore finalization SUCCEEDED: name=${session.displayName}, " +
                        "sizeBytes=$size, durationMs=$duration, uri=${session.uri}"
                )

                // Notify Event Bridge for decoupled repository and ViewModel auto-refresh
                RecordingEventBridge.notifyRecordingCompleted(
                    RecordingEventBridge.RecordingCompletedEvent(
                        uri = session.uri,
                        memberName = memberName,
                        displayName = session.displayName,
                        sizeBytes = size,
                        durationMs = duration,
                        isDirectStream = true
                    )
                )

                onStatusChanged(DirectStreamStatus.COMPLETED, "Rekaman tersimpan: ${session.displayName}")
                val summary = FinalizationSummary(
                    isSuccess = true,
                    uri = session.uri,
                    displayName = session.displayName,
                    sizeBytes = size,
                    durationMs = duration,
                    warningMessage = pipelineWarning
                )
                finalizationSummary = summary
                return summary
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Gagal memvalidasi integritas file video."
                Log.e(
                    TAG_FINALIZE,
                    "MediaStore finalization FAILED: $errMsg",
                    result.exceptionOrNull()
                )
                onStatusChanged(DirectStreamStatus.ERROR, errMsg)
                val summary = FinalizationSummary(isSuccess = false, errorMessage = errMsg)
                finalizationSummary = summary
                return summary
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during MediaStore finalization: ${e.message}", e)
            val errMsg = "Gagal finalisasi: ${e.message}"
            onStatusChanged(DirectStreamStatus.ERROR, errMsg)
            val summary = FinalizationSummary(isSuccess = false, errorMessage = errMsg)
            finalizationSummary = summary
            return summary
        } finally {
            outputSession = null
            cleanupCache()
        }
    }

    private fun handleRecordingError(error: String) {
        if (isFinalized.get()) return
        isRecordingRunning.set(false)
        onStatusChanged(DirectStreamStatus.ERROR, error)
        try {
            if (mediaMuxer != null && isMuxerStarted) {
                mediaMuxer!!.stop()
                mediaMuxer!!.release()
            }
        } catch (_: Exception) {}
        mediaMuxer = null
        isMuxerStarted = false
        outputSession?.let { session ->
            try { session.parcelFileDescriptor.close() } catch (_: Exception) {}
            MediaStoreRecorderStorage.discardVideo(context, session.uri)
        }
        outputSession = null
        cleanupCache()
    }

    private fun cleanupCache() {
        synchronized(pendingSegmentsBeforeStart) {
            pendingSegmentsBeforeStart.forEach {
                try { it.downloaded.file.delete() } catch (_: Exception) {}
            }
            pendingSegmentsBeforeStart.clear()
        }
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
