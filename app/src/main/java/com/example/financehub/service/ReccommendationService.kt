package com.example.financehub.service

import com.example.financehub.data.repository.ExpenseRepositoryInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

class ReccommendationService (private val repository: ExpenseRepositoryInterface) {
    public suspend fun getTagReccomendations(TagId: Int) : List<Int> {
        val alpha = 0.8
        var iterationDepth = 5
        val tags = repository.getAllTags().stateIn(
            scope = CoroutineScope(Dispatchers.IO),
        ).value
        val numTags = tags.size
        val lastId = tags[numTags - 1].tagID
        val graph = Array(numTags) { IntArray(numTags) }
        val graphEdges = repository.getAllGraphEdges().stateIn(
            scope = CoroutineScope(Dispatchers.IO),
        ).value
        graphEdges.forEachIndexed{ index, it ->
            graph[it.fromTagID][it.toTagID] = it.weight
        }
        graph.map { row -> {
            val rowSum = row.sum()
            if (rowSum > 0) {
                row.map { it.toDouble() / rowSum }
            } else {
                row.map { 0.0 }
            }
        } }
        val visitedNodes = mutableMapOf<Int, Int>()
        var curNode = TagId
        while (iterationDepth > 0) {
            if (Random.nextDouble() < alpha) {
                val prob = Random.nextDouble()
                var cumulativeProb = 0.0
                for (i in 0..lastId) {
                    cumulativeProb += graph[curNode][i]
                    if (prob < cumulativeProb) {
                        curNode = i
                        break
                    }
                }
                visitedNodes[curNode] = (visitedNodes[curNode] ?: 0) + 1
            } else {
                break
            }
            iterationDepth--
        }
        return visitedNodes.toList().sortedByDescending { (_, value) -> value }.toMap().keys.toList()
    }
}