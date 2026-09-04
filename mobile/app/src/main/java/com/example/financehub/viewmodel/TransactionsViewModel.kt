package com.example.financehub.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.vector.PathNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.ExpenseWithTags
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.data.repository.SyncRepositoryState
import com.example.financehub.service.ReccommendationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.collections.minus


private const val maxRecommendedTags = 5

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _selectedExpense = MutableStateFlow<ExpenseWithTags?>(null)
    val selectedExpense = _selectedExpense.asStateFlow()

    // Sync-related state
    val syncState: StateFlow<SyncRepositoryState> = repository.getSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncRepositoryState.IDLE)
    
    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult

    val reccommendationService = ReccommendationService(repository)

    private val _reccommendedTags = MutableStateFlow<List<TagRef>>(emptyList())
    val reccommendedTags: StateFlow<List<TagRef>> = _reccommendedTags.asStateFlow()
    private val _selectedTags = MutableStateFlow<Set<TagRef>>(emptySet())
    val selectedTags: StateFlow<Set<TagRef>> = _selectedTags.asStateFlow()

    private var _newTagText = MutableStateFlow<String>("")
    val newTagText = _newTagText.asStateFlow()
    val newlyAddedTags = mutableStateListOf<String>()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun clickSuggestedTag(tag: TagRef) {
        _selectedTags.value = if(_selectedTags.value.contains(tag)) {
            _selectedTags.value - tag
        } else {
            _newTagText.value = ""
            getReccomendations(tag.tagID)
            _selectedTags.value + tag
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun getReccomendations(tagId: Int) {
        viewModelScope.launch {
            val recs = reccommendationService.getTagReccomendations(tagId) - _selectedTags.value
            if (_reccommendedTags.value.isNotEmpty()) {
                val tagRefMap = mutableMapOf<Int, TagRef>()
                recs.associateByTo(tagRefMap){it.tagID}
                val newRecs = mutableListOf<TagRef>()
                val existingNotRecommendedTagRef = mutableListOf<TagRef>()
                _reccommendedTags.value.forEach {
                    if (tagRefMap.contains(it.tagID) &&  newRecs.size < maxRecommendedTags) {
                        newRecs.addLast(it)
                        tagRefMap.remove(it.tagID)
                    }else {
                        existingNotRecommendedTagRef.addLast(it)
                    }
                }

                val newPreviouslyUnrecommendedTagRef = tagRefMap.values
                for (tagRef in newPreviouslyUnrecommendedTagRef) {
                    if (newRecs.size > maxRecommendedTags)
                        break
                    newRecs.addLast(tagRef)
                }
                for (tagRef in existingNotRecommendedTagRef) {
                    if (newRecs.size > maxRecommendedTags)
                        break
                    newRecs.addLast(tagRef)
                }
                _reccommendedTags.value = newRecs

            }
            else {
                _reccommendedTags.value = recs
            }
        }
    }

    fun onNewTagTextAddition() {
        if (_newTagText.value.isNotBlank()) {
            newlyAddedTags.add(_newTagText.value) // Mutates the state directly in the VM
            _newTagText.value = ""        // Clears out the input text field
        }
    }

    fun updateNewTagText(newQuery: String) {
        _newTagText.value = newQuery
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun clickRecommendedTag(tag: TagRef) {
        _selectedTags.value = if (_selectedTags.value.contains(tag)) {
            _reccommendedTags.value.filter { tag.tagID != it.tagID }
            _selectedTags.value.minus(tag)
        } else {
            _reccommendedTags.value.filter { tag.tagID != it.tagID }
            getReccomendations(tag.tagID)
            _selectedTags.value.plus(tag)
        }

    }

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
            try {
                val addedTags = newTags - oldTags
                val removedTags = oldTags - newTags
                repository.updateExpense(
                    Expense(expenseId, title, amount, year, month, date),
                    addedTags,
                    removedTags,
                    newlyAddedTags
                )
                _operationResult.value = "Expense updated successfully (will sync when connected)"
                clearSelectedExpense()
            } catch (e: Exception) {
                _operationResult.value = "Failed to update expense: ${e.message}"
            }
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

    fun updateRange(range: ClosedFloatingPointRange<Float> ) {
        val cur = _filterParams.value
        _filterParams.value = cur.copy(
            amountRange = range,
            version = cur.version + 1
        )
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

    fun deselectTag(tag: TagRef) {
        _selectedTags.value -= tag
    }

    val pagedExpenses: Flow<androidx.paging.PagingData<ExpenseWithTags>> = _filterParams
        .flatMapLatest { repository.getPagedFilteredExpenses(it) }
        .cachedIn(viewModelScope)

    /**
     * Delete an expense
     */
    fun deleteExpense(expense: ExpenseWithTags) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(expense.expense)
                _operationResult.value = "Expense deleted successfully (will sync when connected)"
            } catch (e: Exception) {
                _operationResult.value = "Failed to delete expense: ${e.message}"
            }
        }
    }

    /**
     * Clear operation result message
     */
    fun clearOperationResult() {
        _operationResult.value = null
    }

    /**
     * Check if any operation is in progress
     */
    fun isOperationInProgress(): Boolean {
        return syncState.value == SyncRepositoryState.SYNCING
    }
}
