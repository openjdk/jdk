/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.hpack;

import jdk.internal.net.http.hpack.SimpleHeaderTable.HeaderField;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class HeaderTableTest extends SimpleHeaderTableTest {

    @Override
    protected HeaderTable createHeaderTable(int maxSize) {
        return new HeaderTable(maxSize, HPACK.getLogger());
    }

    @Test
    public void staticData() {
        HeaderTable table = createHeaderTable(0);
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
    public void lowerIndexPriority() {
        HeaderTable table = createHeaderTable(256);
        int oldLength = table.length();
        table.put("bender", "rodriguez");
        table.put("bender", "rodriguez");
        table.put("bender", "rodriguez");

        assertEquals(table.length(), oldLength + 3); // more like an assumption
        int i = table.indexOf("bender", "rodriguez");
        assertEquals(i, oldLength + 1);
    }

    @Test
    public void indexesAreNotLost2() {
        HeaderTable table = createHeaderTable(256);
        int oldLength = table.length();
        table.put("bender", "rodriguez");
        assertEquals(table.indexOf("bender", "rodriguez"), oldLength + 1);
        table.put("bender", "rodriguez");
        assertEquals(table.indexOf("bender", "rodriguez"), oldLength + 1);
        table.evictEntry();
        assertEquals(table.indexOf("bender", "rodriguez"), oldLength + 1);
        table.evictEntry();
        assertEquals(table.indexOf("bender", "rodriguez"), 0);
    }

    @Test
    public void lowerIndexPriority2() {
        HeaderTable table = createHeaderTable(256);
        int oldLength = table.length();
        int idx = rnd.nextInt(oldLength) + 1;
        HeaderField f = table.get(idx);
        table.put(f.name, f.value);
        assertEquals(table.length(), oldLength + 1);
        int i = table.indexOf(f.name, f.value);
        assertEquals(i, idx);
    }

    @Test
    public void indexOf() {
        // Let's put a series of header fields
        int NUM_HEADERS = 32;
        HeaderTable table =
                createHeaderTable((32 + 4) * NUM_HEADERS);
        //                          ^   ^
        //             entry overhead   symbols per entry (max 2x2 digits)
        for (int i = 1; i <= NUM_HEADERS; i++) {
            String s = String.valueOf(i);
            table.put(s, s);
        }
        // and verify indexOf (reverse lookup) returns correct indexes for
        // full lookup
        for (int j = 1; j <= NUM_HEADERS; j++) {
            String s = String.valueOf(j);
            int actualIndex = table.indexOf(s, s);
            int expectedIndex = STATIC_TABLE_LENGTH + NUM_HEADERS - j + 1;
            assertEquals(actualIndex, expectedIndex);
        }
        // as well as for just a name lookup
        for (int j = 1; j <= NUM_HEADERS; j++) {
            String s = String.valueOf(j);
            int actualIndex = table.indexOf(s, "blah");
            int expectedIndex = -(STATIC_TABLE_LENGTH + NUM_HEADERS - j + 1);
            assertEquals(actualIndex, expectedIndex);
        }
        // lookup for non-existent name returns 0
        assertEquals(table.indexOf("chupacabra", "1"), 0);
    }
}
