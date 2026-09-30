package com.japanesereader.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.VolumeUp
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
import com.japanesereader.ai.data.local.entity.VocabularyEntity
import com.japanesereader.ai.ui.theme.*

@Composable
fun VocabularyScreen(
    vocabularies: List<VocabularyEntity>,
    onUpdateVocabulary: (VocabularyEntity) -> Unit,
    onDeleteVocabulary: (id: String) -> Unit,
    onPlayAudio: (text: String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("Semua") }
    var reviewVocab: VocabularyEntity? by remember { mutableStateOf(null) }

    val filters = listOf("Semua", "Baru", "Dipelajari", "Dikuasai")

    val filteredList = vocabularies.filter {
        when (selectedFilter) {
            "Baru" -> it.masteryStatus == 0
            "Dipelajari" -> it.masteryStatus == 1
            "Dikuasai" -> it.masteryStatus == 2
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("DEK KOSAKATA SRS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Text("Spaced Repetition System", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(CrimsonSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("${vocabularies.size} Kata", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filters
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
                    onClick = { selectedFilter = f },
                    label = { Text(f, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCrimson,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(100)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredList, key = { it.id }) { vocab ->
                val masteryLabel = when (vocab.masteryStatus) {
                    0 -> "Baru"
                    1 -> "Dipelajari"
                    else -> "Dikuasai"
                }
                val masteryBg = when (vocab.masteryStatus) {
                    0 -> HighlightBlue
                    1 -> HighlightYellow
                    else -> HighlightMint
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
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
                            IconButton(
                                onClick = { onPlayAudio(vocab.kanji) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CanvasSecondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Audio",
                                    tint = PrimaryCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = vocab.kanji,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = vocab.reading,
                                        fontSize = 11.sp,
                                        color = TextMuted
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
                                        .background(masteryBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = masteryLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryCrimson
                                    )
                                }
                                Text(
                                    text = "${vocab.reviewCount}x tinjauan",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }

                            IconButton(
                                onClick = { reviewVocab = vocab },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RateReview,
                                    contentDescription = "Review",
                                    tint = PrimaryCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteVocabulary(vocab.id) },
                                modifier = Modifier.size(32.dp)
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

                    Text("Pilih tingkat kemudahan mengingat:", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                onUpdateVocabulary(v.copy(reviewCount = v.reviewCount + 1, masteryStatus = 0))
                                reviewVocab = null
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
