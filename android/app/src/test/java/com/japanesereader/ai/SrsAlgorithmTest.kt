package com.japanesereader.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.japanesereader.ai.util.SrsEngine

class SrsAlgorithmTest {

    private fun calculateNextSrs(currentReviews: Int, quality: Int): Pair<Int, Long> {
        val now = System.currentTimeMillis()
        var days = 1L
        var mastery = 0

        if (quality >= 3) {
            when (currentReviews) {
                0 -> {
                    days = 1L
                    mastery = 1 // Dipelajari
                }
                1 -> {
                    days = 6L
                    mastery = 1
                }
                else -> {
                    days = Math.round(6.0 * Math.pow(1.8, (currentReviews - 1).toDouble()))
                    mastery = 2 // Dikuasai
                }
            }
        } else {
            days = 1L
            mastery = 0 // Reset to Baru
        }

        return Pair(mastery, now + days * 86400000L)
    }

    @Test
    fun testFirstSuccessfulReviewAdvancesToStudied() {
        val (mastery, nextReview) = calculateNextSrs(0, 4)
        assertEquals(1, mastery)
        assertTrue(nextReview > System.currentTimeMillis())
    }

    @Test
    fun testRepeatedSuccessfulReviewAdvancesToMastered() {
        val (mastery, nextReview) = calculateNextSrs(2, 5)
        assertEquals(2, mastery)
        assertTrue(nextReview > System.currentTimeMillis() + 86400000L)
    }

    @Test
    fun testFailedReviewResetsToNew() {
        val (mastery, _) = calculateNextSrs(5, 1)
        assertEquals(0, mastery)
    }

    @Test
    fun testSrsEngineCalculatesIntervalsAndPenalty() {
        val now = System.currentTimeMillis()

        // Normal word first review
        val normalR1 = SrsEngine.calculateNextReview(currentReviews = 0, quality = 4, needsDeepStudy = false, now = now)
        assertEquals(1, normalR1.masteryStatus)
        assertEquals(1L, normalR1.intervalDays)

        // Second review normal
        val normalR2 = SrsEngine.calculateNextReview(currentReviews = 1, quality = 4, needsDeepStudy = false, now = now)
        assertEquals(1, normalR2.masteryStatus)
        assertEquals(6L, normalR2.intervalDays)

        // Second review with needsDeepStudy (penalized to 3 days)
        val deepR2 = SrsEngine.calculateNextReview(currentReviews = 1, quality = 4, needsDeepStudy = true, now = now)
        assertEquals(1, deepR2.masteryStatus)
        assertEquals(3L, deepR2.intervalDays)

        // Third review advances to Mastered (2)
        val normalR3 = SrsEngine.calculateNextReview(currentReviews = 2, quality = 5, needsDeepStudy = false, now = now)
        assertEquals(2, normalR3.masteryStatus)
        assertTrue(normalR3.intervalDays >= 10L)

        // Failed review resets to 0 (Baru)
        val failed = SrsEngine.calculateNextReview(currentReviews = 3, quality = 1, needsDeepStudy = false, now = now)
        assertEquals(0, failed.masteryStatus)
        assertEquals(1L, failed.intervalDays)
    }
}
