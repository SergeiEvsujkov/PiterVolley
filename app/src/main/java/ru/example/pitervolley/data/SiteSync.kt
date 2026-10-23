package ru.example.pitervolley.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fetches games/results straight from pitervolley.ru and the federation's
 * Google Sheet, rebuilds the dataset on the device and caches it.
 *
 * Automatic refresh is throttled by [MIN_INTERVAL_MS]; the manual button in the
 * UI calls [refresh] with force = true.
 */
object SiteSync {

    private const val BASE = "https://pitervolley.ru"
    private const val SHEET_ID = "1qVhAPMFcWAFylnoy2529uQzTXeBs6lOvfvpWbH2x6u4"

    private const val PREFS = "sync"
    private const val KEY_LAST = "last_sync"
    private const val KEY_SITE_OK = "last_site_ok"
    private const val KEY_SHEET_OK = "last_sheet_ok"
    private const val KEY_FAILED = "last_failed"
    private const val UA = "VolleyPiter/0.1 (+android)"

    const val MIN_INTERVAL_MS = 30 * 60 * 1000L
    const val DIVISIONS = 18

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun lastSync(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    fun lastSyncText(context: Context): String {
        val ts = lastSync(context)
        if (ts == 0L) return "данные из приложения"
        val fmt = SimpleDateFormat("dd.MM в HH:mm", java.util.Locale.forLanguageTag("ru-RU"))
        return "обновлено " + fmt.format(Date(ts))
    }

    /** "23.10.2026 в 01:53" или null, если данные ещё ни разу не обновлялись. */
    fun lastSyncDateText(context: Context): String? {
        val ts = lastSync(context)
        if (ts == 0L) return null
        return SimpleDateFormat("dd.MM.yyyy в HH:mm", java.util.Locale.forLanguageTag("ru-RU")).format(Date(ts))
    }

    suspend fun refresh(context: Context, force: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            if (!force && Repo.isLoaded && now - lastSync(context) in 0 until MIN_INTERVAL_MS) {
                return@runCatching 0
            }

            val previous = if (Repo.isLoaded) Repo.data else null
            val divisions = ArrayList<Division>(DIVISIONS)
            val teamIds = LinkedHashMap<String, Team>()
            val games = ArrayList<Game>()

            var siteOk = true
            try {
                for (tid in 1..DIVISIONS) {
                    val html = get("$BASE/table?tid=$tid")
                    val parsed = parseDivision(tid, html)
                    divisions += parsed.division
                    games += parsed.games
                    for (team in parsed.teams) teamIds[team.id] = team
                    delay(120)
                }
            } catch (e: Exception) {
                // сайт недоступен: остаёмся на прежних данных, но расписание из таблицы применим
                if (previous == null) throw e
                siteOk = false
                divisions.clear()
                divisions.addAll(previous.divisions)
                games.clear()
                games.addAll(previous.games.map { it.copy() })
                teamIds.clear()
                previous.teams.forEach { teamIds[it.id] = it }
            }

            val slots = runCatching { SheetSlots.parse(get(sheetCsvUrl())) }.getOrElse { emptyList() }
            val sheetOk = slots.isNotEmpty()
            if (!siteOk && !sheetOk) throw IllegalStateException("Нет данных ни с сайта, ни из таблицы")

            SheetSlots.apply(games, slots, divisions.associateBy { it.id })

            val knownLogos = previous?.teams?.associate { it.id to it.logo }.orEmpty()
            val teams = teamIds.values.map { it.copy(logo = knownLogos[it.id] ?: it.logo) }

            val players = previous?.players?.filter { p -> teamIds.containsKey(p.teamId) }.orEmpty()

            val dataset = Dataset(
                schema = 1,
                generatedAt = java.time.Instant.now().toString(),
                season = previous?.season ?: "2026-27",
                divisions = divisions,
                teams = teams,
                players = players,
                games = games,
                slots = slots,
            )

            val text = json.encodeToString(Dataset.serializer(), dataset)
            val tmp = File(context.filesDir, "data.json.tmp")
            tmp.writeText(text)
            val target = File(context.filesDir, "data.json")
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }

            Repo.apply(dataset)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST, now)
                .putBoolean(KEY_SITE_OK, siteOk)
                .putBoolean(KEY_SHEET_OK, sheetOk)
                .putBoolean(KEY_FAILED, false)
                .apply()

            games.size
        }.also { result ->
            if (result.isFailure) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_FAILED, true).apply()
            }
        }
    }

    /** Откуда удалось обновить данные в последний раз. */
    enum class Source { BOTH, SITE_ONLY, SHEET_ONLY, FAILED, NONE }

    fun lastSource(context: Context): Source {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FAILED, false)) return Source.FAILED
        val site = prefs.getBoolean(KEY_SITE_OK, false)
        val sheet = prefs.getBoolean(KEY_SHEET_OK, false)
        return when {
            site && sheet -> Source.BOTH
            site -> Source.SITE_ONLY
            sheet -> Source.SHEET_ONLY
            else -> Source.NONE
        }
    }

    fun lastSourceText(context: Context): String? = when (lastSource(context)) {
        Source.BOTH -> "Данные: pitervolley.ru + volleyreferee.spb.ru"
        Source.SITE_ONLY -> "Данные: только pitervolley.ru — расписание недоступно"
        Source.SHEET_ONLY -> "Данные: только volleyreferee.spb.ru — сайт недоступен"
        Source.FAILED -> "Данные: обновление не удалось"
        Source.NONE -> null
    }

    private fun sheetCsvUrl(): String =
        "https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=0"

    // ------------------------------------------------------------------ http

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 25_000
        conn.setRequestProperty("User-Agent", UA)
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode} for $url")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ html parsing

    private val H1 = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
    private val GAME_ROW = Regex("""<tr id="game-line-(\d+)"[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
    private val DATE_CELL = Regex("""class="cell-date[^"]*"[^>]*>([^<]*)<""")
    private val TIME_CELL = Regex("""class="cell-time[^"]*"[^>]*>([^<]*)<""")
    private val ADDR_CELL = Regex("""class="cell-addr[^"]*"[^>]*>([^<]*)<""")
    private val ROUND_CELL = Regex("""class="cell-round"[^>]*>([^<]*)<""")
    private val TEAM_LINK = Regex("""<td class="cell-team[^"]*"><a href="team\?team=(\d+)"[^>]*>([^<]+)</a>""")
    private val ANY_TEAM_LINK = Regex("""href="team\?team=(\d+)"[^>]*>([^<]+)</a>""")
    private val SCORE = Regex("""<div class="gmscore">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val BALLS = Regex("""<div class="gmballs">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val SETS = Regex("""(\d{1,2})\s*:\s*(\d{1,2})""")

    private data class ParsedDivision(
        val division: Division,
        val games: List<Game>,
        val teams: List<Team>,
    )

    private fun parseDivision(tid: Int, html: String): ParsedDivision {
        val title = H1.find(html)?.groupValues?.get(1)?.let(::stripTags) ?: ""
        val parts = title.split("/").map { it.trim() }
        val competition = parts.getOrElse(0) { "" }
        val genderRaw = parts.getOrElse(1) { "" }.lowercase()
        val tail = parts.getOrElse(2) { "" }

        val gender = when {
            genderRaw.startsWith("юн") -> "boys"
            genderRaw.startsWith("дев") -> "girls"
            genderRaw.startsWith("муж") -> "men"
            genderRaw.startsWith("жен") -> "women"
            else -> "unknown"
        }
        val birthYear = Regex("""(\d{4})""").find(tail)?.groupValues?.get(1)?.toIntOrNull()
        val teamLabel = Regex("""(\d+)-я команда""").find(tail)?.groupValues?.get(1)?.toIntOrNull()

        val games = ArrayList<Game>()
        for (match in GAME_ROW.findAll(html)) {
            val id = match.groupValues[1]
            val body = match.groupValues[2]
            val teams = TEAM_LINK.findAll(body).toList()
            if (teams.size < 2) continue
            val homeId = teams[0].groupValues[1]
            val homeName = stripTags(teams[0].groupValues[2])
            val awayId = teams[1].groupValues[1]
            val awayName = stripTags(teams[1].groupValues[2])

            val sets = BALLS.find(body)?.groupValues?.get(1)?.let { raw ->
                SETS.findAll(stripTags(raw)).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
                    .map { listOf(it.first, it.second) }
                    .toList()
            } ?: emptyList()

            games += Game(
                id = id,
                divisionId = tid,
                round = ROUND_CELL.find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull(),
                date = isoDate(stripTags(DATE_CELL.find(body)?.groupValues?.get(1) ?: "")),
                time = stripTags(TIME_CELL.find(body)?.groupValues?.get(1) ?: ""),
                venue = normVenue(stripTags(ADDR_CELL.find(body)?.groupValues?.get(1) ?: "")),
                homeId = homeId,
                awayId = awayId,
                home = homeName,
                away = awayName,
                score = stripTags(SCORE.find(body)?.groupValues?.get(1) ?: ""),
                sets = sets,
            )
        }

        val teams = TEAM_LINK.findAll(html).map { m ->
            Team(id = m.groupValues[1], name = stripTags(m.groupValues[2]), divisionId = tid)
        }.filter { it.name.isNotBlank() }.distinctBy { it.id }.toList()

        return ParsedDivision(
            Division(
                id = tid,
                title = title,
                competition = competition,
                gender = gender,
                birthYear = birthYear,
                teamLabel = teamLabel,
            ),
            games,
            teams,
        )
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isoDate(s: String): String? {
        val m = Regex("""(\d{2})\.(\d{2})\.(\d{4})""").find(s) ?: return null
        return "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}"
    }

    private fun normVenue(raw: String): String {
        val s = raw.replace(Regex("\\s+"), " ").trim()
        if (s.trim('.', '…', '?', ' ') == "") return ""
        return VenueNormalizer.normalize(s)
    }
}
