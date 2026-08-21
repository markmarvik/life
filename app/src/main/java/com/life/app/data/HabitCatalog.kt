package com.life.app.data

/**
 * Static habit catalog (from Steady onboarding suggestions, stripped of
 * type / group / target). Never auto-seeded — the user taps to log, which
 * creates a [HabitDef] with a stable catalog id.
 */
data class CatalogHabit(
    val id: String,
    val name: String,
    val icon: String,
    val why: String
)

object HabitCatalog {
    val ALL: List<CatalogHabit> = listOf(
        CatalogHabit("cat_morning_sun", "Morning sunlight", "☀️", "Aligns circadian rhythm."),
        CatalogHabit("cat_meditate", "Meditate", "🪷", "Start calm and present."),
        CatalogHabit("cat_brush", "Brush + floss", "🪥", "Dental foundation."),
        CatalogHabit("cat_make_bed", "Make the bed", "🛏️", "Small win, tidy space."),
        CatalogHabit("cat_hydrate", "Morning hydrate", "💧", "Glass of water on wake."),
        CatalogHabit("cat_cold_shower", "Cold shower / shower", "🚿", "Wake-up and hygiene."),
        CatalogHabit("cat_protein", "Protein first", "🥩", "Muscle, satiety, metabolism."),
        CatalogHabit("cat_lunch_walk", "Lunch walk", "🚶", "Midday movement reset."),
        CatalogHabit("cat_stretch", "Stand / stretch break", "🧍", "Undo sitting."),
        CatalogHabit("cat_move", "Move your body", "🚶", "20+ min walk or exercise."),
        CatalogHabit("cat_strength", "Strength training", "🏋️", "Weights, bodyweight, or gym."),
        CatalogHabit("cat_read", "Read before bed", "📖", "Calm input, not scroll."),
        CatalogHabit("cat_gratitude", "Gratitude / 3 things", "🙏", "Evening review."),
        CatalogHabit("cat_no_screens", "No screens wind-down", "📵", "Protect melatonin."),
        CatalogHabit("cat_evening_hygiene", "Evening hygiene", "🪥", "Brush + floss at night.")
    )

    fun byId(id: String): CatalogHabit? = ALL.firstOrNull { it.id == id }

    fun byName(name: String): CatalogHabit? {
        val n = name.trim()
        return ALL.firstOrNull { it.name.equals(n, ignoreCase = true) }
    }
}
