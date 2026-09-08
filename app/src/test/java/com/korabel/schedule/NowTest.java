package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Состояние «что сейчас» — то, что показывают строка отсчёта и все три виджета.
 * Считается от расписания и момента времени, без Android, поэтому проверяется
 * обычным JUnit.
 */
public class NowTest {

    private static final long MON = Dates.toEpochDay(2026, 9, 7);   // понедельник
    private static final long TUE = MON + 1;

    private static Lesson lesson(String time, String subject, String room, long... days) {
        Lesson l = new Lesson();
        l.time = time;
        l.subject = subject;
        l.room = room;
        l.day = Dates.DAY_FULL[Dates.dayOfWeek(days.length > 0 ? days[0] : MON)];
        for (long d : days) l.days.add(d);
        return l;
    }

    private static Schedule monday() {
        List<Lesson> lessons = new ArrayList<>(Arrays.asList(
                lesson("08:30 - 10:00", "Химия", "167 Корпус У", MON),
                lesson("10:10 - 11:40", "Физика", "509 Корпус Б", MON),
                lesson("13:30 - 15:00", "История", "", MON),
                lesson("10:10 - 11:40", "Матанализ", "301 Корпус А", TUE)));
        return new Schedule(lessons, "12826-11", 0L);
    }

    private static int at(int hour, int minute) {
        return (hour * 60 + minute) * 60;
    }

    @Test public void tellsHowLongTheRunningLessonHasLeft() {
        Now.State st = Now.compute(monday(), MON, at(8, 45));
        assertEquals(Now.ONGOING, st.kind);
        assertEquals("Химия", st.current.subject);
        assertEquals(75 * 60, st.remainSec);
        assertEquals("кольцо считает всю пару целиком", 90 * 60, st.gapSec);
        assertSame(st.current, st.lesson());
    }

    @Test public void countsDownToTheNextLessonDuringABreak() {
        Now.State st = Now.compute(monday(), MON, at(10, 0));
        assertEquals(Now.BREAK, st.kind);
        assertEquals("Физика", st.next.subject);
        assertEquals(10 * 60, st.remainSec);
        assertEquals("перемена меряется от конца прошлой пары", 10 * 60, st.gapSec);
    }

    @Test public void beforeTheFirstLessonTheGapStartsInTheMorning() {
        Now.State st = Now.compute(monday(), MON, at(7, 30));
        assertEquals(Now.BREAK, st.kind);
        assertEquals("Химия", st.next.subject);
        assertEquals(60 * 60, st.remainSec);
        assertEquals(30 * 60, st.gapSec);
    }

    @Test public void afterTheLastLessonItFallsOverToTheNextStudyDay() {
        Now.State st = Now.compute(monday(), MON, at(20, 0));
        assertEquals(Now.AFTER, st.kind);
        assertEquals("Матанализ", st.next.subject);
        assertEquals(TUE, st.nextDay);
        assertTrue(st.isTomorrow());
        assertEquals("ЗАВТРА", Now.dayLabel(st));
        assertEquals((14 * 60 + 10) * 60, st.remainSec);   // 20:00 → завтра 10:10
    }

    @Test public void aDayFurtherOffIsNamedByItsWeekday() {
        List<Lesson> lessons = new ArrayList<>(Arrays.asList(
                lesson("08:30 - 10:00", "Химия", "167 Корпус У", MON),
                lesson("12:00 - 13:30", "Сопромат", "301 Корпус А", MON + 3)));
        Now.State st = Now.compute(new Schedule(lessons, "12826-11", 0L), MON, at(20, 0));
        assertEquals(Now.AFTER, st.kind);
        assertEquals(MON + 3, st.nextDay);
        assertEquals("ЧЕТВЕРГ", Now.dayLabel(st));
    }

    @Test public void nothingAheadMeansNothingToShow() {
        Now.State st = Now.compute(monday(), TUE, at(20, 0));   // всё уже прошло
        assertEquals(Now.NONE, st.kind);
        assertNull(st.lesson());
    }

    @Test public void emptyScheduleMeansNothingToShow() {
        Now.State st = Now.compute(Schedule.EMPTY, MON, at(9, 0));
        assertEquals(Now.NONE, st.kind);
        assertNull(st.lesson());
        assertTrue(st.today.isEmpty());
    }

    @Test public void formatsCountdownsForBothTheAppAndTheWidgets() {
        assertEquals("1:05", Now.hms(65));
        assertEquals("0:00", Now.hms(-5));
        assertEquals("1:15:00", Now.hms(75 * 60));

        assertEquals("25 мин", Now.human(25 * 60));
        assertEquals("округляем вверх: «0 мин» на идущей паре — вранье",
                "2 мин", Now.human(61));
        assertEquals("2 ч", Now.human(120 * 60));
        assertEquals("1 ч 5 мин", Now.human(65 * 60));
    }

    @Test public void shortensWhatTheWidgetsHaveNoRoomFor() {
        assertEquals("08:30–10:00", Now.compactTime("08:30 - 10:00"));
        assertEquals("08:30", Now.startOf("08:30 - 10:00"));
        assertEquals("167", Now.roomShort("167 Корпус У"));
        assertEquals("каф.ИЯ", Now.roomShort("каф.ИЯ Корпус У"));
        assertEquals("название аудитории не обрезаем по первому пробелу",
                "Актовый зал", Now.roomShort("Актовый зал Корпус У"));
        assertEquals("415", Now.roomShort("415"));
        assertEquals("", Now.roomShort(""));
    }
}
