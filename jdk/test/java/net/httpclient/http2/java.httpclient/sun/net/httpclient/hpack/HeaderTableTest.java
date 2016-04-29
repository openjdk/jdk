/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.net.httpclient.hpack;

import org.testng.annotations.Test;
import sun.net.httpclient.hpack.HeaderTable.HeaderField;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static sun.net.httpclient.hpack.TestHelper.*;

public class HeaderTableTest {

    //
    // https://tools.ietf.org/html/rfc7541#appendix-A
    //
    // @formatter:off
    private static final String SPEC =
       "          | 1     | :authority                  |               |\n" +
       "          | 2     | :method                     | GET           |\n" +
       "          | 3     | :method                     | POST          |\n" +
       "          | 4     | :path                       | /             |\n" +
       "          | 5     | :path                       | /index.html   |\n" +
       "          | 6     | :scheme                     | http          |\n" +
       "          | 7     | :scheme                     | https         |\n" +
       "          | 8     | :status                     | 200           |\n" +
       "          | 9     | :status                     | 204           |\n" +
       "          | 10    | :status                     | 206           |\n" +
       "          | 11    | :status                     | 304           |\n" +
       "          | 12    | :status                     | 400           |\n" +
       "          | 13    | :status                     | 404           |\n" +
       "          | 14    | :status                     | 500           |\n" +
       "          | 15    | accept-charset              |               |\n" +
       "          | 16    | accept-encoding             | gzip, deflate |\n" +
       "          | 17    | accept-language             |               |\n" +
       "          | 18    | accept-ranges               |               |\n" +
       "          | 19    | accept                      |               |\n" +
       "          | 20    | access-control-allow-origin |               |\n" +
       "          | 21    | age                         |               |\n" +
       "          | 22    | allow                       |               |\n" +
       "          | 23    | authorization               |               |\n" +
       "          | 24    | cache-control               |               |\n" +
       "          | 25    | content-disposition         |               |\n" +
       "          | 26    | content-encoding            |               |\n" +
       "          | 27    | content-language            |               |\n" +
       "          | 28    | content-length              |               |\n" +
       "          | 29    | content-location            |               |\n" +
       "          | 30    | content-range               |               |\n" +
       "          | 31    | content-type                |               |\n" +
       "          | 32    | cookie                      |               |\n" +
       "          | 33    | date                        |               |\n" +
       "          | 34    | etag                        |               |\n" +
       "          | 35    | expect                      |               |\n" +
       "          | 36    | expires                     |               |\n" +
       "          | 37    | from                        |               |\n" +
       "          | 38    | host                        |               |\n" +
       "          | 39    | if-match                    |               |\n" +
       "          | 40    | if-modified-since           |               |\n" +
       "          | 41    | if-none-match               |               |\n" +
       "          | 42    | if-range                    |               |\n" +
       "          | 43    | if-unmodified-since         |               |\n" +
       "          | 44    | last-modified               |               |\n" +
       "          | 45    | link                        |               |\n" +
       "          | 46    | location                    |               |\n" +
       "          | 47    | max-forwards                |               |\n" +
       "          | 48    | proxy-authenticate          |               |\n" +
       "          | 49    | proxy-authorization         |               |\n" +
       "          | 50    | range                       |               |\n" +
       "          | 51    | referer                     |               |\n" +
       "          | 52    | refresh                     |               |\n" +
       "          | 53    | retry-after                 |               |\n" +
       "          | 54    | server                      |               |\n" +
       "          | 55    | set-cookie                  |               |\n" +
       "          | 56    | strict-transport-security   |               |\n" +
       "          | 57    | transfer-encoding           |               |\n" +
       "          | 58    | user-agent                  |               |\n" +
       "          | 59    | vary                        |               |\n" +
       "          | 60    | via                         |               |\n" +
       "          | 61    | www-authenticate            |               |\n";
    // @formatter:on

    private static final int STATIC_TABLE_LENGTH = createStaticEntries().size();
    private final Random rnd = newRandom();

    @Test
    public void staticData() {
        HeaderTable table = new HeaderTable(0);
        Map<Integer, HeaderField> staticHeaderFields = createStaticEntries();

        Map<String, Integer> minimalIndexes = new HashMap<>();

        for (Map.Entry<Integer, HeaderField> e : staticHeaderFields.entrySet()) {
            Integer idx = e.getKey();
            String hName = e.getValue().name;
            Integer midx = minimalIndexes.get(hName);
            if (midx == null) {
                minimalIndexes.put(hName, idx);
            } else {
                minimalIndexes.put(hName, Math.min(idx, midx));
            }
        }

        staticHeaderFields.entrySet().forEach(
                e -> {
                    // lookup
                    HeaderField actualHeaderField = table.get(e.getKey());
                    HeaderField expectedHeaderField = e.getValue();
                    assertEquals(actualHeaderField, expectedHeaderField);

                    // reverse lookup (name, value)
                    String hName = expectedHeaderField.name;
                    String hValue = expectedHeaderField.value;
                    int expectedIndex = e.getKey();
                    int actualIndex = table.indexOf(hName, hValue);

                    assertEquals(actualIndex, expectedIndex);

                    // reverse lookup (name)
                    int expectedMinimalIndex = minimalIndexes.get(hName);
                    int actualMinimalIndex = table.indexOf(hName, "blah-blah");

                    assertEquals(-actualMinimalIndex, expectedMinimalIndex);
                }
        );
    }

    @Test
    public void constructorSetsMaxSize() {
        int size = rnd.nextInt(64);
        HeaderTable t = new HeaderTable(size);
        assertEquals(t.size(), 0);
        assertEquals(t.maxSize(), size);
    }

    @Test
    public void negativeMaximumSize() {
        int maxSize = -(rnd.nextInt(100) + 1); // [-100, -1]
        IllegalArgumentException e =
                assertVoidThrows(IllegalArgumentException.class,
                        () -> new HeaderTable(0).setMaxSize(maxSize));
        assertExceptionMessageContains(e, "maxSize");
    }

    @Test
    public void zeroMaximumSize() {
        HeaderTable table = new HeaderTable(0);
        table.setMaxSize(0);
        assertEquals(table.maxSize(), 0);
    }

    @Test
    public void negativeIndex() {
        int idx = -(rnd.nextInt(256) + 1); // [-256, -1]
        IllegalArgumentException e =
                assertVoidThrows(IllegalArgumentException.class,
                        () -> new HeaderTable(0).get(idx));
        assertExceptionMessageContains(e, "index");
    }

    @Test
    public void zeroIndex() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class,
                        () -> new HeaderTable(0).get(0));
        assertExceptionMessageContains(e, "index");
    }

    @Test
    public void length() {
        HeaderTable table = new HeaderTable(0);
        assertEquals(table.length(), STATIC_TABLE_LENGTH);
    }

    @Test
    public void indexOutsideStaticRange() {
        HeaderTable table = new HeaderTable(0);
        int idx = table.length() + (rnd.nextInt(256) + 1);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class,
                        () -> table.get(idx));
        assertExceptionMessageContains(e, "index");
    }

    @Test
    public void entryPutAfterStaticArea() {
        HeaderTable table = new HeaderTable(256);
        int idx = table.length() + 1;
        assertThrows(IllegalArgumentException.class, () -> table.get(idx));

        byte[] bytes = new byte[32];
        rnd.nextBytes(bytes);
        String name = new String(bytes, StandardCharsets.ISO_8859_1);
        String value = "custom-value";

        table.put(name, value);
        HeaderField f = table.get(idx);
        assertEquals(name, f.name);
        assertEquals(value, f.value);
    }

    @Test
    public void staticTableHasZeroSize() {
        HeaderTable table = new HeaderTable(0);
        assertEquals(0, table.size());
    }

    @Test
    public void lowerIndexPriority() {
        HeaderTable table = new HeaderTable(256);
        int oldLength = table.length();
        table.put("bender", "rodriguez");
        table.put("bender", "rodriguez");
        table.put("bender", "rodriguez");

        assertEquals(table.length(), oldLength + 3); // more like an assumption
        int i = table.indexOf("bender", "rodriguez");
        assertEquals(oldLength + 1, i);
    }

    @Test
    public void lowerIndexPriority2() {
        HeaderTable table = new HeaderTable(256);
        int oldLength = table.length();
        int idx = rnd.nextInt(oldLength) + 1;
        HeaderField f = table.get(idx);
        table.put(f.name, f.value);
        assertEquals(table.length(), oldLength + 1);
        int i = table.indexOf(f.name, f.value);
        assertEquals(idx, i);
    }

    // TODO: negative indexes check
    // TODO: ensure full table clearance when adding huge header field
    // TODO: ensure eviction deletes minimum needed entries, not more

    @Test
    public void fifo() {
        HeaderTable t = new HeaderTable(Integer.MAX_VALUE);
        // Let's add a series of header fields
        int NUM_HEADERS = 32;
        for (int i = 1; i <= NUM_HEADERS; i++) {
            String s = String.valueOf(i);
            t.put(s, s);
        }
        // They MUST appear in a FIFO order:
        //   newer entries are at lower indexes
        //   older entries are at higher indexes
        for (int j = 1; j <= NUM_HEADERS; j++) {
            HeaderField f = t.get(STATIC_TABLE_LENGTH + j);
            int actualName = Integer.parseInt(f.name);
            int expectedName = NUM_HEADERS - j + 1;
            assertEquals(expectedName, actualName);
        }
        // Entries MUST be evicted in the order they were added:
        //   the newer the entry the later it is evicted
        for (int k = 1; k <= NUM_HEADERS; k++) {
            HeaderField f = t.evictEntry();
            assertEquals(String.valueOf(k), f.name);
        }
    }

    @Test
    public void indexOf() {
        HeaderTable t = new HeaderTable(Integer.MAX_VALUE);
        // Let's put a series of header fields
        int NUM_HEADERS = 32;
        for (int i = 1; i <= NUM_HEADERS; i++) {
            String s = String.valueOf(i);
            t.put(s, s);
        }
        // and verify indexOf (reverse lookup) returns correct indexes for
        // full lookup
        for (int j = 1; j <= NUM_HEADERS; j++) {
            String s = String.valueOf(j);
            int actualIndex = t.indexOf(s, s);
            int expectedIndex = STATIC_TABLE_LENGTH + NUM_HEADERS - j + 1;
            assertEquals(expectedIndex, actualIndex);
        }
        // as well as for just a name lookup
        for (int j = 1; j <= NUM_HEADERS; j++) {
            String s = String.valueOf(j);
            int actualIndex = t.indexOf(s, "blah");
            int expectedIndex = -(STATIC_TABLE_LENGTH + NUM_HEADERS - j + 1);
            assertEquals(expectedIndex, actualIndex);
        }
        // lookup for non-existent name returns 0
        assertEquals(0, t.indexOf("chupacabra", "1"));
    }

    @Test
    public void testToString() {
        HeaderTable table = new HeaderTable(0);
        {
            table.setMaxSize(2048);
            assertEquals("entries: 0; used 0/2048 (0.0%)", table.toString());
        }

        {
            String name = "custom-name";
            String value = "custom-value";
            int size = 512;

            table.setMaxSize(size);
            table.put(name, value);
            String s = table.toString();

            int used = name.length() + value.length() + 32;
            double ratio = used * 100.0 / size;

            String expected = format("entries: 1; used %s/%s (%.1f%%)", used, size, ratio);
            assertEquals(expected, s);
        }

        {
            table.setMaxSize(78);
            table.put(":method", "");
            table.put(":status", "");
            String s = table.toString();
            assertEquals("entries: 2; used 78/78 (100.0%)", s);
        }
    }

    @Test
    public void stateString() {
        HeaderTable table = new HeaderTable(256);
        table.put("custom-key", "custom-header");
        // @formatter:off
        assertEquals("[  1] (s =  55) custom-key: custom-header\n" +
                     "      Table size:  55", table.getStateString());
        // @formatter:on
    }

    private static Map<Integer, HeaderField> createStaticEntries() {
        Pattern line = Pattern.compile(
                "\\|\\s*(?<index>\\d+?)\\s*\\|\\s*(?<name>.+?)\\s*\\|\\s*(?<value>.*?)\\s*\\|");
        Matcher m = line.matcher(SPEC);
        Map<Integer, HeaderField> result = new HashMap<>();
        while (m.find()) {
            int index = Integer.parseInt(m.group("index"));
            String name = m.group("name");
            String value = m.group("value");
            HeaderField f = new HeaderField(name, value);
            result.put(index, f);
        }
        return Collections.unmodifiableMap(result); // lol
    }
}
