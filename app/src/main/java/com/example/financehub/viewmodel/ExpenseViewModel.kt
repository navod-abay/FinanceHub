package com.example.financehub.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.Tags
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.service.ReccommendationService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Date


class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    var name: MutableState<String> = mutableStateOf("")
    var amount: MutableState<String> = mutableStateOf("")

    val date = Date() // Current date
    val dateTuple = com.example.financehub.util.getDateComponents(date)
    var dayState: MutableState<String> = mutableStateOf(dateTuple.first.toString())
    var monthState: MutableState<String> = mutableStateOf(dateTuple.second.toString())
    var yearState: MutableState<String> = mutableStateOf(dateTuple.third.toString())

    // Shared states for both Expense and Target forms
    var selectedTag: MutableState<Tags?> = mutableStateOf(null)
    var targetAmount: MutableState<String> = mutableStateOf("")

    private val _reccommendedTags = MutableStateFlow<List<Int>>(emptyList())
    val reccommendedTags: StateFlow<List<Int>> = _reccommendedTags.asStateFlow()


    fun addExpense(tags: Set<Tags>, newTags: List<String>) {
        viewModelScope.launch {
            if (name.value.isBlank() || amount.value.isBlank()) return@launch
            val day: Int = if (dayState.value.isBlank()) dateTuple.first else dayState.value.toInt()
            val month: Int =
                if (monthState.value.isBlank()) dateTuple.second else monthState.value.toInt()
            val year: Int =
                if (yearState.value.isBlank()) dateTuple.third else yearState.value.toInt()
            repository.insertExpense(
                Expense(
                    title = name.value,
                    amount = amount.value.toInt(),
                    date = day,
                    month = month,
                    year = year
                ),
                tags = tags,
                newTags = newTags
            )
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

    var targetError: MutableState<String?> = mutableStateOf(null)

    fun addTarget() {
        val month = monthState.value
        val year = yearState.value
        val tag = selectedTag.value
        val amount = targetAmount.value
        val currentYear = dateTuple.third
        val currentMonth = dateTuple.second

        // Validation
        if (month.isBlank() || year.isBlank() || tag == null || amount.isBlank()) {
            targetError.value = "All fields are required."
            return
        }
        val monthInt = month.toIntOrNull()
        val yearInt = year.toIntOrNull()
        val amountInt = amount.toIntOrNull()
        if (monthInt == null || yearInt == null || amountInt == null) {
            targetError.value = "Month, year, and amount must be numbers."
            return
        }
        // Only disallow past months; allow current month now
        if (yearInt < currentYear || (yearInt == currentYear && monthInt < currentMonth)) {
            targetError.value = "Target month/year must not be in the past."
            return
        }
        viewModelScope.launch {
            targetError.value = null
            repository.addTarget(monthInt, yearInt, tag, amountInt)
        }
    }

}
