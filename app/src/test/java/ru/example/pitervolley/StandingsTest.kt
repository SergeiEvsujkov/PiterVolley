package ru.example.pitervolley

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.example.pitervolley.data.Dataset
import ru.example.pitervolley.data.Division
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.Repo
import ru.example.pitervolley.data.Team

/** Проверки расчёта таблиц по регламентам Первенства и Чемпионата. */
class StandingsTest {

    private fun division(
        id: Int,
        gender: String = "boys",
        year: Int? = 2010,
        label: Int? = null,
        competition: String = "Первенство СПб",
    ) = Division(id, "$competition / ...", competition, gender, year, label)

    private fun team(id: String, name: String, divisionId: Int) = Team(id, name, divisionId)

    private fun game(
        id: String,
        divisionId: Int,
        home: Team,
        away: Team,
        sets: List<List<Int>>,
    ): Game {
        val homeSets = sets.count { it[0] > it[1] }
        val awaySets = sets.count { it[1] > it[0] }
        return Game(
            id = id,
            divisionId = divisionId,
            round = 1,
            date = "2026-10-01",
            time = "10:00",
            venue = "Зал",
            homeId = home.id,
            awayId = away.id,
            home = home.name,
            away = away.name,
            score = "$homeSets:$awaySets",
            sets = sets,
        )
    }

    private fun win31() = listOf(listOf(25, 20), listOf(20, 25), listOf(25, 18), listOf(25, 19))
    private fun win30() = listOf(listOf(25, 20), listOf(25, 20), listOf(25, 20))
    private fun win32() = listOf(listOf(25, 20), listOf(20, 25), listOf(25, 18), listOf(20, 25), listOf(15, 13))
    private fun lose32() = win32().reversed().map { listOf(it[1], it[0]) }

    private fun apply(divisions: List<Division>, teams: List<Team>, games: List<Game>) {
        Repo.apply(Dataset(1, "", "2026-27", divisions, teams, emptyList(), games, emptyList()))
    }

    @Test
    fun `premier scoring 3 2 1 0`() {
        val d = division(1)
        val a = team("a", "A", 1)
        val b = team("b", "B", 1)
        val c = team("c", "C", 1)
        apply(
            listOf(d), listOf(a, b, c),
            listOf(
                game("1", 1, a, b, win31()),   // A 3 очка
                game("2", 1, c, b, win32()),   // C 2, B 1
            ),
        )
        val table = Repo.standings(1).associateBy { it.team.name }
        assertEquals(3, table["A"]!!.points)
        assertEquals(2, table["C"]!!.points)
        assertEquals(1, table["B"]!!.points)
        assertEquals(0, table["B"]!!.wins)
    }

    @Test
    fun `premier ranks by wins before points`() {
        val d = division(1)
        val a = team("a", "A", 1)
        val b = team("b", "B", 1)
        val c = team("c", "C", 1)
        val e = team("e", "E", 1)
        val f = team("f", "F", 1)
        apply(
            listOf(d), listOf(a, b, c, e, f),
            listOf(
                game("1", 1, a, b, win32()),  // A 2, B 1
                game("2", 1, a, c, win32()),  // A 2, C 1
                game("3", 1, b, e, win32()),  // B 2, E 1
                game("4", 1, c, b, win32()),  // C 2, B 1
                game("5", 1, f, b, win32()),  // F 2, B 1
            ),
        )
        // A: 2 победы, 4 очка; B: 1 победа, 5 очков — при сортировке «сначала очки» B был бы выше
        val table = Repo.standings(1)
        assertEquals("A", table[0].team.name)
        assertEquals("B", table[1].team.name)
        assertEquals(2, table[0].wins)
        assertEquals(1, table.first { it.team.name == "B" }.wins)
        assertEquals(5, table.first { it.team.name == "B" }.points)
    }

    @Test
    fun `team without games goes below played teams`() {
        val d = division(1)
        val a = team("a", "A", 1)
        val b = team("b", "B", 1)
        val c = team("c", "C", 1)
        apply(
            listOf(d), listOf(a, b, c),
            listOf(game("1", 1, a, c, win30())),
        )
        val table = Repo.standings(1)
        assertEquals(listOf("A", "C", "B"), table.map { it.team.name })
    }

    @Test
    fun `championship first team gets six points for 3-0`() {
        val d = division(15, gender = "men", year = null, label = 1, competition = "Чемпионат СПб")
        val a = team("a", "Клуб", 15)
        val b = team("b", "Соперник", 15)
        apply(listOf(d), listOf(a, b), listOf(game("1", 15, a, b, win30())))
        val table = Repo.standings(15).associateBy { it.team.name }
        assertEquals(6, table["Клуб"]!!.points)
        assertEquals(0, table["Соперник"]!!.points)
    }

    @Test
    fun `championship second team gets three points and combined table sums both`() {
        val d1 = division(15, gender = "men", year = null, label = 1, competition = "Чемпионат СПб")
        val d2 = division(16, gender = "men", year = null, label = 2, competition = "Чемпионат СПб")
        val first = team("a1", "Клуб", 15)
        val rival1 = team("b1", "Соперник", 15)
        val second = team("a2", "Клуб", 16)
        val rival2 = team("b2", "Соперник", 16)
        apply(
            listOf(d1, d2), listOf(first, rival1, second, rival2),
            listOf(
                game("1", 15, first, rival1, win30()),   // первая: 6 очков
                game("2", 16, second, rival2, win30()),  // вторая: 3 очка
            ),
        )
        assertEquals(6, Repo.standings(15).first { it.team.name == "Клуб" }.points)
        val secondPoints = Repo.standings(16).first { it.team.name == "Клуб" }.points
        assertEquals(3, secondPoints)

        val combined = Repo.combinedStandings(15)
        val club = combined.first { it.name == "Клуб" }
        assertEquals(9, club.points)
        assertEquals(2, club.wins)
    }
}
