package com.korabel.schedule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Сравнение версий: на нём держится вся проверка обновлений. */
public class VersionTest {

    @Test public void comparesNumbersAndNotStrings() {
        assertTrue("«2.10» новее «2.9» — посимвольно вышло бы наоборот",
                Version.isNewer("2.10", "2.9"));
        assertTrue(Version.isNewer("3.0", "2.9"));
        assertTrue(Version.isNewer("2.5.1", "2.5"));
        assertFalse(Version.isNewer("2.5", "2.5"));
        assertFalse(Version.isNewer("2.4", "2.5"));
    }

    @Test public void toleratesTheTagPrefix() {
        assertTrue(Version.isNewer("v2.6", "2.5"));
        assertFalse(Version.isNewer("v2.5", "2.5"));
        assertArrayEquals(new int[]{2, 5, 0}, Version.parse("v2.5"));
    }

    @Test public void refusesEverythingThatIsNotAVersion() {
        assertNull(Version.parse(null));
        assertNull(Version.parse(""));
        assertNull(Version.parse("v"));
        assertNull(Version.parse("2.5-beta"));
        assertNull(Version.parse("2.5.1.1"));
        assertNull(Version.parse("2..5"));
        assertFalse("непонятная строка не должна выглядеть как обновление",
                Version.isNewer("что-то", "2.5"));
    }
}
