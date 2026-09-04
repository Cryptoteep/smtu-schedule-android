package com.korabel.schedule;

/**
 * A study group as listed on /ru/listschedule/: the numeric page id and the
 * name students actually know ("12826-11").
 */
public final class Group implements Comparable<Group> {

    public final String id;
    public final String name;

    public Group(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** Natural order: digit runs compare as numbers, so 9 sorts before 12. */
    @Override public int compareTo(Group o) {
        String a = name, b = o.name;
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int c = na.compareTo(nb);
                if (c != 0) return c;
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Group && id.equals(((Group) o).id);
    }

    @Override public int hashCode() {
        return id.hashCode();
    }

    @Override public String toString() {
        return name;
    }
}
