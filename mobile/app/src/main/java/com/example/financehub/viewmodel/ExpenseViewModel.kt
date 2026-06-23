package com.example.financehub.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import com.example.financehub.data.database.models.TagRef
import com.example.financehub.data.repository.ExpenseRepository
import com.example.financehub.data.repository.SyncRepositoryState
import com.example.financehub.service.ReccommendationService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Date

private const val maxRecommendedTags = 5
class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    
    var name: MutableState<String> = mutableStateOf("")
    var amount: MutableState<String> = mutableStateOf("")

    val date = Date() // Current date
    val dateTuple = com.example.financehub.util.getDateComponents(date)
    var dayState: MutableState<String> = mutableStateOf(dateTuple.first.toString())
    var monthState: MutableState<String> = mutableStateOf(dateTuple.second.toString())
    var yearState: MutableState<String> = mutableStateOf(dateTuple.third.toString())

    // Shared states for both Expense and Target forms
    var selectedTag: MutableState<TagRef?> = mutableStateOf(null)
    var targetAmount: MutableState<String> = mutableStateOf("")

    val newTags = mutableStateListOf<String>()



    // Sync-related state
    val syncState: StateFlow<SyncRepositoryState> = repository.getSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncRepositoryState.IDLE)

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult

    fun onNewTagTextAddition() {
        if (_newTagText.value.isNotBlank()) {
            newTags.add(_newTagText.value) // Mutates the state directly in the VM
            _newTagText.value = ""        // Clears out the input text field
        }
    }

    fun addExpense() {
        val nameVal = name.value
        val month = monthState.value
        val year = yearState.value
        val day = dayState.value
        val selectedTagsVal  = selectedTags
        val amountVal = amount.value
        // Reset form
        name.value = ""
        amount.value = ""
        _selectedTags.value = emptySet<TagRef>()
        _reccommendedTags.value = emptyList<TagRef>()
        viewModelScope.launch {
            try {
                if (nameVal.isBlank() || amountVal.isBlank()) {
                    _operationResult.value = "Please fill in all required fields"
                    return@launch
                }
                
                val day: Int = if (day.isBlank()) dateTuple.first else day.toInt()
                val month: Int =
                    if (month.isBlank()) dateTuple.second else month.toInt()
                val year: Int =
                    if (year.isBlank()) dateTuple.third else year.toInt()
                print("new Tags in view model: $newTags")
                repository.insertExpense(
                    Expense(
                        title = nameVal,
                        amount = amountVal.toInt(),
                        date = day,
                        month = month,
                        year = year
                    ),
                    tags = _selectedTags.value,
                    newTags = newTags
                )
                
                _operationResult.value = "Expense added successfully"
                repository.triggerSync()
                

            } catch (e: Exception) {
                _operationResult.value = "Failed to add expense: ${e.message}"
            }
        }

    }


    // Tag adding component functions
    private val _reccommendedTags = MutableStateFlow<List<TagRef>>(emptyList())
    val reccommendedTags: StateFlow<List<TagRef>> = _reccommendedTags.asStateFlow()


    private val _selectedTags = MutableStateFlow<Set<TagRef>>(emptySet())
    val selectedTags: StateFlow<Set<TagRef>> = _selectedTags.asStateFlow()
    val reccommendationService = ReccommendationService(repository)

    private var _newTagText = MutableStateFlow<String>("")
    val newTagText = _newTagText.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun clickRecommendedTag(tag: TagRef) {
            _selectedTags.value = if (_selectedTags.value.contains(tag)) {
                _reccommendedTags.value.filter { tag.tagID != it.tagID }
                _selectedTags.value - tag
            } else {
                _reccommendedTags.value.filter { tag.tagID != it.tagID }
                getReccomendations(tag.tagID)
                _selectedTags.value + tag
            }

    }

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

    fun deselectTag(tag: TagRef) {
        _selectedTags.value -= tag
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

    val matchingTags: StateFlow<List<TagRef>> = newTagText
        .debounce(300) // Wait for user to stop typing
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.getMatchingTags(query)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateNewTagText(newQuery: String) {
        _newTagText.value = newQuery
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
            try {
                targetError.value = null
                repository.addTarget(monthInt, yearInt, tag, amountInt)
                _operationResult.value = "Target added successfully (will sync when connected)"
                
                // Reset form
                targetAmount.value = ""
                selectedTag.value = null
                
            } catch (e: Exception) {
                targetError.value = "Failed to add target: ${e.message}"
            }
        }
    }


}
