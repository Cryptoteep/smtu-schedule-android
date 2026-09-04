package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@link Lesson}, {@link Group} and the HTML helpers. */
public class ModelTest {

    // ----------------------------------------------------------------- lesson

    @Test public void readsItsOwnTime() {
        Lesson l = new Lesson();
        l.time = "08:30 - 10:00";
        assertEquals(8 * 60 + 30, l.startMinutes());
        assertEquals(10 * 60, l.endMinutes());

        Lesson broken = new Lesson();
        broken.time = "";
        assertEquals(-1, broken.startMinutes());
        assertEquals(-1, broken.endMinutes());
    }

    @Test public void mapsWeekdayNames() {
        Lesson l = new Lesson();
        l.day = "Среда";
        assertEquals(2, l.weekdayIndex());
        l.day = "среда";
        assertEquals("case-insensitive", 2, l.weekdayIndex());
        l.day = "";
        assertEquals(-1, l.weekdayIndex());
    }

    @Test public void datedLessonsIgnoreParity() {
        long day = Dates.toEpochDay(2026, 9, 14);
        Lesson l = new Lesson();
        l.day = "Понедельник";
        l.upper = true;
        l.days.add(day);
        assertTrue(l.happensOn(day, true));
        assertTrue("the date list wins over parity", l.happensOn(day, false));
        assertFalse(l.happensOn(day + 7, true));
    }

    @Test public void sortsByWeekdayThenTime() {
        Lesson monday = make("Понедельник", "10:10 - 11:40", "Б");
        Lesson mondayEarly = make("Понедельник", "08:30 - 10:00", "В");
        Lesson tuesday = make("Вторник", "08:30 - 10:00", "А");
        List<Lesson> all = new ArrayList<>();
        Collections.addAll(all, tuesday, monday, mondayEarly);
        Collections.sort(all);
        assertEquals(mondayEarly, all.get(0));
        assertEquals(monday, all.get(1));
        assertEquals(tuesday, all.get(2));
    }

    @Test public void keyIdentifiesTheRowNotItsDates() {
        Lesson a = make("Понедельник", "08:30 - 10:00", "Химия");
        Lesson b = make("Понедельник", "08:30 - 10:00", "Химия");
        b.days.add(Dates.toEpochDay(2026, 9, 14));
        assertEquals("dates are not part of the identity", a.key(), b.key());
        assertNotEquals("but they are part of equality", a, b);

        b.room = "167";
        assertNotEquals(a.key(), b.key());
    }

    @Test public void oneLineIsReadable() {
        Lesson l = make("Понедельник", "11:50 - 13:20", "Общая и неорганическая химия");
        l.type = "Лекция";
        l.room = "167 Корпус У";
        l.teacher = "Ходжаев Рустам Саломович";
        l.group = "12826-11";
        String line = l.oneLine();
        assertTrue(line, line.startsWith("11:50 – 13:20"));
        assertTrue(line, line.contains("Общая и неорганическая химия"));
        assertTrue(line, line.contains("(Лекция)"));
        assertTrue(line, line.contains("167 Корпус У"));
        assertTrue(line, line.contains("Ходжаев"));
    }

    // ------------------------------------------------------------------ group

    @Test public void groupsSortNaturally() {
        List<Group> groups = new ArrayList<>();
        Collections.addAll(groups,
                new Group("1", "12826-11"), new Group("2", "12826-2"),
                new Group("3", "1282-11"), new Group("4", "12826-1"),
                new Group("5", "22826-11"));
        Collections.sort(groups);
        assertEquals("1282-11", groups.get(0).name);
        assertEquals("12826-1", groups.get(1).name);
        assertEquals("12826-2", groups.get(2).name);
        assertEquals("12826-11", groups.get(3).name);
        assertEquals("22826-11", groups.get(4).name);
    }

    @Test public void groupsAreIdentifiedById() {
        assertEquals(new Group("7798", "12826-11"), new Group("7798", "другое имя"));
        assertNotEquals(new Group("7798", "12826-11"), new Group("7799", "12826-11"));
    }

    // ------------------------------------------------------------------- html

    @Test public void stripsTagsAndDecodesEntities() {
        assertEquals("Химия и жизнь", Html.text("<span>Химия</span> и <b>жизнь</b>"));
        assertEquals("«Кавычки» — тире", Html.text("&laquo;Кавычки&raquo; &mdash; тире"));
        assertEquals("A&B", Html.text("A&amp;B"));
        assertEquals("нераз рывный", Html.text("нераз&nbsp;рывный"));
        assertEquals("Ю", Html.text("&#1070;"));
        assertEquals("Ю", Html.text("&#x42E;"));
        assertEquals("", Html.text(null));
        assertEquals("&unknown;", Html.text("&unknown;"));
    }

    @Test public void readsAttributes() {
        assertEquals("14.09.2026", Html.attr(" class=\"x\" title=\"14.09.2026\"", "title"));
        assertEquals("14.09.2026", Html.attr(" title='14.09.2026'", "title"));
        assertEquals("", Html.attr(" class=\"x\"", "title"));
        assertEquals("A&B", Html.attr(" title=\"A&amp;B\"", "title"));
    }

    private static Lesson make(String day, String time, String subject) {
        Lesson l = new Lesson();
        l.day = day;
        l.time = time;
        l.subject = subject;
        return l;
    }
}
