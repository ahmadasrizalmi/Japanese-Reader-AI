package com.japanesereader.ai

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesereader.ai.data.local.KomorebiDatabase
import com.japanesereader.ai.data.local.PreferencesManager
import com.japanesereader.ai.data.local.entity.SentenceEntity
import com.japanesereader.ai.data.repository.KomorebiRepository
import com.japanesereader.ai.ui.screens.*
import com.japanesereader.ai.ui.theme.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var database: KomorebiDatabase
    private lateinit var prefs: PreferencesManager
    private lateinit var repository: KomorebiRepository
    private var tts: TextToSpeech? = null

    // State for currently playing text
    val currentlyPlayingText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(applicationContext)
        database = KomorebiDatabase.getInstance(applicationContext)
        repository = KomorebiRepository(database, prefs)

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.JAPANESE
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        currentlyPlayingText.value = null
                    }
                    override fun onError(utteranceId: String?) {
                        currentlyPlayingText.value = null
                    }
                })
            }
        }

        setContent {
            KomorebiTheme {
                KomorebiApp(
                    repository = repository,
                    currentlyPlayingText = currentlyPlayingText.value,
                    onToggleAudio = { text ->
                        if (currentlyPlayingText.value == text) {
                            tts?.stop()
                            currentlyPlayingText.value = null
                        } else {
                            tts?.stop()
                            currentlyPlayingText.value = text
                            val speed = repository.prefs?.ttsSpeed ?: 1.0f
                            tts?.setSpeechRate(speed)
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_${System.currentTimeMillis()}")
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomorebiApp(
    repository: KomorebiRepository,
    currentlyPlayingText: String?,
    onToggleAudio: (text: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen state: persistent across orientation changes (Portrait <-> Landscape)
    var isReaderOpen by rememberSaveable { mutableStateOf(false) }
    var activeArticleId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddTextBottomSheet by remember { mutableStateOf(false) }

    // 3 Main Horizontal Pager tabs: 0: Koleksi, 1: Kemajuan & Kosakata, 2: Pengaturan
    val pagerState = rememberPagerState(pageCount = { 3 })

    val articles by repository.articles.collectAsState(initial = emptyList())
    val vocabularies by repository.vocabularies.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = null)
    val syncStatus by repository.syncStatus.collectAsState()

    val activeArticle = articles.find { it.id == activeArticleId }
    val sentenceFlow = remember(activeArticleId) {
        val id = activeArticleId
        if (id != null) {
            repository.getSentencesForArticle(id)
        } else {
            flowOf(emptyList())
        }
    }
    val activeSentences by sentenceFlow.collectAsState(initial = emptyList())

    // Handle back button when in reader
    BackHandler(enabled = isReaderOpen) {
        isReaderOpen = false
    }

    Scaffold(
        topBar = {
            if (!isReaderOpen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CrimsonSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("読", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryCrimson)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("KOMOREBI", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = PrimaryCrimson)
                                Text("Japanese Reader AI", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Menyinkronkan data...", Toast.LENGTH_SHORT).show()
                                    repository.syncNow()
                                    Toast.makeText(context, "Status Cloudflare: ${repository.syncStatus.value}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonSurface,
                                contentColor = PrimaryCrimson
                            ),
                            shape = RoundedCornerShape(100),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDone, contentDescription = "Sync", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(syncStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasSurface)
                )
            }
        },
        bottomBar = {
            if (!isReaderOpen) {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(68.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Tab 0: Koleksi
                        val isTab0 = pagerState.currentPage == 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(if (isTab0) CrimsonSurface else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CollectionsBookmark,
                                    contentDescription = "Koleksi",
                                    tint = if (isTab0) PrimaryCrimson else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Koleksi",
                                fontSize = 10.sp,
                                fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTab0) PrimaryCrimson else TextMuted
                            )
                        }

                        // Center FAB (+): Add text scratchpad in thumb reach!
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { showAddTextBottomSheet = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryCrimson)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tambah Teks Baru",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Tab 1: Kemajuan & Kosakata (Unified)
                        val isTab1 = pagerState.currentPage == 1
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(if (isTab1) CrimsonSurface else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Insights,
                                    contentDescription = "Kemajuan",
                                    tint = if (isTab1) PrimaryCrimson else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kemajuan",
                                fontSize = 10.sp,
                                fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTab1) PrimaryCrimson else TextMuted
                            )
                        }

                        // Tab 2: Pengaturan
                        val isTab2 = pagerState.currentPage == 2
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(if (isTab2) CrimsonSurface else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Pengaturan",
                                    tint = if (isTab2) PrimaryCrimson else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pengaturan",
                                fontSize = 10.sp,
                                fontWeight = if (isTab2) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTab2) PrimaryCrimson else TextMuted
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CanvasSurface)
        ) {
            if (isReaderOpen && activeArticle != null) {
                // Full Reader Studio Screen
                ReaderScreen(
                    article = activeArticle,
                    sentences = activeSentences,
                    currentlyPlayingText = currentlyPlayingText,
                    onToggleAudio = onToggleAudio,
                    onInspectSentence = { sentId ->
                        coroutineScope.launch {
                            repository.inspectSentence(sentId)
                        }
                    },
                    onAutoSaveVocabulary = { kanji, reading, meaning, sentId ->
                        coroutineScope.launch {
                            repository.addVocabulary(kanji, reading, meaning, "noun", "N4", sentId)
                        }
                    },
                    onBack = { isReaderOpen = false }
                )
            } else {
                // Swipeable HorizontalPager between the 3 main tabs
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> {
                            CollectionScreen(
                                articles = articles,
                                onOpenArticle = { artId ->
                                    activeArticleId = artId
                                    isReaderOpen = true
                                },
                                onAddArticle = { title, category, rawText ->
                                    coroutineScope.launch {
                                        val created = repository.createArticle(title, category, rawText)
                                        activeArticleId = created.id
                                        isReaderOpen = true
                                        Toast.makeText(context, "Bacaan baru berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteArticle = { artId ->
                                    coroutineScope.launch {
                                        repository.deleteArticle(artId)
                                        Toast.makeText(context, "Bacaan berhasil dihapus.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        1 -> {
                            LearningProgressScreen(
                                articles = articles,
                                vocabularies = vocabularies,
                                currentlyPlayingText = currentlyPlayingText,
                                onToggleAudio = onToggleAudio,
                                onUpdateVocabulary = { vocab ->
                                    coroutineScope.launch {
                                        repository.updateVocabulary(vocab)
                                    }
                                },
                                onDeleteVocabulary = { id ->
                                    coroutineScope.launch {
                                        repository.deleteVocabulary(id)
                                    }
                                }
                            )
                        }
                        2 -> {
                            SettingsScreen(
                                settings = settings,
                                totalVocabCount = vocabularies.size,
                                syncStatus = syncStatus,
                                onUpdateSettings = { newSettings ->
                                    coroutineScope.launch {
                                        repository.updateSettings(newSettings)
                                    }
                                },
                                onSyncNow = {
                                    coroutineScope.launch {
                                        repository.syncNow()
                                    }
                                },
                                onPlayAudioPreview = { text ->
                                    onToggleAudio(text)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Thumb-reach Bottom Sheet Modal for Adding Text
    if (showAddTextBottomSheet) {
        var inputTitle by remember { mutableStateOf("") }
        var inputCategory by remember { mutableStateOf("Percakapan") }
        var inputRawText by remember { mutableStateOf("") }
        var isAnalyzingText by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showAddTextBottomSheet = false },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tambah Bahan Bacaan Baru",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = { showAddTextBottomSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                OutlinedTextField(
                    value = inputTitle,
                    onValueChange = { inputTitle = it },
                    label = { Text("Judul Catatan (Opsional)") },
                    placeholder = { Text("Misal: Cerita Kafe Tokyo") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputRawText,
                    onValueChange = { inputRawText = it },
                    label = { Text("Teks Bahasa Jepang") },
                    placeholder = { Text("Tempel atau ketik teks Jepang di sini...") },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (inputRawText.isNotBlank()) {
                            isAnalyzingText = true
                            coroutineScope.launch {
                                try {
                                    val finalTitle = inputTitle.ifBlank {
                                        if (inputRawText.length > 20) inputRawText.substring(0, 20) + "..." else inputRawText
                                    }
                                    val created = repository.createArticle(finalTitle, inputCategory, inputRawText)
                                    activeArticleId = created.id
                                    showAddTextBottomSheet = false
                                    isReaderOpen = true
                                    Toast.makeText(context, "Bacaan siap dibaca!", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isAnalyzingText = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Teks Jepang tidak boleh kosong.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isAnalyzingText,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCrimson),
                    shape = RoundedCornerShape(100),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isAnalyzingText) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menganalisis Teks...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = "Analisis")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analisis & Baca Sekarang", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
