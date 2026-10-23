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

    /** Ключ для группировки: без учёта регистра и знаков препинания. */
    fun key(raw: String): String =
        normalize(raw)
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[.,«»"'`]"""), "")
            .replace(spaces, " ")
            .trim()
}
