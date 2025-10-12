package com.example.financehub.data.repository

import com.example.financehub.data.database.GraphEdge
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.ExpenseRepositoryInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.*
import java.io.File

class MockExpenseRepository: ExpenseRepositoryInterface {

    override fun getAllTags(): Flow<List<Tags>> {

        val inputStream = javaClass.classLoader!!
            .getResourceAsStream("tags.json")
        val text = inputStream.bufferedReader().use { it.readText() }

        val tags = Json.decodeFromString<List<Tags>>(text)
        return listOf(tags).asFlow()
    }

    override suspend fun getAllTagRefs(): Flow<List<TagRef>> {
        val inputStream = javaClass.classLoader!!
            .getResourceAsStream("tags.json")
        val text = inputStream.bufferedReader().use { it.readText() }
        val tags = Json.decodeFromString<List<Tags>>(text)
        return tags.map { TagRef(it.tagID, it.tag) }.let { listOf(it).asFlow() }
    }

    override suspend fun getAllGraphEdges(): Flow<List<GraphEdge>> {
        val inputStream = javaClass.classLoader!!
            .getResourceAsStream("graphEdges.json")
        val text = inputStream.bufferedReader().use { it.readText() }

        val edges = Json.decodeFromString<List<GraphEdge>>(text)
        return listOf(edges).asFlow()
    }


}