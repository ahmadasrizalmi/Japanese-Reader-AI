package com.japanesereader.ai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesereader.ai.data.local.PreferencesManager
import com.japanesereader.ai.data.local.entity.UserSettingsEntity
import com.japanesereader.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun SettingsScreen(
    settings: UserSettingsEntity?,
    totalVocabCount: Int = 0,
    syncStatus: String,
    onUpdateSettings: (UserSettingsEntity) -> Unit,
    onSyncNow: () -> Unit,
    onPlayAudioPreview: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val currentSettings = settings ?: UserSettingsEntity(
        userId = "usr_default",
        furiganaMode = prefs.furiganaMode,
        kanjiFontStyle = prefs.kanjiFontStyle,
        ttsSpeed = prefs.ttsSpeed,
        deepseekTone = prefs.deepseekTone,
        deepseekApiKey = prefs.deepseekApiKey.ifBlank { null }
    )

    // Dynamic user profile state (editable by user, not hardcoded!)
    var userName by remember { mutableStateOf(prefs.userName) }
    var showEditNameDialog by remember { mutableStateOf(false) }

    var tone by remember { mutableStateOf(currentSettings.deepseekTone) }
    var speed by remember { mutableFloatStateOf(currentSettings.ttsSpeed) }
    var furiMode by remember { mutableStateOf(currentSettings.furiganaMode) }
    var fontStyle by remember { mutableStateOf(currentSettings.kanjiFontStyle) }


    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. DYNAMIC USER PROFILE BANNER (No hardcoded Tanaka Aoi!)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(CrimsonSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("読", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                    }

                    Column {
                        Text(
                            text = userName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(HighlightMint)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Pembelajar Mandiri",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                            Text(
                                text = "$totalVocabCount kata tersimpan",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showEditNameDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Nama",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }


        // 3. GAYA HURUF KANJI (FONT STYLE)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FontDownload, contentDescription = "Font", tint = PrimaryCrimson, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GAYA HURUF KANJI (FONT STYLE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                }

                Text("Pilih Tipografi Membaca", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Mincho Option
                    val isMincho = fontStyle == "mincho"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isMincho) CrimsonSurface else CanvasSecondary)
                            .clickable {
                                fontStyle = "mincho"
                                prefs.kanjiFontStyle = "mincho"
                                onUpdateSettings(currentSettings.copy(kanjiFontStyle = "mincho"))
                            }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "明朝体",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = if (isMincho) PrimaryCrimson else TextPrimary
                                )
                                if (isMincho) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Dipilih", tint = PrimaryCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Mincho (Serif Novel)", fontSize = 10.sp, color = TextSecondary)
                        }
                    }

                    // Gothic Option
                    val isGothic = fontStyle == "gothic"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isGothic) CrimsonSurface else CanvasSecondary)
                            .clickable {
                                fontStyle = "gothic"
                                prefs.kanjiFontStyle = "gothic"
                                onUpdateSettings(currentSettings.copy(kanjiFontStyle = "gothic"))
                            }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ゴシック",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = if (isGothic) PrimaryCrimson else TextPrimary
                                )
                                if (isGothic) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Dipilih", tint = PrimaryCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Gothic (Clean Sans)", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // 4. AUDIO & NEURAL TTS
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = "Voice", tint = PrimaryCrimson, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AUDIO & SINTESIS SUARA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kecepatan TTS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(CrimsonSurface)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${String.format("%.1f", speed)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                    }
                }

                Slider(
                    value = speed,
                    onValueChange = {
                        speed = it
                        prefs.ttsSpeed = it
                        onUpdateSettings(currentSettings.copy(ttsSpeed = it))
                    },
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = PrimaryCrimson, activeTrackColor = PrimaryCrimson)
                )

                Button(
                    onClick = { onPlayAudioPreview("東京の春は桜が満開です。") },
                    colors = ButtonDefaults.buttonColors(containerColor = CanvasSecondary, contentColor = PrimaryCrimson),
                    shape = RoundedCornerShape(100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uji Suara Contoh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. SINKRONISASI CLOUDFLARE EDGE
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CloudSync, contentDescription = "Sync", tint = PrimaryCrimson, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SINKRONISASI CLOUDFLARE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(HighlightMint)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("TERHUBUNG", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }

                Text(
                    text = "Pencadangan cloud untuk koleksi bacaan, kartu flashcard, dan mesin AI otomatis di edge network.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Menyinkronkan data...", Toast.LENGTH_SHORT).show()
                            onSyncNow()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                        shape = RoundedCornerShape(100),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sinkronkan Sekarang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Status: $syncStatus",
                        fontSize = 11.sp,
                        color = PrimaryCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Dialog for Editing Display Name
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Ubah Nama Tampilan", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Nama Anda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempName.trim().ifBlank { "Pembaca Komorebi" }
                        userName = trimmed
                        prefs.userName = trimmed
                        showEditNameDialog = false
                        Toast.makeText(context, "Nama tampilan berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }
}
