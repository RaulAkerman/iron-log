package com.ironlog.app.data.db

/**
 * An exercise of the pre-loaded list. [category] is its main category, the muscle group it mostly
 * trains; [subCategories] are the other muscle groups that do a real share of the work. Categories
 * are given by name and created when they do not exist yet.
 */
data class SeedExercise(
  val name: String,
  val category: String,
  val subCategories: List<String> = emptyList(),
  /** The database version whose pre-loaded list first contained this exercise. */
  val since: Int = 1,
)

/**
 * The first database version whose pre-loaded list carries categories. A database older than that
 * gets them filled in when it is upgraded, see `planPreload`.
 */
internal const val SEED_CATEGORIES_SINCE = 3

private const val ABS = "Abs"
private const val BACK = "Back"
private const val BICEPS = "Biceps"
private const val CHEST = "Chest"
private const val FOREARMS = "Forearms"
private const val LEGS = "Legs"
private const val SHOULDERS = "Shoulders"
private const val TRICEPS = "Triceps"

private fun exercise(name: String, category: String, vararg subCategories: String) =
  SeedExercise(name, category, subCategories.toList())

/** The list as it was in database versions 1 and 2, where it had no categories. */
private val Version1: List<SeedExercise> =
  listOf(
    // Chest
    exercise("Bench Press (Barbell)", CHEST, TRICEPS, SHOULDERS),
    exercise("Bench Press (Dumbbell)", CHEST, TRICEPS, SHOULDERS),
    exercise("Bench Press (Smith Machine)", CHEST, TRICEPS, SHOULDERS),
    exercise("Incline Bench Press (Barbell)", CHEST, SHOULDERS, TRICEPS),
    exercise("Incline Bench Press (Dumbbell)", CHEST, SHOULDERS, TRICEPS),
    exercise("Decline Bench Press (Barbell)", CHEST, TRICEPS),
    exercise("Chest Press (Machine)", CHEST, TRICEPS, SHOULDERS),
    exercise("Incline Chest Press (Machine)", CHEST, SHOULDERS, TRICEPS),
    exercise("Chest Fly (Dumbbell)", CHEST),
    exercise("Chest Fly (Cable)", CHEST),
    exercise("Chest Fly (Machine)", CHEST),
    exercise("Push-Up", CHEST, TRICEPS, SHOULDERS),
    exercise("Chest Dip", CHEST, TRICEPS, SHOULDERS),
    exercise("Pullover (Dumbbell)", CHEST, BACK),
    // Shoulders
    exercise("Overhead Press (Barbell)", SHOULDERS, TRICEPS),
    exercise("Overhead Press (Dumbbell)", SHOULDERS, TRICEPS),
    exercise("Shoulder Press (Machine)", SHOULDERS, TRICEPS),
    exercise("Arnold Press (Dumbbell)", SHOULDERS, TRICEPS),
    exercise("Landmine Press", SHOULDERS, CHEST, TRICEPS),
    exercise("Lateral Raise (Dumbbell)", SHOULDERS),
    exercise("Lateral Raise (Cable)", SHOULDERS),
    exercise("Lateral Raise (Machine)", SHOULDERS),
    exercise("Front Raise (Dumbbell)", SHOULDERS),
    exercise("Reverse Fly (Dumbbell)", SHOULDERS, BACK),
    exercise("Reverse Fly (Machine)", SHOULDERS, BACK),
    exercise("Face Pull (Cable)", BACK, SHOULDERS),
    exercise("Upright Row (Barbell)", SHOULDERS, BACK),
    exercise("Shrug (Barbell)", BACK),
    exercise("Shrug (Dumbbell)", BACK),
    // Back
    exercise("Deadlift (Barbell)", LEGS, BACK),
    exercise("Rack Pull (Barbell)", BACK, LEGS),
    exercise("Pull-Up", BACK, BICEPS),
    exercise("Chin-Up", BACK, BICEPS),
    exercise("Assisted Pull-Up (Machine)", BACK, BICEPS),
    exercise("Lat Pulldown (Wide Grip)", BACK, BICEPS),
    exercise("Lat Pulldown (Close Grip)", BACK, BICEPS),
    exercise("Straight-Arm Pulldown (Cable)", BACK),
    exercise("Bent-Over Row (Barbell)", BACK, BICEPS),
    exercise("One-Arm Row (Dumbbell)", BACK, BICEPS),
    exercise("Chest-Supported Row (Dumbbell)", BACK, BICEPS),
    exercise("T-Bar Row", BACK, BICEPS),
    exercise("Seated Row (Cable)", BACK, BICEPS),
    exercise("Seated Row (Machine)", BACK, BICEPS),
    exercise("Inverted Row", BACK, BICEPS),
    exercise("Back Extension", BACK, LEGS),
    // Biceps
    exercise("Biceps Curl (Barbell)", BICEPS),
    exercise("Biceps Curl (Dumbbell)", BICEPS),
    exercise("Biceps Curl (EZ Bar)", BICEPS),
    exercise("Biceps Curl (Cable)", BICEPS),
    exercise("Biceps Curl (Machine)", BICEPS),
    exercise("Hammer Curl (Dumbbell)", BICEPS, FOREARMS),
    exercise("Hammer Curl (Cable Rope)", BICEPS, FOREARMS),
    exercise("Preacher Curl (EZ Bar)", BICEPS),
    exercise("Preacher Curl (Machine)", BICEPS),
    exercise("Incline Curl (Dumbbell)", BICEPS),
    exercise("Concentration Curl (Dumbbell)", BICEPS),
    // Triceps
    exercise("Triceps Pushdown (Cable Bar)", TRICEPS),
    exercise("Triceps Pushdown (Cable Rope)", TRICEPS),
    exercise("Overhead Triceps Extension (Dumbbell)", TRICEPS),
    exercise("Overhead Triceps Extension (Cable)", TRICEPS),
    exercise("Skull Crusher (EZ Bar)", TRICEPS),
    exercise("Close-Grip Bench Press (Barbell)", TRICEPS, CHEST, SHOULDERS),
    exercise("Triceps Dip", TRICEPS, CHEST, SHOULDERS),
    exercise("Triceps Dip (Machine)", TRICEPS, CHEST),
    exercise("Triceps Kickback (Dumbbell)", TRICEPS),
    // Forearms and grip
    exercise("Wrist Curl (Dumbbell)", FOREARMS),
    exercise("Reverse Curl (EZ Bar)", FOREARMS, BICEPS),
    exercise("Farmer's Walk", FOREARMS, BACK),
    // Quads and glutes
    exercise("Squat (Barbell)", LEGS),
    exercise("Front Squat (Barbell)", LEGS),
    exercise("Squat (Smith Machine)", LEGS),
    exercise("Goblet Squat (Dumbbell)", LEGS),
    exercise("Hack Squat (Machine)", LEGS),
    exercise("Leg Press (Machine)", LEGS),
    exercise("Leg Extension (Machine)", LEGS),
    exercise("Lunge (Dumbbell)", LEGS),
    exercise("Walking Lunge (Dumbbell)", LEGS),
    exercise("Bulgarian Split Squat (Dumbbell)", LEGS),
    exercise("Step-Up (Dumbbell)", LEGS),
    exercise("Hip Thrust (Barbell)", LEGS),
    exercise("Hip Thrust (Machine)", LEGS),
    exercise("Glute Bridge", LEGS),
    exercise("Glute Kickback (Cable)", LEGS),
    exercise("Hip Abduction (Machine)", LEGS),
    exercise("Hip Adduction (Machine)", LEGS),
    // Hamstrings
    exercise("Romanian Deadlift (Barbell)", LEGS, BACK),
    exercise("Romanian Deadlift (Dumbbell)", LEGS, BACK),
    exercise("Sumo Deadlift (Barbell)", LEGS, BACK),
    exercise("Good Morning (Barbell)", LEGS, BACK),
    exercise("Lying Leg Curl (Machine)", LEGS),
    exercise("Seated Leg Curl (Machine)", LEGS),
    // Calves
    exercise("Standing Calf Raise (Machine)", LEGS),
    exercise("Seated Calf Raise (Machine)", LEGS),
    exercise("Calf Raise (Leg Press)", LEGS),
    exercise("Calf Raise (Dumbbell)", LEGS),
    // Core
    exercise("Plank", ABS),
    exercise("Side Plank", ABS),
    exercise("Crunch", ABS),
    exercise("Crunch (Machine)", ABS),
    exercise("Crunch (Cable)", ABS),
    exercise("Sit-Up", ABS),
    exercise("Hanging Leg Raise", ABS),
    exercise("Lying Leg Raise", ABS),
    exercise("Russian Twist", ABS),
    exercise("Ab Wheel Rollout", ABS),
    exercise("Woodchopper (Cable)", ABS),
    exercise("Mountain Climber", ABS),
    // Full body
    exercise("Kettlebell Swing", LEGS, BACK),
    exercise("Power Clean (Barbell)", LEGS, BACK, SHOULDERS),
  )

/** Added to the list in database version 3. */
private val Version3: List<SeedExercise> =
  listOf(
    // Chest
    exercise("Pec Deck", CHEST),
    exercise("Incline Bench Press (Smith Machine)", CHEST, SHOULDERS, TRICEPS),
    exercise("Decline Bench Press (Dumbbell)", CHEST, TRICEPS),
    exercise("Decline Chest Press (Machine)", CHEST, TRICEPS),
    exercise("Chest Press (Cable)", CHEST, TRICEPS, SHOULDERS),
    exercise("Incline Chest Fly (Dumbbell)", CHEST),
    exercise("Low-to-High Chest Fly (Cable)", CHEST),
    exercise("High-to-Low Chest Fly (Cable)", CHEST),
    // Shoulders
    exercise("Upright Row (Cable)", SHOULDERS, BACK),
    exercise("Upright Row (Dumbbell)", SHOULDERS, BACK),
    exercise("Overhead Press (Smith Machine)", SHOULDERS, TRICEPS),
    exercise("Push Press (Barbell)", SHOULDERS, TRICEPS, LEGS),
    exercise("Front Raise (Cable)", SHOULDERS),
    exercise("Front Raise (Plate)", SHOULDERS),
    exercise("Reverse Fly (Cable)", SHOULDERS, BACK),
    exercise("Y-Raise (Cable)", SHOULDERS, BACK),
    exercise("External Rotation (Cable)", SHOULDERS),
    exercise("Shrug (Machine)", BACK),
    // Back
    exercise("Lat Pulldown (Neutral Grip)", BACK, BICEPS),
    exercise("Lat Pulldown (Underhand Grip)", BACK, BICEPS),
    exercise("Lat Pulldown (Single Arm)", BACK, BICEPS),
    exercise("Lat Pulldown (Machine)", BACK, BICEPS),
    exercise("Pull-Up (Neutral Grip)", BACK, BICEPS),
    exercise("Bent-Over Row (Dumbbell)", BACK, BICEPS),
    exercise("Pendlay Row (Barbell)", BACK, BICEPS),
    exercise("One-Arm Row (Cable)", BACK, BICEPS),
    exercise("Chest-Supported Row (Machine)", BACK, BICEPS),
    exercise("High Row (Machine)", BACK, BICEPS),
    exercise("Pullover (Machine)", BACK, CHEST),
    exercise("Back Extension (Machine)", BACK, LEGS),
    // Biceps
    exercise("Bayesian Cable Curl", BICEPS),
    exercise("Preacher Curl (Dumbbell)", BICEPS),
    exercise("Spider Curl (EZ Bar)", BICEPS),
    exercise("Spider Curl (Dumbbell)", BICEPS),
    exercise("Cross-Body Hammer Curl (Dumbbell)", BICEPS, FOREARMS),
    exercise("Drag Curl (Barbell)", BICEPS),
    exercise("Zottman Curl (Dumbbell)", BICEPS, FOREARMS),
    exercise("High Cable Curl", BICEPS),
    // Triceps
    exercise("Triceps Pushdown (Single Arm)", TRICEPS),
    exercise("Overhead Triceps Extension (EZ Bar)", TRICEPS),
    exercise("Skull Crusher (Dumbbell)", TRICEPS),
    exercise("Triceps Extension (Machine)", TRICEPS),
    exercise("Cross-Body Triceps Extension (Cable)", TRICEPS),
    exercise("Triceps Kickback (Cable)", TRICEPS),
    exercise("Bench Dip", TRICEPS, CHEST, SHOULDERS),
    exercise("Diamond Push-Up", TRICEPS, CHEST, SHOULDERS),
    exercise("Assisted Dip (Machine)", TRICEPS, CHEST, SHOULDERS),
    // Forearms and grip
    exercise("Wrist Curl (Barbell)", FOREARMS),
    exercise("Reverse Wrist Curl (Dumbbell)", FOREARMS),
    exercise("Reverse Curl (Cable)", FOREARMS, BICEPS),
    exercise("Dead Hang", FOREARMS, BACK),
    // Quads and glutes
    exercise("Pendulum Squat (Machine)", LEGS),
    exercise("Belt Squat (Machine)", LEGS),
    exercise("Sissy Squat", LEGS),
    exercise("Pistol Squat", LEGS),
    exercise("Single-Leg Press (Machine)", LEGS),
    exercise("Lunge (Barbell)", LEGS),
    exercise("Reverse Lunge (Dumbbell)", LEGS),
    exercise("Split Squat (Dumbbell)", LEGS),
    exercise("Glute Kickback (Machine)", LEGS),
    exercise("Pull-Through (Cable)", LEGS, BACK),
    // Hamstrings
    exercise("Deadlift (Trap Bar)", LEGS, BACK),
    exercise("Stiff-Leg Deadlift (Barbell)", LEGS, BACK),
    exercise("Single-Leg Romanian Deadlift (Dumbbell)", LEGS, BACK),
    exercise("Reverse Hyperextension", LEGS, BACK),
    exercise("Glute-Ham Raise", LEGS),
    exercise("Nordic Curl", LEGS),
    exercise("Standing Leg Curl (Machine)", LEGS),
    // Calves and shins
    exercise("Calf Raise (Smith Machine)", LEGS),
    exercise("Single-Leg Calf Raise", LEGS),
    exercise("Tibialis Raise", LEGS),
    // Core
    exercise("Hanging Knee Raise", ABS),
    exercise("Captain's Chair Leg Raise", ABS),
    exercise("Decline Sit-Up", ABS),
    exercise("Reverse Crunch", ABS),
    exercise("Bicycle Crunch", ABS),
    exercise("V-Up", ABS),
    exercise("Toes-to-Bar", ABS),
    exercise("Dead Bug", ABS),
    exercise("Pallof Press (Cable)", ABS),
    exercise("Side Bend (Dumbbell)", ABS),
    exercise("Torso Rotation (Machine)", ABS),
  )

/** Every pre-loaded exercise, in the order a new database gets them. */
internal val SeedExercises: List<SeedExercise> = Version1 + Version3.map { it.copy(since = 3) }
