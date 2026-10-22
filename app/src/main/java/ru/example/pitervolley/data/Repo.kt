package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

/** In-memory dataset loaded once from assets/data.json. */
object Repo {

    private const val ASSET = "data.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var dataset: Dataset? = null

    private lateinit var divisionById: Map<Int, Division>
    private lateinit var teamById: Map<String, Team>
    private lateinit var playersByTeam: Map<String, List<Player>>
    private lateinit var gamesByDivision: Map<Int, List<Game>>
    private lateinit var gamesByTeam: Map<String, List<Game>>
    private val standingsCache = HashMap<Int, List<StandingRow>>()

    val isLoaded: Boolean get() = dataset != null
    val data: Dataset get() = dataset ?: error("Repo not loaded")

    /** Bumped on every dataset swap so screens can recompose. */
    val revision = MutableStateFlow(0)

    fun load(context: Context) {
        if (dataset != null) return
        val cache = Sync.cacheFile(context)
        val cached = if (cache.exists()) runCatching { cache.readText() }.getOrNull() else null
        val text = cached ?: context.assets.open(ASSET).use { it.readBytes().decodeToString() }
        apply(text)
    }

    /** Swap in a newly downloaded dataset (called from [Sync]). */
    fun apply(text: String) {
        apply(json.decodeFromString<Dataset>(text))
    }

    /** Swap in an already parsed dataset (called from [SiteSync]). */
    fun apply(ds: Dataset) {
        dataset = ds

        divisionById = ds.divisions.associateBy { it.id }
        teamById = ds.teams.associateBy { it.id }
        playersByTeam = ds.players.groupBy { it.teamId }
        gamesByDivision = ds.games.groupBy { it.divisionId }
        gamesByTeam = HashMap<String, MutableList<Game>>().apply {
            for (g in ds.games) {
                getOrPut(g.homeId) { mutableListOf() }.add(g)
                getOrPut(g.awayId) { mutableListOf() }.add(g)
            }
        }
        standingsCache.clear()
        revision.value = revision.value + 1
    }

    // ---------------------------------------------------------------- lookups

    fun division(id: Int): Division? = divisionById[id]
    fun team(id: String): Team? = teamById[id]
    fun teamsOf(divisionId: Int): List<Team> = data.teams.filter { it.divisionId == divisionId }.sortedBy { it.name }
    fun playersOf(teamId: String): List<Player> = playersByTeam[teamId].orEmpty()

    fun gamesOf(divisionId: Int): List<Game> =
        gamesByDivision[divisionId].orEmpty().sortedWith(compareBy({ it.localDate == null }, { it.localDate }, { it.time }))

    fun gamesOfTeam(teamId: String): List<Game> =
        gamesByTeam[teamId].orEmpty().sortedWith(compareBy({ it.localDate == null }, { it.localDate }, { it.time }))

    fun game(id: String): Game? = data.games.firstOrNull { it.id == id }

    fun game(divisionId: Int, id: String): Game? =
        gamesByDivision[divisionId]?.firstOrNull { it.id == id } ?: data.games.firstOrNull { it.id == id }

    fun divisionsByCompetition(): Map<String, List<Division>> =
        data.divisions.groupBy { it.competition }.toSortedMap()

    // ---------------------------------------------------------------- feeds

    /** Games with a date, from today on, nearest first. */
    fun upcoming(limit: Int): List<Game> {
        val t = today()
        return data.games
            .filter { it.localDate != null && !it.localDate!!.isBefore(t) }
            .sortedWith(compareBy({ it.localDate }, { it.time }))
            .take(limit)
    }

    /** All dated games, newest dates first (for the calendar list). */
    fun calendarGames(): List<Game> =
        data.games.filter { it.localDate != null }
            .sortedWith(compareByDescending<Game> { it.localDate }.thenBy { it.time })

    fun gamesOn(date: String): List<Game> = data.games.filter { it.date == date }.sortedBy { it.time }

    // ---------------------------------------------------------------- standings

    fun standings(divisionId: Int): List<StandingRow> = standingsCache.getOrPut(divisionId) {
        val rows = LinkedHashMap<String, StandingRow>()
        for (team in teamsOf(divisionId)) {
            rows[team.id] = StandingRow(team)
        }
        for (g in gamesOf(divisionId)) {
            if (!g.hasResult || g.sets.isEmpty()) continue
            val home = rows.getOrPut(g.homeId) { StandingRow(teamById[g.homeId] ?: Team(g.homeId, g.home, divisionId)) }
            val away = rows.getOrPut(g.awayId) { StandingRow(teamById[g.awayId] ?: Team(g.awayId, g.away, divisionId)) }
            home.played++
            away.played++
            val hs = g.homeSets
            val asets = g.awaySets
            home.setsFor += hs
            home.setsAgainst += asets
            away.setsFor += asets
            away.setsAgainst += hs
            for (set in g.sets) {
                if (set.size < 2) continue
                home.ballsFor += set[0]
                home.ballsAgainst += set[1]
                away.ballsFor += set[1]
                away.ballsAgainst += set[0]
            }
            when {
                hs > asets -> {
                    home.wins++
                    away.losses++
                    if (hs == 3 && asets == 2) {
                        home.points += 2
                        away.points += 1
                    } else {
                        home.points += 3
                    }
                }
                asets > hs -> {
                    away.wins++
                    home.losses++
                    if (asets == 3 && hs == 2) {
                        away.points += 2
                        home.points += 1
                    } else {
                        away.points += 3
                    }
                }
            }
        }
        rows.values.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.setsRatio }
                .thenByDescending { it.ballsRatio }
                .thenBy { it.team.name },
        )
    }
}

data class StandingRow(val team: Team) {
    var played: Int = 0
    var wins: Int = 0
    var losses: Int = 0
    var points: Int = 0
    var setsFor: Int = 0
    var setsAgainst: Int = 0
    var ballsFor: Int = 0
    var ballsAgainst: Int = 0

    val setsRatio: Double get() = if (setsAgainst == 0) setsFor.toDouble() else setsFor.toDouble() / setsAgainst
    val ballsRatio: Double get() = if (ballsAgainst == 0) ballsFor.toDouble() else ballsFor.toDouble() / ballsAgainst
    val setsText: String get() = "$setsFor:$setsAgainst"
    val ballsText: String get() = "$ballsFor:$ballsAgainst"
}
