package ru.example.pitervolley.data

import android.content.Context

/**
 * Ручная привязка игрока к команде: если федерация не внесла игрока в заявку,
 * его можно добавить в состав на этом устройстве. Хранится только локально.
 */
object TeamLinks {

    private const val PREFS = "team_links"
    private const val KEY = "links"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** teamId -> playerIds */
    fun all(): Map<String, Set<String>> {
        if (!::prefs.isInitialized) return emptyMap()
        return prefs.getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(">")
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    }

    fun playersOf(teamId: String): Set<String> = all()[teamId].orEmpty()

    fun teamsOf(playerId: String): Set<String> =
        all().filterValues { playerId in it }.keys

    fun add(teamId: String, playerId: String) {
        if (!::prefs.isInitialized) return
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.add("$teamId>$playerId")
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun remove(teamId: String, playerId: String) {
        if (!::prefs.isInitialized) return
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.remove("$teamId>$playerId")
        prefs.edit().putStringSet(KEY, current).apply()
    }

    /**
     * Убирает привязки, которые стали лишними: игрок уже в этой команде
     * по данным федерации или игрока больше нет.
     */
    fun prune(players: List<Player>) {
        if (!::prefs.isInitialized) return
        val byId = players.associateBy { it.id }
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        val actual = current.filter { entry ->
            val parts = entry.split(">")
            if (parts.size != 2) return@filter false
            val player = byId[parts[1]] ?: return@filter false
            player.teamId != parts[0]
        }.toSet()
        if (actual != current) prefs.edit().putStringSet(KEY, actual).apply()
    }

    fun count(): Int = all().values.sumOf { it.size }

    fun clear() {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(KEY).apply()
    }
}
