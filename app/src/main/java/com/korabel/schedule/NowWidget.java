package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 1×2: что идёт сейчас или что будет дальше — цветная полоса слева, подпись
 * состояния, предмет, время и строка «до конца 65 мин · ауд. 167».
 *
 * В отличие от кольца, аудитория показывается всегда, когда она известна:
 * именно её обычно и хотят увидеть с домашнего экрана.
 */
public final class NowWidget extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Widgets.poke(ctx);
    }

    @Override public void onDisabled(Context ctx) {
        Widgets.schedule(ctx);
    }

    static RemoteViews render(Context app, Now.State st) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_now);
        rv.setOnClickPendingIntent(R.id.wn_root, Widgets.open(app));

        if (st.kind == Now.NONE) {
            rv.setTextViewText(R.id.wn_label, st.groupId == null ? "НЕТ ГРУППЫ" : "ПАР НЕТ");
            rv.setTextViewText(R.id.wn_subject, st.groupId == null ? "Выберите группу" : "");
            rv.setTextViewText(R.id.wn_time, "");
            rv.setTextViewText(R.id.wn_extra, "");
            stripe(rv, false, false);
            return rv;
        }

        Lesson lesson = st.lesson();
        String label;
        String extra;
        boolean ongoing = st.kind == Now.ONGOING;
        switch (st.kind) {
            case Now.ONGOING:
                label = "СЕЙЧАС";
                extra = "до конца " + Now.human(st.remainSec);
                break;
            case Now.BREAK:
                label = "СЛЕДУЮЩАЯ";
                extra = "через " + Now.human(st.remainSec);
                break;
            default:
                label = Now.dayLabel(st);
                extra = "";
        }
        String room = Now.roomShort(lesson.room);
        if (!room.isEmpty()) extra = extra.isEmpty() ? "ауд. " + room : extra + " · ауд. " + room;

        int accent = Widgets.color(app, ongoing ? R.color.w_lesson : R.color.w_break);
        rv.setTextViewText(R.id.wn_label, label);
        rv.setTextColor(R.id.wn_label, accent);
        rv.setTextViewText(R.id.wn_subject, lesson.subject);
        rv.setTextViewText(R.id.wn_time, Now.compactTime(lesson.time)
                + (lesson.type.isEmpty() ? "" : " · " + lesson.type));
        rv.setTextViewText(R.id.wn_extra, extra);
        rv.setTextColor(R.id.wn_extra,
                Widgets.color(app, st.kind == Now.AFTER ? R.color.w_sub
                        : ongoing ? R.color.w_lesson : R.color.w_break));
        rv.setViewVisibility(R.id.wn_extra, extra.isEmpty() ? View.GONE : View.VISIBLE);
        stripe(rv, ongoing, !ongoing);
        return rv;
    }

    private static void stripe(RemoteViews rv, boolean lesson, boolean brk) {
        rv.setViewVisibility(R.id.wn_stripe_lesson, lesson ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.wn_stripe_break, brk ? View.VISIBLE : View.GONE);
    }
}
