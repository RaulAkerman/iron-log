package com.ironlog.app.data

import androidx.room3.withWriteTransaction
import com.ironlog.app.data.db.CategoryEntity
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.data.db.ExerciseSubCategoryEntity
import com.ironlog.app.data.db.IronLogDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The exercise list and its categories. */
class ExerciseRepository(private val db: IronLogDatabase) {
  private val dao = db.exerciseDao()

  val exercises: Flow<List<ExerciseItem>> =
    dao.observeExercises().map { list -> list.map { it.withSortedSubCategories() } }
  val categories: Flow<List<CategoryItem>> = dao.observeCategories()

  fun exercise(id: Long): Flow<ExerciseItem?> =
    dao.observeExercise(id).map { it?.withSortedSubCategories() }

  /**
   * Creates the exercise when [id] is null, otherwise renames and re-categorises it:
   * [categoryId] is its main category, [subCategoryIds] replace its sub categories. Returns false
   * when the name is blank or already used by another exercise.
   */
  suspend fun saveExercise(
    id: Long?,
    name: String,
    categoryId: Long?,
    subCategoryIds: Set<Long>,
  ): Boolean {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return false
    return db.withWriteTransaction {
      val sameName = dao.findByName(cleanName)
      val savedId =
        when {
          // A deleted exercise that still has history comes back instead of being duplicated.
          id == null && sameName != null && sameName.archived -> {
            dao.update(sameName.id, cleanName, categoryId)
            sameName.id
          }
          sameName != null && !sameName.archived && sameName.id != id -> null
          id == null -> dao.insert(ExerciseEntity(name = cleanName, categoryId = categoryId))
          else -> {
            dao.update(id, cleanName, categoryId)
            id
          }
        }
      if (savedId != null) {
        dao.clearSubCategories(savedId)
        // The main category is not repeated as a sub category.
        dao.insertSubCategories(
          subCategoryIds.filter { it != categoryId }.map { ExerciseSubCategoryEntity(savedId, it) }
        )
      }
      savedId != null
    }
  }

  /**
   * Removes the exercise from the list and from all workouts. Logged sessions keep their sets: an
   * exercise with history is only hidden, never dropped.
   */
  suspend fun deleteExercise(id: Long) {
    db.withWriteTransaction {
      dao.removeFromWorkouts(id)
      if (dao.countSessionUsages(id) > 0) {
        dao.archive(id)
        dao.clearSubCategories(id)
      } else {
        dao.delete(id)
      }
    }
  }

  /** Sets the main category of the exercises; null leaves them without one. */
  suspend fun setCategory(exerciseIds: Collection<Long>, categoryId: Long?) {
    if (exerciseIds.isEmpty()) return
    val ids = exerciseIds.toList()
    db.withWriteTransaction {
      dao.setCategory(ids, categoryId)
      // What is now the main category stops being a sub category.
      if (categoryId != null) dao.removeSubCategory(ids, categoryId)
    }
  }

  /**
   * Adds the categories in [add] as sub categories to all of the exercises and takes those in
   * [remove] away from all of them. An exercise never gets its main category as a sub category.
   */
  suspend fun changeSubCategories(
    exerciseIds: Collection<Long>,
    add: Set<Long>,
    remove: Set<Long>,
  ) {
    if (exerciseIds.isEmpty() || (add.isEmpty() && remove.isEmpty())) return
    val ids = exerciseIds.toList()
    db.withWriteTransaction {
      for (categoryId in add) dao.addSubCategory(ids, categoryId)
      for (categoryId in remove) dao.removeSubCategory(ids, categoryId)
    }
  }

  /** Returns the id of the category with this name, creating it if needed; null for a blank name. */
  suspend fun createCategory(name: String): Long? {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return null
    return db.withWriteTransaction {
      dao.findCategoryByName(cleanName)?.id ?: dao.insertCategory(CategoryEntity(name = cleanName))
    }
  }

  /** Returns false when the name is blank or taken by another category. */
  suspend fun renameCategory(id: Long, name: String): Boolean {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return false
    return db.withWriteTransaction {
      val sameName = dao.findCategoryByName(cleanName)
      if (sameName != null && sameName.id != id) {
        false
      } else {
        dao.renameCategory(id, cleanName)
        true
      }
    }
  }

  /**
   * Deletes the category. Exercises that had it as their main category become uncategorised, and
   * it disappears wherever it was a sub category.
   */
  suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
}

private fun ExerciseItem.withSortedSubCategories(): ExerciseItem =
  if (subCategories.size < 2) this
  else copy(subCategories = subCategories.sortedBy { it.name.lowercase() })
