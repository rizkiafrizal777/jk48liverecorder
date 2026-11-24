package com.example.idnliverecorder.ui.jkt48

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.idnliverecorder.core.formatDuration
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.recording.direct.DirectStreamStatus
import com.example.idnliverecorder.ui.theme.AccentAmber
import com.example.idnliverecorder.ui.theme.AccentCyan
import com.example.idnliverecorder.ui.theme.AccentGreen
import com.example.idnliverecorder.ui.theme.CardBorder
import com.example.idnliverecorder.ui.theme.DangerBg
import com.example.idnliverecorder.ui.theme.DarkBackground
import com.example.idnliverecorder.ui.theme.PrimaryRed
import com.example.idnliverecorder.ui.theme.SurfaceDark
import com.example.idnliverecorder.ui.theme.SurfaceVariantDark
import com.example.idnliverecorder.ui.theme.TextPrimary
import com.example.idnliverecorder.ui.theme.TextSecondary
import com.example.idnliverecorder.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Jkt48LiveMonitorScreen(
    uiState: Jkt48LiveUiState,
    apiKey: String,
    directRecordingStatus: DirectStreamStatus = DirectStreamStatus.SOURCE_UNAVAILABLE,
    directRecordingDurationMs: Long = 0L,
    activeRecordingMember: String? = null,
    onRefresh: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onStartDirectRecording: (Jkt48LiveMember) -> Unit = {},
    onStopDirectRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var selectedMemberForDetail by remember { mutableStateOf<Jkt48LiveMember?>(null) }
    var memberToConfirmRecording by remember { mutableStateOf<Jkt48LiveMember?>(null) }

    // API Key Dialog
    if (showApiKeyDialog) {
        ApiKeyConfigDialog(
            currentKey = apiKey,
            onConfirm = { newKey ->
                onSaveApiKey(newKey)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // Explicit Recording Confirmation Dialog (Requirement 8)
    memberToConfirmRecording?.let { member ->
        DirectRecordingConfirmationDialog(
            member = member,
            onConfirmStart = {
                onStartDirectRecording(member)
                memberToConfirmRecording = null
            },
            onDismiss = { memberToConfirmRecording = null }
        )
    }

    // Livestream Detail Dialog (Requirement 3)
    selectedMemberForDetail?.let { member ->
        LivestreamDetailDialog(
            member = member,
            onDismiss = { selectedMemberForDetail = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(PrimaryRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JKT48 Live Monitor",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Phase 5 • Direct Livestream Recording",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Row {
                IconButton(onClick = { showApiKeyDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Config API Key",
                        tint = if (apiKey.isBlank()) AccentAmber else AccentCyan
                    )
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Data Source Attribution & Last Updated Header
        val lastUpdatedFormatted = when (uiState) {
            is Jkt48LiveUiState.Success -> formatTime(uiState.lastUpdatedMs)
            is Jkt48LiveUiState.Empty -> formatTime(uiState.lastCheckedMs)
            else -> "—"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141722))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sumber: JKT48Connect API",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "Pembaruan: $lastUpdatedFormatted",
                color = TextTertiary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Background Recording Banner (if service is running)
        if (directRecordingStatus == DirectStreamStatus.RECORDING && !activeRecordingMember.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryRed.copy(alpha = 0.15f))
                    .border(1.dp, PrimaryRed, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PrimaryRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Merekam Direct Stream: $activeRecordingMember",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Durasi: ${directRecordingDurationMs.formatDuration()} • Latar Belakang",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Button(
                        onClick = onStopDirectRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content Area based on UiState
        when (uiState) {
            is Jkt48LiveUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PrimaryRed, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Memeriksa live stream JKT48...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            is Jkt48LiveUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Tidak Ada Livestream Aktif",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.message,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                            ) {
                                Text("Cek Ulang", color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            is Jkt48LiveUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DangerBg)
                            .border(1.dp, PrimaryRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = PrimaryRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Gagal Memuat Data",
                            color = PrimaryRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.errorMessage,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (uiState.isApiKeyMissing) {
                                Button(
                                    onClick = { showApiKeyDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                                ) {
                                    Text("Atur API Key", color = Color.White, fontSize = 13.sp)
                                }
                            } else if (uiState.canRetry) {
                                Button(
                                    onClick = onRefresh,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                                ) {
                                    Text("Coba Lagi", color = Color.White, fontSize = 13.sp)
                                }
                            }

                        }
                    }
                }
            }

            is Jkt48LiveUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Member Sedang Live (${uiState.members.size})",
                                color = AccentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Ketuk untuk melihat detail",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (uiState.isRefreshing) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(9.dp))
                                Text("Memperbarui data live…", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    uiState.refreshError?.let { message ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DangerBg)
                                    .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Pembaruan gagal. Menampilkan data terakhir. $message",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRefresh) {
                                    Text("Coba lagi", color = PrimaryRed, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    items(uiState.members, key = { it.sessionKey }) { member ->
                        val isRecordingThisMember = (directRecordingStatus == DirectStreamStatus.RECORDING &&
                                activeRecordingMember == member.memberName)

                        LiveMemberCard(
                            member = member,
                            isRecordingActive = isRecordingThisMember,
                            recordingDurationMs = directRecordingDurationMs,
                            onCardClick = { selectedMemberForDetail = member },
                            onToggleFavorite = { onToggleFavorite(member.memberName) },
                            onRequestRecord = { memberToConfirmRecording = member },
                            onStopRecord = onStopDirectRecording
                        )
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun LiveMemberCard(
    member: Jkt48LiveMember,
    isRecordingActive: Boolean,
    recordingDurationMs: Long,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestRecord: () -> Unit,
    onStopRecord: () -> Unit
) {
    val borderColor = if (isRecordingActive) PrimaryRed else CardBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(if (isRecordingActive) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Avatar Image via Coil
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                if (!member.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = member.thumbnail,
                        contentDescription = member.memberName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Member details & Live badge
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.memberName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (member.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorit",
                            tint = AccentAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaryRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE IDN",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!member.startedAt.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mulai: ${member.startedAt}",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!member.title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = member.title,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons: Favorite and Chevron
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (member.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (member.isFavorite) AccentAmber else TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Lihat Detail",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Phase 5 Availability & Recording Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141722))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stream & Recording Availability Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Stream Availability
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (member.isStreamAvailable) AccentGreen.copy(alpha = 0.15f)
                            else CardBorder.copy(alpha = 0.3f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (member.isStreamAvailable) "SOURCE AVAILABLE" else "SOURCE UNAVAILABLE",
                        color = if (member.isStreamAvailable) AccentGreen else TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Recording Availability Status
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isRecordingActive) PrimaryRed.copy(alpha = 0.2f)
                            else AccentGreen.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isRecordingActive) "MEREKAM" else "SIAP MEREKAM",
                        color = if (isRecordingActive) PrimaryRed else AccentGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Start / Stop Direct Record Action Button
            if (isRecordingActive) {
                Button(
                    onClick = onStopRecord,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Stop (${recordingDurationMs.formatDuration()})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onRequestRecord,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Direct Record", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ApiKeyConfigDialog(
    currentKey: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Pengaturan API Key JKT48Connect", color = TextPrimary) },
        text = {
            Column {
                Text(
                    text = "Dapatkan API key dari portal JKT48Connect (https://v3.jkt48connect.com). Key dienkripsi di penyimpanan lokal perangkat.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
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
                onClick = { onConfirm(keyInput) },
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

private fun formatTime(timeMs: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss 'WIB'", Locale.getDefault())
        sdf.format(Date(timeMs))
    } catch (_: Exception) {
        "—"
    }
}
