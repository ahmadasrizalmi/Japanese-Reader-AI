package com.japanesereader.ai.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesereader.ai.data.local.entity.SentenceEntity
import com.japanesereader.ai.data.local.entity.VocabularyEntity
import com.japanesereader.ai.ui.theme.*
import com.japanesereader.ai.util.SrsEngine

private fun matchesFilter(filter: String, vocab: VocabularyEntity): Boolean {
    return when (filter) {
        "Perlu Dipelajari" -> vocab.masteryStatus == 0 || vocab.reviewCount == 0
        "Sedang Diulang" -> vocab.masteryStatus == 1
        "Dikuasai" -> vocab.masteryStatus == 2
        else -> true
    }
}

@Composable
fun FlashcardScreen(
    vocabularies: List<VocabularyEntity>,
    sentences: List<SentenceEntity>,
    kanjiFontStyle: String = "mincho",
    currentlyPlayingText: String?,
    onPlayAudio: (text: String) -> Unit,
    onUpdateVocabulary: (VocabularyEntity) -> Unit
) {
    val activeFontFamily = if (kanjiFontStyle == "gothic") FontFamily.SansSerif else FontFamily.Serif
    var selectedFilter by remember { mutableStateOf("Semua") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val filters = listOf("Semua", "Perlu Dipelajari", "Sedang Diulang", "Dikuasai")

    val filteredList = remember(vocabularies, selectedFilter) {
        vocabularies.filter {
            when (selectedFilter) {
                "Perlu Dipelajari" -> it.masteryStatus == 0 || it.reviewCount == 0
                "Sedang Diulang" -> it.masteryStatus == 1
                "Dikuasai" -> it.masteryStatus == 2
                else -> true
            }
        }
    }

    // Clamp current index when filter changes
    LaunchedEffect(filteredList.size) {
        if (currentIndex >= filteredList.size) {
            currentIndex = if (filteredList.isNotEmpty()) filteredList.size - 1 else 0
        }
        isFlipped = false
    }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "flashcard_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasSurface)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "DEK KOSAKATA SRS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Latihan Flashcard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (filteredList.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(CrimsonSurface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${filteredList.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCrimson
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { f ->
                val isSelected = selectedFilter == f
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedFilter = f
                        currentIndex = 0
                        isFlipped = false
                    },
                    label = { Text(f, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCrimson,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(100)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredList.isEmpty()) {
            // Empty State
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceCard)
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Belum ada kartu di kategori ini",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ketuk kosakata saat membaca di Reader Studio untuk menyimpannya ke dek flashcard ini secara otomatis.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            val safeIndex = currentIndex.coerceIn(0, filteredList.lastIndex)
            val currentVocab = filteredList[safeIndex]
            val isAudioPlaying = currentlyPlayingText == currentVocab.kanji

            // Find Context Sentence (Original sentence from the article!)
            val contextSentence = remember(currentVocab, sentences) {
                sentences.find { it.id == currentVocab.sentenceId }
                    ?: sentences.find { it.originalText.contains(currentVocab.kanji) }
            }

            // 3D Flip Card Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceCard)
                    .clickable {
                        isFlipped = !isFlipped
                    }
                    .padding(22.dp)
            ) {
                if (rotation <= 90f) {
                    // ==========================================
                    // SISI DEPAN (FRONT - CHALLENGE / RECALL)
                    // ==========================================
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top info row
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(CrimsonSurface)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "JLPT ${currentVocab.jlptLevel.ifBlank { "N5" }}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCrimson
                                )
                            }

                            IconButton(
                                onClick = { onPlayAudio(currentVocab.kanji) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isAudioPlaying) PrimaryCrimson else CanvasSecondary)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Audio",
                                    tint = if (isAudioPlaying) Color.White else PrimaryCrimson,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Center: Big Kanji
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentVocab.kanji,
                                fontSize = if (currentVocab.kanji.length <= 4) 48.sp else 34.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = activeFontFamily,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tinjauan: ${currentVocab.reviewCount}x",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        // Bottom: Tap hint
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ketuk kartu untuk melihat arti & konteks",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // ==========================================
                    // SISI BELAKANG (BACK - DETAILS & REINFORCEMENT)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f },
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Top Row: Reading, Romaji & POS
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = currentVocab.reading,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCrimson
                                    )
                                    Text(
                                        text = "${currentVocab.partOfSpeech} • ${currentVocab.kanji}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                IconButton(
                                    onClick = { onPlayAudio(currentVocab.kanji) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isAudioPlaying) PrimaryCrimson else CanvasSecondary)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Audio",
                                        tint = if (isAudioPlaying) Color.White else PrimaryCrimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Meaning Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CanvasSecondary)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "ARTI (INDONESIA)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCrimson,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentVocab.meaning,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Diamond Feature: Context Sentence from Reading
                            if (contextSentence != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = HighlightYellow.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "📝 Konteks Bacaan Asli:",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryCrimson
                                            )
                                            IconButton(
                                                onClick = { onPlayAudio(contextSentence.originalText) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Audio Kalimat",
                                                    tint = PrimaryCrimson,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = contextSentence.originalText,
                                            fontSize = 12.sp,
                                            fontFamily = activeFontFamily,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (contextSentence.translatedText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = contextSentence.translatedText,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom SRS Memory Assessment Buttons: [🔴 Lupa] & [🟢 Ingat]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            // Lupa Button (Quality 1)
                            Button(
                                onClick = {
                                    val result = SrsEngine.calculateNextReview(
                                        currentReviews = currentVocab.reviewCount,
                                        quality = 1,
                                        needsDeepStudy = contextSentence?.needsDeepStudy ?: false
                                    )
                                    val updated = currentVocab.copy(
                                        masteryStatus = result.masteryStatus,
                                        reviewCount = result.reviewCount,
                                        nextReviewAt = result.nextReviewAt,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    onUpdateVocabulary(updated)
                                    isFlipped = false
                                    // If the card stays in this filter, advance; if it was
                                    // removed (mastery changed), the next card shifts into place.
                                    if (matchesFilter(selectedFilter, updated) && currentIndex < filteredList.size - 1) {
                                        currentIndex++
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "🔴 Lupa",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCrimson
                                )
                            }

                            // Ingat Button (Quality 4)
                            Button(
                                onClick = {
                                    val result = SrsEngine.calculateNextReview(
                                        currentReviews = currentVocab.reviewCount,
                                        quality = 4,
                                        needsDeepStudy = contextSentence?.needsDeepStudy ?: false
                                    )
                                    val updated = currentVocab.copy(
                                        masteryStatus = result.masteryStatus,
                                        reviewCount = result.reviewCount,
                                        nextReviewAt = result.nextReviewAt,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    onUpdateVocabulary(updated)
                                    isFlipped = false
                                    // If the card stays in this filter, advance; if it was
                                    // removed (mastery changed), the next card shifts into place.
                                    if (matchesFilter(selectedFilter, updated) && currentIndex < filteredList.size - 1) {
                                        currentIndex++
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(
                                    text = "🟢 Ingat",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card Navigation Bar (Prev / Next buttons)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            isFlipped = false
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (currentIndex > 0) SurfaceCard else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Sebelumnya",
                        tint = if (currentIndex > 0) TextPrimary else TextMuted
                    )
                }

                Text(
                    text = "Ketuk untuk membalik kartu",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                IconButton(
                    onClick = {
                        if (currentIndex < filteredList.size - 1) {
                            currentIndex++
                            isFlipped = false
                        }
                    },
                    enabled = currentIndex < filteredList.size - 1,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (currentIndex < filteredList.size - 1) SurfaceCard else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Berikutnya",
                        tint = if (currentIndex < filteredList.size - 1) TextPrimary else TextMuted
                    )
                }
            }
        }
    }
}
