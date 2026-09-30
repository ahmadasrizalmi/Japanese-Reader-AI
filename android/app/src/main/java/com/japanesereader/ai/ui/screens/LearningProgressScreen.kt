package com.japanesereader.ai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesereader.ai.data.local.entity.ArticleEntity
import com.japanesereader.ai.data.local.entity.VocabularyEntity
import com.japanesereader.ai.ui.theme.*

@Composable
fun LearningProgressScreen(
    articles: List<ArticleEntity>,
    vocabularies: List<VocabularyEntity>,
    currentlyPlayingText: String?,
    onToggleAudio: (text: String) -> Unit,
    onUpdateVocabulary: (VocabularyEntity) -> Unit,
    onDeleteVocabulary: (id: String) -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Semua") }
    var reviewVocab: VocabularyEntity? by remember { mutableStateOf(null) }

    // 100% Dynamic stats from actual Room database
    val totalWords = vocabularies.size
    val hardWords = vocabularies.filter { it.reviewCount >= 3 || it.masteryStatus == 0 }
    val learningWords = vocabularies.filter { it.masteryStatus == 1 }
    val masteredWords = vocabularies.filter { it.masteryStatus == 2 }

    val totalCharsRead = articles.sumOf { it.rawText.length }
    val estimatedMinutes = if (totalCharsRead > 0) Math.max(1, totalCharsRead / 120) else 0

    // Dynamic JLPT distribution from user's actual database
    val jlptMap = remember(vocabularies) {
        val map = mutableMapOf("N5" to 0, "N4" to 0, "N3" to 0, "N2" to 0, "N1" to 0)
        vocabularies.forEach { v ->
            val lvl = v.jlptLevel.uppercase()
            if (map.containsKey(lvl)) {
                map[lvl] = map[lvl]!! + 1
            } else {
                map["N5"] = map["N5"]!! + 1
            }
        }
        map
    }

    val filteredList = vocabularies.filter {
        when (selectedFilter) {
            "Perlu Pendalaman" -> it.reviewCount >= 3 || it.masteryStatus == 0
            "Sedang Dipelajari" -> it.masteryStatus == 1
            "Dikuasai" -> it.masteryStatus == 2
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Analytics Summary Section
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "ANALISIS KEMAJUAN BELAJAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCrimson
                        )
                        Text(
                            text = "Statistik Riil Kosakata & Atensi",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(CrimsonSurface)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$totalWords Kata Disimpan",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCrimson
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3 Mini Metric blocks
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CanvasSecondary)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Waktu Baca", fontSize = 10.sp, color = TextSecondary)
                            Text("$estimatedMinutes mnt", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HighlightYellow.copy(alpha = 0.6f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Dipelajari", fontSize = 10.sp, color = TextPrimary)
                            Text("${learningWords.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HighlightMint.copy(alpha = 0.6f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Dikuasai", fontSize = 10.sp, color = TextPrimary)
                            Text("${masteredWords.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // JLPT Distribution Row
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sebaran Level JLPT:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("N5", "N4", "N3", "N2", "N1").forEach { lvl ->
                            val count = jlptMap[lvl] ?: 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CanvasSecondary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("$lvl: $count", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Semua" to totalWords,
                "Perlu Pendalaman" to hardWords.size,
                "Sedang Dipelajari" to learningWords.size,
                "Dikuasai" to masteredWords.size
            ).forEach { (filterTitle, count) ->
                val isSelected = selectedFilter == filterTitle
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filterTitle },
                    label = {
                        Text("$filterTitle ($count)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCrimson,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(100)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Vocabulary List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Kosong",
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Belum ada kosakata di filter ini.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Sentuh kata apa saja saat membaca artikel untuk auto-save.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { vocab ->
                    val isPlaying = currentlyPlayingText == vocab.kanji
                    val isHard = vocab.reviewCount >= 3 || vocab.masteryStatus == 0

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Toggle Play/Stop Audio Button with color indicator
                                IconButton(
                                    onClick = { onToggleAudio(vocab.kanji) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isPlaying) PrimaryCrimson else CanvasSecondary)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
                                        contentDescription = if (isPlaying) "Stop" else "Play",
                                        tint = if (isPlaying) Color.White else PrimaryCrimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = vocab.kanji,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Serif,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = vocab.reading,
                                            fontSize = 12.sp,
                                            color = PrimaryCrimson,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = vocab.meaning,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100))
                                            .background(if (isHard) CrimsonSurface else HighlightMint)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isHard) "Sulit (${vocab.reviewCount}x)" else "Dikuasai",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHard) PrimaryCrimson else Color(0xFF1B5E20)
                                        )
                                    }
                                    Text(
                                        text = vocab.jlptLevel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                }

                                IconButton(
                                    onClick = { reviewVocab = vocab },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RateReview,
                                        contentDescription = "Review",
                                        tint = PrimaryCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        onDeleteVocabulary(vocab.id)
                                        Toast.makeText(context, "Kosakata dihapus.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Review Dialog
    reviewVocab?.let { v ->
        AlertDialog(
            onDismissRequest = { reviewVocab = null },
            title = {
                Text(
                    text = "Tinjauan Kosakata (SRS)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = v.kanji,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = TextPrimary
                    )
                    Text(
                        text = v.reading,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryCrimson
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = v.meaning,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Seberapa mudah Anda mengingat kata ini?", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                onUpdateVocabulary(v.copy(reviewCount = v.reviewCount + 1, masteryStatus = 0))
                                reviewVocab = null
                                Toast.makeText(context, "Ditandai: Perlu Latihan!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CanvasSecondary, contentColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ulangi", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                onUpdateVocabulary(v.copy(reviewCount = v.reviewCount + 1, masteryStatus = 1))
                                reviewVocab = null
                                Toast.makeText(context, "Ditandai: Sedang Dipelajari!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonSurface, contentColor = PrimaryCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bagus", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                onUpdateVocabulary(v.copy(reviewCount = v.reviewCount + 1, masteryStatus = 2))
                                reviewVocab = null
                                Toast.makeText(context, "Ditandai: Dikuasai!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mudah", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
