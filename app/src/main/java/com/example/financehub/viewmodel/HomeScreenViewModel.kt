package com.example.financehub.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.TagWithAmount
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import java.util.Calendar


class HomeScreenViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private  val _highestTag = MutableStateFlow<TagWithAmount>(TagWithAmount("Loading...", 0))
    val highestTag: StateFlow<TagWithAmount> = _highestTag

    // LiveData for monthly total
    private val _monthlyTotal = MutableStateFlow<Int>(0)
    val monthlyTotal: StateFlow<Int> = _monthlyTotal

    private val _missedTargets = MutableStateFlow(0)
    val missedTargets: StateFlow<Int> = _missedTargets
    private val _totalTargets = MutableStateFlow(0)
    val totalTargets: StateFlow<Int> = _totalTargets

    val missedFraction: StateFlow<Float> = combine(_missedTargets, _totalTargets) { missed, total ->
        if (total == 0) 0f else missed.toFloat() / total.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    init {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        viewModelScope.launch {
            combine(
                repository.getCurrentMonthTotal(currentMonth + 1, currentYear),
                repository.getTopTagForMonth(currentMonth, currentYear)
            ) { total, topTag ->
                Pair(total, topTag)
            }.map { (total, topTag) ->
                _monthlyTotal.value = total
                _highestTag.value = TagWithAmount(topTag.tag, 0)
            }.collect {}
        }
        viewModelScope.launch {
            repository.getMonthlyTargetsStats().collect { (missed, total) ->
                _missedTargets.value = missed
                _totalTargets.value = total
            }
        }
    }
}
