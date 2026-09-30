package com.japanesereader.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.japanesereader.ai.data.local.entity.ArticleEntity
import com.japanesereader.ai.data.local.entity.SentenceEntity
import com.japanesereader.ai.ui.components.RubyToken
import com.japanesereader.ai.ui.theme.*

data class GrammarBreakdownItem(
    val pattern: String,
    val explanation: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    article: ArticleEntity,
    sentences: List<SentenceEntity>,
    currentlyPlayingText: String?,
    onToggleAudio: (text: String) -> Unit,
    onInspectSentence: (sentenceId: String) -> Unit,
    onAutoSaveVocabulary: (kanji: String, reading: String, meaning: String, sentenceId: String?) -> Unit,
    onBack: () -> Unit
) {
    // Zoom scale state: Default 22sp, scalable from 18sp to 36sp
    var currentFontSizeSp by remember { mutableFloatStateOf(22f) }

    // Active tapped word / sentence state (null initially -> 100% clean canvas!)
    var activeToken by remember { mutableStateOf<RubyToken?>(null) }
    var activeSentence by remember { mutableStateOf<SentenceEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasSurface)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Reader Sub-header Bar (Clean: NO toggle buttons!)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(CrimsonSurface)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "JLPT ${article.difficultyLevel}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCrimson
                    )
                }

                Text(
                    text = article.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
            }

            // Quick Font Zoom Steppers (A- / A+)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(CanvasSecondary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "A-",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentFontSizeSp > 18f) TextPrimary else TextMuted,
                    modifier = Modifier
                        .clickable(enabled = currentFontSizeSp > 18f) {
                            currentFontSizeSp = Math.max(18f, currentFontSizeSp - 2f)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = "${currentFontSizeSp.toInt()}sp",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryCrimson
                )
                Text(
                    text = "A+",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentFontSizeSp < 36f) TextPrimary else TextMuted,
                    modifier = Modifier
                        .clickable(enabled = currentFontSizeSp < 36f) {
                            currentFontSizeSp = Math.min(36f, currentFontSizeSp + 2f)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Reading Canvas Container with Pinch-to-Zoom Gesture Detection
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newSize = currentFontSizeSp * zoom
                        currentFontSizeSp = newSize.coerceIn(18f, 36f)
                    }
                }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(sentences, key = { _, s -> s.id }) { index, sent ->
                    val tokens = remember(sent.furiganaPayload) {
                        try {
                            val listType = object : TypeToken<List<RubyToken>>() {}.type
                            Gson().fromJson<List<RubyToken>>(sent.furiganaPayload, listType) ?: emptyList()
                        } catch (e: Exception) {
                            listOf(RubyToken(surface = sent.originalText))
                        }
                    }

                    // Chat Bubble Style (Tanaka-san / Partner Style from UI/UX design)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Avatar Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CrimsonSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "田",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCrimson
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Sender name and timestamp
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "田中 健一",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Kalimat ${sent.sequenceOrder}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Chat Bubble Card
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                                    .background(CanvasSecondary)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                // FlowRow for word-by-word interactive tap
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    tokens.forEach { token ->
                                        val isTapped = activeToken?.surface == token.surface && activeSentence?.id == sent.id
                                        val hasReading = !token.reading.isNullOrBlank() && token.reading != token.surface

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                            modifier = Modifier
                                                .padding(horizontal = 1.5.dp, vertical = 2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isTapped) PastelPalette[Math.abs(token.surface.hashCode()) % PastelPalette.size]
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    activeToken = token
                                                    activeSentence = sent
                                                    showBottomSheet = true

                                                    // 1. Auto-save vocabulary immediately in background!
                                                    val meaning = token.meaning ?: sent.translatedText
                                                    val reading = token.reading ?: token.surface
                                                    onAutoSaveVocabulary(token.surface, reading, meaning, sent.id)

                                                    // 2. Increment inspection telemetry ($C_{inspect} + 1$)
                                                    onInspectSentence(sent.id)
                                                }
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        ) {
                                            // Furigana appears ON-DEMAND when tapped, or if previously inspected
                                            if (isTapped && hasReading && token.reading != null) {
                                                Text(
                                                    text = token.reading,
                                                    fontSize = (currentFontSizeSp * 0.52f).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryCrimson,
                                                    lineHeight = (currentFontSizeSp * 0.6f).sp
                                                )
                                            }

                                            Text(
                                                text = token.surface,
                                                fontSize = currentFontSizeSp.sp,
                                                fontFamily = FontFamily.Serif,
                                                color = TextPrimary,
                                                lineHeight = (currentFontSizeSp * 1.5f).sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Needs Deep Study Flag Indicator
                            if (sent.needsDeepStudy) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = "Sulit",
                                        tint = SecondaryVermilion,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Sering di-tap (Inspeksi: ${sent.inspectionCount}x)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SecondaryVermilion
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Word & Grammar Detail (With toggleable Play/Stop Audio)
    if (showBottomSheet && activeToken != null && activeSentence != null) {
        val token = activeToken!!
        val sentence = activeSentence!!
        val isPlaying = currentlyPlayingText == token.surface

        val grammarList = remember(sentence.grammarAnalysis) {
            try {
                val listType = object : TypeToken<List<GrammarBreakdownItem>>() {}.type
                Gson().fromJson<List<GrammarBreakdownItem>>(sentence.grammarAnalysis, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                activeToken = null
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                // Header: Selected Word + Reading
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = token.surface,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = PrimaryCrimson
                            )
                            if (!token.reading.isNullOrBlank() && token.reading != token.surface) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = token.reading,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (!token.romaji.isNullOrBlank()) {
                            Text(
                                text = token.romaji,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // AUDIO SPEAKER BUTTON: Tap to Play, Tap again to STOP!
                    // Visual status indicator: Crimson background when playing!
                    IconButton(
                        onClick = { onToggleAudio(token.surface) },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) PrimaryCrimson else CrimsonSurface)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (isPlaying) "Hentikan Suara" else "Putar Pelafalan",
                            tint = if (isPlaying) Color.White else PrimaryCrimson,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Meaning Tile
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CanvasSecondary)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ARTI KONTEKS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(HighlightMint)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Auto-Saved", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = token.meaning ?: sentence.translatedText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kalimat: \"${sentence.translatedText}\"",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Grammar breakdown (if present)
                if (grammarList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BEDAH TATA BAHASA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                        grammarList.forEach { g ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(HighlightPink.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(text = g.pattern, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = g.explanation, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
