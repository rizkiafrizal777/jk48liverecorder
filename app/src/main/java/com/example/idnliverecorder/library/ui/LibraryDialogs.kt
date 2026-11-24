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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.idnliverecorder.core.formatFileSize
import com.example.idnliverecorder.library.RecordingLibraryItem
import com.example.idnliverecorder.library.RecordingStorageAnalyzer
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

@Composable
fun StorageInfoCard(
    storageInfo: RecordingStorageAnalyzer.StorageUsageInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SdCard,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Penyimpanan Rekaman",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${storageInfo.recordingsCount} Video",
                color = AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { (storageInfo.folderUsagePercentage / 100f).coerceIn(0f, 1f) },
            color = PrimaryRed,
            trackColor = Color(0xFF1B202E),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Total File Rekaman", color = TextTertiary, fontSize = 11.sp)
                Text(
                    text = storageInfo.totalRecordingsBytes.formatFileSize(),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Ruang Bebas Perangkat", color = TextTertiary, fontSize = 11.sp)
                Text(
                    text = storageInfo.availableDeviceBytes.formatFileSize(),
                    color = AccentGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RenameDialog(
    item: RecordingLibraryItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember {
        mutableStateOf(item.displayName.removeSuffix(".mp4"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Ubah Nama Rekaman", color = TextPrimary, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "Masukkan nama baru untuk file rekaman. Ekstensi .mp4 akan ditambahkan otomatis.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nama File") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Text("Simpan", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun DeleteConfirmationDialog(
    item: RecordingLibraryItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = PrimaryRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Hapus Rekaman?", color = TextPrimary, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "Apakah Anda yakin ingin menghapus file berikut secara permanen?",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ukuran: ${item.sizeBytes.formatFileSize()}",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Text("Hapus", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}

@Composable
fun RecoveryDialog(
    item: RecordingLibraryItem,
    onRecover: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = AccentAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Pemeriksaan Integritas", color = TextPrimary, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = item.integrityMessage ?: "File mengalami anomali status (belum selesai atau rusak).",
                    color = AccentAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "File: ${item.displayName}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ukuran: ${item.sizeBytes.formatFileSize()}",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aplikasi dapat mencoba memulihkan status file jika data audio/video masih utuh di disk.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRecover,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text("Coba Pulihkan", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Hapus File", color = PrimaryRed)
                }
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = TextSecondary)
                }
            }
        },
        containerColor = SurfaceDark
    )
}

