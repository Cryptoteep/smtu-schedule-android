package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Week parity derived from the schedule itself. This is what the app shows in
 * the header badge and what dateless lessons are matched against, so it is
 * checked against the real pages rather than a hard-coded anchor.
 */
public class WeekParityTest {

    @Test public void derivesTheCycleFromRealPages() {
        for (String page : new String[]{Fixtures.GROUP_12826_11, Fixtures.GROUP_12815_55,
                                        Fixtures.TEACHER_MANUKYAN}) {
            WeekParity p = WeekParity.derive(ScheduleParser.parseSchedule(page));
            assertTrue("derived from data", p.isDerived());
            assertEquals("every dated week agrees with the anchor", 1.0, p.agreement(), 0.0001);
            assertTrue("14.09.2026 is верхняя", p.isUpper(Dates.toEpochDay(2026, 9, 14)));
            assertFalse("21.09.2026 is нижняя", p.isUpper(Dates.toEpochDay(2026, 9, 21)));
            assertTrue("28.09.2026 is верхняя", p.isUpper(Dates.toEpochDay(2026, 9, 28)));
        }
    }

    @Test public void parityIsConstantWithinAWeek() {
        WeekParity p = WeekParity.derive(ScheduleParser.parseSchedule(Fixtures.GROUP_12826_11));
        long monday = Dates.toEpochDay(2026, 9, 14);
        for (int i = 0; i < 7; i++)
            assertTrue("day " + i + " of the same week", p.isUpper(monday + i));
        for (int i = 0; i < 7; i++)
            assertFalse("day " + i + " of the next week", p.isUpper(monday + 7 + i));
    }

    /** The anchor may sit anywhere; parity must still alternate in both directions. */
    @Test public void alternatesBackwardsFromTheAnchor() {
        WeekParity p = new WeekParity(Dates.toEpochDay(2026, 9, 16), true); // a Wednesday
        assertTrue(p.isUpper(Dates.toEpochDay(2026, 9, 14)));   // Monday of the same week
        assertTrue(p.isUpper(Dates.toEpochDay(2026, 9, 20)));   // Sunday of the same week
        assertFalse(p.isUpper(Dates.toEpochDay(2026, 9, 7)));   // week before
        assertTrue(p.isUpper(Dates.toEpochDay(2026, 8, 31)));   // two weeks before
        assertFalse(p.isUpper(Dates.toEpochDay(2026, 9, 21)));  // week after
        assertTrue(p.isUpper(Dates.toEpochDay(2026, 9, 28)));
    }

    @Test public void fallsBackWhenThereIsNoData() {
        WeekParity p = WeekParity.derive(new ArrayList<Lesson>());
        assertFalse(p.isDerived());
        assertTrue("the constant anchor: week of 31.08.2026 is верхняя",
                p.isUpper(Dates.toEpochDay(2026, 8, 31)));
        assertFalse(p.isUpper(Dates.toEpochDay(2026, 9, 7)));
    }

    /** One mislabelled row must not flip the whole semester. */
    @Test public void toleratesAnOutlier() {
        List<Lesson> lessons = new ArrayList<>();
        for (int week = 0; week < 8; week++) {
            for (int i = 0; i < 4; i++)
                lessons.add(lesson(Dates.toEpochDay(2026, 9, 14) + week * 7L, week % 2 == 0));
        }
        Lesson wrong = lesson(Dates.toEpochDay(2026, 9, 21), true);        // should be нижняя
        lessons.add(wrong);

        WeekParity p = WeekParity.derive(lessons);
        assertTrue(p.isUpper(Dates.toEpochDay(2026, 9, 14)));
        assertFalse("the majority still wins that week", p.isUpper(Dates.toEpochDay(2026, 9, 21)));
        assertEquals("all weeks agree", 1.0, p.agreement(), 0.0001);
    }

    @Test public void reportsDisagreementWhenTheCycleIsBroken() {
        List<Lesson> lessons = new ArrayList<>();
        lessons.add(lesson(Dates.toEpochDay(2026, 9, 14), true));
        lessons.add(lesson(Dates.toEpochDay(2026, 9, 21), true));          // no alternation
        WeekParity p = WeekParity.derive(lessons);
        assertTrue("disagreement is visible", p.agreement() < 1.0);
    }

    private static Lesson lesson(long day, boolean upper) {
        Lesson l = new Lesson();
        l.subject = "X";
        l.time = "08:30 - 10:00";
        l.day = Dates.DAY_FULL[Dates.dayOfWeek(day)];
        l.upper = upper;
        l.days.add(day);
        return l;
    }
}
