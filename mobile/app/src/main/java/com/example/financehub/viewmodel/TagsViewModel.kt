package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.repository.ExpenseRepository
import androidx.paging.cachedIn

class TagsViewModel (private val repository: ExpenseRepository) : ViewModel(){

    val pagedTags = repository.getPagedTags().
    cachedIn(viewModelScope)
}
