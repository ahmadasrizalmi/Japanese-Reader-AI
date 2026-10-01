package com.japanesereader.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    kanjiFontStyle: String = "mincho",
    currentlyPlayingText: String?,
    onToggleAudio: (text: String, sentenceId: String?) -> Unit,
    onInspectSentence: (sentenceId: String) -> Unit,
    onAutoSaveVocabulary: (kanji: String, reading: String, meaning: String, pos: String, jlpt: String, sentenceId: String?) -> Unit,
    onBack: () -> Unit
) {
    // Zoomable font size: 22sp default, scalable from 18sp to 36sp
    var currentFontSizeSp by remember { mutableFloatStateOf(22f) }

    // Selected word or sentence state (initially null -> 100% clean Zen canvas)
    var selectedToken by remember { mutableStateOf<RubyToken?>(null) }
    var selectedSentence by remember { mutableStateOf<SentenceEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Persistent revealed furigana map for Tap-to-Reveal:
    // Key: "${sentenceId}_${token.surface}_$tokenIndex"
    val revealedWordKeys = remember { mutableStateMapOf<String, Boolean>() }

    val activeFontFamily = if (kanjiFontStyle == "gothic") FontFamily.SansSerif else FontFamily.Serif

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP CONTROL BAR: Clean & Minimalist
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
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

                Column {
                    Text(
                        text = article.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${article.category} • JLPT ${article.difficultyLevel}",
                        fontSize = 11.sp,
                        color = PrimaryCrimson,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

        // Contextual Floating Action Pill (Appears when text or sentence is selected)
        if (selectedSentence != null || selectedToken != null) {
            val isSentenceMode = selectedSentence != null && selectedToken == null
            val activeSent = selectedSentence
            val activeTok = selectedToken
            val textToAct = if (isSentenceMode) activeSent?.originalText ?: "" else activeTok?.surface ?: ""
            val sentenceIdForAudio = activeSent?.id
            val isAudioPlaying = currentlyPlayingText == textToAct

            Card(
                shape = RoundedCornerShape(100),
                colors = CardDefaults.cardColors(containerColor = TextPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Mode Switcher: [Pilih Kalimat] or [Pilih Kata]
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(if (isSentenceMode) HighlightYellow else CrimsonSurface)
                            .clickable {
                                if (isSentenceMode) {
                                    if (activeTok != null) {
                                        selectedToken = activeTok
                                    }
                                } else {
                                    // Switch to sentence mode
                                    selectedToken = null
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isSentenceMode) "Kalimat Utuh" else "Pilih Kalimat",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSentenceMode) TextPrimary else PrimaryCrimson
                        )
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.3f)))

                    // Translate / Detail Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickable { showBottomSheet = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Detail",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Detail",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.3f)))

                    // Audio Play/Stop Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickable { onToggleAudio(textToAct, sentenceIdForAudio) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isAudioPlaying) "Stop" else "Audio",
                            tint = if (isAudioPlaying) HighlightYellow else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAudioPlaying) "Hentikan" else "Audio",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAudioPlaying) HighlightYellow else Color.White
                        )
                    }

                    // Close Selection Button
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Batal",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                selectedToken = null
                                selectedSentence = null
                            }
                    )
                }
            }
        }

        // 100% ZEN READING CANVAS: Responsive, tactile, non-blocking touch targets
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(sentences, key = { _, s -> s.id }) { sIndex, sent ->
                    // Tokenize into full lexical words
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

                    val isSentenceSelected = selectedSentence?.id == sent.id
                    val isOnlySentenceActive = isSentenceSelected && selectedToken == null

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isOnlySentenceActive) HighlightYellow.copy(alpha = 0.45f)
                                else if (isSentenceSelected) HighlightYellow.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .padding(6.dp)
                    ) {
                        // Attention tracker badge if sentence needs deep study (C_inspect >= 3)
                        if (sent.needsDeepStudy) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .clip(RoundedCornerShape(100))
                                    .background(CrimsonSurface)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = PrimaryCrimson,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Perlu pengulangan intensif (${sent.inspectionCount}x dibongkar)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCrimson
                                )
                            }
                        }

                        // Tokens FlowRow
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            tokens.forEachIndexed { tIndex, token ->
                                val wordKey = "${sent.id}_${token.surface}_$tIndex"
                                val isTokenSelected = selectedToken?.surface == token.surface && selectedSentence?.id == sent.id
                                val hasReading = !token.reading.isNullOrBlank() && token.reading != token.surface
                                val isRevealed = (revealedWordKeys[wordKey] == true) || isTokenSelected

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isTokenSelected) PastelPalette[Math.abs(token.surface.hashCode()) % PastelPalette.size]
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            // Toggle furigana reveal on tap
                                            revealedWordKeys[wordKey] = !(revealedWordKeys[wordKey] ?: false)
                                            selectedToken = token
                                            selectedSentence = sent

                                            // Auto-save word in background if it's a content word (skip punctuation and bare particles)
                                            if (token.pos !in listOf("punct", "particle") &&
                                                token.surface !in listOf("、", "。", "！", "？", "は", "が", "の", "に", "で", "を", "と", "へ")
                                            ) {
                                                val meaning = token.meaning ?: sent.translatedText
                                                val reading = token.reading ?: token.surface
                                                val pos = token.pos ?: "noun"
                                                val jlpt = token.jlpt ?: "N5"
                                                onAutoSaveVocabulary(token.surface, reading, meaning, pos, jlpt, sent.id)
                                            }

                                            // Inspection telemetry
                                            onInspectSentence(sent.id)
                                        }
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    // Furigana appears on tap!
                                    if (hasReading && token.reading != null) {
                                        if (isRevealed) {
                                            Text(
                                                text = token.reading,
                                                fontSize = (currentFontSizeSp * 0.52f).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryCrimson,
                                                lineHeight = (currentFontSizeSp * 0.6f).sp
                                            )
                                        } else {
                                            // Reserve vertical space so baseline NEVER jumps when furigana appears
                                            Spacer(modifier = Modifier.height((currentFontSizeSp * 0.6f).dp))
                                        }
                                    }

                                    // Full lexical word
                                    Text(
                                        text = token.surface,
                                        fontSize = currentFontSizeSp.sp,
                                        fontFamily = activeFontFamily,
                                        color = TextPrimary,
                                        lineHeight = (currentFontSizeSp * 1.55f).sp
                                    )
                                }
                            }
                        }

                        // Direct Contextual Translation Box right on canvas when sentence is selected!
                        if (isSentenceSelected && sent.translatedText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CanvasSecondary)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "TERJEMAHAN KALIMAT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCrimson,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sent.translatedText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        lineHeight = 18.sp
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
    if (showBottomSheet && (selectedToken != null || selectedSentence != null)) {
        val token = selectedToken
        val sentence = selectedSentence
        val isSentenceMode = sentence != null && token == null
        val surfaceText = if (isSentenceMode) sentence?.originalText ?: "" else token?.surface ?: ""
        val sentenceIdForAudio = sentence?.id
        val isAudioPlaying = currentlyPlayingText == surfaceText

        val grammarList = remember(sentence?.grammarAnalysis) {
            try {
                val listType = object : TypeToken<List<GrammarBreakdownItem>>() {}.type
                Gson().fromJson<List<GrammarBreakdownItem>>(sentence?.grammarAnalysis ?: "[]", listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                // Header: Selected Word + Reading + JLPT/POS Badges
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = surfaceText,
                                fontSize = if (surfaceText.length <= 6) 28.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = activeFontFamily,
                                color = PrimaryCrimson
                            )
                            if (token != null && !token.reading.isNullOrBlank() && token.reading != token.surface) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = token.reading,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (token != null && !token.romaji.isNullOrBlank()) {
                            Text(
                                text = token.romaji,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        // Badges for JLPT and POS
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            if (token != null && !token.jlpt.isNullOrBlank() && token.jlpt != "-") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100))
                                        .background(CrimsonSurface)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "JLPT ${token.jlpt}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCrimson
                                    )
                                }
                            }
                            if (token != null && !token.pos.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100))
                                        .background(CanvasSecondary)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = token.pos.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Audio Button: Tap to Play, Tap again to STOP!
                    IconButton(
                        onClick = { onToggleAudio(surfaceText, sentenceIdForAudio) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isAudioPlaying) PrimaryCrimson else CrimsonSurface)
                    ) {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isAudioPlaying) "Hentikan Suara" else "Putar Pelafalan",
                            tint = if (isAudioPlaying) Color.White else PrimaryCrimson,
                            modifier = Modifier.size(24.dp)
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
                                Text("Otomatis Tersimpan", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = token?.meaning ?: sentence?.translatedText ?: "Terjemahan",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (sentence != null && token != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kalimat: \"${sentence.translatedText}\"",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Grammar breakdown (if present)
                if (grammarList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BEDAH TATA BAHASA & POLA KALIMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
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

                // Kanji Deconstruction
                if (surfaceText.any { JapaneseMorphologyEngine.isKanji(it) }) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val firstKanji = surfaceText.first { JapaneseMorphologyEngine.isKanji(it) }.toString()

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
                                    fontFamily = activeFontFamily,
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
