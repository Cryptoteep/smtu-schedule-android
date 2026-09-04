package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The parser against real pages saved from www.smtu.ru. */
public class ScheduleParserTest {

    // ------------------------------------------------------------- group list

    @Test public void parsesEveryGroupFromTheList() {
        List<Group> groups = ScheduleParser.parseGroups(Fixtures.GROUP_LIST);
        assertEquals("all groups on /ru/listschedule/", 449, groups.size());
        for (Group g : groups) {
            assertFalse("group id", g.id.isEmpty());
            assertFalse("group name", g.name.isEmpty());
            assertFalse("name still has markup: " + g.name, g.name.contains("<"));
        }
        assertNotNull(find(groups, "12826-11"));
    }

    @Test public void sortsGroupsNaturally() {
        List<Group> groups = ScheduleParser.parseGroups(Fixtures.GROUP_LIST);
        for (int i = 1; i < groups.size(); i++)
            assertTrue(groups.get(i - 1).name + " before " + groups.get(i).name,
                    groups.get(i - 1).compareTo(groups.get(i)) <= 0);
    }

    @Test public void readsTheTitleOfEachPage() {
        assertEquals("12826-11", ScheduleParser.parseTitle(Fixtures.GROUP_12826_11));
        assertEquals("12815-55", ScheduleParser.parseTitle(Fixtures.GROUP_12815_55));
        // teacher pages are headed "Расписание занятий преподаватель", with no name
        assertEquals("", ScheduleParser.parseTitle(Fixtures.TEACHER_MANUKYAN));
    }

    // ----------------------------------------------------------- group schedule

    @Test public void parsesEveryRowOfAGroupSchedule() {
        List<Lesson> lessons = ScheduleParser.parseSchedule(Fixtures.GROUP_12826_11);
        assertEquals("rows in the table view", 47, lessons.size());
        for (Lesson l : lessons) {
            assertFalse("subject", l.subject.isEmpty());
            assertTrue("start time of " + l, l.startMinutes() > 0);
            assertTrue("end after start of " + l, l.endMinutes() > l.startMinutes());
            assertTrue("weekday of " + l, l.weekdayIndex() >= 0);
            assertFalse("group of " + l, l.group.isEmpty());
            assertFalse("leftover markup in " + l.subject, l.subject.contains("<"));
        }
    }

    @Test public void readsEveryFieldOfALesson() {
        Lesson l = one(ScheduleParser.parseSchedule(Fixtures.GROUP_12826_11),
                "Общая и неорганическая химия", "11:50 - 13:20", true);
        assertEquals("Понедельник", l.day);
        assertEquals("Лекция", l.type);
        assertEquals("167 Корпус У", l.room);
        assertEquals("12826-11", l.group);
        assertEquals("Ходжаев Рустам Саломович", l.teacher);
        assertEquals("105760", l.teacherId);
        assertEquals("14 сентября — 21 декабря 2026", l.dateRange);
        assertEquals(8, l.days.size());
        assertEquals(Dates.toEpochDay(2026, 9, 14), (long) l.days.get(0));
        assertEquals(Dates.toEpochDay(2026, 12, 21), (long) l.days.get(7));
    }

    /** The site prints some teachers in the subject cell, with no person page. */
    @Test public void readsTeachersPrintedWithoutALink() {
        Lesson l = one(ScheduleParser.parseSchedule(Fixtures.GROUP_12815_55),
                "Теория эксперимента в исследованиях систем", "15:40 - 17:10", true);
        assertEquals("Жеребцова Надежда Юрьевна", l.teacher);
        assertEquals("", l.teacherId);
        assertEquals("Лекция", l.type);
        assertEquals("", l.note);
    }

    /** A trailing remark ("С 26.10 по 14.12") must not be mistaken for a teacher. */
    @Test public void keepsRemarksOutOfTheTeacherField() {
        Lesson l = one(ScheduleParser.parseSchedule(Fixtures.GROUP_12826_11),
                "Физическая культура и спорт", "14:00 - 15:30", true);
        assertEquals("", l.teacher);
        assertEquals("С 26.10 по 14.12", l.note);
        assertEquals("Практическое занятие", l.type);
        assertEquals("Спортзал Корпус У", l.room);
    }

    // --------------------------------------------------------- teacher schedule

    @Test public void teacherPageNamesTheGroupOfEveryLesson() {
        List<Lesson> lessons = ScheduleParser.parseSchedule(Fixtures.TEACHER_MANUKYAN);
        assertEquals(26, lessons.size());
        Set<String> groups = new HashSet<>();
        for (Lesson l : lessons) {
            assertFalse("teacher page lesson without a group: " + l, l.group.isEmpty());
            assertEquals("102710", l.teacherId);
            groups.add(l.group);
        }
        assertTrue("teaches several groups", groups.size() > 1);
        assertTrue(groups.contains("12126-63"));
    }

    // -------------------------------------------------------------- fallbacks

    /** The card view is the fallback: it must agree with the table on the essentials. */
    @Test public void cardViewMatchesTableView() {
        for (String page : new String[]{Fixtures.GROUP_12826_11, Fixtures.GROUP_12815_55,
                                        Fixtures.TEACHER_MANUKYAN}) {
            List<Lesson> table = ScheduleParser.parseTable(page);
            List<Lesson> cards = ScheduleParser.parseCards(page);
            assertEquals("same number of lessons in both views", table.size(), cards.size());
            assertEquals("same subjects", signatures(table), signatures(cards));
        }
    }

    @Test public void cardViewKeepsDatesAndRooms() {
        List<Lesson> cards = ScheduleParser.parseCards(Fixtures.GROUP_12826_11);
        Lesson l = one(cards, "Общая и неорганическая химия", "11:50 - 13:20", true);
        assertEquals("167 Корпус У", l.room);
        assertEquals("Лекция", l.type);
        assertEquals("Ходжаев Рустам Саломович", l.teacher);
        assertEquals("105760", l.teacherId);
        assertEquals(8, l.days.size());
        assertEquals("14 сентября — 21 декабря 2026", l.dateRange);
    }

    @Test public void survivesGarbageInput() {
        assertTrue(ScheduleParser.parseSchedule("").isEmpty());
        assertTrue(ScheduleParser.parseSchedule("<html><body>404</body></html>").isEmpty());
        assertTrue(ScheduleParser.parseGroups("<html></html>").isEmpty());
        assertEquals("", ScheduleParser.parseTitle("<html></html>"));
        // a truncated page must not throw
        String half = Fixtures.GROUP_12826_11.substring(0, Fixtures.GROUP_12826_11.length() / 2);
        assertNotNull(ScheduleParser.parseSchedule(half));
    }

    /** Column order is read from the header row, not assumed. */
    @Test public void followsReorderedColumns() {
        String page = "<div id=\"table-container\"><div class=\"js-day-block\">"
                + "<h3>Среда</h3><table><thead><tr>"
                + "<th scope=\"col\">Предмет</th><th scope=\"col\">Время</th>"
                + "<th scope=\"col\">Даты</th><th scope=\"col\">Аудитория</th>"
                + "<th scope=\"col\">Группа</th><th scope=\"col\">Преподаватель</th>"
                + "</tr></thead><tbody>"
                + "<tr class=\"js-week-container js-week-2\">"
                + "<td><span>Матанализ</span><br><small class=\"text-muted\">Лекция</small></td>"
                + "<td>08:30 - 10:00</td>"
                + "<td title=\"16.09.2026\">16 сентября 2026</td>"
                + "<td>301 Корпус У</td><td>12826-11</td>"
                + "<td><a href='/ru/viewperson/1/'>Иванов Иван Иванович</a></td>"
                + "</tr></tbody></table></div></main>";
        List<Lesson> lessons = ScheduleParser.parseSchedule(page);
        assertEquals(1, lessons.size());
        Lesson l = lessons.get(0);
        assertEquals("Матанализ", l.subject);
        assertEquals("08:30 - 10:00", l.time);
        assertEquals("301 Корпус У", l.room);
        assertEquals("12826-11", l.group);
        assertEquals("Иванов Иван Иванович", l.teacher);
        assertEquals("Среда", l.day);
        assertFalse("js-week-2 is нижняя", l.upper);
        assertEquals(1, l.days.size());
    }

    // ----------------------------------------------------------------- helpers

    private static Group find(List<Group> groups, String name) {
        for (Group g : groups) if (g.name.equals(name)) return g;
        return null;
    }

    private static Lesson one(List<Lesson> lessons, String subject, String time, boolean upper) {
        for (Lesson l : lessons)
            if (l.subject.equals(subject) && l.time.equals(time) && l.upper == upper) return l;
        throw new AssertionError("no lesson " + subject + " at " + time);
    }

    private static Set<String> signatures(List<Lesson> lessons) {
        Set<String> out = new HashSet<>();
        for (Lesson l : lessons) out.add(l.day + '|' + l.time + '|' + l.upper + '|' + l.subject);
        return out;
    }

    /** Sanity check on the fixtures themselves. */
    @Test public void fixturesAreRealPages() {
        List<String> pages = new ArrayList<>();
        pages.add(Fixtures.GROUP_12826_11);
        pages.add(Fixtures.GROUP_12815_55);
        pages.add(Fixtures.TEACHER_MANUKYAN);
        for (String p : pages) {
            assertTrue(p.contains("js-week-container"));
            assertTrue(p.contains("table-container"));
            assertTrue(p.contains("card-container"));
        }
    }
}
