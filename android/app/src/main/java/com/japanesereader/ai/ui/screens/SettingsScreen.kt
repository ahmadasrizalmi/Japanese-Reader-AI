package com.japanesereader.ai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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

    var tone by remember { mutableStateOf(currentSettings.deepseekTone) }
    var speed by remember { mutableFloatStateOf(currentSettings.ttsSpeed) }
    var furiMode by remember { mutableStateOf(currentSettings.furiganaMode) }
    var fontStyle by remember { mutableStateOf(currentSettings.kanjiFontStyle) }

    // Persistent API key input state
    var apiKey by remember {
        mutableStateOf(prefs.deepseekApiKey.ifBlank { currentSettings.deepseekApiKey ?: "" })
    }
    var backendUrl by remember { mutableStateOf(prefs.backendUrl) }
    var isTestingKey by remember { mutableStateOf(false) }
    var isTestingBackend by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Banner
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(100))
                        .background(CrimsonSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("読", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Aoi Tanaka (田中 葵)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .background(CrimsonSurface)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("N3 CHALLENGER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                        }
                        Text("🔥 42 Hari Beruntun", fontSize = 11.sp, color = SecondaryVermilion, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section 1: DeepSeek API (BYOK)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "Key", tint = PrimaryCrimson, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("API DEEPSEEK (BYOK)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(HighlightMint)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("TERENKRIPSI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("sk-deepseek-...", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val trimmed = apiKey.trim()
                            prefs.deepseekApiKey = trimmed
                            onUpdateSettings(currentSettings.copy(deepseekApiKey = trimmed.ifBlank { null }))
                            Toast.makeText(context, "Kunci API DeepSeek berhasil disimpan!", Toast.LENGTH_SHORT).show()

                            if (trimmed.isNotBlank()) {
                                isTestingKey = true
                                coroutineScope.launch {
                                    try {
                                        val testOk = withContext(Dispatchers.IO) {
                                            val conn = (URL("https://api.deepseek.com/v1/models").openConnection() as HttpURLConnection).apply {
                                                requestMethod = "GET"
                                                setRequestProperty("Authorization", "Bearer $trimmed")
                                                connectTimeout = 5000
                                                readTimeout = 5000
                                            }
                                            conn.responseCode in 200..299
                                        }
                                        if (testOk) {
                                            Toast.makeText(context, "✅ Kunci DeepSeek valid & aktif!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "⚠️ Kunci disimpan, periksa koneksi/saldo DeepSeek.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Kunci tersimpan secara lokal.", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isTestingKey = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                        shape = RoundedCornerShape(100),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingKey) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Simpan Kunci", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (apiKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                apiKey = ""
                                prefs.deepseekApiKey = ""
                                onUpdateSettings(currentSettings.copy(deepseekApiKey = null))
                                Toast.makeText(context, "Kunci dihapus (Memakai parser lokal)", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(100)
                        ) {
                            Text("Hapus", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                Text(
                    text = "Aplikasi dapat mem-parsing bahasa Jepang secara cerdas memakai AI DeepSeek (jika kunci diisi) maupun mesin morfologi deterministik bawaan luring.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )
            }
        }

        // Section 2: Display & Furigana
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.MenuBook, contentDescription = "Buku", tint = PrimaryCrimson, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TAMPILAN BACA & FURIGANA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                }

                Text("Mode Furigana", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("always" to "Selalu Aktif", "tap" to "Ketuk Tampil", "off" to "Nonaktif").forEach { (valKey, label) ->
                        val isSelected = furiMode == valKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) PrimaryCrimson else CanvasSecondary)
                                .clickable {
                                    furiMode = valKey
                                    prefs.furiganaMode = valKey
                                    onUpdateSettings(currentSettings.copy(furiganaMode = valKey))
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("Gaya Huruf Kanji", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("mincho" to "明朝体 (Mincho)", "gothic" to "ゴシック (Gothic)").forEach { (valKey, label) ->
                        val isSelected = fontStyle == valKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) CrimsonSurface else CanvasSecondary)
                                .clickable {
                                    fontStyle = valKey
                                    prefs.kanjiFontStyle = valKey
                                    onUpdateSettings(currentSettings.copy(kanjiFontStyle = valKey))
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) PrimaryCrimson else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Audio & Neural TTS
        Card(
            shape = RoundedCornerShape(24.dp),
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

        // Section 4: Cloudflare Edge Backend Sync
        Card(
            shape = RoundedCornerShape(24.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CloudSync, contentDescription = "Sync", tint = PrimaryCrimson, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CLOUDFLARE EDGE BACKEND", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                }

                Text("URL Server Cloudflare:", fontSize = 11.sp, color = TextSecondary)

                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val trimmed = backendUrl.trim().trimEnd('/')
                            prefs.backendUrl = trimmed
                            Toast.makeText(context, "URL Server disimpan!", Toast.LENGTH_SHORT).show()

                            isTestingBackend = true
                            coroutineScope.launch {
                                try {
                                    val ok = withContext(Dispatchers.IO) {
                                        val conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                                            connectTimeout = 4000
                                            readTimeout = 4000
                                        }
                                        conn.responseCode in 200..299
                                    }
                                    if (ok) {
                                        Toast.makeText(context, "✅ Terhubung ke Cloudflare Edge Worker!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "⚠️ Server merespon status tidak 200.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal menghubungi URL: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isTestingBackend = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                        shape = RoundedCornerShape(100),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingBackend) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Simpan & Uji", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "Menyinkronkan ke Cloudflare...", Toast.LENGTH_SHORT).show()
                            onSyncNow()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonSurface, contentColor = PrimaryCrimson),
                        shape = RoundedCornerShape(100),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sinkron Sekarang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
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
