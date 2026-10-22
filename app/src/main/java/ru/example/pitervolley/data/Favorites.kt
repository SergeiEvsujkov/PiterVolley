package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Favorite teams and players, stored in SharedPreferences. */
object Favorites {

    private const val PREFS = "favorites"
    private const val KEY_TEAMS = "team_ids"
    private const val KEY_PLAYERS = "player_ids"

    private lateinit var prefs: android.content.SharedPreferences

    private val _teams = MutableStateFlow<Set<String>>(emptySet())
    val teams: StateFlow<Set<String>> get() = _teams

    private val _players = MutableStateFlow<Set<String>>(emptySet())
    val players: StateFlow<Set<String>> get() = _players

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _teams.value = prefs.getStringSet(KEY_TEAMS, emptySet())?.toSet().orEmpty()
        _players.value = prefs.getStringSet(KEY_PLAYERS, emptySet())?.toSet().orEmpty()
    }

    fun isFavorite(teamId: String): Boolean = teamId in _teams.value

    fun toggle(teamId: String): Boolean {
        val current = _teams.value
        val next = if (teamId in current) current - teamId else current + teamId
        _teams.value = next
        prefs.edit().putStringSet(KEY_TEAMS, next).apply()
        return teamId in next
    }

    fun isPlayerFavorite(playerId: String): Boolean = playerId in _players.value

    fun togglePlayer(playerId: String): Boolean {
        val current = _players.value
        val next = if (playerId in current) current - playerId else current + playerId
        _players.value = next
        prefs.edit().putStringSet(KEY_PLAYERS, next).apply()
        return playerId in next
    }

    /** Team ids of the favourite teams and of the teams the favourite players belong to. */
    fun trackedTeamIds(): Set<String> {
        val teamsOfPlayers = _players.value.mapNotNull { playerId ->
            Repo.data.players.firstOrNull { it.id == playerId }?.teamId
        }
        return _teams.value + teamsOfPlayers
    }
}
