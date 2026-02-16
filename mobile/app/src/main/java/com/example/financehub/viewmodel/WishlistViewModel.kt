package com.example.financehub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Tags
import com.example.financehub.data.database.Wishlist
import com.example.financehub.data.database.WishlistWithTags
import com.example.financehub.data.repository.WishlistRepository
import com.example.financehub.data.dao.TagsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WishlistViewModel(
    private val wishlistRepository: WishlistRepository,
    private val tagsDao: TagsDao
) : ViewModel() {

    // Using DAO directly for tags if no repository exists.
    // Ideally we should have a TagsRepository.
    val allTags: Flow<List<Tags>> = tagsDao.getAllTags()

    val wishlistItems: StateFlow<List<WishlistWithTags>> = wishlistRepository.allWishlistItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addWishlistItem(name: String, expectedPrice: Int, tagIds: List<Int>) {
        viewModelScope.launch {
            wishlistRepository.addWishlistItem(name, expectedPrice, tagIds)
        }
    }

    fun updateWishlistItem(item: Wishlist, tagIds: List<Int>) {
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
