/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8020156 8020009
 * @run testng SpliteratorCharacteristics
 */

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.testng.Assert.*;

@Test
public class SpliteratorCharacteristics {

    public void testTreeMap() {
        TreeMap<Integer, String> tm = new TreeMap<>();
        tm.put(1, "4");
        tm.put(2, "3");
        tm.put(3, "2");
        tm.put(4, "1");

        assertCharacteristics(tm.keySet(),
                              Spliterator.SIZED | Spliterator.DISTINCT |
                              Spliterator.SORTED | Spliterator.ORDERED);
        assertNullComparator(tm.keySet());

        assertCharacteristics(tm.values(),
                              Spliterator.SIZED | Spliterator.ORDERED);
        assertISEComparator(tm.values());

        assertCharacteristics(tm.entrySet(),
                              Spliterator.SIZED | Spliterator.DISTINCT |
                              Spliterator.SORTED | Spliterator.ORDERED);
        assertNotNullComparator(tm.entrySet());
    }

    public void testTreeMapWithComparator() {
        TreeMap<Integer, String> tm = new TreeMap<>(Comparator.<Integer>reverseOrder());
        tm.put(1, "4");
        tm.put(2, "3");
        tm.put(3, "2");
        tm.put(4, "1");

        assertCharacteristics(tm.keySet(),
                              Spliterator.SIZED | Spliterator.DISTINCT |
                              Spliterator.SORTED | Spliterator.ORDERED);
        assertNotNullComparator(tm.keySet());

        assertCharacteristics(tm.values(),
                              Spliterator.SIZED | Spliterator.ORDERED);
        assertISEComparator(tm.values());

        assertCharacteristics(tm.entrySet(),
                              Spliterator.SIZED | Spliterator.DISTINCT |
                              Spliterator.SORTED | Spliterator.ORDERED);
        assertNotNullComparator(tm.entrySet());
    }

    public void testTreeSet() {
        TreeSet<Integer> ts = new TreeSet<>();
        ts.addAll(Arrays.asList(1, 2, 3, 4));

        assertCharacteristics(ts,
                              Spliterator.SIZED | Spliterator.DISTINCT |
                              Spliterator.SORTED | Spliterator.ORDERED);
        assertNullComparator(ts);
    }

    public void testTreeSetWithComparator() {
        TreeSet<Integer> ts = new TreeSet<>(Comparator.reverseOrder());
        ts.addAll(Arrays.asList(1, 2, 3, 4));

        assertCharacteristics(ts,
                              Spliterator.SIZED | Spliterator.DISTINCT |
                              Spliterator.SORTED | Spliterator.ORDERED);
        assertNotNullComparator(ts);
    }


    void assertCharacteristics(Collection<?> c, int expectedCharacteristics) {
        assertCharacteristics(c.spliterator(), expectedCharacteristics);
    }

    void assertCharacteristics(Spliterator<?> s, int expectedCharacteristics) {
        assertTrue(s.hasCharacteristics(expectedCharacteristics));
    }

    void assertNullComparator(Collection<?> c) {
        assertNullComparator(c.spliterator());
    }

    void assertNullComparator(Spliterator<?> s) {
        assertNull(s.getComparator());
    }

    void assertNotNullComparator(Collection<?> c) {
        assertNotNullComparator(c.spliterator());
    }

    void assertNotNullComparator(Spliterator<?> s) {
        assertNotNull(s.getComparator());
    }

    void assertISEComparator(Collection<?> c) {
        assertISEComparator(c.spliterator());
    }

    void assertISEComparator(Spliterator<?> s) {
        boolean caught = false;
        try {
            s.getComparator();
        }
        catch (IllegalStateException e) {
            caught = true;
        }
        assertTrue(caught);
    }
}
