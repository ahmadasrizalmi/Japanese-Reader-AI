# **Dokumen Spesifikasi Teknis, Arsitektur Sistem & Rekayasa Perangkat Lunak: Japanese Reader AI (Cross-Platform Android & Web)**

Dokumen ini memuat spesifikasi rekayasa perangkat lunak lengkap untuk implementasi platform **Japanese Reader AI** lintas platform (*cross-platform*), berfokus pada arsitektur sistem, alur pemrosesan data, integrasi kecerdasan buatan (*AI Morphological Analysis*), sintesis suara berkinerja tinggi menggunakan **Piper TTS**, skema database relasional, serta standar komunikasi API end-to-end.

# **1\. Ringkasan Eksekutif & Karakteristik Platform**

## **1.1. Ikhtisar Produk**

Japanese Reader AI adalah sistem berbantu kecerdasan buatan untuk akselerasi penguasaan bahasa Jepang berbasis *extensive & intensive contextual reading*. Sistem ini membaca, mem-parsing struktur gramatikal, menyematkan Furigana dan pitch accent secara otomatis, serta menyediakan umpan balik audio fonetis instan berbasis neural text-to-speech sumber terbuka (*open-source*), Piper TTS.

## **1.2. Pilar Desain Sistem & Rekayasa**

> 1. **Zero-Latency Acoustic Synthesis**: Mengeliminasi ketergantungan pada vendor komersial cloud TTS per-karakter yang lambat dan mahal, digantikan oleh model inferensi ringan berbasis ONNX dari **Piper TTS** yang di-cache di edge storage.  
> 2. **Deterministic Morphological Parsing**: Tokenisasi presisi teks Jepang, pemisahan kanji-kana, part-of-speech (POS) tagging, dan pemetaan Furigana kontekstual berbasis model bahasa skala besar (DeepSeek API Gateway).  
> 3. **Behavioral Analytics Driven**: Algoritma pelacakan atensi pembaca (*sentence inspection threshold*) yang secara otomatis memetakan kalimat yang memerlukan pengulangan terjadwal (*Spaced Repetition System* / SRS).  
> 4. **Resilient Offline-First Data Strategy**: Skema data terpadu dan sinkron antara SQLite Room (klien Android) dan Cloudflare D1 (Edge Database).

# **2\. Tumpukan Teknologi Terpadu (Unified Tech Stack)**

| Domain Arsitektur | Klien Android Native | Web Client & Edge Backend | Layanan Inferensi & Storage |
| :---- | :---- | :---- | :---- |
| **Bahasa & Runtime** | Kotlin 1.9+, Java Virtual Machine 17 | TypeScript 5+, Cloudflare Workers (V8 Isolate) | Python / C++ (ONNX Runtime Server) |
| **User Interface** | Jetpack Compose, Material Design 3 | Next.js (App Router) / SvelteKit, TailwindCSS | N/A |
| **Arsitektur Aplikasi** | MVI (Model-View-Intent), Clean Architecture | Server/Client Component Hybrid, Unidirectional Data Flow | Microservice / Serverless Edge |
| **Persistensi Lokal** | Android Room Database (SQLite Engine) | IndexedDB (Client Cache), Cloudflare D1 (Edge SQL) | Cloudflare R2 (Audio Object Store) |
| **Jaringan & I/O** | Retrofit 2, OkHttp 4, Kotlinx Coroutines | Fetch API standar, Hono.js Middleware | HTTP/2 REST Endpoints |
| **Audio Processing** | AndroidX Media3 ExoPlayer | HTML5 Web Audio API, Web Workers | Piper TTS Engine (ONNX Runtime) |
| **Kecerdasan Buatan** | DeepSeek Client SDK via Gateway | DeepSeek API (LLM Parsing) | Piper TTS (Model: ja\_JP-hira-medium) |

# **3\. Arsitektur Komponen & Alur Data Sistem (System Data Flow)**

\+-----------------------------------------------------------------------------------+  
|                                 CLIENT PLATFORMS                                  |  
|   \[ Android Native: Jetpack Compose \]       \[ Web Client: Next.js / TailwindCSS \] |  
\+-----------------------------------------------------------------------------------+  
                                         │  
                                         ▼ HTTPS / REST  
\+-----------------------------------------------------------------------------------+  
|                        CLOUDFLARE EDGE WORKER (HONO.JS)                           |  
|  \- Rate Limiting & Auth Header Validation                                         |  
|  \- Request Normalization & Hash Generation (SHA-256)                              |  
\+-----------------------------------------------------------------------------------+  
             │                                              │  
             ▼                                              ▼  
   \[ DeepSeek LLM Gateway \]                     \[ Cache Verification Layer \]  
   \- Tokenization & Furigana Tagging            \- Query: Cloudflare D1 (audio\_cache)  
   \- Grammar Breakdown & Translation                        │  
                                                ┌───────────┴───────────┐  
                                           Cache HIT                Cache MISS  
                                                │                       │  
                                                ▼                       ▼  
                                       \[ Return Public R2 URL \]  \[ Piper TTS Service \]  
                                                                 \- ONNX Model Inference  
                                                                 \- Generate WAV Stream  
                                                                        │  
                                                                        ▼  
                                                                 \[ Upload to R2 \]  
                                                                        │  
                                                                        ▼  
                                                                 \[ Record in D1 \]

### **Mekanisme Sintesis Audio Piper TTS**

> 1. Setiap permintaan sintesis kalimat dikonstruksi menjadi kunci deterministik: SHA256(text \+ voice\_model \+ speed).  
> 2. Cloudflare D1 diperiksa terlebih dahulu. Jika URL cache ditemukan (*Cache Hit*), URL publik Cloudflare R2 langsung dikembalikan dengan status HTTP 200 tanpa membebani server inferensi.  
> 3. Apabila terjadi *Cache Miss*, Edge Worker memicu POST request ke Piper TTS Gateway.  
> 4. Piper TTS mengeksekusi inferensi berbasis model ONNX bahasa Jepang (ja\_JP-hira-medium atau varian phoneme-to-audio lainnya) dengan parameter length\_scale \= 1.0 / speed.  
> 5. Buffer audio WAV yang dihasilkan langsung di-stream dan disimpan ke Cloudflare R2 Bucket, dicatat pada tabel audio\_cache di Cloudflare D1, lalu URL publiknya dikirim ke klien.

# **4\. Skema Database Relasional Lengkap**

Struktur database dirancang modular dengan integritas referensial penuh (ON DELETE CASCADE) untuk menjamin konsistensi pada SQLite Room (Android) maupun Cloudflare D1 (Edge).

SQL  
\-- 1\. Table: articles  
\-- Menyimpan metadata dan konten mentah bahan bacaan pengguna.  
CREATE TABLE articles (  
    id TEXT PRIMARY KEY NOT NULL,  
    user\_id TEXT NOT NULL,  
    title TEXT NOT NULL,  
    category TEXT NOT NULL,  
    raw\_text TEXT NOT NULL,  
    difficulty\_level TEXT NOT NULL, \-- Klasifikasi JLPT ('N5', 'N4', 'N3', 'N2', 'N1')  
    kanji\_ratio REAL NOT NULL DEFAULT 0.0,  
    created\_at INTEGER NOT NULL     \-- Unix Timestamp dalam milidetik  
);

\-- 2\. Table: sentences  
\-- Hasil segmentasi teks menjadi unit kalimat terstruktur beserta status belajarnya.  
CREATE TABLE sentences (  
    id TEXT PRIMARY KEY NOT NULL,  
    article\_id TEXT NOT NULL,  
    original\_text TEXT NOT NULL,  
    translated\_text TEXT NOT NULL,  
    furigana\_payload TEXT NOT NULL, \-- JSON string berisi token, kanji, furigana, pitch accent  
    grammar\_analysis TEXT NOT NULL, \-- JSON string berisi breakdown partikel dan tenses  
    sequence\_order INTEGER NOT NULL,  
    inspection\_count INTEGER NOT NULL DEFAULT 0,  
    audio\_play\_count INTEGER NOT NULL DEFAULT 0,  
    needs\_deep\_study INTEGER NOT NULL DEFAULT 0, \-- Nilai boolean: 0 atau 1  
    FOREIGN KEY (article\_id) REFERENCES articles(id) ON DELETE CASCADE  
);

\-- 3\. Table: vocabularies  
\-- Repositori kata hasil ekstraksi morfologi per kalimat untuk Spaced Repetition System.  
CREATE TABLE vocabularies (  
    id TEXT PRIMARY KEY NOT NULL,  
    user\_id TEXT NOT NULL,  
    sentence\_id TEXT NOT NULL,  
    kanji TEXT NOT NULL,  
    reading TEXT NOT NULL,  
    meaning TEXT NOT NULL,  
    part\_of\_speech TEXT NOT NULL,  
    jlpt\_level TEXT NOT NULL,  
    mastery\_status INTEGER NOT NULL DEFAULT 0, \-- 0: Baru, 1: Dipelajari, 2: Dikuasai  
    review\_count INTEGER NOT NULL DEFAULT 0,  
    next\_review\_at INTEGER,                    \-- Unix Timestamp untuk SRS  
    FOREIGN KEY (sentence\_id) REFERENCES sentences(id) ON DELETE CASCADE  
);

\-- 4\. Table: study\_logs  
\-- Pencatatan telemetri perilaku pengguna guna mendukung modul analitik.  
CREATE TABLE study\_logs (  
    id TEXT PRIMARY KEY NOT NULL,  
    user\_id TEXT NOT NULL,  
    sentence\_id TEXT NOT NULL,  
    action\_type TEXT NOT NULL, \-- 'INSPECT', 'AUDIO\_PLAY', 'TRANSLATE\_SELECT'  
    timestamp INTEGER NOT NULL,  
    FOREIGN KEY (sentence\_id) REFERENCES sentences(id) ON DELETE CASCADE  
);

\-- 5\. Table: audio\_cache  
\-- Registry deterministik untuk pemetaan hash teks audio ke objek di Cloudflare R2.  
CREATE TABLE audio\_cache (  
    text\_hash TEXT PRIMARY KEY NOT NULL, \-- SHA-256(text \+ voice\_model \+ speed)  
    voice\_model TEXT NOT NULL,          \-- Identifier model Piper (misal: 'ja\_JP-hira-medium')  
    speed REAL NOT NULL,                \-- Parameter playback speed (misal: 1.0, 0.8)  
    r2\_url TEXT NOT NULL,               \-- CDN URL file WAV/MP3 di Cloudflare R2  
    created\_at INTEGER NOT NULL  
);

\-- Indeks Kinerja  
CREATE INDEX idx\_sentences\_article ON sentences(article\_id);  
CREATE INDEX idx\_vocab\_user ON vocabularies(user\_id);  
CREATE INDEX idx\_study\_logs\_sentence ON study\_logs(sentence\_id);  
CREATE INDEX idx\_audio\_cache\_hash ON audio\_cache(text\_hash);

# **5\. Spesifikasi Antarmuka Komunikasi & Kontrak API (API Specifications)**

Base URL: \[https://api.japanesereader.ai/api/v1\](https://api.japanesereader.ai/api/v1)

## **5.1. Analisis Morfologi Teks (POST /analyze)**

Menerima teks bahasa Jepang mentah dan memecahnya menjadi struktur kalimat teranotasi Furigana, hiragana breakdown, arti konteks, serta klasifikasi JLPT.

* **Request Body:**

JSON  
{  
  "text": "東京の春は花が咲いて、とてもきれいです。"  
}

* **Response Body (200 OK):**

JSON  
{  
  "sentences": \[  
    {  
      "sequence\_order": 1,  
      "original\_text": "東京の春は花が咲いて、とてもきれいです。",  
      "translated\_text": "Musim semi di Tokyo bunganya bermekaran, dan sangat indah.",  
      "tokens": \[  
        {"surface": "東京", "reading": "とうきょう", "romaji": "toukyou", "pos": "noun", "jlpt": "N5"},  
        {"surface": "の", "reading": "の", "romaji": "no", "pos": "particle", "jlpt": "N5"},  
        {"surface": "春", "reading": "はる", "romaji": "haru", "pos": "noun", "jlpt": "N5"},  
        {"surface": "は", "reading": "は", "romaji": "wa", "pos": "particle", "jlpt": "N5"},  
        {"surface": "花", "reading": "はな", "romaji": "hana", "pos": "noun", "jlpt": "N5"},  
        {"surface": "が", "reading": "が", "romaji": "ga", "pos": "particle", "jlpt": "N5"},  
        {"surface": "咲いて", "reading": "さいて", "romaji": "saite", "pos": "verb", "base\_form": "咲く", "form": "te-form", "jlpt": "N4"},  
        {"surface": "、", "reading": "、", "romaji": "", "pos": "punct", "jlpt": "-"},  
        {"surface": "とても", "reading": "とても", "romaji": "totemo", "pos": "adverb", "jlpt": "N5"},  
        {"surface": "きれい", "reading": "きれい", "romaji": "kirei", "pos": "na-adj", "jlpt": "N5"},  
        {"surface": "です", "reading": "です", "romaji": "desu", "pos": "copula", "jlpt": "N5"},  
        {"surface": "。", "reading": "。", "romaji": "", "pos": "punct", "jlpt": "-"}  
      \],  
      "grammar\_points": \[  
        {  
          "pattern": "〜て",  
          "explanation": "Bentuk konjungtif penghubung klausa yang mengindikasikan urutan atau alasan."  
        }  
      \]  
    }  
  \]  
}

## **5.2. Sintesis Suara Piper TTS (POST /tts)**

Menghasilkan atau mengambil stream audio hasil render Piper TTS dari cache terdistribusi.

* **Request Body:**

JSON  
{  
  "text": "東京の春は花が咲いて、とてもきれいです。",  
  "voice\_model": "ja\_JP-hira-medium",  
  "speed": 1.0  
}

* **Response Body (200 OK):**

JSON  
{  
  "url": "https://audio.japanesereader.ai/audio/7a1f59c8de484439fa3881b213b7df08947ab5d7ea7ff7b12.wav",  
  "format": "audio/wav",  
  "cached": true,  
  "duration\_seconds": 3.42  
}

## **5.3. Telemetri & Log Belajar (POST /analytics/inspect)**

Mencatat interaksi tap kalimat pengguna untuk memperbarui algoritma needs\_deep\_study.

* **Request Body:**

JSON  
{  
  "sentence\_id": "sent\_88294a\_01",  
  "user\_id": "usr\_991823"  
}

* **Response Body (200 OK):**

JSON  
{  
  "success": true,  
  "inspection\_count": 4,  
  "needs\_deep\_study": true  
}

# **6\. Implementasi Perangkat Lunak Inti**

## **6.1. Edge API Gateway: Cloudflare Workers \+ Hono.js**

Kode berikut mengelola alur orkestrasi analitik, verifikasi cache R2 berbasis hash, serta komunikasi ke mesin Piper TTS.

TypeScript  
import { Hono } from 'hono';  
import { cors } from 'hono/cors';

type Bindings \= {  
  DB: D1Database;  
  R2\_AUDIO: R2Bucket;  
  DEEPSEEK\_API\_KEY: string;  
  PIPER\_TTS\_ENDPOINT: string; // URL ke worker/server Piper TTS (ONNX)  
};

const app \= new Hono\<{ Bindings: Bindings }\>();

app.use('\*', cors());

// Helper: Generasi Hash SHA-256 Deterministik  
async function generateSha256(input: string): Promise\<string\> {  
  const encoder \= new TextEncoder();  
  const data \= encoder.encode(input);  
  const hashBuffer \= await crypto.subtle.digest('SHA-256', data);  
  const hashArray \= Array.from(new Uint8Array(hashBuffer));  
  return hashArray.map((b) \=\> b.toString(16).padStart(2, '0')).join('');  
}

// 1\. Endpoint Analisis DeepSeek  
app.post('/api/v1/analyze', async (c) \=\> {  
  const { text } \= await c.req.json\<{ text: string }\>();

  if (\!text || text.trim() \=== '') {  
    return c.json({ error: 'Parameter text tidak boleh kosong' }, 400);  
  }

  const systemPrompt \= \`You are a Japanese Morphological Analyzer. Return only valid JSON conforming to the requested schema. Analyze text into sentences, tokens with furigana, POS tags, and grammar points.\`;

  const deepseekResponse \= await fetch('https://api.deepseek.com/v1/chat/completions', {  
    method: 'POST',  
    headers: {  
      'Authorization': \`Bearer \${c.env.DEEPSEEK\_API\_KEY}\`,  
      'Content-Type': 'application/json'  
    },  
    body: JSON.stringify({  
      model: 'deepseek-chat',  
      response\_format: { type: 'json\_object' },  
      messages: \[  
        { role: 'system', content: systemPrompt },  
        { role: 'user', content: text }  
      \]  
    })  
  });

  if (\!deepseekResponse.ok) {  
    return c.json({ error: 'Gagal menghubungi DeepSeek Gateway' }, 502);  
  }

  const result \= await deepseekResponse.json();  
  const parsedContent \= JSON.parse(result.choices\[0\].message.content);  
  return c.json(parsedContent);  
});

// 2\. Endpoint Piper TTS Terpadu dengan D1 & R2 Caching  
app.post('/api/v1/tts', async (c) \=\> {  
  const { text, speed \= 1.0, voice\_model \= 'ja\_JP-hira-medium' } \= await c.req.json\<{  
    text: string;  
    speed?: number;  
    voice\_model?: string;  
  }\>();

  if (\!text) {  
    return c.json({ error: 'Parameter text diperlukan' }, 400);  
  }

  const textHash \= await generateSha256(\`\${text}\_\${voice\_model}\_\${speed}\`);

  // Query D1 Cache  
  const cachedAudio \= await c.env.DB.prepare(  
    'SELECT r2\_url FROM audio\_cache WHERE text\_hash \= ?'  
  ).bind(textHash).first\<{ r2\_url: string }\>();

  if (cachedAudio) {  
    return c.json({ url: cachedAudio.r2\_url, cached: true });  
  }

  // Panggilan ke Piper TTS Engine Server (ONNX inference)  
  const piperPayload \= {  
    text: text,  
    model: voice\_model,  
    length\_scale: 1.0 / speed // Nilai lebih rendah mempercepat tempo ucapan  
  };

  const piperRes \= await fetch(\`\${c.env.PIPER\_TTS\_ENDPOINT}/synthesize\`, {  
    method: 'POST',  
    headers: { 'Content-Type': 'application/json' },  
    body: JSON.stringify(piperPayload)  
  });

  if (\!piperRes.ok) {  
    return c.json({ error: 'Inferensi Piper TTS gagal' }, 500);  
  }

  const audioBuffer \= await piperRes.arrayBuffer();  
  const r2Key \= \`audio/\${textHash}.wav\`;

  // Simpan output audio mentah ke Cloudflare R2  
  await c.env.R2\_AUDIO.put(r2Key, audioBuffer, {  
    httpMetadata: { contentType: 'audio/wav' }  
  });

  const publicR2Url \= \`https://audio.japanesereader.ai/\${r2Key}\`;

  // Daftarkan ke tabel cache Cloudflare D1  
  await c.env.DB.prepare(  
    'INSERT INTO audio\_cache (text\_hash, voice\_model, speed, r2\_url, created\_at) VALUES (?, ?, ?, ?, ?)'  
  ).bind(textHash, voice\_model, speed, publicR2Url, Date.now()).run();

  return c.json({ url: publicR2Url, cached: false });  
});

// 3\. Endpoint Telemetri Analisis Kalimat  
app.post('/api/v1/analytics/inspect', async (c) \=\> {  
  const { sentence\_id, user\_id } \= await c.req.json\<{  
    sentence\_id: string;  
    user\_id: string;  
  }\>();

  // Atomically update inspection count & check deep study threshold (\>= 3\)  
  await c.env.DB.prepare(\`  
    UPDATE sentences   
    SET inspection\_count \= inspection\_count \+ 1,  
        needs\_deep\_study \= CASE WHEN inspection\_count \+ 1 \>= 3 THEN 1 ELSE 0 END  
    WHERE id \= ?  
  \`).bind(sentence\_id).run();

  await c.env.DB.prepare(\`  
    INSERT INTO study\_logs (id, user\_id, sentence\_id, action\_type, timestamp)  
    VALUES (?, ?, ?, 'INSPECT', ?)  
  \`).bind(crypto.randomUUID(), user\_id, sentence\_id, Date.now()).run();

  return c.json({ success: true });  
});

export default app;

## **6.2. Klien Android Native: Integrasi Room Database & ExoPlayer Media3**

### **Entity Room Database (SentenceEntity.kt)**

Kotlin  
package com.japanesereader.ai.data.local.entity

import androidx.room.ColumnInfo  
import androidx.room.Entity  
import androidx.room.ForeignKey  
import androidx.room.Index  
import androidx.room.PrimaryKey

@Entity(  
    tableName \= "sentences",  
    foreignKeys \= \[  
        ForeignKey(  
            entity \= ArticleEntity::class,  
            parentColumns \= \["id"\],  
            childColumns \= \["article\_id"\],  
            onDelete \= ForeignKey.CASCADE  
        )  
    \],  
    indices \= \[Index(value \= \["article\_id"\])\]  
)  
data class SentenceEntity(  
    @PrimaryKey  
    @ColumnInfo(name \= "id") val id: String,  
    @ColumnInfo(name \= "article\_id") val articleId: String,  
    @ColumnInfo(name \= "original\_text") val originalText: String,  
    @ColumnInfo(name \= "translated\_text") val translatedText: String,  
    @ColumnInfo(name \= "furigana\_payload") val furiganaPayload: String,  
    @ColumnInfo(name \= "grammar\_analysis") val grammarAnalysis: String,  
    @ColumnInfo(name \= "sequence\_order") val sequenceOrder: Int,  
    @ColumnInfo(name \= "inspection\_count") val inspectionCount: Int \= 0,  
    @ColumnInfo(name \= "audio\_play\_count") val audioPlayCount: Int \= 0,  
    @ColumnInfo(name \= "needs\_deep\_study") val needsDeepStudy: Boolean \= false  
)

### **Manajer Audio Media3 ExoPlayer (PiperAudioManager.kt)**

Kotlin  
package com.japanesereader.ai.media

import android.content.Context  
import androidx.media3.common.MediaItem  
import androidx.media3.common.PlaybackParameters  
import androidx.media3.exoplayer.ExoPlayer  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow

class PiperAudioManager(context: Context) {  
    private val exoPlayer: ExoPlayer \= ExoPlayer.Builder(context).build()

    private val \_isPlaying \= MutableStateFlow(false)  
    val isPlaying: StateFlow\<Boolean\> \= \_isPlaying.asStateFlow()

    fun playAudioStream(audioUrl: String, playbackSpeed: Float \= 1.0f) {  
        val mediaItem \= MediaItem.fromUri(audioUrl)  
        exoPlayer.setMediaItem(mediaItem)  
        exoPlayer.playbackParameters \= PlaybackParameters(playbackSpeed)  
        exoPlayer.prepare()  
        exoPlayer.playWhenReady \= true  
        \_isPlaying.value \= true  
    }

    fun stopPlayback() {  
        if (exoPlayer.isPlaying) {  
            exoPlayer.stop()  
        }  
        \_isPlaying.value \= false  
    }

    fun release() {  
        exoPlayer.release()  
    }  
}

# **7\. Spesifikasi Algoritma Pembelajaran Adaptif (Adaptive Learning Algorithm)**

Sistem mengadopsi modifikasi algoritma *SuperMemo-2 (SM-2)* yang terhubung langsung dengan intensitas interaksi membaca:

> 1. **Threshold Inspection:**  
>    Setiap kali pengguna melakukan interaksi pembongkaran (*inspection tap*) pada sebuah kalimat, counter \$C\_{inspect}\$ bertambah (\$C\_{inspect} \= C\_{inspect} \+ 1\$). Jika \$C\_{inspect} \\ge 3\$, flag needs\_deep\_study diaktifkan ke status 1\.  
> 2. **Interval Pengulangan Spaced Repetition (SRS):**  
>    Kosakata yang terikat pada kalimat tersebut dihitung interval retensinya:  
>    \$\$I(n) \= \\begin{cases} 1 \\text{ hari}, & n \= 1 \\\\ 6 \\text{ hari}, & n \= 2 \\\\ I(n-1) \\times EF, & n \> 2 \\end{cases}\$\$  
>    Di mana \$EF\$ (*Easiness Factor*) dihitung dari interaksi membaca:  
>    \$\$EF' \= EF \+ (0.1 \- (5 \- q) \\times (0.08 \+ (5 \- q) \\times 0.02))\$\$  
>    *Nilai kualitas pemahaman \$q\$ direduksi secara proporsional jika jumlah inspeksi kalimat pada teks asli tinggi.*

# **8\. Konfigurasi Keamanan & Skalabilitas Deployment**

> 1. **Edge Deployment (Cloudflare)**:  
   * Seluruh rute API dilindungi dengan enkripsi transit TLS 1.3.  
   * Kunci API DeepSeek disimpan menggunakan modul *Cloudflare Secrets*.  
   * Cache-Control header untuk respons audio R2 diatur ke public, max-age=31536000, immutable guna meminimalkan I/O egress.  
> 2. **Isolasi Piper TTS Engine**:  
   * Mesin Piper dijalankan dalam container Docker minimalis berbasis C++ ONNX Runtime.  
   * Model suara dimuat langsung ke memori RAM (*shared memory / mmap*) saat inisialisasi kontainer untuk menjamin latensi inferensi di bawah 150 milidetik per kalimat pendek.  
> 3. **Integritas Database Klien**:  
   * SQLite Room di Android dikonfigurasi dengan enkripsi *SQLCipher* untuk melindungi data artikel offline pengguna.