package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Разбор резервного среза — того самого JSON, который приложение берёт, когда
 * до smtu.ru не достучаться (например, под VPN с зарубежным выходом).
 */
public class MirrorTest {

    private static final String INDEX = "{"
            + "\"updated\":\"2026-09-08T09:05:38.000Z\","
            + "\"groups\":[{\"id\":\"7798\",\"name\":\"12826-11\",\"lessons\":47},"
            + "{\"id\":\"7482\",\"name\":\"1201\",\"lessons\":28}],"
            + "\"teachers\":[{\"id\":\"105760\",\"name\":\"Ходжаев Рустам Саломович\",\"lessons\":31}]}";

    private static final String GROUP = "{\"id\":\"7798\",\"name\":\"12826-11\",\"lessons\":["
            + "{\"day\":\"Понедельник\",\"upper\":true,\"time\":\"11:50 - 13:20\","
            + "\"dateRange\":\"14 сентября — 21 декабря 2026\",\"room\":\"167 Корпус У\","
            + "\"group\":\"12826-11\",\"subject\":\"Общая и неорганическая химия\","
            + "\"type\":\"Лекция\",\"note\":\"\",\"teacher\":\"Ходжаев Рустам Саломович\","
            + "\"teacherId\":\"105760\",\"days\":[20710,20724]},"
            + "{\"day\":\"Вторник\",\"upper\":false,\"time\":\"08:30 - 10:00\","
            + "\"dateRange\":\"\",\"room\":\"\",\"group\":\"12826-11\",\"subject\":\"Физика\","
            + "\"type\":\"\",\"note\":\"2 п/г\",\"teacher\":\"\",\"teacherId\":\"\",\"days\":[]}]}";

    @Test public void readsTheGroupList() throws Exception {
        List<Group> groups = Mirror.parseGroups(INDEX);
        assertEquals(2, groups.size());
        assertEquals("7798", groups.get(0).id);
        assertEquals("12826-11", groups.get(0).name);
    }

    @Test public void readsEveryFieldOfALesson() throws Exception {
        List<Lesson> lessons = Mirror.parseSchedule(GROUP);
        assertEquals(2, lessons.size());

        Lesson first = lessons.get(0);
        assertEquals("Понедельник", first.day);
        assertEquals("11:50 - 13:20", first.time);
        assertTrue(first.upper);
        assertEquals("Общая и неорганическая химия", first.subject);
        assertEquals("Лекция", first.type);
        assertEquals("167 Корпус У", first.room);
        assertEquals("12826-11", first.group);
        assertEquals("Ходжаев Рустам Саломович", first.teacher);
        assertEquals("105760", first.teacherId);
        assertEquals("14 сентября — 21 декабря 2026", first.dateRange);
        assertEquals(2, first.days.size());
        assertEquals(11 * 60 + 50, first.startMinutes());
        assertEquals(13 * 60 + 20, first.endMinutes());

        Lesson second = lessons.get(1);
        assertFalse(second.upper);
        assertEquals("2 п/г", second.note);
        assertTrue("занятие без дат разрешено: сработает запасное правило по чётности",
                second.days.isEmpty());
    }

    @Test public void readsTheTitle() throws Exception {
        assertEquals("12826-11", Mirror.parseTitle(GROUP));
    }

    @Test public void mirrorAnswersTheSameQuestionsAsTheSite() throws Exception {
        Schedule s = new Schedule(Mirror.parseSchedule(GROUP), Mirror.parseTitle(GROUP),
                1757322338000L, true);
        assertTrue("помечено как резервная копия", s.isMirrored());
        assertEquals("12826-11", s.title());
        assertFalse(s.on(20710L).isEmpty());
        assertFalse(s.search("химия").isEmpty());
    }

    @Test public void survivesGarbage() throws Exception {
        assertTrue(Mirror.parseSchedule("{}").isEmpty());
        assertTrue(Mirror.parseGroups("{}").isEmpty());
        assertEquals("", Mirror.parseTitle("{}"));
    }

    @Test public void buildsUrls() {
        assertTrue(Mirror.groupsUrl().endsWith("/data/index.json"));
        assertTrue(Mirror.scheduleUrl(false, "7798").endsWith("/data/g/7798.json"));
        assertTrue(Mirror.scheduleUrl(true, "105760").endsWith("/data/t/105760.json"));
        assertTrue("срез лежит на страницах самого проекта",
                Mirror.BASE.startsWith("https://cryptoteep.github.io/smtu-schedule-android/"));
    }
}
