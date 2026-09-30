package com.japanesereader.ai

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
}
