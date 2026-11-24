package com.example.idnliverecorder.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.database.ExportTask
import com.example.idnliverecorder.library.RecordingLibraryItem
import com.example.idnliverecorder.ui.theme.AccentAmber
import com.example.idnliverecorder.ui.theme.AccentCyan
import com.example.idnliverecorder.ui.theme.AccentGreen
import com.example.idnliverecorder.ui.theme.CardBorder
import com.example.idnliverecorder.ui.theme.DangerBg
import com.example.idnliverecorder.ui.theme.PrimaryRed
import com.example.idnliverecorder.ui.theme.SurfaceDark
import com.example.idnliverecorder.ui.theme.SurfaceVariantDark
import com.example.idnliverecorder.ui.theme.TextPrimary
import com.example.idnliverecorder.ui.theme.TextSecondary
import com.example.idnliverecorder.ui.theme.TextTertiary

/**
 * Advanced Recording Analytics & Summary Section for Dashboard (Phase 7).
 */
@Composable
fun DashboardAnalyticsSection(
    recordings: List<RecordingLibraryItem>,
    favoriteCount: Int,
    recentExports: List<ExportTask>,
    onOpenLibrary: () -> Unit,
    isLoading: Boolean = false,
    loadError: String? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalRecordings = recordings.size
    val totalDurationMs = recordings.sumOf { it.durationMs }
    val totalSizeBytes = recordings.sumOf { it.sizeBytes }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Ringkasan Rekaman",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Ringkasan koleksi rekaman lokal",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenLibrary() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Buka Library",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading && recordings.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariantDark)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = AccentCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Memuat ringkasan rekaman…", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else if (loadError != null && recordings.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariantDark)
                    .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ringkasan gagal dimuat: $loadError",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) {
                    Text("Coba lagi", color = AccentCyan, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else if (loadError != null) {
            Text(
                text = "Pembaruan gagal; menampilkan data rekaman terakhir.",
                color = AccentAmber,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (recordings.isEmpty() && !isLoading && loadError == null) {
            Text(
                text = "Belum ada rekaman. File yang berhasil disimpan akan muncul di sini.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // 4 Statistical Metric Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 1: Total Rekaman
            MetricTile(
                title = "Total Rekaman",
                value = "$totalRecordings",
                unit = "video",
                icon = Icons.Default.Movie,
                iconTint = PrimaryRed,
                modifier = Modifier.weight(1f)
            )
            // Tile 2: Total Durasi
            MetricTile(
                title = "Total Durasi",
                value = totalDurationMs.formatDuration(),
                unit = "",
                icon = Icons.Default.Timer,
                iconTint = AccentCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 3: Total Storage
            MetricTile(
                title = "Penyimpanan",
                value = totalSizeBytes.formatFileSize(),
                unit = "",
                icon = Icons.Default.VideoLibrary,
                iconTint = AccentGreen,
                modifier = Modifier.weight(1f)
            )
            // Tile 4: Favorit
            MetricTile(
                title = "Video Favorit",
                value = "$favoriteCount",
                unit = "tersimpan",
                icon = Icons.Default.Star,
                iconTint = AccentAmber,
                modifier = Modifier.weight(1f)
            )
        }

        // Visual Distribution by Top Members (if recordings exist)
        if (recordings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "DISTRIBUSI MEMBER TERBANYAK",
                color = AccentCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            val memberCounts = recordings.groupBy { it.memberName }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(3)

            memberCounts.forEach { (memberName, count) ->
                val ratio = if (totalRecordings > 0) count.toFloat() / totalRecordings else 0f
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = memberName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "$count rekaman (${(ratio * 100).toInt()}%)", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = PrimaryRed,
                        trackColor = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // Recent Exports List (Phase 7)
        if (recentExports.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AKTIVITAS EKSPOR TERAKHIR",
                    color = AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${recentExports.size} riwayat",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recentExports.take(3).forEach { task ->
                    RecentExportItemRow(task = task)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = TextTertiary, fontSize = 10.sp)
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = unit, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 1.dp))
            }
        }
    }
}

@Composable
private fun RecentExportItemRow(task: ExportTask) {
    val statusColor = when (task.status) {
        ExportTask.STATUS_COMPLETED -> AccentGreen
        ExportTask.STATUS_RUNNING -> AccentCyan
        ExportTask.STATUS_QUEUED -> AccentAmber
        ExportTask.STATUS_CANCELLED -> TextTertiary
        else -> DangerBg
    }

    val statusIcon = when (task.status) {
        ExportTask.STATUS_COMPLETED -> Icons.Default.CheckCircle
        ExportTask.STATUS_RUNNING, ExportTask.STATUS_QUEUED -> Icons.Default.HourglassEmpty
        else -> Icons.Default.Error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = task.displayName,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(task.totalBytes),
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = task.status,
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

