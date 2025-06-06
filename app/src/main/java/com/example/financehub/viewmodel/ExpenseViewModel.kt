package com.example.financehub.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.Tags
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Date


class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    var name : MutableState<String> = mutableStateOf("")
    var amount : MutableState<String> = mutableStateOf("")

    val date = Date() // Current date
    val dateTuple = com.example.financehub.util.getDateComponents(date)
    var dayState: MutableState<String> = mutableStateOf(dateTuple.first.toString())
    var monthState: MutableState<String> = mutableStateOf(dateTuple.second.toString())
    var yearState: MutableState<String> = mutableStateOf(dateTuple.third.toString())



    fun addExpense(tags: Set<String> ) {
        viewModelScope.launch {
            if (name.value.isBlank() || amount.value.isBlank()) return@launch
            val day: Int = if(dayState.value.isBlank())  dateTuple.first else dayState.value.toInt()
            val month: Int = if(monthState.value.isBlank())  dateTuple.second else monthState.value.toInt()
            val year: Int = if(yearState.value.isBlank())  dateTuple.third else yearState.value.toInt()
            repository.insertExpense(Expense(title = name.value, amount = amount.value.toInt(), date = day, month = month, year = year), tags = tags)
        }
    }

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
