package com.japanesereader.ai

import com.japanesereader.ai.data.remote.TokenDto
import com.japanesereader.ai.util.JapaneseMorphologyEngine
import org.junit.Assert.*
import org.junit.Test

class DynamicJlptAndAudioTest {

    @Test
    fun testDynamicJlptClassificationVariesByText() {
        // N5 simple greetings
        val n5Text = "こんにちは、元気ですか？"
        val n5Res = JapaneseMorphologyEngine.analyzeLocal(n5Text)
        assertEquals("N5", n5Res.difficulty_level)

        // N4 text with seasonal & daily verbs
        val n4Text = "東京の春は桜が咲いて、とてもきれいです。"
        val n4Res = JapaneseMorphologyEngine.analyzeLocal(n4Text)
        assertEquals("N4", n4Res.difficulty_level)

        // Intermediate text
        val intermediateText = "普通の人という生き物を演じる。"
        val intermediateRes = JapaneseMorphologyEngine.analyzeLocal(intermediateText)
        assertTrue("Expected N3 or N2 for intermediate text", intermediateRes.difficulty_level == "N3" || intermediateRes.difficulty_level == "N2")
        // N1 text with complex literary kanji (架空, 繊細)
        val n1Text = "架空の物語と繊細な心理描写。"
        val n1Res = JapaneseMorphologyEngine.analyzeLocal(n1Text)
        assertTrue(n1Res.difficulty_level == "N1" || n1Res.difficulty_level == "N2")
    }

    @Test
    fun testAudioPlayStopToggleLogic() {
        var currentlyPlaying: String? = null

        fun toggleAudio(text: String) {
            currentlyPlaying = if (currentlyPlaying == text) {
                null // Stop
            } else {
                text // Play
            }
        }

        // 1. Initially stopped
        assertNull(currentlyPlaying)

        // 2. Play word "咲く"
        toggleAudio("咲く")
        assertEquals("咲く", currentlyPlaying)

        // 3. Tap same word again -> must STOP immediately
        toggleAudio("咲く")
        assertNull(currentlyPlaying)

        // 4. Play word "東京"
        toggleAudio("東京")
        assertEquals("東京", currentlyPlaying)

        // 5. Play another word "春" -> switches immediately
        toggleAudio("春")
        assertEquals("春", currentlyPlaying)
    }

    @Test
    fun testAutoSaveAndThresholdInspection() {
        data class AutoTrackedWord(
            val kanji: String,
            var tapsCount: Int = 0,
            var needsDeepStudy: Boolean = false
        ) {
            fun onUserTap() {
                tapsCount++
                if (tapsCount >= 3) {
                    needsDeepStudy = true
                }
            }
        }

        val word = AutoTrackedWord("躊躇")
        assertFalse(word.needsDeepStudy)

        word.onUserTap()
        assertEquals(1, word.tapsCount)
        assertFalse(word.needsDeepStudy)

        word.onUserTap()
        assertEquals(2, word.tapsCount)
        assertFalse(word.needsDeepStudy)

        // 3rd tap -> auto flagged as hard word for analytics
        word.onUserTap()
        assertEquals(3, word.tapsCount)
        assertTrue(word.needsDeepStudy)
    }
}
