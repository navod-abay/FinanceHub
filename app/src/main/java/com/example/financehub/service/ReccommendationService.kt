package com.example.financehub.service

import android.util.Log
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.ExpenseRepositoryInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

class ReccommendationService(private val repository: ExpenseRepositoryInterface) {
    suspend fun getTagReccomendations(TagId: Int): List<TagRef> {
        val alpha = 0.8
        var iterationDepth = 20
        val tags = repository.getAllTagRefs().first().sortedByDescending { it.tagID }
        val numTags = tags.size
        Log.d("ReccomendationService", "numTags: $numTags")
        Log.d("ReccomendationService", "tags: $tags")
        val lastId = tags[0].tagID
        Log.d("ReccomendationService", "lastId: $lastId")
        val graph = Array(lastId) { DoubleArray(lastId) }
        val graphEdges = repository.getAllGraphEdges().stateIn(
            scope = CoroutineScope(Dispatchers.IO),
        ).value
        graphEdges.forEach {
            graph[it.fromTagId - 1][it.toTagId - 1] = it.weight.toDouble()
        }
        graph.forEachIndexed { rowIndex, row ->
            val rowSum = row.sum()
            if (rowSum > 0) {
                row.forEachIndexed { colIndex, _ ->
                    graph[rowIndex][colIndex] = graph[rowIndex][colIndex] / rowSum
                }
            } else {
                row.forEachIndexed { colIndex, _ ->
                    graph[rowIndex][colIndex] = 0.0
                }
            }
        }
        println("\n\nGenerating reccommendations for tagId: $TagId")
        val visitedNodes = mutableMapOf<Int, Int>()
        var curNode = TagId - 1
        while (iterationDepth > 0) {
            // println("Iteration depth: $iterationDepth")
            while (true) {
                if (curNode == TagId - 1 && Random.nextDouble() < alpha) {
                    // println("Moving to another node")
                    val walkedNodes = BooleanArray(lastId) { false }
                    walkedNodes[curNode] = true
                    val prob = Random.nextDouble()
                    var cumulativeProb = 0.0
                    var i = 0
                    while (i < lastId) {
                        cumulativeProb += graph[curNode][i]
                        // println("Cumulative prob: $cumulativeProb, prob: $prob")
                        if (prob < cumulativeProb && !walkedNodes[i]) {
                            // println("Moving to node $i + 1")
                            curNode = i
                            walkedNodes[i] = true
                            break
                        }
                        i++
                    }
                    if (i == lastId) {
                        // print("Walk ended cuz of dead end")
                        break
                    }
                    visitedNodes[curNode + 1] = (visitedNodes[curNode + 1] ?: 0) + 1
                } else {
                    // println("Restarting at original node, Previous node ${curNode + 1}")
                    curNode = TagId - 1
                    break
                }
            }
            iterationDepth--
        }
        val reccommendedTagRefs = tags.filter {
            visitedNodes.containsKey(it.tagID)
        }.sortedByDescending { visitedNodes[it.tagID] }
        Log.d("ReccomendationService", "Reccommended tags: $reccommendedTagRefs")
        return reccommendedTagRefs
    }
}