package ru.example.pitervolley

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.pitervolley.data.Division
import ru.example.pitervolley.data.Game
import ru.example.pitervolley.data.SheetSlots

class SheetSlotsTest {

    private val header = "№,Дата,День,Время,Группа,Команды,,,Адрес,Игр,1 Судья,2 Судья,Возраст,Комментарии"

    @Test
    fun `row expands into several games with staggered time`() {
        val csv = header + "\n" +
            "24,07.10.2026,ср,16:00,дев,Ижорец,-,Московская,\"Колпино, ул. Тверская, д.25\",2,Коляда,Реутов,\"2013,2010\","
        val slots = SheetSlots.parse(csv)

        assertEquals(2, slots.size)
        assertEquals("2026-10-07", slots[0].date)
        assertEquals("16:00", slots[0].time)
        assertEquals(2013, slots[0].year)
        assertEquals("17:15", slots[1].time)
        assertEquals(2010, slots[1].year)
        assertEquals(24, slots[0].num)
    }

    @Test
    fun `cancelled rows are skipped`() {
        val csv = header + "\n" +
            "!24,07.10.2026,ср,16:00,дев,Ижорец,-,Московская,Зал,1,Коляда,Реутов,2013,"
        assertTrue(SheetSlots.parse(csv).isEmpty())
    }

    @Test
    fun `zalozena means no date and clears site date`() {
        val csv = header + "\n" +
            "30,заложена,,16:00,дев,Ижорец,-,Московская,Зал,1,Коляда,Реутов,2013,"
        val slots = SheetSlots.parse(csv)
        assertEquals(1, slots.size)
        assertEquals("", slots[0].date)

        val division = Division(1, "Первенство", "Первенство СПб", "boys", 2013, null)
        val game = Game(
            id = "g1",
            divisionId = 1,
            date = "2026-01-01",
            time = "10:00",
            home = "Ижорец",
            away = "Московская",
        )
        val applied = SheetSlots.apply(listOf(game), slots, mapOf(1 to division))
        assertEquals(1, applied)
        assertNull(game.date)
    }

    @Test
    fun `championship row order follows comment`() {
        val csv = header + "\n" +
            "401,23.10.2026,пт,19:00,муж,ЧМ-Политех,-,Динамо-ЛО,пр. Энгельса д.23,2,Дарбинян,Дмитриев,,\"2 команда играет первой\""
        val slots = SheetSlots.parse(csv)

        assertEquals(2, slots.size)
        assertEquals(2, slots[0].label)
        assertEquals("19:00", slots[0].time)
        assertEquals(1, slots[1].label)
        assertEquals("20:30", slots[1].time)
    }

    @Test
    fun `championship step is ninety minutes and first team plays first by default`() {
        val csv = header + "\n" +
            "402,24.10.2026,сб,18:00,жен,Клуб,-,Соперник,Зал,2,Судья,Судья,,"
        val slots = SheetSlots.parse(csv)

        assertEquals(2, slots.size)
        assertEquals(1, slots[0].label)
        assertEquals("18:00", slots[0].time)
        assertEquals(2, slots[1].label)
        assertEquals("19:30", slots[1].time)
    }

    @Test
    fun `club prefixes do not break matching`() {
        assertEquals(
            SheetSlots.pairKey("Экран", "Клуб"),
            SheetSlots.pairKey("СШ ЭКРАН", "ГБУ Клуб"),
        )
        assertEquals(
            SheetSlots.pairKey("Приморская-1", "Невская"),
            SheetSlots.pairKey("СШОР Приморская-1", "Невская"),
        )
    }

    @Test
    fun `two legs of the same pair get different dates`() {
        val csv = header + "\n" +
            "10,07.10.2026,ср,16:00,дев,Ижорец,-,Московская,Зал,1,Коляда,Реутов,2013,\n" +
            "20,20.11.2026,пт,18:00,дев,Ижорец,-,Московская,Зал,1,Коляда,Реутов,2013,"
        val slots = SheetSlots.parse(csv)
        val division = Division(1, "Первенство", "Первенство СПб", "boys", 2013, null)

        val first = Game(id = "g1", divisionId = 1, round = 1, home = "Ижорец", away = "Московская")
        val second = Game(id = "g2", divisionId = 1, round = 2, home = "Московская", away = "Ижорец")
        SheetSlots.apply(listOf(first, second), slots, mapOf(1 to division))

        assertEquals("2026-10-07", first.date)
        assertEquals("2026-11-20", second.date)
    }

    @Test
    fun `sheet overrides site date but not played games`() {
        val csv = header + "\n" +
            "24,07.10.2026,ср,16:00,дев,Ижорец,-,Московская,Зал,1,Коляда,Реутов,2013,"
        val slots = SheetSlots.parse(csv)
        val division = Division(1, "Первенство", "Первенство СПб", "boys", 2013, null)

        val fresh = Game(
            id = "g1",
            divisionId = 1,
            date = "2026-01-01",
            time = "10:00",
            home = "Ижорец",
            away = "Московская",
        )
        val played = Game(
            id = "g2",
            divisionId = 1,
            date = "2026-09-01",
            time = "10:00",
            home = "Ижорец",
            away = "Московская",
            score = "3:0",
        )
        SheetSlots.apply(listOf(fresh, played), slots, mapOf(1 to division))

        assertEquals("2026-10-07", fresh.date)
        assertEquals("16:00", fresh.time)
        assertEquals("sheet", fresh.dateSource)
        assertEquals("2026-09-01", played.date)
    }
}
