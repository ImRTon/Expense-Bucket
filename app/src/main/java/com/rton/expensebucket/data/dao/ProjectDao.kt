package com.rton.expanses.data.dao

import androidx.room.*
import com.rton.expanses.data.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getActiveProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("""
        SELECT * FROM projects
        WHERE isActive = 1
          AND startDate IS NOT NULL AND endDate IS NOT NULL
          AND startDate <= :dateMillis AND endDate >= :dateMillis
        ORDER BY startDate DESC LIMIT 1
    """)
    suspend fun getProjectByDate(dateMillis: Long): Project?
}
