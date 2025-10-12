package com.example.financehub

import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.MockExpenseRepository
import org.junit.Test
import com.example.financehub.service.ReccommendationService
import kotlinx.coroutines.test.runTest

class ReccommendationServiceUnitTest {
    private val mockRepo = MockExpenseRepository()
    private val service = ReccommendationService(mockRepo)

    @Test
    fun `Food recs aren't empty`() = runTest {
        val reccs = service.getTagReccomendations(2)
        assert(reccs.isNotEmpty())
    }

    @Test
    fun `Food reccs gives snack`() = runTest{
        val reccs = service.getTagReccomendations(2)
        assert(reccs.contains(TagRef(5, "Snacks")))
    }

    @Test
    fun `Food reccs contains Meal`() = runTest{
        val reccs = service.getTagReccomendations(2)
        assert(reccs.contains(TagRef(3, "Meal")))
    }

    @Test
    fun `Transportation reccs is not empty`()= runTest {
        val reccs = service.getTagReccomendations(6)
        assert(reccs.isNotEmpty())
    }

    @Test
    fun `Transportation reccs contains AIESEC`()= runTest {
        val reccs = service.getTagReccomendations(6)
        assert(reccs.contains(TagRef(1, "AIESEC")))
    }

    @Test
    fun `Transportation reccs contains Bus`()= runTest {
        val reccs = service.getTagReccomendations(6)
        assert(reccs.contains(TagRef(14, "Bus")))
    }

}