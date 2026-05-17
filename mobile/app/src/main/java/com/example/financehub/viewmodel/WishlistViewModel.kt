package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.Wishlist
import com.example.financehub.data.database.WishlistWithTags
import com.example.financehub.data.repository.WishlistRepository
import com.example.financehub.data.dao.TagsDao
import com.example.financehub.data.database.models.TagRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val tagsDao: TagsDao
) : ViewModel() {

    // Using DAO directly for tags if no repository exists.
    // Ideally we should have a TagsRepository.
    private val _query = MutableStateFlow("")

    val allTags: Flow<List<Tags>> = tagsDao.getAllTags()

    val wishlistItems: StateFlow<List<WishlistWithTags>> = wishlistRepository.allWishlistItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }
    fun addWishlistItem(name: String, minPrice: Int, maxPrice: Int, tagIds: List<Tags>) {
        viewModelScope.launch {
            wishlistRepository.addWishlistItem(name, minPrice, maxPrice, tagIds)
        }
    }

    val matchingTags: StateFlow<List<Tags>> = _query
        .debounce(300) // Wait for user to stop typing
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else wishlistRepository.getMatchingTags(query)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateWishlistItem(item: Wishlist, tagIds: List<Tags>) {
        viewModelScope.launch {
            wishlistRepository.updateWishlistItem(item, tagIds)
        }
    }

    fun deleteWishlistItem(item: Wishlist) {
        viewModelScope.launch {
            wishlistRepository.deleteWishlistItem(item)
        }
    }
}
