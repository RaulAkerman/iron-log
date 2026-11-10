package com.ironlog.app.data

import androidx.room3.withWriteTransaction
import com.ironlog.app.data.db.CategoryEntity
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseEntity
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.data.db.IronLogDatabase
import kotlinx.coroutines.flow.Flow

/** The exercise list and the user's categories. */
class ExerciseRepository(private val db: IronLogDatabase) {
  private val dao = db.exerciseDao()

  val exercises: Flow<List<ExerciseItem>> = dao.observeExercises()
  val categories: Flow<List<CategoryItem>> = dao.observeCategories()

  fun exercise(id: Long): Flow<ExerciseItem?> = dao.observeExercise(id)

  /**
   * Creates the exercise when [id] is null, otherwise renames and re-categorises it. Returns false
   * when the name is blank or already used by another exercise.
   */
  suspend fun saveExercise(id: Long?, name: String, categoryId: Long?): Boolean {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) return false
    return db.withWriteTransaction {
      val sameName = dao.findByName(cleanName)
      when {
        // A deleted exercise that still has history comes back instead of being duplicated.
        id == null && sameName != null && sameName.archived -> {
          dao.update(sameName.id, cleanName, categoryId)
          true
        }
        sameName != null && !sameName.archived && sameName.id != id -> false
        id == null -> {
          dao.insert(ExerciseEntity(name = cleanName, categoryId = categoryId))
          true
        }
        else -> {
          dao.update(id, cleanName, categoryId)
          true
        }
      }
    }
  }

  /**
   * Removes the exercise from the list and from all workouts. Logged sessions keep their sets: an
   * exercise with history is only hidden, never dropped.
   */
  suspend fun deleteExercise(id: Long) {
    db.withWriteTransaction {
      dao.removeFromWorkouts(id)
      if (dao.countSessionUsages(id) > 0) dao.archive(id) else dao.delete(id)
    }
  }

  suspend fun setCategory(exerciseIds: Collection<Long>, categoryId: Long?) {
    if (exerciseIds.isNotEmpty()) dao.setCategory(exerciseIds.toList(), categoryId)
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

  /** Deletes the category; its exercises become uncategorised. */
  suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
}
