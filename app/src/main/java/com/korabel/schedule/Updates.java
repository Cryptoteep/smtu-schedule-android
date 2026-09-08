package com.korabel.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * Тихая проверка новой версии.
 *
 * Приложение раздаётся APK-файлом, магазина за спиной нет — значит, узнать об
 * обновлении человеку неоткуда, если ему не сказать. Проверка ходит на
 * {@code latest.json} рядом с веб-версией: это тот же GitHub Pages, что и срез
 * расписания, без лимитов GitHub API и без учётных записей.
 *
 * О новой версии сообщается ненавязчиво: не чаще раза в сутки и не более трёх
 * напоминаний на версию, дальше — только в диалоге «О приложении». Ничего
 * никогда не скачивается и не ставится само.
 */
public final class Updates {

    public static final String REPO = "https://github.com/Cryptoteep/smtu-schedule-android";
    public static final String RELEASES = REPO + "/releases/latest";
    private static final String FEED = Mirror.SITE + "/latest.json";

    private static final String P_VERSION = "updVersion";
    private static final String P_SHOWN = "updShown";
    private static final String P_CHECKED = "updChecked";
    private static final int MAX_REMINDERS = 3;
    private static final long CHECK_EVERY_MS = 24 * 3600_000L;

    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** Результат проверки: null-версия означает «не удалось проверить». */
    public interface Result {
        void done(String version, boolean newer);
    }

    private Updates() { }

    /**
     * Проверка при запуске. Возвращает через {@code onNewer} версию, о которой
     * стоит сказать вслух, — но не чаще, чем описано в шапке класса.
     */
    public static void onLaunch(Context ctx, Result onNewer) {
        final Context app = ctx.getApplicationContext();
        final SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        if (now - p.getLong(P_CHECKED, 0) < CHECK_EVERY_MS) return;
        p.edit().putLong(P_CHECKED, now).apply();

        new Thread(() -> {
            String latest = fetch();
            if (latest == null) return;
            boolean newer = isNewer(latest, BuildConfig.VERSION_NAME);
            int shown = latest.equals(p.getString(P_VERSION, "")) ? p.getInt(P_SHOWN, 0) : 0;
            p.edit().putString(P_VERSION, latest)
                    .putInt(P_SHOWN, newer ? shown + 1 : 0).apply();
            if (newer && shown < MAX_REMINDERS) UI.post(() -> onNewer.done(latest, true));
        }, "smtu-updates").start();
    }

    /** Проверка по кнопке: сообщает результат всегда, даже когда всё свежее. */
    public static void checkNow(Context ctx, Result cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            String latest = fetch();
            if (latest != null) {
                prefs(app).edit().putString(P_VERSION, latest)
                        .putLong(P_CHECKED, System.currentTimeMillis()).apply();
            }
            final boolean newer = latest != null && isNewer(latest, BuildConfig.VERSION_NAME);
            UI.post(() -> cb.done(latest, newer));
        }, "smtu-updates").start();
    }

    /** Последняя версия, которую удавалось увидеть, или null. */
    public static String latestKnown(Context ctx) {
        String v = prefs(ctx).getString(P_VERSION, "");
        return v.isEmpty() ? null : v;
    }

    private static String fetch() {
        try {
            String v = new JSONObject(Smtu.httpGet(FEED)).optString("version", "");
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /** Больше ли {@code a}, чем {@code b}; см. {@link Version}. */
    public static boolean isNewer(String a, String b) {
        return Version.isNewer(a, b);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("sched", Context.MODE_PRIVATE);
    }
}
