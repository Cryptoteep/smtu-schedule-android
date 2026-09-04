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
    private final long firstDay, lastDay;

    public Schedule(List<Lesson> lessons, String title, long fetchedAt) {
        List<Lesson> copy = new ArrayList<>(lessons);
        Collections.sort(copy);
        this.lessons = Collections.unmodifiableList(copy);
        this.parity = WeekParity.derive(copy);
        this.title = title == null ? "" : title;
        this.fetchedAt = fetchedAt;

        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (Lesson l : copy)
            for (long d : l.days) {
                if (d < min) min = d;
                if (d > max) max = d;
            }
        this.firstDay = min == Long.MAX_VALUE ? Dates.NO_DATE : min;
        this.lastDay = max == Long.MIN_VALUE ? Dates.NO_DATE : max;
    }

    // ------------------------------------------------------------- accessors

    public List<Lesson> lessons()  { return lessons; }
    public WeekParity parity()     { return parity; }
    public String title()          { return title; }
    public long fetchedAt()        { return fetchedAt; }
    public boolean isEmpty()       { return lessons.isEmpty(); }
    public int size()              { return lessons.size(); }

    /** First and last dated day of the semester, or {@link Dates#NO_DATE}. */
    public long firstDay()         { return firstDay; }
    public long lastDay()          { return lastDay; }

    public boolean isUpper(long epochDay) {
        return parity.isUpper(epochDay);
    }

    /** Is that day inside the semester the page covers? */
    public boolean inSemester(long epochDay) {
        return firstDay != Dates.NO_DATE && epochDay >= firstDay && epochDay <= lastDay;
    }

    // --------------------------------------------------------------- queries

    /** Lessons of one calendar day, ordered by start time. */
    public List<Lesson> on(long epochDay) {
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
        if (firstDay == Dates.NO_DATE) return Dates.NO_DATE;
        for (int i = 0; i <= 400; i++) {
            long d = from + (long) direction * i;
            if (d < firstDay - 7 || d > lastDay + 7) break;
            if (!on(d).isEmpty()) return d;
        }
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

    /**
     * Merge a freshly fetched schedule into this one: fresh rows win, rows that
     * vanished from the site are kept (a lesson that already happened stays
     * visible in history), and the merged result keeps the fresh title.
     */
    public Schedule mergedWith(Schedule fresh) {
        if (fresh == null || fresh.isEmpty()) return this;
        List<Lesson> out = new ArrayList<>(fresh.lessons);
        Set<String> freshKeys = new LinkedHashSet<>();
        for (Lesson l : fresh.lessons) freshKeys.add(l.key());
        for (Lesson l : lessons) if (!freshKeys.contains(l.key())) out.add(l);
        return new Schedule(out, fresh.title.isEmpty() ? title : fresh.title, fresh.fetchedAt);
    }
}
