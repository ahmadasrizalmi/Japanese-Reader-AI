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
import com.japanesereader.ai.data.remote.TokenDto
import com.japanesereader.ai.ui.components.RubyToken
import com.japanesereader.ai.ui.theme.*
import com.japanesereader.ai.util.JapaneseMorphologyEngine

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
    // Zoomable font size: 22sp default, pinch or buttons from 18sp to 36sp
    var currentFontSizeSp by remember { mutableFloatStateOf(22f) }

    // Active selected word token and sentence (Clean initial state: null!)
    var activeToken by remember { mutableStateOf<RubyToken?>(null) }
    var activeSentenceId by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val readingTimeMin = Math.max(1, article.rawText.length / 100)
    val kanjiPercent = (article.kanjiRatio * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasSurface)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // 1. TOP APP BAR
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

                Text(
                    text = "Reader Studio",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
                    fontSize = 11.sp,
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

        // 2. ARTICLE HEADER CARD (Restored from UI/UX design: text_selection_translation_pop_up)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(CrimsonSurface)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = article.category.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCrimson
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(HighlightMint)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "JLPT ${article.difficultyLevel}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = article.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$readingTimeMin menit baca",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text("•", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "$kanjiPercent% Kanji",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text("•", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "Ketuk kata untuk arti",
                        fontSize = 11.sp,
                        color = PrimaryCrimson,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. EDITORIAL READER CANVAS (Restored layout with Pinch-to-Zoom & Word-by-Word Tap)
        Card(
            shape = RoundedCornerShape(24.dp),
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
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(sentences, key = { _, s -> s.id }) { sIndex, sent ->
                    // Parse tokens into real words using tokenizer if payload is empty/fallback
                    val tokens = remember(sent.furiganaPayload, sent.originalText) {
                        try {
                            val listType = object : TypeToken<List<RubyToken>>() {}.type
                            val parsed: List<RubyToken>? = Gson().fromJson(sent.furiganaPayload, listType)
                            if (parsed != null && parsed.isNotEmpty()) {
                                parsed
                            } else {
                                JapaneseMorphologyEngine.tokenize(sent.originalText).map {
                                    RubyToken(
                                        surface = it.surface,
                                        reading = it.reading,
                                        romaji = it.romaji,
                                        pos = it.pos,
                                        meaning = it.meaning,
                                        jlpt = it.jlpt
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            JapaneseMorphologyEngine.tokenize(sent.originalText).map {
                                RubyToken(surface = it.surface, reading = it.reading, romaji = it.romaji, pos = it.pos)
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Natural editorial paragraph flow with FlowRow
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            tokens.forEach { token ->
                                val isSelected = activeToken?.surface == token.surface && activeSentenceId == sent.id
                                val hasReading = !token.reading.isNullOrBlank() && token.reading != token.surface

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) PastelPalette[Math.abs(token.surface.hashCode()) % PastelPalette.size]
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            activeToken = token
                                            activeSentenceId = sent.id
                                            showBottomSheet = true

                                            // Auto-save word to database immediately
                                            val meaning = token.meaning ?: sent.translatedText
                                            val reading = token.reading ?: token.surface
                                            onAutoSaveVocabulary(token.surface, reading, meaning, sent.id)

                                            // Increment inspection telemetry counter
                                            onInspectSentence(sent.id)
                                        }
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    // Furigana appears directly above kanji on demand when selected!
                                    if (isSelected && hasReading && token.reading != null) {
                                        Text(
                                            text = token.reading,
                                            fontSize = (currentFontSizeSp * 0.52f).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryCrimson,
                                            lineHeight = (currentFontSizeSp * 0.6f).sp
                                        )
                                    }

                                    // Full word token (NOT per single character!)
                                    Text(
                                        text = token.surface,
                                        fontSize = currentFontSizeSp.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = TextPrimary,
                                        lineHeight = (currentFontSizeSp * 1.55f).sp
                                    )
                                }
                            }
                        }

                        // Sentence Play Audio bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CanvasSecondary)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val isSentencePlaying = currentlyPlayingText == sent.originalText

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleAudio(sent.originalText) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isSentencePlaying) PrimaryCrimson else CrimsonSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSentencePlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Putar Kalimat",
                                        tint = if (isSentencePlaying) Color.White else PrimaryCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (isSentencePlaying) "Sedang diputar... (Klik untuk stop)" else "Dengarkan kalimat ini",
                                    fontSize = 11.sp,
                                    color = if (isSentencePlaying) PrimaryCrimson else TextSecondary,
                                    fontWeight = if (isSentencePlaying) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            if (sent.needsDeepStudy) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100))
                                        .background(CrimsonSurface)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Inspeksi: ${sent.inspectionCount}x",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. FOCUSED CONTEXTUAL TRANSLATION & GRAMMAR OVERLAY CARD (Restored from text_selection_translation_pop_up design)
    if (showBottomSheet && activeToken != null) {
        val token = activeToken!!
        val sentence = sentences.find { it.id == activeSentenceId }
        val isPlaying = currentlyPlayingText == token.surface

        val grammarList = remember(sentence?.grammarAnalysis) {
            try {
                val listType = object : TypeToken<List<GrammarBreakdownItem>>() {}.type
                Gson().fromJson<List<GrammarBreakdownItem>>(sentence?.grammarAnalysis ?: "[]", listType) ?: emptyList()
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
                // Header of the Card: Selected Term + Furigana + Phonetics + Pronounce Toggle Action
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = token.surface,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = PrimaryCrimson
                            )
                            if (!token.reading.isNullOrBlank() && token.reading != token.surface) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = token.reading,
                                    fontSize = 17.sp,
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

                    // Native Audio Sound Button: Tap to Play, Tap again to STOP!
                    IconButton(
                        onClick = { onToggleAudio(token.surface) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) PrimaryCrimson else CrimsonSurface)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (isPlaying) "Hentikan Suara" else "Putar Pelafalan",
                            tint = if (isPlaying) Color.White else PrimaryCrimson,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lexical & Meaning Tile
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
                            Text("ARTI UTAMA (INDONESIAN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(HighlightMint)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Otomatis Tersimpan", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = token.meaning ?: sentence?.translatedText ?: "Kosakata terpilih",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (sentence != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Konteks Kalimat: \"${sentence.translatedText}\"",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Grammar Breakdown Block
                if (grammarList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ANALISIS TATA BAHASA & POLA KALIMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
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

                // Kanji Deconstruction Detail Tile
                if (token.surface.any { JapaneseMorphologyEngine.isKanji(it) }) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val firstKanji = token.surface.first { JapaneseMorphologyEngine.isKanji(it) }.toString()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CanvasSecondary)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = firstKanji,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = PrimaryCrimson
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Kanji: $firstKanji",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Radikal kanji & morfem dasar",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
