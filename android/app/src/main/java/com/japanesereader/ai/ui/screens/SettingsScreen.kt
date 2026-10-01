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

    // API key input state with masking & eye toggle
    var apiKey by remember {
        mutableStateOf(prefs.deepseekApiKey.ifBlank { currentSettings.deepseekApiKey ?: "" })
    }
    var isKeyVisible by remember { mutableStateOf(false) }
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

        // 2. API DEEPSEEK (BYOK) - MASKED & ENCRYPTED
        Card(
            shape = RoundedCornerShape(22.dp),
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
                    if (apiKey.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .background(HighlightMint)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("TERENKRIPSI & AKTIF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .background(CanvasSecondary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("MODE OFFLINE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        }
                    }
                }

                // Password / masked input with visibility toggle
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("sk-deepseek-...", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Sembunyikan" else "Tampilkan",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
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
                            isKeyVisible = false // Auto-mask on save
                            Toast.makeText(context, "Kunci API DeepSeek tersimpan secara terenkripsi!", Toast.LENGTH_SHORT).show()

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
                                            Toast.makeText(context, "✅ Kunci DeepSeek valid dan terverifikasi!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "⚠️ Kunci disimpan (Status: Offline / Gagal verifikasi saldo).", Toast.LENGTH_LONG).show()
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
                        Text(if (apiKey.isBlank()) "Simpan Kunci" else "Ubah Kunci", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (apiKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                apiKey = ""
                                prefs.deepseekApiKey = ""
                                onUpdateSettings(currentSettings.copy(deepseekApiKey = null))
                                Toast.makeText(context, "Kunci dihapus (Beralih ke mesin luring bawaan)", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(100)
                        ) {
                            Text("Hapus", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                Text(
                    text = "Kunci Anda disimpan dengan enkripsi lokal. Jika kosong, sistem otomatis memakai mesin morfologi bawaan secara offline.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )
            }
        }

        // 3. AUDIO & NEURAL TTS
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

        // 4. CLOUDFLARE EDGE BACKEND
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
