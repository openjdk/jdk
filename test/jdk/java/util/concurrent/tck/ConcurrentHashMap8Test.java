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

import static java.util.Spliterator.CONCURRENT;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.NONNULL;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ConcurrentHashMap8Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(ConcurrentHashMap8Test.class);
    }

    /**
     * Returns a new map from Integers 1-5 to Strings "A"-"E".
     */
    private static ConcurrentHashMap map5() {
        ConcurrentHashMap map = new ConcurrentHashMap(5);
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

    /**
     * getOrDefault returns value if present, else default
     */
    public void testGetOrDefault() {
        ConcurrentHashMap map = map5();
        assertEquals(map.getOrDefault(one, "Z"), "A");
        assertEquals(map.getOrDefault(six, "Z"), "Z");
    }

    /**
     * computeIfAbsent adds when the given key is not present
     */
    public void testComputeIfAbsent() {
        ConcurrentHashMap map = map5();
        map.computeIfAbsent(six, x -> "Z");
        assertTrue(map.containsKey(six));
    }

    /**
     * computeIfAbsent does not replace if the key is already present
     */
    public void testComputeIfAbsent2() {
        ConcurrentHashMap map = map5();
        assertEquals("A", map.computeIfAbsent(one, x -> "Z"));
    }

    /**
     * computeIfAbsent does not add if function returns null
     */
    public void testComputeIfAbsent3() {
        ConcurrentHashMap map = map5();
        map.computeIfAbsent(six, x -> null);
        assertFalse(map.containsKey(six));
    }

    /**
     * computeIfPresent does not replace if the key is already present
     */
    public void testComputeIfPresent() {
        ConcurrentHashMap map = map5();
        map.computeIfPresent(six, (x, y) -> "Z");
        assertFalse(map.containsKey(six));
    }

    /**
     * computeIfPresent adds when the given key is not present
     */
    public void testComputeIfPresent2() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.computeIfPresent(one, (x, y) -> "Z"));
    }

    /**
     * compute does not replace if the function returns null
     */
    public void testCompute() {
        ConcurrentHashMap map = map5();
        map.compute(six, (x, y) -> null);
        assertFalse(map.containsKey(six));
    }

    /**
     * compute adds when the given key is not present
     */
    public void testCompute2() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.compute(six, (x, y) -> "Z"));
    }

    /**
     * compute replaces when the given key is present
     */
    public void testCompute3() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.compute(one, (x, y) -> "Z"));
    }

    /**
     * compute removes when the given key is present and function returns null
     */
    public void testCompute4() {
        ConcurrentHashMap map = map5();
        map.compute(one, (x, y) -> null);
        assertFalse(map.containsKey(one));
    }

    /**
     * merge adds when the given key is not present
     */
    public void testMerge1() {
        ConcurrentHashMap map = map5();
        assertEquals("Y", map.merge(six, "Y", (x, y) -> "Z"));
    }

    /**
     * merge replaces when the given key is present
     */
    public void testMerge2() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.merge(one, "Y", (x, y) -> "Z"));
    }

    /**
     * merge removes when the given key is present and function returns null
     */
    public void testMerge3() {
        ConcurrentHashMap map = map5();
        map.merge(one, "Y", (x, y) -> null);
        assertFalse(map.containsKey(one));
    }

    static Set<Integer> populatedSet(int n) {
        Set<Integer> a = ConcurrentHashMap.<Integer>newKeySet();
        assertTrue(a.isEmpty());
        for (int i = 0; i < n; i++)
            assertTrue(a.add(i));
        assertEquals(n == 0, a.isEmpty());
        assertEquals(n, a.size());
        return a;
    }

    static Set populatedSet(Integer[] elements) {
        Set<Integer> a = ConcurrentHashMap.<Integer>newKeySet();
        assertTrue(a.isEmpty());
        for (Integer element : elements)
            assertTrue(a.add(element));
        assertFalse(a.isEmpty());
        assertEquals(elements.length, a.size());
        return a;
    }

    /**
     * replaceAll replaces all matching values.
     */
    public void testReplaceAll() {
        ConcurrentHashMap<Integer, String> map = map5();
        map.replaceAll((x, y) -> (x > 3) ? "Z" : y);
        assertEquals("A", map.get(one));
        assertEquals("B", map.get(two));
        assertEquals("C", map.get(three));
        assertEquals("Z", map.get(four));
        assertEquals("Z", map.get(five));
    }

    /**
     * Default-constructed set is empty
     */
    public void testNewKeySet() {
        Set a = ConcurrentHashMap.newKeySet();
        assertTrue(a.isEmpty());
    }

    /**
     * keySet.add adds the key with the established value to the map;
     * remove removes it.
     */
    public void testKeySetAddRemove() {
        ConcurrentHashMap map = map5();
        Set set1 = map.keySet();
        Set set2 = map.keySet(true);
        set2.add(six);
        assertSame(map, ((ConcurrentHashMap.KeySetView)set2).getMap());
        assertSame(map, ((ConcurrentHashMap.KeySetView)set1).getMap());
        assertEquals(set2.size(), map.size());
        assertEquals(set1.size(), map.size());
        assertTrue((Boolean)map.get(six));
        assertTrue(set1.contains(six));
        assertTrue(set2.contains(six));
        set2.remove(six);
        assertNull(map.get(six));
        assertFalse(set1.contains(six));
        assertFalse(set2.contains(six));
    }

    /**
     * keySet.addAll adds each element from the given collection
     */
    public void testAddAll() {
        Set full = populatedSet(3);
        assertTrue(full.addAll(Arrays.asList(three, four, five)));
        assertEquals(6, full.size());
        assertFalse(full.addAll(Arrays.asList(three, four, five)));
        assertEquals(6, full.size());
    }

    /**
     * keySet.addAll adds each element from the given collection that did not
     * already exist in the set
     */
    public void testAddAll2() {
        Set full = populatedSet(3);
        // "one" is duplicate and will not be added
        assertTrue(full.addAll(Arrays.asList(three, four, one)));
        assertEquals(5, full.size());
        assertFalse(full.addAll(Arrays.asList(three, four, one)));
        assertEquals(5, full.size());
    }

    /**
     * keySet.add will not add the element if it already exists in the set
     */
    public void testAdd2() {
        Set full = populatedSet(3);
        assertFalse(full.add(one));
        assertEquals(3, full.size());
    }

    /**
     * keySet.add adds the element when it does not exist in the set
     */
    public void testAdd3() {
        Set full = populatedSet(3);
        assertTrue(full.add(three));
        assertTrue(full.contains(three));
        assertFalse(full.add(three));
        assertTrue(full.contains(three));
    }

    /**
     * keySet.add throws UnsupportedOperationException if no default
     * mapped value
     */
    public void testAdd4() {
        Set full = map5().keySet();
        try {
            full.add(three);
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
    }

    /**
     * keySet.add throws NullPointerException if the specified key is
     * null
     */
    public void testAdd5() {
        Set full = populatedSet(3);
        try {
            full.add(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * KeySetView.getMappedValue returns the map's mapped value
     */
    public void testGetMappedValue() {
        ConcurrentHashMap map = map5();
        assertNull(map.keySet().getMappedValue());
        try {
            map.keySet(null);
            shouldThrow();
        } catch (NullPointerException success) {}
        ConcurrentHashMap.KeySetView set = map.keySet(one);
        assertFalse(set.add(one));
        assertTrue(set.add(six));
        assertTrue(set.add(seven));
        assertSame(one, set.getMappedValue());
        assertNotSame(one, map.get(one));
        assertSame(one, map.get(six));
        assertSame(one, map.get(seven));
    }

    void checkSpliteratorCharacteristics(Spliterator<?> sp,
                                         int requiredCharacteristics) {
        assertEquals(requiredCharacteristics,
                     requiredCharacteristics & sp.characteristics());
    }

    /**
     * KeySetView.spliterator returns spliterator over the elements in this set
     */
    public void testKeySetSpliterator() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap map = map5();
        Set set = map.keySet();
        Spliterator<Integer> sp = set.spliterator();
        checkSpliteratorCharacteristics(sp, CONCURRENT | DISTINCT | NONNULL);
        assertEquals(sp.estimateSize(), map.size());
        Spliterator<Integer> sp2 = sp.trySplit();
        sp.forEachRemaining((Integer x) -> adder.add(x.longValue()));
        long v = adder.sumThenReset();
        sp2.forEachRemaining((Integer x) -> adder.add(x.longValue()));
        long v2 = adder.sum();
        assertEquals(v + v2, 15);
    }

    /**
     * keyset.clear removes all elements from the set
     */
    public void testClear() {
        Set full = populatedSet(3);
        full.clear();
        assertEquals(0, full.size());
    }

    /**
     * keyset.contains returns true for added elements
     */
    public void testContains() {
        Set full = populatedSet(3);
        assertTrue(full.contains(one));
        assertFalse(full.contains(five));
    }

    /**
     * KeySets with equal elements are equal
     */
    public void testEquals() {
        Set a = populatedSet(3);
        Set b = populatedSet(3);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
        a.add(m1);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        b.add(m1);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * KeySet.containsAll returns true for collections with subset of elements
     */
    public void testContainsAll() {
        Collection full = populatedSet(3);
        assertTrue(full.containsAll(Arrays.asList()));
        assertTrue(full.containsAll(Arrays.asList(one)));
        assertTrue(full.containsAll(Arrays.asList(one, two)));
        assertFalse(full.containsAll(Arrays.asList(one, two, six)));
        assertFalse(full.containsAll(Arrays.asList(six)));
    }

    /**
     * KeySet.isEmpty is true when empty, else false
     */
    public void testIsEmpty() {
        assertTrue(populatedSet(0).isEmpty());
        assertFalse(populatedSet(3).isEmpty());
    }

    /**
     * KeySet.iterator() returns an iterator containing the elements of the
     * set
     */
    public void testIterator() {
        Collection empty = ConcurrentHashMap.newKeySet();
        int size = 20;
        assertFalse(empty.iterator().hasNext());
        try {
            empty.iterator().next();
            shouldThrow();
        } catch (NoSuchElementException success) {}

        Integer[] elements = new Integer[size];
        for (int i = 0; i < size; i++)
            elements[i] = i;
        shuffle(elements);
        Collection<Integer> full = populatedSet(elements);

        Iterator it = full.iterator();
        for (int j = 0; j < size; j++) {
            assertTrue(it.hasNext());
            it.next();
        }
        assertIteratorExhausted(it);
    }

    /**
     * iterator of empty collections has no elements
     */
    public void testEmptyIterator() {
        assertIteratorExhausted(ConcurrentHashMap.newKeySet().iterator());
        assertIteratorExhausted(new ConcurrentHashMap().entrySet().iterator());
        assertIteratorExhausted(new ConcurrentHashMap().values().iterator());
        assertIteratorExhausted(new ConcurrentHashMap().keySet().iterator());
    }

    /**
     * KeySet.iterator.remove removes current element
     */
    public void testIteratorRemove() {
        Set q = populatedSet(3);
        Iterator it = q.iterator();
        Object removed = it.next();
        it.remove();

        it = q.iterator();
        assertFalse(it.next().equals(removed));
        assertFalse(it.next().equals(removed));
        assertFalse(it.hasNext());
    }

    /**
     * KeySet.toString holds toString of elements
     */
    public void testToString() {
        assertEquals("[]", ConcurrentHashMap.newKeySet().toString());
        Set full = populatedSet(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i)
            assertTrue(s.contains(String.valueOf(i)));
    }

    /**
     * KeySet.removeAll removes all elements from the given collection
     */
    public void testRemoveAll() {
        Set full = populatedSet(3);
        assertTrue(full.removeAll(Arrays.asList(one, two)));
        assertEquals(1, full.size());
        assertFalse(full.removeAll(Arrays.asList(one, two)));
        assertEquals(1, full.size());
    }

    /**
     * KeySet.remove removes an element
     */
    public void testRemove() {
        Set full = populatedSet(3);
        full.remove(one);
        assertFalse(full.contains(one));
        assertEquals(2, full.size());
    }

    /**
     * keySet.size returns the number of elements
     */
    public void testSize() {
        Set empty = ConcurrentHashMap.newKeySet();
        Set full = populatedSet(3);
        assertEquals(3, full.size());
        assertEquals(0, empty.size());
    }

    /**
     * KeySet.toArray() returns an Object array containing all elements from
     * the set
     */
    public void testToArray() {
        Object[] a = ConcurrentHashMap.newKeySet().toArray();
        assertTrue(Arrays.equals(new Object[0], a));
        assertSame(Object[].class, a.getClass());
        int size = 20;
        Integer[] elements = new Integer[size];
        for (int i = 0; i < size; i++)
            elements[i] = i;
        shuffle(elements);
        Collection<Integer> full = populatedSet(elements);

        assertTrue(Arrays.asList(elements).containsAll(Arrays.asList(full.toArray())));
        assertTrue(full.containsAll(Arrays.asList(full.toArray())));
        assertSame(Object[].class, full.toArray().getClass());
    }

    /**
     * toArray(Integer array) returns an Integer array containing all
     * elements from the set
     */
    public void testToArray2() {
        Collection empty = ConcurrentHashMap.newKeySet();
        Integer[] a;
        int size = 20;

        a = new Integer[0];
        assertSame(a, empty.toArray(a));

        a = new Integer[size / 2];
        Arrays.fill(a, 42);
        assertSame(a, empty.toArray(a));
        assertNull(a[0]);
        for (int i = 1; i < a.length; i++)
            assertEquals(42, (int) a[i]);

        Integer[] elements = new Integer[size];
        for (int i = 0; i < size; i++)
            elements[i] = i;
        shuffle(elements);
        Collection<Integer> full = populatedSet(elements);

        Arrays.fill(a, 42);
        assertTrue(Arrays.asList(elements).containsAll(Arrays.asList(full.toArray(a))));
        for (int i = 0; i < a.length; i++)
            assertEquals(42, (int) a[i]);
        assertSame(Integer[].class, full.toArray(a).getClass());

        a = new Integer[size];
        Arrays.fill(a, 42);
        assertSame(a, full.toArray(a));
        assertTrue(Arrays.asList(elements).containsAll(Arrays.asList(full.toArray(a))));
    }

    /**
     * A deserialized/reserialized set equals original
     */
    public void testSerialization() throws Exception {
        int size = 20;
        Set x = populatedSet(size);
        Set y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x, y);
        assertEquals(y, x);
    }

    static final int SIZE = 10000;
    static ConcurrentHashMap<Long, Long> longMap;

    static ConcurrentHashMap<Long, Long> longMap() {
        if (longMap == null) {
            longMap = new ConcurrentHashMap<Long, Long>(SIZE);
            for (int i = 0; i < SIZE; ++i)
                longMap.put(Long.valueOf(i), Long.valueOf(2 *i));
        }
        return longMap;
    }

    // explicit function class to avoid type inference problems
    static class AddKeys implements BiFunction<Map.Entry<Long,Long>, Map.Entry<Long,Long>, Map.Entry<Long,Long>> {
        public Map.Entry<Long,Long> apply(Map.Entry<Long,Long> x, Map.Entry<Long,Long> y) {
            return new AbstractMap.SimpleEntry<Long,Long>
             (Long.valueOf(x.getKey().longValue() + y.getKey().longValue()),
              Long.valueOf(1L));
        }
    }

    /**
     * forEachKeySequentially traverses all keys
     */
    public void testForEachKeySequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachKey(Long.MAX_VALUE, (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1) / 2);
    }

    /**
     * forEachValueSequentially traverses all values
     */
    public void testForEachValueSequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachValue(Long.MAX_VALUE, (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1));
    }

    /**
     * forEachSequentially traverses all mappings
     */
    public void testForEachSequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEach(Long.MAX_VALUE, (Long x, Long y) -> adder.add(x.longValue() + y.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * forEachEntrySequentially traverses all entries
     */
    public void testForEachEntrySequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachEntry(Long.MAX_VALUE, (Map.Entry<Long,Long> e) -> adder.add(e.getKey().longValue() + e.getValue().longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * forEachKeyInParallel traverses all keys
     */
    public void testForEachKeyInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachKey(1L, (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1) / 2);
    }

    /**
     * forEachValueInParallel traverses all values
     */
    public void testForEachValueInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachValue(1L, (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1));
    }

    /**
     * forEachInParallel traverses all mappings
     */
    public void testForEachInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEach(1L, (Long x, Long y) -> adder.add(x.longValue() + y.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * forEachEntryInParallel traverses all entries
     */
    public void testForEachEntryInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachEntry(1L, (Map.Entry<Long,Long> e) -> adder.add(e.getKey().longValue() + e.getValue().longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachKeySequentially traverses the given
     * transformations of all keys
     */
    public void testMappedForEachKeySequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachKey(Long.MAX_VALUE, (Long x) -> Long.valueOf(4 * x.longValue()),
                                 (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachValueSequentially traverses the given
     * transformations of all values
     */
    public void testMappedForEachValueSequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachValue(Long.MAX_VALUE, (Long x) -> Long.valueOf(4 * x.longValue()),
                                   (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1));
    }

    /**
     * Mapped forEachSequentially traverses the given
     * transformations of all mappings
     */
    public void testMappedForEachSequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEach(Long.MAX_VALUE, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                              (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachEntrySequentially traverses the given
     * transformations of all entries
     */
    public void testMappedForEachEntrySequentially() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachEntry(Long.MAX_VALUE, (Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
                                   (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachKeyInParallel traverses the given
     * transformations of all keys
     */
    public void testMappedForEachKeyInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachKey(1L, (Long x) -> Long.valueOf(4 * x.longValue()),
                               (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachValueInParallel traverses the given
     * transformations of all values
     */
    public void testMappedForEachValueInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachValue(1L, (Long x) -> Long.valueOf(4 * x.longValue()),
                                 (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1));
    }

    /**
     * Mapped forEachInParallel traverses the given
     * transformations of all mappings
     */
    public void testMappedForEachInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEach(1L, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                            (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachEntryInParallel traverses the given
     * transformations of all entries
     */
    public void testMappedForEachEntryInParallel() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachEntry(1L, (Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
                                 (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysSequentially accumulates across all keys,
     */
    public void testReduceKeysSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.reduceKeys(Long.MAX_VALUE, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceValuesSequentially accumulates across all values
     */
    public void testReduceValuesSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.reduceKeys(Long.MAX_VALUE, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceEntriesSequentially accumulates across all entries
     */
    public void testReduceEntriesSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Map.Entry<Long,Long> r;
        r = m.reduceEntries(Long.MAX_VALUE, new AddKeys());
        assertEquals(r.getKey().longValue(), (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysInParallel accumulates across all keys
     */
    public void testReduceKeysInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.reduceKeys(1L, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceValuesInParallel accumulates across all values
     */
    public void testReduceValuesInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.reduceValues(1L, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1));
    }

    /**
     * reduceEntriesInParallel accumulate across all entries
     */
    public void testReduceEntriesInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Map.Entry<Long,Long> r;
        r = m.reduceEntries(1L, new AddKeys());
        assertEquals(r.getKey().longValue(), (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped reduceKeysSequentially accumulates mapped keys
     */
    public void testMapReduceKeysSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r = m.reduceKeys(Long.MAX_VALUE, (Long x) -> Long.valueOf(4 * x.longValue()),
                                     (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped reduceValuesSequentially accumulates mapped values
     */
    public void testMapReduceValuesSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r = m.reduceValues(Long.MAX_VALUE, (Long x) -> Long.valueOf(4 * x.longValue()),
                                       (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1));
    }

    /**
     * reduceSequentially accumulates across all transformed mappings
     */
    public void testMappedReduceSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r = m.reduce(Long.MAX_VALUE, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                                 (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));

        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped reduceKeysInParallel, accumulates mapped keys
     */
    public void testMapReduceKeysInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r = m.reduceKeys(1L, (Long x) -> Long.valueOf(4 * x.longValue()),
                                   (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped reduceValuesInParallel accumulates mapped values
     */
    public void testMapReduceValuesInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r = m.reduceValues(1L, (Long x) -> Long.valueOf(4 * x.longValue()),
                                     (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1));
    }

    /**
     * reduceInParallel accumulate across all transformed mappings
     */
    public void testMappedReduceInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.reduce(1L, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                               (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysToLongSequentially accumulates mapped keys
     */
    public void testReduceKeysToLongSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        long lr = m.reduceKeysToLong(Long.MAX_VALUE, (Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysToIntSequentially accumulates mapped keys
     */
    public void testReduceKeysToIntSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        int ir = m.reduceKeysToInt(Long.MAX_VALUE, (Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysToDoubleSequentially accumulates mapped keys
     */
    public void testReduceKeysToDoubleSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        double dr = m.reduceKeysToDouble(Long.MAX_VALUE, (Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceValuesToLongSequentially accumulates mapped values
     */
    public void testReduceValuesToLongSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        long lr = m.reduceValuesToLong(Long.MAX_VALUE, (Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1));
    }

    /**
     * reduceValuesToIntSequentially accumulates mapped values
     */
    public void testReduceValuesToIntSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        int ir = m.reduceValuesToInt(Long.MAX_VALUE, (Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, SIZE * (SIZE - 1));
    }

    /**
     * reduceValuesToDoubleSequentially accumulates mapped values
     */
    public void testReduceValuesToDoubleSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        double dr = m.reduceValuesToDouble(Long.MAX_VALUE, (Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1));
    }

    /**
     * reduceKeysToLongInParallel accumulates mapped keys
     */
    public void testReduceKeysToLongInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        long lr = m.reduceKeysToLong(1L, (Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysToIntInParallel accumulates mapped keys
     */
    public void testReduceKeysToIntInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        int ir = m.reduceKeysToInt(1L, (Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysToDoubleInParallel accumulates mapped values
     */
    public void testReduceKeysToDoubleInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        double dr = m.reduceKeysToDouble(1L, (Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceValuesToLongInParallel accumulates mapped values
     */
    public void testReduceValuesToLongInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        long lr = m.reduceValuesToLong(1L, (Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1));
    }

    /**
     * reduceValuesToIntInParallel accumulates mapped values
     */
    public void testReduceValuesToIntInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        int ir = m.reduceValuesToInt(1L, (Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, SIZE * (SIZE - 1));
    }

    /**
     * reduceValuesToDoubleInParallel accumulates mapped values
     */
    public void testReduceValuesToDoubleInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        double dr = m.reduceValuesToDouble(1L, (Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1));
    }

    /**
     * searchKeysSequentially returns a non-null result of search
     * function, or null if none
     */
    public void testSearchKeysSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchKeys(Long.MAX_VALUE, (Long x) -> x.longValue() == (long)(SIZE/2) ? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchKeys(Long.MAX_VALUE, (Long x) -> x.longValue() < 0L ? x : null);
        assertNull(r);
    }

    /**
     * searchValuesSequentially returns a non-null result of search
     * function, or null if none
     */
    public void testSearchValuesSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchValues(Long.MAX_VALUE,
            (Long x) -> (x.longValue() == (long)(SIZE/2)) ? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchValues(Long.MAX_VALUE,
            (Long x) -> (x.longValue() < 0L) ? x : null);
        assertNull(r);
    }

    /**
     * searchSequentially returns a non-null result of search
     * function, or null if none
     */
    public void testSearchSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.search(Long.MAX_VALUE, (Long x, Long y) -> x.longValue() == (long)(SIZE/2) ? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.search(Long.MAX_VALUE, (Long x, Long y) -> x.longValue() < 0L ? x : null);
        assertNull(r);
    }

    /**
     * searchEntriesSequentially returns a non-null result of search
     * function, or null if none
     */
    public void testSearchEntriesSequentially() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchEntries(Long.MAX_VALUE, (Map.Entry<Long,Long> e) -> e.getKey().longValue() == (long)(SIZE/2) ? e.getKey() : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchEntries(Long.MAX_VALUE, (Map.Entry<Long,Long> e) -> e.getKey().longValue() < 0L ? e.getKey() : null);
        assertNull(r);
    }

    /**
     * searchKeysInParallel returns a non-null result of search
     * function, or null if none
     */
    public void testSearchKeysInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchKeys(1L, (Long x) -> x.longValue() == (long)(SIZE/2) ? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchKeys(1L, (Long x) -> x.longValue() < 0L ? x : null);
        assertNull(r);
    }

    /**
     * searchValuesInParallel returns a non-null result of search
     * function, or null if none
     */
    public void testSearchValuesInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchValues(1L, (Long x) -> x.longValue() == (long)(SIZE/2) ? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchValues(1L, (Long x) -> x.longValue() < 0L ? x : null);
        assertNull(r);
    }

    /**
     * searchInParallel returns a non-null result of search function,
     * or null if none
     */
    public void testSearchInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.search(1L, (Long x, Long y) -> x.longValue() == (long)(SIZE/2) ? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.search(1L, (Long x, Long y) -> x.longValue() < 0L ? x : null);
        assertNull(r);
    }

    /**
     * searchEntriesInParallel returns a non-null result of search
     * function, or null if none
     */
    public void testSearchEntriesInParallel() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchEntries(1L, (Map.Entry<Long,Long> e) -> e.getKey().longValue() == (long)(SIZE/2) ? e.getKey() : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchEntries(1L, (Map.Entry<Long,Long> e) -> e.getKey().longValue() < 0L ? e.getKey() : null);
        assertNull(r);
    }

    /**
     * Tests performance of computeIfAbsent when the element is present.
     * See JDK-8161372
     * ant -Djsr166.tckTestClass=ConcurrentHashMapTest -Djsr166.methodFilter=testcomputeIfAbsent_performance -Djsr166.expensiveTests=true tck
     */
    public void testcomputeIfAbsent_performance() {
        final int mapSize = 20;
        final int iterations = expensiveTests ? (1 << 23) : mapSize * 2;
        final int threads = expensiveTests ? 10 : 2;
        final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < mapSize; i++)
            map.put(i, i);
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(pool)) {
            Runnable r = new CheckedRunnable() {
                public void realRun() {
                    int result = 0;
                    for (int i = 0; i < iterations; i++)
                        result += map.computeIfAbsent(i % mapSize, k -> k + k);
                    if (result == -42) throw new Error();
                }};
            for (int i = 0; i < threads; i++)
                pool.execute(r);
        }
    }

}
