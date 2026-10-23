package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Что напоминать в 18:00 за день до игры. Источники комбинируются:
 * все игры, избранные команды, избранные игроки, отдельные дивизионы.
 */
object ReminderSettings {

    private const val PREFS = "reminder_settings"
    private const val KEY_ALL = "all_games"
    private const val KEY_TEAMS = "favorite_teams"
    private const val KEY_PLAYERS = "favorite_players"
    private const val KEY_DIVISIONS_ON = "divisions_on"
    private const val KEY_DIVISIONS = "division_ids"

    data class State(
        val allGames: Boolean = false,
        val favoriteTeams: Boolean = true,
        val favoritePlayers: Boolean = true,
        val divisionsOn: Boolean = false,
        val divisionIds: Set<Int> = emptySet(),
    ) {
        val anyEnabled: Boolean
            get() = allGames || favoriteTeams || favoritePlayers || (divisionsOn && divisionIds.isNotEmpty())
    }

    private lateinit var prefs: android.content.SharedPreferences
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> get() = _state

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = State(
            allGames = prefs.getBoolean(KEY_ALL, false),
            favoriteTeams = prefs.getBoolean(KEY_TEAMS, true),
            favoritePlayers = prefs.getBoolean(KEY_PLAYERS, true),
            divisionsOn = prefs.getBoolean(KEY_DIVISIONS_ON, false),
            divisionIds = prefs.getStringSet(KEY_DIVISIONS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        )
    }

    private fun save(value: State) {
        _state.value = value
        prefs.edit()
            .putBoolean(KEY_ALL, value.allGames)
            .putBoolean(KEY_TEAMS, value.favoriteTeams)
            .putBoolean(KEY_PLAYERS, value.favoritePlayers)
            .putBoolean(KEY_DIVISIONS_ON, value.divisionsOn)
            .putStringSet(KEY_DIVISIONS, value.divisionIds.map { it.toString() }.toSet())
            .apply()
    }

    fun setAllGames(enabled: Boolean) = save(_state.value.copy(allGames = enabled))

    fun setFavoriteTeams(enabled: Boolean) = save(_state.value.copy(favoriteTeams = enabled))

    fun setFavoritePlayers(enabled: Boolean) = save(_state.value.copy(favoritePlayers = enabled))

    fun setDivisionsOn(enabled: Boolean) = save(_state.value.copy(divisionsOn = enabled))

    fun toggleDivision(divisionId: Int): Boolean {
        val current = _state.value.divisionIds
        val next = if (divisionId in current) current - divisionId else current + divisionId
        save(_state.value.copy(divisionIds = next, divisionsOn = true))
        return divisionId in next
    }

    /** «Без напоминаний»: выключает все источники сразу. */
    fun disableAll() = save(State(allGames = false, favoriteTeams = false, favoritePlayers = false))

    /** Состояние по умолчанию (избранные команды и игроки). */
    fun restoreDefaults() = save(State())
}
