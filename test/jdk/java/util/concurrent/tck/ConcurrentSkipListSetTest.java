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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ConcurrentSkipListSetTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(ConcurrentSkipListSetTest.class);
    }

    static class MyReverseComparator implements Comparator {
        public int compare(Object x, Object y) {
            return ((Comparable)y).compareTo(x);
        }
    }

    /**
     * Returns a new set of given size containing consecutive
     * Integers 0 ... n - 1.
     */
    private static ConcurrentSkipListSet<Integer> populatedSet(int n) {
        ConcurrentSkipListSet<Integer> q = new ConcurrentSkipListSet<>();
        assertTrue(q.isEmpty());
        for (int i = n - 1; i >= 0; i -= 2)
            assertTrue(q.add(new Integer(i)));
        for (int i = (n & 1); i < n; i += 2)
            assertTrue(q.add(new Integer(i)));
        assertFalse(q.isEmpty());
        assertEquals(n, q.size());
        return q;
    }

    /**
     * Returns a new set of first 5 ints.
     */
    private static ConcurrentSkipListSet set5() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        assertTrue(q.isEmpty());
        q.add(one);
        q.add(two);
        q.add(three);
        q.add(four);
        q.add(five);
        assertEquals(5, q.size());
        return q;
    }

    /**
     * A new set has unbounded capacity
     */
    public void testConstructor1() {
        assertEquals(0, new ConcurrentSkipListSet().size());
    }

    /**
     * Initializing from null Collection throws NPE
     */
    public void testConstructor3() {
        try {
            new ConcurrentSkipListSet((Collection)null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws NPE
     */
    public void testConstructor4() {
        try {
            new ConcurrentSkipListSet(Arrays.asList(new Integer[SIZE]));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection with some null elements throws NPE
     */
    public void testConstructor5() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        try {
            new ConcurrentSkipListSet(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Set contains all elements of collection used to initialize
     */
    public void testConstructor6() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(i);
        ConcurrentSkipListSet q = new ConcurrentSkipListSet(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.pollFirst());
    }

    /**
     * The comparator used in constructor is used
     */
    public void testConstructor7() {
        MyReverseComparator cmp = new MyReverseComparator();
        ConcurrentSkipListSet q = new ConcurrentSkipListSet(cmp);
        assertEquals(cmp, q.comparator());
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(i);
        q.addAll(Arrays.asList(ints));
        for (int i = SIZE - 1; i >= 0; --i)
            assertEquals(ints[i], q.pollFirst());
    }

    /**
     * isEmpty is true before add, false after
     */
    public void testEmpty() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        assertTrue(q.isEmpty());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.add(new Integer(2));
        q.pollFirst();
        q.pollFirst();
        assertTrue(q.isEmpty());
    }

    /**
     * size changes when elements added and removed
     */
    public void testSize() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(SIZE - i, q.size());
            q.pollFirst();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    /**
     * add(null) throws NPE
     */
    public void testAddNull() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        try {
            q.add(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Add of comparable element succeeds
     */
    public void testAdd() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        assertTrue(q.add(zero));
        assertTrue(q.add(one));
    }

    /**
     * Add of duplicate element fails
     */
    public void testAddDup() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        assertTrue(q.add(zero));
        assertFalse(q.add(zero));
    }

    /**
     * Add of non-Comparable throws CCE
     */
    public void testAddNonComparable() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        try {
            q.add(new Object());
            q.add(new Object());
            shouldThrow();
        } catch (ClassCastException success) {
            assertTrue(q.size() < 2);
            for (int i = 0, size = q.size(); i < size; i++)
                assertSame(Object.class, q.pollFirst().getClass());
            assertNull(q.pollFirst());
            assertTrue(q.isEmpty());
            assertEquals(0, q.size());
        }
    }

    /**
     * addAll(null) throws NPE
     */
    public void testAddAll1() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        try {
            q.addAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll of a collection with null elements throws NPE
     */
    public void testAddAll2() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        Integer[] ints = new Integer[SIZE];
        try {
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll of a collection with any null elements throws NPE after
     * possibly adding some elements
     */
    public void testAddAll3() {
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        try {
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Set contains all elements of successful addAll
     */
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(SIZE - 1 - i);
        ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(i, q.pollFirst());
    }

    /**
     * pollFirst succeeds unless empty
     */
    public void testPollFirst() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.pollFirst());
        }
        assertNull(q.pollFirst());
    }

    /**
     * pollLast succeeds unless empty
     */
    public void testPollLast() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        for (int i = SIZE - 1; i >= 0; --i) {
            assertEquals(i, q.pollLast());
        }
        assertNull(q.pollFirst());
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testRemoveElement() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        for (int i = 1; i < SIZE; i += 2) {
            assertTrue(q.contains(i));
            assertTrue(q.remove(i));
            assertFalse(q.contains(i));
            assertTrue(q.contains(i - 1));
        }
        for (int i = 0; i < SIZE; i += 2) {
            assertTrue(q.contains(i));
            assertTrue(q.remove(i));
            assertFalse(q.contains(i));
            assertFalse(q.remove(i + 1));
            assertFalse(q.contains(i + 1));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.pollFirst();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        ConcurrentSkipListSet p = new ConcurrentSkipListSet();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true if changed
     */
    public void testRetainAll() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        ConcurrentSkipListSet p = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0)
                assertFalse(changed);
            else
                assertTrue(changed);

            assertTrue(q.containsAll(p));
            assertEquals(SIZE - i, q.size());
            p.pollFirst();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            ConcurrentSkipListSet q = populatedSet(SIZE);
            ConcurrentSkipListSet p = populatedSet(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer x = (Integer)(p.pollFirst());
                assertFalse(q.contains(x));
            }
        }
    }

    /**
     * lower returns preceding element
     */
    public void testLower() {
        ConcurrentSkipListSet q = set5();
        Object e1 = q.lower(three);
        assertEquals(two, e1);

        Object e2 = q.lower(six);
        assertEquals(five, e2);

        Object e3 = q.lower(one);
        assertNull(e3);

        Object e4 = q.lower(zero);
        assertNull(e4);
    }

    /**
     * higher returns next element
     */
    public void testHigher() {
        ConcurrentSkipListSet q = set5();
        Object e1 = q.higher(three);
        assertEquals(four, e1);

        Object e2 = q.higher(zero);
        assertEquals(one, e2);

        Object e3 = q.higher(five);
        assertNull(e3);

        Object e4 = q.higher(six);
        assertNull(e4);
    }

    /**
     * floor returns preceding element
     */
    public void testFloor() {
        ConcurrentSkipListSet q = set5();
        Object e1 = q.floor(three);
        assertEquals(three, e1);

        Object e2 = q.floor(six);
        assertEquals(five, e2);

        Object e3 = q.floor(one);
        assertEquals(one, e3);

        Object e4 = q.floor(zero);
        assertNull(e4);
    }

    /**
     * ceiling returns next element
     */
    public void testCeiling() {
        ConcurrentSkipListSet q = set5();
        Object e1 = q.ceiling(three);
        assertEquals(three, e1);

        Object e2 = q.ceiling(zero);
        assertEquals(one, e2);

        Object e3 = q.ceiling(five);
        assertEquals(five, e3);

        Object e4 = q.ceiling(six);
        assertNull(e4);
    }

    /**
     * toArray contains all elements in sorted order
     */
    public void testToArray() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        Object[] a = q.toArray();
        assertSame(Object[].class, a.getClass());
        for (Object o : a)
            assertSame(o, q.pollFirst());
        assertTrue(q.isEmpty());
    }

    /**
     * toArray(a) contains all elements in sorted order
     */
    public void testToArray2() {
        ConcurrentSkipListSet<Integer> q = populatedSet(SIZE);
        Integer[] ints = new Integer[SIZE];
        assertSame(ints, q.toArray(ints));
        for (Integer o : ints)
            assertSame(o, q.pollFirst());
        assertTrue(q.isEmpty());
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        Iterator it = q.iterator();
        int i;
        for (i = 0; it.hasNext(); i++)
            assertTrue(q.contains(it.next()));
        assertEquals(i, SIZE);
        assertIteratorExhausted(it);
    }

    /**
     * iterator of empty set has no elements
     */
    public void testEmptyIterator() {
        NavigableSet s = new ConcurrentSkipListSet();
        assertIteratorExhausted(s.iterator());
        assertIteratorExhausted(s.descendingSet().iterator());
    }

    /**
     * iterator.remove removes current element
     */
    public void testIteratorRemove() {
        final ConcurrentSkipListSet q = new ConcurrentSkipListSet();
        q.add(new Integer(2));
        q.add(new Integer(1));
        q.add(new Integer(3));

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertEquals(it.next(), new Integer(2));
        assertEquals(it.next(), new Integer(3));
        assertFalse(it.hasNext());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testToString() {
        ConcurrentSkipListSet q = populatedSet(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * A cloned set equals original
     */
    public void testClone() {
        ConcurrentSkipListSet x = populatedSet(SIZE);
        ConcurrentSkipListSet y = x.clone();

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x, y);
        assertEquals(y, x);
        while (!x.isEmpty()) {
            assertFalse(y.isEmpty());
            assertEquals(x.pollFirst(), y.pollFirst());
        }
        assertTrue(y.isEmpty());
    }

    /**
     * A deserialized/reserialized set equals original
     */
    public void testSerialization() throws Exception {
        NavigableSet x = populatedSet(SIZE);
        NavigableSet y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x, y);
        assertEquals(y, x);
        while (!x.isEmpty()) {
            assertFalse(y.isEmpty());
            assertEquals(x.pollFirst(), y.pollFirst());
        }
        assertTrue(y.isEmpty());
    }

    /**
     * subSet returns set with keys in requested range
     */
    public void testSubSetContents() {
        ConcurrentSkipListSet set = set5();
        SortedSet sm = set.subSet(two, four);
        assertEquals(two, sm.first());
        assertEquals(three, sm.last());
        assertEquals(2, sm.size());
        assertFalse(sm.contains(one));
        assertTrue(sm.contains(two));
        assertTrue(sm.contains(three));
        assertFalse(sm.contains(four));
        assertFalse(sm.contains(five));
        Iterator i = sm.iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(two, k);
        k = (Integer)(i.next());
        assertEquals(three, k);
        assertFalse(i.hasNext());
        Iterator j = sm.iterator();
        j.next();
        j.remove();
        assertFalse(set.contains(two));
        assertEquals(4, set.size());
        assertEquals(1, sm.size());
        assertEquals(three, sm.first());
        assertEquals(three, sm.last());
        assertTrue(sm.remove(three));
        assertTrue(sm.isEmpty());
        assertEquals(3, set.size());
    }

    public void testSubSetContents2() {
        ConcurrentSkipListSet set = set5();
        SortedSet sm = set.subSet(two, three);
        assertEquals(1, sm.size());
        assertEquals(two, sm.first());
        assertEquals(two, sm.last());
        assertFalse(sm.contains(one));
        assertTrue(sm.contains(two));
        assertFalse(sm.contains(three));
        assertFalse(sm.contains(four));
        assertFalse(sm.contains(five));
        Iterator i = sm.iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(two, k);
        assertFalse(i.hasNext());
        Iterator j = sm.iterator();
        j.next();
        j.remove();
        assertFalse(set.contains(two));
        assertEquals(4, set.size());
        assertEquals(0, sm.size());
        assertTrue(sm.isEmpty());
        assertFalse(sm.remove(three));
        assertEquals(4, set.size());
    }

    /**
     * headSet returns set with keys in requested range
     */
    public void testHeadSetContents() {
        ConcurrentSkipListSet set = set5();
        SortedSet sm = set.headSet(four);
        assertTrue(sm.contains(one));
        assertTrue(sm.contains(two));
        assertTrue(sm.contains(three));
        assertFalse(sm.contains(four));
        assertFalse(sm.contains(five));
        Iterator i = sm.iterator();
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
        assertEquals(2, set.size());
        assertEquals(four, set.first());
    }

    /**
     * tailSet returns set with keys in requested range
     */
    public void testTailSetContents() {
        ConcurrentSkipListSet set = set5();
        SortedSet sm = set.tailSet(two);
        assertFalse(sm.contains(one));
        assertTrue(sm.contains(two));
        assertTrue(sm.contains(three));
        assertTrue(sm.contains(four));
        assertTrue(sm.contains(five));
        Iterator i = sm.iterator();
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

        SortedSet ssm = sm.tailSet(four);
        assertEquals(four, ssm.first());
        assertEquals(five, ssm.last());
        assertTrue(ssm.remove(four));
        assertEquals(1, ssm.size());
        assertEquals(3, sm.size());
        assertEquals(4, set.size());
    }

    Random rnd = new Random(666);

    /**
     * Subsets of subsets subdivide correctly
     */
    public void testRecursiveSubSets() throws Exception {
        int setSize = expensiveTests ? 1000 : 100;
        Class cl = ConcurrentSkipListSet.class;

        NavigableSet<Integer> set = newSet(cl);
        BitSet bs = new BitSet(setSize);

        populate(set, setSize, bs);
        check(set,                 0, setSize - 1, true, bs);
        check(set.descendingSet(), 0, setSize - 1, false, bs);

        mutateSet(set, 0, setSize - 1, bs);
        check(set,                 0, setSize - 1, true, bs);
        check(set.descendingSet(), 0, setSize - 1, false, bs);

        bashSubSet(set.subSet(0, true, setSize, false),
                   0, setSize - 1, true, bs);
    }

    /**
     * addAll is idempotent
     */
    public void testAddAll_idempotent() throws Exception {
        Set x = populatedSet(SIZE);
        Set y = new ConcurrentSkipListSet(x);
        y.addAll(x);
        assertEquals(x, y);
        assertEquals(y, x);
    }

    static NavigableSet<Integer> newSet(Class cl) throws Exception {
        NavigableSet<Integer> result =
            (NavigableSet<Integer>) cl.getConstructor().newInstance();
        assertEquals(0, result.size());
        assertFalse(result.iterator().hasNext());
        return result;
    }

    void populate(NavigableSet<Integer> set, int limit, BitSet bs) {
        for (int i = 0, n = 2 * limit / 3; i < n; i++) {
            int element = rnd.nextInt(limit);
            put(set, element, bs);
        }
    }

    void mutateSet(NavigableSet<Integer> set, int min, int max, BitSet bs) {
        int size = set.size();
        int rangeSize = max - min + 1;

        // Remove a bunch of entries directly
        for (int i = 0, n = rangeSize / 2; i < n; i++) {
            remove(set, min - 5 + rnd.nextInt(rangeSize + 10), bs);
        }

        // Remove a bunch of entries with iterator
        for (Iterator<Integer> it = set.iterator(); it.hasNext(); ) {
            if (rnd.nextBoolean()) {
                bs.clear(it.next());
                it.remove();
            }
        }

        // Add entries till we're back to original size
        while (set.size() < size) {
            int element = min + rnd.nextInt(rangeSize);
            assertTrue(element >= min && element <= max);
            put(set, element, bs);
        }
    }

    void mutateSubSet(NavigableSet<Integer> set, int min, int max,
                      BitSet bs) {
        int size = set.size();
        int rangeSize = max - min + 1;

        // Remove a bunch of entries directly
        for (int i = 0, n = rangeSize / 2; i < n; i++) {
            remove(set, min - 5 + rnd.nextInt(rangeSize + 10), bs);
        }

        // Remove a bunch of entries with iterator
        for (Iterator<Integer> it = set.iterator(); it.hasNext(); ) {
            if (rnd.nextBoolean()) {
                bs.clear(it.next());
                it.remove();
            }
        }

        // Add entries till we're back to original size
        while (set.size() < size) {
            int element = min - 5 + rnd.nextInt(rangeSize + 10);
            if (element >= min && element <= max) {
                put(set, element, bs);
            } else {
                try {
                    set.add(element);
                    shouldThrow();
                } catch (IllegalArgumentException success) {}
            }
        }
    }

    void put(NavigableSet<Integer> set, int element, BitSet bs) {
        if (set.add(element))
            bs.set(element);
    }

    void remove(NavigableSet<Integer> set, int element, BitSet bs) {
        if (set.remove(element))
            bs.clear(element);
    }

    void bashSubSet(NavigableSet<Integer> set,
                    int min, int max, boolean ascending,
                    BitSet bs) {
        check(set, min, max, ascending, bs);
        check(set.descendingSet(), min, max, !ascending, bs);

        mutateSubSet(set, min, max, bs);
        check(set, min, max, ascending, bs);
        check(set.descendingSet(), min, max, !ascending, bs);

        // Recurse
        if (max - min < 2)
            return;
        int midPoint = (min + max) / 2;

        // headSet - pick direction and endpoint inclusion randomly
        boolean incl = rnd.nextBoolean();
        NavigableSet<Integer> hm = set.headSet(midPoint, incl);
        if (ascending) {
            if (rnd.nextBoolean())
                bashSubSet(hm, min, midPoint - (incl ? 0 : 1), true, bs);
            else
                bashSubSet(hm.descendingSet(), min, midPoint - (incl ? 0 : 1),
                           false, bs);
        } else {
            if (rnd.nextBoolean())
                bashSubSet(hm, midPoint + (incl ? 0 : 1), max, false, bs);
            else
                bashSubSet(hm.descendingSet(), midPoint + (incl ? 0 : 1), max,
                           true, bs);
        }

        // tailSet - pick direction and endpoint inclusion randomly
        incl = rnd.nextBoolean();
        NavigableSet<Integer> tm = set.tailSet(midPoint,incl);
        if (ascending) {
            if (rnd.nextBoolean())
                bashSubSet(tm, midPoint + (incl ? 0 : 1), max, true, bs);
            else
                bashSubSet(tm.descendingSet(), midPoint + (incl ? 0 : 1), max,
                           false, bs);
        } else {
            if (rnd.nextBoolean()) {
                bashSubSet(tm, min, midPoint - (incl ? 0 : 1), false, bs);
            } else {
                bashSubSet(tm.descendingSet(), min, midPoint - (incl ? 0 : 1),
                           true, bs);
            }
        }

        // subSet - pick direction and endpoint inclusion randomly
        int rangeSize = max - min + 1;
        int[] endpoints = new int[2];
        endpoints[0] = min + rnd.nextInt(rangeSize);
        endpoints[1] = min + rnd.nextInt(rangeSize);
        Arrays.sort(endpoints);
        boolean lowIncl = rnd.nextBoolean();
        boolean highIncl = rnd.nextBoolean();
        if (ascending) {
            NavigableSet<Integer> sm = set.subSet(
                endpoints[0], lowIncl, endpoints[1], highIncl);
            if (rnd.nextBoolean())
                bashSubSet(sm, endpoints[0] + (lowIncl ? 0 : 1),
                           endpoints[1] - (highIncl ? 0 : 1), true, bs);
            else
                bashSubSet(sm.descendingSet(), endpoints[0] + (lowIncl ? 0 : 1),
                           endpoints[1] - (highIncl ? 0 : 1), false, bs);
        } else {
            NavigableSet<Integer> sm = set.subSet(
                endpoints[1], highIncl, endpoints[0], lowIncl);
            if (rnd.nextBoolean())
                bashSubSet(sm, endpoints[0] + (lowIncl ? 0 : 1),
                           endpoints[1] - (highIncl ? 0 : 1), false, bs);
            else
                bashSubSet(sm.descendingSet(), endpoints[0] + (lowIncl ? 0 : 1),
                           endpoints[1] - (highIncl ? 0 : 1), true, bs);
        }
    }

    /**
     * min and max are both inclusive.  If max < min, interval is empty.
     */
    void check(NavigableSet<Integer> set,
               final int min, final int max, final boolean ascending,
               final BitSet bs) {
        class ReferenceSet {
            int lower(int element) {
                return ascending ?
                    lowerAscending(element) : higherAscending(element);
            }
            int floor(int element) {
                return ascending ?
                    floorAscending(element) : ceilingAscending(element);
            }
            int ceiling(int element) {
                return ascending ?
                    ceilingAscending(element) : floorAscending(element);
            }
            int higher(int element) {
                return ascending ?
                    higherAscending(element) : lowerAscending(element);
            }
            int first() {
                return ascending ? firstAscending() : lastAscending();
            }
            int last() {
                return ascending ? lastAscending() : firstAscending();
            }
            int lowerAscending(int element) {
                return floorAscending(element - 1);
            }
            int floorAscending(int element) {
                if (element < min)
                    return -1;
                else if (element > max)
                    element = max;

                // BitSet should support this! Test would run much faster
                while (element >= min) {
                    if (bs.get(element))
                        return element;
                    element--;
                }
                return -1;
            }
            int ceilingAscending(int element) {
                if (element < min)
                    element = min;
                else if (element > max)
                    return -1;
                int result = bs.nextSetBit(element);
                return result > max ? -1 : result;
            }
            int higherAscending(int element) {
                return ceilingAscending(element + 1);
            }
            private int firstAscending() {
                int result = ceilingAscending(min);
                return result > max ? -1 : result;
            }
            private int lastAscending() {
                int result = floorAscending(max);
                return result < min ? -1 : result;
            }
        }
        ReferenceSet rs = new ReferenceSet();

        // Test contents using containsElement
        int size = 0;
        for (int i = min; i <= max; i++) {
            boolean bsContainsI = bs.get(i);
            assertEquals(bsContainsI, set.contains(i));
            if (bsContainsI)
                size++;
        }
        assertEquals(size, set.size());

        // Test contents using contains elementSet iterator
        int size2 = 0;
        int previousElement = -1;
        for (int element : set) {
            assertTrue(bs.get(element));
            size2++;
            assertTrue(previousElement < 0 || (ascending ?
                element - previousElement > 0 : element - previousElement < 0));
            previousElement = element;
        }
        assertEquals(size2, size);

        // Test navigation ops
        for (int element = min - 1; element <= max + 1; element++) {
            assertEq(set.lower(element), rs.lower(element));
            assertEq(set.floor(element), rs.floor(element));
            assertEq(set.higher(element), rs.higher(element));
            assertEq(set.ceiling(element), rs.ceiling(element));
        }

        // Test extrema
        if (set.size() != 0) {
            assertEq(set.first(), rs.first());
            assertEq(set.last(), rs.last());
        } else {
            assertEq(rs.first(), -1);
            assertEq(rs.last(),  -1);
            try {
                set.first();
                shouldThrow();
            } catch (NoSuchElementException success) {}
            try {
                set.last();
                shouldThrow();
            } catch (NoSuchElementException success) {}
        }
    }

    static void assertEq(Integer i, int j) {
        if (i == null)
            assertEquals(j, -1);
        else
            assertEquals((int) i, j);
    }

    static boolean eq(Integer i, int j) {
        return (i == null) ? j == -1 : i == j;
    }

}
