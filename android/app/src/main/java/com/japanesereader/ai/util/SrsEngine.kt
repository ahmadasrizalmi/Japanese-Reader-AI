package com.japanesereader.ai.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

data class SrsResult(
    val masteryStatus: Int, // 0: Baru, 1: Dipelajari, 2: Dikuasai
    val nextReviewAt: Long,
    val reviewCount: Int,
    val intervalDays: Long
)

object SrsEngine {
    private const val ONE_DAY_MS = 86_400_000L

    /**
     * Calculates the next review schedule based on SuperMemo-2 (SM-2) algorithm.
     *
     * @param currentReviews Number of successful reviews so far.
     * @param quality User rating 0..5 (e.g. Ulangi = 1, Bagus = 3, Mudah = 5).
     * @param needsDeepStudy If true (C_inspect >= 3), applies attention penalty to ensure higher frequency.
     * @param now Current timestamp in milliseconds.
     */
    fun calculateNextReview(
        currentReviews: Int,
        quality: Int,
        needsDeepStudy: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): SrsResult {
        // Apply inspection threshold penalty if sentence was flagged as difficult
        val effectiveQuality = if (needsDeepStudy) {
            max(1, quality - 1)
        } else {
            quality
        }

        if (effectiveQuality < 3) {
            // Failed recall: reset interval to 1 day, status back to Baru (0)
            return SrsResult(
                masteryStatus = 0,
                nextReviewAt = now + ONE_DAY_MS,
                reviewCount = currentReviews + 1,
                intervalDays = 1L
            )
        }

        // Successful recall (q >= 3)
        val days: Long = when (currentReviews) {
            0 -> 1L
            1 -> if (needsDeepStudy) 3L else 6L
            else -> {
                // EF calculation: default 2.5 with quality adjustment
                val qFactor = (5 - effectiveQuality).toDouble()
                val calculatedEf = max(1.3, 2.5 + (0.1 - qFactor * (0.08 + qFactor * 0.02)))
                val exponent = (currentReviews - 1).toDouble()
                val base = if (needsDeepStudy) 4.0 else 6.0
                (base * calculatedEf.pow(exponent)).roundToLong().coerceAtLeast(2L)
            }
        }

        val masteryStatus = when {
            currentReviews >= 2 -> 2 // Dikuasai
            else -> 1 // Dipelajari
        }

        return SrsResult(
            masteryStatus = masteryStatus,
            nextReviewAt = now + (days * ONE_DAY_MS),
            reviewCount = currentReviews + 1,
            intervalDays = days
        )
    }

    /**
     * Initial interval for newly added words.
     * If the source sentence was heavily inspected (C_inspect >= 3),
     * the word needs to be reviewed sooner (12 hours vs 24 hours).
     */
    fun initialInterval(needsDeepStudy: Boolean = false, now: Long = System.currentTimeMillis()): Long {
        val delayMs = if (needsDeepStudy) 43_200_000L else ONE_DAY_MS
        return now + delayMs
    }
}
