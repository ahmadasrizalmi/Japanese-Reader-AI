package com.japanesereader.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesereader.ai.data.local.entity.ArticleEntity
import com.japanesereader.ai.ui.theme.*

@Composable
fun CollectionScreen(
    articles: List<ArticleEntity>,
    onOpenArticle: (articleId: String) -> Unit,
    onAddArticle: (title: String, category: String, rawText: String) -> Unit,
    onDeleteArticle: (articleId: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Semua") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf("Semua", "Percakapan", "Buku & Artikel", "Lirik Lagu", "Menu & Tempat")

    val filteredArticles = articles.filter {
        val matchCategory = selectedCategory == "Semua" || it.category.equals(selectedCategory, ignoreCase = true)
        val matchSearch = searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.rawText.contains(searchQuery, ignoreCase = true)
        matchCategory && matchSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Deck
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                shape = RoundedCornerShape(100),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Catatan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCrimson),
                shape = RoundedCornerShape(100)
            ) {
                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Tempel", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tempel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari judul atau isi bacaan...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Cari", tint = TextMuted)
            },
            shape = RoundedCornerShape(100),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = PrimaryCrimson,
                unfocusedBorderColor = SurfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCrimson,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(100)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Section Title
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Arsip Catatan", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("${filteredArticles.size} bacaan", fontSize = 11.sp, color = TextMuted)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Articles List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredArticles, key = { it.id }) { article ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArticle(article.id) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(HighlightYellow)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "JLPT ${article.difficultyLevel}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCrimson
                                )
                            }
                            Text(
                                text = "${article.rawText.length} huruf",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = article.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CanvasSecondary)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = article.rawText,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = article.category,
                                fontSize = 11.sp,
                                color = TextMuted
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { onDeleteArticle(article.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Button(
                                    onClick = { onOpenArticle(article.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CrimsonSurface,
                                        contentColor = PrimaryCrimson
                                    ),
                                    shape = RoundedCornerShape(100),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Buka", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Buka",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Article Dialog
    if (showAddDialog) {
        var inputTitle by remember { mutableStateOf("") }
        var inputCat by remember { mutableStateOf("Percakapan") }
        var inputRaw by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Bacaan Baru", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Judul Catatan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputCat,
                        onValueChange = { inputCat = it },
                        label = { Text("Kategori (Percakapan / Buku / Lirik / Menu)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputRaw,
                        onValueChange = { inputRaw = it },
                        label = { Text("Teks Jepang") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTitle.isNotBlank() && inputRaw.isNotBlank()) {
                            onAddArticle(inputTitle, inputCat, inputRaw)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }
}
