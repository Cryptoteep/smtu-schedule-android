package com.korabel.schedule;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Everything that talks to www.smtu.ru or to the disk: fetching a page, caching
 * the parsed result, and handing it back on the main thread.
 *
 * Parsing itself lives in {@link ScheduleParser}; this class stays thin so the
 * interesting logic can be unit-tested without Android.
 *
 * Caching policy: every fetch is merged into the cached copy of that schedule
 * (see {@link Schedule#mergedWith}), so the app starts instantly, works offline,
 * and keeps lessons that the university later removes from the page.
 */
public final class Smtu {

    public static final String HOST = "https://www.smtu.ru";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int CACHE_SCHEMA = 2;
    private static final int MAX_TEACHER_CACHES = 12;

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "smtu-net");
        t.setDaemon(true);
        return t;
    });

    /** Result callback; `error` is a human-readable message or null. */
    public interface Callback<T> {
        void done(T value, String error);
    }

    private Smtu() { }

    // ------------------------------------------------------------------ groups

    /**
     * All groups from /ru/listschedule/. The cached list (if any) is delivered
     * immediately, then a fresh one when the network answers.
     */
    public static void groups(Context ctx, Callback<List<Group>> cb) {
        Context app = ctx.getApplicationContext();
        List<Group> cached = readGroups(app);
        if (!cached.isEmpty()) cb.done(cached, null);
        POOL.execute(() -> {
            try {
                List<Group> fresh = ScheduleParser.parseGroups(httpGet(HOST + "/ru/listschedule/"));
                if (fresh.isEmpty()) throw new IOException("список групп пуст");
                writeGroups(app, fresh);
                post(cb, fresh, null);
            } catch (Exception e) {
                if (cached.isEmpty()) post(cb, cached, message(e));
            }
        });
    }

    // ---------------------------------------------------------------- schedule

    /** The cached schedule, read synchronously; {@link Schedule#EMPTY} if none. */
    public static Schedule cached(Context ctx, boolean teacher, String id) {
        return readSchedule(ctx.getApplicationContext(), teacher, id);
    }

    /**
     * Fetch a group's or teacher's full-semester schedule and merge it into the
     * cache. On a network error the cached copy is returned with the error, so
     * the UI can keep showing data and still say what went wrong.
     */
    public static void schedule(Context ctx, boolean teacher, String id, Callback<Schedule> cb) {
        Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            Schedule cached = readSchedule(app, teacher, id);
            try {
                String path = teacher ? "/ru/viewschedule_new/teacher/" : "/ru/viewschedule_new/";
                String html = httpGet(HOST + path + id + "/");
                List<Lesson> lessons = ScheduleParser.parseSchedule(html);
                if (lessons.isEmpty() && !cached.isEmpty()) {
                    post(cb, cached, "Расписание на сайте пусто — показано сохранённое");
                    return;
                }
                String title = ScheduleParser.parseTitle(html);
                if (title.isEmpty() && teacher && !lessons.isEmpty()) title = lessons.get(0).teacher;
                Schedule merged = cached.mergedWith(
                        new Schedule(lessons, title, System.currentTimeMillis()));
                writeSchedule(app, teacher, id, merged);
                if (teacher) trimTeacherCaches(app);
                post(cb, merged, null);
            } catch (Exception e) {
                post(cb, cached, cached.isEmpty()
                        ? "Не удалось загрузить: " + message(e)
                        : "Нет связи с smtu.ru — показано сохранённое");
            }
        });
    }

    /** Drop every cached schedule (the group list survives). */
    public static void clearScheduleCache(Context ctx) {
        File[] files = ctx.getApplicationContext().getFilesDir().listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if ((n.startsWith("g_") || n.startsWith("t_")) && n.endsWith(".json")) f.delete();
        }
    }

    // -------------------------------------------------------------------- net

    /** GET a page as UTF-8 text, following redirects, with one retry. */
    static String httpGet(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return getOnce(url);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private static String getOnce(String url) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT);
            c.setReadTimeout(READ_TIMEOUT);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            c.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9");
            c.setRequestProperty("Accept-Encoding", "gzip");

            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK)
                throw new IOException("сайт ответил " + code);

            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(64 * 1024);
            byte[] chunk = new byte[16 * 1024];
            int total = 0;
            for (int n; (n = in.read(chunk)) > 0; ) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("страница слишком большая");
                buf.write(chunk, 0, n);
            }
            in.close();
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        if (e instanceof java.net.UnknownHostException) return "нет подключения к сети";
        if (e instanceof java.net.SocketTimeoutException) return "сайт не отвечает";
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    // ------------------------------------------------------------------ cache

    private static File scheduleFile(Context ctx, boolean teacher, String id) {
        return new File(ctx.getFilesDir(), (teacher ? "t_" : "g_") + id + ".json");
    }

    private static Schedule readSchedule(Context ctx, boolean teacher, String id) {
        try {
            File f = scheduleFile(ctx, teacher, id);
            if (!f.exists()) return Schedule.EMPTY;
            JSONObject root = new JSONObject(read(f));
            if (root.optInt("schema") != CACHE_SCHEMA) return Schedule.EMPTY;
            JSONArray arr = root.optJSONArray("lessons");
            List<Lesson> lessons = new ArrayList<>(arr == null ? 0 : arr.length());
            for (int i = 0; arr != null && i < arr.length(); i++)
                lessons.add(fromJson(arr.getJSONObject(i)));
            return new Schedule(lessons, root.optString("title"), root.optLong("fetchedAt"));
        } catch (Exception e) {
            return Schedule.EMPTY;
        }
    }

    private static void writeSchedule(Context ctx, boolean teacher, String id, Schedule s) {
        try {
            JSONArray arr = new JSONArray();
            for (Lesson l : s.lessons()) arr.put(toJson(l));
            JSONObject root = new JSONObject()
                    .put("schema", CACHE_SCHEMA)
                    .put("title", s.title())
                    .put("fetchedAt", s.fetchedAt())
                    .put("lessons", arr);
            write(scheduleFile(ctx, teacher, id), root.toString());
        } catch (Exception ignored) {
            // a schedule that fails to cache is still usable in memory
        }
    }

    private static JSONObject toJson(Lesson l) throws Exception {
        JSONArray days = new JSONArray();
        for (long d : l.days) days.put(d);
        return new JSONObject()
                .put("day", l.day).put("time", l.time).put("upper", l.upper)
                .put("subject", l.subject).put("type", l.type).put("room", l.room)
                .put("teacher", l.teacher).put("teacherId", l.teacherId)
                .put("group", l.group).put("note", l.note)
                .put("dateRange", l.dateRange).put("days", days);
    }

    private static Lesson fromJson(JSONObject o) {
        Lesson l = new Lesson();
        l.day = o.optString("day");
        l.time = o.optString("time");
        l.upper = o.optBoolean("upper");
        l.subject = o.optString("subject");
        l.type = o.optString("type");
        l.room = o.optString("room");
        l.teacher = o.optString("teacher");
        l.teacherId = o.optString("teacherId");
        l.group = o.optString("group");
        l.note = o.optString("note");
        l.dateRange = o.optString("dateRange");
        JSONArray days = o.optJSONArray("days");
        for (int i = 0; days != null && i < days.length(); i++) l.days.add(days.optLong(i));
        return l;
    }

    /** Teacher schedules pile up as the user taps around: keep the newest few. */
    private static void trimTeacherCaches(Context ctx) {
        File[] files = ctx.getFilesDir().listFiles((dir, name) ->
                name.startsWith("t_") && name.endsWith(".json"));
        if (files == null || files.length <= MAX_TEACHER_CACHES) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = MAX_TEACHER_CACHES; i < files.length; i++) files[i].delete();
    }

    private static List<Group> readGroups(Context ctx) {
        List<Group> out = new ArrayList<>();
        try {
            File f = new File(ctx.getFilesDir(), "groups.json");
            if (!f.exists()) return out;
            JSONArray arr = new JSONObject(read(f)).optJSONArray("groups");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Group(o.getString("id"), o.getString("name")));
            }
        } catch (Exception ignored) {
            out.clear();
        }
        return out;
    }

    private static void writeGroups(Context ctx, List<Group> groups) {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) arr.put(new JSONObject().put("id", g.id).put("name", g.name));
            write(new File(ctx.getFilesDir(), "groups.json"),
                    new JSONObject().put("groups", arr).toString());
        } catch (Exception ignored) {
            // the list is re-fetched next launch
        }
    }

    private static void write(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
        if (!tmp.renameTo(f)) {                       // renameTo fails if the target exists
            f.delete();
            if (!tmp.renameTo(f)) tmp.delete();
        }
    }

    private static String read(File f) throws IOException {
        BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder((int) Math.min(f.length(), 1 << 20));
            char[] buf = new char[8192];
            for (int n; (n = r.read(buf)) > 0; ) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            r.close();
        }
    }

    private static <T> void post(Callback<T> cb, T value, String error) {
        UI.post(() -> cb.done(value, error));
    }
}
