package com.example.financehub.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.TagWithAmount
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    init {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        viewModelScope.launch {
            combine(
                repository.getCurrentMonthTotal(currentMonth, currentYear),
                repository.getTopTagForMonth(currentMonth, currentYear)
            ) { total, topTag ->
                Pair(total, topTag)
            }.map { (total, topTag) ->
                _monthlyTotal.value = total
                _highestTag.value = TagWithAmount(topTag.tag, 0)
            }.collect {}
        }
    }
}
