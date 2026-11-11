package com.ironlog.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
  /** The sub categories of each exercise come in no particular order. */
  @Transaction
  @Query(
    """
    SELECT e.id, e.name, e.categoryId, c.name AS categoryName
    FROM exercise e LEFT JOIN category c ON c.id = e.categoryId
    WHERE e.archived = 0
    ORDER BY e.name COLLATE NOCASE
    """
  )
  fun observeExercises(): Flow<List<ExerciseItem>>

  @Transaction
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

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertSubCategories(links: List<ExerciseSubCategoryEntity>)

  /** Leaves out exercises that have [categoryId] as their main category. */
  @Query(
    """
    INSERT OR IGNORE INTO exercise_sub_category (exerciseId, categoryId)
    SELECT id, :categoryId FROM exercise
    WHERE id IN (:ids) AND (categoryId IS NULL OR categoryId != :categoryId)
    """
  )
  suspend fun addSubCategory(ids: List<Long>, categoryId: Long)

  @Query("DELETE FROM exercise_sub_category WHERE exerciseId IN (:ids) AND categoryId = :categoryId")
  suspend fun removeSubCategory(ids: List<Long>, categoryId: Long)

  @Query("DELETE FROM exercise_sub_category WHERE exerciseId = :exerciseId")
  suspend fun clearSubCategories(exerciseId: Long)

  @Query("DELETE FROM exercise WHERE id = :id") suspend fun delete(id: Long)

  @Query("SELECT COUNT(*) FROM session_exercise WHERE exerciseId = :exerciseId")
  suspend fun countSessionUsages(exerciseId: Long): Int

  @Query("DELETE FROM workout_exercise WHERE exerciseId = :exerciseId")
  suspend fun removeFromWorkouts(exerciseId: Long)

  @Query(
    """
    SELECT c.id, c.name,
      (SELECT COUNT(*) FROM exercise e WHERE e.categoryId = c.id AND e.archived = 0)
        AS exerciseCount,
      (SELECT COUNT(*) FROM exercise_sub_category s JOIN exercise e ON e.id = s.exerciseId
       WHERE s.categoryId = c.id AND e.archived = 0) AS subExerciseCount
    FROM category c
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
