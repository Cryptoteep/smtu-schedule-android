package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The whole app: one screen showing a group's (or a teacher's) semester, in a
 * week view or a day view.
 *
 *   header   group/teacher name, date range, week parity, ‹ › navigation
 *   body     day headers + lesson rows; the lesson happening right now is
 *            highlighted, past lessons of today are dimmed
 *   gestures swipe left/right to move a day (day view) or a week (week view)
 *
 * Tapping a lesson opens its details: every date it occurs on, the teacher (whose
 * own full schedule can be opened in place), the subject's other slots, adding it
 * to the phone's calendar, and sharing it as text.
 *
 * Views are built in code — no XML layouts, no AndroidX, no third-party
 * libraries; the release APK stays around 40 KB.
 */
public final class MainActivity extends Activity {

    private static final String PREFS = "sched";
    private static final String PREF_GID = "groupId";
    private static final String PREF_GNAME = "groupName";
    private static final String PREF_DAY_MODE = "dayMode";

    // ------------------------------------------------------------------ state

    /** What the screen currently shows: the saved group, or a teacher opened from a lesson. */
    private String groupId, groupName;
    private String teacherId, teacherName;      // non-null while viewing a teacher
    private Schedule schedule = Schedule.EMPTY;
    private boolean loading;

    private boolean dayMode;
    private long anchorMonday = Dates.monday(Dates.today());
    private int dayOffset = Dates.dayOfWeek(Dates.today());

    // ------------------------------------------------------------------ views

    private Ui ui;
    private TextView tvTitle, tvRange, tvWeek, tvMode, tvToday, tvEmpty, tvStatus;
    private LinearLayout dayStrip;
    private ListView list;
    private ProgressBar progress;

    // ------------------------------------------------------------------ setup

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = new Ui(this);
        setContentView(buildUi());

        SharedPreferences p = prefs();
        groupId = p.getString(PREF_GID, null);
        groupName = p.getString(PREF_GNAME, groupId);
        dayMode = p.getBoolean(PREF_DAY_MODE, false);

        if (saved != null) {
            dayMode = saved.getBoolean("dayMode", dayMode);
            anchorMonday = saved.getLong("anchor", anchorMonday);
            dayOffset = saved.getInt("dayOffset", dayOffset);
            teacherId = saved.getString("teacherId");
            teacherName = saved.getString("teacherName");
        }

        if (groupId == null) showGroupPicker(true);
        else load();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("dayMode", dayMode);
        out.putLong("anchor", anchorMonday);
        out.putInt("dayOffset", dayOffset);
        out.putString("teacherId", teacherId);
        out.putString("teacherName", teacherName);
    }

    @Override protected void onResume() {
        super.onResume();
        render();               // the "now" highlight and "today" marker age quickly
    }

    @Override public void onBackPressed() {
        if (teacherId != null) {
            closeTeacher();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------- data

    private void load() {
        schedule = Smtu.cached(this, false, groupId);
        if (!schedule.title().isEmpty()) groupName = schedule.title();
        render();
        refresh();
    }

    private void refresh() {
        if (groupId == null && teacherId == null) {
            showGroupPicker(true);
            return;
        }
        boolean teacher = teacherId != null;
        String id = teacher ? teacherId : groupId;
        setLoading(true);
        Smtu.schedule(this, teacher, id, (result, error) -> {
            if (isFinishing()) return;
            setLoading(false);
            if (result != null && !result.isEmpty()) {
                schedule = result;
                if (!teacher && !result.title().isEmpty()) {
                    groupName = result.title();
                    prefs().edit().putString(PREF_GNAME, groupName).apply();
                } else if (teacher && !result.title().isEmpty()) {
                    teacherName = result.title();
                }
            }
            render();
            if (error != null) toast(error);
        });
    }

    private void setLoading(boolean on) {
        loading = on;
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
        updateStatus();
    }

    private void openTeacher(Lesson lesson) {
        if (lesson.teacherId.isEmpty()) {
            showLessonList(lesson.teacher, schedule.ofTeacher(lesson.teacher), true);
            return;
        }
        teacherId = lesson.teacherId;
        teacherName = lesson.teacher;
        schedule = Smtu.cached(this, true, teacherId);
        render();
        refresh();
    }

    private void closeTeacher() {
        teacherId = null;
        teacherName = null;
        load();
    }

    // -------------------------------------------------------------------- ui

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(ui.bg);

        LinearLayout header = Ui.column(this);
        header.setBackgroundColor(ui.headerBg);
        header.setPadding(dp(8), dp(10), dp(8), dp(6));
        root.addView(header, Ui.lp(-1, -2));

        // row 1: ‹  title / range  ›
        LinearLayout row1 = Ui.row(this);
        header.addView(row1, Ui.lp(-1, -2));

        TextView prev = ui.iconButton(this, "‹", 26);
        prev.setOnClickListener(v -> shift(-1));
        TextView next = ui.iconButton(this, "›", 26);
        next.setOnClickListener(v -> shift(1));

        LinearLayout titleBox = Ui.column(this);
        titleBox.setPadding(dp(8), 0, dp(8), 0);
        titleBox.setOnClickListener(v -> {
            if (teacherId != null) closeTeacher();
            else showGroupPicker(false);
        });

        tvTitle = new TextView(this);
        tvTitle.setTextSize(19);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(ui.text);
        tvTitle.setSingleLine(true);
        titleBox.addView(tvTitle);

        tvRange = new TextView(this);
        tvRange.setTextSize(13);
        tvRange.setTextColor(ui.muted);
        titleBox.addView(tvRange);

        row1.addView(prev, Ui.lp(dp(40), dp(44)));
        row1.addView(titleBox, Ui.lp(0, -2, 1f));
        row1.addView(next, Ui.lp(dp(40), dp(44)));

        // row 2: mode · parity · today ......... search · refresh · menu
        LinearLayout row2 = Ui.row(this);
        LinearLayout.LayoutParams row2lp = Ui.lp(-1, -2);
        row2lp.topMargin = dp(6);
        header.addView(row2, row2lp);

        tvMode = Ui.pill(this, "", 0x00000000, ui.text);
        tvMode.setTypeface(Typeface.DEFAULT_BOLD);
        tvMode.setBackground(Ui.rounded(ui.dark ? 0x22FFFFFF : 0x14000000, 10, this));
        tvMode.setOnClickListener(v -> setDayMode(!dayMode));
        row2.addView(tvMode, Ui.lp(-2, -2));

        tvWeek = Ui.pill(this, "", ui.upperBadge, Color.WHITE);
        tvWeek.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams weekLp = Ui.lp(-2, -2);
        weekLp.leftMargin = dp(6);
        row2.addView(tvWeek, weekLp);

        tvToday = Ui.pill(this, "Сегодня", ui.dark ? 0x22FFFFFF : 0x14000000, ui.text);
        tvToday.setOnClickListener(v -> goToday());
        LinearLayout.LayoutParams todayLp = Ui.lp(-2, -2);
        todayLp.leftMargin = dp(6);
        row2.addView(tvToday, todayLp);

        row2.addView(new View(this), Ui.lp(0, 1, 1f));

        TextView search = ui.iconButton(this, "⌕", 18);   // ⌕
        search.setOnClickListener(v -> showSearch());
        TextView reload = ui.iconButton(this, "⟳", 18);   // ⟳
        reload.setOnClickListener(v -> refresh());
        TextView menu = ui.iconButton(this, "⋮", 18);     // ⋮
        menu.setOnClickListener(v -> showMenu());
        LinearLayout.LayoutParams btn = Ui.lp(dp(36), dp(32));
        btn.leftMargin = dp(4);
        row2.addView(search, btn);
        row2.addView(reload, Ui.lp(dp(36), dp(32)));
        row2.addView(menu, Ui.lp(dp(36), dp(32)));

        // row 3: Mon..Sun strip (day view only)
        dayStrip = Ui.row(this);
        LinearLayout.LayoutParams stripLp = Ui.lp(-1, -2);
        stripLp.topMargin = dp(6);
        header.addView(dayStrip, stripLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, Ui.lp(-1, dp(3)));

        // body
        FrameLayout body = new FrameLayout(this);
        root.addView(body, Ui.lp(-1, 0, 1f));

        tvEmpty = new TextView(this);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setTextColor(ui.muted);
        tvEmpty.setTextSize(15);
        tvEmpty.setPadding(dp(24), dp(32), dp(24), dp(24));
        body.addView(tvEmpty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(8), dp(8), dp(8), dp(16));
        list.setClipToPadding(false);
        list.setEmptyView(tvEmpty);
        attachSwipe(list);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        tvStatus = new TextView(this);
        tvStatus.setTextSize(11);
        tvStatus.setTextColor(ui.muted);
        tvStatus.setPadding(dp(12), dp(4), dp(12), dp(6));
        root.addView(tvStatus, Ui.lp(-1, -2));

        return root;
    }

    /** Horizontal flings move through the schedule; vertical scrolling is untouched. */
    private void attachSwipe(View target) {
        GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onFling(MotionEvent e1, MotionEvent e2,
                                                     float vx, float vy) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) < dp(64) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                        shift(dx < 0 ? 1 : -1);
                        return true;
                    }
                });
        target.setOnTouchListener((v, event) -> {
            if (!detector.onTouchEvent(event)) return false;
            v.performClick();               // keep talkback and click handling intact
            return true;
        });
    }

    // -------------------------------------------------------------- rendering

    private void render() {
        boolean teacher = teacherId != null;
        tvTitle.setText(teacher ? (teacherName == null ? "Преподаватель" : teacherName)
                                : (groupName == null ? "Группа" : groupName));
        tvMode.setText(dayMode ? "День" : "Неделя");
        tvRange.setText(rangeLabel(teacher));

        long shown = shownDay();
        boolean upper = schedule.isUpper(shown);
        tvWeek.setText(upper ? "ВЕРХНЯЯ" : "НИЖНЯЯ");
        tvWeek.setBackground(Ui.rounded(upper ? ui.upperBadge : ui.lowerBadge, 10, this));
        tvWeek.setVisibility(schedule.isEmpty() ? View.GONE : View.VISIBLE);
        tvToday.setVisibility(isTodayShown() ? View.GONE : View.VISIBLE);

        buildDayStrip();
        list.setAdapter(new RowAdapter(buildRows()));
        tvEmpty.setText(emptyLabel());
        updateStatus();
    }

    private List<Object> buildRows() {
        List<Object> rows = new ArrayList<>();
        if (schedule.isEmpty()) return rows;   // let the empty view speak instead
        if (dayMode) {
            long day = anchorMonday + dayOffset;
            List<Lesson> lessons = schedule.on(day);
            if (lessons.isEmpty()) return rows;
            rows.add(new DayHeader(day));
            rows.addAll(lessons);
            return rows;
        }
        for (int i = 0; i < 7; i++) {
            long day = anchorMonday + i;
            List<Lesson> lessons = schedule.on(day);
            if (lessons.isEmpty() && i >= 5) continue;         // hide empty weekends
            rows.add(new DayHeader(day));
            if (lessons.isEmpty()) rows.add(new FreeDay());
            else rows.addAll(lessons);
        }
        // a week entirely outside the semester says so instead of five "свободно"
        for (Object row : rows) if (row instanceof Lesson) return rows;
        rows.clear();
        return rows;
    }

    private String rangeLabel(boolean teacher) {
        if (dayMode) {
            long day = anchorMonday + dayOffset;
            return Dates.weekdayDayMonth(day) + (isToday(day) ? " · сегодня" : "");
        }
        String range = Dates.range(anchorMonday, anchorMonday + 6);
        return teacher ? range + " · все группы" : range;
    }

    private String emptyLabel() {
        if (schedule.isEmpty())
            return loading ? "Загружаю расписание…"
                    : "Расписание не загружено.\nНажмите ⟳ при подключении к сети.";
        long shown = shownDay();
        if (!schedule.inSemester(shown))
            return "Вне семестра.\nРасписание есть с " + Dates.dayMonth(schedule.firstDay())
                    + " по " + Dates.dayMonthYear(schedule.lastDay()) + ".";
        String empty = dayMode ? "Занятий нет — свободный день." : "На этой неделе занятий нет.";
        long next = schedule.nextDayWithLessons(shown + (dayMode ? 1 : 7), +1);
        return next == Dates.NO_DATE ? empty
                : empty + "\nСледующие занятия: "
                        + Dates.weekdayDayMonth(next).toLowerCase(Locale.ROOT);
    }

    private void updateStatus() {
        if (loading) {
            tvStatus.setText("Обновляю с smtu.ru…");
            return;
        }
        if (schedule.isEmpty()) {
            tvStatus.setText("");
            return;
        }
        StringBuilder s = new StringBuilder();
        s.append(schedule.size()).append(' ').append(plural(schedule.size(),
                "занятие", "занятия", "занятий"));
        if (schedule.fetchedAt() > 0) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(schedule.fetchedAt());
            long day = Dates.toEpochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH));
            String when = day == Dates.today() ? "сегодня" : Dates.dayMonth(day);
            s.append(" · обновлено ").append(when).append(", ")
             .append(String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
        }
        if (schedule.parity().isDerived() && schedule.parity().agreement() < 0.9)
            s.append(" · чётность недель неточная");
        tvStatus.setText(s);
    }

    private void buildDayStrip() {
        dayStrip.setVisibility(dayMode ? View.VISIBLE : View.GONE);
        dayStrip.removeAllViews();
        if (!dayMode) return;
        for (int i = 0; i < 7; i++) {
            final int index = i;
            long day = anchorMonday + i;
            boolean selected = i == dayOffset;
            int count = schedule.on(day).size();

            LinearLayout cell = Ui.column(this);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(Ui.rounded(selected ? (ui.dark ? 0x33FFFFFF : 0x1A1A3E8C)
                                                   : Color.TRANSPARENT, 8, this));
            cell.setOnClickListener(v -> {
                dayOffset = index;
                render();
            });

            TextView name = new TextView(this);
            name.setText(Dates.DAY_SHORT[i]);
            name.setTextSize(12);
            name.setGravity(Gravity.CENTER);
            name.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            name.setTextColor(isToday(day) ? ui.accent : (selected ? ui.text : ui.muted));
            cell.addView(name, Ui.lp(-1, -2));

            TextView dot = new TextView(this);
            dot.setText(count == 0 ? "·" : String.valueOf(count));
            dot.setTextSize(10);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(count == 0 ? ui.muted : ui.accent);
            cell.addView(dot, Ui.lp(-1, -2));

            LinearLayout.LayoutParams lp = Ui.lp(0, dp(40), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            dayStrip.addView(cell, lp);
        }
    }

    // ------------------------------------------------------------- navigation

    private void shift(int direction) {
        if (dayMode) {
            dayOffset += direction;
            while (dayOffset > 6) {
                dayOffset -= 7;
                anchorMonday += 7;
            }
            while (dayOffset < 0) {
                dayOffset += 7;
                anchorMonday -= 7;
            }
        } else {
            anchorMonday += 7L * direction;
        }
        render();                 // the whole semester is already loaded
    }

    private void goToday() {
        anchorMonday = Dates.monday(Dates.today());
        dayOffset = Dates.dayOfWeek(Dates.today());
        render();
    }

    private void setDayMode(boolean on) {
        dayMode = on;
        prefs().edit().putBoolean(PREF_DAY_MODE, on).apply();
        render();
    }

    private long shownDay() {
        return dayMode ? anchorMonday + dayOffset : anchorMonday;
    }

    private boolean isTodayShown() {
        long today = Dates.today();
        return dayMode ? anchorMonday + dayOffset == today
                       : Dates.monday(today) == anchorMonday;
    }

    private static boolean isToday(long epochDay) {
        return epochDay == Dates.today();
    }

    // ------------------------------------------------------------ list adapter

    /** A day's heading inside the list. */
    private static final class DayHeader {
        final long day;
        DayHeader(long day) { this.day = day; }
    }

    /** Placeholder for a day with no lessons in the week view. */
    private static final class FreeDay { }

    private final class RowAdapter extends BaseAdapter {
        private final List<Object> rows;

        RowAdapter(List<Object> rows) { this.rows = rows; }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 3; }

        @Override public int getItemViewType(int i) {
            Object o = rows.get(i);
            if (o instanceof DayHeader) return 0;
            return o instanceof FreeDay ? 1 : 2;
        }

        @Override public boolean isEnabled(int i) { return getItemViewType(i) == 2; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            Object o = rows.get(i);
            if (o instanceof DayHeader) return headerView((DayHeader) o, convert);
            if (o instanceof FreeDay) return freeDayView(convert);
            return lessonView((Lesson) o, dayOf(i), convert);
        }

        /** Which calendar day the lesson at that row belongs to. */
        private long dayOf(int index) {
            for (int i = index; i >= 0; i--)
                if (rows.get(i) instanceof DayHeader) return ((DayHeader) rows.get(i)).day;
            return shownDay();
        }
    }

    private View headerView(DayHeader h, View convert) {
        TextView tv = convert instanceof TextView ? (TextView) convert : new TextView(this);
        String label = Dates.DAY_FULL[Dates.dayOfWeek(h.day)] + ", " + Dates.dayMonth(h.day);
        tv.setText(isToday(h.day) ? label + "  ·  сегодня" : label);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(isToday(h.day) ? ui.accent : ui.muted);
        tv.setPadding(dp(6), dp(14), dp(6), dp(6));
        tv.setBackgroundColor(Color.TRANSPARENT);
        return tv;
    }

    private View freeDayView(View convert) {
        TextView tv = convert instanceof TextView ? (TextView) convert : new TextView(this);
        tv.setText("свободно");
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT);
        tv.setTextColor(ui.muted);
        tv.setPadding(dp(14), dp(8), dp(14), dp(10));
        tv.setBackground(Ui.rounded(ui.dark ? 0x0DFFFFFF : 0x0A000000, 10, this));
        return tv;
    }

    private View lessonView(Lesson lesson, long day, View convert) {
        LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert
                                                           : buildLessonRow();
        View stripe = row.findViewById(R.id.lesson_stripe);
        TextView time = row.findViewById(R.id.lesson_time);
        TextView subject = row.findViewById(R.id.lesson_subject);
        TextView meta = row.findViewById(R.id.lesson_meta);
        TextView room = row.findViewById(R.id.lesson_room);

        boolean now = isToday(day)
                && Dates.nowMinutes() >= lesson.startMinutes()
                && Dates.nowMinutes() < lesson.endMinutes();
        boolean past = isToday(day) && Dates.nowMinutes() >= lesson.endMinutes();

        stripe.setBackgroundColor(Ui.typeColor(lesson.type));
        time.setText(lesson.time.replace(" - ", "\n"));
        time.setTextColor(now ? ui.accent : ui.muted);
        subject.setText(lesson.subject);
        subject.setTextColor(ui.text);
        subject.setTypeface(now ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

        // on a teacher's own page their name is on every row, so show the group instead
        StringBuilder m = new StringBuilder(lesson.type);
        String who = teacherId != null ? lesson.group : lesson.teacher;
        if (!who.isEmpty()) m.append(m.length() > 0 ? " · " : "").append(who);
        if (!lesson.note.isEmpty()) m.append(m.length() > 0 ? " · " : "").append(lesson.note);
        meta.setText(m);
        meta.setTextColor(ui.muted);

        room.setText(lesson.room);
        room.setTextColor(ui.muted);

        row.setBackground(Ui.rounded(now ? Ui.blend(ui.card, ui.accent, 0.14f) : ui.card, 12, this));
        row.setAlpha(past ? 0.55f : 1f);
        row.setOnClickListener(v -> showLesson(lesson, day));
        return row;
    }

    private LinearLayout buildLessonRow() {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams rowLp = Ui.lp(-1, -2);
        row.setLayoutParams(rowLp);

        View stripe = new View(this);
        stripe.setId(R.id.lesson_stripe);
        LinearLayout.LayoutParams stripeLp = Ui.lp(dp(4), dp(44));
        stripeLp.rightMargin = dp(10);
        row.addView(stripe, stripeLp);

        TextView time = new TextView(this);
        time.setId(R.id.lesson_time);
        time.setTextSize(12);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setLineSpacing(0, 0.95f);
        row.addView(time, Ui.lp(dp(48), -2));

        LinearLayout middle = Ui.column(this);
        LinearLayout.LayoutParams midLp = Ui.lp(0, -2, 1f);
        midLp.leftMargin = dp(8);
        row.addView(middle, midLp);

        TextView subject = new TextView(this);
        subject.setId(R.id.lesson_subject);
        subject.setTextSize(15);
        middle.addView(subject);

        TextView meta = new TextView(this);
        meta.setId(R.id.lesson_meta);
        meta.setTextSize(12);
        middle.addView(meta);

        TextView room = new TextView(this);
        room.setId(R.id.lesson_room);
        room.setTextSize(12);
        room.setGravity(Gravity.END);
        row.addView(room, Ui.lp(dp(80), -2));

        return row;
    }

    // ---------------------------------------------------------------- dialogs

    private void showLesson(Lesson lesson, long day) {
        StringBuilder m = new StringBuilder();
        m.append(Dates.weekdayDayMonth(day)).append(", ")
         .append(lesson.time.replace(" - ", " – ")).append('\n');
        if (!lesson.type.isEmpty()) m.append(lesson.type);
        if (!lesson.room.isEmpty()) m.append(m.length() > 0 ? " · " : "").append(lesson.room);
        m.append('\n').append(lesson.upper ? "Верхняя" : "Нижняя").append(" неделя");
        if (!lesson.group.isEmpty()) m.append(" · группа ").append(lesson.group);
        if (!lesson.teacher.isEmpty()) m.append("\n\nПреподаватель: ").append(lesson.teacher);
        if (!lesson.note.isEmpty()) m.append("\n\n").append(lesson.note);
        if (!lesson.dateRange.isEmpty()) m.append("\n\n").append(lesson.dateRange);
        if (!lesson.days.isEmpty()) {
            m.append("\n\nВсего занятий: ").append(lesson.days.size()).append('\n');
            for (int i = 0; i < lesson.days.size(); i++) {
                if (i > 0) m.append(i % 4 == 0 ? "\n" : ", ");
                m.append(Dates.formatRu(lesson.days.get(i)));
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(lesson.subject)
                .setMessage(m)
                .setNeutralButton("Ещё", null)
                .setNegativeButton("Закрыть", null);
        if (!lesson.teacher.isEmpty())
            b.setPositiveButton("Преподаватель", (d, w) -> openTeacher(lesson));
        AlertDialog dialog = b.create();
        dialog.show();
        // keep the dialog open: "Ещё" opens a second menu instead of dismissing
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
              .setOnClickListener(v -> showLessonExtras(lesson, day, dialog));
    }

    private void showLessonExtras(Lesson lesson, long day, AlertDialog parent) {
        List<String> actions = new ArrayList<>();
        actions.add("Все занятия по предмету");
        actions.add("Добавить в календарь");
        actions.add("Поделиться");
        if (!lesson.group.isEmpty() && teacherId != null) actions.add("Расписание группы " + lesson.group);

        new AlertDialog.Builder(this)
                .setTitle(lesson.subject)
                .setItems(actions.toArray(new String[0]), (d, which) -> {
                    parent.dismiss();       // the action replaces what is on screen
                    switch (which) {
                        case 0:
                            showLessonList("Предмет: " + lesson.subject,
                                    schedule.ofSubject(lesson.subject), false);
                            break;
                        case 1:
                            addToCalendar(lesson, day);
                            break;
                        case 2:
                            share(lesson.subject, lessonText(lesson, day));
                            break;
                        default:
                            openGroupByName(lesson.group);
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showLessonList(String title, List<Lesson> lessons, boolean withSubject) {
        if (lessons.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(title)
                    .setMessage("Занятий не найдено.")
                    .setPositiveButton("Закрыть", null).show();
            return;
        }
        String[] items = new String[lessons.size()];
        for (int i = 0; i < lessons.size(); i++) {
            Lesson l = lessons.get(i);
            String when = l.days.isEmpty() ? l.day
                    : Dates.DAY_SHORT[Dates.dayOfWeek(l.days.get(0))] + " " + Dates.formatRu(l.days.get(0));
            items[i] = when + " · " + l.oneLine(withSubject);
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    Lesson l = lessons.get(which);
                    long day = l.days.isEmpty() ? shownDay() : l.days.get(0);
                    goTo(day);
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    /** Jump the main view to a specific date. */
    private void goTo(long day) {
        anchorMonday = Dates.monday(day);
        dayOffset = Dates.dayOfWeek(day);
        render();
    }

    private void showSearch() {
        final EditText input = new EditText(this);
        input.setHint("Предмет, преподаватель, аудитория");
        input.setSingleLine(true);

        LinearLayout box = Ui.column(this);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        box.addView(input, Ui.lp(-1, -2));

        final TextView hint = new TextView(this);
        hint.setTextSize(12);
        hint.setTextColor(ui.muted);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint, Ui.lp(-1, -2));

        final ListView results = new ListView(this);
        final List<Lesson> found = new ArrayList<>();
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        results.setAdapter(adapter);
        box.addView(results, Ui.lp(-1, dp(300)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Поиск по расписанию")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                found.clear();
                found.addAll(schedule.search(e.toString()));
                adapter.clear();
                for (Lesson l : found) {
                    String when = l.days.isEmpty() ? l.day
                            : Dates.DAY_SHORT[Dates.dayOfWeek(l.days.get(0))] + " " + Dates.formatRu(l.days.get(0));
                    adapter.add(when + " · " + l.oneLine());
                }
                adapter.notifyDataSetChanged();
                hint.setText(e.length() == 0 ? "Введите запрос"
                        : found.size() + " " + plural(found.size(), "совпадение", "совпадения", "совпадений"));
            }
        });
        hint.setText("Введите запрос");

        results.setOnItemClickListener((parent, view, position, id) -> {
            Lesson l = found.get(position);
            dialog.dismiss();
            goTo(l.days.isEmpty() ? shownDay() : l.days.get(0));
            showLesson(l, l.days.isEmpty() ? shownDay() : l.days.get(0));
        });
        dialog.show();
    }

    private void showMenu() {
        List<String> items = new ArrayList<>();
        items.add(teacherId != null ? "Вернуться к группе " + groupName : "Сменить группу");
        items.add(dayMode ? "Показать неделю" : "Показать день");
        items.add("Поделиться " + (dayMode ? "днём" : "неделей"));
        items.add("Обновить с smtu.ru");
        items.add("Очистить кэш и загрузить заново");
        items.add("Открыть сайт расписания");
        items.add("О приложении");

        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    switch (which) {
                        case 0:
                            if (teacherId != null) closeTeacher(); else showGroupPicker(false);
                            break;
                        case 1: setDayMode(!dayMode); break;
                        case 2: shareCurrentView(); break;
                        case 3: refresh(); break;
                        case 4: clearCache(); break;
                        case 5: openSite(); break;
                        default: showAbout();
                    }
                })
                .show();
    }

    /** Drop every cached schedule and pull this one again. */
    private void clearCache() {
        Smtu.clearScheduleCache(this);
        schedule = Schedule.EMPTY;
        render();
        refresh();
        toast("Кэш очищен");
    }

    private void showAbout() {
        String parity = schedule.parity().isDerived()
                ? "Чётность недель вычислена по датам занятий с сайта."
                : "Чётность недель — по встроенному календарю (расписание ещё не загружено).";
        new AlertDialog.Builder(this)
                .setTitle("Расписание СПбГМТУ " + BuildConfig.VERSION_NAME)
                .setMessage("Неофициальный просмотрщик расписания СПбГМТУ.\n\n"
                        + "Данные берутся напрямую с www.smtu.ru — официального сайта "
                        + "университета — при запуске и по кнопке ⟳, затем работают "
                        + "офлайн из кэша на устройстве.\n\n"
                        + parity + "\n\n"
                        + "Свайп влево/вправо — следующий день или неделя. "
                        + "Тап по названию группы — смена группы. "
                        + "Тап по занятию — детали, преподаватель, экспорт в календарь.\n\n"
                        + "Приложение не связано с университетом; все данные расписания "
                        + "принадлежат СПбГМТУ.")
                .setPositiveButton("Закрыть", null)
                .show();
    }

    // ----------------------------------------------------------- group picker

    private void showGroupPicker(boolean firstRun) {
        LinearLayout box = Ui.column(this);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);

        EditText search = new EditText(this);
        search.setHint("Группа, например 12826-11");
        search.setSingleLine(true);
        box.addView(search, Ui.lp(-1, -2));

        final TextView status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(ui.muted);
        status.setPadding(0, dp(8), 0, 0);
        status.setText("Загружаю список групп с smtu.ru…");
        box.addView(status, Ui.lp(-1, -2));

        final List<Group> all = new ArrayList<>();
        final List<Group> shown = new ArrayList<>();
        ListView listView = new ListView(this);
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        box.addView(listView, Ui.lp(-1, dp(320)));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Ваша группа")
                .setView(box)
                .setNeutralButton("Повторить", null);
        if (!firstRun) builder.setNegativeButton("Отмена", null);
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!firstRun);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                filterGroups(e.toString(), all, shown, adapter);
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Group picked = shown.get(position);
            prefs().edit().putString(PREF_GID, picked.id).putString(PREF_GNAME, picked.name).apply();
            groupId = picked.id;
            groupName = picked.name;
            teacherId = null;
            teacherName = null;
            goToday();
            dialog.dismiss();
            load();
        });

        final Runnable loadGroups = () -> {
            status.setText("Загружаю список групп с smtu.ru…");
            Smtu.groups(this, (groups, error) -> {
                if (isFinishing() || !dialog.isShowing()) return;
                if (groups.isEmpty()) {
                    status.setText((error != null ? error : "Список групп недоступен")
                            + ".\nПроверьте сеть и нажмите «Повторить».");
                    return;
                }
                all.clear();
                all.addAll(groups);
                filterGroups(search.getText().toString(), all, shown, adapter);
                status.setText(groups.size() + " " + plural(groups.size(),
                        "группа", "группы", "групп") + " · начните вводить номер");
            });
        };

        dialog.show();
        // "Повторить" must re-fetch without closing the dialog
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> loadGroups.run());
        loadGroups.run();
    }

    private void filterGroups(String query, List<Group> all, List<Group> shown,
                              ArrayAdapter<String> adapter) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Group g : all) if (q.isEmpty() || g.name.toLowerCase(Locale.ROOT).contains(q)) shown.add(g);
        adapter.clear();
        for (Group g : shown) adapter.add(g.name);
        adapter.notifyDataSetChanged();
    }

    /** Open a group's schedule by its name, as printed in a teacher's timetable. */
    private void openGroupByName(String name) {
        Smtu.groups(this, (groups, error) -> {
            if (isFinishing()) return;
            for (Group g : groups) {
                if (!g.name.equalsIgnoreCase(name)) continue;
                teacherId = null;
                teacherName = null;
                groupId = g.id;
                groupName = g.name;
                prefs().edit().putString(PREF_GID, g.id).putString(PREF_GNAME, g.name).apply();
                load();
                return;
            }
            toast("Группа " + name + " не найдена в списке");
        });
    }

    // ---------------------------------------------------------------- sharing

    private void addToCalendar(Lesson lesson, long day) {
        long start = Dates.startOfDayMillis(day, Math.max(lesson.startMinutes(), 0));
        long end = Dates.startOfDayMillis(day, Math.max(lesson.endMinutes(), lesson.startMinutes() + 90));
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                .putExtra(CalendarContract.Events.TITLE, lesson.subject)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, lesson.room)
                .putExtra(CalendarContract.Events.DESCRIPTION, lessonText(lesson, day));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("На устройстве нет приложения-календаря");
        }
    }

    private void shareCurrentView() {
        StringBuilder text = new StringBuilder();
        String who = teacherId != null ? teacherName : groupName;
        if (dayMode) {
            long day = anchorMonday + dayOffset;
            text.append(who).append(" · ").append(Dates.weekdayDayMonth(day)).append('\n');
            appendDay(text, day);
        } else {
            text.append(who).append(" · ").append(Dates.range(anchorMonday, anchorMonday + 6))
                .append(schedule.isUpper(anchorMonday) ? " · верхняя неделя" : " · нижняя неделя")
                .append('\n');
            for (int i = 0; i < 7; i++) {
                long day = anchorMonday + i;
                if (schedule.on(day).isEmpty()) continue;
                text.append('\n').append(Dates.DAY_FULL[i]).append(", ")
                    .append(Dates.dayMonth(day)).append('\n');
                appendDay(text, day);
            }
        }
        share(who + " · расписание", text.toString().trim());
    }

    private void appendDay(StringBuilder text, long day) {
        List<Lesson> lessons = schedule.on(day);
        if (lessons.isEmpty()) {
            text.append("занятий нет\n");
            return;
        }
        for (Lesson l : lessons) text.append(l.oneLine()).append('\n');
    }

    private String lessonText(Lesson lesson, long day) {
        StringBuilder t = new StringBuilder();
        t.append(lesson.subject).append('\n')
         .append(Dates.weekdayDayMonth(day)).append(", ")
         .append(lesson.time.replace(" - ", " – "));
        if (!lesson.type.isEmpty()) t.append('\n').append(lesson.type);
        if (!lesson.room.isEmpty()) t.append(" · ").append(lesson.room);
        if (!lesson.teacher.isEmpty()) t.append('\n').append(lesson.teacher);
        if (!lesson.group.isEmpty()) t.append('\n').append("Группа ").append(lesson.group);
        if (!lesson.note.isEmpty()) t.append('\n').append(lesson.note);
        return t.toString();
    }

    private void share(String subject, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "Поделиться"));
        } catch (ActivityNotFoundException e) {
            toast("Нечем поделиться");
        }
    }

    private void openSite() {
        String url = Smtu.HOST + (teacherId != null
                ? "/ru/viewschedule_new/teacher/" + teacherId + "/"
                : "/ru/viewschedule_new/" + groupId + "/");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            toast("Нет браузера");
        }
    }

    // ----------------------------------------------------------------- utils

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /** Russian plural: 1 занятие, 2 занятия, 5 занятий. */
    static String plural(int n, String one, String few, String many) {
        int mod100 = n % 100, mod10 = n % 10;
        if (mod100 >= 11 && mod100 <= 14) return many;
        if (mod10 == 1) return one;
        if (mod10 >= 2 && mod10 <= 4) return few;
        return many;
    }
}
