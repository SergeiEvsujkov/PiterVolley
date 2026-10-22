package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Favorite teams, stored in SharedPreferences. */
object Favorites {

    private const val PREFS = "favorites"
    private const val KEY = "team_ids"

    private lateinit var prefs: android.content.SharedPreferences
    private val _teams = MutableStateFlow<Set<String>>(emptySet())
    val teams: StateFlow<Set<String>> get() = _teams

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _teams.value = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()
    }

    fun isFavorite(teamId: String): Boolean = teamId in _teams.value

    fun toggle(teamId: String): Boolean {
        val current = _teams.value
        val next = if (teamId in current) current - teamId else current + teamId
        _teams.value = next
        prefs.edit().putStringSet(KEY, next).apply()
        return teamId in next
    }
}
