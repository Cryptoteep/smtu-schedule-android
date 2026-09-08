package com.korabel.schedule;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * 2×3: блок «сейчас» с тонкой полосой обратного отсчёта и список ближайших пар.
 * Когда учебный день кончился — то же самое, но на следующий учебный день, и в
 * заголовке написано, на какой именно.
 */
public final class AgendaWidget extends AppWidgetProvider {

    private static final int MAX_ROWS = 4;
    private static final int[] ROW = {R.id.wa_row1, R.id.wa_row2, R.id.wa_row3, R.id.wa_row4};
    private static final int[] TIME = {R.id.wa_r1_time, R.id.wa_r2_time,
                                       R.id.wa_r3_time, R.id.wa_r4_time};
    private static final int[] SUBJECT = {R.id.wa_r1_subject, R.id.wa_r2_subject,
                                          R.id.wa_r3_subject, R.id.wa_r4_subject};
    private static final int[] ROOM = {R.id.wa_r1_room, R.id.wa_r2_room,
                                       R.id.wa_r3_room, R.id.wa_r4_room};

    @Override public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        Widgets.poke(ctx);
    }

    @Override public void onDisabled(Context ctx) {
        Widgets.schedule(ctx);
    }

    static RemoteViews render(Context app, Now.State st) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget_agenda);
        rv.setOnClickPendingIntent(R.id.wa_root, Widgets.open(app));
        rv.setTextViewText(R.id.wa_group, st.groupName == null ? "" : st.groupName);

        if (st.kind == Now.NONE) {
            rv.setTextViewText(R.id.wa_header, st.groupId == null ? "НЕТ ГРУППЫ" : "ПАР НЕТ");
            nowBlock(rv, false);
            rv.setTextViewText(R.id.wa_next_header, "");
            rv.setViewVisibility(R.id.wa_next_header, View.GONE);
            rows(rv, new ArrayList<>());
            hint(rv, st.groupId == null ? "Откройте приложение и выберите группу"
                                        : "На ближайшую неделю занятий нет");
            return rv;
        }

        List<Lesson> upcoming;
        if (st.kind == Now.ONGOING) {
            rv.setTextViewText(R.id.wa_header, "СЕЙЧАС");
            rv.setTextViewText(R.id.wa_subject, st.current.subject);
            rv.setTextViewText(R.id.wa_meta, meta(st.current, "осталось "
                    + Now.human(st.remainSec)));
            rv.setInt(R.id.wa_bar, "setProgress", Widgets.percent(st.remainSec, st.gapSec));
            nowBlock(rv, true);
            upcoming = laterThan(st.today, st.current.endMinutes());
        } else if (st.kind == Now.BREAK) {
            rv.setTextViewText(R.id.wa_header, "ЧЕРЕЗ " + Now.human(st.remainSec).toUpperCase(Dates.RU));
            nowBlock(rv, false);
            upcoming = laterThan(st.today, st.nowSec / 60);
        } else {
            rv.setTextViewText(R.id.wa_header, Now.dayLabel(st));
            nowBlock(rv, false);
            upcoming = st.schedule.on(st.nextDay);
        }

        rv.setViewVisibility(R.id.wa_next_header, upcoming.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setTextViewText(R.id.wa_next_header, st.kind == Now.ONGOING ? "ДАЛЕЕ" : "");
        rows(rv, upcoming);
        hint(rv, upcoming.isEmpty() && st.kind == Now.ONGOING ? "Это последняя пара" : null);
        return rv;
    }

    /** «08:30–10:00 · 167 · осталось 25 мин» */
    private static String meta(Lesson lesson, String tail) {
        StringBuilder b = new StringBuilder(Now.compactTime(lesson.time));
        String room = Now.roomShort(lesson.room);
        if (!room.isEmpty()) b.append(" · ").append(room);
        if (tail != null && !tail.isEmpty()) b.append(" · ").append(tail);
        return b.toString();
    }

    private static List<Lesson> laterThan(List<Lesson> today, int minutes) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson l : today) if (l.startMinutes() >= minutes) out.add(l);
        return out;
    }

    private static void nowBlock(RemoteViews rv, boolean show) {
        int v = show ? View.VISIBLE : View.GONE;
        rv.setViewVisibility(R.id.wa_subject, v);
        rv.setViewVisibility(R.id.wa_meta, v);
        rv.setViewVisibility(R.id.wa_bar, v);
    }

    private static void rows(RemoteViews rv, List<Lesson> list) {
        for (int i = 0; i < MAX_ROWS; i++) {
            boolean has = i < list.size();
            rv.setViewVisibility(ROW[i], has ? View.VISIBLE : View.GONE);
            if (!has) continue;
            Lesson l = list.get(i);
            rv.setTextViewText(TIME[i], Now.startOf(l.time));
            rv.setTextViewText(SUBJECT[i], l.subject);
            rv.setTextViewText(ROOM[i], Now.roomShort(l.room));
        }
    }

    private static void hint(RemoteViews rv, String text) {
        rv.setViewVisibility(R.id.wa_empty, text == null ? View.GONE : View.VISIBLE);
        if (text != null) rv.setTextViewText(R.id.wa_empty, text);
    }
}
