package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.launch

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    fun addExpense(title: String, amount: Int) {
        viewModelScope.launch {
            repository.insertExpense(Expense(title = title, amount = amount))
        }
    }
}
