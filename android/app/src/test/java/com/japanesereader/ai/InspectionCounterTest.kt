package com.japanesereader.ai

import org.junit.Assert.*
import org.junit.Test

class InspectionCounterTest {

    data class MockSentence(
        val id: String,
        var inspectionCount: Int = 0,
        var needsDeepStudy: Boolean = false
    ) {
        fun inspect() {
            inspectionCount++
            if (inspectionCount >= 3) {
                needsDeepStudy = true
            }
        }
    }

    @Test
    fun testInspectionCounterTriggersDeepStudyAtThree() {
        val sentence = MockSentence("sent_01", inspectionCount = 0)

        // 1st inspect
        sentence.inspect()
        assertEquals(1, sentence.inspectionCount)
        assertFalse(sentence.needsDeepStudy)

        // 2nd inspect
        sentence.inspect()
        assertEquals(2, sentence.inspectionCount)
        assertFalse(sentence.needsDeepStudy)

        // 3rd inspect -> Threshold reached!
        sentence.inspect()
        assertEquals(3, sentence.inspectionCount)
        assertTrue(sentence.needsDeepStudy)
    }
}
