package ru.example.pitervolley.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Разбор листа «Назначения».
 *
 * Одна строка листа описывает несколько игр: колонка «Игр» — сколько их,
 * «Возраст» — годы этих игр (для Чемпионата — 1-я и 2-я команды). Время в строке —
 * начало первой игры, каждая следующая сдвигается на 1 час 15 минут.
 * Строки с «!» в номере — отменённые или перенесённые, их пропускаем.
 * «Заложена» в дате — дата ещё не назначена.
 */
object SheetSlots {

    private const val STEP_MINUTES = 75L
    private const val CHAMPIONSHIP_STEP_MINUTES = 90L
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    fun parse(csv: String): List<Slot> {
        val rows = parseCsv(csv)
        if (rows.size < 2) return emptyList()
        val slots = ArrayList<Slot>()

        for (row in rows.drop(1)) {
            if (row.size < 9) continue
            val number = row.getOrElse(0) { "" }.trim()
            if (number.startsWith("!")) continue // отменено или перенесено

            val dateRaw = row.getOrElse(1) { "" }.trim()
            val noDate = dateRaw.lowercase().contains("залож")
            val date = if (noDate) "" else sheetDate(dateRaw) ?: continue

            val baseTime = parseTime(row.getOrElse(3) { "" })
            val group = row.getOrElse(4) { "" }.trim()
            val home = row.getOrElse(5) { "" }.trim()
            val away = row.getOrElse(7) { "" }.trim()
            val venue = normalizeVenue(row.getOrElse(8) { "" })
            val games = row.getOrElse(9) { "" }.trim().toDoubleOrNull()?.toInt()?.coerceIn(1, 8) ?: 1
            val refs = listOfNotNull(
                row.getOrElse(10) { "" }.trim().takeIf { it.isNotEmpty() },
                row.getOrElse(11) { "" }.trim().takeIf { it.isNotEmpty() },
            )
            val ages = row.getOrElse(12) { "" }
                .split(',', '.', '/')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1990..2030 }
            val comment = row.getOrElse(13) { "" }.trim()
            // муж/жен — Чемпионат: игры 1-й и 2-й команд с шагом 1:30
            val championship = isChampionshipGroup(group) || (ages.isEmpty() && games > 1)
            val step = if (championship) CHAMPIONSHIP_STEP_MINUTES else STEP_MINUTES
            val labels = championshipOrder(games, comment)

            for (i in 0 until games) {
                slots += Slot(
                    num = number.toDoubleOrNull()?.toInt(),
                    date = date,
                    time = if (baseTime == null) "" else baseTime.plusMinutes(step * i).format(TIME),
                    group = group,
                    home = home,
                    away = away,
                    venue = venue,
                    games = 1,
                    refs = refs,
                    ages = listOfNotNull(ages.getOrNull(i)),
                    comment = comment,
                    year = ages.getOrNull(i),
                    label = if (ages.isEmpty()) labels.getOrNull(i) else null,
                )
            }
        }
        return slots
    }

    private fun isChampionshipGroup(group: String): Boolean {
        val g = group.trim().lowercase()
        return g.startsWith("муж") || g.startsWith("жен")
    }

    /** «2 команда играет первой» меняет порядок игр в строке Чемпионата. */
    private fun championshipOrder(games: Int, comment: String): List<Int> =
        if (comment.contains("2 команд", ignoreCase = true)) {
            (2..maxOf(2, games)).toList() + listOf(1)
        } else {
            (1..maxOf(1, games)).toList()
        }

    private fun parseTime(raw: String): LocalTime? {
        val s = raw.trim().replace('.', ':')
        val m = Regex("""(\d{1,2}):(\d{2})""").find(s) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }

    private fun sheetDate(raw: String): String? {
        if (raw.isBlank()) return null
        Regex("""(\d{2})\.(\d{2})\.(\d{4})""").find(raw)?.let {
            return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}"
        }
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(raw)?.let {
            return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}"
        }
        return null
    }

    private fun normalizeVenue(raw: String): String {
        val s = raw.replace(Regex("\\s+"), " ").trim()
        if (s.trim('.', '…', '?', ' ') == "") return ""
        return VenueNormalizer.normalize(s)
    }

    /** Приставки клуба, которые в таблице и на сайте пишут по-разному. */
    private val CLUB_PREFIXES = listOf("гбоушиор", "гбоу", "гбу", "сшор", "сш", "дюсшор", "дюсш")

    fun normalizeName(raw: String): String {
        var s = raw.lowercase()
            .replace('ё', 'е')
            .replace('й', 'и')
            .replace(Regex("""[«»"'`.,]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        var changed = true
        while (changed) {
            changed = false
            for (prefix in CLUB_PREFIXES) {
                if (s.startsWith("$prefix ")) {
                    s = s.removePrefix("$prefix ").trim()
                    changed = true
                }
            }
        }
        return s
    }

    fun pairKey(a: String, b: String): String {
        val x = normalizeName(a)
        val y = normalizeName(b)
        return if (x <= y) "$x|$y" else "$y|$x"
    }

    /**
     * Применяет расписание к играм. Таблица главнее сайта: если для пары команд нашёлся
     * слот, его дата/время/зал заменяют данные сайта. Сыгранные матчи не трогаем.
     * Слоты одной пары раздаются играм по порядку дат, чтобы два круга не совпали.
     */
    fun apply(games: List<Game>, slots: List<Slot>, divisions: Map<Int, Division>): Int {
        if (slots.isEmpty()) return 0

        val slotGroups = HashMap<String, MutableList<Slot>>()
        for (slot in slots) {
            slotGroups.getOrPut(
                slotKey(slot.home, slot.away, genderOf(slot.group), slot.year, slot.label),
            ) { ArrayList() } += slot
        }
        for (group in slotGroups.values) {
            group.sortWith(compareBy({ it.date }, { it.time }, { it.num ?: 0 }))
        }

        val gameGroups = LinkedHashMap<String, MutableList<Game>>()
        for (game in games) {
            if (game.hasResult) continue
            val division = divisions[game.divisionId] ?: continue
            gameGroups.getOrPut(
                slotKey(game.home, game.away, division.gender, division.birthYear, division.teamLabel),
            ) { ArrayList() } += game
        }

        var applied = 0
        for ((key, group) in gameGroups) {
            val matching = slotGroups[key] ?: continue
            group.sortWith(compareBy({ it.date ?: "9999-12-31" }, { it.round ?: 0 }, { it.id }))
            for (i in 0 until minOf(group.size, matching.size)) {
                if (applySlot(group[i], matching[i])) applied++
            }
        }

        // одна игра пары без возраста/номера команды — берём единственный слот того же пола
        val singleByPair = slotGroups
            .filterKeys { it.endsWith("|*") }
            .values
            .flatten()
            .groupBy { pairKey(it.home, it.away) to genderOf(it.group) }
        for (game in games) {
            if (game.hasResult || game.dateSource == "sheet") continue
            val division = divisions[game.divisionId] ?: continue
            val candidates = singleByPair[pairKey(game.home, game.away) to division.gender].orEmpty()
            if (candidates.size == 1 && applySlot(game, candidates[0])) applied++
        }
        return applied
    }

    /** Группа из таблицы («дев», «юн», «муж», «жен») → пол дивизиона. */
    fun genderOf(group: String): String? {
        val g = group.trim().lowercase()
        return when {
            g.startsWith("дев") -> "girls"
            g.startsWith("юн") -> "boys"
            g.startsWith("муж") -> "men"
            g.startsWith("жен") -> "women"
            else -> null
        }
    }

    private fun slotKey(home: String, away: String, gender: String?, year: Int?, label: Int?): String {
        val pair = pairKey(home, away)
        val g = gender ?: "*"
        return when {
            year != null -> "$pair|$g|y=$year"
            label != null -> "$pair|$g|l=$label"
            else -> "$pair|$g|*"
        }
    }

    private fun applySlot(game: Game, slot: Slot): Boolean {
        if (slot.date.isBlank()) {
            // «заложена»: дату ещё не назначили — убираем дату сайта
            val hadDate = game.date != null
            game.date = null
            game.time = ""
            game.slotNum = slot.num
            game.dateSource = "sheet"
            game.refs = slot.refs.filter { it.isNotBlank() }
            game.comment = slot.comment
            return hadDate
        }
        game.date = slot.date
        if (slot.time.isNotBlank()) game.time = slot.time
        if (slot.venue.isNotBlank()) game.venue = slot.venue
        game.slotNum = slot.num
        game.dateSource = "sheet"
        game.refs = slot.refs.filter { it.isNotBlank() }
        game.comment = slot.comment
        return true
    }
    /** Минимальный RFC4180 reader. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"'); i++
                    }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    row.add(field.toString()); field.clear()
                }
                ch == '\r' -> Unit
                ch == '\n' -> {
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = ArrayList()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
