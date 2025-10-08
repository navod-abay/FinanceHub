package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _selectedExpense = MutableStateFlow<ExpenseWithTags?>(null)
    val selectedExpense = _selectedExpense.asStateFlow()

    fun selectExpenseForEdit(expense: ExpenseWithTags) {
        _selectedExpense.value = expense
    }

    fun clearSelectedExpense() {
        _selectedExpense.value = null
    }

    fun updateExpense(
        expenseId: Int, title: String, amount: Int, year: Int, month: Int, date: Int,
        newTags: Set<TagRef>, oldTags: Set<TagRef>, newlyAddedTags: List<String>
    ) {
        viewModelScope.launch {
            val addedTags = newTags - oldTags
            val removedTags = oldTags - newTags
            repository.updateExpense(
                Expense(expenseId, title, amount, year, month, date),
                addedTags,
                removedTags,
                newlyAddedTags
            )
        }
    }

    private val _filterParams = MutableStateFlow(FilterParams())
    val filterParams: StateFlow<FilterParams> = _filterParams.asStateFlow()

    private val tagQuery = MutableStateFlow("")
    val matchingTags: StateFlow<List<TagRef>> = tagQuery
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.getMatchingTags(
                q
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            emptyList()
        )

    val allTags: Flow<List<Tags>> = repository.getAllTags()

    fun updateTagSearch(query: String) {
        tagQuery.value = query
    }



    fun toggleTagId(tagId: Int) {
        val cur = _filterParams.value
        val newSet = cur.requiredTagIds.toMutableSet()
            .apply { if (contains(tagId)) remove(tagId) else add(tagId) }
        _filterParams.value = cur.copy(requiredTagIds = newSet, version = cur.version + 1)
    }

    fun clearAllTags() {
        val cur = _filterParams.value; if (cur.requiredTagIds.isNotEmpty()) _filterParams.value =
            cur.copy(requiredTagIds = emptySet(), version = cur.version + 1)
    }

    fun selectPreset(preset: DatePreset) {
        val cur = _filterParams.value
        _filterParams.value = cur.copy(
            dateMode = DateMode.PRESET,
            preset = preset,
            singleDay = null,
            rangeStart = null,
            rangeEnd = null,
            version = cur.version + 1
        )
    }

    fun setSingleDay(day: Int, month: Int, year: Int) {
        val cur = _filterParams.value
        _filterParams.value = cur.copy(
            dateMode = DateMode.SINGLE_DAY,
            singleDay = Triple(day, month, year),
            preset = null,
            rangeStart = null,
            rangeEnd = null,
            version = cur.version + 1
        )
    }

    fun setRange(start: Triple<Int, Int, Int>?, end: Triple<Int, Int, Int>?) {
        val cur = _filterParams.value
        _filterParams.value = cur.copy(
            dateMode = if (start != null && end != null) DateMode.RANGE else DateMode.NONE,
            rangeStart = start,
            rangeEnd = end,
            singleDay = null,
            preset = null,
            version = cur.version + 1
        )
    }

    fun clearDateFilter() {
        val cur = _filterParams.value; _filterParams.value = cur.copy(
            dateMode = DateMode.NONE,
            singleDay = null,
            rangeStart = null,
            rangeEnd = null,
            preset = null,
            version = cur.version + 1
        )
    }

    val pagedExpenses: Flow<androidx.paging.PagingData<ExpenseWithTags>> = _filterParams
        .flatMapLatest { repository.getPagedFilteredExpenses(it) }
        .cachedIn(viewModelScope)
}
