package ru.example.pitervolley.data

/**
 * Приводит адреса залов к единому виду, чтобы «пр. Энгельса д.23» и
 * «пр. Энгельса, д. 23» считались одним залом.
 */
object VenueNormalizer {

    private val spaces = Regex("\\s+")

    fun normalize(raw: String): String {
        var s = raw.replace('\u00A0', ' ').replace(spaces, " ").trim()
        if (s.trim('.', '…', '?', ' ') == "") return ""

        // в Java-регексе \b не считает кириллицу словом, поэтому используем (^|\s)
        s = s.replace(Regex("(?i)(^|\\s)просп(ект)?\\.?\\s*"), "$1пр. ")
        s = s.replace(Regex("(?i)улица"), "ул.")
        s = s.replace(Regex("(?i)(^|\\s)ул\\.?\\s*"), "$1ул. ")
        s = s.replace(Regex("(?i)(^|\\s)дом\\.?\\s*(\\d)"), "$1д.$2")
        s = s.replace(Regex("(?i)(^|\\s)д\\.?\\s*(\\d)"), "$1д.$2")
        s = s.replace(
            Regex("(?i)(?<![А-Яа-яA-Za-z])В\\.?\\s*О\\.?(?![А-Яа-яA-Za-z])"),
            "В.О.",
        )
        s = s.replace(spaces, " ").trim()

        // единый вид «<улица>, д.<номер>»
        s = s.replace(Regex("(?i)\\s+д\\."), ", д.")
        s = s.replace(Regex("(\\s*,\\s*)+"), ", ")

        // «д.50 Б» -> «д.50Б»
        s = s.replace(Regex("(д\\.\\d+)\\s+(\\p{Lu})(?![а-яa-z])"), "$1$2")
        s = s.replace(Regex("(д\\.)\\s+(\\d)"), "$1$2")

        return s.trim().trimEnd(',')
    }

    private val NOTE = Regex("""\([^)]*\)""")
    private val TYPE_WORDS = setOf(
        "ул", "пр", "пер", "б-р", "ш", "наб", "пл",
        "улица", "проспект", "проезд", "бульвар", "шоссе", "набережная", "площадь",
    )

    /** Известные варианты одного зала, которые не сходятся автоматически. */
    private val ALIASES = mapOf(
        "ветеранов д 133" to "пр. Ветеранов, 133, к.1",
        "ветеранов 133 к 1" to "пр. Ветеранов, 133, к.1",
    )

    fun groupKey(raw: String): String {
        val withoutNotes = key(raw).replace(NOTE, " ")
        return withoutNotes.split(' ')
            .filter { it.isNotBlank() && it !in TYPE_WORDS }
            .joinToString(" ")
    }

    /** Один зал — одно название: выбираем самый частый вариант записи. */
    fun canonicalize(games: List<Game>): Int {
        val variants = HashMap<String, MutableMap<String, Int>>()
        for (game in games) {
            val venue = game.venue
            if (venue.isBlank()) continue
            val bucket = variants.getOrPut(groupKey(venue)) { HashMap() }
            bucket[venue] = (bucket[venue] ?: 0) + 1
        }
        val best = variants.mapValues { (_, bucket) ->
            bucket.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenByDescending { it.key.length }
                        .thenBy { it.key },
                )
                .first().key
        }.toMutableMap()
        ALIASES.forEach { (key, value) -> best[key] = value }

        val names = HashSet<String>()
        for (game in games) {
            val venue = game.venue
            if (venue.isBlank()) continue
            val canonical = best[groupKey(venue)] ?: venue
            game.venue = canonical
            names.add(canonical)
        }
        return names.size
    }

    /** Ключ для группировки: без учёта регистра и знаков препинания. */
    fun key(raw: String): String =
        normalize(raw)
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[.,«»"'`]"""), "")
            .replace(spaces, " ")
            .trim()
}
