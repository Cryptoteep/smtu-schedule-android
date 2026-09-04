package com.korabel.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the server-rendered schedule pages of www.smtu.ru (the site has no
 * API).
 *
 *   /ru/listschedule/                     -> group ids and names
 *   /ru/viewschedule_new/&lt;gid&gt;/           -> a group's full-semester schedule
 *   /ru/viewschedule_new/teacher/&lt;pid&gt;/   -> a teacher's full-semester schedule
 *
 * Each schedule page renders the same data twice: as cards (#card-container)
 * and as tables (#table-container). This parser reads the table, which is both
 * regular and strictly richer — it is the only view that names the group of
 * each lesson, so a teacher's schedule stays usable. The card view is kept as a
 * fallback in case the table disappears.
 *
 *   &lt;tr class="js-week-container js-week-1"&gt;           (1 = верхняя, 2 = нижняя)
 *     &lt;th&gt;08:30 - 10:00&lt;/th&gt;
 *     &lt;td&gt;верхняя&lt;/td&gt;
 *     &lt;td title="14.09.2026, 28.09.2026, …"&gt;14 сентября — 21 декабря 2026&lt;/td&gt;
 *     &lt;td&gt;167 Корпус У&lt;/td&gt;
 *     &lt;td&gt;12826-11&lt;/td&gt;
 *     &lt;td&gt;&lt;span&gt;Предмет&lt;/span&gt;&lt;br&gt;&lt;small class="text-muted"&gt;Лекция&lt;/small&gt;&lt;/td&gt;
 *     &lt;td&gt;&lt;a href='/ru/viewperson/105760/'&gt;Фамилия Имя Отчество&lt;/a&gt;&lt;/td&gt;
 *
 * Columns are located by their header text, so an added or reordered column
 * does not silently shift the data.
 *
 * Plain Java, no Android APIs: covered by JVM unit tests against saved pages.
 */
public final class ScheduleParser {

    private static final Pattern DAY_BLOCK   = Pattern.compile("js-day-block");
    private static final Pattern HEADING     = Pattern.compile("<h[23][^>]*>(.*?)</h[23]>", Pattern.DOTALL);
    private static final Pattern COL_HEADER  = Pattern.compile("<th[^>]*scope=\"col\"[^>]*>(.*?)</th>", Pattern.DOTALL);
    private static final Pattern ROW         = Pattern.compile("<tr[^>]*class=\"[^\"]*js-week-([12])[^\"]*\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL        = Pattern.compile("<t([hd])([^>]*)>(.*?)</t\\1>", Pattern.DOTALL);
    private static final Pattern PERSON_LINK = Pattern.compile("/ru/viewperson/(\\d+)/");
    private static final Pattern GROUP_LINK  = Pattern.compile("<a[^>]+href=\"/ru/viewschedule_new/(\\d+)/\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TIME_SPAN   = Pattern.compile("(\\d\\d:\\d\\d)\\s*-\\s*(\\d\\d:\\d\\d)");
    private static final Pattern DATE        = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");
    private static final Pattern H1          = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL);
    private static final Pattern CARD_SUBJ   = Pattern.compile("<h3[^>]*class=\"[^\"]*h6[^\"]*\"[^>]*>(.*?)</h3>", Pattern.DOTALL);
    private static final Pattern CARD_DATES  = Pattern.compile("<p([^>]*title=\"[^\"]*\"[^>]*)>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern CARD_ROOM   = Pattern.compile("<p[^>]*class=\"card-text[^\"]*\"[^>]*>\\s*<em>(.*?)</em>", Pattern.DOTALL);
    private static final Pattern CARD_TYPE   = Pattern.compile("<small[^>]*class=\"text-muted\"[^>]*>(.*?)</small>", Pattern.DOTALL);
    private static final Pattern CARD_PERSON = Pattern.compile("<a[^>]+href=[\"']/ru/viewperson/(\\d+)/[\"'][^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern WEEK_CLASS  = Pattern.compile("js-week-([12])");
    private static final Pattern BR          = Pattern.compile("(?i)<br\\s*/?>");
    /** "Фамилия Имя Отчество" or "Фамилия И О" — used to tell a teacher from a remark. */
    private static final Pattern FIO         = Pattern.compile("^\\p{Lu}[\\p{L}-]+(\\s+\\p{Lu}[\\p{L}]*\\.?){1,3}$");

    private ScheduleParser() { }

    // ---------------------------------------------------------------- groups

    /** All groups from /ru/listschedule/, de-duplicated and naturally sorted. */
    public static List<Group> parseGroups(String html) {
        Map<String, Group> byId = new LinkedHashMap<>();
        Matcher m = GROUP_LINK.matcher(html);
        while (m.find()) {
            String name = Html.text(m.group(2));
            if (!name.isEmpty()) byId.put(m.group(1), new Group(m.group(1), name));
        }
        List<Group> out = new ArrayList<>(byId.values());
        Collections.sort(out);
        return out;
    }

    /**
     * "Расписание занятий группы 12826-11" -&gt; "12826-11".
     *
     * Teacher pages are headed "Расписание занятий преподаватель" with no name,
     * so this returns "" there and the caller falls back to the teacher named
     * by the lessons themselves.
     */
    public static String parseTitle(String html) {
        Matcher m = H1.matcher(html);
        while (m.find()) {
            String t = Html.text(m.group(1));
            int i = t.indexOf("Расписание занятий");
            if (i < 0) continue;
            t = t.substring(i + "Расписание занятий".length()).trim();
            for (String prefix : new String[]{"группы", "группа", "преподавателя", "преподаватель",
                                              "аудитории", "аудитория"})
                if (t.startsWith(prefix)) {
                    t = t.substring(prefix.length()).trim();
                    break;
                }
            return t;
        }
        return "";
    }

    // -------------------------------------------------------------- schedule

    /** Lessons of a group or teacher page: table view, card view as fallback. */
    public static List<Lesson> parseSchedule(String html) {
        List<Lesson> out = parseTable(html);
        if (out.isEmpty()) out = parseCards(html);
        Collections.sort(out);
        return out;
    }

    // --------------------------------------------------------- table (primary)

    static List<Lesson> parseTable(String html) {
        List<Lesson> out = new ArrayList<>();
        String section = section(html, "id=\"table-container\"");
        for (String block : dayBlocks(section)) {
            String day = firstHeading(block);
            int[] col = columns(block);
            Matcher row = ROW.matcher(block);
            while (row.find()) {
                Lesson l = parseRow(row.group(2), col);
                if (l == null) continue;
                l.day = day;
                l.upper = "1".equals(row.group(1));
                out.add(l);
            }
        }
        return out;
    }

    /** Column indexes {time, dates, room, group, subject, teacher} for one table. */
    private static int[] columns(String block) {
        int[] idx = {0, 2, 3, 4, 5, 6};              // the site's current layout
        Matcher m = COL_HEADER.matcher(block);
        int i = 0;
        while (m.find() && i < 16) {
            String name = Html.text(m.group(1)).toLowerCase(Locale.ROOT);
            if (name.startsWith("время")) idx[0] = i;
            else if (name.startsWith("дат")) idx[1] = i;
            else if (name.startsWith("аудитор")) idx[2] = i;
            else if (name.startsWith("групп")) idx[3] = i;
            else if (name.startsWith("предмет") || name.startsWith("дисциплин")) idx[4] = i;
            else if (name.startsWith("преподават")) idx[5] = i;
            i++;
        }
        return idx;
    }

    private static Lesson parseRow(String row, int[] col) {
        List<String> body = new ArrayList<>();
        List<String> attrs = new ArrayList<>();
        Matcher c = CELL.matcher(row);
        while (c.find()) {
            attrs.add(c.group(2));
            body.add(c.group(3));
        }
        if (body.size() < 3) return null;

        Lesson l = new Lesson();
        l.time = time(cell(body, col[0]));
        l.dateRange = Html.text(cell(body, col[1]));
        addDates(l, Html.attr(cell(attrs, col[1]), "title"));
        l.room = Html.text(cell(body, col[2]));
        l.group = Html.text(cell(body, col[3]));

        // subject cell: <span>Предмет</span><br><small class="text-muted">Тип</small>
        //               [<br><small>Преподаватель или примечание</small>]
        List<String> lines = lines(cell(body, col[4]));
        if (!lines.isEmpty()) l.subject = lines.get(0);
        if (lines.size() > 1) l.type = lines.get(1);
        for (int i = 2; i < lines.size(); i++) assignExtra(l, lines.get(i));

        String teacherCell = cell(body, col[5]);
        String teacherName = Html.text(teacherCell);
        if (!teacherName.isEmpty()) l.teacher = teacherName;
        Matcher p = PERSON_LINK.matcher(teacherCell);
        if (p.find()) l.teacherId = p.group(1);

        return l.subject.isEmpty() ? null : l;
    }

    /** A trailing &lt;small&gt; is either the teacher (no card on the site) or a remark. */
    private static void assignExtra(Lesson l, String extra) {
        if (extra.isEmpty()) return;
        if (l.teacher.isEmpty() && FIO.matcher(extra).matches()) l.teacher = extra;
        else l.note = l.note.isEmpty() ? extra : l.note + "; " + extra;
    }

    // ---------------------------------------------------------- card (fallback)

    static List<Lesson> parseCards(String html) {
        List<Lesson> out = new ArrayList<>();
        String section = section(html, "id=\"card-container\"");
        for (String block : dayBlocks(section)) {
            String day = firstHeading(block);
            for (String card : split(block, "js-time-card")) {
                String time = time(card);
                if (time.isEmpty()) continue;
                for (String week : split(card, "js-week-container")) {
                    Matcher w = WEEK_CLASS.matcher(week);
                    if (!w.find()) continue;
                    Lesson l = parseCardLesson(week);
                    if (l == null) continue;
                    l.day = day;
                    l.time = time;
                    l.upper = "1".equals(w.group(1));
                    out.add(l);
                }
            }
        }
        return out;
    }

    private static Lesson parseCardLesson(String chunk) {
        Lesson l = new Lesson();
        Matcher m = CARD_SUBJ.matcher(chunk);
        if (m.find()) {
            List<String> lines = lines(m.group(1));
            if (!lines.isEmpty()) l.subject = lines.get(0);
            for (int i = 1; i < lines.size(); i++) assignExtra(l, lines.get(i));
        }
        m = CARD_DATES.matcher(chunk);
        if (m.find()) {
            addDates(l, Html.attr(m.group(1), "title"));
            String body = m.group(2);
            int cal = body.indexOf("fa-calendar");
            if (cal >= 0) {
                int close = body.indexOf("</i>", cal);
                if (close > 0) l.dateRange = Html.text(body.substring(close + 4));
            }
        }
        m = CARD_ROOM.matcher(chunk);
        if (m.find()) {
            String em = m.group(1);
            Matcher t = CARD_TYPE.matcher(em);
            if (t.find()) {
                l.type = Html.text(t.group(1));
                l.room = Html.text(em.substring(0, t.start()));
            } else {
                l.room = Html.text(em);
            }
        }
        m = CARD_PERSON.matcher(chunk);
        if (m.find()) {
            l.teacherId = m.group(1);
            String name = Html.text(m.group(2));
            if (!name.isEmpty()) l.teacher = name;
        }
        return l.subject.isEmpty() ? null : l;
    }

    // ----------------------------------------------------------------- shared

    /** The chunk of the page starting at `marker`, stopping before the footer. */
    private static String section(String html, String marker) {
        int start = html.indexOf(marker);
        if (start < 0) return "";
        int end = html.indexOf("</main", start);
        if (end < 0) end = html.indexOf("<footer", start);
        return end < 0 ? html.substring(start) : html.substring(start, end);
    }

    /** The section split into per-weekday blocks. */
    private static List<String> dayBlocks(String section) {
        List<String> out = new ArrayList<>();
        Matcher m = DAY_BLOCK.matcher(section);
        int prev = -1;
        while (m.find()) {
            if (prev >= 0) out.add(section.substring(prev, m.start()));
            prev = m.start();
        }
        if (prev >= 0) out.add(section.substring(prev));
        return out;
    }

    private static List<String> split(String block, String marker) {
        List<String> out = new ArrayList<>();
        int prev = -1;
        for (int i = block.indexOf(marker); i >= 0; i = block.indexOf(marker, i + marker.length())) {
            if (prev >= 0) out.add(block.substring(prev, i));
            prev = i;
        }
        if (prev >= 0) out.add(block.substring(prev));
        return out;
    }

    private static String firstHeading(String block) {
        Matcher m = HEADING.matcher(block);
        return m.find() ? Html.text(m.group(1)) : "";
    }

    /** "&lt;span&gt;08:30 - 10:00&lt;/span&gt;" -&gt; "08:30 - 10:00". */
    private static String time(String chunk) {
        Matcher m = TIME_SPAN.matcher(Html.text(chunk));
        return m.find() ? m.group(1) + " - " + m.group(2) : "";
    }

    private static void addDates(Lesson l, String title) {
        if (title == null || title.isEmpty()) return;
        Matcher m = DATE.matcher(title);
        while (m.find()) {
            long d = Dates.parseRu(m.group());
            if (d != Dates.NO_DATE && !l.days.contains(d)) l.days.add(d);
        }
        Collections.sort(l.days);
    }

    /** Cell text split on &lt;br&gt;, tags stripped, empties dropped. */
    private static List<String> lines(String cellHtml) {
        List<String> out = new ArrayList<>();
        for (String part : BR.split(cellHtml)) {
            String t = Html.text(part);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String cell(List<String> cells, int i) {
        return i >= 0 && i < cells.size() ? cells.get(i) : "";
    }
}
