package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Favorite teams and players. Players are stored as person keys, so one person
 * who is registered for several teams is still a single favourite.
 */
object Favorites {

    private const val PREFS = "favorites"
    private const val KEY_TEAMS = "team_ids"
    private const val KEY_PLAYERS = "player_ids"

    private lateinit var prefs: android.content.SharedPreferences

    private val _teams = MutableStateFlow<Set<String>>(emptySet())
    val teams: StateFlow<Set<String>> get() = _teams

    private val _persons = MutableStateFlow<Set<String>>(emptySet())
    val persons: StateFlow<Set<String>> get() = _persons

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _teams.value = prefs.getStringSet(KEY_TEAMS, emptySet())?.toSet().orEmpty()
        _persons.value = prefs.getStringSet(KEY_PLAYERS, emptySet())?.toSet().orEmpty()
    }

    fun isFavorite(teamId: String): Boolean = teamId in _teams.value

    fun toggle(teamId: String): Boolean {
        val current = _teams.value
        val next = if (teamId in current) current - teamId else current + teamId
        _teams.value = next
        prefs.edit().putStringSet(KEY_TEAMS, next).apply()
        return teamId in next
    }

    /** Ключи людей, приведённые к актуальному виду (старые записи хранили id игрока). */
    fun personKeys(): Set<String> {
        if (!Repo.isLoaded) return _persons.value
        return _persons.value.map { Repo.resolvePersonKey(it) }.toSet()
    }

    fun isPersonFavorite(key: String): Boolean = key in personKeys()

    fun togglePerson(key: String): Boolean {
        val current = _persons.value
        val normalized = if (Repo.isLoaded) Repo.resolvePersonKey(key) else key
        val exists = normalized in personKeys()
        val next = if (exists) {
            _persons.value.filterNot { if (Repo.isLoaded) Repo.resolvePersonKey(it) == normalized else it == normalized }
                .toSet()
        } else {
            _persons.value + normalized
        }
        _persons.value = next
        prefs.edit().putStringSet(KEY_PLAYERS, next).apply()
        return !exists
    }

    /** Team ids of the favourite teams and of every team the favourite people play for. */
    fun trackedTeamIds(): Set<String> {
        if (!Repo.isLoaded) return _teams.value
        val teamsOfPersons = personKeys().flatMap { key ->
            Repo.teamsOfPerson(key).map { it.id }
        }
        return _teams.value + teamsOfPersons
    }
}
