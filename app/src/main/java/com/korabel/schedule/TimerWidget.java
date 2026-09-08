package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 1×1: кольцо обратного отсчёта. Синее убывает, пока идёт пара; жёлтое
 * наполняется, пока идёт перемена; после учебного дня кольца нет, а в центре —
 * время ближайшей пары. Тап открывает приложение.
 */
public final class TimerWidget extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Widgets.poke(ctx);
    }

    @Override public void onDisabled(Context ctx) {
        Widgets.schedule(ctx);      // последний убрали — будильник снимется сам
    }

    static RemoteViews render(Context app, Now.State st) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_timer);
        String main;
        String sub;
        int lesson = View.GONE, brk = View.GONE;

        switch (st.kind) {
            case Now.ONGOING:
                lesson = View.VISIBLE;
                rv.setInt(R.id.wt_ring_lesson, "setProgress",
                        Widgets.percent(st.remainSec, st.gapSec));
                main = String.valueOf(minutes(st.remainSec));
                sub = "мин";
                break;
            case Now.BREAK:
                brk = View.VISIBLE;
                rv.setInt(R.id.wt_ring_break, "setProgress",
                        Widgets.percent(st.gapSec - st.remainSec, st.gapSec));
                if (st.remainSec > 90 * 60) {          // далеко — тикать незачем
                    main = Now.startOf(st.next.time);
                    sub = "начало";
                } else {
                    main = String.valueOf(minutes(st.remainSec));
                    sub = "до пары";
                }
                break;
            case Now.AFTER:
                main = Now.startOf(st.next.time);
                sub = st.isTomorrow() ? "завтра"
                        : Dates.DAY_SHORT[Dates.dayOfWeek(st.nextDay)].toLowerCase(Dates.RU);
                break;
            default:
                main = "—";
                sub = st.groupId == null ? "нет группы" : "нет пар";
        }

        rv.setViewVisibility(R.id.wt_ring_lesson, lesson);
        rv.setViewVisibility(R.id.wt_ring_break, brk);
        rv.setTextViewText(R.id.wt_main, main);
        rv.setTextViewText(R.id.wt_sub, sub);
        rv.setOnClickPendingIntent(R.id.wt_root, Widgets.open(app));
        return rv;
    }

    /** Округление вверх: пока пара идёт, в кольце не должно быть нуля. */
    private static int minutes(int sec) {
        return Math.max(1, (sec + 59) / 60);
    }
}
