package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.financehub.data.repository.ExpenseRepository

class TransactionsViewModel(private val repository: ExpenseRepository): ViewModel() {

    val pagedExpenses = repository.getPagedExpensesWithTags()
        .cachedIn(viewModelScope)
}
