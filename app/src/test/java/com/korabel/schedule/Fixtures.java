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
