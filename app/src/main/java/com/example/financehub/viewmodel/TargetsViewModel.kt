package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.data.database.Target
import com.example.financehub.data.database.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow

// Data class to hold target info with tag name
// (if you want to show tag name, month, year, spent, etc.)
data class TargetWithTag(
    val tag: Tags,
    val target: Target
)

class TargetsViewModel(private val repository: ExpenseRepository) : ViewModel() {
    // You can use paging if you expect many targets, or just a Flow<List<TargetWithTag>>
    val targetsWithTags: StateFlow<List<TargetWithTag>> = repository.getAllTargetsWithTagsFromCurrentMonth()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteTarget(target: Target) {
        viewModelScope.launch {
            repository.deleteTarget(target)
        }
    }

    fun updateTargetAmount(target: Target, newAmount: Int) {
        viewModelScope.launch {
            repository.updateTargetAmount(target, newAmount)
        }
    }
}
