package com.korabel.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A parsed schedule plus everything the UI wants to ask of it: what happens on
 * a given day, which lesson is running right now, where the semester starts and
 * ends, and free-text search.
 *
 * Immutable and free of Android APIs, so the whole query layer is unit-tested.
 */
public final class Schedule {

    public static final Schedule EMPTY = new Schedule(new ArrayList<Lesson>(), "", 0L);

    private final List<Lesson> lessons;
    private final WeekParity parity;
    private final String title;      // group name or teacher name, as printed by the site
    private final long fetchedAt;    // millis, 0 when unknown
    private final boolean mirrored;  // взято из резервного среза, а не с сайта
    private final long firstDay, lastDay;
    private final boolean dated;     // у занятий есть точные даты — границы настоящие

    /**
     * На сколько недель вперёд имеет смысл разворачивать расписание, у которого
     * нет точных дат.
     *
     * До 15.09.2026 каждое занятие несло список своих дат, и вопрос «а идут ли
     * пары в июле» решался сам собой. Теперь на странице только «день недели +
     * чётность», и это правило можно продолжать бесконечно — приложение будет
     * бодро показывать занятия и в каникулы, и через год. Поэтому расписание
     * считается действующим примерно на длину семестра от дня загрузки, а
     * дальше честно говорится, что данные туда не достают.
     */
    private static final int HORIZON_WEEKS = 16;

    public Schedule(List<Lesson> lessons, String title, long fetchedAt) {
        this(lessons, title, fetchedAt, false, null);
    }

    public Schedule(List<Lesson> lessons, String title, long fetchedAt, boolean mirrored) {
        this(lessons, title, fetchedAt, mirrored, null);
    }

    /**
     * @param anchor чётность недели со страницы сайта; {@code null} — вывести
     *               её из дат занятий, как делалось до смены вёрстки
     */
    public Schedule(List<Lesson> lessons, String title, long fetchedAt, boolean mirrored,
                    WeekParity anchor) {
        List<Lesson> copy = new ArrayList<>(lessons);
        Collections.sort(copy);
        this.lessons = Collections.unmodifiableList(copy);
        this.parity = anchor != null ? anchor : WeekParity.derive(copy);
        this.title = title == null ? "" : title;
        this.fetchedAt = fetchedAt;
        this.mirrored = mirrored;

        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Lesson l : copy)
            for (long d : l.days) {
                if (d < min) min = d;
                if (d > max) max = d;
            }
        this.dated = min != Long.MAX_VALUE;
        if (dated) {
            this.firstDay = min;
            this.lastDay = max;
        } else if (!copy.isEmpty() && fetchedAt > 0) {
            // дат нет: считаем расписание действующим от начала недели, в которую
            // его загрузили, и примерно на семестр вперёд
            long monday = Dates.monday(Dates.epochDayOf(fetchedAt));
            this.firstDay = monday;
            this.lastDay = monday + HORIZON_WEEKS * 7L - 1;
        } else {
            this.firstDay = Dates.NO_DATE;
            this.lastDay = Dates.NO_DATE;
        }
    }

    // ------------------------------------------------------------- accessors

    public List<Lesson> lessons()  { return lessons; }
    public WeekParity parity()     { return parity; }
    public String title()          { return title; }
    public long fetchedAt()        { return fetchedAt; }
    /** Данные пришли из резервного среза (сайт был недоступен). */
    public boolean isMirrored()    { return mirrored; }
    public boolean isEmpty()       { return lessons.isEmpty(); }

    /**
     * Собрано раньше, чем {@code other}, — то есть заменять им {@code other}
     * значит откатиться назад. Пустое {@code other} заменить можно всегда.
     */
    public boolean isOlderThan(Schedule other) {
        return !other.isEmpty() && fetchedAt < other.fetchedAt;
    }
    public int size()              { return lessons.size(); }

    /** Первый и последний день, который покрывает это расписание. */
    public long firstDay()         { return firstDay; }
    public long lastDay()          { return lastDay; }
    /**
     * Границы настоящие — взяты из дат занятий, а не рассчитаны от дня загрузки.
     * Нужно интерфейсу, чтобы не выдавать оценку за точное знание.
     */
    public boolean hasExactDates() { return dated; }

    public boolean isUpper(long epochDay) {
        return parity.isUpper(epochDay);
    }

    /**
     * Покрывает ли это расписание такой день.
     *
     * Когда границы неизвестны (расписание собрано в тесте или пришло без
     * времени загрузки), спорить не о чем — считаем, что покрывает.
     */
    public boolean inSemester(long epochDay) {
        if (lessons.isEmpty()) return false;
        if (firstDay == Dates.NO_DATE) return true;
        return epochDay >= firstDay && epochDay <= lastDay;
    }

    // --------------------------------------------------------------- queries

    /** Lessons of one calendar day, ordered by start time. */
    public List<Lesson> on(long epochDay) {
        // за горизонтом расписания правило «день недели + чётность» продолжать
        // нечестно: занятий там не потому что они есть, а потому что мы умеем
        // считать вперёд
        if (!inSemester(epochDay)) return new ArrayList<>();
        boolean upper = isUpper(epochDay);
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : lessons) if (l.happensOn(epochDay, upper)) out.add(l);
        Collections.sort(out, (a, b) -> Integer.compare(a.startMinutes(), b.startMinutes()));
        return out;
    }

    /** The lesson running at that moment, or null. */
    public Lesson runningAt(long epochDay, int minutes) {
        for (Lesson l : on(epochDay))
            if (minutes >= l.startMinutes() && minutes < l.endMinutes()) return l;
        return null;
    }

    /** The next lesson later that same day, or null. */
    public Lesson nextAfter(long epochDay, int minutes) {
        for (Lesson l : on(epochDay)) if (l.startMinutes() > minutes) return l;
        return null;
    }

    /** The next day at or after `from` that has lessons, or {@link Dates#NO_DATE}. */
    public long nextDayWithLessons(long from, int direction) {
        boolean bounded = firstDay != Dates.NO_DATE;
        for (int i = 0; i <= 400; i++) {
            long d = from + (long) direction * i;
            if (bounded && (d < firstDay || d > lastDay)) break;
            if (!on(d).isEmpty()) return d;
            if (!bounded && i >= 14) break;   // без границ дальше двух недель смысла нет
        }
        return Dates.NO_DATE;
    }

    /**
     * Ближайший к {@code from} день, когда это занятие действительно идёт:
     * сначала вперёд, потом назад, в пределах расписания.
     *
     * Нужно поиску и списку «все занятия по предмету». Пока у занятий были
     * точные даты, достаточно было взять первую; с 15.09.2026 дат нет, и
     * интерфейс открывал найденную пару в тот день, что был на экране, —
     * лекцию понедельника с подписью «среда». Заодно учитывается чётность:
     * занятие нижней недели не откроется на верхней.
     *
     * @return день или {@link Dates#NO_DATE}, если в пределах расписания его нет
     */
    public long occurrenceNear(Lesson lesson, long from) {
        if (!lesson.days.isEmpty()) {
            for (long d : lesson.days) if (d >= from) return d;
            return lesson.days.get(lesson.days.size() - 1);
        }
        // цикл «день недели + чётность» повторяется за две недели
        for (int i = 0; i < 14; i++)
            if (on(from + i).contains(lesson)) return from + i;
        for (int i = 1; i <= 14; i++)
            if (on(from - i).contains(lesson)) return from - i;
        return Dates.NO_DATE;
    }

    /** Every occurrence of one subject. */
    public List<Lesson> ofSubject(String subject) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : lessons) if (l.subject.equals(subject)) out.add(l);
        return out;
    }

    /** Every lesson taught by that person (by name, for the offline fallback). */
    public List<Lesson> ofTeacher(String teacher) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : lessons) if (l.teacher.equals(teacher)) out.add(l);
        return out;
    }

    /** Case-insensitive search over subject, teacher, room, type and group. */
    public List<Lesson> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Lesson> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (Lesson l : lessons) {
            String hay = (l.subject + ' ' + l.teacher + ' ' + l.room + ' '
                    + l.type + ' ' + l.group + ' ' + l.note).toLowerCase(Locale.ROOT);
            if (hay.contains(q)) out.add(l);
        }
        return out;
    }

    /** Distinct subjects, in schedule order. */
    public List<String> subjects() {
        Set<String> seen = new LinkedHashSet<>();
        for (Lesson l : lessons) if (!l.subject.isEmpty()) seen.add(l.subject);
        List<String> out = new ArrayList<>(seen);
        Collections.sort(out);
        return out;
    }

    /** Distinct teachers, sorted. */
    public List<String> teachers() {
        Set<String> seen = new LinkedHashSet<>();
        for (Lesson l : lessons) if (!l.teacher.isEmpty()) seen.add(l.teacher);
        List<String> out = new ArrayList<>(seen);
        Collections.sort(out);
        return out;
    }

}
