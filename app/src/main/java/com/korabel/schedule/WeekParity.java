package com.korabel.schedule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which calendar weeks are верхняя and which are нижняя.
 *
 * Источников два, в порядке доверия:
 *
 * <ol>
 *   <li><b>Строка самого сайта</b> «Сегодня: 20 Сентября 2026 года,
 *       Воскресенье, верхняя неделя» ({@link #fromSite}). Её пишет
 *       университет, поэтому она переживает и праздники, и сдвиги цикла между
 *       семестрами. С 15.09.2026 это единственный машиночитаемый признак на
 *       странице.</li>
 *   <li><b>Даты самих занятий</b> ({@link #derive}) — так считалось раньше,
 *       когда каждая строка расписания несла список дат проведения. Эти данные
 *       остались в сохранённом кэше, поэтому путь сохранён.</li>
 * </ol>
 *
 * При выводе из дат якорем берётся неделя с самым уверенным большинством;
 * {@link #agreement()} показывает, насколько остальной семестр с ней согласен —
 * по этому числу интерфейс решает, доверять ли плашке.
 */
public final class WeekParity {

    /** Used only when there is no schedule at all: the week of 2026-08-31 was верхняя. */
    private static final long FALLBACK_MONDAY = Dates.toEpochDay(2026, 8, 31);

    private final long anchorMonday;
    private final boolean anchorUpper;
    private final double agreement;
    private final int weeks;

    public WeekParity(long anchorMonday, boolean anchorUpper) {
        this(anchorMonday, anchorUpper, 1.0, 0);
    }

    private WeekParity(long anchorMonday, boolean anchorUpper, double agreement, int weeks) {
        this.anchorMonday = Dates.monday(anchorMonday);
        this.anchorUpper = anchorUpper;
        this.agreement = agreement;
        this.weeks = weeks;
    }

    public static WeekParity fallback() {
        return new WeekParity(FALLBACK_MONDAY, true, 0.0, 0);
    }

    /**
     * Якорь со страницы: «сегодня такое-то число, неделя верхняя».
     *
     * Сайт знает чётность точно, спорить с ним не о чем — согласие 1.0.
     */
    public static WeekParity fromSite(long day, boolean upper) {
        return new WeekParity(day, upper, 1.0, 1);
    }

    /**
     * Derive the parity cycle from the lessons' own dates. Falls back to the
     * constant anchor when the schedule carries no dated lessons.
     */
    public static WeekParity derive(List<Lesson> lessons) {
        Map<Long, int[]> votes = new HashMap<>();     // monday -> {верхняя, нижняя}
        for (Lesson l : lessons) {
            for (long day : l.days) {
                int[] v = votes.get(Dates.monday(day));
                if (v == null) votes.put(Dates.monday(day), v = new int[2]);
                v[l.upper ? 0 : 1]++;
            }
        }
        if (votes.isEmpty()) return fallback();

        long bestMonday = 0;
        int bestMargin = Integer.MIN_VALUE;
        boolean bestUpper = true;
        for (Map.Entry<Long, int[]> e : votes.entrySet()) {
            int[] v = e.getValue();
            int margin = Math.abs(v[0] - v[1]);
            if (margin > bestMargin || (margin == bestMargin && e.getKey() < bestMonday)) {
                bestMargin = margin;
                bestMonday = e.getKey();
                bestUpper = v[0] >= v[1];
            }
        }

        WeekParity candidate = new WeekParity(bestMonday, bestUpper);
        int agree = 0, total = 0;
        for (Map.Entry<Long, int[]> e : votes.entrySet()) {
            int[] v = e.getValue();
            if (v[0] == v[1]) continue;               // a week with no majority says nothing
            total++;
            if (candidate.isUpper(e.getKey()) == (v[0] > v[1])) agree++;
        }
        double ratio = total == 0 ? 1.0 : (double) agree / total;
        return new WeekParity(bestMonday, bestUpper, ratio, votes.size());
    }

    /** Is the week containing that day верхняя? */
    public boolean isUpper(long epochDay) {
        long weeksApart = (Dates.monday(epochDay) - anchorMonday) / 7;
        return (Math.floorMod(weeksApart, 2) == 0) == anchorUpper;
    }

    /** Понедельник недели, от которой отсчитывается цикл. */
    public long anchorMonday() {
        return anchorMonday;
    }

    /** Share of dated weeks that agree with the anchor (1.0 = a perfect cycle). */
    public double agreement() {
        return agreement;
    }

    /** True when derived from real data rather than the constant fallback. */
    public boolean isDerived() {
        return weeks > 0;
    }

    @Override public String toString() {
        return "anchor " + Dates.formatRu(anchorMonday) + (anchorUpper ? " верхняя" : " нижняя")
                + ", weeks=" + weeks + ", agreement=" + agreement;
    }
}
