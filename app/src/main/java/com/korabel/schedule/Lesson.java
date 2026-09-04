package com.korabel.schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One row of a schedule: a subject in a time slot on a weekday, with the exact
 * dates it actually happens on.
 *
 * The site gives every row a `title` attribute listing every occurrence date,
 * so a lesson knows its own dates and the UI never has to guess. Rows that lack
 * that list fall back to weekday + week parity.
 *
 * Plain Java (no Android, no JSON) so it can be unit-tested on the JVM.
 */
public final class Lesson implements Comparable<Lesson> {

    public String day = "";        // Понедельник..Воскресенье, as printed by the site
    public String time = "";       // "08:30 - 10:00"
    public boolean upper;          // js-week-1 = верхняя, js-week-2 = нижняя
    public String subject = "";
    public String type = "";       // Лекция / Практическое занятие / Лабораторная работа / ...
    public String room = "";       // "167 Корпус У"
    public String teacher = "";
    public String teacherId = "";  // viewperson id, "" when the site has no card for them
    public String group = "";      // "12826-11" — the only place a teacher's page names the group
    public String note = "";       // "С 26.10 по 14.12" and similar free-form remarks
    public String dateRange = "";  // "14 сентября — 21 декабря 2026"
    public List<Long> days = new ArrayList<>();   // exact occurrences, epoch days, sorted

    private int startMin = -2, endMin = -2;       // lazily parsed from `time`

    // ------------------------------------------------------------------ time

    /** Minutes since midnight when the lesson starts, or -1. */
    public int startMinutes() {
        if (startMin == -2) parseTime();
        return startMin;
    }

    /** Minutes since midnight when the lesson ends, or -1. */
    public int endMinutes() {
        if (endMin == -2) parseTime();
        return endMin;
    }

    private void parseTime() {
        int dash = time.indexOf('-');
        startMin = Dates.parseTime(time.trim());
        endMin = dash < 0 ? -1 : Dates.parseTime(time.substring(dash + 1).trim());
    }

    /** Weekday index of {@link #day} (0 = Monday), or -1 when unknown. */
    public int weekdayIndex() {
        for (int i = 0; i < Dates.DAY_FULL.length; i++)
            if (Dates.DAY_FULL[i].equalsIgnoreCase(day)) return i;
        return -1;
    }

    /** Does this lesson happen on that day? Exact dates first, parity as fallback. */
    public boolean happensOn(long epochDay, boolean upperWeek) {
        if (!days.isEmpty()) return days.contains(epochDay);
        return upper == upperWeek && weekdayIndex() == Dates.dayOfWeek(epochDay);
    }

    // ---------------------------------------------------------------- identity

    /**
     * Stable identity for cache merging: everything the site could print about
     * the slot except the date list, which may legitimately grow or shrink when
     * the university edits the page.
     */
    public String key() {
        return day + '|' + time + '|' + upper + '|' + subject + '|' + type + '|'
                + room + '|' + teacher + '|' + group;
    }

    /** Human one-liner used by list dialogs and the share/export text. */
    public String oneLine() {
        return oneLine(true);
    }

    /** Same, but the subject can be dropped when the dialog title already names it. */
    public String oneLine(boolean withSubject) {
        StringBuilder b = new StringBuilder();
        b.append(time.replace(" - ", " – "));
        if (withSubject) b.append("  ").append(subject);
        if (!type.isEmpty()) b.append(withSubject ? " (" + type + ")" : "  " + type);
        if (!room.isEmpty()) b.append(" · ").append(room);
        if (!group.isEmpty()) b.append(" · ").append(group);
        if (!teacher.isEmpty()) b.append(" · ").append(teacher);
        return b.toString();
    }

    /** Sort by first occurrence, then weekday, then start time, then subject. */
    @Override public int compareTo(Lesson o) {
        int a = weekdayIndex(), b = o.weekdayIndex();
        if (a != b) return Integer.compare(a < 0 ? 9 : a, b < 0 ? 9 : b);
        int c = Integer.compare(startMinutes(), o.startMinutes());
        if (c != 0) return c;
        c = Boolean.compare(!upper, !o.upper);
        if (c != 0) return c;
        c = subject.compareTo(o.subject);
        if (c != 0) return c;
        return group.compareTo(o.group);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Lesson && key().equals(((Lesson) o).key())
                && days.equals(((Lesson) o).days);
    }

    @Override public int hashCode() {
        return Arrays.hashCode(new Object[]{key(), days});
    }

    @Override public String toString() {
        return day + " " + time + " " + subject + (days.isEmpty() ? "" : " " + days.size() + "×");
    }
}
