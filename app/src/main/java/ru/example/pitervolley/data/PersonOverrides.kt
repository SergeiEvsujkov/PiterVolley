package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ручные правки состава людей:
 *  - split: запись игрока считается отдельным человеком (федерация ошибочно склеила
 *    двух тёзок или один игрок заявлен в двух командах как два разных человека);
 *  - merge: два разных написания/записи считаются одним человеком.
 */
object PersonOverrides {

    private const val PREFS = "person_overrides"
    private const val KEY_SPLIT = "split_ids"
    private const val KEY_MERGES = "merges"

    private lateinit var prefs: android.content.SharedPreferences

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> get() = _changes

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isSplit(playerId: String): Boolean =
        ::prefs.isInitialized && prefs.getStringSet(KEY_SPLIT, emptySet())?.contains(playerId) == true

    fun split(playerId: String) {
        val current = prefs.getStringSet(KEY_SPLIT, emptySet()).orEmpty().toMutableSet()
        if (!current.add(playerId)) return
        prefs.edit().putStringSet(KEY_SPLIT, current).apply()
        _changes.value = _changes.value + 1
    }

    fun merges(): Map<String, String> =
        if (!::prefs.isInitialized) emptyMap()
        else prefs.getStringSet(KEY_MERGES, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(">")
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

    fun merge(from: String, to: String) {
        if (from == to) return
        val current = prefs.getStringSet(KEY_MERGES, emptySet()).orEmpty().toMutableSet()
        current.add("$from>$to")
        prefs.edit().putStringSet(KEY_MERGES, current).apply()
        _changes.value = _changes.value + 1
    }

    /** Применяет цепочку слияний, защищаясь от циклов. */
    fun resolve(key: String): String {
        val map = merges()
        if (map.isEmpty()) return key
        var current = key
        val seen = HashSet<String>()
        while (true) {
            if (!seen.add(current)) return current
            current = map[current] ?: return current
        }
    }

    fun clear() {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(KEY_SPLIT).remove(KEY_MERGES).apply()
        _changes.value = _changes.value + 1
    }

    fun splitCount(): Int =
        if (!::prefs.isInitialized) 0 else prefs.getStringSet(KEY_SPLIT, emptySet())?.size ?: 0

    fun mergeCount(): Int = merges().size

    fun hasAny(): Boolean = splitCount() > 0 || mergeCount() > 0

    /** Есть ли ручная правка, затрагивающая этого человека. */
    fun isManuallyAdjusted(personKey: String, playerIds: Collection<String>): Boolean {
        if (playerIds.any { isSplit(it) }) return true
        val map = merges()
        return map.containsKey(personKey) || map.values.any { it == personKey }
    }

    /** Сбрасывает правки, относящиеся к одному человеку. */
    fun resetFor(personKey: String, playerIds: Collection<String>) {
        if (!::prefs.isInitialized) return
        val split = prefs.getStringSet(KEY_SPLIT, emptySet()).orEmpty().toMutableSet()
        playerIds.forEach { split.remove(it) }
        val merges = merges().filterNot { it.key == personKey || it.value == personKey }
        prefs.edit()
            .putStringSet(KEY_SPLIT, split)
            .putStringSet(KEY_MERGES, merges.map { "${it.key}>${it.value}" }.toSet())
            .apply()
        _changes.value = _changes.value + 1
    }
}
