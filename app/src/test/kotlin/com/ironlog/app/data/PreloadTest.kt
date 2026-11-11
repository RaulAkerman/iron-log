package com.ironlog.app.data

import com.ironlog.app.data.db.SeedExercise
import com.ironlog.app.data.db.SeedExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreloadTest {
  private val bench = SeedExercise("Bench Press", "Chest", listOf("Triceps", "Shoulders"))
  private val facePull = SeedExercise("Face Pull", "Shoulders", listOf("Back"))
  private val plank = SeedExercise("Plank", "Abs")
  private val pecDeck = SeedExercise("Pec Deck", "Chest", since = 3)
  private val dip = SeedExercise("Dip", "Triceps", listOf("Chest"), since = 3)
  private val seed = listOf(bench, facePull, plank, pecDeck, dip)

  private fun stored(id: Long, name: String, category: String? = null, archived: Boolean = false) =
    StoredExercise(id, name, category, archived)

  @Test
  fun newDatabase_getsEveryExercise() {
    val plan = planPreload(seed, stored = emptyList(), storedVersion = 0)

    assertEquals(seed, plan.newExercises)
    assertEquals(emptyMap<Long, String>(), plan.mainCategories)
    assertEquals(emptyMap<Long, List<String>>(), plan.subCategories)
  }

  @Test
  fun upgrade_insertsOnlyWhatTheListGained() {
    val existing = listOf(stored(1, "Bench Press"), stored(2, "Face Pull"), stored(3, "Plank"))

    val plan = planPreload(seed, existing, storedVersion = 2)

    assertEquals(listOf(pecDeck, dip), plan.newExercises)
  }

  @Test
  fun upgrade_doesNotBringBackWhatTheUserDeleted() {
    // "Plank" was offered by version 2 and is gone, so it was deleted.
    val existing = listOf(stored(1, "Bench Press"), stored(2, "Face Pull"))

    val plan = planPreload(seed, existing, storedVersion = 2)

    assertEquals(listOf(pecDeck, dip), plan.newExercises)
  }

  @Test
  fun upgrade_fillsInCategoriesOfUncategorisedExercises() {
    val existing = listOf(stored(1, "Bench Press"), stored(3, "Plank"))

    val plan = planPreload(seed, existing, storedVersion = 2)

    assertEquals(mapOf(1L to "Chest", 3L to "Abs"), plan.mainCategories)
    assertEquals(mapOf(1L to listOf("Triceps", "Shoulders")), plan.subCategories)
  }

  @Test
  fun upgrade_keepsTheUsersMainCategory() {
    val existing =
      listOf(
        // Same as the list.
        stored(1, "Bench Press", category = "chest"),
        // The user's choice is one of the list's sub categories.
        stored(2, "Face Pull", category = "Back"),
        // The user's choice is unknown to the list.
        stored(3, "Plank", category = "Warm-up"),
      )

    val plan = planPreload(seed, existing, storedVersion = 2)

    assertEquals(emptyMap<Long, String>(), plan.mainCategories)
    assertEquals(
      mapOf(
        1L to listOf("Triceps", "Shoulders"),
        // The list's main category becomes a sub category; the user's main one is not repeated.
        2L to listOf("Shoulders"),
        3L to listOf("Abs"),
      ),
      plan.subCategories,
    )
  }

  @Test
  fun upgrade_matchesNamesIgnoringCase_andTreatsOwnExercisesLikeListedOnes() {
    // The user added "pec deck" before the list had it.
    val existing = listOf(stored(7, "pec deck"), stored(8, "My Own Exercise"))

    val plan = planPreload(seed, existing, storedVersion = 2)

    assertEquals(listOf(dip), plan.newExercises)
    assertEquals(mapOf(7L to "Chest"), plan.mainCategories)
    assertEquals(emptyMap<Long, List<String>>(), plan.subCategories)
  }

  @Test
  fun upgrade_leavesArchivedExercisesAlone() {
    val existing = listOf(stored(1, "Bench Press", archived = true), stored(9, "Dip", archived = true))

    val plan = planPreload(seed, existing, storedVersion = 2)

    // Neither re-added next to the archived one nor categorised.
    assertEquals(listOf(pecDeck), plan.newExercises)
    assertEquals(emptyMap<Long, String>(), plan.mainCategories)
    assertEquals(emptyMap<Long, List<String>>(), plan.subCategories)
  }

  @Test
  fun upgrade_prefersTheVisibleExerciseOverAnArchivedNamesake() {
    val existing = listOf(stored(1, "Plank", archived = true), stored(2, "plank"))

    val plan = planPreload(seed, existing, storedVersion = 2)

    assertEquals(mapOf(2L to "Abs"), plan.mainCategories)
  }

  @Test
  fun laterUpgrade_onlyAddsExercises() {
    // A database that already has categories: its exercises are the user's business.
    val existing = listOf(stored(1, "Bench Press"), stored(3, "Plank", category = "Abs"))
    val newer = seed + SeedExercise("Sissy Squat", "Legs", since = 4)

    val plan = planPreload(newer, existing, storedVersion = 3)

    assertEquals(listOf("Sissy Squat"), plan.newExercises.map { it.name })
    assertEquals(emptyMap<Long, String>(), plan.mainCategories)
    assertEquals(emptyMap<Long, List<String>>(), plan.subCategories)
  }

  @Test
  fun seedList_hasNoDuplicateNames() {
    val duplicates = SeedExercises.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }.keys

    assertEquals(emptySet<String>(), duplicates)
  }

  @Test
  fun seedList_subCategoriesAreDistinctAndNeverTheMainCategory() {
    for (exercise in SeedExercises) {
      assertTrue(exercise.name, exercise.category !in exercise.subCategories)
      assertEquals(exercise.name, exercise.subCategories.distinct(), exercise.subCategories)
    }
  }

  @Test
  fun seedList_usesOneSpellingPerCategory() {
    val names = SeedExercises.flatMap { it.subCategories + it.category }.distinct()

    assertEquals(names.size, names.distinctBy { it.lowercase() }.size)
    assertTrue(names.all { it == it.trim() && it.isNotEmpty() })
  }

  @Test
  fun seedList_keepsTheOriginalExercisesAndMarksLaterOnes() {
    // 109 exercises shipped with versions 1 and 2; everything after them belongs to version 3.
    assertTrue(SeedExercises.take(109).all { it.since == 1 })
    assertTrue(SeedExercises.drop(109).all { it.since == 3 })
    assertEquals("Bench Press (Barbell)", SeedExercises.first().name)
    assertEquals("Power Clean (Barbell)", SeedExercises[108].name)
  }
}
