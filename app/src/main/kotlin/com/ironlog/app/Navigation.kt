package com.ironlog.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ironlog.app.ui.exercises.CategoriesScreen
import com.ironlog.app.ui.exercises.ExerciseDetailScreen
import com.ironlog.app.ui.exercises.ExercisePickerScreen
import com.ironlog.app.ui.exercises.ExercisesScreen
import com.ironlog.app.ui.history.HistoryScreen
import com.ironlog.app.ui.history.SessionDetailScreen
import com.ironlog.app.ui.home.HomeScreen
import com.ironlog.app.ui.plan.PlanScreen
import com.ironlog.app.ui.plan.WorkoutEditorScreen
import com.ironlog.app.ui.session.SessionScreen

private data class TopLevelDestination(
  val key: NavKey,
  val labelRes: Int,
  val icon: ImageVector? = null,
  val iconRes: Int? = null,
)

private val TopLevelDestinations =
  listOf(
    TopLevelDestination(Home, R.string.nav_home, icon = Icons.Filled.Home),
    TopLevelDestination(Plan, R.string.nav_plan, icon = Icons.AutoMirrored.Filled.List),
    TopLevelDestination(Exercises, R.string.nav_exercises, iconRes = R.drawable.ic_dumbbell),
    TopLevelDestination(History, R.string.nav_history, icon = Icons.Filled.DateRange),
  )

/** Shows a top-level destination. Home stays at the bottom of the stack, so Back returns to it. */
private fun NavBackStack<NavKey>.switchTo(destination: NavKey) {
  if (lastOrNull() == destination) return
  while (size > 1) removeLastOrNull()
  if (destination != Home) add(destination)
}

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Home)
  val current = backStack.lastOrNull()
  val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

  Scaffold(
    // Each screen has its own top bar and handles the system bars itself.
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (TopLevelDestinations.any { it.key == current }) {
        NavigationBar {
          for (destination in TopLevelDestinations) {
            NavigationBarItem(
              selected = current == destination.key,
              onClick = { backStack.switchTo(destination.key) },
              icon = {
                if (destination.icon != null) {
                  Icon(destination.icon, contentDescription = null)
                } else if (destination.iconRes != null) {
                  Icon(painterResource(destination.iconRes), contentDescription = null)
                }
              },
              label = { Text(stringResource(destination.labelRes)) },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
      onBack = goBack,
      entryDecorators =
        listOf(
          rememberSaveableStateHolderNavEntryDecorator(),
          // Gives every screen on the stack its own ViewModels, cleared when it is popped.
          rememberViewModelStoreNavEntryDecorator(),
        ),
      entryProvider =
        entryProvider {
          entry<Home> {
            HomeScreen(
              onOpenSession = { backStack.add(ActiveSession) },
              onOpenPlan = { backStack.switchTo(Plan) },
            )
          }
          entry<Plan> { PlanScreen(onOpenWorkout = { backStack.add(WorkoutEditor(it)) }) }
          entry<WorkoutEditor> { key ->
            WorkoutEditorScreen(
              workoutId = key.workoutId,
              onBack = goBack,
              onAddExercises = { backStack.add(ExercisePicker(workoutId = key.workoutId)) },
            )
          }
          entry<Exercises> {
            ExercisesScreen(
              onOpenExercise = { backStack.add(ExerciseDetail(it)) },
              onOpenCategories = { backStack.add(Categories) },
            )
          }
          entry<Categories> { CategoriesScreen(onBack = goBack) }
          entry<ExerciseDetail> { key ->
            ExerciseDetailScreen(exerciseId = key.exerciseId, onBack = goBack)
          }
          entry<ExercisePicker> { key -> ExercisePickerScreen(target = key, onDone = goBack) }
          entry<ActiveSession> {
            SessionScreen(
              onBack = goBack,
              onAddExercises = { sessionId -> backStack.add(ExercisePicker(sessionId = sessionId)) },
              onReplaceExercise = { sessionExerciseId, exerciseId ->
                backStack.add(
                  ExercisePicker(
                    replaceSessionExerciseId = sessionExerciseId,
                    replacedExerciseId = exerciseId,
                  )
                )
              },
            )
          }
          entry<History> { HistoryScreen(onOpenSession = { backStack.add(SessionDetail(it)) }) }
          entry<SessionDetail> { key ->
            SessionDetailScreen(sessionId = key.sessionId, onBack = goBack)
          }
        },
    )
  }
}
