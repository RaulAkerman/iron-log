package com.ironlog.app.ui.exercises

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ironlog.app.R
import com.ironlog.app.data.db.ExerciseItem
import com.ironlog.app.ui.common.categoryLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which sections of an exercise list show their exercises. Only a [folded] list hides any (see
 * [sectionsFold]); there, the sections in [open] are open, by main category id (null =
 * uncategorised).
 */
data class SectionFolding(val folded: Boolean = true, val open: Set<Long?> = emptySet()) {
  fun isOpen(section: ExerciseSection): Boolean = !folded || section.categoryId in open
}

/** These open sections with the one of [categoryId] opened if it was closed, or closed if open. */
fun Set<Long?>.toggle(categoryId: Long?): Set<Long?> =
  if (categoryId in this) this - categoryId else this + categoryId

/**
 * [sections] as headings that stay pinned while their exercises scroll by, each followed by its
 * exercises, drawn by [row], while [folding] has it open. In a folded list a tap on a heading
 * calls [onToggle] with the section's main category.
 */
internal fun LazyListScope.exerciseSections(
  sections: List<ExerciseSection>,
  folding: SectionFolding,
  listState: LazyListState,
  scope: CoroutineScope,
  onToggle: (Long?) -> Unit,
  row: @Composable (ExerciseItem) -> Unit,
) {
  var itemCount = 0
  for (section in sections) {
    val headerIndex = itemCount
    val open = folding.isOpen(section)
    stickyHeader(key = "header-${section.categoryId}") {
      SectionHeader(
        section.title ?: stringResource(R.string.category_uncategorised),
        section.exercises.size,
        open = open.takeIf { folding.folded },
        onToggle = {
          // Closing the section the list is scrolled into would leave the view somewhere in the
          // sections below; keep its heading at the top instead.
          if (open && listState.firstVisibleItemIndex > headerIndex) {
            scope.launch { listState.scrollToItem(headerIndex) }
          }
          onToggle(section.categoryId)
        },
      )
    }
    itemCount += 1
    if (!open) continue
    items(section.exercises, key = { it.id }) { row(it) }
    itemCount += section.exercises.size
  }
}

/**
 * The heading of a section of exercises. With [open] set, tapping it opens or closes the section
 * and an arrow shows which it is.
 */
@Composable
private fun SectionHeader(title: String, count: Int, open: Boolean?, onToggle: () -> Unit) {
  val text = stringResource(R.string.section_header, title, count)
  val style = MaterialTheme.typography.titleSmall
  val color = MaterialTheme.colorScheme.primary
  // Opaque, so rows scrolling underneath a pinned header do not show through.
  val background = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
  if (open == null) {
    Text(
      text,
      style = style,
      color = color,
      modifier = background.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    return
  }
  val stateText = stringResource(if (open) R.string.section_expanded else R.string.section_collapsed)
  val arrowRotation by animateFloatAsState(if (open) 180f else 0f, label = "section arrow")
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      background
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = stateText }
        .heightIn(min = 48.dp)
        .padding(horizontal = 16.dp),
  ) {
    Text(text, style = style, color = color, modifier = Modifier.weight(1f))
    Icon(
      Icons.Filled.KeyboardArrowDown,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.rotate(arrowRotation),
    )
  }
}

/** The line under an exercise's name with its categories, or null when it has none. */
internal fun categoriesContent(exercise: ExerciseItem): (@Composable () -> Unit)? =
  if (exercise.categoryName == null && exercise.subCategories.isEmpty()) {
    null
  } else {
    { Text(categoryLine(exercise.categoryName, exercise.subCategories.map { it.name })) }
  }
