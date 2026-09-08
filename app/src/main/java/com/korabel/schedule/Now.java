package com.korabel.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ответ на вопрос «что сейчас»: идёт ли пара и сколько до её конца, а если нет
 * — какая следующая и через сколько. Один и тот же расчёт использует и строка
 * обратного отсчёта в приложении, и все виджеты, поэтому они не могут разойтись.
 *
 * Класс намеренно ничего не знает об Android: это чистая функция от расписания
 * и момента времени, поэтому покрывается обычными JVM-тестами. Настройки и кэш
 * подставляет {@link Widgets#state}.
 */
public final class Now {

    /** Ничего не идёт и впереди на неделю ничего нет (каникулы, нет группы). */
    public static final int NONE = 0;
    /** Пара идёт прямо сейчас. */
    public static final int ONGOING = 1;
    /** Перемена или время до первой пары — следующая пара сегодня. */
    public static final int BREAK = 2;
    /** Учебный день кончился: ближайшая пара в другой день. */
    public static final int AFTER = 3;

    private static final int DAY_START_MIN = 8 * 60;   // с чего считать «утро» для кольца
    private static final int SEC_PER_DAY = 24 * 3600;

    public static final class State {
        public int kind = NONE;
        /** Идущая пара (ONGOING) — иначе null. */
        public Lesson current;
        /** Ближайшая пара (BREAK/AFTER) — иначе null. */
        public Lesson next;
        /** День, на который приходится {@link #next}; для ONGOING — сегодня. */
        public long nextDay = Dates.NO_DATE;
        /** Сегодняшний день и момент внутри него. */
        public long day;
        public int nowSec;
        /** Сколько осталось: до конца идущей пары или до начала следующей. */
        public int remainSec;
        /** Длина промежутка, который иллюстрирует кольцо виджета (BREAK). */
        public int gapSec;
        /** Занятия сегодня, по времени начала. */
        public List<Lesson> today = new ArrayList<>();
        /** Сохранённая группа — null, если её ещё не выбрали. */
        public String groupId;
        public String groupName;
        /** Расписание, из которого всё посчитано. */
        public Schedule schedule = Schedule.EMPTY;

        /** Пара, о которой сейчас идёт речь. */
        public Lesson lesson() {
            return kind == ONGOING ? current : next;
        }

        public boolean isTomorrow() {
            return nextDay == day + 1;
        }
    }

    private Now() { }

    /** Чистый расчёт: расписание + «сегодня» + секунда суток. */
    public static State compute(Schedule s, long day, int nowSec) {
        State st = new State();
        st.schedule = s;
        st.day = day;
        st.nowSec = nowSec;
        st.today.addAll(s.on(day));
        if (s.isEmpty()) return st;

        int nowMin = nowSec / 60;

        Lesson running = s.runningAt(day, nowMin);
        if (running != null) {
            st.kind = ONGOING;
            st.current = running;
            st.nextDay = day;
            st.remainSec = running.endMinutes() * 60 - nowSec;
            st.gapSec = Math.max(60, (running.endMinutes() - running.startMinutes()) * 60);
            return st;
        }

        Lesson next = s.nextAfter(day, nowMin);
        if (next != null) {
            st.kind = BREAK;
            st.next = next;
            st.nextDay = day;
            st.remainSec = next.startMinutes() * 60 - nowSec;
            // кольцо перемены заполняется от конца прошлой пары (или от утра)
            int since = DAY_START_MIN;
            for (Lesson l : st.today) {
                int end = l.endMinutes();
                if (end > 0 && end <= nowMin && end > since) since = end;
            }
            st.gapSec = Math.max(60, (next.startMinutes() - since) * 60);
            return st;
        }

        long ahead = s.nextDayWithLessons(day + 1, +1);
        if (ahead != Dates.NO_DATE && ahead - day <= 7) {
            List<Lesson> of = s.on(ahead);
            if (!of.isEmpty()) {
                st.kind = AFTER;
                st.next = of.get(0);
                st.nextDay = ahead;
                st.remainSec = (int) ((ahead - day) * SEC_PER_DAY)
                        + of.get(0).startMinutes() * 60 - nowSec;
                st.gapSec = Math.max(60, st.remainSec);
            }
        }
        return st;
    }

    // --------------------------------------------------------------- вывод

    /** Обратный отсчёт с секундами: «23:45», при часах — «4:51:23». */
    public static String hms(int sec) {
        if (sec <= 0) return "0:00";
        int h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                     : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** Округлённо и словами: «2 ч 15 мин», «25 мин» — для виджетов. */
    public static String human(int sec) {
        int m = Math.max(0, (sec + 59) / 60);
        if (m < 60) return m + " мин";
        int h = m / 60, rest = m % 60;
        return rest > 0 ? h + " ч " + rest + " мин" : h + " ч";
    }

    /** «08:30 - 10:00» → «08:30–10:00». */
    public static String compactTime(String time) {
        return time.replace(" ", "").replace("-", "–");
    }

    /** «08:30 - 10:00» → «08:30». */
    public static String startOf(String time) {
        int dash = time.indexOf('-');
        return (dash < 0 ? time : time.substring(0, dash)).trim();
    }

    /** «167 Корпус У» → «167»; «Актовый зал Корпус У» → «Актовый зал». */
    public static String roomShort(String room) {
        if (room == null || room.isEmpty()) return "";
        int at = room.indexOf("Корпус");
        String head = (at > 0 ? room.substring(0, at) : room).trim();
        return head.isEmpty() ? room.trim() : head;
    }

    /** Заголовок дня для виджета: «ЗАВТРА», «СРЕДА». */
    public static String dayLabel(State st) {
        if (st.isTomorrow()) return "ЗАВТРА";
        int index = Dates.dayOfWeek(st.nextDay);
        return Dates.DAY_FULL[index].toUpperCase(Dates.RU);
    }
}
