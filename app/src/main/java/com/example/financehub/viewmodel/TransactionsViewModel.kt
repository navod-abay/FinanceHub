package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionsViewModel(private val repository: ExpenseRepository): ViewModel() {

    private val _selectedExpense = MutableStateFlow<ExpenseWithTags?>(null)
    val selectedExpense = _selectedExpense.asStateFlow()

    fun selectExpenseForEdit(expense: ExpenseWithTags){
        _selectedExpense.value =  expense
    }

    fun clearSelectedExpense(){
        _selectedExpense.value = null
    }

    fun updateExpense(expenseId: Int, title: String, amount: Int, year: Int, month: Int, date: Int, tags: Set<String>) {
        viewModelScope.launch {
            repository.updateExpense(Expense(expenseId, title, amount, year, month, date), tags)
        }

    }

    val pagedExpenses = repository.getPagedExpensesWithTags()
        .cachedIn(viewModelScope)
}
