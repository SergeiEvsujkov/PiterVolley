package ru.example.pitervolley.data

/** Русские окончания после чисел: 1 игра, 2 игры, 5 игр. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val abs = if (n < 0) -n else n
    val lastTwo = abs % 100
    val last = abs % 10
    return when {
        lastTwo in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
}

fun countGames(n: Int): String = "$n ${plural(n, "игра", "игры", "игр")}"

fun countTeams(n: Int): String = "$n ${plural(n, "команда", "команды", "команд")}"

fun countPlayers(n: Int): String = "$n ${plural(n, "игрок", "игрока", "игроков")}"

fun countDivisions(n: Int): String = "$n ${plural(n, "дивизион", "дивизиона", "дивизионов")}"

fun countVenues(n: Int): String = "$n ${plural(n, "зал", "зала", "залов")}"

fun countAddresses(n: Int): String = "$n ${plural(n, "адрес", "адреса", "адресов")}"

fun countSplits(n: Int): String = "$n ${plural(n, "разделение", "разделения", "разделений")}"

fun countMerges(n: Int): String = "$n ${plural(n, "объединение", "объединения", "объединений")}"
