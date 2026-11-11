package com.ironlog.app.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Something taken on a daily schedule, e.g. creatine. */
@Entity(tableName = "supplement")
data class SupplementEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
)

/**
 * One daily time at which a supplement is taken. Rows are never edited: changing a time deletes the
 * row and inserts a new one, so an id always stands for the same time of day.
 */
@Entity(
  tableName = "supplement_time",
  foreignKeys =
    [
      ForeignKey(
        entity = SupplementEntity::class,
        parentColumns = ["id"],
        childColumns = ["supplementId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["supplementId", "minuteOfDay"], unique = true)],
)
data class SupplementTimeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val supplementId: Long,
  /** Minutes after local midnight, 0..1439. */
  val minuteOfDay: Int,
)

/**
 * A dose that was taken. The dose is identified by the day and the time of day it was scheduled
 * for, not by a time row, so the record survives later changes to the schedule.
 */
@Entity(
  tableName = "supplement_intake",
  foreignKeys =
    [
      ForeignKey(
        entity = SupplementEntity::class,
        parentColumns = ["id"],
        childColumns = ["supplementId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["supplementId", "epochDay", "minuteOfDay"], unique = true)],
)
data class SupplementIntakeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val supplementId: Long,
  /** Local date the dose was scheduled for, as days since 1970-01-01. */
  val epochDay: Long,
  val minuteOfDay: Int,
  /** Epoch milliseconds at which it was ticked off. */
  val takenAt: Long,
)
