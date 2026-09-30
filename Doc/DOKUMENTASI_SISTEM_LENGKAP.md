# Dokumen Spesifikasi Rekayasa, Arsitektur Sistem & Panduan Implementasi Lengkap: Komorebi Reader AI (Cross-Platform Android & Web)

**Versi:** 1.0.0  
**Nama Aplikasi:** Komorebi Reader (Japanese Reader AI)  
**Tumpukan Teknologi:** Kotlin, Jetpack Compose, Room SQLite, TypeScript, Cloudflare Workers, Cloudflare D1/KV, DeepSeek AI Gateway, Tailwind CSS.  
**Tanggal:** 2026-10-01  

---

## 1. Ikhtisar Sistem & Filosofi Desain

**Komorebi Reader (Japanese Reader AI)** adalah platform pembelajaran bahasa Jepang lintas platform yang memadukan keindahan editorial Jepang modern (*Tactile Material 3 & Wabi-Sabi Warmth*) dengan kecerdasan buatan (*AI Morphological Analysis*). 

Sistem ini didesain untuk akselerasi penguasaan bahasa Jepang berbasis *extensive & intensive contextual reading*. Sistem memecah teks Jepang (Kanji, Hiragana, Katakana), menyematkan Furigana fonetis di atas kanji secara proporsional, menganalisis struktur partikel dan tata bahasa (*grammar pattern*), mengklasifikasikan tingkat kesulitan JLPT (N5 hingga N1), menyediakan pelafalan audio fonetis, serta mengelola Spaced Repetition System (SRS) untuk kosakata yang disimpan.

### 1.1. Prinsip Utama Sistem (Core Architecture Pillars)
1. **Zero-Friction Contextual Reading**: Membaca teks otentik Jepang tanpa hambatan kognitif radikal kanji berkat Furigana dinamis (Mode: *Always On*, *Tap to Reveal*, *Off*).
2. **Deterministic & AI Morphological Parsing**: Menggabungkan ketepatan aturan morfologi leksikal deterministik dengan kekuatan LLM DeepSeek (BYOK - *Bring Your Own Key*) untuk penjelasan nuansa konteks bahasa alami.
3. **Resilient Offline-First Sync Strategy**: Sinkronisasi dua arah (*bidirectional delta sync*) antara klien Android lokal (SQLite Room) dan Cloudflare Edge (Workers & D1/KV Storage) yang memastikan aplikasi berfungsi optimal saat luring maupun daring.
4. **Behavioral Attention Tracking**: Algoritma pelacakan atensi pembaca (*sentence inspection threshold*) yang secara otomatis menandai kalimat yang memerlukan studi mendalam ($C_{\text{inspect}} \ge 3$) dan menjadwalkan kosakata ke dalam antrean SRS SuperMemo-2.

---

## 2. Arsitektur Komponen & Topologi Cloudflare Edge

```
+-----------------------------------------------------------------------------------+
|                                 CLIENT PLATFORMS                                  |
|   [ Android Native: Jetpack Compose ]       [ Web Client: Cloudflare Pages / SPA ]|
+-----------------------------------------------------------------------------------+
                                        │
                                        ▼ HTTPS / REST JSON
+-----------------------------------------------------------------------------------+
|                        CLOUDFLARE EDGE WORKER (HONO.JS)                           |
|  - Rate Limiting, CORS, BYOK API Key Middleware                                   |
|  - Full RESTful CRUD Handlers (Articles, Sentences, Vocabularies, Logs)           |
|  - Bidirectional Delta Synchronization Endpoint (/api/v1/sync)                    |
+-----------------------------------------------------------------------------------+
            │                                              │
            ▼                                              ▼
  [ DeepSeek LLM Gateway ]                     [ Edge Persistence Layer ]
  - Tokenization & Furigana Tagging            - Cloudflare D1 Relational Engine
  - Context Translation & Grammar Points       - Cloudflare KV Distributed Cache
  - Deterministic Lexicon Engine (Fallback)    - Cloudflare R2 Audio Blob Store
```

---

## 3. Skema Basis Data Relasional Terpadu (Database Schema)

Skema database dirancang identik dan konsisten antara **Cloudflare D1 (Edge SQL)** dan **SQLite Room (Android Native)** dengan relasi `ON DELETE CASCADE`:

### 3.1. Tabel `articles`
Menyimpan metadata dan konten mentah bahan bacaan pengguna.
```sql
CREATE TABLE articles (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL, -- 'Percakapan', 'Buku & Artikel', 'Lirik Lagu', 'Menu & Tempat', 'Umum'
    raw_text TEXT NOT NULL,
    difficulty_level TEXT NOT NULL, -- 'N5', 'N4', 'N3', 'N2', 'N1'
    kanji_ratio REAL NOT NULL DEFAULT 0.0,
    created_at INTEGER NOT NULL,    -- Unix timestamp dalam ms
    updated_at INTEGER NOT NULL     -- Unix timestamp dalam ms untuk delta sync
);
```

### 3.2. Tabel `sentences`
Hasil segmentasi teks menjadi unit kalimat terstruktur beserta status belajarnya.
```sql
CREATE TABLE sentences (
    id TEXT PRIMARY KEY NOT NULL,
    article_id TEXT NOT NULL,
    original_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    furigana_payload TEXT NOT NULL, -- JSON string berisi token, kanji, furigana, romaji, pos, jlpt
    grammar_analysis TEXT NOT NULL, -- JSON string berisi pola tata bahasa dan eksplanasi
    sequence_order INTEGER NOT NULL,
    inspection_count INTEGER NOT NULL DEFAULT 0,
    audio_play_count INTEGER NOT NULL DEFAULT 0,
    needs_deep_study INTEGER NOT NULL DEFAULT 0, -- 0 atau 1
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);
```

### 3.3. Tabel `vocabularies`
Repositori kata hasil ekstraksi morfologi per kalimat untuk Spaced Repetition System (SRS).
```sql
CREATE TABLE vocabularies (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    sentence_id TEXT,
    kanji TEXT NOT NULL,
    reading TEXT NOT NULL,
    meaning TEXT NOT NULL,
    part_of_speech TEXT NOT NULL,
    jlpt_level TEXT NOT NULL,       -- 'N5', 'N4', 'N3', 'N2', 'N1'
    mastery_status INTEGER NOT NULL DEFAULT 0, -- 0: Baru, 1: Dipelajari, 2: Dikuasai
    review_count INTEGER NOT NULL DEFAULT 0,
    next_review_at INTEGER,         -- Unix timestamp ms untuk jadwal SRS
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### 3.4. Tabel `study_logs`
Pencatatan telemetri aktivitas pengguna guna mendukung analitik belajar.
```sql
CREATE TABLE study_logs (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    sentence_id TEXT NOT NULL,
    action_type TEXT NOT NULL, -- 'INSPECT', 'AUDIO_PLAY', 'TRANSLATE_SELECT'
    timestamp INTEGER NOT NULL
);
```

### 3.5. Tabel `user_settings`
Konfigurasi preferensi baca pengguna, model suara, dan kredensial BYOK.
```sql
CREATE TABLE user_settings (
    user_id TEXT PRIMARY KEY NOT NULL,
    target_language TEXT NOT NULL DEFAULT 'id-ID',
    deepseek_tone TEXT NOT NULL DEFAULT 'colloquial', -- 'colloquial', 'formal', 'literal'
    tts_speed REAL NOT NULL DEFAULT 1.0,
    voice_model TEXT NOT NULL DEFAULT 'ja_JP-hira-medium',
    furigana_mode TEXT NOT NULL DEFAULT 'always',     -- 'always', 'tap', 'off'
    dynamic_pastel_highlights INTEGER NOT NULL DEFAULT 1,
    kanji_font_style TEXT NOT NULL DEFAULT 'mincho',  -- 'mincho', 'gothic'
    deepseek_api_key TEXT,
    updated_at INTEGER NOT NULL
);
```

### 3.6. Tabel `audio_cache`
Registri deterministik untuk pemetaan hash teks audio ke penyimpanan CDN R2.
```sql
CREATE TABLE audio_cache (
    text_hash TEXT PRIMARY KEY NOT NULL, -- SHA-256(text + voice_model + speed)
    voice_model TEXT NOT NULL,
    speed REAL NOT NULL,
    r2_url TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
```

---

## 4. Kontrak Antarmuka Komunikasi API (API Contract Specifications)

Semua rute bernaung di bawah prefix `/api/v1`.

### 4.1. Analisis Morfologi Teks (`POST /api/v1/analyze`)
- **Tujuan**: Menerima teks mentah dan mengembalikan struktur terpecah lengkap dengan furigana, terjemahan, token leksikal, dan poin tata bahasa.
- **Request Body**:
  ```json
  {
    "text": "東京の春は花が咲いて、とてもきれいです。",
    "apiKey": "sk-optional-byok-key"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "difficulty_level": "N4",
    "kanji_ratio": 0.35,
    "sentences": [
      {
        "sequence_order": 1,
        "original_text": "東京の春は花が咲いて、とてもきれいです。",
        "translated_text": "Musim semi di Tokyo bunga-bunga bermekaran, dan sangat indah.",
        "tokens": [
          {"surface": "東京", "reading": "とうきょう", "romaji": "toukyou", "pos": "noun", "jlpt": "N5", "meaning": "Tokyo"},
          {"surface": "の", "reading": "の", "romaji": "no", "pos": "particle", "jlpt": "N5", "meaning": "Partikel kepemilikan"},
          {"surface": "春", "reading": "はる", "romaji": "haru", "pos": "noun", "jlpt": "N5", "meaning": "Musim semi"},
          {"surface": "は", "reading": "は", "romaji": "wa", "pos": "particle", "jlpt": "N5", "meaning": "Partikel topik"},
          {"surface": "花", "reading": "はな", "romaji": "hana", "pos": "noun", "jlpt": "N5", "meaning": "Bunga"},
          {"surface": "が", "reading": "が", "romaji": "ga", "pos": "particle", "jlpt": "N5", "meaning": "Partikel subjek"},
          {"surface": "咲いて", "reading": "さいて", "romaji": "saite", "pos": "verb", "jlpt": "N4", "meaning": "Mekar (te-form)", "base_form": "咲く"},
          {"surface": "、", "reading": "", "romaji": "", "pos": "punct", "jlpt": "-"},
          {"surface": "とても", "reading": "とても", "romaji": "totemo", "pos": "adverb", "jlpt": "N5", "meaning": "Sangat"},
          {"surface": "きれい", "reading": "きれい", "romaji": "kirei", "pos": "na-adj", "jlpt": "N5", "meaning": "Indah / Bersih"},
          {"surface": "です", "reading": "です", "romaji": "desu", "pos": "copula", "jlpt": "N5", "meaning": "Adalah (sopan)"},
          {"surface": "。", "reading": "", "romaji": "", "pos": "punct", "jlpt": "-"}
        ],
        "grammar_points": [
          {
            "pattern": "〜て (te-form)",
            "explanation": "Bentuk konjungtif penghubung klausa yang mengindikasikan urutan kegiatan atau alasan sebab-akibat."
          }
        ]
      }
    ]
  }
  ```

### 4.2. CRUD Artikel (`/api/v1/articles`)
- `GET /api/v1/articles?category=...&search=...`: Mendapatkan daftar artikel pengguna beserta jumlah kata dan level JLPT.
- `POST /api/v1/articles`: Menambahkan bacaan baru (bisa sekaligus diproses parsing atau diurai otomatis).
- `GET /api/v1/articles/:id`: Mengambil artikel lengkap dengan relasi `sentences` dan anotasi furigana.
- `PUT /api/v1/articles/:id`: Memperbarui judul, kategori, atau teks artikel.
- `DELETE /api/v1/articles/:id`: Menghapus artikel bersangkutan secara kaskade (`CASCADE`).

### 4.3. CRUD Kosakata / Flashcard SRS (`/api/v1/vocabularies`)
- `GET /api/v1/vocabularies?status=...&jlpt=...`: Mengambil koleksi kosakata tersimpan beserta status SRS.
- `POST /api/v1/vocabularies`: Menambahkan kosakata baru dari kalimat yang sedang dibaca.
- `PUT /api/v1/vocabularies/:id`: Memperbarui data kata atau mencatat hasil tinjauan SRS (*review rating 0-5*).
- `DELETE /api/v1/vocabularies/:id`: Menghapus kosakata dari dek belajar.

### 4.4. Telemetri & Analisis Interaksi (`POST /api/v1/analytics/inspect`)
- Mencatat aksi tap inspeksi pembaca pada kalimat. Jika `inspection_count >= 3`, maka `needs_deep_study` diaktifkan.
- Mengembalikan status terkini jumlah inspeksi.

### 4.5. Ringkasan Statistik Belajar (`GET /api/v1/analytics/stats`)
- Mengembalikan agregasi durasi membaca mingguan, jumlah kalimat yang dikuasai, total kosakata tersimpan, dan streak harian.

### 4.6. Sinkronisasi Data Lintas Platform (`POST /api/v1/sync` & `GET /api/v1/sync`)
- Mengizinkan klien Android dan Web mengunggah modifikasi lokal berbasis `updated_at` (Last-Write-Wins) serta menarik rekaman terbaru dari Edge Database Cloudflare.

---

## 5. Pemetaan Desain UI/UX ke Jetpack Compose & Web

Sistem desain diimplementasikan berpedoman pada `Doc/UI UX Design/komorebi_reader/DESIGN.md`:

| Layar / Komponen UI UX | File Acuan HTML | Padanan Jetpack Compose (Android) | Padanan Web Component |
| :--- | :--- | :--- | :--- |
| **Splash & Onboarding** | `splash_onboarding_screen/code.html` | `SplashScreen.kt` (Wavy pattern SVG canvas, Crimson hero, pill buttons) | `SplashScreen.tsx` (CSS wavy background, Tailwind pill buttons) |
| **Quick Read (Instant Parser)** | `quick_read_instant_parser/code.html` | `QuickReadScreen.kt` (Text scratchpad, inspiration chips, instant ruby parser card, lexical token pill slider) | `QuickReadView.tsx` (Interactive scratchpad, instant token breakdown slider) |
| **Koleksi Catatan (Library)** | `koleksi_catatan_komorebi/code.html` | `CollectionScreen.kt` (Category filter pills, 2-row horizontal snapping grid card deck, JLPT badges) | `CollectionView.tsx` (Filter pills, responsive card grid with preview) |
| **Reader Studio & Selection** | `interactive_reader_analisis_pop_up/code.html` & `text_selection_translation_pop_up/code.html` | `ReaderScreen.kt` & `SentenceAnalysisBottomSheet.kt` (Ruby text renderer, pastel highlights, floating action toolbar, grammar breakdown modal) | `ReaderView.tsx` (HTML5 `<ruby>` & `<rt>` renderer, pastel highlight toggle, floating selection popover) |
| **Analisis Belajar** | `analisis_belajar_komorebi/code.html` | `AnalyticsScreen.kt` (Dual metric cards: Waktu Baca & Dikuasai, Weekly Bar Chart, JLPT Distribution) | `AnalyticsView.tsx` (SVG Weekly bar chart, metric statistics, retention progress) |
| **Settings & BYOK** | `settings_configuration_screen/code.html` | `SettingsScreen.kt` (Profile header, Tone picker, TTS speed slider, Furigana segmented control, DeepSeek BYOK input) | `SettingsView.tsx` (BYOK configuration, Furigana mode toggle, audio speed controls) |

---

## 6. Prosedur Kompilasi & Build Release (Android APK & AAB)

Proyek Android dikonfigurasi dengan Gradle Wrapper versi modern (Gradle 8.5+, Android Gradle Plugin 8.3+, Kotlin 1.9.22):
- **Nama Aplikasi**: `Komorebi Reader`
- **Application ID**: `com.japanesereader.ai`
- **Versi Aplikasi**: `v1.0.0` (Version Code: `100`)
- **Direktori Output Release**: `releases/`
  - APK: `releases/komorebi-reader-v1.0.0.apk`
  - AAB: `releases/komorebi-reader-v1.0.0.aab`
