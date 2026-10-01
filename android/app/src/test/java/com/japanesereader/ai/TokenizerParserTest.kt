package com.japanesereader.ai

import com.japanesereader.ai.util.JapaneseMorphologyEngine
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class TokenizerParserTest {

    private fun isKanji(c: Char): Boolean {
        val codePoint = c.code
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }

    private fun calculateKanjiRatio(text: String): Double {
        var kanjiCount = 0
        var total = 0
        for (c in text) {
            if (c.isWhitespace() || c == '。' || c == '、' || c == '！' || c == '？') continue
            total++
            if (isKanji(c)) kanjiCount++
        }
        return if (total > 0) Math.round((kanjiCount.toDouble() / total.toDouble()) * 100.0) / 100.0 else 0.0
    }

    @Test
    fun testKanjiDetectionAndRatioCalculation() {
        val sampleText = "東京の春は花が咲いて、とてもきれいです。"
        val ratio = calculateKanjiRatio(sampleText)

        assertTrue("Kanji ratio should be greater than 0", ratio > 0.1)
        assertTrue(isKanji('東'))
        assertTrue(isKanji('京'))
        assertTrue(isKanji('花'))
        assertFalse(isKanji('の'))
        assertFalse(isKanji('は'))
    }

    @Test
    fun testFuriganaPayloadParsing() {
        val payload = """[
            {"surface":"東京","reading":"とうきょう","pos":"noun","jlpt":"N5"},
            {"surface":"の","reading":"の","pos":"particle","jlpt":"N5"},
            {"surface":"春","reading":"はる","pos":"noun","jlpt":"N5"}
        ]"""

        val jsonArray = JSONArray(payload)
        assertEquals(3, jsonArray.length())

        val token1 = jsonArray.getJSONObject(0)
        assertEquals("東京", token1.getString("surface"))
        assertEquals("とうきょう", token1.getString("reading"))
        assertEquals("noun", token1.getString("pos"))
    }

    @Test
    fun testCompoundWordTokenization() {
        val sentence = "明日は友達と映画を見に行きます。"
        val tokens = JapaneseMorphologyEngine.tokenize(sentence)

        // Ensure 友達 and 映画 are single compound tokens, not split into 友, 達, 映, 画
        val surfaces = tokens.map { it.surface }
        assertTrue("Expected '友達' in surfaces: $surfaces", surfaces.contains("友達"))
        assertTrue("Expected '映画' in surfaces: $surfaces", surfaces.contains("映画"))
        assertFalse("Should not contain split '友'", surfaces.contains("友"))
        assertFalse("Should not contain split '達'", surfaces.contains("達"))

        // Check reading for 友達
        val tomodachi = tokens.find { it.surface == "友達" }
        assertNotNull(tomodachi)
        assertEquals("ともだち", tomodachi?.reading)
        assertNotEquals(tomodachi?.surface, tomodachi?.reading)
    }

    @Test
    fun testKanjiAlwaysHasHiraganaReading() {
        val sampleWithKanji = "学校の先生と日本語を勉強します。"
        val tokens = JapaneseMorphologyEngine.tokenize(sampleWithKanji)

        for (token in tokens) {
            val containsKanji = token.surface.any { isKanji(it) }
            if (containsKanji) {
                // Reading must not be identical to kanji surface!
                assertNotEquals("Kanji token '${token.surface}' must have distinct hiragana reading!", token.surface, token.reading)
                assertTrue("Reading for '${token.surface}' should not be empty", !token.reading.isNullOrBlank())
            }
        }
    }

    @Test
    fun testFallbackTranslationIsMeaningful() {
        val text = "明日は友達と映画を見に行きます。"
        val translation = JapaneseMorphologyEngine.translate(text)
        assertFalse("Translation should not echo raw text", translation.contains("Arti konteks: 明日は友達と映画を見に行きます。"))
        assertTrue("Translation should be in Indonesian: $translation", translation.contains("film") || translation.contains("teman") || translation.contains("Besok") || translation.contains("Konteks"))
    }
}
