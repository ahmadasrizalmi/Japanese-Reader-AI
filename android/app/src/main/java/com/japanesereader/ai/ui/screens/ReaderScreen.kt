package com.japanesereader.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
    analysisProgress: com.japanesereader.ai.data.repository.AnalysisProgress? = null,
    kanjiFontStyle: String = "mincho",
    currentlyPlayingText: String?,
    onToggleAudio: (text: String, sentenceId: String?) -> Unit,
    onInspectSentence: (sentenceId: String) -> Unit,
    onAutoSaveVocabulary: (kanji: String, reading: String, meaning: String, pos: String, jlpt: String, sentenceId: String?) -> Unit,
    onNextArticle: (() -> Unit)? = null,
    onPreviousArticle: (() -> Unit)? = null,
    onShowVocabList: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    // Native back handler (bezel swipe / system back)
    BackHandler { onBack() }

    // Minimalist Zen Mode States
    var isFuriganaEnabled by remember { mutableStateOf(false) }
    var isTranslateModeEnabled by remember { mutableStateOf(false) }
    var showFloatingPills by remember { mutableStateOf(false) }
    val expandedSentences = remember { mutableStateMapOf<String, Boolean>() }

    // Zoomable font size: 20sp default, scalable from 16sp to 32sp
    var currentFontSizeSp by remember { mutableFloatStateOf(20f) }

    // Selected word or sentence state for bottom sheet
    var selectedToken by remember { mutableStateOf<RubyToken?>(null) }
    var selectedSentence by remember { mutableStateOf<SentenceEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Tap-to-reveal fallback per word
    val revealedWordKeys = remember { mutableStateMapOf<String, Boolean>() }

    val activeFontFamily = if (kanjiFontStyle == "gothic") FontFamily.SansSerif else FontFamily.Serif
    val listState = rememberLazyListState()

    // Keep gesture callbacks fresh: pointerInput(Unit) runs once and would otherwise
    // capture stale lambda instances across recompositions.
    val latestOnNext = rememberUpdatedState(onNextArticle)
    val latestOnPrevious = rememberUpdatedState(onPreviousArticle)
    val latestOnBack = rememberUpdatedState(onBack)

    // 100% Full-Width Canvas Root with horizontal swipe navigation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasSurface)
            .pointerInput(Unit) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX < -150f) {
                            // Swiped Left -> Next Article
                            latestOnNext.value?.invoke()
                        } else if (totalDragX > 150f) {
                            // Swiped Right -> Previous Article or Back
                            val prev = latestOnPrevious.value
                            if (prev != null) prev.invoke() else latestOnBack.value.invoke()
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragX += dragAmount
                    }
                )
            }
    ) {
        // Subtle top reading progress bar (1.5dp)
        val progress by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val total = layoutInfo.totalItemsCount
                if (total <= 1) 0f
                else (listState.firstVisibleItemIndex.toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter),
            color = PrimaryCrimson.copy(alpha = 0.6f),
            trackColor = Color.Transparent
        )

        // Non-intrusive Progressive Analysis Banner
        val prog = analysisProgress
        if (prog != null && prog.articleId == article.id) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(100))
                    .background(if (prog.isComplete) Color(0xFFE8F5E9) else CrimsonSurface)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!prog.isComplete) {
                        CircularProgressIndicator(
                            color = PrimaryCrimson,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Menganalisis teks: bagian ${prog.currentChunk} dari ${prog.totalChunks}...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCrimson
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Analisis teks lengkap siap dibaca!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }

        // Article Scrollable Canvas (Edge-to-Edge with natural reading margins)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap on background / margin toggles the floating control pill!
                    showFloatingPills = !showFloatingPills
                }
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header: Title & Info
            item(key = "article_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 8.dp)
                ) {
                    val charCount = article.rawText.length
                    val estimatedMinutes = Math.max(1, charCount / 300)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "~$estimatedMinutes menit baca",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryCrimson
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "$charCount karakter",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }

                    Text(
                        text = article.title,
                        fontSize = (currentFontSizeSp * 1.15f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = activeFontFamily,
                        color = TextPrimary,
                        lineHeight = (currentFontSizeSp * 1.5f).sp
                    )
                }
            }

            // Sentences List
            itemsIndexed(sentences, key = { _, s -> s.id }) { _, sent ->
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
                val isSentenceExpanded = isTranslateModeEnabled && (expandedSentences[sent.id] == true)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Seamless あA sentence translation trigger icon
                        if (isTranslateModeEnabled) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp, top = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSentenceExpanded) PrimaryCrimson else CanvasSecondary)
                                    .clickable {
                                        expandedSentences[sent.id] = !(expandedSentences[sent.id] ?: false)
                                    }
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "あA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSentenceExpanded) Color.White else TextSecondary
                                )
                            }
                        }

                        // Japanese Tokens FlowRow
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            tokens.forEachIndexed { tIndex, token ->
                                val wordKey = "${sent.id}_${token.surface}_$tIndex"
                                val isTokenSelected = selectedToken?.surface == token.surface && selectedSentence?.id == sent.id
                                val hasReading = !token.reading.isNullOrBlank() &&
                                        token.reading != token.surface &&
                                        token.surface !in listOf("、", "。", "！", "？", "「", "」", "（", "）", "・")
                                val showRuby = isFuriganaEnabled || (revealedWordKeys[wordKey] == true) || isTokenSelected

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isTokenSelected) PastelPalette[Math.abs(token.surface.hashCode()) % PastelPalette.size]
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            revealedWordKeys[wordKey] = !(revealedWordKeys[wordKey] ?: false)
                                            selectedToken = token
                                            selectedSentence = sent
                                            showBottomSheet = true

                                            if (token.pos !in listOf("punct", "particle") &&
                                                token.surface !in listOf("、", "。", "！", "？", "は", "が", "の", "に", "で", "を", "と", "へ")
                                            ) {
                                                val meaning = token.meaning ?: sent.translatedText
                                                val reading = token.reading ?: token.surface
                                                val pos = token.pos ?: "noun"
                                                val jlpt = token.jlpt ?: "N5"
                                                onAutoSaveVocabulary(token.surface, reading, meaning, pos, jlpt, sent.id)
                                            }

                                            onInspectSentence(sent.id)
                                        }
                                        .padding(horizontal = if (isTokenSelected) 2.dp else 0.dp)
                                ) {
                                    // Zero layout shift & flat typographic baseline
                                    if (isFuriganaEnabled) {
                                        if (hasReading && token.reading != null) {
                                            Text(
                                                text = token.reading,
                                                fontSize = (currentFontSizeSp * 0.46f).sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PrimaryCrimson,
                                                lineHeight = (currentFontSizeSp * 0.55f).sp
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height((currentFontSizeSp * 0.55f).dp))
                                        }
                                    } else if (revealedWordKeys[wordKey] == true || isTokenSelected) {
                                        if (hasReading && token.reading != null) {
                                            Text(
                                                text = token.reading,
                                                fontSize = (currentFontSizeSp * 0.46f).sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PrimaryCrimson,
                                                lineHeight = (currentFontSizeSp * 0.55f).sp
                                            )
                                        }
                                    }

                                    // Natural Japanese character typography
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
                    }

                    // Seamless Inline Sentence Translation: directly below the Japanese sentence
                    AnimatedVisibility(
                        visible = isSentenceExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (sent.translatedText.isNotBlank()) {
                            Text(
                                text = sent.translatedText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary,
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isTranslateModeEnabled) 28.dp else 4.dp, top = 4.dp, bottom = 6.dp)
                            )
                        }
                    }
                }
            }

            // Article Footer Actions (Mark as Finished & Show Looked-Up Words)
            item(key = "article_footer") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 90.dp)
                ) {
                    // Green solid CTA button: finish reading (returns to collection)
                    Button(
                        onClick = {
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Selesai Membaca",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary outlined button: SHOW LOOKED-UP WORDS
                    OutlinedButton(
                        onClick = {
                            if (onShowVocabList != null) {
                                onShowVocabList()
                            } else {
                                onBack()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "SHOW LOOKED-UP WORDS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Floating Minimalist Action Pill (Appears upon tapping outside text)
        AnimatedVisibility(
            visible = showFloatingPills,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(100),
                color = TextPrimary.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Toggle Furigana [ふりがな]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(if (isFuriganaEnabled) PrimaryCrimson else Color.Transparent)
                            .clickable { isFuriganaEnabled = !isFuriganaEnabled }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ふりがな",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFuriganaEnabled) Color.White else Color.White.copy(alpha = 0.65f)
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    // Toggle Translate [あA]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(if (isTranslateModeEnabled) PrimaryCrimson else Color.Transparent)
                            .clickable {
                                isTranslateModeEnabled = !isTranslateModeEnabled
                                if (isTranslateModeEnabled) {
                                    // Expand all sentences by default for convenient inline reading
                                    sentences.forEach { s -> expandedSentences[s.id] = true }
                                } else {
                                    expandedSentences.clear()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "あA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTranslateModeEnabled) Color.White else Color.White.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Terjemah",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isTranslateModeEnabled) Color.White else Color.White.copy(alpha = 0.65f)
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    // Font Stepper (A- / A+)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "A-",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentFontSizeSp > 16f) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .clickable(enabled = currentFontSizeSp > 16f) {
                                    currentFontSizeSp = Math.max(16f, currentFontSizeSp - 2f)
                                }
                                .padding(4.dp)
                        )
                        Text(
                            text = "A+",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentFontSizeSp < 32f) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .clickable(enabled = currentFontSizeSp < 32f) {
                                    currentFontSizeSp = Math.min(32f, currentFontSizeSp + 2f)
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Word & Grammar Detail (preserves word inspection)
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
                            contentDescription = "Audio",
                            tint = if (isAudioPlaying) Color.White else PrimaryCrimson,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Meaning Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CanvasSecondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ARTI UTAMA (INDONESIAN)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCrimson,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = token?.meaning ?: sentence?.translatedText ?: "Memuat penjelasan kosakata...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Sentence Context Translation
                if (sentence != null && sentence.translatedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Konteks Kalimat:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = sentence.translatedText,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Grammar Analysis Section
                if (grammarList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ANALISIS TATA BAHASA & POLA KALIMAT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCrimson,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    grammarList.forEach { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.pattern,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCrimson
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.explanation,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Kanji Breakdown Info
                if (surfaceText.any { JapaneseMorphologyEngine.isKanji(it) }) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val firstKanji = surfaceText.firstOrNull { JapaneseMorphologyEngine.isKanji(it) }?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CanvasSecondary)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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
                                    text = "Tersimpan otomatis ke dek flashcard",
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
