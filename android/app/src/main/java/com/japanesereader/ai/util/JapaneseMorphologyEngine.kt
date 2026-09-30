package com.japanesereader.ai.util

import com.google.gson.Gson
import com.japanesereader.ai.data.remote.AnalyzeResponseDto
import com.japanesereader.ai.data.remote.GrammarPointDto
import com.japanesereader.ai.data.remote.SentenceAnalysisDto
import com.japanesereader.ai.data.remote.TokenDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object JapaneseMorphologyEngine {
    private val gson = Gson()

    private const val N5_KANJI = "日月火水木金土年時分前後午今先毎半人男女子女父母友本名何大小高安新古長白赤青黒上下中外右左北南東西口目耳手足行来帰食飲見聞読書話買休会校車電駅店道国社語天気雨万千百十一二三四五六七八九"
    private const val N4_KANJI = "週末曜考思知始終待急借貸送返教習勉強研究質問題答宿試験合覚忘運動歩走起寝降咲泳映画音楽歌写真旅館堂飯院病薬医員工場図地風雪度界屋具雑誌洗漢字"
    private const val N3_KANJI = "演役普通規則係案内状態況観察経済営業招待紹介歴史育命恋愛念感想構造満開季節改札留守独雑難易容喜怒悲痛疲眠苦悩"
    private const val N2_N1_KANJI = "架空繊細躊躇顕著裁判憲法律批判抽象概念崩壊貿易緻密曖昧葛藤憂鬱膨脹凝縮覇権巧妙詭弁錯覚偏頗"
    private val KANA_ROMAJI = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "wo", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po"
    )

    data class LexItem(
        val surface: String,
        val reading: String,
        val pos: String,
        val meaning: String,
        val jlpt: String
    )

    private val LEXICON = listOf(
        LexItem("元気", "げんき", "na-adj", "Sehat / bersemangat", "N5"),
        LexItem("こんにちは", "こんにちは", "expression", "Halo / selamat siang", "N5"),
        LexItem("ありがとう", "ありがとう", "expression", "Terima kasih", "N5"),
        LexItem("先生", "せんせい", "noun", "Guru / dokter", "N5"),
        LexItem("学生", "がくせい", "noun", "Murid / siswa", "N5"),
        LexItem("私", "わたし", "noun", "Saya", "N5"),
        LexItem("今週", "こんしゅう", "noun", "Minggu ini", "N5"),
        LexItem("来週", "らいしゅう", "noun", "Minggu depan", "N5"),
        LexItem("今週末", "こんしゅうまつ", "noun", "Akhir pekan ini", "N4"),
        LexItem("土曜日", "どようび", "noun", "Hari Sabtu", "N5"),
        LexItem("日曜日", "にちようび", "noun", "Hari Minggu", "N5"),
        LexItem("東京", "とうきょう", "noun", "Tokyo", "N5"),
        LexItem("渋谷", "しぶや", "noun", "Shibuya", "N4"),
        LexItem("秋葉原", "あきはばら", "noun", "Akihabara", "N4"),
        LexItem("カフェ", "カフェ", "noun", "Kafe", "N5"),
        LexItem("春", "はる", "noun", "Musim semi", "N5"),
        LexItem("夏", "なつ", "noun", "Musim panas", "N5"),
        LexItem("秋", "あき", "noun", "Musim gugur", "N5"),
        LexItem("冬", "ふゆ", "noun", "Musim dingin", "N5"),
        LexItem("花", "はな", "noun", "Bunga", "N5"),
        LexItem("桜", "さくら", "noun", "Bunga sakura", "N5"),
        LexItem("公園", "こうえん", "noun", "Taman", "N5"),
        LexItem("時間", "じかん", "noun", "Waktu", "N5"),
        LexItem("試験", "しけん", "noun", "Ujian", "N5"),
        LexItem("準備", "じゅんび", "noun", "Persiapan", "N4"),
        LexItem("雨", "あめ", "noun", "Hujan", "N5"),
        LexItem("予報", "よほう", "noun", "Prakiraan cuaca", "N4"),
        LexItem("傘", "かさ", "noun", "Payung", "N5"),
        LexItem("午後", "ごご", "noun", "Sore hari", "N5"),
        LexItem("午前", "ごぜん", "noun", "Pagi hari", "N5"),
        LexItem("駅", "えき", "noun", "Stasiun", "N5"),
        LexItem("改札", "かいさつ", "noun", "Gerbang tiket", "N3"),
        LexItem("前", "まえ", "noun", "Depan", "N5"),
        LexItem("人間", "にんげん", "noun", "Manusia", "N3"),
        LexItem("生き物", "いきもの", "noun", "Makhluk hidup", "N3"),
        LexItem("季節", "きせつ", "noun", "Musim", "N4"),
        LexItem("満開", "まんかい", "noun", "Mekar penuh", "N3"),
        LexItem("田中", "たなか", "noun", "Tanaka", "N5"),
        LexItem("健一", "けんいち", "noun", "Kenichi", "N5"),

        // Verbs
        LexItem("咲いて", "さいて", "verb", "Mekar (te-form)", "N4"),
        LexItem("咲く", "さく", "verb", "Mekar", "N4"),
        LexItem("行きます", "いきます", "verb", "Pergi (sopan)", "N5"),
        LexItem("行きませんか", "いきませんか", "verb", "Maukah pergi? (ajakan)", "N5"),
        LexItem("行きましょう", "いきましょう", "verb", "Ayo pergi!", "N5"),
        LexItem("行く", "いく", "verb", "Pergi", "N5"),
        LexItem("話しませんか", "はなしませんか", "verb", "Maukah mengobrol?", "N5"),
        LexItem("話す", "はなす", "verb", "Berbicara", "N5"),
        LexItem("進んでいますか", "すすんでいますか", "verb", "Apakah mengalami kemajuan?", "N4"),
        LexItem("進む", "すすむ", "verb", "Maju", "N4"),
        LexItem("あれば", "あれば", "verb", "Jika ada", "N4"),
        LexItem("ある", "ある", "verb", "Ada", "N5"),
        LexItem("あります", "あります", "verb", "Ada (sopan)", "N5"),
        LexItem("待っています", "まっています", "verb", "Sedang menunggu", "N5"),
        LexItem("待つ", "まつ", "verb", "Menunggu", "N5"),
        LexItem("降る", "ふる", "verb", "Turun (hujan)", "N5"),
        LexItem("演じる", "えんじる", "verb", "Memerankan", "N2"),

        // Adjectives
        LexItem("新しい", "あたらしい", "i-adj", "Baru", "N5"),
        LexItem("きれい", "きれい", "na-adj", "Indah / bersih", "N5"),
        LexItem("静か", "しずか", "na-adj", "Tenang", "N5"),
        LexItem("美味しい", "おいしい", "i-adj", "Lezat", "N5"),

        // Adverbs & Expressions
        LexItem("とても", "とても", "adverb", "Sangat", "N5"),
        LexItem("もし", "もし", "adverb", "Jika", "N5"),
        LexItem("はい", "はい", "expression", "Ya", "N5"),
        LexItem("ぜひ", "ぜひ", "adverb", "Tentu saja", "N4"),
        LexItem("一緒に", "いっしょに", "adverb", "Bersama", "N5"),
        LexItem("お疲れ様です", "おつかれさまです", "expression", "Terima kasih atas kerja kerasnya", "N4"),
        LexItem("明日", "あした", "noun", "Besok", "N5"),

        // Particles
        LexItem("は", "は", "particle", "Partikel topik", "N5"),
        LexItem("が", "が", "particle", "Partikel subjek", "N5"),
        LexItem("の", "の", "particle", "Partikel kepemilikan", "N5"),
        LexItem("に", "に", "particle", "Partikel arah/waktu", "N5"),
        LexItem("で", "で", "particle", "Partikel lokasi", "N5"),
        LexItem("を", "を", "particle", "Partikel objek", "N5"),
        LexItem("へ", "へ", "particle", "Partikel tujuan", "N5"),
        LexItem("と", "と", "particle", "Dan / dengan", "N5"),
        LexItem("ね", "ね", "particle", "Bukan begitu?", "N5"),
        LexItem("よ", "よ", "particle", "Penegas info", "N5"),
        LexItem("か", "か", "particle", "Tanda tanya", "N5"),
        LexItem("です", "です", "copula", "Adalah", "N5"),
        LexItem("という", "という", "particle", "Yang dinamakan", "N4"),
        LexItem("、", "", "punct", "Koma", "-"),
        LexItem("。", "", "punct", "Titik", "-"),
        LexItem("？", "", "punct", "Tanda tanya", "-"),
        LexItem("！", "", "punct", "Tanda seru", "-")
    ).sortedByDescending { it.surface.length }

    fun isKanji(c: Char): Boolean {
        val cp = c.code
        return (cp in 0x4E00..0x9FAF) || (cp in 0x3400..0x4DBF)
    }

    fun kanaToRomaji(kana: String): String {
        val sb = StringBuilder()
        for (c in kana) {
            sb.append(KANA_ROMAJI[c.toString()] ?: c.toString())
        }
        return sb.toString()
    }

    fun tokenize(text: String): List<TokenDto> {
        val result = mutableListOf<TokenDto>()
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }
            var matched: LexItem? = null
            for (item in LEXICON) {
                if (text.startsWith(item.surface, i)) {
                    matched = item
                    break
                }
            }

            if (matched != null) {
                result.add(
                    TokenDto(
                        surface = matched.surface,
                        reading = matched.reading,
                        romaji = kanaToRomaji(matched.reading),
                        pos = matched.pos,
                        meaning = matched.meaning,
                        jlpt = matched.jlpt
                    )
                )
                i += matched.surface.length
            } else {
                val singleChar = text[i].toString()
                val isK = isKanji(text[i])
                result.add(
                    TokenDto(
                        surface = singleChar,
                        reading = singleChar,
                        romaji = kanaToRomaji(singleChar),
                        pos = if (isK) "noun" else "kana",
                        meaning = if (isK) "Karakter kanji" else "",
                        jlpt = if (isK) {
                            val ch = text[i]
                            when {
                                N5_KANJI.contains(ch) -> "N5"
                                N4_KANJI.contains(ch) -> "N4"
                                N3_KANJI.contains(ch) -> "N3"
                                N2_N1_KANJI.contains(ch) -> "N2"
                                else -> "N4"
                            }
                        } else "-"
                    )
                )
                i++
            }
        }
        return result
    }

    fun detectGrammar(text: String): List<GrammarPointDto> {
        val points = mutableListOf<GrammarPointDto>()
        if (text.contains("〜ませんか") || text.contains("ませんか")) {
            points.add(GrammarPointDto("〜ませんか (~masen ka)", "Pola ajakan sopan: Maukah kamu / Bagaimana kalau kita...?"))
        }
        if (text.contains("咲いて") || text.contains("て、") || text.contains("て ")) {
            points.add(GrammarPointDto("〜て (Te-form)", "Bentuk konjungtif penghubung klausa (urutan atau sebab-akibat)."))
        }
        if (text.contains("ている") || text.contains("ています")) {
            points.add(GrammarPointDto("〜ている (~te iru)", "Bentuk progresif atau kondisi yang sedang berlangsung."))
        }
        if (text.contains("あれば") || text.contains("ば")) {
            points.add(GrammarPointDto("〜ば (Ba-conditional)", "Bentuk pengandaian (jika / seandainya)."))
        }
        if (text.contains("ましょう")) {
            points.add(GrammarPointDto("〜ましょう (~mashou)", "Bentuk ajakan positif: Mari kita lakukan!"))
        }
        if (text.contains("という")) {
            points.add(GrammarPointDto("〜という (~to iu)", "Menyatakan sebutan atau definisi konsep."))
        }
        return points
    }

    fun translate(text: String): String {
        val t = text.trim()
        if (t.contains("今週の土曜日に新しいカフェに行きませんか")) return "Maukah kamu pergi ke kafe baru pada hari Sabtu pekan ini?"
        if (t.contains("東京の春は花が咲いて、とてもきれいです")) return "Musim semi di Tokyo bunga-bunga bermekaran, dan sangat indah."
        if (t.contains("明日は午後から雨が降る予報です")) return "Besok diperkirakan akan turun hujan mulai sore hari. Mari kita bawa payung."
        if (t.contains("お疲れ様です！駅の改札前で待っていますね")) return "Terima kasih atas kerja kerasnya! Aku menunggu di depan gerbang tiket stasiun ya."
        if (t.contains("今週末、渋谷のカフェで話しませんか")) return "Akhir pekan ini, maukah mengobrol di kafe Shibuya?"
        if (t.contains("普通の人という架空の生き物を演じる")) return "Memerankan sosok makhluk imajiner yang dinamakan manusia normal."
        return "Arti konteks: $t"
    }


    fun calculateDynamicJlpt(text: String, tokens: List<TokenDto>): String {
        var n1Count = 0
        var n2Count = 0
        var n3Count = 0
        var n4Count = 0
        var n5Count = 0

        for (c in text) {
            when {
                N2_N1_KANJI.contains(c) -> n1Count++
                N3_KANJI.contains(c) -> n3Count++
                N4_KANJI.contains(c) -> n4Count++
                N5_KANJI.contains(c) -> n5Count++
            }
        }

        for (t in tokens) {
            when (t.jlpt) {
                "N1" -> n1Count++
                "N2" -> n2Count++
                "N3" -> n3Count++
                "N4" -> n4Count++
                "N5" -> n5Count++
            }
        }

        return when {
            n1Count > 1 -> "N1"
            n1Count == 1 || n2Count > 0 -> "N2"
            n3Count > 0 -> "N3"
            n4Count > 0 -> "N4"
            else -> "N5"
        }
    }

    fun analyzeLocal(text: String): AnalyzeResponseDto {
        val sentences = text.split(Regex("([。！？\n]+)"))
            .filter { it.isNotBlank() }
            .mapIndexed { idx, s ->
                val trimmed = s.trim()
                val tokens = tokenize(trimmed)
                val grammar = detectGrammar(trimmed)
                val trans = translate(trimmed)
                SentenceAnalysisDto(
                    sequence_order = idx + 1,
                    original_text = trimmed,
                    translated_text = trans,
                    tokens = tokens,
                    grammar_points = grammar
                )
            }

        var kanjiCount = 0
        var total = 0
        for (c in text) {
            if (!c.isWhitespace() && c != '。' && c != '、') {
                total++
                if (isKanji(c)) kanjiCount++
            }
        }
        val ratio = if (total > 0) Math.round((kanjiCount.toDouble() / total.toDouble()) * 100.0) / 100.0 else 0.0
        val allTokens = sentences.flatMap { it.tokens }
        val computedJlpt = calculateDynamicJlpt(text, allTokens)

        return AnalyzeResponseDto(
            difficulty_level = computedJlpt,
            kanji_ratio = ratio,
            sentences = if (sentences.isNotEmpty()) sentences else listOf(
                SentenceAnalysisDto(
                    sequence_order = 1,
                    original_text = text,
                    translated_text = translate(text),
                    tokens = tokenize(text),
                    grammar_points = detectGrammar(text)
                )
            )
        )
    }

    // Direct DeepSeek API call with BYOK key
    suspend fun analyzeWithDeepSeek(text: String, apiKey: String): AnalyzeResponseDto? = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("https://api.deepseek.com/v1/chat/completions")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
            }

            val prompt = """You are a Japanese Morphological Analyzer. Return only valid JSON strictly matching schema:
                {"difficulty_level":"N4","kanji_ratio":0.3,"sentences":[{"sequence_order":1,"original_text":"...","translated_text":"...","tokens":[{"surface":"...","reading":"...","romaji":"...","pos":"noun","meaning":"...","jlpt":"N4"}],"grammar_points":[{"pattern":"...","explanation":"..."}]}]}
                Target translation is Indonesian. Do not include markdown codeblocks."""

            val payload = mapOf(
                "model" to "deepseek-chat",
                "response_format" to mapOf("type" to "json_object"),
                "messages" to listOf(
                    mapOf("role" to "system", "content" to prompt),
                    mapOf("role" to "user", "content" to text)
                )
            )

            OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(payload)) }

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    val resp = reader.readText()
                    val root = gson.fromJson(resp, Map::class.java)
                    val choices = root["choices"] as? List<*>
                    val firstChoice = choices?.firstOrNull() as? Map<*, *>
                    val message = firstChoice?.get("message") as? Map<*, *>
                    val content = message?.get("content") as? String
                    if (!content.isNullOrBlank()) {
                        return@withContext gson.fromJson(content, AnalyzeResponseDto::class.java)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
