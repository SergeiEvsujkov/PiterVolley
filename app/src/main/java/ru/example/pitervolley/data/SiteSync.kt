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
    private const val UA = "PiterVolleyApp/0.1 (+android)"

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

            for (tid in 1..DIVISIONS) {
                val html = get("$BASE/table?tid=$tid")
                val parsed = parseDivision(tid, html)
                divisions += parsed.division
                games += parsed.games
                for (team in parsed.teams) teamIds[team.id] = team
                delay(120)
            }

            val slots = runCatching { parseSheet(get(sheetCsvUrl())) }.getOrElse { emptyList() }
            fillDatesFromSlots(games, slots)

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
                .edit().putLong(KEY_LAST, now).apply()

            games.size
        }
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
        return s.replace(Regex("""\s*,\s*"""), ", ")
            .replace(Regex("""д\.\s*(\d)"""), "д.$1")
    }

    // ------------------------------------------------------------------ sheet

    private fun parseSheet(csv: String): List<Slot> {
        val rows = parseCsv(csv)
        if (rows.size < 2) return emptyList()
        val slots = ArrayList<Slot>()
        for (row in rows.drop(1)) {
            if (row.size < 9) continue
            val date = sheetDate(row.getOrElse(1) { "" }) ?: continue
            val time = row.getOrElse(3) { "" }.take(5)
            val ages = row.getOrElse(12) { "" }
                .split(',', '.')
                .mapNotNull { it.trim().toIntOrNull() }
            slots += Slot(
                num = row.getOrElse(0) { "" }.toDoubleOrNull()?.toInt(),
                date = date,
                time = time,
                group = row.getOrElse(4) { "" }.trim(),
                home = row.getOrElse(5) { "" }.trim(),
                away = row.getOrElse(7) { "" }.trim(),
                venue = normVenue(row.getOrElse(8) { "" }),
                ages = ages,
            )
        }
        return slots
    }

    private fun sheetDate(raw: String): String? {
        val s = raw.trim()
        Regex("""(\d{2})\.(\d{2})\.(\d{4})""").find(s)?.let {
            return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}"
        }
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(s)?.let {
            return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}"
        }
        return null
    }

    /** Minimal RFC4180 reader: handles quoted fields and embedded commas/newlines. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    row.add(field.toString()); field.clear()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    private fun normalizeName(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[«»"'`.,]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Fills games that have no date yet using the federation's schedule slots. */
    private fun fillDatesFromSlots(games: List<Game>, slots: List<Slot>) {
        if (slots.isEmpty()) return
        val index = HashMap<String, Slot>()
        for (slot in slots) {
            index[pairKey(slot.home, slot.away)] = slot
        }
        for (game in games) {
            if (!game.date.isNullOrBlank()) continue
            val slot = index[pairKey(game.home, game.away)] ?: continue
            game.date = slot.date
            game.time = slot.time
            if (game.venue.isBlank()) game.venue = slot.venue
            game.slotNum = slot.num
            game.dateSource = "sheet"
        }
    }

    private fun pairKey(a: String, b: String): String {
        val x = normalizeName(a)
        val y = normalizeName(b)
        return if (x <= y) "$x|$y" else "$y|$x"
    }
}
