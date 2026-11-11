package com.ironlog.app.ui.exercises

import androidx.compose.ui.state.ToggleableState
import com.ironlog.app.data.db.CategoryEntity
import com.ironlog.app.data.db.CategoryItem
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.data.db.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseListLogicTest {
  private val machinePress = ExerciseItem(1, "Shoulder Press (Machine)", 10, "Shoulder press")
  private val dumbbellPress = ExerciseItem(2, "Overhead Press (Dumbbell)", 10, "Shoulder press")
  private val benchPress = ExerciseItem(3, "Bench Press (Barbell)", 20, "Bench")
  private val plank = ExerciseItem(4, "Plank", null, null)
  private val squat = ExerciseItem(5, "Squat (Barbell)", null, null)
  private val all = listOf(benchPress, dumbbellPress, plank, machinePress, squat)

  @Test
  fun sections_groupByCategory_alphabeticalWithUncategorisedLast() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.All)

    assertEquals(listOf("Bench", "Shoulder press", null), sections.map { it.title })
    assertEquals(listOf(dumbbellPress, machinePress), sections[1].exercises)
    assertEquals(listOf(plank, squat), sections[2].exercises)
  }

  @Test
  fun sections_uncategorisedFilter_keepsOnlyThose() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.Uncategorised)

    assertEquals(listOf(listOf(plank, squat)), sections.map { it.exercises })
  }

  @Test
  fun sections_categoryFilter_keepsOnlyThatCategory() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.Category(10))

    assertEquals(listOf(listOf(dumbbellPress, machinePress)), sections.map { it.exercises })
  }

  @Test
  fun sections_categoryFilter_alsoKeepsExercisesThatHaveItAsSubCategory() {
    val triceps = CategoryEntity(30, "Triceps")
    val bench = benchPress.copy(subCategories = listOf(triceps))
    val dips = ExerciseItem(6, "Dip", null, null, listOf(triceps))
    val pushdown = ExerciseItem(7, "Triceps Pushdown", 30, "Triceps")
    val closeGrip = ExerciseItem(8, "Close-Grip Bench Press", 30, "Triceps")
    val exercises = listOf(bench, closeGrip, dips, machinePress, pushdown)

    val sections = buildSections(exercises, query = "", filter = CategoryFilter.Category(30))

    // The category itself first, then the other main categories by name, uncategorised last.
    assertEquals(listOf("Triceps", "Bench", null), sections.map { it.title })
    assertEquals(
      listOf(listOf(closeGrip, pushdown), listOf(bench), listOf(dips)),
      sections.map { it.exercises },
    )
  }

  @Test
  fun sections_uncategorisedFilter_goesByMainCategoryOnly() {
    val dips = ExerciseItem(6, "Dip", null, null, listOf(CategoryEntity(30, "Triceps")))

    val sections = buildSections(all + dips, query = "", filter = CategoryFilter.Uncategorised)

    assertEquals(listOf(plank, squat, dips), sections.flatMap { it.exercises })
  }

  @Test
  fun sections_query_matchesEveryWordIgnoringCaseAndOrder() {
    val names = { query: String ->
      buildSections(all, query, CategoryFilter.All).flatMap { it.exercises }.map { it.name }
    }

    assertEquals(
      listOf("Bench Press (Barbell)", "Overhead Press (Dumbbell)", "Shoulder Press (Machine)"),
      names("press"),
    )
    assertEquals(listOf("Shoulder Press (Machine)"), names("machine PRESS"))
    assertEquals(listOf("Bench Press (Barbell)", "Squat (Barbell)"), names("  barbell "))
    assertEquals(emptyList<String>(), names("press kettlebell"))
  }

  @Test
  fun sections_queryAndFilterCombine() {
    val sections = buildSections(all, query = "press", filter = CategoryFilter.Category(20))

    assertEquals(listOf(benchPress), sections.flatMap { it.exercises })
  }

  private val chest = CategoryItem(1, "Chest", exerciseCount = 0)
  private val shoulders = CategoryItem(2, "Shoulders", exerciseCount = 0)
  private val tricepsItem = CategoryItem(3, "Triceps", exerciseCount = 0)

  private fun exercise(id: Long, mainId: Long?, vararg subIds: Long) =
    ExerciseItem(id, "Exercise $id", mainId, null, subIds.map { CategoryEntity(it, "Category $it") })

  @Test
  fun subCategoryStates_tellAllSomeAndNoneApart() {
    val selected = listOf(exercise(1, null, 2, 3), exercise(2, null, 3), exercise(3, null, 3))

    val states = subCategoryStates(selected, listOf(chest, shoulders, tricepsItem))

    assertEquals(
      mapOf(
        1L to ToggleableState.Off,
        2L to ToggleableState.Indeterminate,
        3L to ToggleableState.On,
      ),
      states,
    )
  }

  @Test
  fun subCategoryStates_ignoreExercisesWhoseMainCategoryItIs() {
    // Chest is the main category of both, so it is not on offer at all. Shoulders is the main
    // category of one: only the other one counts, and it has it.
    val selected = listOf(exercise(1, 1, 2), exercise(2, 1), exercise(3, 2))

    val states = subCategoryStates(selected.take(2), listOf(chest, shoulders))
    val withShouldersMain = subCategoryStates(listOf(selected[0], selected[2]), listOf(shoulders))

    assertEquals(mapOf(2L to ToggleableState.Indeterminate), states)
    assertEquals(mapOf(2L to ToggleableState.On), withShouldersMain)
  }

  @Test
  fun nextSubCategoryState_cyclesAndOffersUnchangedOnlyForMixedSelections() {
    val on = ToggleableState.On
    val off = ToggleableState.Off
    val mixed = ToggleableState.Indeterminate

    assertEquals(on, nextSubCategoryState(initial = off, current = off))
    assertEquals(off, nextSubCategoryState(initial = off, current = on))
    assertEquals(off, nextSubCategoryState(initial = on, current = on))
    assertEquals(on, nextSubCategoryState(initial = on, current = off))
    assertEquals(on, nextSubCategoryState(initial = mixed, current = mixed))
    assertEquals(off, nextSubCategoryState(initial = mixed, current = on))
    assertEquals(mixed, nextSubCategoryState(initial = mixed, current = off))
  }

  @Test
  fun subCategoryChange_addsWhatWasTickedAndRemovesWhatWasUnticked() {
    val initial =
      mapOf(
        1L to ToggleableState.Off,
        2L to ToggleableState.Indeterminate,
        3L to ToggleableState.On,
        4L to ToggleableState.Indeterminate,
        5L to ToggleableState.On,
      )
    val edited =
      initial +
        mapOf(
          1L to ToggleableState.On,
          2L to ToggleableState.On,
          3L to ToggleableState.Off,
          4L to ToggleableState.Off,
          // A category created in the dialog was not there initially.
          6L to ToggleableState.On,
        )

    val change = subCategoryChange(initial, edited)

    assertEquals(SubCategoryChange(add = setOf(1L, 2L, 6L), remove = setOf(3L, 4L)), change)
  }

  @Test
  fun subCategoryChange_untouchedDialogChangesNothing() {
    val initial = mapOf(1L to ToggleableState.Indeterminate, 2L to ToggleableState.On)

    assertEquals(SubCategoryChange(emptySet(), emptySet()), subCategoryChange(initial, initial))
  }

  @Test
  fun groupBySession_keepsNewestFirstOrderAndSetOrder() {
    val sets =
      listOf(
        LoggedSet(sessionId = 9, startedAt = 300, weightKg = 62.5, reps = 8),
        LoggedSet(sessionId = 9, startedAt = 300, weightKg = 62.5, reps = 7),
        LoggedSet(sessionId = 4, startedAt = 100, weightKg = 60.0, reps = 8),
      )

    val grouped = groupBySession(sets)

    assertEquals(listOf(9L, 4L), grouped.map { it.sessionId })
    assertEquals(listOf(8, 7), grouped[0].sets.map { it.reps })
    assertEquals(100L, grouped[1].startedAt)
  }

  @Test
  fun bestSet_isHeaviest_thenMostReps() {
    val heavy = LoggedSet(sessionId = 1, startedAt = 100, weightKg = 62.5, reps = 6)
    val heavyMoreReps = LoggedSet(sessionId = 2, startedAt = 200, weightKg = 62.5, reps = 8)
    val lightManyReps = LoggedSet(sessionId = 3, startedAt = 300, weightKg = 50.0, reps = 15)

    assertEquals(heavyMoreReps, bestSet(listOf(heavy, lightManyReps, heavyMoreReps)))
  }

  @Test
  fun bestSet_bodyweightOnly_isMostReps() {
    val ten = LoggedSet(sessionId = 1, startedAt = 100, weightKg = null, reps = 10)
    val twelve = LoggedSet(sessionId = 2, startedAt = 200, weightKg = null, reps = 12)

    assertEquals(twelve, bestSet(listOf(ten, twelve)))
    assertNull(bestSet(emptyList()))
  }

  @Test
  fun sectionsFold_onlyTheFullList() {
    assertTrue(sectionsFold(query = "", filter = CategoryFilter.All))
    assertTrue(sectionsFold(query = "  ", filter = CategoryFilter.All))
    assertFalse(sectionsFold(query = "press", filter = CategoryFilter.All))
    assertFalse(sectionsFold(query = "", filter = CategoryFilter.Category(10)))
    assertFalse(sectionsFold(query = "", filter = CategoryFilter.Uncategorised))
  }

  @Test
  fun isOpen_foldedListShowsOnlyOpenedSections_otherwiseAll() {
    val sections = buildSections(all, query = "", filter = CategoryFilter.All)
    val folded = SectionFolding(folded = true, open = setOf(10, null))

    // Bench (20) stays closed; Shoulder press (10) and the uncategorised ones were opened.
    assertEquals(listOf(false, true, true), sections.map { folded.isOpen(it) })
    assertEquals(
      listOf(true, true, true),
      sections.map { folded.copy(folded = false).isOpen(it) },
    )
  }

  @Test
  fun toggle_opensAClosedSectionAndClosesAnOpenOne() {
    assertEquals(setOf(10L, null), setOf<Long?>(10).toggle(null))
    assertEquals(setOf<Long?>(null), setOf<Long?>(10, null).toggle(10))
  }

  @Test
  fun pickerOpenSections_startWithTheReplacedExercisesCategory_thenAsTheUserLeftThem() {
    assertEquals(emptySet<Long?>(), pickerOpenSections(opened = null, replaced = null))
    assertEquals(setOf<Long?>(10), pickerOpenSections(opened = null, replaced = machinePress))
    // An uncategorised exercise's stand-ins are not listed first, so nothing opens for it.
    assertEquals(emptySet<Long?>(), pickerOpenSections(opened = null, replaced = plank))
    // Once a heading was tapped, the user's choice counts, also closing the replaced one's.
    assertEquals(emptySet<Long?>(), pickerOpenSections(opened = emptySet(), replaced = machinePress))
    assertEquals(setOf(20L, 10L), pickerOpenSections(opened = setOf(20, 10), replaced = null))
  }
}
