package com.sparnai.bari.core

/**
 * Three-way merge of the trip when a new app version ships a changed config/trip.json:
 * things the user did not touch follow the new defaults, the user's own edits and additions are kept.
 */
object TripMerge {
    fun merge(user: TripConfig, oldBase: TripConfig, newBase: TripConfig): TripConfig = user.copy(
        name = if (user.name == oldBase.name) newBase.name else user.name,
        segments = mergeById(user.segments, oldBase.segments, newBase.segments) { it.id },
        routes = mergeById(user.routes, oldBase.routes, newBase.routes) { it.id },
    )

    private fun <T> mergeById(user: List<T>, old: List<T>, new: List<T>, id: (T) -> String): List<T> {
        val oldById = old.associateBy(id)
        val newById = new.associateBy(id)
        val userIds = user.map(id).toSet()
        val result = mutableListOf<T>()
        for (item in user) {
            val key = id(item)
            val before = oldById[key]
            val after = newById[key]
            when {
                before == null -> result += item // added by the user
                item != before -> result += item // edited by the user
                after == null -> Unit // removed from the defaults and untouched → drop
                else -> result += after // untouched → follow the defaults
            }
        }
        // New default items; skip ones the user deliberately deleted earlier.
        new.filter { id(it) !in userIds && id(it) !in oldById }.forEach { result += it }
        return result
    }
}
