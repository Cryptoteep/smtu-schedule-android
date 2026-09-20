package com.korabel.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Разбор вёрстки, которую университет включил 15.09.2026.
 *
 * Что тогда сломалось и почему эти тесты существуют: сайт перестал печатать
 * точные даты занятий, перенёс чётность недели из класса строки в её id и
 * добавил третий вид недели — «обе». Прежний парсер на такой странице находил
 * ноль занятий, но никакой ошибки не возникало: сборщик данных пять дней
 * подряд коммитил пустые расписания, а приложение молча показывало старый кэш.
 */
public class ScheduleParserV2Test {

    private static final List<Lesson> LESSONS = ScheduleParser.parseSchedule(Fixtures.GROUP_V2);

    @Test public void readsEveryRowOfTheNewLayout() {
        assertEquals(32, LESSONS.size());
        for (Lesson l : LESSONS) {
            assertFalse("у занятия должен быть предмет", l.subject.isEmpty());
            assertFalse("и день недели: " + l.subject, l.day.isEmpty());
            assertTrue("и время: " + l.subject, l.startMinutes() > 0);
        }
    }

    @Test public void readsEveryFieldOfALesson() {
        Lesson first = LESSONS.get(0);
        assertEquals("Понедельник", first.day);
        assertEquals("11:50 - 13:20", first.time);
        assertEquals("Общая и неорганическая химия", first.subject);
        assertEquals("Лекция", first.type);
        assertEquals("аудитория теперь пишется корпусом вперёд", "У 167", first.room);
        assertEquals("12226-11", first.group);
        assertEquals("Ходжаев Рустам Саломович", first.teacher);
        assertEquals("105760", first.teacherId);
        assertTrue("точных дат на странице больше нет", first.days.isEmpty());
        assertTrue(first.upper);
        assertFalse(first.bothWeeks);
    }

    @Test public void understandsTheThirdKindOfWeek() {
        int both = 0, upper = 0, lower = 0;
        for (Lesson l : LESSONS) {
            if (l.bothWeeks) both++;
            else if (l.upper) upper++;
            else lower++;
        }
        assertEquals("«обе недели» — новый вид занятия", 13, both);
        assertEquals(9, upper);
        assertEquals(10, lower);
    }

    @Test public void aBothWeeksLessonHappensOnEitherWeek() {
        Lesson both = null;
        for (Lesson l : LESSONS) if (l.bothWeeks) { both = l; break; }
        assertNotNull(both);
        long monday = Dates.toEpochDay(2026, 9, 21);
        long day = monday + both.weekdayIndex();
        assertTrue(both.happensOn(day, true));
        assertTrue(both.happensOn(day, false));
        assertEquals("Каждую неделю", both.weekLabel());
    }

    @Test public void takesWeekParityFromThePageItself() {
        WeekParity parity = ScheduleParser.parseWeekAnchor(Fixtures.GROUP_V2);
        assertNotNull("«Сегодня: 20 Сентября 2026 года, …, верхняя неделя»", parity);
        assertTrue(parity.isDerived());
        assertTrue("20.09.2026 — верхняя неделя", parity.isUpper(Dates.toEpochDay(2026, 9, 20)));
        assertFalse("значит следующая — нижняя", parity.isUpper(Dates.toEpochDay(2026, 9, 21)));
    }

    @Test public void theTeacherPageStillNamesEveryGroup() {
        List<Lesson> lessons = ScheduleParser.parseSchedule(Fixtures.TEACHER_V2);
        assertEquals(17, lessons.size());
        for (Lesson l : lessons)
            assertFalse("группа — единственное, чего нет в карточном виде", l.group.isEmpty());
    }

    @Test public void readsTheGroupList() {
        List<Group> groups = ScheduleParser.parseGroups(Fixtures.GROUP_LIST_V2);
        assertEquals(412, groups.size());
        assertEquals("1101", groups.get(0).name);
    }

    @Test public void readsTheTitle() {
        assertEquals("12226-11", ScheduleParser.parseTitle(Fixtures.GROUP_V2));
        assertEquals("у преподавателя имени в заголовке нет, его берут из занятий",
                "", ScheduleParser.parseTitle(Fixtures.TEACHER_V2));
    }

    @Test public void aScheduleWithoutDatesStillAnswersQuestions() {
        Schedule s = new Schedule(LESSONS, "12226-11", 0L, false,
                ScheduleParser.parseWeekAnchor(Fixtures.GROUP_V2));
        long monday = Dates.toEpochDay(2026, 9, 21);          // нижняя неделя
        assertFalse("день недели + чётность заменяют список дат", s.on(monday).isEmpty());
        assertTrue("границы семестра неизвестны — значит не отсекаем ничего",
                s.inSemester(monday));
        assertEquals(monday, s.nextDayWithLessons(monday, +1));
        assertFalse(s.isUpper(monday));
        assertTrue(s.isUpper(monday + 7));
    }

    @Test public void theCardViewIsARealFallback() {
        List<Lesson> cards = ScheduleParser.parseCards(Fixtures.GROUP_V2);
        assertEquals("карточки дают то же, что таблица", LESSONS.size(), cards.size());

        Lesson first = cards.get(0);
        assertEquals("Понедельник", first.day);
        assertEquals("11:50 - 13:20", first.time);
        assertEquals("Общая и неорганическая химия", first.subject);
        assertEquals("Лекция", first.type);
        assertEquals("У 167", first.room);
        assertEquals("Ходжаев Рустам Саломович", first.teacher);
        assertEquals("105760", first.teacherId);
        assertEquals("группу в карточках не пишут — берём из заголовка страницы",
                "12226-11", first.group);
        assertTrue(first.upper);

        int both = 0;
        for (Lesson l : cards) if (l.bothWeeks) both++;
        assertEquals(13, both);
    }

    @Test public void aLessonBeyondTheHorizonIsNotInvented() {
        long fetched = Dates.startOfDayMillis(Dates.toEpochDay(2026, 9, 21), 12 * 60);
        Schedule s = new Schedule(LESSONS, "12226-11", fetched, false,
                ScheduleParser.parseWeekAnchor(Fixtures.GROUP_V2));
        long july = Dates.toEpochDay(2027, 7, 12);
        assertTrue("в июле занятий не показываем", s.on(july).isEmpty());
        assertFalse(s.inSemester(july));
        assertEquals("и виджет в каникулы молчит", Now.NONE,
                Now.compute(s, july, 9 * 3600).kind);
    }

    @Test public void saysNothingWhenThePageHasNoAnchor() {
        assertNull(ScheduleParser.parseWeekAnchor("<html><body>ничего</body></html>"));
        assertNull(ScheduleParser.parseWeekAnchor("Сегодня: 32 Кактября 2026 года, верхняя неделя"));
    }
}
