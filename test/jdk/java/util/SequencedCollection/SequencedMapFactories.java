/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.IntStream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @bug 8156081
 * @summary Test convenience static factory methods on SequencedMap.
 * @run testng SequencedMapFactories
 */

public class SequencedMapFactories {

    static final int MAX_ENTRIES = 20; // should be larger than the largest fixed-arg overload
    static String valueFor(int i) {
        // the String literal below should be of length MAX_ENTRIES
        return "abcdefghijklmnopqrst".substring(i, i+1);
    }

    // for "expected" values
    SequencedMap<Integer,String> genMap(int n) {
        SequencedMap<Integer,String> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            result.put(i, valueFor(i));
        }
        return result;
    }

    static void assertSeqMapsCompatible(SequencedMap<?, ?> one, SequencedMap<?, ?> two) {
        assertEquals(one, two);
        assertEquals(two, one);
        for (Iterator<?> i1 = one.entrySet().iterator(), i2 = two.entrySet().iterator(); i1.hasNext() && i2.hasNext();) {
            assertEquals(i1.next(), i2.next());
        }
    }

    // for varargs Map.Entry methods

    @SuppressWarnings("unchecked")
    Map.Entry<Integer,String>[] genEmptyEntryArray1() {
        return (Map.Entry<Integer,String>[])new Map.Entry<?,?>[1];
    }

    @SuppressWarnings("unchecked")
    Map.Entry<Integer,String>[] genEntries(int n) {
        return IntStream.range(0, n)
            .mapToObj(i -> Map.entry(i, valueFor(i)))
            .toArray(Map.Entry[]::new);
    }

    // returns array of [actual, expected]
    static Object[] a(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        return new Object[] { act, exp };
    }

    @DataProvider(name="empty")
    public Iterator<Object[]> empty() {
        return Collections.singletonList(
            a(SequencedMap.of(), genMap(0))
        ).iterator();
    }

    @DataProvider(name="nonempty")
    @SuppressWarnings("unchecked")
    public Iterator<Object[]> nonempty() {
        return Arrays.asList(
            a(SequencedMap.of(0, "a"), genMap(1)),
            a(SequencedMap.of(0, "a", 1, "b"), genMap(2)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c"), genMap(3)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d"), genMap(4)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e"), genMap(5)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f"), genMap(6)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g"), genMap(7)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h"), genMap(8)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i"), genMap(9)),
            a(SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9, "j"), genMap(10)),
            a(SequencedMap.of(9, "j", 8, "i", 7, "h", 6, "g", 5, "f", 4, "e", 3, "d", 2, "c", 1, "b", 0, "a"), genMap(10).reversed()),
            a(SequencedMap.ofEntries(genEntries(MAX_ENTRIES)), genMap(MAX_ENTRIES))
        ).iterator();
    }

    @DataProvider(name="all")
    public Iterator<Object[]> all() {
        List<Object[]> all = new ArrayList<>();
        empty().forEachRemaining(all::add);
        nonempty().forEachRemaining(all::add);
        return all.iterator();
    }

    @Test(dataProvider="all", expectedExceptions=UnsupportedOperationException.class)
    public void cannotPutNew(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        act.put(-1, "xyzzy");
    }

    @Test(dataProvider="nonempty", expectedExceptions=UnsupportedOperationException.class)
    public void cannotPutOld(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        act.put(0, "a");
    }

    @Test(dataProvider="nonempty", expectedExceptions=UnsupportedOperationException.class)
    public void cannotRemove(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        act.remove(act.keySet().iterator().next());
    }

    @Test(dataProvider="all")
    public void contentsMatch(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        assertSeqMapsCompatible(act, exp);
    }

    @Test(dataProvider="all")
    public void containsAllKeys(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        assertTrue(act.keySet().containsAll(exp.keySet()));
        assertTrue(exp.keySet().containsAll(act.keySet()));
    }

    @Test(dataProvider="all")
    public void containsAllValues(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        assertTrue(act.values().containsAll(exp.values()));
        assertTrue(exp.values().containsAll(act.values()));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed2() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 0, "b");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed3() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 0, "c");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed4() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 0, "d");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed5() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 0, "e");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed6() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          0, "f");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed7() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 0, "g");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed8() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 0, "h");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed9() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, "h", 0, "i");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowed10() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, "h", 8, "i", 0, "j");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void dupKeysDisallowedN() {
        Map.Entry<Integer,String>[] entries = genEntries(MAX_ENTRIES);
        entries[MAX_ENTRIES-1] = Map.entry(0, "xxx");
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(dataProvider="all")
    public void hashCodeEquals(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        assertEquals(act.hashCode(), exp.hashCode());
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed1() {
        SequencedMap<Integer, String> map = SequencedMap.of(null, "a");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed1() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed2() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", null, "b");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed2() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed3() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", null, "c");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed3() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed4() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", null, "d");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed4() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed5() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", null, "e");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed5() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed6() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          null, "f");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed6() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed7() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", null, "g");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed7() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed8() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", null, "h");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed8() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed9() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, "h", null, "i");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed9() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, "h", 8, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowed10() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, "h", 8, "i", null, "j");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowed10() {
        SequencedMap<Integer, String> map = SequencedMap.of(0, "a", 1, "b", 2, "c", 3, "d", 4, "e",
                                          5, "f", 6, "g", 7, "h", 8, "i", 9, null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowedVar1() {
        Map.Entry<Integer,String>[] entries = genEmptyEntryArray1();
        entries[0] = new AbstractMap.SimpleImmutableEntry<>(null, "a");
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowedVar1() {
        Map.Entry<Integer,String>[] entries = genEmptyEntryArray1();
        entries[0] = new AbstractMap.SimpleImmutableEntry<>(0, null);
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullEntryDisallowedVar1() {
        Map.Entry<Integer,String>[] entries = genEmptyEntryArray1();
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullKeyDisallowedVarN() {
        Map.Entry<Integer,String>[] entries = genEntries(MAX_ENTRIES);
        entries[0] = new AbstractMap.SimpleImmutableEntry<>(null, "a");
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullValueDisallowedVarN() {
        Map.Entry<Integer,String>[] entries = genEntries(MAX_ENTRIES);
        entries[0] = new AbstractMap.SimpleImmutableEntry<>(0, null);
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullEntryDisallowedVarN() {
        Map.Entry<Integer,String>[] entries = genEntries(MAX_ENTRIES);
        entries[5] = null;
        SequencedMap<Integer, String> map = SequencedMap.ofEntries(entries);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void nullArrayDisallowed() {
        SequencedMap.ofEntries((Map.Entry<?,?>[])null);
    }

    @Test(dataProvider="all", expectedExceptions=NullPointerException.class)
    public void containsValueNullShouldThrowNPE(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        act.containsValue(null);
    }

    @Test(dataProvider="all", expectedExceptions=NullPointerException.class)
    public void containsKeyNullShouldThrowNPE(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        act.containsKey(null);
    }

    @Test(dataProvider="all", expectedExceptions=NullPointerException.class)
    public void getNullShouldThrowNPE(SequencedMap<Integer,String> act, SequencedMap<Integer,String> exp) {
        act.get(null);
    }

    @Test(dataProvider="all")
    public void serialEquality(SequencedMap<Integer, String> act, SequencedMap<Integer, String> exp) {
        // assume that act.equals(exp) tested elsewhere
        SequencedMap<Integer, String> copy = serialClone(act);
        assertSeqMapsCompatible(act, copy);
    }

    @SuppressWarnings("unchecked")
    static <T> T serialClone(T obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(obj);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    SequencedMap<Integer, String> genMap() {
        SequencedMap<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");
        return map;
    }

    @Test
    public void copyOfResultsEqual() {
        SequencedMap<Integer, String> orig = genMap();
        SequencedMap<Integer, String> copy = SequencedMap.copyOf(orig);

        assertSeqMapsCompatible(copy, orig);
    }

    @Test
    public void copyOfModifiedUnequal() {
        SequencedMap<Integer, String> orig = genMap();
        SequencedMap<Integer, String> copy = SequencedMap.copyOf(orig);
        orig.put(4, "d");

        assertNotEquals(orig, copy);
        assertNotEquals(copy, orig);
    }

    @Test
    public void copyOfIdentity() {
        SequencedMap<Integer, String> orig = genMap();
        SequencedMap<Integer, String> copy1 = SequencedMap.copyOf(orig);
        SequencedMap<Integer, String> copy2 = SequencedMap.copyOf(copy1);

        assertNotSame(orig, copy1);
        assertSame(copy1, copy2);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void copyOfRejectsNullMap() {
        SequencedMap<Integer, String> map = SequencedMap.copyOf(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void copyOfRejectsNullKey() {
        SequencedMap<Integer, String> map = genMap();
        map.put(null, "x");
        SequencedMap<Integer, String> copy = SequencedMap.copyOf(map);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void copyOfRejectsNullValue() {
        SequencedMap<Integer, String> map = genMap();
        map.put(-1, null);
        SequencedMap<Integer, String> copy = SequencedMap.copyOf(map);
    }

    // compile-time test of wildcards
    @Test
    public void entryWildcardTests() {
        Map.Entry<Integer,Double> e1 = Map.entry(1, 2.0);
        Map.Entry<Float,Long> e2 = Map.entry(3.0f, 4L);
        SequencedMap<Number,Number> map = SequencedMap.ofEntries(e1, e2);
        assertEquals(map.size(), 2);
    }
}
