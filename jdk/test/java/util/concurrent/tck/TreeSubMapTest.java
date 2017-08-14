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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TreeSubMapTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(TreeSubMapTest.class);
    }

    /**
     * Returns a new map from Integers 1-5 to Strings "A"-"E".
     */
    private static NavigableMap map5() {
        TreeMap map = new TreeMap();
        assertTrue(map.isEmpty());
        map.put(zero, "Z");
        map.put(one, "A");
        map.put(five, "E");
        map.put(three, "C");
        map.put(two, "B");
        map.put(four, "D");
        map.put(seven, "F");
        assertFalse(map.isEmpty());
        assertEquals(7, map.size());
        return map.subMap(one, true, seven, false);
    }

    private static NavigableMap map0() {
        TreeMap map = new TreeMap();
        assertTrue(map.isEmpty());
        return map.tailMap(one, true);
    }

    /**
     * Returns a new map from Integers -5 to -1 to Strings "A"-"E".
     */
    private static NavigableMap dmap5() {
        TreeMap map = new TreeMap();
        assertTrue(map.isEmpty());
        map.put(m1, "A");
        map.put(m5, "E");
        map.put(m3, "C");
        map.put(m2, "B");
        map.put(m4, "D");
        assertFalse(map.isEmpty());
        assertEquals(5, map.size());
        return map.descendingMap();
    }

    private static NavigableMap dmap0() {
        TreeMap map = new TreeMap();
        assertTrue(map.isEmpty());
        return map;
    }

    /**
     * clear removes all pairs
     */
    public void testClear() {
        NavigableMap map = map5();
        map.clear();
        assertEquals(0, map.size());
    }

    /**
     * Maps with same contents are equal
     */
    public void testEquals() {
        NavigableMap map1 = map5();
        NavigableMap map2 = map5();
        assertEquals(map1, map2);
        assertEquals(map2, map1);
        map1.clear();
        assertFalse(map1.equals(map2));
        assertFalse(map2.equals(map1));
    }

    /**
     * containsKey returns true for contained key
     */
    public void testContainsKey() {
        NavigableMap map = map5();
        assertTrue(map.containsKey(one));
        assertFalse(map.containsKey(zero));
    }

    /**
     * containsValue returns true for held values
     */
    public void testContainsValue() {
        NavigableMap map = map5();
        assertTrue(map.containsValue("A"));
        assertFalse(map.containsValue("Z"));
    }

    /**
     * get returns the correct element at the given key,
     * or null if not present
     */
    public void testGet() {
        NavigableMap map = map5();
        assertEquals("A", (String)map.get(one));
        NavigableMap empty = map0();
        assertNull(empty.get(one));
    }

    /**
     * isEmpty is true of empty map and false for non-empty
     */
    public void testIsEmpty() {
        NavigableMap empty = map0();
        NavigableMap map = map5();
        assertTrue(empty.isEmpty());
        assertFalse(map.isEmpty());
    }

    /**
     * firstKey returns first key
     */
    public void testFirstKey() {
        NavigableMap map = map5();
        assertEquals(one, map.firstKey());
    }

    /**
     * lastKey returns last key
     */
    public void testLastKey() {
        NavigableMap map = map5();
        assertEquals(five, map.lastKey());
    }

    /**
     * keySet returns a Set containing all the keys
     */
    public void testKeySet() {
        NavigableMap map = map5();
        Set s = map.keySet();
        assertEquals(5, s.size());
        assertTrue(s.contains(one));
        assertTrue(s.contains(two));
        assertTrue(s.contains(three));
        assertTrue(s.contains(four));
        assertTrue(s.contains(five));
    }

    /**
     * keySet is ordered
     */
    public void testKeySetOrder() {
        NavigableMap map = map5();
        Set s = map.keySet();
        Iterator i = s.iterator();
        Integer last = (Integer)i.next();
        assertEquals(last, one);
        while (i.hasNext()) {
            Integer k = (Integer)i.next();
            assertTrue(last.compareTo(k) < 0);
            last = k;
        }
    }

    /**
     * values collection contains all values
     */
    public void testValues() {
        NavigableMap map = map5();
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
        NavigableMap map = map5();
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
        NavigableMap empty = map0();
        NavigableMap map = map5();
        empty.putAll(map);
        assertEquals(5, empty.size());
        assertTrue(empty.containsKey(one));
        assertTrue(empty.containsKey(two));
        assertTrue(empty.containsKey(three));
        assertTrue(empty.containsKey(four));
        assertTrue(empty.containsKey(five));
    }

    /**
     * remove removes the correct key-value pair from the map
     */
    public void testRemove() {
        NavigableMap map = map5();
        map.remove(five);
        assertEquals(4, map.size());
        assertFalse(map.containsKey(five));
    }

    /**
     * lowerEntry returns preceding entry.
     */
    public void testLowerEntry() {
        NavigableMap map = map5();
        Map.Entry e1 = map.lowerEntry(three);
        assertEquals(two, e1.getKey());

        Map.Entry e2 = map.lowerEntry(six);
        assertEquals(five, e2.getKey());

        Map.Entry e3 = map.lowerEntry(one);
        assertNull(e3);

        Map.Entry e4 = map.lowerEntry(zero);
        assertNull(e4);
    }

    /**
     * higherEntry returns next entry.
     */
    public void testHigherEntry() {
        NavigableMap map = map5();
        Map.Entry e1 = map.higherEntry(three);
        assertEquals(four, e1.getKey());

        Map.Entry e2 = map.higherEntry(zero);
        assertEquals(one, e2.getKey());

        Map.Entry e3 = map.higherEntry(five);
        assertNull(e3);

        Map.Entry e4 = map.higherEntry(six);
        assertNull(e4);
    }

    /**
     * floorEntry returns preceding entry.
     */
    public void testFloorEntry() {
        NavigableMap map = map5();
        Map.Entry e1 = map.floorEntry(three);
        assertEquals(three, e1.getKey());

        Map.Entry e2 = map.floorEntry(six);
        assertEquals(five, e2.getKey());

        Map.Entry e3 = map.floorEntry(one);
        assertEquals(one, e3.getKey());

        Map.Entry e4 = map.floorEntry(zero);
        assertNull(e4);
    }

    /**
     * ceilingEntry returns next entry.
     */
    public void testCeilingEntry() {
        NavigableMap map = map5();
        Map.Entry e1 = map.ceilingEntry(three);
        assertEquals(three, e1.getKey());

        Map.Entry e2 = map.ceilingEntry(zero);
        assertEquals(one, e2.getKey());

        Map.Entry e3 = map.ceilingEntry(five);
        assertEquals(five, e3.getKey());

        Map.Entry e4 = map.ceilingEntry(six);
        assertNull(e4);
    }

    /**
     * pollFirstEntry returns entries in order
     */
    public void testPollFirstEntry() {
        NavigableMap map = map5();
        Map.Entry e = map.pollFirstEntry();
        assertEquals(one, e.getKey());
        assertEquals("A", e.getValue());
        e = map.pollFirstEntry();
        assertEquals(two, e.getKey());
        map.put(one, "A");
        e = map.pollFirstEntry();
        assertEquals(one, e.getKey());
        assertEquals("A", e.getValue());
        e = map.pollFirstEntry();
        assertEquals(three, e.getKey());
        map.remove(four);
        e = map.pollFirstEntry();
        assertEquals(five, e.getKey());
        try {
            e.setValue("A");
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
        assertTrue(map.isEmpty());
        Map.Entry f = map.firstEntry();
        assertNull(f);
        e = map.pollFirstEntry();
        assertNull(e);
    }

    /**
     * pollLastEntry returns entries in order
     */
    public void testPollLastEntry() {
        NavigableMap map = map5();
        Map.Entry e = map.pollLastEntry();
        assertEquals(five, e.getKey());
        assertEquals("E", e.getValue());
        e = map.pollLastEntry();
        assertEquals(four, e.getKey());
        map.put(five, "E");
        e = map.pollLastEntry();
        assertEquals(five, e.getKey());
        assertEquals("E", e.getValue());
        e = map.pollLastEntry();
        assertEquals(three, e.getKey());
        map.remove(two);
        e = map.pollLastEntry();
        assertEquals(one, e.getKey());
        try {
            e.setValue("E");
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
        e = map.pollLastEntry();
        assertNull(e);
    }

    /**
     * size returns the correct values
     */
    public void testSize() {
        NavigableMap map = map5();
        NavigableMap empty = map0();
        assertEquals(0, empty.size());
        assertEquals(5, map.size());
    }

    /**
     * toString contains toString of elements
     */
    public void testToString() {
        NavigableMap map = map5();
        String s = map.toString();
        for (int i = 1; i <= 5; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    // Exception tests

    /**
     * get(null) of nonempty map throws NPE
     */
    public void testGet_NullPointerException() {
        NavigableMap c = map5();
        try {
            c.get(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * containsKey(null) of nonempty map throws NPE
     */
    public void testContainsKey_NullPointerException() {
        NavigableMap c = map5();
        try {
            c.containsKey(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * put(null,x) throws NPE
     */
    public void testPut1_NullPointerException() {
        NavigableMap c = map5();
        try {
            c.put(null, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * remove(null) throws NPE
     */
    public void testRemove1_NullPointerException() {
        NavigableMap c = map5();
        try {
            c.remove(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A deserialized/reserialized map equals original
     */
    public void testSerialization() throws Exception {
        NavigableMap x = map5();
        NavigableMap y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertEquals(x, y);
        assertEquals(y, x);
    }

    /**
     * subMap returns map with keys in requested range
     */
    public void testSubMapContents() {
        NavigableMap map = map5();
        SortedMap sm = map.subMap(two, four);
        assertEquals(two, sm.firstKey());
        assertEquals(three, sm.lastKey());
        assertEquals(2, sm.size());
        assertFalse(sm.containsKey(one));
        assertTrue(sm.containsKey(two));
        assertTrue(sm.containsKey(three));
        assertFalse(sm.containsKey(four));
        assertFalse(sm.containsKey(five));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(two, k);
        k = (Integer)(i.next());
        assertEquals(three, k);
        assertFalse(i.hasNext());
        Iterator j = sm.keySet().iterator();
        j.next();
        j.remove();
        assertFalse(map.containsKey(two));
        assertEquals(4, map.size());
        assertEquals(1, sm.size());
        assertEquals(three, sm.firstKey());
        assertEquals(three, sm.lastKey());
        assertEquals("C", sm.remove(three));
        assertTrue(sm.isEmpty());
        assertEquals(3, map.size());
    }

    public void testSubMapContents2() {
        NavigableMap map = map5();
        SortedMap sm = map.subMap(two, three);
        assertEquals(1, sm.size());
        assertEquals(two, sm.firstKey());
        assertEquals(two, sm.lastKey());
        assertFalse(sm.containsKey(one));
        assertTrue(sm.containsKey(two));
        assertFalse(sm.containsKey(three));
        assertFalse(sm.containsKey(four));
        assertFalse(sm.containsKey(five));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(two, k);
        assertFalse(i.hasNext());
        Iterator j = sm.keySet().iterator();
        j.next();
        j.remove();
        assertFalse(map.containsKey(two));
        assertEquals(4, map.size());
        assertEquals(0, sm.size());
        assertTrue(sm.isEmpty());
        assertSame(sm.remove(three), null);
        assertEquals(4, map.size());
    }

    /**
     * headMap returns map with keys in requested range
     */
    public void testHeadMapContents() {
        NavigableMap map = map5();
        SortedMap sm = map.headMap(four);
        assertTrue(sm.containsKey(one));
        assertTrue(sm.containsKey(two));
        assertTrue(sm.containsKey(three));
        assertFalse(sm.containsKey(four));
        assertFalse(sm.containsKey(five));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(one, k);
        k = (Integer)(i.next());
        assertEquals(two, k);
        k = (Integer)(i.next());
        assertEquals(three, k);
        assertFalse(i.hasNext());
        sm.clear();
        assertTrue(sm.isEmpty());
        assertEquals(2, map.size());
        assertEquals(four, map.firstKey());
    }

    /**
     * headMap returns map with keys in requested range
     */
    public void testTailMapContents() {
        NavigableMap map = map5();
        SortedMap sm = map.tailMap(two);
        assertFalse(sm.containsKey(one));
        assertTrue(sm.containsKey(two));
        assertTrue(sm.containsKey(three));
        assertTrue(sm.containsKey(four));
        assertTrue(sm.containsKey(five));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(two, k);
        k = (Integer)(i.next());
        assertEquals(three, k);
        k = (Integer)(i.next());
        assertEquals(four, k);
        k = (Integer)(i.next());
        assertEquals(five, k);
        assertFalse(i.hasNext());

        Iterator ei = sm.entrySet().iterator();
        Map.Entry e;
        e = (Map.Entry)(ei.next());
        assertEquals(two, e.getKey());
        assertEquals("B", e.getValue());
        e = (Map.Entry)(ei.next());
        assertEquals(three, e.getKey());
        assertEquals("C", e.getValue());
        e = (Map.Entry)(ei.next());
        assertEquals(four, e.getKey());
        assertEquals("D", e.getValue());
        e = (Map.Entry)(ei.next());
        assertEquals(five, e.getKey());
        assertEquals("E", e.getValue());
        assertFalse(i.hasNext());

        SortedMap ssm = sm.tailMap(four);
        assertEquals(four, ssm.firstKey());
        assertEquals(five, ssm.lastKey());
        assertEquals("D", ssm.remove(four));
        assertEquals(1, ssm.size());
        assertEquals(3, sm.size());
        assertEquals(4, map.size());
    }

    /**
     * clear removes all pairs
     */
    public void testDescendingClear() {
        NavigableMap map = dmap5();
        map.clear();
        assertEquals(0, map.size());
    }

    /**
     * Maps with same contents are equal
     */
    public void testDescendingEquals() {
        NavigableMap map1 = dmap5();
        NavigableMap map2 = dmap5();
        assertEquals(map1, map2);
        assertEquals(map2, map1);
        map1.clear();
        assertFalse(map1.equals(map2));
        assertFalse(map2.equals(map1));
    }

    /**
     * containsKey returns true for contained key
     */
    public void testDescendingContainsKey() {
        NavigableMap map = dmap5();
        assertTrue(map.containsKey(m1));
        assertFalse(map.containsKey(zero));
    }

    /**
     * containsValue returns true for held values
     */
    public void testDescendingContainsValue() {
        NavigableMap map = dmap5();
        assertTrue(map.containsValue("A"));
        assertFalse(map.containsValue("Z"));
    }

    /**
     * get returns the correct element at the given key,
     * or null if not present
     */
    public void testDescendingGet() {
        NavigableMap map = dmap5();
        assertEquals("A", (String)map.get(m1));
        NavigableMap empty = dmap0();
        assertNull(empty.get(m1));
    }

    /**
     * isEmpty is true of empty map and false for non-empty
     */
    public void testDescendingIsEmpty() {
        NavigableMap empty = dmap0();
        NavigableMap map = dmap5();
        assertTrue(empty.isEmpty());
        assertFalse(map.isEmpty());
    }

    /**
     * firstKey returns first key
     */
    public void testDescendingFirstKey() {
        NavigableMap map = dmap5();
        assertEquals(m1, map.firstKey());
    }

    /**
     * lastKey returns last key
     */
    public void testDescendingLastKey() {
        NavigableMap map = dmap5();
        assertEquals(m5, map.lastKey());
    }

    /**
     * keySet returns a Set containing all the keys
     */
    public void testDescendingKeySet() {
        NavigableMap map = dmap5();
        Set s = map.keySet();
        assertEquals(5, s.size());
        assertTrue(s.contains(m1));
        assertTrue(s.contains(m2));
        assertTrue(s.contains(m3));
        assertTrue(s.contains(m4));
        assertTrue(s.contains(m5));
    }

    /**
     * keySet is ordered
     */
    public void testDescendingKeySetOrder() {
        NavigableMap map = dmap5();
        Set s = map.keySet();
        Iterator i = s.iterator();
        Integer last = (Integer)i.next();
        assertEquals(last, m1);
        while (i.hasNext()) {
            Integer k = (Integer)i.next();
            assertTrue(last.compareTo(k) > 0);
            last = k;
        }
    }

    /**
     * values collection contains all values
     */
    public void testDescendingValues() {
        NavigableMap map = dmap5();
        Collection s = map.values();
        assertEquals(5, s.size());
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
        assertTrue(s.contains("D"));
        assertTrue(s.contains("E"));
    }

    /**
     * keySet.toArray returns contains all keys
     */
    public void testDescendingAscendingKeySetToArray() {
        NavigableMap map = dmap5();
        Set s = map.keySet();
        Object[] ar = s.toArray();
        assertTrue(s.containsAll(Arrays.asList(ar)));
        assertEquals(5, ar.length);
        ar[0] = m10;
        assertFalse(s.containsAll(Arrays.asList(ar)));
    }

    /**
     * descendingkeySet.toArray returns contains all keys
     */
    public void testDescendingDescendingKeySetToArray() {
        NavigableMap map = dmap5();
        Set s = map.descendingKeySet();
        Object[] ar = s.toArray();
        assertEquals(5, ar.length);
        assertTrue(s.containsAll(Arrays.asList(ar)));
        ar[0] = m10;
        assertFalse(s.containsAll(Arrays.asList(ar)));
    }

    /**
     * Values.toArray contains all values
     */
    public void testDescendingValuesToArray() {
        NavigableMap map = dmap5();
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
     * entrySet contains all pairs
     */
    public void testDescendingEntrySet() {
        NavigableMap map = dmap5();
        Set s = map.entrySet();
        assertEquals(5, s.size());
        Iterator it = s.iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            assertTrue(
                       (e.getKey().equals(m1) && e.getValue().equals("A")) ||
                       (e.getKey().equals(m2) && e.getValue().equals("B")) ||
                       (e.getKey().equals(m3) && e.getValue().equals("C")) ||
                       (e.getKey().equals(m4) && e.getValue().equals("D")) ||
                       (e.getKey().equals(m5) && e.getValue().equals("E")));
        }
    }

    /**
     * putAll adds all key-value pairs from the given map
     */
    public void testDescendingPutAll() {
        NavigableMap empty = dmap0();
        NavigableMap map = dmap5();
        empty.putAll(map);
        assertEquals(5, empty.size());
        assertTrue(empty.containsKey(m1));
        assertTrue(empty.containsKey(m2));
        assertTrue(empty.containsKey(m3));
        assertTrue(empty.containsKey(m4));
        assertTrue(empty.containsKey(m5));
    }

    /**
     * remove removes the correct key-value pair from the map
     */
    public void testDescendingRemove() {
        NavigableMap map = dmap5();
        map.remove(m5);
        assertEquals(4, map.size());
        assertFalse(map.containsKey(m5));
    }

    /**
     * lowerEntry returns preceding entry.
     */
    public void testDescendingLowerEntry() {
        NavigableMap map = dmap5();
        Map.Entry e1 = map.lowerEntry(m3);
        assertEquals(m2, e1.getKey());

        Map.Entry e2 = map.lowerEntry(m6);
        assertEquals(m5, e2.getKey());

        Map.Entry e3 = map.lowerEntry(m1);
        assertNull(e3);

        Map.Entry e4 = map.lowerEntry(zero);
        assertNull(e4);
    }

    /**
     * higherEntry returns next entry.
     */
    public void testDescendingHigherEntry() {
        NavigableMap map = dmap5();
        Map.Entry e1 = map.higherEntry(m3);
        assertEquals(m4, e1.getKey());

        Map.Entry e2 = map.higherEntry(zero);
        assertEquals(m1, e2.getKey());

        Map.Entry e3 = map.higherEntry(m5);
        assertNull(e3);

        Map.Entry e4 = map.higherEntry(m6);
        assertNull(e4);
    }

    /**
     * floorEntry returns preceding entry.
     */
    public void testDescendingFloorEntry() {
        NavigableMap map = dmap5();
        Map.Entry e1 = map.floorEntry(m3);
        assertEquals(m3, e1.getKey());

        Map.Entry e2 = map.floorEntry(m6);
        assertEquals(m5, e2.getKey());

        Map.Entry e3 = map.floorEntry(m1);
        assertEquals(m1, e3.getKey());

        Map.Entry e4 = map.floorEntry(zero);
        assertNull(e4);
    }

    /**
     * ceilingEntry returns next entry.
     */
    public void testDescendingCeilingEntry() {
        NavigableMap map = dmap5();
        Map.Entry e1 = map.ceilingEntry(m3);
        assertEquals(m3, e1.getKey());

        Map.Entry e2 = map.ceilingEntry(zero);
        assertEquals(m1, e2.getKey());

        Map.Entry e3 = map.ceilingEntry(m5);
        assertEquals(m5, e3.getKey());

        Map.Entry e4 = map.ceilingEntry(m6);
        assertNull(e4);
    }

    /**
     * pollFirstEntry returns entries in order
     */
    public void testDescendingPollFirstEntry() {
        NavigableMap map = dmap5();
        Map.Entry e = map.pollFirstEntry();
        assertEquals(m1, e.getKey());
        assertEquals("A", e.getValue());
        e = map.pollFirstEntry();
        assertEquals(m2, e.getKey());
        map.put(m1, "A");
        e = map.pollFirstEntry();
        assertEquals(m1, e.getKey());
        assertEquals("A", e.getValue());
        e = map.pollFirstEntry();
        assertEquals(m3, e.getKey());
        map.remove(m4);
        e = map.pollFirstEntry();
        assertEquals(m5, e.getKey());
        try {
            e.setValue("A");
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
        e = map.pollFirstEntry();
        assertNull(e);
    }

    /**
     * pollLastEntry returns entries in order
     */
    public void testDescendingPollLastEntry() {
        NavigableMap map = dmap5();
        Map.Entry e = map.pollLastEntry();
        assertEquals(m5, e.getKey());
        assertEquals("E", e.getValue());
        e = map.pollLastEntry();
        assertEquals(m4, e.getKey());
        map.put(m5, "E");
        e = map.pollLastEntry();
        assertEquals(m5, e.getKey());
        assertEquals("E", e.getValue());
        e = map.pollLastEntry();
        assertEquals(m3, e.getKey());
        map.remove(m2);
        e = map.pollLastEntry();
        assertEquals(m1, e.getKey());
        try {
            e.setValue("E");
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
        e = map.pollLastEntry();
        assertNull(e);
    }

    /**
     * size returns the correct values
     */
    public void testDescendingSize() {
        NavigableMap map = dmap5();
        NavigableMap empty = dmap0();
        assertEquals(0, empty.size());
        assertEquals(5, map.size());
    }

    /**
     * toString contains toString of elements
     */
    public void testDescendingToString() {
        NavigableMap map = dmap5();
        String s = map.toString();
        for (int i = 1; i <= 5; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    // Exception testDescendings

    /**
     * get(null) of nonempty map throws NPE
     */
    public void testDescendingGet_NullPointerException() {
        NavigableMap c = dmap5();
        try {
            c.get(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * put(null,x) throws NPE
     */
    public void testDescendingPut1_NullPointerException() {
        NavigableMap c = dmap5();
        try {
            c.put(null, "whatever");
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A deserialized/reserialized map equals original
     */
    public void testDescendingSerialization() throws Exception {
        NavigableMap x = dmap5();
        NavigableMap y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertEquals(x, y);
        assertEquals(y, x);
    }

    /**
     * subMap returns map with keys in requested range
     */
    public void testDescendingSubMapContents() {
        NavigableMap map = dmap5();
        SortedMap sm = map.subMap(m2, m4);
        assertEquals(m2, sm.firstKey());
        assertEquals(m3, sm.lastKey());
        assertEquals(2, sm.size());
        assertFalse(sm.containsKey(m1));
        assertTrue(sm.containsKey(m2));
        assertTrue(sm.containsKey(m3));
        assertFalse(sm.containsKey(m4));
        assertFalse(sm.containsKey(m5));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(m2, k);
        k = (Integer)(i.next());
        assertEquals(m3, k);
        assertFalse(i.hasNext());
        Iterator j = sm.keySet().iterator();
        j.next();
        j.remove();
        assertFalse(map.containsKey(m2));
        assertEquals(4, map.size());
        assertEquals(1, sm.size());
        assertEquals(m3, sm.firstKey());
        assertEquals(m3, sm.lastKey());
        assertEquals("C", sm.remove(m3));
        assertTrue(sm.isEmpty());
        assertEquals(3, map.size());
    }

    public void testDescendingSubMapContents2() {
        NavigableMap map = dmap5();
        SortedMap sm = map.subMap(m2, m3);
        assertEquals(1, sm.size());
        assertEquals(m2, sm.firstKey());
        assertEquals(m2, sm.lastKey());
        assertFalse(sm.containsKey(m1));
        assertTrue(sm.containsKey(m2));
        assertFalse(sm.containsKey(m3));
        assertFalse(sm.containsKey(m4));
        assertFalse(sm.containsKey(m5));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(m2, k);
        assertFalse(i.hasNext());
        Iterator j = sm.keySet().iterator();
        j.next();
        j.remove();
        assertFalse(map.containsKey(m2));
        assertEquals(4, map.size());
        assertEquals(0, sm.size());
        assertTrue(sm.isEmpty());
        assertSame(sm.remove(m3), null);
        assertEquals(4, map.size());
    }

    /**
     * headMap returns map with keys in requested range
     */
    public void testDescendingHeadMapContents() {
        NavigableMap map = dmap5();
        SortedMap sm = map.headMap(m4);
        assertTrue(sm.containsKey(m1));
        assertTrue(sm.containsKey(m2));
        assertTrue(sm.containsKey(m3));
        assertFalse(sm.containsKey(m4));
        assertFalse(sm.containsKey(m5));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(m1, k);
        k = (Integer)(i.next());
        assertEquals(m2, k);
        k = (Integer)(i.next());
        assertEquals(m3, k);
        assertFalse(i.hasNext());
        sm.clear();
        assertTrue(sm.isEmpty());
        assertEquals(2, map.size());
        assertEquals(m4, map.firstKey());
    }

    /**
     * headMap returns map with keys in requested range
     */
    public void testDescendingTailMapContents() {
        NavigableMap map = dmap5();
        SortedMap sm = map.tailMap(m2);
        assertFalse(sm.containsKey(m1));
        assertTrue(sm.containsKey(m2));
        assertTrue(sm.containsKey(m3));
        assertTrue(sm.containsKey(m4));
        assertTrue(sm.containsKey(m5));
        Iterator i = sm.keySet().iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(m2, k);
        k = (Integer)(i.next());
        assertEquals(m3, k);
        k = (Integer)(i.next());
        assertEquals(m4, k);
        k = (Integer)(i.next());
        assertEquals(m5, k);
        assertFalse(i.hasNext());

        Iterator ei = sm.entrySet().iterator();
        Map.Entry e;
        e = (Map.Entry)(ei.next());
        assertEquals(m2, e.getKey());
        assertEquals("B", e.getValue());
        e = (Map.Entry)(ei.next());
        assertEquals(m3, e.getKey());
        assertEquals("C", e.getValue());
        e = (Map.Entry)(ei.next());
        assertEquals(m4, e.getKey());
        assertEquals("D", e.getValue());
        e = (Map.Entry)(ei.next());
        assertEquals(m5, e.getKey());
        assertEquals("E", e.getValue());
        assertFalse(i.hasNext());

        SortedMap ssm = sm.tailMap(m4);
        assertEquals(m4, ssm.firstKey());
        assertEquals(m5, ssm.lastKey());
        assertEquals("D", ssm.remove(m4));
        assertEquals(1, ssm.size());
        assertEquals(3, sm.size());
        assertEquals(4, map.size());
    }

}
