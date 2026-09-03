package com.korabel.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the only authoritative source of СПбГМТУ schedules: www.smtu.ru.
 *
 *   GET /ru/listschedule/                     -> all group ids and names
 *   GET /ru/viewschedule_new/<gid>/           -> full-semester group schedule
 *   GET /ru/viewschedule_new/teacher/<pid>/   -> full-semester teacher schedule
 *                                               (pid = the /ru/viewperson/<pid>/ id
 *                                                found in group schedule links)
 *
 * Pages are server-rendered HTML. The parser targets the "card" view whose
 * structure every schedule page shares:
 *
 *   <div class="js-day-block"> <h2>Понедельник</h2>
 *     <div class="col js-time-card"> <h3><span>08:30 - 10:00</span></h3>
 *       <div class="js-week-container js-week-1|2">     (upper | lower week)
 *         <h3 class="h6 ..."><span>SUBJECT</span></h3>
 *         <p class="small text-muted ..." title="14.09.2026, 28.09.2026, ...">…</p>
 *         <p class="card-text ..."><em>ROOM <small class="text-muted">TYPE</small></em></p>
 *         <a href="/ru/viewperson/100952/"><small>TEACHER</small></a>
 *
 * The title attribute lists the exact dates of every occurrence, which is what
 * makes day/week views exact. Every fetch merges into a per-schedule cache
 * file, so the app works offline and history survives page edits.
 */
public final class Smtu {

    public static final String HOST = "https://www.smtu.ru";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) Chrome/126.0 Mobile";
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** One class occurrence: a subject at a time slot on a weekday. */
    public static final class Slot implements Comparable<Slot> {
        public String day = "";        // Понедельник..Воскресенье
        public String time = "";       // 08:30 - 10:00
        public boolean upper;          // js-week-1 (верхняя) / js-week-2 (нижняя)
        public String subject = "";
        public String type = "";       // Лекция / Практическое занятие / ...
        public String room = "";       // 167 Корпус У
        public String teacher = "";
        public String teacherId = "";  // viewperson id, "" when not linked
        public String dateRange = "";  // "14 сентября — 21 декабря 2026"
        public List<String> dates = new ArrayList<>(); // exact dd.MM.yyyy occurrences

        @Override public int compareTo(Slot o) {
            String a = dates.isEmpty() ? "" : dates.get(0);
            String b = o.dates.isEmpty() ? "" : o.dates.get(0);
            int c = a.compareTo(b);
            return c != 0 ? c : time.compareTo(o.time);
        }

        String key() { return day + "|" + time + "|" + upper + "|" + subject + "|" + type + "|" + room + "|" + dates; }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("day", day).put("time", time).put("upper", upper).put("subject", subject)
             .put("type", type).put("room", room).put("teacher", teacher)
             .put("teacherId", teacherId).put("dateRange", dateRange)
             .put("dates", new JSONArray(dates));
            return o;
        }

        static Slot fromJson(JSONObject o) {
            Slot s = new Slot();
            s.day = o.optString("day"); s.time = o.optString("time"); s.upper = o.optBoolean("upper");
            s.subject = o.optString("subject"); s.type = o.optString("type"); s.room = o.optString("room");
            s.teacher = o.optString("teacher"); s.teacherId = o.optString("teacherId");
            s.dateRange = o.optString("dateRange");
            JSONArray d = o.optJSONArray("dates");
            if (d != null) for (int i = 0; i < d.length(); i++) s.dates.add(d.optString(i));
            return s;
        }
    }

    public static final class Group {
        public final String id, name;
        Group(String id, String name) { this.id = id; this.name = name; }
    }

    public interface Callback<T> { void done(T result, String error); }

    // ------------------------------------------------------------------ public

    /** All groups from /ru/listschedule/, cache-first. */
    public static void groups(Context ctx, final Callback<List<Group>> cb) {
        final List<Group> cached = readGroupsCache(ctx);
        if (!cached.isEmpty()) cb.done(cached, null);
        new Thread(() -> {
            try {
                List<Group> fresh = parseGroups(httpGet(HOST + "/ru/listschedule/"));
                writeGroupsCache(ctx, fresh);
                post(fresh, null, cb);
            } catch (Exception e) {
                if (cached.isEmpty()) post(cached, "Нет сети: " + e.getMessage(), cb);
            }
        }).start();
    }

    /** Full-semester schedule (group or teacher); merges fetch into cache. */
    public static void schedule(Context ctx, boolean teacher, String id, Callback<List<Slot>> cb) {
        new Thread(() -> {
            try {
                String path = teacher ? "/ru/viewschedule_new/teacher/" : "/ru/viewschedule_new/";
                String html = httpGet(HOST + path + id + "/");
                calibrateParity(ctx, html);
                post(mergeAndStore(ctx, teacher, id, parseSchedule(html)), null, cb);
            } catch (Exception e) {
                List<Slot> cached = readSlotCache(ctx, teacher, id);
                post(cached, cached.isEmpty() ? "Нет сети: " + e.getMessage() : null, cb);
            }
        }).start();
    }

    /** Synchronous cache read for instant restore. */
    public static List<Slot> loadCacheSync(Context ctx, boolean teacher, String id) {
        return readSlotCache(ctx, teacher, id);
    }

    /**
     * Week parity for a date. The site prints "Сегодня: ... верхняя/нижняя
     * неделя" on every page; every fetch recalibrates the anchor so the
     * computation stays correct across semesters. Fallback: the week of
     * 2026-08-31 is upper.
     */
    public static boolean isUpperWeek(Context ctx, Calendar date) {
        SharedPreferences p = ctx.getSharedPreferences("sched", Context.MODE_PRIVATE);
        long anchor = p.getLong("parityAnchor", 0);
        boolean anchorUpper = p.getBoolean("parityAnchorUpper", true);
        if (anchor == 0) { // constant fallback: Monday 2026-08-31 was upper
            Calendar c = Calendar.getInstance();
            c.set(2026, Calendar.AUGUST, 31, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            anchor = c.getTimeInMillis();
            anchorUpper = true;
        }
        long weeks = ((date.getTimeInMillis() - anchor) / (7L * 86400000L)) % 2;
        return (weeks == 0) == anchorUpper;
    }

    // ----------------------------------------------------------------- parsing

    /** /ru/viewschedule_new/&lt;id&gt;/ links with display names, in page order. */
    static List<Group> parseGroups(String html) {
        Pattern link = Pattern.compile("<a[^>]+href=\"/ru/viewschedule_new/(\\d+)/\"[^>]*>(.*?)</a>", Pattern.DOTALL);
        Map<String, Group> byId = new TreeMap<>();
        Matcher m = link.matcher(html);
        while (m.find()) {
            String name = stripTags(m.group(2)).trim();
            if (!name.isEmpty()) byId.put(m.group(1), new Group(m.group(1), name));
        }
        return new ArrayList<>(byId.values());
    }

    /** Card-view parser shared by group and teacher schedule pages. */
    static List<Slot> parseSchedule(String html) {
        List<Slot> out = new ArrayList<>();
        int i = 0;
        while ((i = html.indexOf("js-day-block", i)) >= 0) {
            int dayEnd = indexOfAny(html, new String[]{"js-day-block", "card-container-end", "</main"}, i + 12);
            if (dayEnd < 0) dayEnd = html.length();
            String block = html.substring(i, dayEnd);

            Matcher h2 = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL).matcher(block);
            String day = h2.find() ? stripTags(h2.group(1)).trim() : "";

            Matcher card = Pattern.compile("js-time-card.*?<span>(\\d\\d:\\d\\d - \\d\\d:\\d\\d)</span>(.*?)(?=js-time-card|js-day-block|$)", Pattern.DOTALL).matcher(block);
            while (card.find()) {
                String time = card.group(1);
                String cardBody = card.group(2);
                Matcher wk = Pattern.compile("js-week-container js-week-([12])(.*?)(?=js-week-container js-week|$)", Pattern.DOTALL).matcher(cardBody);
                while (wk.find()) {
                    Slot s = parseLesson(wk.group(2));
                    s.day = day;
                    s.time = time;
                    s.upper = "1".equals(wk.group(1));
                    if (!s.subject.isEmpty()) out.add(s);
                }
            }
            i = dayEnd;
        }
        Collections.sort(out);
        return out;
    }

    private static final Pattern SUBJECT = Pattern.compile("<h3[^>]*class=\"[^\"]*h6[^\"]*\"[^>]*>\\s*<span>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern DATES   = Pattern.compile("<p[^>]*class=\"[^\"]*text-muted[^\"]*\"[^>]*title=\"([^\"]*)\"");
    private static final Pattern RANGE   = Pattern.compile("fa-calendar[^>]*></i>\\s*([^<]*)</p>");
    private static final Pattern ROOM    = Pattern.compile("<p class=\"card-text[^\"]*\"[^>]*><em>(.*?)</em>", Pattern.DOTALL);
    private static final Pattern TYPE    = Pattern.compile("<small class=\"text-muted\">([^<]*)</small>");
    private static final Pattern PERSON  = Pattern.compile("<a href=\"/ru/viewperson/(\\d+)/\">\\s*<small>(.*?)</small>", Pattern.DOTALL);

    private static Slot parseLesson(String chunk) {
        Slot s = new Slot();
        Matcher m = SUBJECT.matcher(chunk);
        if (m.find()) s.subject = stripTags(m.group(1)).trim();
        m = DATES.matcher(chunk);
        if (m.find() && !m.group(1).isEmpty())
            for (String d : m.group(1).split("\\s*,\\s*"))
                if (d.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) s.dates.add(d);
        m = RANGE.matcher(chunk);
        if (m.find()) s.dateRange = m.group(1).trim();
        m = ROOM.matcher(chunk);
        if (m.find()) {
            String em = m.group(1);
            Matcher t = TYPE.matcher(em);
            if (t.find()) { s.type = t.group(1).trim(); s.room = stripTags(em.substring(0, t.start())).trim(); }
            else s.room = stripTags(em).trim();
        }
        m = PERSON.matcher(chunk);
        if (m.find()) { s.teacherId = m.group(1); s.teacher = stripTags(m.group(2)).trim(); }
        return s;
    }

    /** "Сегодня: ... верхняя|нижняя неделя" -> parity anchor at today. */
    static void calibrateParity(Context ctx, String page) {
        Matcher m = Pattern.compile("Сегодня:[^<]*?(верхняя|нижняя)\\s+недел").matcher(page);
        if (!m.find()) return;
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        ctx.getSharedPreferences("sched", Context.MODE_PRIVATE).edit()
           .putLong("parityAnchor", today.getTimeInMillis())
           .putBoolean("parityAnchorUpper", m.group(1).equals("верхняя"))
           .apply();
    }

    // ------------------------------------------------------------------ cache

    private static List<Slot> mergeAndStore(Context ctx, boolean teacher, String id, List<Slot> fresh) {
        Map<String, Slot> byKey = new TreeMap<>();
        for (Slot s : readSlotCache(ctx, teacher, id)) byKey.put(s.key(), s);
        for (Slot s : fresh) byKey.put(s.key(), s);
        List<Slot> all = new ArrayList<>(byKey.values());
        Collections.sort(all);
        try {
            JSONArray arr = new JSONArray();
            for (Slot s : all) arr.put(s.toJson());
            write(schedFile(ctx, teacher, id), new JSONObject().put("slots", arr).toString());
        } catch (Exception ignored) { }
        return all;
    }

    private static List<Slot> readSlotCache(Context ctx, boolean teacher, String id) {
        try {
            File f = schedFile(ctx, teacher, id);
            if (!f.exists()) return new ArrayList<>();
            JSONArray arr = new JSONObject(read(f)).getJSONArray("slots");
            List<Slot> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) out.add(Slot.fromJson(arr.getJSONObject(i)));
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private static List<Group> readGroupsCache(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), "groups.json");
            if (!f.exists()) return new ArrayList<>();
            JSONArray arr = new JSONObject(read(f)).getJSONArray("groups");
            List<Group> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Group(o.getString("id"), o.getString("name")));
            }
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private static void writeGroupsCache(Context ctx, List<Group> groups) {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) arr.put(new JSONObject().put("id", g.id).put("name", g.name));
            write(new File(ctx.getFilesDir(), "groups.json"), new JSONObject().put("groups", arr).toString());
        } catch (Exception ignored) { }
    }

    private static File schedFile(Context ctx, boolean teacher, String id) {
        return new File(ctx.getFilesDir(), (teacher ? "t_" : "g_") + id + ".json");
    }

    // ------------------------------------------------------------------- net

    static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "ru");
        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        in.close();
        c.disconnect();
        return sb.toString();
    }

    private static void write(File f, String s) throws IOException {
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
        w.write(s);
        w.close();
    }

    private static String read(File f) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    // ------------------------------------------------------------------ utils

    static String stripTags(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", " ")
                            .replace("&nbsp;", " ").replace("&amp;", "&")
                            .replace("&laquo;", "«").replace("&raquo;", "»")
                            .replace("&mdash;", "—").replace("&ndash;", "–")
                            .replaceAll("\\s+", " ").trim();
    }

    private static int indexOfAny(String s, String[] keys, int from) {
        int best = -1;
        for (String k : keys) {
            int i = s.indexOf(k, from);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    /** dd.MM.yyyy -> Calendar midnight (for date matching in the UI). */
    public static Calendar parseRu(String dd_mm_yyyy) {
        String[] p = dd_mm_yyyy.split("\\.");
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(p[2]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[0]), 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** Calendar -> dd.MM.yyyy. */
    public static String fmtRu(Calendar c) {
        return String.format(Locale.US, "%1$td.%1$tm.%1$tY", c);
    }

    private static <T> void post(final T val, final String err, final Callback<T> cb) {
        UI.post(() -> cb.done(val, err));
    }

    private Smtu() { }
}
