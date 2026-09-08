package com.korabel.schedule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Запасной источник расписания — срез, который несколько раз в сутки собирает
 * GitHub Actions со страниц smtu.ru и кладёт рядом с веб-версией.
 *
 * Нужен там, где до сайта университета не достучаться: под VPN с зарубежным
 * выходом сервер вуза просто не отвечает, и приложение оставалось бы со старым
 * кэшем. Данные те же самые, разобранные тем же парсером, — отличается лишь
 * свежесть, поэтому в интерфейсе такой ответ помечается как резервная копия.
 *
 *   index.json      {"updated": "...", "groups": [{"id","name","lessons"}], ...}
 *   g/<id>.json     {"id","name","lessons":[…]}
 *   t/<id>.json     то же для преподавателя
 *
 * Разбор отделён от сети, так что покрывается обычными JVM-тестами.
 */
public final class Mirror {

    /** Страница самого проекта — там же живёт веб-версия. */
    public static final String SITE = "https://cryptoteep.github.io/smtu-schedule-android";
    /** Где лежит срез: это страница самого проекта, не сторонний сервер. */
    public static final String BASE = SITE + "/data";

    private Mirror() { }

    public static String groupsUrl() {
        return BASE + "/index.json";
    }

    public static String scheduleUrl(boolean teacher, String id) {
        return BASE + (teacher ? "/t/" : "/g/") + id + ".json";
    }

    /** Список групп из index.json. */
    public static List<Group> parseGroups(String json) throws Exception {
        JSONArray arr = new JSONObject(json).optJSONArray("groups");
        List<Group> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject g = arr.optJSONObject(i);
            if (g == null) continue;
            String id = g.optString("id", "");
            String name = g.optString("name", "");
            if (!id.isEmpty() && !name.isEmpty()) out.add(new Group(id, name));
        }
        return out;
    }

    /** Название группы или преподавателя из файла расписания. */
    public static String parseTitle(String json) throws Exception {
        return new JSONObject(json).optString("name", "");
    }

    /** Занятия из файла расписания. */
    public static List<Lesson> parseSchedule(String json) throws Exception {
        JSONArray arr = new JSONObject(json).optJSONArray("lessons");
        List<Lesson> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Lesson l = new Lesson();
            l.day = o.optString("day", "");
            l.time = o.optString("time", "");
            l.upper = o.optBoolean("upper", true);
            l.subject = o.optString("subject", "");
            l.type = o.optString("type", "");
            l.room = o.optString("room", "");
            l.teacher = o.optString("teacher", "");
            l.teacherId = o.optString("teacherId", "");
            l.group = o.optString("group", "");
            l.note = o.optString("note", "");
            l.dateRange = o.optString("dateRange", "");
            JSONArray days = o.optJSONArray("days");
            if (days != null)
                for (int d = 0; d < days.length(); d++) l.days.add(days.optLong(d));
            if (!l.subject.isEmpty()) out.add(l);
        }
        return out;
    }
}
