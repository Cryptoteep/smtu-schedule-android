package com.korabel.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import java.io.File;

/**
 * Общая часть трёх виджетов: перерисовка и один самозаводящийся будильник.
 *
 * Виджет живёт без процесса приложения, поэтому обновлять его должен не таймер
 * внутри Activity, а AlarmManager. Будильник ставится не «раз в минуту всегда»,
 * а на ближайшее осмысленное событие: пока идёт пара или вот-вот начнётся —
 * каждую минуту, иначе — на момент, когда картинка действительно изменится
 * (начало следующей пары или полночь). Так виджет не будит телефон зря.
 *
 * Будильник неточный ({@code setAndAllowWhileIdle}): точные потребовали бы
 * разрешения уровня будильника, а расписание — не будильник. Цена — минута
 * запаздывания в глубоком сне; приложение при открытии перерисовывает виджеты
 * само.
 */
public final class Widgets {

    /** Перезапросить расписание, если кэшу больше стольких часов. */
    private static final long STALE_MS = 12 * 3600_000L;
    /** Не чаще раза в час, даже если сеть недоступна и кэш так и остаётся старым. */
    private static final long FETCH_THROTTLE_MS = 3600_000L;
    private static final String P_LAST_FETCH = "widgetFetch";

    private Widgets() { }

    /** Настройки + кэш + расчёт: единственный путь данных виджетов. */
    public static Now.State state(Context ctx) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("sched", Context.MODE_PRIVATE);
        String gid = p.getString("groupId", null);
        Schedule s = gid == null ? Schedule.EMPTY : Smtu.cached(app, false, gid);
        Now.State st = Now.compute(s, Dates.today(), Dates.nowSeconds());
        st.groupId = gid;
        st.groupName = s.title().isEmpty() ? p.getString("groupName", gid) : s.title();
        return st;
    }

    /** Перерисовать всё и переставить будильник. */
    public static void poke(Context ctx) {
        render(ctx);
        schedule(ctx);
    }

    /** Разослать свежие RemoteViews во все поставленные виджеты. */
    public static void render(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager awm = AppWidgetManager.getInstance(app);
        if (awm == null) return;
        Now.State st = state(app);
        int painted = 0;
        painted += push(awm, app, TimerWidget.class, TimerWidget.render(app, st));
        painted += push(awm, app, NowWidget.class, NowWidget.render(app, st));
        painted += push(awm, app, AgendaWidget.class, AgendaWidget.render(app, st));
        if (painted > 0) maybeRefresh(app, st);
    }

    private static int push(AppWidgetManager awm, Context app, Class<?> provider, RemoteViews rv) {
        int[] ids = awm.getAppWidgetIds(new ComponentName(app, provider));
        for (int id : ids) awm.updateAppWidget(id, rv);
        return ids.length;
    }

    /** Есть ли на экранах хоть один наш виджет. */
    public static boolean any(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager awm = AppWidgetManager.getInstance(app);
        if (awm == null) return false;
        return awm.getAppWidgetIds(new ComponentName(app, TimerWidget.class)).length
                + awm.getAppWidgetIds(new ComponentName(app, NowWidget.class)).length
                + awm.getAppWidgetIds(new ComponentName(app, AgendaWidget.class)).length > 0;
    }

    /** Догрузить расписание в фоне, если кэш устарел (не чаще раза в час). */
    private static void maybeRefresh(final Context app, Now.State st) {
        if (st.groupId == null) return;
        SharedPreferences p = app.getSharedPreferences("sched", Context.MODE_PRIVATE);
        File cache = new File(app.getFilesDir(), "g_" + st.groupId + ".json");
        long now = System.currentTimeMillis();
        if (cache.exists() && now - cache.lastModified() < STALE_MS) return;
        if (now - p.getLong(P_LAST_FETCH, 0) < FETCH_THROTTLE_MS) return;
        p.edit().putLong(P_LAST_FETCH, now).apply();
        Smtu.schedule(app, false, st.groupId, (result, error) -> {
            if (result != null && !result.isEmpty()) poke(app);
        });
    }

    /** Поставить будильник на ближайший момент, когда картинка изменится. */
    public static void schedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = tick(app);
        if (!any(app)) {
            am.cancel(pi);
            return;
        }

        Now.State st = state(app);
        long now = System.currentTimeMillis();
        long at;
        switch (st.kind) {
            case Now.ONGOING:
                at = now + 60_000L;
                break;
            case Now.BREAK:
                // близко к началу — тикаем каждую минуту, далеко — спим до него
                at = st.remainSec <= 90 * 60 ? now + 60_000L : now + st.remainSec * 1000L;
                break;
            case Now.AFTER:
                at = Dates.startOfDayMillis(st.nextDay, st.next.startMinutes());
                break;
            default:
                at = midnight(st.day);
                break;
        }
        // полночь всегда меняет подписи («завтра» → «сегодня»)
        at = Math.min(at, midnight(st.day));
        at = Math.max(at, now + 15_000L);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
    }

    /** Снять будильник — последний виджет убрали с экрана. */
    public static void stop(Context ctx) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tick(app));
    }

    private static long midnight(long day) {
        return Dates.startOfDayMillis(day + 1, 1);
    }

    private static PendingIntent tick(Context app) {
        return PendingIntent.getBroadcast(app, 0, new Intent(app, WidgetTick.class),
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());
    }

    /** Открыть приложение по тапу на виджет. */
    static PendingIntent open(Context app) {
        Intent intent = new Intent(app, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());
    }

    private static int flagImmutable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    /**
     * Попросить лаунчер поставить виджет — так его не приходится искать в общем
     * списке виджетов системы. Работает с Android 8; ниже — {@code false}, и
     * тогда остаётся обычный длинный тап по домашнему экрану.
     */
    public static boolean pin(Context ctx, Class<?> provider) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        Context app = ctx.getApplicationContext();
        AppWidgetManager awm = AppWidgetManager.getInstance(app);
        if (awm == null || !awm.isRequestPinAppWidgetSupported()) return false;
        return awm.requestPinAppWidget(new ComponentName(app, provider), null, null);
    }

    /** Доля в процентах, зажатая в 0..100. */
    static int percent(int part, int whole) {
        if (whole <= 0) return 0;
        return Math.max(0, Math.min(100, (int) (part * 100L / whole)));
    }

    /** Цвет из ресурсов; {@code getColor(int)} появился только в API 23. */
    @SuppressWarnings("deprecation")
    static int color(Context app, int id) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? app.getColor(id) : app.getResources().getColor(id);
    }
}
