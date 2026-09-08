package com.korabel.schedule;

/**
 * Сравнение номеров версий вида «2.5», «v2.5.1».
 *
 * Отдельным классом — чтобы проверка обновлений (которая живёт в Android и
 * ходит в сеть) не мешала покрыть тестами то единственное место, где легко
 * ошибиться: «2.10» новее «2.9», а «v2.5» и «2.5» — одно и то же.
 */
public final class Version {

    private Version() { }

    /** Больше ли {@code a}, чем {@code b}. Нечисловые строки — всегда false. */
    public static boolean isNewer(String a, String b) {
        int[] x = parse(a), y = parse(b);
        if (x == null || y == null) return false;
        for (int i = 0; i < x.length; i++) if (x[i] != y[i]) return x[i] > y[i];
        return false;
    }

    /** «v2.4.1» → {2, 4, 1}; null, если это вообще не номер версии. */
    public static int[] parse(String version) {
        if (version == null) return null;
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        if (v.isEmpty()) return null;
        String[] parts = v.split("\\.", -1);
        if (parts.length > 3) return null;
        int[] out = {0, 0, 0};
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].trim();
            if (digits.isEmpty() || digits.length() > 6) return null;
            for (int j = 0; j < digits.length(); j++)
                if (digits.charAt(j) < '0' || digits.charAt(j) > '9') return null;
            out[i] = Integer.parseInt(digits);
        }
        return out;
    }
}
