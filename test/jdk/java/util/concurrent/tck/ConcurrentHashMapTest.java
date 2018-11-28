/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import junit.framework.Test;

public class ConcurrentHashMapTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        class Implementation implements MapImplementation {
            public Class<?> klazz() { return ConcurrentHashMap.class; }
            public Map emptyMap() { return new ConcurrentHashMap(); }
            public Object makeKey(int i) { return i; }
            public Object makeValue(int i) { return i; }
            public boolean isConcurrent() { return true; }
            public boolean permitsNullKeys() { return false; }
            public boolean permitsNullValues() { return false; }
            public boolean supportsSetValue() { return true; }
        }
        return newTestSuite(
            ConcurrentHashMapTest.class,
            MapTest.testSuite(new Implementation()));
    }

    /**
     * Returns a new map from Integers 1-5 to Strings "A"-"E".
     */
    private static ConcurrentHashMap<Integer, String> map5() {
        ConcurrentHashMap<Integer, String> map = new ConcurrentHashMap<>(5);
        assertTrue(map.isEmpty());
        map.put(one, "A");
        map.put(two, "B");
        map.put(three, "C");
        map.put(four, "D");
        map.put(five, "E");
        assertFalse(map.isEmpty());
        assertEquals(5, map.size());
        return map;
    }

    // classes for testing Comparable fallbacks
    static class BI implements Comparable<BI> {
        private final int value;
        BI(int value) { this.value = value; }
        public int compareTo(BI other) {
            return Integer.compare(value, other.value);
        }
        public boolean equals(Object x) {
            return (x instanceof BI) && ((BI)x).value == value;
        }
        public int hashCode() { return 42; }
    }
    static class CI extends BI { CI(int value) { super(value); } }
    static class DI extends BI { DI(int value) { super(value); } }

    static class BS implements Comparable<BS> {
        private final String value;
        BS(String value) { this.value = value; }
        public int compareTo(BS other) {
            return value.compareTo(other.value);
        }
        public boolean equals(Object x) {
            return (x instanceof BS) && value.equals(((BS)x).value);
        }
        public int hashCode() { return 42; }
    }

    static class LexicographicList<E extends Comparable<E>> extends ArrayList<E>
        implements Comparable<LexicographicList<E>> {
        LexicographicList(Collection<E> c) { super(c); }
        LexicographicList(E e) { super(Collections.singleton(e)); }
        public int compareTo(LexicographicList<E> other) {
            int common = Math.min(size(), other.size());
            int r = 0;
            for (int i = 0; i < common; i++) {
                if ((r = get(i).compareTo(other.get(i))) != 0)
                    break;
            }
            if (r == 0)
                r = Integer.compare(size(), other.size());
            return r;
        }
        private static final long serialVersionUID = 0;
    }

    static class CollidingObject {
        final String value;
        CollidingObject(final String value) { this.value = value; }
        public int hashCode() { return this.value.hashCode() & 1; }
        public boolean equals(final Object obj) {
            return (obj instanceof CollidingObject) && ((CollidingObject)obj).value.equals(value);
        }
    }

    static class ComparableCollidingObject extends CollidingObject implements Comparable<ComparableCollidingObject> {
        ComparableCollidingObject(final String value) { super(value); }
        public int compareTo(final ComparableCollidingObject o) {
            return value.compareTo(o.value);
        }
    }

    /**
     * Inserted elements that are subclasses of the same Comparable
     * class are found.
     */
    public void testComparableFamily() {
        int size = 500;         // makes measured test run time -> 60ms
        ConcurrentHashMap<BI, Boolean> m = new ConcurrentHashMap<>();
        for (int i = 0; i < size; i++) {
            assertNull(m.put(new CI(i), true));
        }
        for (int i = 0; i < size; i++) {
            assertTrue(m.containsKey(new CI(i)));
            assertTrue(m.containsKey(new DI(i)));
        }
    }

    /**
     * Elements of classes with erased generic type parameters based
     * on Comparable can be inserted and found.
     */
    public void testGenericComparable() {
        int size = 120;         // makes measured test run time -> 60ms
        ConcurrentHashMap<Object, Boolean> m = new ConcurrentHashMap<>();
        for (int i = 0; i < size; i++) {
            BI bi = new BI(i);
            BS bs = new BS(String.valueOf(i));
            LexicographicList<BI> bis = new LexicographicList<>(bi);
            LexicographicList<BS> bss = new LexicographicList<>(bs);
            assertNull(m.putIfAbsent(bis, true));
            assertTrue(m.containsKey(bis));
            if (m.putIfAbsent(bss, true) == null)
                assertTrue(m.containsKey(bss));
            assertTrue(m.containsKey(bis));
        }
        for (int i = 0; i < size; i++) {
            assertTrue(m.containsKey(Collections.singletonList(new BI(i))));
        }
    }

    /**
     * Elements of non-comparable classes equal to those of classes
     * with erased generic type parameters based on Comparable can be
     * inserted and found.
     */
    public void testGenericComparable2() {
        int size = 500;         // makes measured test run time -> 60ms
        ConcurrentHashMap<Object, Boolean> m = new ConcurrentHashMap<>();
        for (int i = 0; i < size; i++) {
            m.put(Collections.singletonList(new BI(i)), true);
        }

        for (int i = 0; i < size; i++) {
            LexicographicList<BI> bis = new LexicographicList<>(new BI(i));
            assertTrue(m.containsKey(bis));
        }
    }

    /**
     * Mixtures of instances of comparable and non-comparable classes
     * can be inserted and found.
     */
    public void testMixedComparable() {
        int size = 1200;        // makes measured test run time -> 35ms
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        Random rng = new Random();
        for (int i = 0; i < size; i++) {
            Object x;
            switch (rng.nextInt(4)) {
            case 0:
                x = new Object();
                break;
            case 1:
                x = new CollidingObject(Integer.toString(i));
                break;
            default:
                x = new ComparableCollidingObject(Integer.toString(i));
            }
            assertNull(map.put(x, x));
        }
        int count = 0;
        for (Object k : map.keySet()) {
            assertEquals(map.get(k), k);
            ++count;
        }
        assertEquals(count, size);
        assertEquals(map.size(), size);
        for (Object k : map.keySet()) {
            assertEquals(map.put(k, k), k);
        }
    }

    /**
     * clear removes all pairs
     */
    public void testClear() {
        ConcurrentHashMap map = map5();
        map.clear();
        assertEquals(0, map.size());
    }

    /**
     * Maps with same contents are equal
     */
    public void testEquals() {
        ConcurrentHashMap map1 = map5();
        ConcurrentHashMap map2 = map5();
        assertEquals(map1, map2);
        assertEquals(map2, map1);
        map1.clear();
        assertFalse(map1.equals(map2));
        assertFalse(map2.equals(map1));
    }

    /**
     * hashCode() equals sum of each key.hashCode ^ value.hashCode
     */
    public void testHashCode() {
        ConcurrentHashMap<Integer,String> map = map5();
        int sum = 0;
        for (Map.Entry<Integer,String> e : map.entrySet())
            sum += e.getKey().hashCode() ^ e.getValue().hashCode();
        assertEquals(sum, map.hashCode());
    }

    /**
     * contains returns true for contained value
     */
    public void testContains() {
        ConcurrentHashMap map = map5();
        assertTrue(map.contains("A"));
        assertFalse(map.contains("Z"));
    }

    /**
     * containsKey returns true for contained key
     */
    public void testContainsKey() {
        ConcurrentHashMap map = map5();
        assertTrue(map.containsKey(one));
        assertFalse(map.containsKey(zero));
    }

    /**
     * containsValue returns true for held values
     */
    public void testContainsValue() {
        ConcurrentHashMap map = map5();
        assertTrue(map.containsValue("A"));
        assertFalse(map.containsValue("Z"));
    }

    /**
     * enumeration returns an enumeration containing the correct
     * elements
     */
    public void testEnumeration() {
        ConcurrentHashMap map = map5();
        Enumeration e = map.elements();
        int count = 0;
        while (e.hasMoreElements()) {
            count++;
            e.nextElement();
        }
        assertEquals(5, count);
    }

    /**
     * get returns the correct element at the given key,
     * or null if not present
     */
    public void testGet() {
        ConcurrentHashMap map = map5();
        assertEquals("A", (String)map.get(one));
        ConcurrentHashMap empty = new ConcurrentHashMap();
        assertNull(map.get("anything"));
        assertNull(empty.get("anything"));
    }

    /**
     * isEmpty is true of empty map and false for non-empty
     */
    public void testIsEmpty() {
        ConcurrentHashMap empty = new ConcurrentHashMap();
        ConcurrentHashMap map = map5();
        assertTrue(empty.isEmpty());
        assertFalse(map.isEmpty());
    }

    /**
     * keys returns an enumeration containing all the keys from the map
     */
    public void testKeys() {
        ConcurrentHashMap map = map5();
        Enumeration e = map.keys();
        int count = 0;
        while (e.hasMoreElements()) {
            count++;
            e.nextElement();
        }
        assertEquals(5, count);
    }

    /**
     * keySet returns a Set containing all the keys
     */
    public void testKeySet() {
        ConcurrentHashMap map = map5();
        Set s = map.keySet();
        assertEquals(5, s.size());
        assertTrue(s.contains(one));
        assertTrue(s.contains(two));
        assertTrue(s.contains(three));
        assertTrue(s.contains(four));
        assertTrue(s.contains(five));
    }

    /**
     * Test keySet().removeAll on empty map
     */
    public void testKeySet_empty_removeAll() {
        ConcurrentHashMap<Integer, String> map = new ConcurrentHashMap<>();
        Set<Integer> set = map.keySet();
        set.removeAll(Collections.emptyList());
        assertTrue(map.isEmpty());
        assertTrue(set.isEmpty());
        // following is test for JDK-8163353
        set.removeAll(Collections.emptySet());
        assertTrue(map.isEmpty());
        assertTrue(set.isEmpty());
    }

    /**
     * keySet.toArray returns contains all keys
     */
    public void testKeySetToArray() {
        ConcurrentHashMap map = map5();
        Set s = map.keySet();
        Object[] ar = s.toArray();
        assertTrue(s.containsAll(Arrays.asList(ar)));
        assertEquals(5, ar.length);
        ar[0] = m10;
        assertFalse(s.containsAll(Arrays.asList(ar)));
    }

    /**
     * Values.toArray contains all values
     */
    public void testValuesToArray() {
        ConcurrentHashMap map = map5();
        Collection v = map.values();
        Object[] ar = v.toArray();
        ArrayList s = new ArrayList(Arrays.asList(ar));
        assertEquals(5, ar.length);
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
        assertTrue(s.contains("D"));
        assertTrue(s.contains("E"));
    }

    /**
     * entrySet.toArray contains all entries
     */
    public void testEntrySetToArray() {
        ConcurrentHashMap map = map5();
        Set s = map.entrySet();
        Object[] ar = s.toArray();
        assertEquals(5, ar.length);
        for (int i = 0; i < 5; ++i) {
            assertTrue(map.containsKey(((Map.Entry)(ar[i])).getKey()));
            assertTrue(map.containsValue(((Map.Entry)(ar[i])).getValue()));
        }
    }

    /**
     * values collection contains all values
     */
    public void testValues() {
        ConcurrentHashMap map = map5();
        Collection s = map.values();
        assertEquals(5, s.size());
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
        assertTrue(s.contains("D"));
        assertTrue(s.contains("E"));
    }

    /**
     * entrySet contains all pairs
     */
    public void testEntrySet() {
        ConcurrentHashMap map = map5();
        Set s = map.entrySet();
        assertEquals(5, s.size());
        Iterator it = s.iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            assertTrue(
                       (e.getKey().equals(one) && e.getValue().equals("A")) ||
                       (e.getKey().equals(two) && e.getValue().equals("B")) ||
                       (e.getKey().equals(three) && e.getValue().equals("C")) ||
                       (e.getKey().equals(four) && e.getValue().equals("D")) ||
                       (e.getKey().equals(five) && e.getValue().equals("E")));
        }
    }

    /**
     * putAll adds all key-value pairs from the given map
     */
    public void testPutAll() {
        ConcurrentHashMap empty = new ConcurrentHashMap();
        ConcurrentHashMap map = map5();
        empty.putAll(map);
        assertEquals(5, empty.size());
        assertTrue(empty.containsKey(one));
        assertTrue(empty.containsKey(two));
        assertTrue(empty.containsKey(three));
        assertTrue(empty.containsKey(four));
        assertTrue(empty.containsKey(five));
    }

    /**
     * putIfAbsent works when the given key is not present
     */
    public void testPutIfAbsent() {
        ConcurrentHashMap map = map5();
        map.putIfAbsent(six, "Z");
        assertTrue(map.containsKey(six));
    }

    /**
     * putIfAbsent does not add the pair if the key is already present
     */
    public void testPutIfAbsent2() {
        ConcurrentHashMap map = map5();
        assertEquals("A", map.putIfAbsent(one, "Z"));
    }

    /**
     * replace fails when the given key is not present
     */
    public void testReplace() {
        ConcurrentHashMap map = map5();
        assertNull(map.replace(six, "Z"));
        assertFalse(map.containsKey(six));
    }

    /**
     * replace succeeds if the key is already present
     */
    public void testReplace2() {
        ConcurrentHashMap map = map5();
        assertNotNull(map.replace(one, "Z"));
        assertEquals("Z", map.get(one));
    }

    /**
     * replace value fails when the given key not mapped to expected value
     */
    public void testReplaceValue() {
        ConcurrentHashMap map = map5();
        assertEquals("A", map.get(one));
        assertFalse(map.replace(one, "Z", "Z"));
        assertEquals("A", map.get(one));
    }

    /**
     * replace value succeeds when the given key mapped to expected value
     */
    public void testReplaceValue2() {
        ConcurrentHashMap map = map5();
        assertEquals("A", map.get(one));
        assertTrue(map.replace(one, "A", "Z"));
        assertEquals("Z", map.get(one));
    }

    /**
     * remove removes the correct key-value pair from the map
     */
    public void testRemove() {
        ConcurrentHashMap map = map5();
        map.remove(five);
        assertEquals(4, map.size());
        assertFalse(map.containsKey(five));
    }

    /**
     * remove(key,value) removes only if pair present
     */
    public void testRemove2() {
        ConcurrentHashMap map = map5();
        map.remove(five, "E");
        assertEquals(4, map.size());
        assertFalse(map.containsKey(five));
        map.remove(four, "A");
        assertEquals(4, map.size());
        assertTrue(map.containsKey(four));
    }

    /**
     * size returns the correct values
     */
    public void testSize() {
        ConcurrentHashMap map = map5();
        ConcurrentHashMap empty = new ConcurrentHashMap();
        assertEquals(0, empty.size());
        assertEquals(5, map.size());
    }

    /**
     * toString contains toString of elements
     */
    public void testToString() {
        ConcurrentHashMap map = map5();
        String s = map.toString();
        for (int i = 1; i <= 5; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    // Exception tests

    /**
     * Cannot create with only negative capacity
     */
    public void testConstructor1() {
        try {
            new ConcurrentHashMap(-1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Constructor (initialCapacity, loadFactor) throws
     * IllegalArgumentException if either argument is negative
     */
    public void testConstructor2() {
        try {
            new ConcurrentHashMap(-1, .75f);
            shouldThrow();
        } catch (IllegalArgumentException success) {}

        try {
            new ConcurrentHashMap(16, -1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Constructor (initialCapacity, loadFactor, concurrencyLevel)
     * throws IllegalArgumentException if any argument is negative
     */
    public void testConstructor3() {
        try {
            new ConcurrentHashMap(-1, .75f, 1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}

        try {
            new ConcurrentHashMap(16, -1, 1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}

        try {
            new ConcurrentHashMap(16, .75f, -1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * ConcurrentHashMap(map) throws NullPointerException if the given
     * map is null
     */
    public void testConstructor4() {
        try {
            new ConcurrentHashMap(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * ConcurrentHashMap(map) creates a new map with the same mappings
     * as the given map
     */
    public void testConstructor5() {
        ConcurrentHashMap map1 = map5();
        ConcurrentHashMap map2 = new ConcurrentHashMap(map5());
        assertTrue(map2.equals(map1));
        map2.put(one, "F");
        assertFalse(map2.equals(map1));
    }

    /**
     * get(null) throws NPE
     */
    public void testGet_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.get(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * containsKey(null) throws NPE
     */
    public void testContainsKey_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.containsKey(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * containsValue(null) throws NPE
     */
    public void testContainsValue_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.containsValue(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * contains(null) throws NPE
     */
    public void testContains_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.contains(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * put(null,x) throws NPE
     */
    public void testPut1_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.put(null, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * put(x, null) throws NPE
     */
    public void testPut2_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.put("whatever", null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * putIfAbsent(null, x) throws NPE
     */
    public void testPutIfAbsent1_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.putIfAbsent(null, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * replace(null, x) throws NPE
     */
    public void testReplace_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.replace(null, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * replace(null, x, y) throws NPE
     */
    public void testReplaceValue_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.replace(null, one, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * putIfAbsent(x, null) throws NPE
     */
    public void testPutIfAbsent2_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.putIfAbsent("whatever", null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * replace(x, null) throws NPE
     */
    public void testReplace2_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.replace("whatever", null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * replace(x, null, y) throws NPE
     */
    public void testReplaceValue2_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.replace("whatever", null, "A");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * replace(x, y, null) throws NPE
     */
    public void testReplaceValue3_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        try {
            c.replace("whatever", one, null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * remove(null) throws NPE
     */
    public void testRemove1_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        c.put("sadsdf", "asdads");
        try {
            c.remove(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * remove(null, x) throws NPE
     */
    public void testRemove2_NullPointerException() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        c.put("sadsdf", "asdads");
        try {
            c.remove(null, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * remove(x, null) returns false
     */
    public void testRemove3() {
        ConcurrentHashMap c = new ConcurrentHashMap(5);
        c.put("sadsdf", "asdads");
        assertFalse(c.remove("sadsdf", null));
    }

    /**
     * A deserialized/reserialized map equals original
     */
    public void testSerialization() throws Exception {
        Map x = map5();
        Map y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x, y);
        assertEquals(y, x);
    }

    /**
     * SetValue of an EntrySet entry sets value in the map.
     */
    public void testSetValueWriteThrough() {
        // Adapted from a bug report by Eric Zoerner
        ConcurrentHashMap map = new ConcurrentHashMap(2, 5.0f, 1);
        assertTrue(map.isEmpty());
        for (int i = 0; i < 20; i++)
            map.put(new Integer(i), new Integer(i));
        assertFalse(map.isEmpty());
        Map.Entry entry1 = (Map.Entry)map.entrySet().iterator().next();
        // Unless it happens to be first (in which case remainder of
        // test is skipped), remove a possibly-colliding key from map
        // which, under some implementations, may cause entry1 to be
        // cloned in map
        if (!entry1.getKey().equals(new Integer(16))) {
            map.remove(new Integer(16));
            entry1.setValue("XYZ");
            assertTrue(map.containsValue("XYZ")); // fails if write-through broken
        }
    }

    /**
     * Tests performance of removeAll when the other collection is much smaller.
     * ant -Djsr166.tckTestClass=ConcurrentHashMapTest -Djsr166.methodFilter=testRemoveAll_performance -Djsr166.expensiveTests=true tck
     */
    public void testRemoveAll_performance() {
        final int mapSize = expensiveTests ? 1_000_000 : 100;
        final int iterations = expensiveTests ? 500 : 2;
        final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < mapSize; i++)
            map.put(i, i);
        Set<Integer> keySet = map.keySet();
        Collection<Integer> removeMe = Arrays.asList(new Integer[] { -99, -86 });
        for (int i = 0; i < iterations; i++)
            assertFalse(keySet.removeAll(removeMe));
        assertEquals(mapSize, map.size());
    }

    public void testReentrantComputeIfAbsent() {
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>(16);
        try {
            for (int i = 0; i < 100; i++) { // force a resize
                map.computeIfAbsent(i, key -> findValue(map, key));
            }
            fail("recursive computeIfAbsent should throw IllegalStateException");
        } catch (IllegalStateException success) {}
    }

    private Integer findValue(ConcurrentHashMap<Integer, Integer> map,
                              Integer key) {
        return (key % 5 == 0) ?  key :
            map.computeIfAbsent(key + 1, k -> findValue(map, k));
    }

}
