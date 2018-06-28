/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.util.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.graalvm.util.CollectionsUtil;
import org.junit.Assert;
import org.junit.Test;

public class CollectionUtilTest {

    private static int sum(Iterable<Integer> iterable) {
        int sum = 0;
        for (int i : iterable) {
            sum += i;
        }
        return sum;
    }

    private static int indexOf(Iterable<Integer> iterable, int element) {
        int index = 0;
        for (int i : iterable) {
            if (i == element) {
                return index;
            }
            index++;
        }
        return -1;
    }

    @Test
    public void testConcat() {
        List<Integer> a = Arrays.asList(1, 2);
        List<Integer> b = Arrays.asList(3, 4, 5);
        Assert.assertEquals(sum(CollectionsUtil.concat(a, b)), 15);
        Assert.assertEquals(sum(CollectionsUtil.concat(b, a)), 15);
        Assert.assertEquals(indexOf(CollectionsUtil.concat(a, b), 5), 4);
        Assert.assertEquals(indexOf(CollectionsUtil.concat(b, a), 5), 2);
    }

    @Test
    public void testMatch() {
        String[] array = {"a", "b", "c", "d", "e"};
        Assert.assertTrue(CollectionsUtil.allMatch(array, s -> !s.isEmpty()));
        Assert.assertFalse(CollectionsUtil.allMatch(array, s -> !s.startsWith("c")));
        Assert.assertFalse(CollectionsUtil.anyMatch(array, String::isEmpty));
        Assert.assertTrue(CollectionsUtil.anyMatch(array, s -> s.startsWith("c")));
    }

    @Test
    public void testFilterToList() {
        String[] array = {"a", "b", "", "d", "e"};
        Assert.assertEquals(CollectionsUtil.filterToList(Arrays.asList(array), String::isEmpty).size(), 1);
    }

    @Test
    public void testFilterAndMapToArray() {
        String[] array = {"a", "b", "", "d", "e"};
        String[] newArray = CollectionsUtil.filterAndMapToArray(array, s -> !s.isEmpty(), String::toUpperCase, String[]::new);
        Assert.assertArrayEquals(newArray, new String[]{"A", "B", "D", "E"});
    }

    @Test
    public void testMapToArray() {
        String[] array = {"a", "b", "c", "d", "e"};
        String[] newArray = CollectionsUtil.mapToArray(array, String::toUpperCase, String[]::new);
        Assert.assertArrayEquals(newArray, new String[]{"A", "B", "C", "D", "E"});
    }

    @Test
    public void testMapAndJoin() {
        String[] array = {"a", "b", "c", "d", "e"};
        Assert.assertEquals(CollectionsUtil.mapAndJoin(array, String::toUpperCase, ", "), "A, B, C, D, E");
        Assert.assertEquals(CollectionsUtil.mapAndJoin(array, String::toUpperCase, ", ", "'"), "'A, 'B, 'C, 'D, 'E");
        Assert.assertEquals(CollectionsUtil.mapAndJoin(array, String::toUpperCase, ", ", "'", "'"), "'A', 'B', 'C', 'D', 'E'");

        Assert.assertEquals(CollectionsUtil.mapAndJoin(Arrays.asList(array), String::toUpperCase, ", "), "A, B, C, D, E");
        Assert.assertEquals(CollectionsUtil.mapAndJoin(Arrays.asList(array), String::toUpperCase, ", ", "'"), "'A, 'B, 'C, 'D, 'E");
    }

    @Test
    public void testIterableConcat() {
        List<String> i1 = Arrays.asList("1", "2", "3");
        List<String> i2 = Arrays.asList();
        List<String> i3 = Arrays.asList("4", "5");
        List<String> i4 = Arrays.asList();
        List<String> i5 = Arrays.asList("6");
        List<String> iNull = null;

        List<String> actual = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        expected.addAll(i1);
        expected.addAll(i2);
        expected.addAll(i3);
        expected.addAll(i4);
        expected.addAll(i5);
        Iterable<String> iterable = CollectionsUtil.concat(Arrays.asList(i1, i2, i3, i4, i5));
        for (String s : iterable) {
            actual.add(s);
        }
        Assert.assertEquals(expected, actual);

        Iterator<String> iter = iterable.iterator();
        while (iter.hasNext()) {
            iter.next();
        }
        try {
            iter.next();
            Assert.fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Expected
        }
        try {
            CollectionsUtil.concat(i1, iNull);
            Assert.fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }

        Iterable<Object> emptyIterable = CollectionsUtil.concat(Collections.emptyList());
        Assert.assertFalse(emptyIterable.iterator().hasNext());
    }

}
