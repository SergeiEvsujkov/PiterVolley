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
    private lateinit var playersByPerson: Map<String, List<Player>>
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
        playersByPerson = ds.players.groupBy { effectivePersonKey(it) }
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

    // ---------------------------------------------------------------- players as persons

    /**
     * Один человек может быть заявлен за несколько команд (разные дивизионы/возрасты).
     * Ключ человека — нормализованное ФИО + год рождения.
     */
    fun personKey(name: String, birthYear: Int?): String {
        val tokens = name.lowercase()
            .replace('ё', 'е')
            .replace(".", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(" ")
        return "$tokens|${birthYear ?: 0}"
    }

    fun personKeyOf(player: Player): String = effectivePersonKey(player)

    /** Ключ с учётом ручных правок: разделение записей и склейка разных написаний. */
    fun effectivePersonKey(player: Player): String {
        if (PersonOverrides.isSplit(player.id)) return "id:${player.id}"
        return PersonOverrides.resolve(personKey(player.name, player.birthYear))
    }

    fun rebuildPersons() {
        if (!isLoaded) return
        playersByPerson = data.players.groupBy { effectivePersonKey(it) }
        revision.value = revision.value + 1
    }

    /** Сколько уникальных людей (а не записей в командах). */
    fun personCount(): Int = if (isLoaded) playersByPerson.size else 0

    /**
     * Приводит сохранённое значение к ключу человека. Поддерживает старые записи,
     * где хранился id игрока.
     */
    fun resolvePersonKey(token: String): String {
        data.players.firstOrNull { it.id == token }?.let { return effectivePersonKey(it) }
        return if (token.startsWith("id:")) token else PersonOverrides.resolve(token)
    }

    fun playersOfPerson(key: String): List<Player> = playersByPerson[key].orEmpty()

    fun teamsOfPerson(key: String): List<Team> =
        playersOfPerson(key).mapNotNull { team(it.teamId) }.distinctBy { it.id }

    fun person(key: String): Person? {
        val players = playersOfPerson(key)
        if (players.isEmpty()) return null
        val first = players.first()
        return Person(key, first.name, first.birthYear, players, teamsOfPerson(key))
    }

    fun baseKeyOf(player: Player): String = personKey(player.name, player.birthYear)

    /** Есть ли у человека ручные правки группировки (разделение или объединение). */
    fun hasManualEdits(personKey: String, baseKey: String): Boolean {
        if (!isLoaded) return false
        if (personKey.startsWith("id:") || personKey != baseKey) return true
        val ids = data.players.filter { baseKeyOf(it) == baseKey }.map { it.id }
        return ids.any { PersonOverrides.isSplit(it) }
    }

    /** Сбрасывает ручные правки всех записей с этим базовым ключом. */
    fun resetManualEdits(baseKey: String) {
        if (!isLoaded) return
        val ids = data.players.filter { baseKeyOf(it) == baseKey }.map { it.id }
        PersonOverrides.resetFor(baseKey, ids)
        rebuildPersons()
    }

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
        val ranked = rows.values.sortedWith(RANK_ORDER)
        orderWithHeadToHead(ranked, gamesOf(divisionId))
    }

    /**
     * Порядок мест по п. 6.5.2 Первенства СПб: победы → очки → партии → мячи,
     * при полном равенстве — личные встречи.
     */
    private val RANK_ORDER = Comparator<StandingRow> { a, b ->
        var c = b.wins.compareTo(a.wins)
        if (c != 0) return@Comparator c
        c = b.points.compareTo(a.points)
        if (c != 0) return@Comparator c
        c = ratioCompare(a.setsFor, a.setsAgainst, b.setsFor, b.setsAgainst)
        if (c != 0) return@Comparator -c
        c = ratioCompare(a.ballsFor, a.ballsAgainst, b.ballsFor, b.ballsAgainst)
        if (c != 0) return@Comparator -c
        0
    }

    private fun orderWithHeadToHead(ranked: List<StandingRow>, games: List<Game>): List<StandingRow> {
        val result = ArrayList<StandingRow>(ranked.size)
        var i = 0
        while (i < ranked.size) {
            var j = i + 1
            while (j < ranked.size && RANK_ORDER.compare(ranked[i], ranked[j]) == 0) j++
            val group = ranked.subList(i, j)
            if (group.size == 1) result += group[0] else result += sortByHeadToHead(group, games)
            i = j
        }
        return result
    }

    /** Очки, набранные командами в матчах только между собой. */
    private fun sortByHeadToHead(group: List<StandingRow>, games: List<Game>): List<StandingRow> {
        val ids = group.map { it.team.id }.toSet()
        val mini = HashMap<String, Int>()
        for (g in games) {
            if (!g.hasResult || g.sets.isEmpty()) continue
            if (g.homeId !in ids || g.awayId !in ids) continue
            val hs = g.homeSets
            val asets = g.awaySets
            when {
                hs > asets -> if (hs == 3 && asets == 2) {
                    mini[g.homeId] = (mini[g.homeId] ?: 0) + 2
                    mini[g.awayId] = (mini[g.awayId] ?: 0) + 1
                } else {
                    mini[g.homeId] = (mini[g.homeId] ?: 0) + 3
                }
                asets > hs -> if (asets == 3 && hs == 2) {
                    mini[g.awayId] = (mini[g.awayId] ?: 0) + 2
                    mini[g.homeId] = (mini[g.homeId] ?: 0) + 1
                } else {
                    mini[g.awayId] = (mini[g.awayId] ?: 0) + 3
                }
            }
        }
        return group.sortedWith(
            compareByDescending<StandingRow> { mini[it.team.id] ?: 0 }
                .thenByDescending { it.wins }
                .thenBy { it.team.name },
        )
    }

    /** Сравнение дробей без потери точности; x/0 при x > 0 — бесконечность, 0/0 — ноль. */
    private fun ratioCompare(aNum: Int, aDen: Int, bNum: Int, bDen: Int): Int {
        // 0:0 (игр не было) считаем как 0, а не как «ничья» с любым отношением
        val aEmpty = aNum == 0 && aDen == 0
        val bEmpty = bNum == 0 && bDen == 0
        if (aEmpty && bEmpty) return 0
        if (aEmpty) return if (bNum == 0) 0 else -1
        if (bEmpty) return if (aNum == 0) 0 else 1

        val aInf = aDen == 0 && aNum > 0
        val bInf = bDen == 0 && bNum > 0
        if (aInf || bInf) return when {
            aInf && bInf -> 0
            aInf -> 1
            else -> -1
        }
        val left = aNum.toLong() * bDen
        val right = bNum.toLong() * aDen
        return left.compareTo(right)
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

/** Один человек: может быть заявлен за несколько команд разных дивизионов. */
data class Person(
    val key: String,
    val name: String,
    val birthYear: Int?,
    val players: List<Player>,
    val teams: List<Team>,
) {
    val teamNames: String get() = teams.joinToString(", ") { it.name }

    /** У одного человека в разных командах могут быть разные фото — берём первое доступное. */
    val photo: String? get() = players.firstNotNullOfOrNull { it.photo }
}
