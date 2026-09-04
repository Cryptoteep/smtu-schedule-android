package com.korabel.schedule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Date arithmetic: epoch days, weekdays, Russian formatting. */
public class DatesTest {

    @Test public void matchesTheGregorianCalendar() {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2020, Calendar.JANUARY, 1);
        for (int i = 0; i < 4000; i++) {                       // 2020 through 2030
            long expected = c.getTimeInMillis() / 86400000L;
            long actual = Dates.toEpochDay(c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            assertEquals(c.getTime().toString(), expected, actual);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    @Test public void roundTripsThroughEpochDays() {
        for (long d = Dates.toEpochDay(2000, 1, 1); d <= Dates.toEpochDay(2040, 1, 1); d++) {
            int[] ymd = Dates.fromEpochDay(d);
            assertEquals(d, Dates.toEpochDay(ymd[0], ymd[1], ymd[2]));
        }
    }

    @Test public void knowsWeekdays() {
        assertEquals(0, Dates.dayOfWeek(Dates.toEpochDay(2026, 8, 31)));   // Monday
        assertEquals(1, Dates.dayOfWeek(Dates.toEpochDay(2026, 9, 1)));    // Tuesday
        assertEquals(6, Dates.dayOfWeek(Dates.toEpochDay(2026, 9, 6)));    // Sunday
        assertEquals(3, Dates.dayOfWeek(Dates.toEpochDay(1970, 1, 1)));    // Thursday
        assertEquals("Понедельник", Dates.DAY_FULL[Dates.dayOfWeek(Dates.toEpochDay(2026, 9, 14))]);
    }

    @Test public void snapsToMonday() {
        long monday = Dates.toEpochDay(2026, 8, 31);
        for (int i = 0; i < 7; i++) assertEquals(monday, Dates.monday(monday + i));
        assertEquals(monday + 7, Dates.monday(monday + 7));
        // and before the epoch, where naive division rounds the wrong way
        long old = Dates.toEpochDay(1969, 12, 30);                         // Tuesday
        assertEquals(Dates.toEpochDay(1969, 12, 29), Dates.monday(old));
    }

    @Test public void handlesLeapYears() {
        assertTrue(Dates.isLeap(2024));
        assertTrue(Dates.isLeap(2000));
        assertTrue(!Dates.isLeap(2100));
        assertTrue(!Dates.isLeap(2026));
        assertArrayEquals(new int[]{2024, 2, 29},
                Dates.fromEpochDay(Dates.toEpochDay(2024, 2, 29)));
        assertEquals(1, Dates.toEpochDay(2024, 3, 1) - Dates.toEpochDay(2024, 2, 29));
    }

    @Test public void parsesTheSitesDateFormat() {
        assertEquals(Dates.toEpochDay(2026, 9, 14), Dates.parseRu("14.09.2026"));
        assertEquals(Dates.NO_DATE, Dates.parseRu("14.9.2026"));
        assertEquals(Dates.NO_DATE, Dates.parseRu("не дата"));
        assertEquals(Dates.NO_DATE, Dates.parseRu(""));
        assertEquals(Dates.NO_DATE, Dates.parseRu(null));
        assertEquals(Dates.NO_DATE, Dates.parseRu("32.01.2026"));
        assertEquals(Dates.NO_DATE, Dates.parseRu("01.13.2026"));
    }

    @Test public void parsesTimes() {
        assertEquals(8 * 60 + 30, Dates.parseTime("08:30"));
        assertEquals(19 * 60, Dates.parseTime("19:00 - 20:30"));
        assertEquals(-1, Dates.parseTime("8:30"));
        assertEquals(-1, Dates.parseTime("25:00"));
        assertEquals(-1, Dates.parseTime(null));
    }

    @Test public void formatsInRussian() {
        long d = Dates.toEpochDay(2026, 9, 4);
        assertEquals("04.09.2026", Dates.formatRu(d));
        assertEquals("4 сентября", Dates.dayMonth(d));
        assertEquals("4 сентября 2026", Dates.dayMonthYear(d));
        assertEquals("Пятница, 4 сентября", Dates.weekdayDayMonth(d));
    }

    @Test public void formatsRanges() {
        long monday = Dates.toEpochDay(2026, 9, 14);
        assertEquals("14–20 сентября", Dates.range(monday, monday + 6));
        assertEquals("28 сентября – 4 октября",
                Dates.range(Dates.toEpochDay(2026, 9, 28), Dates.toEpochDay(2026, 10, 4)));
        assertEquals("28 декабря 2026 – 3 января 2027",
                Dates.range(Dates.toEpochDay(2026, 12, 28), Dates.toEpochDay(2027, 1, 3)));
    }

    @Test public void buildsLocalMillisForCalendarIntents() {
        long millis = Dates.startOfDayMillis(Dates.toEpochDay(2026, 9, 14), 8 * 60 + 30);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        assertEquals(2026, c.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, c.get(Calendar.MONTH));
        assertEquals(14, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, c.get(Calendar.MINUTE));
    }

    @Test public void todayIsConsistent() {
        long today = Dates.today();
        assertEquals(today, Dates.toEpochDay(
                Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH) + 1,
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH)));
        assertTrue(Dates.nowMinutes() >= 0 && Dates.nowMinutes() < 24 * 60);
    }
}
