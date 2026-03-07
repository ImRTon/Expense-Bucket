package com.rton.expensebucket.data.dao

import androidx.room.*
import com.rton.expensebucket.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isExpense = :isExpense ORDER BY sortOrder ASC")
    fun getCategoriesByType(isExpense: Boolean): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isExpense = :isExpense AND parentId IS NULL ORDER BY sortOrder ASC")
    fun getParentCategories(isExpense: Boolean): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder ASC")
    fun getSubCategories(parentId: Long): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name AND isExpense = :isExpense LIMIT 1")
    suspend fun getCategoryByNameAndType(name: String, isExpense: Boolean): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Update
    suspend fun updateCategory(category: Category)

    @Update
    suspend fun updateCategories(categories: List<Category>)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories WHERE parentId = :parentId")
    suspend fun deleteSubCategories(parentId: Long)
}
