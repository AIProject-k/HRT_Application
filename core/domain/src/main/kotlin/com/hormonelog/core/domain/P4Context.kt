package com.hormonelog.core.domain

/** Product identity is reported by the user; never inferred for old records. */
enum class P4Product { UNKNOWN, UTROGESTAN_SOFT_CAPSULE, OTHER }

/** These are recording categories, not instructions on how to take a medicine. */
enum class P4Meal { UNKNOWN, FASTING, AFTER_MEAL, HIGH_FAT_MEAL }

data class P4Context(
    val product: P4Product = P4Product.UNKNOWN,
    val meal: P4Meal = P4Meal.UNKNOWN,
) {
    /** A plan or a frequent combination cannot establish what the person actually ate. */
    fun forNewAdministration(): P4Context = copy(meal = P4Meal.UNKNOWN)
}
