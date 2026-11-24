package com.example.idnliverecorder.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.idnliverecorder.core.formatDate
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.library.RecordingLibraryItem
import com.example.idnliverecorder.library.metadata.AdvancedRecordingMetadata
import com.example.idnliverecorder.ui.theme.AccentAmber
import com.example.idnliverecorder.ui.theme.AccentCyan
import com.example.idnliverecorder.ui.theme.AccentGreen
import com.example.idnliverecorder.ui.theme.CardBorder
import com.example.idnliverecorder.ui.theme.PrimaryRed
import com.example.idnliverecorder.ui.theme.SurfaceDark
import com.example.idnliverecorder.ui.theme.SurfaceVariantDark
import com.example.idnliverecorder.ui.theme.TextPrimary
import com.example.idnliverecorder.ui.theme.TextSecondary
import com.example.idnliverecorder.ui.theme.TextTertiary

/**
 * Advanced Technical Detail Dialog for a recorded video in the library (Phase 6 & 7).
 */
@Composable
fun RecordingDetailDialog(
    item: RecordingLibraryItem,
    advancedMetadata: AdvancedRecordingMetadata? = null,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceDark)
                .border(1.5.dp, PrimaryRed, RoundedCornerShape(22.dp))
                .padding(22.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = PrimaryRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Detail Rekaman",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Metadata & Parameter Teknis",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Member & Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "MEMBER / SUMBER",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.memberName,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.displayName,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Visual Metrik Durasi & Ukuran (Phase 7)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "VISUALISASI RESOURCE",
                    color = AccentAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Ukuran visual (skala 1 GB)
                val sizeFraction = (item.sizeBytes.toFloat() / (1024f * 1024f * 1024f)).coerceIn(0f, 1f)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ukuran File", color = TextSecondary, fontSize = 11.sp)
                    Text(item.sizeBytes.formatFileSize(), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { sizeFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AccentCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Durasi visual (skala 2 jam)
                val durFraction = (item.durationMs.toFloat() / (2 * 3600 * 1000f)).coerceIn(0f, 1f)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Durasi Live", color = TextSecondary, fontSize = 11.sp)
                    Text(item.durationMs.formatDuration(), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { durFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = PrimaryRed,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Technical Specs Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "SPESIFIKASI TEKNIS & CODEC",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                MetaItemRow(label = "Tanggal Rekaman", value = advancedMetadata?.recordingDateFormatted ?: item.dateAddedMs.formatDate())
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "Format Kontainer", value = "MP4 (MPEG-4 Part 14)")
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "MIME Type", value = advancedMetadata?.mimeType ?: "video/mp4")
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "Resolusi", value = advancedMetadata?.resolution ?: item.resolution.ifBlank { "Unknown" })
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "Frame Rate", value = advancedMetadata?.frameRate ?: "Unknown")
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "Bitrate Rata-Rata", value = advancedMetadata?.bitrateFormatted ?: "Unknown")
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "Video Codec", value = advancedMetadata?.videoCodec ?: item.videoCodec.ifBlank { "H.264 / AVC" }, valueColor = AccentGreen)
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(label = "Audio Codec", value = advancedMetadata?.audioCodec ?: item.audioCodec.ifBlank { "AAC-LC" }, valueColor = AccentCyan)
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(
                    label = "Kompatibilitas",
                    value = advancedMetadata?.playbackCompatibility ?: if (item.isPlayable) "Optimal (Hardware)" else "Perlu Pemulihan",
                    valueColor = if (item.isPlayable) AccentGreen else AccentAmber
                )
                Spacer(modifier = Modifier.height(8.dp))
                MetaItemRow(
                    label = "Status Integritas",
                    value = if (item.isPlayable) "Valid (Siap Putar)" else (item.integrityMessage ?: "Perlu Pemulihan"),
                    valueColor = if (item.isPlayable) AccentGreen else AccentAmber
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Storage URI Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariantDark)
                    .padding(12.dp)
            ) {
                Text(
                    text = "CONTENT URI (MEDIASTORE)",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.uri.toString(),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(text = "Tutup", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MetaItemRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
