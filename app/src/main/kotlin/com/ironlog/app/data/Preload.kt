package com.ironlog.app.data

import com.ironlog.app.data.db.SEED_CATEGORIES_SINCE
import com.ironlog.app.data.db.SeedExercise

/** An exercise as it is stored when the pre-loaded list is applied; [category] is its main one. */
data class StoredExercise(
  val id: Long,
  val name: String,
  val category: String?,
  val archived: Boolean,
)

/** What applying the pre-loaded list changes. Categories are given by name. */
data class PreloadPlan(
  /** Exercises to insert together with their categories. */
  val newExercises: List<SeedExercise> = emptyList(),
  /** Exercise id to the main category it gets because it had none. */
  val mainCategories: Map<Long, String> = emptyMap(),
  /** Exercise id to the sub categories it gets. */
  val subCategories: Map<Long, List<String>> = emptyMap(),
)

/**
 * Works out how the pre-loaded list [seed] is brought into a database that was created by version
 * [storedVersion] (0 for a new, empty one) and now holds [stored]. Names are compared ignoring
 * case. What the user set up always wins:
 * - An exercise is inserted only if the list gained it after [storedVersion]. One that the stored
 *   version already offered and is gone now was deleted by the user and stays deleted.
 * - A database from before the list had categories gets them filled in for the exercises it
 *   already has. A main category the user chose is kept, and the list's main category then becomes
 *   a sub category. Archived exercises are left alone.
 */
fun planPreload(
  seed: List<SeedExercise>,
  stored: List<StoredExercise>,
  storedVersion: Int,
): PreloadPlan {
  // The visible exercise wins when an archived one has the same name.
  val storedByName =
    stored.sortedBy { it.archived }.groupBy { it.name.lowercase() }.mapValues { it.value.first() }
  val fillCategories = storedVersion < SEED_CATEGORIES_SINCE

  val newExercises = mutableListOf<SeedExercise>()
  val mainCategories = mutableMapOf<Long, String>()
  val subCategories = mutableMapOf<Long, List<String>>()
  for (exercise in seed) {
    val existing = storedByName[exercise.name.lowercase()]
    when {
      existing == null -> if (exercise.since > storedVersion) newExercises += exercise
      existing.archived || !fillCategories -> Unit
      else -> {
        val main = existing.category ?: exercise.category
        if (existing.category == null) mainCategories[existing.id] = main
        val subs =
          (exercise.subCategories + exercise.category)
            .filterNot { it.equals(main, ignoreCase = true) }
            .distinctBy { it.lowercase() }
        if (subs.isNotEmpty()) subCategories[existing.id] = subs
      }
    }
  }
  return PreloadPlan(newExercises, mainCategories, subCategories)
}
