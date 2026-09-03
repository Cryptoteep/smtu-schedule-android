package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.korabel.schedule.Smtu.Callback;
import com.korabel.schedule.Smtu.Group;
import com.korabel.schedule.Smtu.Slot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Minimal native СПбГМТУ schedule app. Data comes from www.smtu.ru only
 * (the authoritative source), parsed from the server-rendered schedule pages.
 * One Activity, programmatic views, no external libraries.
 *
 * Header: group name, date range, upper/lower week badge, ‹ › navigation.
 * Week mode shows Mon–Sun of the selected week; day mode a single day with a
 * weekday quick-switch strip. Lessons match a day via their exact occurrence
 * dates (the title attribute on the site). Tapping a lesson opens details;
 * from there: all occurrences of the subject, or the teacher's full schedule.
 */
public final class MainActivity extends Activity {

    private static final String PREFS = "sched";
    private static final String PREF_GID = "groupId";
    private static final String PREF_GNAME = "groupName";
    private static final Locale RU = new Locale("ru");

    // views
    private TextView tvGroup, tvRange, tvWeek, tvEmpty;
    private LinearLayout dayStrip;
    private ListView list;
    private Button btnMode;

    // state
    private String groupId, groupName;
    private List<Slot> slots = new ArrayList<>();
    private boolean dayMode = false;
    private Calendar anchor = monday(today());                 // Monday of shown week
    private int dayOffset = (today().get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0..Sun=6

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        groupId = p.getString(PREF_GID, null);
        groupName = p.getString(PREF_GNAME, groupId == null ? null : groupId);
        if (savedInstanceState != null) {
            dayMode = savedInstanceState.getBoolean("day");
            anchor.setTimeInMillis(savedInstanceState.getLong("anchor"));
            dayOffset = savedInstanceState.getInt("off");
        }
        if (groupId == null) showGroupPicker(); else load();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("day", dayMode);
        out.putLong("anchor", anchor.getTimeInMillis());
        out.putInt("off", dayOffset);
    }

    // ------------------------------------------------------------------- data

    private void load() {
        slots = Smtu.loadCacheSync(this, false, groupId);
        tvGroup.setText(groupName);
        render();          // instant from cache...
        refresh();         // ...then silently update from the network
    }

    private void refresh() {
        if (groupId == null) { showGroupPicker(); return; }
        Smtu.schedule(this, false, groupId, (result, error) -> {
            slots = result;
            render();
            if (error != null) toast(error);
        });
    }

    /** Slots occurring on a calendar day: exact-date match, or parity+weekday for rows without a date list. */
    private List<Slot> slotsOfDay(Calendar day) {
        String key = Smtu.fmtRu(day);
        String dayName = dayName(day);
        boolean upper = Smtu.isUpperWeek(this, day);
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots) {
            if (s.dates.contains(key)) { out.add(s); continue; }
            if (s.dates.isEmpty() && s.upper == upper && s.day.equalsIgnoreCase(dayName)) out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------- ui

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFEEF1F4);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(8), dp(10), dp(8), dp(8));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        Button prev = navButton("‹");
        prev.setOnClickListener(v -> shift(-1));
        Button next = navButton("›");
        next.setOnClickListener(v -> shift(1));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(4), 0, dp(4), 0);

        tvGroup = new TextView(this);
        tvGroup.setTextSize(20);
        tvGroup.setTypeface(Typeface.DEFAULT_BOLD);
        tvGroup.setTextColor(0xFF212121);
        title.addView(tvGroup);

        tvRange = new TextView(this);
        tvRange.setTextSize(13);
        tvRange.setTextColor(0xFF616161);
        title.addView(tvRange);
        title.setOnClickListener(v -> showGroupPicker()); // tap title = change group

        row1.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row1.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        row1.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        btnMode = new Button(this);
        btnMode.setTextSize(13);
        btnMode.setAllCaps(false);
        btnMode.setPadding(dp(12), 0, dp(12), 0);
        btnMode.setOnClickListener(v -> { dayMode = !dayMode; render(); });
        row2.addView(btnMode, new LinearLayout.LayoutParams(-2, dp(36)));

        tvWeek = new TextView(this);
        tvWeek.setTextSize(12);
        tvWeek.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeek.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2);
        wp.leftMargin = dp(8);
        row2.addView(tvWeek, wp);

        Button refresh = new Button(this);
        refresh.setText("⟳");
        refresh.setTextSize(16);
        refresh.setPadding(0, 0, 0, 0);
        refresh.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(36), dp(36));
        rp.leftMargin = dp(8);
        row2.addView(refresh, rp);

        View gap = new View(this);
        row2.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));

        Button info = new Button(this);
        info.setText("ⓘ");
        info.setTextSize(16);
        info.setAllCaps(false);
        info.setPadding(0, 0, 0, 0);
        info.setOnClickListener(v -> showInfo());
        row2.addView(info, new LinearLayout.LayoutParams(dp(36), dp(36)));

        dayStrip = new LinearLayout(this);
        dayStrip.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(dayStrip, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        tvEmpty = new TextView(this);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setTextColor(0xFF9E9E9E);
        tvEmpty.setTextSize(15);
        tvEmpty.setPadding(dp(24), dp(24), dp(24), dp(24));
        body.addView(tvEmpty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(8), 0, dp(8), dp(16));
        list.setClipToPadding(false);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(tvEmpty);

        return root;
    }

    private Button navButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(24);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    // -------------------------------------------------------------- rendering

    private void render() {
        btnMode.setText(dayMode ? "▶ Неделя" : "▶ День");
        buildDayStrip();

        List<Object> rows = new ArrayList<>();
        if (dayMode) {
            Calendar day = plus(anchor, dayOffset);
            rows.add(new DayHeader(day, isToday(day)));
            rows.addAll(slotsOfDay(day));
        } else {
            for (int i = 0; i < 7; i++) {
                Calendar day = plus(anchor, i);
                rows.add(new DayHeader(day, isToday(day)));
                rows.addAll(slotsOfDay(day));
            }
        }
        tvEmpty.setText(slots.isEmpty()
                ? "Нет данных.\nНажмите ⟳ при подключении к сети."
                : "Занятий нет.");

        tvRange.setText(rangeText());
        boolean upper = Smtu.isUpperWeek(this, dayMode ? plus(anchor, dayOffset) : anchor);
        tvWeek.setText(upper ? "ВЕРХНЯЯ НЕДЕЛЯ" : "НИЖНЯЯ НЕДЕЛЯ");
        tvWeek.setBackgroundColor(upper ? 0xFF1A237E : 0xFF00695C);
        tvWeek.setTextColor(Color.WHITE);

        list.setAdapter(new RowsAdapter(rows));
    }

    private String rangeText() {
        SimpleDateFormat df = new SimpleDateFormat("d MMMM", RU);
        if (dayMode)
            return new SimpleDateFormat("d MMMM, EEEE", RU).format(plus(anchor, dayOffset).getTime());
        String a = df.format(anchor.getTime());
        String b = df.format(plus(anchor, 6).getTime());
        return sameMonth(anchor, plus(anchor, 6)) ? a.split(" ")[0] + " – " + b : a + " – " + b;
    }

    private static boolean sameMonth(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.MONTH) == b.get(Calendar.MONTH);
    }

    // ------------------------------------------------------------- navigation

    private void shift(int dir) {
        if (dayMode) {
            dayOffset += dir;
            while (dayOffset > 6) { dayOffset -= 7; anchor.add(Calendar.DAY_OF_YEAR, 7); }
            while (dayOffset < 0) { dayOffset += 7; anchor.add(Calendar.DAY_OF_YEAR, -7); }
        } else {
            anchor.add(Calendar.DAY_OF_YEAR, 7 * dir);
        }
        render(); // full semester is already loaded: no refetch needed
    }

    private void buildDayStrip() {
        dayStrip.setVisibility(dayMode ? View.VISIBLE : View.GONE);
        dayStrip.removeAllViews();
        if (!dayMode) return;
        String[] names = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText(names[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(13);
            boolean sel = i == dayOffset;
            b.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setTextColor(sel ? 0xFF1A237E : 0xFF757575);
            b.setBackgroundColor(sel ? 0xFFDDE1F9 : Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            b.setOnClickListener(v -> { dayOffset = idx; render(); });
            dayStrip.addView(b, lp);
        }
    }

    // ----------------------------------------------------------- list adapter

    private static final class DayHeader {
        final Calendar day; final boolean today;
        DayHeader(Calendar d, boolean t) { day = d; today = t; }
    }

    private final class RowsAdapter extends BaseAdapter {
        private final List<Object> rows;
        RowsAdapter(List<Object> rows) { this.rows = rows; }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            Object o = rows.get(i);
            return (o instanceof DayHeader) ? headerView((DayHeader) o, convert)
                                            : lessonView((Slot) o, convert);
        }

        private View headerView(DayHeader h, View convert) {
            TextView tv = (convert instanceof TextView) ? (TextView) convert : new TextView(MainActivity.this);
            String label = new SimpleDateFormat("EEEE, d MMMM", RU).format(h.day.getTime());
            label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            tv.setText(label + (h.today ? "  •  сегодня" : ""));
            tv.setTextSize(13);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextColor(0xFF37474F);
            tv.setBackgroundColor(h.today ? 0xFFE8EAF6 : 0x11000000);
            tv.setPadding(dp(12), dp(10), dp(12), dp(10));
            return tv;
        }

        private View lessonView(final Slot s, View convert) {
            LinearLayout row = (convert instanceof LinearLayout) ? (LinearLayout) convert : null;
            if (row == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackgroundColor(Color.WHITE);

                TextView time = new TextView(MainActivity.this);
                time.setId(1);
                time.setTextSize(13);
                time.setTextColor(0xFF1A237E);
                time.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(time, new LinearLayout.LayoutParams(dp(84), -2));

                LinearLayout mid = new LinearLayout(MainActivity.this);
                mid.setId(2);
                mid.setOrientation(LinearLayout.VERTICAL);
                row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView subj = new TextView(MainActivity.this);
                subj.setId(3);
                subj.setTextSize(15);
                subj.setTextColor(0xFF212121);
                mid.addView(subj);

                TextView meta = new TextView(MainActivity.this);
                meta.setId(4);
                meta.setTextSize(12);
                meta.setTextColor(0xFF757575);
                mid.addView(meta);

                TextView room = new TextView(MainActivity.this);
                room.setId(5);
                room.setTextSize(12);
                room.setTextColor(0xFF37474F);
                room.setGravity(Gravity.END);
                row.addView(room, new LinearLayout.LayoutParams(dp(96), -2));
            }
            TextView time = row.findViewById(1), subj = row.findViewById(3),
                     meta = row.findViewById(4), room = row.findViewById(5);
            time.setText(s.time.replace(" - ", "\n– "));
            subj.setText(s.subject);
            meta.setText(s.teacher.isEmpty() ? s.type : s.type + " · " + s.teacher);
            room.setText(s.room);
            row.setOnClickListener(v -> showLesson(s));
            return row;
        }
    }

    // ---------------------------------------------------------------- dialogs

    private void showLesson(Slot s) {
        StringBuilder m = new StringBuilder();
        m.append(s.day).append(", ").append(s.time.replace(" - ", " – ")).append('\n')
         .append(s.type);
        if (!s.room.isEmpty()) m.append(" · ").append(s.room);
        m.append('\n').append(s.upper ? "Верхняя" : "Нижняя").append(" неделя");
        if (!s.dateRange.isEmpty()) m.append('\n').append('\n').append(s.dateRange);
        if (!s.dates.isEmpty()) {
            m.append('\n');
            for (int i = 0; i < s.dates.size(); i++) {
                if (i > 0) m.append(i % 4 == 0 ? '\n' : ", ");
                m.append(s.dates.get(i));
            }
        }
        if (!s.teacher.isEmpty()) m.append('\n').append('\n').append("Преподаватель: ").append(s.teacher);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(s.subject);
        b.setMessage(m);
        b.setNeutralButton("О предмете", (d, w) -> showSubject(s.subject));
        if (!s.teacher.isEmpty())
            b.setPositiveButton("Преподаватель", (d, w) -> showTeacher(s));
        b.setNegativeButton("Закрыть", null);
        b.show();
    }

    /** All occurrences of a subject in the loaded schedule. */
    private void showSubject(String subject) {
        List<Slot> of = new ArrayList<>();
        for (Slot s : slots) if (s.subject.equals(subject)) of.add(s);
        Collections.sort(of);
        showSlotList(subject, of);
    }

    /**
     * Teacher info: their full schedule across all groups, fetched from
     * smtu.ru by the viewperson id found in the schedule page. Falls back to
     * their lessons within this group's schedule.
     */
    private void showTeacher(Slot lesson) {
        String teacher = lesson.teacher;
        if (lesson.teacherId.isEmpty()) {
            List<Slot> of = new ArrayList<>();
            for (Slot s : slots) if (s.teacher.equals(teacher)) of.add(s);
            Collections.sort(of);
            showSlotList(teacher, of);
            return;
        }
        final AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(teacher)
                .setMessage("Загрузка расписания преподавателя…")
                .setNegativeButton("Закрыть", null).create();
        loading.show();
        Smtu.schedule(this, true, lesson.teacherId, (result, error) -> {
            loading.dismiss();
            List<Slot> toShow = (result != null && !result.isEmpty()) ? result : null;
            if (toShow == null) {
                toShow = new ArrayList<>();
                for (Slot s : slots) if (s.teacher.equals(teacher)) toShow.add(s);
                if (error != null) toast(error);
            }
            Collections.sort(toShow);
            showSlotList(teacher, toShow);
        });
    }

    private void showSlotList(String title, List<Slot> of) {
        CharSequence[] items = new CharSequence[of.size()];
        for (int i = 0; i < of.size(); i++) {
            Slot s = of.get(i);
            StringBuilder it = new StringBuilder();
            if (!s.dates.isEmpty()) it.append(s.dates.get(0));
            else it.append(shortDay(s.day));
            it.append(" · ").append(s.time.replace(" - ", " – "))
              .append(" · ").append(s.subject);
            if (!s.type.isEmpty()) it.append(" (").append(s.type).append(")");
            if (!s.room.isEmpty()) it.append(" · ").append(s.room);
            items[i] = it.toString();
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(title);
        if (of.isEmpty()) b.setMessage("Занятий не найдено.");
        else b.setItems(items, null);
        b.setPositiveButton("Закрыть", null);
        b.show();
    }

    // ------------------------------------------------------------ group picker

    private void showGroupPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);

        EditText search = new EditText(this);
        search.setHint("Группа, например 12826-61");
        box.addView(search, new LinearLayout.LayoutParams(-1, -2));

        final List<Group> all = new ArrayList<>();
        final List<Group> filtered = new ArrayList<>();
        ListView lv = new ListView(this);
        box.addView(lv, new LinearLayout.LayoutParams(-1, dp(320)));

        final android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        lv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable e) {
                applyFilter(e.toString(), all, filtered, adapter);
            }
        });

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Ваша группа")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .create();

        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Group picked = filtered.get(pos);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_GID, picked.id).putString(PREF_GNAME, picked.name).apply();
            groupId = picked.id;
            groupName = picked.name;
            anchor = monday(today());
            dayOffset = (today().get(Calendar.DAY_OF_WEEK) + 5) % 7;
            dlg.dismiss();
            load();
        });

        dlg.show();

        Smtu.groups(this, (groups, error) -> {
            if (groups.isEmpty()) { toast(error != null ? error : "Список групп недоступен"); return; }
            all.clear();
            all.addAll(groups);
            applyFilter(search.getText().toString(), all, filtered, adapter);
        });
    }

    private void applyFilter(String q, List<Group> all, List<Group> filtered, android.widget.ArrayAdapter<String> adapter) {
        q = q.trim().toLowerCase(RU);
        filtered.clear();
        for (Group g : all)
            if (q.isEmpty() || g.name.toLowerCase(RU).contains(q)) filtered.add(g);
        List<String> names = new ArrayList<>(filtered.size());
        for (Group g : filtered) names.add(g.name);
        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ about

    private void showInfo() {
        new AlertDialog.Builder(this)
                .setTitle("О приложении")
                .setMessage("Расписание СПбГМТУ " + BuildConfig.VERSION_NAME + "\n\n"
                        + "Неофициальное приложение-просмотрщик расписания. Все данные "
                        + "загружаются напрямую с www.smtu.ru — официального сайта "
                        + "университета — при запуске и по кнопке ⟳, после чего "
                        + "работают офлайн из кэша на устройстве.\n\n"
                        + "Верхняя/нижняя неделя определяется автоматически по данным "
                        + "сайта.\n\n"
                        + "Тап по названию группы вверху — смена группы.")
                .setPositiveButton("Закрыть", null)
                .show();
    }

    // ----------------------------------------------------------------- utils

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private boolean isToday(Calendar c) {
        Calendar t = today();
        return t.get(Calendar.YEAR) == c.get(Calendar.YEAR)
            && t.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
    }

    private static Calendar today() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Monday 00:00 of the week containing c. */
    private static Calendar monday(Calendar c) {
        Calendar r = (Calendar) c.clone();
        int shift = (r.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0 .. Sun=6
        r.add(Calendar.DAY_OF_YEAR, -shift);
        return r;
    }

    private static Calendar plus(Calendar c, int days) {
        Calendar r = (Calendar) c.clone();
        r.add(Calendar.DAY_OF_YEAR, days);
        return r;
    }

    /** "Понедельник" for a Calendar, to match Slot.day. */
    private static String dayName(Calendar c) {
        return new SimpleDateFormat("EEEE", RU).format(c.getTime());
    }

    private static String shortDay(String day) {
        return day.length() <= 3 ? day : day.substring(0, 3);
    }
}
