package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The query layer the UI is built on, against a real group's semester. */
public class ScheduleTest {

    private static final long MON_14_SEP = Dates.toEpochDay(2026, 9, 14);

    private static Schedule group() {
        return new Schedule(ScheduleParser.parseSchedule(Fixtures.GROUP_12826_11), "12826-11", 0);
    }

    @Test public void knowsTheSemesterBounds() {
        Schedule s = group();
        assertEquals(Dates.toEpochDay(2026, 9, 1), s.firstDay());
        assertTrue(s.lastDay() >= Dates.toEpochDay(2026, 12, 21));
        assertTrue(s.inSemester(MON_14_SEP));
        assertFalse(s.inSemester(Dates.toEpochDay(2026, 7, 1)));
        assertFalse(s.inSemester(Dates.toEpochDay(2027, 3, 1)));
    }

    @Test public void listsLessonsOfADayInTimeOrder() {
        List<Lesson> monday = group().on(MON_14_SEP);
        assertFalse("Monday 14.09 has lessons", monday.isEmpty());
        for (int i = 1; i < monday.size(); i++)
            assertTrue("ordered by start time",
                    monday.get(i - 1).startMinutes() <= monday.get(i).startMinutes());
        for (Lesson l : monday) {
            assertEquals("Понедельник", l.day);
            assertTrue("happens that day", l.days.contains(MON_14_SEP));
        }
    }

    @Test public void separatesUpperAndLowerWeeks() {
        Schedule s = group();
        List<Lesson> upper = s.on(MON_14_SEP);
        List<Lesson> lower = s.on(MON_14_SEP + 7);
        assertFalse(upper.isEmpty());
        assertFalse(lower.isEmpty());
        for (Lesson l : upper) assertTrue("верхняя", l.upper);
        for (Lesson l : lower) assertFalse("нижняя", l.upper);
        assertFalse("the two weeks differ", sameSubjects(upper, lower));
    }

    @Test public void findsWhatIsRunningNow() {
        Schedule s = group();
        List<Lesson> monday = s.on(MON_14_SEP);
        Lesson first = monday.get(0);
        assertEquals(first, s.runningAt(MON_14_SEP, first.startMinutes() + 5));
        assertNull("before the first lesson", s.runningAt(MON_14_SEP, first.startMinutes() - 5));
        assertNull("after the last lesson", s.runningAt(MON_14_SEP, 23 * 60));
        assertEquals(first, s.nextAfter(MON_14_SEP, first.startMinutes() - 5));
        assertNull(s.nextAfter(MON_14_SEP, 23 * 60));
    }

    @Test public void skipsEmptyDaysWhenAsked() {
        Schedule s = group();
        long sunday = MON_14_SEP + 6;
        assertTrue("Sunday itself is empty", s.on(sunday).isEmpty());
        long next = s.nextDayWithLessons(sunday, +1);
        assertEquals("the next Monday", MON_14_SEP + 7, next);
        long prev = s.nextDayWithLessons(sunday, -1);
        assertTrue("a day earlier in the same week", prev < sunday && !s.on(prev).isEmpty());
        assertEquals(Dates.NO_DATE, Schedule.EMPTY.nextDayWithLessons(sunday, +1));
    }

    @Test public void groupsOccurrencesOfASubject() {
        Schedule s = group();
        List<Lesson> intro = s.ofSubject("Введение в специальность");
        assertTrue("every slot of a weekly subject", intro.size() >= 8);
        for (Lesson l : intro) assertEquals("Введение в специальность", l.subject);
        assertEquals(1, s.ofSubject("Общая и неорганическая химия").size());
        assertTrue(s.ofSubject("нет такого").isEmpty());
        assertTrue(s.subjects().contains("Общая и неорганическая химия"));
        assertTrue(s.teachers().contains("Ходжаев Рустам Саломович"));
    }

    @Test public void searchesEveryField() {
        Schedule s = group();
        assertFalse(s.search("химия").isEmpty());
        assertFalse("case-insensitive", s.search("ХИМИЯ").isEmpty());
        assertFalse("by room", s.search("167").isEmpty());
        assertFalse("by teacher", s.search("Ходжаев").isEmpty());
        assertFalse("by type", s.search("лекция").isEmpty());
        assertTrue(s.search("").isEmpty());
        assertTrue(s.search("нет такого предмета").isEmpty());
    }

    @Test public void mergeKeepsHistoryAndPrefersFreshRows() {
        Lesson old = lesson("Старый предмет", MON_14_SEP);
        Lesson kept = lesson("Общий предмет", MON_14_SEP);
        Schedule cached = new Schedule(list(old, kept), "12826-11", 1000);

        Lesson updated = lesson("Общий предмет", MON_14_SEP);
        updated.room = "999";                                  // same key? no: room is part of it
        Lesson added = lesson("Новый предмет", MON_14_SEP + 7);
        Schedule fresh = new Schedule(list(updated, added), "12826-11", 2000);

        Schedule merged = cached.mergedWith(fresh);
        assertEquals("history survives", 4, merged.size());
        assertTrue(merged.subjects().contains("Старый предмет"));
        assertTrue(merged.subjects().contains("Новый предмет"));
        assertEquals("fetch time comes from the fresh copy", 2000, merged.fetchedAt());

        Schedule sameRows = cached.mergedWith(new Schedule(list(kept), "12826-11", 3000));
        assertEquals("identical rows are not duplicated", 2, sameRows.size());
        assertEquals(cached.size(), cached.mergedWith(Schedule.EMPTY).size());
    }

    @Test public void emptyScheduleAnswersEveryQuery() {
        Schedule s = Schedule.EMPTY;
        assertTrue(s.isEmpty());
        assertTrue(s.on(MON_14_SEP).isEmpty());
        assertTrue(s.search("x").isEmpty());
        assertTrue(s.subjects().isEmpty());
        assertNull(s.runningAt(MON_14_SEP, 600));
        assertEquals(Dates.NO_DATE, s.firstDay());
        assertFalse(s.inSemester(MON_14_SEP));
    }

    /** Rows without a date list fall back to weekday + parity. */
    @Test public void matchesDatelessRowsByParity() {
        Lesson l = new Lesson();
        l.subject = "Военная кафедра";
        l.time = "09:00 - 15:00";
        l.day = "Четверг";
        l.upper = true;
        Schedule s = new Schedule(list(l), "", 0);
        long thursdayUpper = Dates.toEpochDay(2026, 9, 17);
        assertTrue("upper-week Thursday", s.on(thursdayUpper).contains(l));
        assertFalse("Friday", s.on(thursdayUpper + 1).contains(l));
        assertFalse("lower-week Thursday", s.on(thursdayUpper + 7).contains(l));
    }

    private static boolean sameSubjects(List<Lesson> a, List<Lesson> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!a.get(i).subject.equals(b.get(i).subject)) return false;
        return true;
    }

    private static Lesson lesson(String subject, long day) {
        Lesson l = new Lesson();
        l.subject = subject;
        l.time = "08:30 - 10:00";
        l.day = Dates.DAY_FULL[Dates.dayOfWeek(day)];
        l.upper = true;
        l.days.add(day);
        return l;
    }

    private static List<Lesson> list(Lesson... lessons) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : lessons) out.add(l);
        return out;
    }
}
