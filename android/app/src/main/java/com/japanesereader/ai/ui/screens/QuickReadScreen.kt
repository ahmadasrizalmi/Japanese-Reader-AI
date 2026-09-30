package com.japanesereader.ai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.japanesereader.ai.data.remote.AnalyzeResponseDto
import com.japanesereader.ai.ui.components.RubyText
import com.japanesereader.ai.ui.components.RubyToken
import com.japanesereader.ai.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun QuickReadScreen(
    furiganaMode: String = "always",
    onAnalyze: suspend (String) -> AnalyzeResponseDto,
    onSaveToCollection: (title: String, rawText: String) -> Unit,
    onPlayAudio: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("今週の土曜日に新しいカフェに行きませんか。") }
    var analyzedTranslation by remember { mutableStateOf("Maukah kamu pergi ke kafe baru pada hari Sabtu pekan ini?") }
    var difficultyLevel by remember { mutableStateOf("N4") }
    var isAnalyzing by remember { mutableStateOf(false) }

    var analyzedFurigana by remember {
        mutableStateOf(
            """[
                {"surface":"今週","reading":"こんしゅう","romaji":"konshuu","pos":"noun","meaning":"Minggu ini","jlpt":"N5"},
                {"surface":"の","reading":"の","romaji":"no","pos":"particle","meaning":"Partikel kepemilikan","jlpt":"N5"},
                {"surface":"土曜日","reading":"どようび","romaji":"doyoubi","pos":"noun","meaning":"Hari Sabtu","jlpt":"N5"},
                {"surface":"に","reading":"に","romaji":"ni","pos":"particle","meaning":"Partikel waktu","jlpt":"N5"},
                {"surface":"新しい","reading":"あたらしい","romaji":"atarashii","pos":"i-adj","meaning":"Baru","jlpt":"N5"},
                {"surface":"カフェ","reading":"カフェ","romaji":"kafe","pos":"noun","meaning":"Kafe","jlpt":"N5"},
                {"surface":"に","reading":"に","romaji":"ni","pos":"particle","meaning":"Partikel tujuan","jlpt":"N5"},
                {"surface":"行きませんか","reading":"いきませんか","romaji":"ikimasenka","pos":"verb","meaning":"Maukah pergi?","jlpt":"N5"},
                {"surface":"。","reading":"","romaji":"","pos":"punct","jlpt":"-"}
            ]"""
        )
    }

    val tokens = remember(analyzedFurigana) {
        try {
            val listType = object : TypeToken<List<RubyToken>>() {}.type
            Gson().fromJson<List<RubyToken>>(analyzedFurigana, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val scrollState = rememberScrollState()

    fun triggerAnalyze(textToAnalyze: String) {
        if (textToAnalyze.isBlank()) return
        coroutineScope.launch {
            isAnalyzing = true
            try {
                val res = onAnalyze(textToAnalyze)
                difficultyLevel = res.difficulty_level
                if (res.sentences.isNotEmpty()) {
                    val s0 = res.sentences[0]
                    analyzedTranslation = s0.translated_text
                    analyzedFurigana = Gson().toJson(s0.tokens)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Analisis lokal aktif", Toast.LENGTH_SHORT).show()
            } finally {
                isAnalyzing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Minimalist Scratchpad Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SCRATCHPAD TEKS JEPANG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCrimson
                    )
                    TextButton(
                        onClick = { inputText = "" },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Bersihkan", fontSize = 11.sp, color = TextMuted)
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ketik atau tempel kalimat Jepang...", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                HorizontalDivider(color = CanvasSecondary, thickness = 1.dp)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${inputText.length} Karakter",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Button(
                        onClick = { triggerAnalyze(inputText) },
                        enabled = !isAnalyzing && inputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                        shape = RoundedCornerShape(100)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Analisis",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAnalyzing) "Menganalisis..." else "Analisis Teks", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Inspiration Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Inspirasi:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

            SuggestionChip(
                onClick = {
                    inputText = "今週の土曜日に新しいカフェに行きませんか。"
                    triggerAnalyze(inputText)
                },
                label = { Text("Kalimat Kafe", fontSize = 11.sp) }
            )
            SuggestionChip(
                onClick = {
                    inputText = "明日は午後から雨が降る予報です。傘を持っていきましょう。"
                    triggerAnalyze(inputText)
                },
                label = { Text("Status Cuaca", fontSize = 11.sp) }
            )
            SuggestionChip(
                onClick = {
                    inputText = "お疲れ様です！駅の改札前で待っていますね。"
                    triggerAnalyze(inputText)
                },
                label = { Text("Pesan Singkat", fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instant AI Analysis Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header status tag & actions
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(HighlightMint)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$difficultyLevel Analisis Instan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCrimson
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { onPlayAudio(inputText) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Audio", tint = PrimaryCrimson, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(inputText))
                                Toast.makeText(context, "Teks berhasil disalin!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Furigana Display Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HighlightYellow.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    RubyText(
                        furiganaPayload = analyzedFurigana,
                        furiganaMode = furiganaMode,
                        fontSize = 19
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Translation
                Text(
                    text = "TERJEMAHAN LANGSUNG",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "\"$analyzedTranslation\"",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Lexical Token Slider
                Text(
                    text = "TOKEN KATA & PARTIKEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tokens.filter { it.pos != "punct" }.forEach { tok ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CanvasSecondary)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tok.surface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = TextPrimary
                                )
                                Text(
                                    text = tok.meaning ?: tok.reading ?: "",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save to collection button
                Button(
                    onClick = {
                        val title = if (analyzedTranslation.length > 28) analyzedTranslation.substring(0, 28) + "..." else analyzedTranslation
                        onSaveToCollection(title, inputText)
                        Toast.makeText(context, "Bacaan disimpan ke Koleksi!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonSurface,
                        contentColor = PrimaryCrimson
                    ),
                    shape = RoundedCornerShape(100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Simpan",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan ke Koleksi Bacaan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
