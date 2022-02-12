/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/*
 * @test
 * @bug     8266571
 * @summary Basic tests for SequencedMap
 * @modules java.base/java.util:open
 * @build   SimpleSortedMap
 * @run     testng BasicMap
 */

public class BasicMap {

    // ========== Data Providers ==========

    static final List<Map.Entry<String, Integer>> ORIGINAL =
        List.of(Map.entry("a", 1),
                Map.entry("b", 2),
                Map.entry("c", 3),
                Map.entry("d", 4),
                Map.entry("e", 5));

    static Map<String, Integer> load(Map<String, Integer> map, List<Map.Entry<String, Integer>> mappings) {
        for (var e : mappings)
            map.put(e.getKey(), e.getValue());
        return map;
    }

    @DataProvider(name="all")
    public Iterator<Object[]> all() {
        var result = new ArrayList<Object[]>();
        populated().forEachRemaining(result::add);
        empties().forEachRemaining(result::add);
        return result.iterator();
    }

    public Iterator<Object[]> populated() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", load(new LinkedHashMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "SimpleSortedMap", load(new SimpleSortedMap<>(), ORIGINAL), ORIGINAL },
            new Object[] { "TreeMap", load(new TreeMap<>(), ORIGINAL), ORIGINAL }
        ).iterator();
    }

    public Iterator<Object[]> empties() {
        return Arrays.asList(
            new Object[] { "LinkedHashMap", new LinkedHashMap<>(), List.of() },
            new Object[] { "SimpleSortedMap", new SimpleSortedMap<>(), List.of() },
            new Object[] { "TreeMap", new TreeMap<>(), List.of() }
        ).iterator();
    }

//    @DataProvider(name="adds")
//    public Iterator<Object[]> adds() {
//        return Arrays.asList(
//            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
//            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
//            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
//            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
//            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL }
//        ).iterator();
//    }
//
//    @DataProvider(name="removes")
//    public Iterator<Object[]> removes() {
//        return Arrays.asList(
//            new Object[] { "ArrayDeque", new ArrayDeque<>(ORIGINAL), ORIGINAL },
//            new Object[] { "ArrayList", new ArrayList<>(ORIGINAL), ORIGINAL },
//            new Object[] { "LinkedHashSet", new LinkedHashSet<>(ORIGINAL), ORIGINAL },
//            new Object[] { "SimpleDeque", new SimpleDeque<>(ORIGINAL), ORIGINAL },
//            new Object[] { "SimpleList", new SimpleList<>(ORIGINAL), ORIGINAL },
//            new Object[] { "SimpleSortedSet", new SimpleSortedSet<>(ORIGINAL), ORIGINAL },
//            new Object[] { "TreeSet", new TreeSet<>(ORIGINAL), ORIGINAL }
//        ).iterator();
//    }

    // ========== Assertions ==========

    public void checkEntrySet(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        var list1 = new ArrayList<Map.Entry<String, Integer>>();
        map.forEach((k, v) -> list1.add(Map.entry(k, v)));
        assertEquals(list1, ref);

        var list2 = new ArrayList<Map.Entry<String, Integer>>();
        for (var e : map.entrySet())
            list2.add(Map.Entry.copyOf(e));
        assertEquals(list2, ref);

        var list3 = new ArrayList<Map.Entry<String, Integer>>();
        map.entrySet().forEach(e -> list3.add(Map.Entry.copyOf(e)));
        assertEquals(list3, ref);

        var list4 = Arrays.asList(map.entrySet().toArray());
        assertEquals(list4, ref);

        var list5 = Arrays.asList(map.entrySet().toArray(new Map.Entry<?,?>[0]));
        assertEquals(list5, ref);

        var list6 = Arrays.asList(map.entrySet().toArray(Map.Entry[]::new));
        assertEquals(list6, ref);

        var list7 = map.entrySet().stream().toList();
        assertEquals(list7, ref);

        var list8 = map.entrySet().parallelStream().toList();
        assertEquals(list8, ref);

        assertEquals(map.size(), ref.size());
        assertEquals(map.entrySet().size(), ref.size());

        for (var e : ref) {
            assertTrue(map.containsKey(e.getKey()));
            assertEquals(map.get(e.getKey()), e.getValue());
        }

        if (map.isEmpty()) {
            assertEquals(map.size(), 0);
        } else {
            assertTrue(map.size() > 0);
            assertEquals(map.firstEntry(), ref.get(0));
            assertEquals(map.lastEntry(), ref.get(ref.size() - 1));
            assertEquals(map.firstKey(), ref.get(0).getKey());
            assertEquals(map.lastKey(), ref.get(ref.size() - 1).getKey());
        }

        // keySet
        // values
        // individual order-specific methods on SequencedMap
        // forward and reverse
    }

    public void checkKeySet(SequencedMap<String, Integer> map, List<String> refKeys) {
        var mapKeys = map.keySet();

        var list1 = new ArrayList<String>();
        mapKeys.forEach(list1::add);
        assertEquals(list1, refKeys);

        var list2 = new ArrayList<String>();
        for (var k : mapKeys)
            list2.add(k);
        assertEquals(list2, refKeys);

        var list3 = Arrays.asList(mapKeys.toArray());
        assertEquals(list3, refKeys);

        var list4 = Arrays.asList(mapKeys.toArray(new String[0]));
        assertEquals(list4, refKeys);

        var list5 = Arrays.asList(mapKeys.toArray(String[]::new));
        assertEquals(list5, refKeys);

        var list6 = mapKeys.stream().toList();
        assertEquals(list6, refKeys);

        var list7 = mapKeys.parallelStream().toList();
        assertEquals(list7, refKeys);

        assertEquals(mapKeys.size(), refKeys.size());

        for (var k : mapKeys)
            assertTrue(map.containsKey(k));

        if (map.isEmpty()) {
            assertEquals(mapKeys.size(), 0);
        } else {
            assertTrue(mapKeys.size() > 0);
            // TODO when covariant override for keySet is added, add checks for map.keySet().reversed()
            assertEquals(mapKeys.iterator().next(), refKeys.get(0));
            assertEquals(map.reversed().keySet().iterator().next(), refKeys.get(refKeys.size() - 1));
        }
    }

    public void checkValues(SequencedMap<String, Integer> map, List<Integer> refVals) {
        var mapVals = map.values();

        var list1 = new ArrayList<Integer>();
        mapVals.forEach(list1::add);
        assertEquals(list1, refVals);

        var list2 = new ArrayList<Integer>();
        for (var v : mapVals)
            list2.add(v);
        assertEquals(list2, refVals);

        var list3 = Arrays.asList(mapVals.toArray());
        assertEquals(list3, refVals);

        var list4 = Arrays.asList(mapVals.toArray(new Integer[0]));
        assertEquals(list4, refVals);

        var list5 = Arrays.asList(mapVals.toArray(Integer[]::new));
        assertEquals(list5, refVals);

        var list6 = mapVals.stream().toList();
        assertEquals(list6, refVals);

        var list7 = mapVals.parallelStream().toList();
        assertEquals(list7, refVals);

        assertEquals(mapVals.size(), refVals.size());

        for (var k : mapVals)
            assertTrue(map.containsValue(k));

        if (map.isEmpty()) {
            assertEquals(mapVals.size(), 0);
        } else {
            assertTrue(mapVals.size() > 0);
            // TODO when covariant override for values is added, add checks for map.values().reversed()
            assertEquals(mapVals.iterator().next(), refVals.get(0));
            assertEquals(map.reversed().values().iterator().next(), refVals.get(refVals.size() - 1));
        }
    }

    public void checkForward(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkEntrySet(map, ref);
        checkKeySet(map, ref.stream().map(Map.Entry::getKey).toList());
        checkValues(map, ref.stream().map(Map.Entry::getValue).toList());
    }

    public void checkFundamentals(SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkForward(map, ref);

        var rref = new ArrayList<>(ref);
        Collections.reverse(rref);
        var rmap = map.reversed();
        checkForward(rmap, rref);

        var map1 = rmap.reversed();
        checkForward(map1, ref);
    }


    // ========== Tests ==========

    @Test(dataProvider="all")
    public void testFundamentals(String label, SequencedMap<String, Integer> map, List<Map.Entry<String, Integer>> ref) {
        checkEntrySet(map, ref);
        checkKeySet(map, ref.stream().map(Map.Entry::getKey).toList());
        checkValues(map, ref.stream().map(Map.Entry::getValue).toList());
    }
}
