package com.japanesereader.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
