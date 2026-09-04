package com.korabel.schedule;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Calendar arithmetic on epoch days (days since 1970-01-01), with Russian
 * names baked in.
 *
 * Epoch days instead of {@link Calendar} everywhere: schedule matching is pure
 * date logic, and millisecond arithmetic silently breaks on DST shifts and on
 * phones set to a different time zone. Russian names are hard-coded rather than
 * taken from a Locale so the app reads the same on a phone set to English —
 * and so weekday names always match the ones the site prints.
 *
 * No Android APIs here: this class is covered by plain JVM unit tests.
 */
public final class Dates {

    /** 0 = Monday .. 6 = Sunday, matching the site's day headers. */
    public static final String[] DAY_FULL = {
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    };
    public static final String[] DAY_SHORT = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private static final String[] MONTH_GEN = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };
    public static final long NO_DATE = Long.MIN_VALUE;

    private Dates() { }

    // ------------------------------------------------------------- conversion

    /** Days since 1970-01-01 for a proleptic Gregorian y-m-d (m: 1..12). */
    public static long toEpochDay(int year, int month, int day) {
        long y = year, m = month, total = 0;
        total += 365 * y;
        if (y >= 0) total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        else total -= y / -4 - y / -100 + y / -400;
        total += (367 * m - 362) / 12;
        total += day - 1;
        if (m > 2) {
            total--;
            if (!isLeap(year)) total--;
        }
        return total - 719528L;
    }

    /** Inverse of {@link #toEpochDay}: returns {year, month, day}. */
    public static int[] fromEpochDay(long epochDay) {
        long zeroDay = epochDay + 719528L - 60L; // shift the epoch to 0000-03-01
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / 146097L - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * 146097L;
        }
        long yearEst = (400 * zeroDay + 591) / 146097L;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;
        int marchDoy = (int) doyEst;
        int marchMonth = (marchDoy * 5 + 2) / 153;
        int month = (marchMonth + 2) % 12 + 1;
        int day = marchDoy - (marchMonth * 306 + 5) / 10 + 1;
        yearEst += marchMonth / 10;
        return new int[]{(int) yearEst, month, day};
    }

    public static boolean isLeap(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    /** 0 = Monday .. 6 = Sunday. 1970-01-01 was a Thursday. */
    public static int dayOfWeek(long epochDay) {
        return (int) Math.floorMod(epochDay + 3, 7);
    }

    /** Epoch day of the Monday of that date's week. */
    public static long monday(long epochDay) {
        return epochDay - dayOfWeek(epochDay);
    }

    /** Today in the device's time zone. */
    public static long today() {
        Calendar c = Calendar.getInstance();
        return toEpochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Minutes since midnight, device time zone. */
    public static int nowMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** Milliseconds at local midnight of an epoch day (for calendar intents). */
    public static long startOfDayMillis(long epochDay, int minutes) {
        int[] ymd = fromEpochDay(epochDay);
        Calendar c = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2], minutes / 60, minutes % 60, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---------------------------------------------------------------- parsing

    /** "14.09.2026" -> epoch day, or {@link #NO_DATE} if it is not a date. */
    public static long parseRu(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.length() != 10) return NO_DATE;
        try {
            if (ddMMyyyy.charAt(2) != '.' || ddMMyyyy.charAt(5) != '.') return NO_DATE;
            int d = Integer.parseInt(ddMMyyyy.substring(0, 2));
            int m = Integer.parseInt(ddMMyyyy.substring(3, 5));
            int y = Integer.parseInt(ddMMyyyy.substring(6, 10));
            if (d < 1 || d > 31 || m < 1 || m > 12 || y < 1900 || y > 2200) return NO_DATE;
            return toEpochDay(y, m, d);
        } catch (NumberFormatException e) {
            return NO_DATE;
        }
    }

    /** "HH:MM" -> minutes since midnight, or -1. */
    public static int parseTime(String hhmm) {
        if (hhmm == null || hhmm.length() < 5 || hhmm.charAt(2) != ':') return -1;
        try {
            int h = Integer.parseInt(hhmm.substring(0, 2));
            int m = Integer.parseInt(hhmm.substring(3, 5));
            if (h > 23 || m > 59) return -1;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // -------------------------------------------------------------- rendering

    /** "14.09.2026" */
    public static String formatRu(long epochDay) {
        int[] p = fromEpochDay(epochDay);
        return two(p[2]) + "." + two(p[1]) + "." + p[0];
    }

    /** "14 сентября" */
    public static String dayMonth(long epochDay) {
        int[] p = fromEpochDay(epochDay);
        return p[2] + " " + MONTH_GEN[p[1] - 1];
    }

    /** "14 сентября 2026" */
    public static String dayMonthYear(long epochDay) {
        int[] p = fromEpochDay(epochDay);
        return p[2] + " " + MONTH_GEN[p[1] - 1] + " " + p[0];
    }

    /** "Понедельник, 14 сентября" */
    public static String weekdayDayMonth(long epochDay) {
        return DAY_FULL[dayOfWeek(epochDay)] + ", " + dayMonth(epochDay);
    }

    /** "14–20 сентября" / "28 сентября – 4 октября" / "28 декабря 2026 – 3 января 2027" */
    public static String range(long fromDay, long toDay) {
        int[] a = fromEpochDay(fromDay), b = fromEpochDay(toDay);
        if (a[0] != b[0]) return dayMonthYear(fromDay) + " – " + dayMonthYear(toDay);
        if (a[1] != b[1]) return dayMonth(fromDay) + " – " + dayMonth(toDay);
        return a[2] + "–" + b[2] + " " + MONTH_GEN[b[1] - 1];
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
