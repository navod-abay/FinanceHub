package com.example.financehub

import com.example.financehub.data.repository.MockExpenseRepository
import org.junit.Test
import com.example.financehub.service.ReccommendationService

class ReccommendationServiceUnitTest {
    @Test
    public suspend fun testReccomendationService() {
        val mockRepo = MockExpenseRepository()
        val service = ReccommendationService(mockRepo)
        val reccs = service.getTagReccomendations(0)
        assert(reccs.isNotEmpty())
    }
}