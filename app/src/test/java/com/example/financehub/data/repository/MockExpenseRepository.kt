package com.example.financehub.data.repository

import com.example.financehub.data.dao.*
import com.example.financehub.data.repository.ExpenseRepositoryInterface

class MockExpenseRepository: ExpenseRepositoryInterface {
    override suspend fun getAllTags() {
        TODO("Not yet implemented")
    }
}