package com.ironlog.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
  @Query(
    """
    SELECT e.id, e.name, e.categoryId, c.name AS categoryName
    FROM exercise e LEFT JOIN category c ON c.id = e.categoryId
    WHERE e.archived = 0
    ORDER BY e.name COLLATE NOCASE
    """
  )
  fun observeExercises(): Flow<List<ExerciseItem>>

  @Query(
    """
    SELECT e.id, e.name, e.categoryId, c.name AS categoryName
    FROM exercise e LEFT JOIN category c ON c.id = e.categoryId
    WHERE e.id = :id
    """
  )
  fun observeExercise(id: Long): Flow<ExerciseItem?>

  /** Case-insensitive lookup that prefers a visible exercise over an archived one. */
  @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE ORDER BY archived LIMIT 1")
  suspend fun findByName(name: String): ExerciseEntity?

  @Insert suspend fun insert(exercise: ExerciseEntity): Long

  @Query("UPDATE exercise SET name = :name, categoryId = :categoryId, archived = 0 WHERE id = :id")
  suspend fun update(id: Long, name: String, categoryId: Long?)

  @Query("UPDATE exercise SET archived = 1, categoryId = NULL WHERE id = :id")
  suspend fun archive(id: Long)

  @Query("UPDATE exercise SET categoryId = :categoryId WHERE id IN (:ids)")
  suspend fun setCategory(ids: List<Long>, categoryId: Long?)

  @Query("DELETE FROM exercise WHERE id = :id") suspend fun delete(id: Long)

  @Query("SELECT COUNT(*) FROM session_exercise WHERE exerciseId = :exerciseId")
  suspend fun countSessionUsages(exerciseId: Long): Int

  @Query("DELETE FROM workout_exercise WHERE exerciseId = :exerciseId")
  suspend fun removeFromWorkouts(exerciseId: Long)

  @Query(
    """
    SELECT c.id, c.name, COUNT(e.id) AS exerciseCount
    FROM category c LEFT JOIN exercise e ON e.categoryId = c.id AND e.archived = 0
    GROUP BY c.id
    ORDER BY c.name COLLATE NOCASE
    """
  )
  fun observeCategories(): Flow<List<CategoryItem>>

  @Query("SELECT * FROM category WHERE name = :name COLLATE NOCASE LIMIT 1")
  suspend fun findCategoryByName(name: String): CategoryEntity?

  @Insert suspend fun insertCategory(category: CategoryEntity): Long

  @Query("UPDATE category SET name = :name WHERE id = :id")
  suspend fun renameCategory(id: Long, name: String)

  @Query("DELETE FROM category WHERE id = :id") suspend fun deleteCategory(id: Long)
}
