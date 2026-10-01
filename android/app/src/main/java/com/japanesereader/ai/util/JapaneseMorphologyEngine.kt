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
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o",
        "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo", "ゔ" to "vu",
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo"
    )

    private val KANJI_READINGS = mapOf(
        '日' to "ひ", '月' to "つき", '火' to "ひ", '水' to "みず", '木' to "き", '金' to "かね", '土' to "つち",
        '年' to "とし", '時' to "とき", '分' to "ふん", '前' to "まえ", '後' to "あと", '午' to "ご", '今' to "いま",
        '先' to "さき", '毎' to "まい", '半' to "はん", '人' to "ひと", '男' to "おとこ", '女' to "おんな", '子' to "こ",
        '父' to "ちち", '母' to "はは", '友' to "とも", '達' to "たち", '本' to "ほん", '名' to "な", '何' to "なに",
        '大' to "おお", '小' to "ちい", '高' to "たか", '安' to "やす", '新' to "あたら", '古' to "ふる", '長' to "なが",
        '白' to "しろ", '赤' to "あか", '青' to "あお", '黒' to "くろ", '上' to "うえ", '下' to "した", '中' to "なか",
        '外' to "そと", '右' to "みぎ", '左' to "ひだり", '北' to "きた", '南' to "みなみ", '東' to "ひがし", '西' to "にし",
        '口' to "くち", '目' to "め", '耳' to "みみ", '手' to "て", '足' to "あし", '行' to "い", '来' to "き",
        '帰' to "かえ", '食' to "た", '飲' to "の", '見' to "み", '聞' to "き", '読' to "よ", '書' to "か",
        '話' to "はな", '買' to "か", '休' to "やす", '会' to "あ", '校' to "こう", '車' to "くるま", '電' to "でん",
        '駅' to "えき", '店' to "みせ", '道' to "みち", '国' to "くに", '社' to "しゃ", '語' to "ご", '天' to "てん",
        '気' to "き", '雨' to "あめ", '映' to "えい", '画' to "が", '勉' to "べん", '強' to "きょう", '仕' to "し",
        '事' to "ごと", '物' to "もの", '誰' to "だれ", '持' to "も", '待' to "ま", '教' to "おし", '習' to "なら",
        '寝' to "ね", '起' to "お", '好' to "す", '思' to "おも", '知' to "し", '作' to "つく", '使' to "つか",
        '入' to "はい", '出' to "で", '開' to "あ", '閉' to "し", '立' to "た", '座' to "すわ", '歩' to "ある",
        '走' to "はし", '泳' to "およ", '早' to "はや", '遅' to "おそ", '近' to "ちか", '遠' to "とお", '多' to "おお",
        '少' to "すく", '難' to "むずか", '易' to "やさ", '茶' to "ちゃ", '花' to "はな", '桜' to "さくら", '公' to "こう",
        '園' to "えん", '試' to "し", '験' to "けん", '準' to "じゅん", '備' to "び", '予' to "よ", '報' to "ほう",
        '傘' to "かさ", '改' to "かい", '札' to "さつ", '生' to "せい", '演' to "えん", '役' to "やく", '普' to "ふ",
        '通' to "つう", '架' to "か", '空' to "くう", '満' to "まん", '開' to "かい", '季' to "き", '節' to "せつ",
        '朝' to "あさ", '昼' to "ひる", '夜' to "よる", '晩' to "ばん", '週' to "しゅう", '末' to "まつ", '病' to "びょう",
        '院' to "いん", '薬' to "くすり", '屋' to "や", '図' to "と", '書' to "しょ", '館' to "かん", '銀' to "ぎん",
        '町' to "まち", '村' to "むら", '海' to "うみ", '山' to "やま", '川' to "かわ", '森' to "もり", '林' to "はやし",
        '空' to "そら", '風' to "かぜ", '雪' to "ゆき", '音' to "おと", '楽' to "がく", '歌' to "うた", '世' to "せ",
        '界' to "かい", '理' to "り", '由' to "ゆう", '心' to "こころ", '体' to "からだ", '頭' to "あたま", '顔' to "かお"
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
        LexItem("おはよう", "おはよう", "expression", "Selamat pagi", "N5"),
        LexItem("こんばんは", "こんばんは", "expression", "Selamat malam", "N5"),
        LexItem("さようなら", "さようなら", "expression", "Selamat tinggal", "N5"),
        LexItem("先生", "せんせい", "noun", "Guru / dokter", "N5"),
        LexItem("学生", "がくせい", "noun", "Murid / siswa", "N5"),
        LexItem("私", "わたし", "noun", "Saya", "N5"),
        LexItem("僕", "ぼく", "noun", "Aku (pria)", "N5"),
        LexItem("友達", "ともだち", "noun", "Teman / kawan", "N5"),
        LexItem("映画館", "えいがかん", "noun", "Bioskop", "N4"),
        LexItem("映画", "えいが", "noun", "Film / sinema", "N5"),
        LexItem("日本語", "にほんご", "noun", "Bahasa Jepang", "N5"),
        LexItem("英語", "えいご", "noun", "Bahasa Inggris", "N5"),
        LexItem("日本", "にほん", "noun", "Jepang", "N5"),
        LexItem("図書館", "としょかん", "noun", "Perpustakaan", "N5"),
        LexItem("学校", "がっこう", "noun", "Sekolah", "N5"),
        LexItem("勉強", "べんきょう", "noun", "Belajar", "N5"),
        LexItem("仕事", "しごと", "noun", "Pekerjaan", "N5"),
        LexItem("家族", "かぞく", "noun", "Keluarga", "N5"),
        LexItem("子供", "こども", "noun", "Anak-anak", "N5"),
        LexItem("本", "ほん", "noun", "Buku", "N5"),
        LexItem("手紙", "てがみ", "noun", "Surat", "N5"),
        LexItem("写真", "しゃしん", "noun", "Foto", "N5"),
        LexItem("音楽", "おんがく", "noun", "Musik", "N5"),
        LexItem("料理", "りょうり", "noun", "Masakan", "N5"),
        LexItem("今週", "こんしゅう", "noun", "Minggu ini", "N5"),
        LexItem("来週", "らいしゅう", "noun", "Minggu depan", "N5"),
        LexItem("先週", "せんしゅう", "noun", "Minggu lalu", "N5"),
        LexItem("今週末", "こんしゅうまつ", "noun", "Akhir pekan ini", "N4"),
        LexItem("土曜日", "どようび", "noun", "Hari Sabtu", "N5"),
        LexItem("日曜日", "にちようび", "noun", "Hari Minggu", "N5"),
        LexItem("今日", "きょう", "noun", "Hari ini", "N5"),
        LexItem("明日", "あした", "noun", "Besok", "N5"),
        LexItem("昨日", "きのう", "noun", "Kemarin", "N5"),
        LexItem("毎日", "まいにち", "noun", "Setiap hari", "N5"),
        LexItem("朝", "あさ", "noun", "Pagi", "N5"),
        LexItem("昼", "ひる", "noun", "Siang", "N5"),
        LexItem("夜", "よる", "noun", "Malam", "N5"),
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
        LexItem("電車", "でんしゃ", "noun", "Kereta listrik", "N5"),
        LexItem("車", "くるま", "noun", "Mobil", "N5"),
        LexItem("何", "なに", "noun", "Apa", "N5"),
        LexItem("誰", "だれ", "noun", "Siapa", "N5"),
        LexItem("どこ", "どこ", "noun", "Di mana", "N5"),
        LexItem("いつ", "いつ", "noun", "Kapan", "N5"),

        // Verbs & Inflections
        LexItem("咲いて", "さいて", "verb", "Mekar (te-form)", "N4"),
        LexItem("咲く", "さく", "verb", "Mekar", "N4"),
        LexItem("行きます", "いきます", "verb", "Pergi (sopan)", "N5"),
        LexItem("行きませんか", "いきませんか", "verb", "Maukah pergi? (ajakan)", "N5"),
        LexItem("行きましょう", "いきましょう", "verb", "Ayo pergi!", "N5"),
        LexItem("行って", "いって", "verb", "Pergi (te-form)", "N5"),
        LexItem("行った", "いった", "verb", "Telah pergi", "N5"),
        LexItem("行く", "いく", "verb", "Pergi", "N5"),
        LexItem("見に行きます", "みにいきます", "verb", "Pergi untuk menonton", "N5"),
        LexItem("見に行き", "みにいき", "verb", "Pergi menonton (stem)", "N5"),
        LexItem("見ます", "みます", "verb", "Melihat / menonton (sopan)", "N5"),
        LexItem("見て", "みて", "verb", "Melihat / menonton (te-form)", "N5"),
        LexItem("見た", "みた", "verb", "Telah melihat / menonton", "N5"),
        LexItem("見る", "みる", "verb", "Melihat / menonton", "N5"),
        LexItem("食べます", "たべます", "verb", "Makan (sopan)", "N5"),
        LexItem("食べて", "たべて", "verb", "Makan (te-form)", "N5"),
        LexItem("食べた", "たべた", "verb", "Telah makan", "N5"),
        LexItem("食べる", "たべる", "verb", "Makan", "N5"),
        LexItem("飲みます", "のみます", "verb", "Minum (sopan)", "N5"),
        LexItem("飲んで", "のんで", "verb", "Minum (te-form)", "N5"),
        LexItem("飲む", "のむ", "verb", "Minum", "N5"),
        LexItem("話しませんか", "はなしませんか", "verb", "Maukah mengobrol?", "N5"),
        LexItem("話します", "はなします", "verb", "Berbicara (sopan)", "N5"),
        LexItem("話して", "はなして", "verb", "Berbicara (te-form)", "N5"),
        LexItem("話す", "はなす", "verb", "Berbicara", "N5"),
        LexItem("聞きます", "ききます", "verb", "Mendengar / bertanya (sopan)", "N5"),
        LexItem("聞いて", "きいて", "verb", "Mendengar (te-form)", "N5"),
        LexItem("聞く", "きく", "verb", "Mendengar", "N5"),
        LexItem("読みます", "よみます", "verb", "Membaca (sopan)", "N5"),
        LexItem("読んで", "よんで", "verb", "Membaca (te-form)", "N5"),
        LexItem("読む", "よむ", "verb", "Membaca", "N5"),
        LexItem("書きます", "かきます", "verb", "Menulis (sopan)", "N5"),
        LexItem("書いて", "かいて", "verb", "Menulis (te-form)", "N5"),
        LexItem("書く", "かく", "verb", "Menulis", "N5"),
        LexItem("買います", "かいます", "verb", "Membeli (sopan)", "N5"),
        LexItem("買って", "かって", "verb", "Membeli (te-form)", "N5"),
        LexItem("買う", "かう", "verb", "Membeli", "N5"),
        LexItem("進んでいますか", "すすんでいますか", "verb", "Apakah mengalami kemajuan?", "N4"),
        LexItem("進んでいます", "すすんでいます", "verb", "Sedang mengalami kemajuan", "N4"),
        LexItem("進む", "すすむ", "verb", "Maju / berproses", "N4"),
        LexItem("あれば", "あれば", "verb", "Jika ada", "N4"),
        LexItem("あります", "あります", "verb", "Ada (benda mati, sopan)", "N5"),
        LexItem("ある", "ある", "verb", "Ada (benda mati)", "N5"),
        LexItem("います", "います", "verb", "Ada (makhluk hidup, sopan)", "N5"),
        LexItem("いる", "いる", "verb", "Ada (makhluk hidup)", "N5"),
        LexItem("待っています", "まっています", "verb", "Sedang menunggu", "N5"),
        LexItem("待ちます", "まちます", "verb", "Menunggu (sopan)", "N5"),
        LexItem("待つ", "まつ", "verb", "Menunggu", "N5"),
        LexItem("降る", "ふる", "verb", "Turun (hujan/salju)", "N5"),
        LexItem("演じる", "えんじる", "verb", "Memerankan", "N2"),
        LexItem("します", "します", "verb", "Melakukan (sopan)", "N5"),
        LexItem("して", "して", "verb", "Melakukan (te-form)", "N5"),
        LexItem("する", "する", "verb", "Melakukan", "N5"),

        // Adjectives
        LexItem("新しい", "あたらしい", "i-adj", "Baru", "N5"),
        LexItem("古い", "ふるい", "i-adj", "Lama / kuno", "N5"),
        LexItem("大きい", "おおきい", "i-adj", "Besar", "N5"),
        LexItem("小さい", "ちいさい", "i-adj", "Kecil", "N5"),
        LexItem("高い", "たかい", "i-adj", "Tinggi / mahal", "N5"),
        LexItem("安い", "やすい", "i-adj", "Murah", "N5"),
        LexItem("いい", "いい", "i-adj", "Bagus / baik", "N5"),
        LexItem("良い", "よい", "i-adj", "Bagus / baik", "N5"),
        LexItem("きれい", "きれい", "na-adj", "Indah / bersih", "N5"),
        LexItem("静か", "しずか", "na-adj", "Tenang", "N5"),
        LexItem("美味しい", "おいしい", "i-adj", "Lezat / enak", "N5"),
        LexItem("楽しい", "たのしい", "i-adj", "Menyenangkan", "N5"),
        LexItem("面白い", "おもしろい", "i-adj", "Menarik", "N5"),
        LexItem("好き", "すき", "na-adj", "Suka", "N5"),
        LexItem("上手", "じょうず", "na-adj", "Mahir / pandai", "N5"),

        // Adverbs & Expressions
        LexItem("とても", "とても", "adverb", "Sangat", "N5"),
        LexItem("もし", "もし", "adverb", "Jika / seandainya", "N5"),
        LexItem("はい", "はい", "expression", "Ya", "N5"),
        LexItem("いいえ", "いいえ", "expression", "Tidak", "N5"),
        LexItem("ぜひ", "ぜひ", "adverb", "Tentu saja / pasti", "N4"),
        LexItem("一緒に", "いっしょに", "adverb", "Bersama-sama", "N5"),
        LexItem("お疲れ様です", "おつかれさまです", "expression", "Terima kasih atas kerja kerasnya", "N4"),

        // Particles & Functional Words
        LexItem("は", "は", "particle", "Partikel topik (dibaca 'wa')", "N5"),
        LexItem("が", "が", "particle", "Partikel subjek", "N5"),
        LexItem("の", "の", "particle", "Partikel kepemilikan / asosiasi", "N5"),
        LexItem("に", "に", "particle", "Partikel waktu / arah / target", "N5"),
        LexItem("で", "で", "particle", "Partikel lokasi kegiatan / alat", "N5"),
        LexItem("を", "を", "particle", "Partikel objek langsung", "N5"),
        LexItem("へ", "へ", "particle", "Partikel arah tujuan (dibaca 'e')", "N5"),
        LexItem("と", "と", "particle", "Partikel 'dan' / 'bersama'", "N5"),
        LexItem("ね", "ね", "particle", "Partikel penegas 'bukan begitu?'", "N5"),
        LexItem("よ", "よ", "particle", "Partikel penegas info baru", "N5"),
        LexItem("か", "か", "particle", "Partikel tanda tanya", "N5"),
        LexItem("も", "も", "particle", "Partikel 'juga' / 'pun'", "N5"),
        LexItem("から", "から", "particle", "Partikel 'dari' / 'karena'", "N5"),
        LexItem("まで", "まで", "particle", "Partikel 'sampai'", "N5"),
        LexItem("より", "より", "particle", "Partikel perbandingan 'daripada'", "N5"),
        LexItem("です", "です", "copula", "Kopula penegas sopan (adalah)", "N5"),
        LexItem("でした", "でした", "copula", "Kopula lampau sopan", "N5"),
        LexItem("だ", "だ", "copula", "Kopula biasa", "N5"),
        LexItem("という", "という", "particle", "Yang dinamakan / disebut", "N4"),
        LexItem("、", "", "punct", "Koma", "-"),
        LexItem("。", "", "punct", "Titik", "-"),
        LexItem("？", "", "punct", "Tanda tanya", "-"),
        LexItem("！", "", "punct", "Tanda seru", "-")
    ).sortedByDescending { it.surface.length }

    fun isKanji(c: Char): Boolean {
        val cp = c.code
        return (cp in 0x4E00..0x9FAF) || (cp in 0x3400..0x4DBF)
    }

    fun isKatakana(c: Char): Boolean {
        val cp = c.code
        return (cp in 0x30A0..0x30FF) || c == 'ー'
    }

    fun isHiragana(c: Char): Boolean {
        val cp = c.code
        return cp in 0x3040..0x309F
    }

    fun katakanaToHiragana(c: Char): Char {
        return if (c in '\u30A1'..'\u30FA') (c.code - 0x60).toChar() else c
    }

    fun kanaToRomaji(kana: String): String {
        val sb = StringBuilder()
        var idx = 0
        while (idx < kana.length) {
            val c = kana[idx]
            if (c == 'ー') {
                if (sb.isNotEmpty()) sb.append(sb.last())
                idx++
                continue
            }
            val hiraChar = katakanaToHiragana(c)
            if (idx + 1 < kana.length) {
                val nextHira = katakanaToHiragana(kana[idx + 1])
                val combo = "$hiraChar$nextHira"
                if (KANA_ROMAJI.containsKey(combo)) {
                    sb.append(KANA_ROMAJI[combo])
                    idx += 2
                    continue
                }
            }
            sb.append(KANA_ROMAJI[hiraChar.toString()] ?: hiraChar.toString())
            idx++
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
                val ch = text[i]
                when {
                    isKatakana(ch) -> {
                        var end = i
                        while (end < text.length && isKatakana(text[end])) {
                            end++
                        }
                        val kataWord = text.substring(i, end)
                        val kataRomaji = kanaToRomaji(kataWord)
                        result.add(
                            TokenDto(
                                surface = kataWord,
                                reading = kataWord,
                                romaji = kataRomaji,
                                pos = "noun",
                                meaning = kataWord,
                                jlpt = "N4"
                            )
                        )
                        i = end
                    }
                    isKanji(ch) -> {
                        var end = i
                        while (end < text.length && isKanji(text[end])) {
                            end++
                        }
                        val kanjiWord = text.substring(i, end)
                        val readingSb = StringBuilder()
                        for (k in kanjiWord) {
                            readingSb.append(KANJI_READINGS[k] ?: "")
                        }
                        val reading = if (readingSb.isNotEmpty()) readingSb.toString() else kanjiWord
                        val romaji = kanaToRomaji(reading)
                        val jlpt = when {
                            kanjiWord.any { N2_N1_KANJI.contains(it) } -> "N2"
                            kanjiWord.any { N3_KANJI.contains(it) } -> "N3"
                            kanjiWord.any { N4_KANJI.contains(it) } -> "N4"
                            else -> "N5"
                        }
                        result.add(
                            TokenDto(
                                surface = kanjiWord,
                                reading = reading,
                                romaji = romaji,
                                pos = "noun",
                                meaning = "Kosakata Kanji: $kanjiWord",
                                jlpt = jlpt
                            )
                        )
                        i = end
                    }
                    ch.isLetterOrDigit() && ch.code < 0x3000 -> {
                        var end = i
                        while (end < text.length && text[end].isLetterOrDigit() && text[end].code < 0x3000) {
                            end++
                        }
                        val word = text.substring(i, end)
                        result.add(
                            TokenDto(
                                surface = word,
                                reading = word,
                                romaji = word,
                                pos = "other",
                                meaning = word,
                                jlpt = "-"
                            )
                        )
                        i = end
                    }
                    else -> {
                        val singleChar = text[i].toString()
                        result.add(
                            TokenDto(
                                surface = singleChar,
                                reading = singleChar,
                                romaji = kanaToRomaji(singleChar),
                                pos = if (isHiragana(ch)) "particle" else "punct",
                                meaning = "",
                                jlpt = "-"
                            )
                        )
                        i++
                    }
                }
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
        if (t.contains("友達と映画を見に行きます")) return "Besok pergi menonton film bersama teman."
        if (t.contains("映画を見に行きます")) return "Pergi untuk menonton film."
        if (t.contains("来週の試験、準備は進んでいますか")) return "Apakah persiapan untuk ujian minggu depan berjalan lancar?"
        if (t.contains("秋葉原のカフェに行きませんか")) return "Maukah pergi ke kafe di Akihabara?"
        if (t.contains("一緒に行きましょう")) return "Ya, mari kita pergi bersama-sama!"

        // Contextual keyword extraction for unknown sentences
        val tokens = tokenize(t)
        val keywords = tokens.filter {
            it.pos in listOf("noun", "verb", "i-adj", "na-adj") &&
            !it.meaning.isNullOrBlank() &&
            it.meaning?.startsWith("Kanji:") == false &&
            it.surface !in listOf("は", "が", "の", "に", "で", "を", "と", "です", "ます", "、", "。")
        }
        if (keywords.isNotEmpty()) {
            val summary = keywords.take(4).joinToString(", ") { "${it.surface}: ${it.meaning}" }
            return "Konteks: $summary"
        }
        return "Konteks bacaan: $t"
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
                        var clean = content.trim()
                        if (clean.startsWith("```")) {
                            clean = clean.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("\\s*```$"), "").trim()
                        }
                        val firstBrace = clean.indexOf('{')
                        val lastBrace = clean.lastIndexOf('}')
                        if (firstBrace != -1 && lastBrace != -1) {
                            clean = clean.substring(firstBrace, lastBrace + 1)
                        }
                        return@withContext gson.fromJson(clean, AnalyzeResponseDto::class.java)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
