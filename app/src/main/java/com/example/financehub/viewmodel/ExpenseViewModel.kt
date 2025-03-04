package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.Date
import com.example.financehub.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _monthlyTotal = MutableStateFlow(0)

    fun addExpense(title: String, amount: Int, tags: Set<String>) {
        viewModelScope.launch {
            val date = Date() // Current date
            val (day, month, year) = getDateComponents(date)
            repository.insertExpense(Expense(title = title, amount = amount, date = day, month = month, year = year), tags = tags)
        }
    }


}
