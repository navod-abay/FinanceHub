package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.Tags
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Date
import com.example.financehub.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn


class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _monthlyTotal = MutableStateFlow(0)

    fun addExpense(title: String, amount: Int, tags: Set<String>) {
        viewModelScope.launch {
            val date = Date() // Current date
            val (day, month, year) = getDateComponents(date)
            repository.insertExpense(Expense(title = title, amount = amount, date = day, month = month, year = year), tags = tags)
        }
    }

    private val _query = MutableStateFlow("")
    val matchingTags: StateFlow<List<Tags>> = _query
        .debounce(300) // Wait for user to stop typing
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.getMatchingTags(query)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

}
