package com.korabel.schedule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Real pages saved from www.smtu.ru (autumn semester 2026/2027), trimmed to the
 * schedule containers. Parser tests run against these instead of hand-written
 * HTML so a change in the site's markup shows up as a failing test.
 */
final class Fixtures {

    /** A first-year group: 47 rows, teachers linked to their person pages. */
    static final String GROUP_12826_11 = load("group_7798.html");
    /** A master's group: teachers printed inside the subject cell, no person link. */
    static final String GROUP_12815_55 = load("group_7843.html");
    /** A teacher's own page: same table, and the group column is the only place the group appears. */
    static final String TEACHER_MANUKYAN = load("teacher_102710.html");
    /** /ru/listschedule/ trimmed to the list of group links. */
    static final String GROUP_LIST = load("listschedule.html");

    // --- вёрстка, которую университет включил 15.09.2026 -------------------
    // Без точных дат, чётность в id строки, появились занятия «обе недели»,
    // аудитория пишется корпусом вперёд («У 167»).

    /** Та же группа 12226-11 (её переименовали из 12826-11): 32 строки. */
    static final String GROUP_V2 = load("group_7798_v2.html");
    /** Страница преподавателя в новой вёрстке. */
    static final String TEACHER_V2 = load("teacher_105760_v2.html");
    /** Список групп в новой вёрстке. */
    static final String GROUP_LIST_V2 = load("listschedule_v2.html");

    private Fixtures() { }

    static String load(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IllegalStateException("missing fixture: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
