package com.japanesereader.ai

import org.junit.Assert.*
import org.junit.Test

class ArticleCrudTest {

    data class MockArticle(
        val id: String,
        val title: String,
        val category: String,
        val rawText: String,
        val difficultyLevel: String
    )

    @Test
    fun testArticleCreationAndFiltering() {
        val list = mutableListOf<MockArticle>()

        val art1 = MockArticle("art_1", "Percakapan LINE", "Percakapan", "こんにちは", "N5")
        val art2 = MockArticle("art_2", "Berita NHK", "Buku & Artikel", "東京の春", "N4")
        list.add(art1)
        list.add(art2)

        assertEquals(2, list.size)

        val conversationArticles = list.filter { it.category == "Percakapan" }
        assertEquals(1, conversationArticles.size)
        assertEquals("art_1", conversationArticles[0].id)

        // Delete operation
        list.removeIf { it.id == "art_1" }
        assertEquals(1, list.size)
        assertEquals("art_2", list[0].id)
    }
}
