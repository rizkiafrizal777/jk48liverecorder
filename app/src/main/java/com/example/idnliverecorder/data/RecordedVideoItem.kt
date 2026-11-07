package com.example.idnliverecorder.data

import android.net.Uri

data class RecordedVideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val durationMs: Long,
    val dateAddedMs: Long,
    val resolution: String
)

