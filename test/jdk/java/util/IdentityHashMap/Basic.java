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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.Map.entry;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8285295
 * @summary Basic tests for IdentityHashMap
 * @run testng Basic
 */

// NOTE: avoid using TestNG's assertEquals/assertNotEquals directly on two IDHM instances,
// as its logic for testing collections equality is suspect. Use checkEntries() to assert
// that a map's entrySet contains exactly the expected mappings. There are no guarantees about
// the identities of Map.Entry instances obtained from the entrySet; however, the keys and
// values they contain are guaranteed to have the right identity.

// TODO remove(k, v)
// TODO replace(k, v1, v2)
// TODO add tests using null keys and values
// TODO deeper testing of view collections including iterators, equals, contains, etc.
// TODO Map.Entry::setValue

public class Basic {
    /*
     * Helpers
     */

    record Box(int i) {
        Box(Box other) {
            this(other.i());
        }
    }

    // Checks that a collection contains exactly the given elements and no others, using the
    // provided predicate for equivalence. Checking is performed both using contains() on the
    // collection and by simple array searching. The latter is O(N^2) so is suitable only for
    // small arrays. No two of the given elements can be equivalent according to the predicate.

    // TODO: read out the elements using iterator and stream and check them too

    @SafeVarargs
    private <E> void checkContents(Collection<E> c, BiPredicate<E,E> p, E... given) {
        @SuppressWarnings("unchecked")
        E[] contents = (E[]) c.toArray();

        assertEquals(c.size(), given.length);
        assertEquals(contents.length, given.length);
        final int LEN = given.length;

        for (E e : given) {
            assertTrue(c.contains(e));
        }

        // Fill indexes array with position of a given element in the contents array,
        // or -1 if the given element cannot be found.

        int[] indexes = new int[LEN];

        outer:
        for (int i = 0; i < LEN; i++) {
            for (int j = 0; j < LEN; j++) {
                if (p.test(given[i], contents[j])) {
                    indexes[i] = j;
                    continue outer;
                }
            }
            indexes[i] = -1;
        }

        // If every given element matches a distinct element in the contents array,
        // the sorted indexes array will be the sequence [0..LEN-1].

        Arrays.sort(indexes);
        assertEquals(indexes, IntStream.range(0, LEN).toArray());
    }

    // Checks that the collection contains the given boxes, by identity.
    private void checkElements(Collection<Box> c, Box... given) {
        checkContents(c, (b1, b2) -> b1 == b2, given);
    }

    // Checks that the collection contains entries that have identical keys and values.
    // The entries themselves are not checked for identity.
    @SafeVarargs
    private void checkEntries(Collection<Map.Entry<Box, Box>> c, Map.Entry<Box, Box>... given) {
        checkContents(c, (e1, e2) -> e1.getKey() == e2.getKey() && e1.getValue() == e2.getValue(), given);
    }

    /*
     * Setup
     */

    final Box k1a = new Box(17);
    final Box k1b = new Box(17); // equals but != k1a
    final Box k2  = new Box(42);

    final Box v1a = new Box(30);
    final Box v1b = new Box(30); // equals but != v1a
    final Box v2  = new Box(99);

    IdentityHashMap<Box, Box> map;
    IdentityHashMap<Box, Box> map2;

    @BeforeMethod
    public void setup() {
        map = new IdentityHashMap<>();
        map.put(k1a, v1a);
        map.put(k1b, v1b);
        map.put(k2,  v2);

        map2 = new IdentityHashMap<>();
        map2.put(k1a, v1a);
        map2.put(k1b, v1b);
        map2.put(k2,  v2);
    }

    /*
     * Tests
     */

    // containsKey
    // containsValue
    // size
    @Test
    public void testSizeContainsKeyValue() {
        assertEquals(map.size(), 3);

        assertTrue(map.containsKey(k1a));
        assertTrue(map.containsKey(k1b));
        assertTrue(map.containsKey(k2));
        assertFalse(map.containsKey(new Box(k1a)));

        assertTrue(map.containsValue(v1a));
        assertTrue(map.containsValue(v1b));
        assertFalse(map.containsValue(new Box(v1a)));
        assertTrue(map.containsValue(v2));
    }

    // get
    @Test
    public void testGet() {
        assertSame(map.get(k1a), v1a);
        assertSame(map.get(k1b), v1b);
        assertSame(map.get(k2), v2);
        assertNull(map.get(new Box(k1a)));
    }

    // getOrDefault
    @Test
    public void testGetOrDefault() {
        Box other = new Box(22);

        assertSame(map.getOrDefault(k1a, other), v1a);
        assertSame(map.getOrDefault(k1b, other), v1b);
        assertSame(map.getOrDefault(new Box(k1a), other), other);
        assertSame(map.getOrDefault(k2, other), v2);
    }

    // clear
    // isEmpty
    @Test
    public void testClearEmpty() {
        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
    }

    // hashCode
    @Test
    public void testHashCode() {
        int expected = (System.identityHashCode(k1a) ^ System.identityHashCode(v1a)) +
                       (System.identityHashCode(k1b) ^ System.identityHashCode(v1b)) +
                       (System.identityHashCode(k2)  ^ System.identityHashCode(v2));
        assertEquals(map.hashCode(), expected);
        assertEquals(map.entrySet().hashCode(), expected);
    }

    // equals
    @Test
    public void testEquals() {
        assertTrue(map.equals(map));
        assertTrue(map.equals(map2));
        assertTrue(map2.equals(map));

        assertTrue(map.keySet().equals(map.keySet()));
        assertTrue(map.keySet().equals(map2.keySet()));
        assertTrue(map2.keySet().equals(map.keySet()));

        assertTrue(map.entrySet().equals(map.entrySet()));
        assertTrue(map.entrySet().equals(map2.entrySet()));
        assertTrue(map2.entrySet().equals(map.entrySet()));
    }

    // equals
    @Test
    public void testEqualsDifferentKey() {
        map2.remove(k1a);
        map2.put(new Box(k1a), v1a);

        assertFalse(map.equals(map2));
        assertFalse(map2.equals(map));

        assertFalse(map.keySet().equals(map2.keySet()));
        assertFalse(map2.keySet().equals(map.keySet()));

        assertFalse(map.entrySet().equals(map2.entrySet()));
        assertFalse(map2.entrySet().equals(map.entrySet()));
    }

    // equals
    @Test
    public void testEqualsDifferentValue() {
        map2.put(k1a, new Box(v1a));

        assertFalse(map.equals(map2));
        assertFalse(map2.equals(map));

        assertTrue(map.keySet().equals(map2.keySet()));
        assertTrue(map2.keySet().equals(map.keySet()));

        assertFalse(map.entrySet().equals(map2.entrySet()));
        assertFalse(map2.entrySet().equals(map.entrySet()));
    }

    // equals
    @Test
    public void testEqualsNewMapping() {
        map.put(new Box(k1a), new Box(v1a));

        assertFalse(map.equals(map2));
        assertFalse(map2.equals(map));

        assertFalse(map.keySet().equals(map2.keySet()));
        assertFalse(map2.keySet().equals(map.keySet()));

        assertFalse(map.entrySet().equals(map2.entrySet()));
        assertFalse(map2.entrySet().equals(map.entrySet()));
    }

    // equals
    @Test
    public void testEqualsMissingMapping() {
        var tmp = new IdentityHashMap<Box, Box>();
        tmp.put(k1a, v1a);
        tmp.put(k1b, v1b);

        assertFalse(map.equals(tmp));
        assertFalse(tmp.equals(map));

        assertFalse(map.keySet().equals(tmp.keySet()));
        assertFalse(tmp.keySet().equals(map.keySet()));

        assertFalse(map.entrySet().equals(tmp.entrySet()));
        assertFalse(tmp.entrySet().equals(map.entrySet()));
    }

    // keySet equals, contains
    @Test
    public void testKeySet() {
        Set<Box> keySet = map.keySet();

        checkElements(keySet, k1a, k1b, k2);
        assertFalse(keySet.contains(new Box(k1a)));
        assertTrue(map.keySet().equals(map2.keySet()));
        assertTrue(map2.keySet().equals(map.keySet()));
    }

    // keySet remove
    @Test
    public void testKeySetNoRemove() {
        Set<Box> keySet = map.keySet();
        boolean r = keySet.remove(new Box(k1a));

        assertFalse(r);
        checkElements(keySet, k1a, k1b, k2);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
        assertTrue(map.keySet().equals(map2.keySet()));
        assertTrue(map2.keySet().equals(map.keySet()));
    }

    // keySet remove
    @Test
    public void testKeySetRemove() {
        Set<Box> keySet = map.keySet();
        boolean r = keySet.remove(k1a);

        assertTrue(r);
        checkElements(keySet, k1b, k2);
        checkEntries(map.entrySet(), entry(k1b, v1b),
                                     entry(k2, v2));
        assertFalse(map.keySet().equals(map2.keySet()));
        assertFalse(map2.keySet().equals(map.keySet()));
    }

    // values
    @Test
    public void testValues() {
        Collection<Box> values = map.values();
        checkElements(values, v1a, v1b, v2);
        assertFalse(values.contains(new Box(v1a)));
    }

    // values remove
    @Test
    public void testValuesNoRemove() {
        Collection<Box> values = map.values();
        boolean r = values.remove(new Box(v1a));

        assertFalse(r);
        checkElements(values, v1a, v1b, v2);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // values remove
    @Test
    public void testValuesRemove() {
        Collection<Box> values = map.values();
        boolean r = values.remove(v1a);

        assertTrue(r);
        checkElements(values, v1b, v2);
        checkEntries(map.entrySet(), entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // entrySet equals, contains
    @Test
    public void testEntrySet() {
        Set<Map.Entry<Box,Box>> entrySet = map.entrySet();

        assertFalse(entrySet.contains(entry(new Box(k1a), v1a)));
        assertFalse(entrySet.contains(entry(k1b, new Box(v1b))));
        assertFalse(entrySet.contains(entry(new Box(k2), new Box(v2))));
        assertTrue(map.entrySet().equals(map2.entrySet()));
        checkEntries(entrySet, entry(k1a, v1a),
                               entry(k1b, v1b),
                               entry(k2, v2));
    }

    // entrySet remove
    @Test
    public void testEntrySetNoRemove() {
        Set<Map.Entry<Box, Box>> entrySet = map.entrySet();
        boolean r1 = entrySet.remove(entry(new Box(k1a), v1a));
        boolean r2 = entrySet.remove(entry(k1a, new Box(v1a)));

        assertFalse(r1);
        assertFalse(r2);
        assertTrue(entrySet.equals(map2.entrySet()));
        checkEntries(entrySet, entry(k1a, v1a),
                               entry(k1b, v1b),
                               entry(k2, v2));
    }

    // entrySet remove
    @Test
    public void testEntrySetRemove() {
        Set<Map.Entry<Box, Box>> entrySet = map.entrySet();
        boolean r = entrySet.remove(Map.entry(k1a, v1a));

        assertTrue(r);
        assertFalse(entrySet.equals(map2.entrySet()));
        assertFalse(map.entrySet().equals(map2.entrySet()));
        checkEntries(entrySet, entry(k1b, v1b),
                               entry(k2, v2));
        checkEntries(map.entrySet(), entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // put
    @Test
    public void testPutNew() {
        Box newKey = new Box(k1a);
        Box newVal = new Box(v1a);
        Box r = map.put(newKey, newVal);

        assertNull(r);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2),
                                     entry(newKey, newVal));
    }

    // put
    @Test
    public void testPutOverwrite() {
        Box newVal = new Box(v1a);
        Box r = map.put(k1a, newVal);

        assertSame(r, v1a);
        checkEntries(map.entrySet(), entry(k1a, newVal),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // putAll
    @Test
    public void testPutAll() {
        Box newKey  = new Box(k1a);
        Box newVal  = new Box(v1a);
        Box newValB = new Box(v1b);
        var argMap = new IdentityHashMap<Box, Box>();
        argMap.put(newKey, newVal); // new entry
        argMap.put(k1b, newValB);   // will overwrite value
        map.putAll(argMap);

        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, newValB),
                                     entry(k2, v2),
                                     entry(newKey, newVal));
    }

    // putIfAbsent
    @Test
    public void testPutIfAbsentNoop() {
        Box r = map.putIfAbsent(k1a, new Box(v1a)); // no-op

        assertSame(r, v1a);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // putIfAbsent
    @Test
    public void testPutIfAbsentAddsNew() {
        Box newKey = new Box(k1a);
        Box newVal = new Box(v1a);
        Box r = map.putIfAbsent(newKey, newVal); // adds new entry

        assertNull(r);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2),
                                     entry(newKey, newVal));
    }

    // remove(Object)
    @Test
    public void testRemoveKey() {
        Box r = map.remove(k1b);

        assertSame(r, v1b);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k2, v2));
    }

    // AN: key absent, remappingFunction returns null
    @Test
    public void testComputeAN() {
        Box newKey = new Box(k1a);
        Box r = map.compute(newKey, (k, v) -> null);

        assertNull(r);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // AV: key absent, remappingFunction returns non-null value
    @Test
    public void testComputeAV() {
        Box newKey = new Box(k1a);
        Box newVal = new Box(v1a);
        Box r = map.compute(newKey, (k, v) -> newVal);

        assertSame(r, newVal);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2),
                                     entry(newKey, newVal));
    }

    // PN: key present, remappingFunction returns null
    @Test
    public void testComputePN() {
        Box r = map.compute(k1a, (k, v) -> null);

        assertNull(r);
        checkEntries(map.entrySet(), entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // PV: key present, remappingFunction returns non-null value
    @Test
    public void testComputePV() {
        Box newVal = new Box(v1a);
        Box r = map.compute(k1a, (k, v) -> newVal);

        assertSame(r, newVal);
        checkEntries(map.entrySet(), entry(k1a, newVal),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // computeIfAbsent
    @Test
    public void testComputeIfAbsentIsCalled() {
        boolean[] called = new boolean[1];
        Box newKey = new Box(k1a);
        Box newVal = new Box(v1a);
        Box r = map.computeIfAbsent(newKey, k -> { called[0] = true; return newVal; });

        assertSame(r, newVal);
        assertTrue(called[0]);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2),
                                     entry(newKey, newVal));
    }

    // computeIfAbsent
    @Test
    public void testComputeIfAbsentNotCalled() {
        boolean[] called = new boolean[1];
        Box r = map.computeIfAbsent(k1a, k -> { called[0] = true; return null; });

        assertSame(r, v1a);
        assertFalse(called[0]);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // computeIfAbsent
    @Test
    public void testComputeIfAbsentNullReturn() {
        boolean[] called = new boolean[1];
        Box newKey = new Box(k1a);
        Box r = map.computeIfAbsent(newKey, k -> { called[0] = true; return null; });

        assertNull(r);
        assertTrue(called[0]);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // computeIfPresent
    @Test
    public void testComputeIfPresentIsCalled() {
        boolean[] called = new boolean[1];
        Box newVal = new Box(v1a);
        Box r = map.computeIfPresent(k1a, (k, v) -> { called[0] = true; return newVal; });

        assertSame(r, newVal);
        assertTrue(called[0]);
        checkEntries(map.entrySet(), entry(k1a, newVal),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // computeIfPresent
    @Test
    public void testComputeIfPresentNotCalled() {
        boolean[] called = new boolean[1];
        Box r = map.computeIfPresent(new Box(k1a), (k, v) -> { called[0] = true; return null; });

        assertNull(r);
        assertFalse(called[0]);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // computeIfPresent
    @Test
    public void testComputeIfPresentNullReturn() {
        boolean[] called = new boolean[1];
        Box r = map.computeIfPresent(k1a, (k, v) -> { called[0] = true; return null; });

        assertNull(r);
        assertTrue(called[0]);
        checkEntries(map.entrySet(), entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // merge
    @Test
    public void testMergeAbsent() {
        boolean[] called = new boolean[1];
        Box newKey = new Box(k1a);
        Box newVal = new Box(v1a);
        Box r = map.merge(newKey, newVal, (v1, v2) -> { called[0] = true; return newVal; });

        assertSame(r, newVal);
        assertFalse(called[0]);
        checkEntries(map.entrySet(), entry(k1a, v1a),
                                     entry(k1b, v1b),
                                     entry(k2, v2),
                                     entry(newKey, newVal));
    }

    // merge
    @Test
    public void testMergePresent() {
        boolean[] called = new boolean[1];
        Box val2 = new Box(47);
        Box[] mergedVal = new Box[1];
        Box r = map.merge(k1a, val2, (v1, v2) -> {
            called[0] = true;
            mergedVal[0] = new Box(v1.i + v2.i);
            return mergedVal[0];
        });

        assertSame(r, mergedVal[0]);
        assertTrue(called[0]);
        checkEntries(map.entrySet(), entry(k1a, mergedVal[0]),
                                     entry(k1b, v1b),
                                     entry(k2, v2));
    }

    // forEach
    @Test
    public void testForEach() {
        @SuppressWarnings("unchecked")
        List<Map.Entry<Box, Box>> entries = new ArrayList<>();
        map.forEach((k, v) -> entries.add(entry(k, v)));
        checkEntries(entries, entry(k1a, v1a),
                              entry(k1b, v1b),
                              entry(k2, v2));
    }

    // replaceAll
    @Test
    public void testReplaceAll() {
        List<Map.Entry<Box, Box>> replacements = new ArrayList<>();

        map.replaceAll((k, v) -> {
            Box v1 = new Box(v);
            replacements.add(entry(k, v1));
            return v1;
        });

        @SuppressWarnings("unchecked")
        var replacementArray = (Map.Entry<Box, Box>[]) replacements.toArray(Map.Entry[]::new);
        checkEntries(map.entrySet(), replacementArray);
    }
}
