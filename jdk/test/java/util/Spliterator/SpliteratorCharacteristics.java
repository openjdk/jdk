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
 * @bug 8020156 8020009 8022326
 * @run testng SpliteratorCharacteristics
 */

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.testng.Assert.*;

@Test
public class SpliteratorCharacteristics {

    // TreeMap

    public void testTreeMap() {
        assertSortedMapCharacteristics(new TreeMap<>(),
                                       Spliterator.SIZED | Spliterator.DISTINCT |
                                       Spliterator.SORTED | Spliterator.ORDERED);
    }

    public void testTreeMapWithComparator() {
        assertSortedMapCharacteristics(new TreeMap<>(Comparator.reverseOrder()),
                                       Spliterator.SIZED | Spliterator.DISTINCT |
                                       Spliterator.SORTED | Spliterator.ORDERED);
    }


    // TreeSet

    public void testTreeSet() {
        assertSortedSetCharacteristics(new TreeSet<>(),
                                       Spliterator.SIZED | Spliterator.DISTINCT |
                                       Spliterator.SORTED | Spliterator.ORDERED);
    }

    public void testTreeSetWithComparator() {
        assertSortedSetCharacteristics(new TreeSet<>(Comparator.reverseOrder()),
                                       Spliterator.SIZED | Spliterator.DISTINCT |
                                       Spliterator.SORTED | Spliterator.ORDERED);
    }


    // ConcurrentSkipListMap

    public void testConcurrentSkipListMap() {
        assertSortedMapCharacteristics(new ConcurrentSkipListMap<>(),
                                       Spliterator.CONCURRENT | Spliterator.NONNULL |
                                       Spliterator.DISTINCT | Spliterator.SORTED |
                                       Spliterator.ORDERED);
    }

    public void testConcurrentSkipListMapWithComparator() {
        assertSortedMapCharacteristics(new ConcurrentSkipListMap<>(Comparator.<Integer>reverseOrder()),
                                       Spliterator.CONCURRENT | Spliterator.NONNULL |
                                       Spliterator.DISTINCT | Spliterator.SORTED |
                                       Spliterator.ORDERED);
    }


    // ConcurrentSkipListSet

    public void testConcurrentSkipListSet() {
        assertSortedSetCharacteristics(new ConcurrentSkipListSet<>(),
                                       Spliterator.CONCURRENT | Spliterator.NONNULL |
                                       Spliterator.DISTINCT | Spliterator.SORTED |
                                       Spliterator.ORDERED);
    }

    public void testConcurrentSkipListSetWithComparator() {
        assertSortedSetCharacteristics(new ConcurrentSkipListSet<>(Comparator.reverseOrder()),
                                       Spliterator.CONCURRENT | Spliterator.NONNULL |
                                       Spliterator.DISTINCT | Spliterator.SORTED |
                                       Spliterator.ORDERED);
    }


    //

    void assertSortedMapCharacteristics(SortedMap<Integer, String> m, int keyCharacteristics) {
        initMap(m);

        boolean hasComparator = m.comparator() != null;

        Set<Integer> keys = m.keySet();
        assertCharacteristics(keys, keyCharacteristics);
        if (hasComparator) {
            assertNotNullComparator(keys);
        }
        else {
            assertNullComparator(keys);
        }

        assertCharacteristics(m.values(),
                              keyCharacteristics & ~(Spliterator.DISTINCT | Spliterator.SORTED));
        assertISEComparator(m.values());

        assertCharacteristics(m.entrySet(), keyCharacteristics);
        assertNotNullComparator(m.entrySet());
    }

    void assertSortedSetCharacteristics(SortedSet<Integer> s, int keyCharacteristics) {
        initSet(s);

        boolean hasComparator = s.comparator() != null;

        assertCharacteristics(s, keyCharacteristics);
        if (hasComparator) {
            assertNotNullComparator(s);
        }
        else {
            assertNullComparator(s);
        }
    }

    void initMap(Map<Integer, String> m) {
        m.put(1, "4");
        m.put(2, "3");
        m.put(3, "2");
        m.put(4, "1");
    }

    void initSet(Set<Integer> s) {
        s.addAll(Arrays.asList(1, 2, 3, 4));
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
