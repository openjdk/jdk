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
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TreeSubSetTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(TreeSubSetTest.class);
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
    private static NavigableSet<Integer> populatedSet(int n) {
        TreeSet<Integer> q = new TreeSet<>();
        assertTrue(q.isEmpty());

        for (int i = n - 1; i >= 0; i -= 2)
            assertTrue(q.add(new Integer(i)));
        for (int i = (n & 1); i < n; i += 2)
            assertTrue(q.add(new Integer(i)));
        assertTrue(q.add(new Integer(-n)));
        assertTrue(q.add(new Integer(n)));
        NavigableSet s = q.subSet(new Integer(0), true, new Integer(n), false);
        assertFalse(s.isEmpty());
        assertEquals(n, s.size());
        return s;
    }

    /**
     * Returns a new set of first 5 ints.
     */
    private static NavigableSet set5() {
        TreeSet q = new TreeSet();
        assertTrue(q.isEmpty());
        q.add(one);
        q.add(two);
        q.add(three);
        q.add(four);
        q.add(five);
        q.add(zero);
        q.add(seven);
        NavigableSet s = q.subSet(one, true, seven, false);
        assertEquals(5, s.size());
        return s;
    }

    private static NavigableSet dset5() {
        TreeSet q = new TreeSet();
        assertTrue(q.isEmpty());
        q.add(m1);
        q.add(m2);
        q.add(m3);
        q.add(m4);
        q.add(m5);
        NavigableSet s = q.descendingSet();
        assertEquals(5, s.size());
        return s;
    }

    private static NavigableSet set0() {
        TreeSet set = new TreeSet();
        assertTrue(set.isEmpty());
        return set.tailSet(m1, false);
    }

    private static NavigableSet dset0() {
        TreeSet set = new TreeSet();
        assertTrue(set.isEmpty());
        return set;
    }

    /**
     * A new set has unbounded capacity
     */
    public void testConstructor1() {
        assertEquals(0, set0().size());
    }

    /**
     * isEmpty is true before add, false after
     */
    public void testEmpty() {
        NavigableSet q = set0();
        assertTrue(q.isEmpty());
        assertTrue(q.add(new Integer(1)));
        assertFalse(q.isEmpty());
        assertTrue(q.add(new Integer(2)));
        q.pollFirst();
        q.pollFirst();
        assertTrue(q.isEmpty());
    }

    /**
     * size changes when elements added and removed
     */
    public void testSize() {
        NavigableSet q = populatedSet(SIZE);
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
        NavigableSet q = set0();
        try {
            q.add(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Add of comparable element succeeds
     */
    public void testAdd() {
        NavigableSet q = set0();
        assertTrue(q.add(six));
    }

    /**
     * Add of duplicate element fails
     */
    public void testAddDup() {
        NavigableSet q = set0();
        assertTrue(q.add(six));
        assertFalse(q.add(six));
    }

    /**
     * Add of non-Comparable throws CCE
     */
    public void testAddNonComparable() {
        NavigableSet q = set0();
        try {
            q.add(new Object());
            q.add(new Object());
            shouldThrow();
        } catch (ClassCastException success) {}
    }

    /**
     * addAll(null) throws NPE
     */
    public void testAddAll1() {
        NavigableSet q = set0();
        try {
            q.addAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll of a collection with null elements throws NPE
     */
    public void testAddAll2() {
        NavigableSet q = set0();
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
        NavigableSet q = set0();
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i + SIZE);
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
        NavigableSet q = set0();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(new Integer(i), q.pollFirst());
    }

    /**
     * poll succeeds unless empty
     */
    public void testPoll() {
        NavigableSet q = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.pollFirst());
        }
        assertNull(q.pollFirst());
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testRemoveElement() {
        NavigableSet q = populatedSet(SIZE);
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
        NavigableSet q = populatedSet(SIZE);
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
        NavigableSet q = populatedSet(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertTrue(q.add(new Integer(1)));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        NavigableSet q = populatedSet(SIZE);
        NavigableSet p = set0();
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
        NavigableSet q = populatedSet(SIZE);
        NavigableSet p = populatedSet(SIZE);
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
            NavigableSet q = populatedSet(SIZE);
            NavigableSet p = populatedSet(i);
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
        NavigableSet q = set5();
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
        NavigableSet q = set5();
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
        NavigableSet q = set5();
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
        NavigableSet q = set5();
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
        NavigableSet q = populatedSet(SIZE);
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
        NavigableSet<Integer> q = populatedSet(SIZE);
        Integer[] ints = new Integer[SIZE];
        Integer[] array = q.toArray(ints);
        assertSame(ints, array);
        for (Integer o : ints)
            assertSame(o, q.pollFirst());
        assertTrue(q.isEmpty());
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() {
        NavigableSet q = populatedSet(SIZE);
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
        assertIteratorExhausted(set0().iterator());
    }

    /**
     * iterator.remove removes current element
     */
    public void testIteratorRemove() {
        final NavigableSet q = set0();
        q.add(new Integer(2));
        q.add(new Integer(1));
        q.add(new Integer(3));

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertEquals(2, it.next());
        assertEquals(3, it.next());
        assertFalse(it.hasNext());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testToString() {
        NavigableSet q = populatedSet(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
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
        NavigableSet set = set5();
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
        NavigableSet set = set5();
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
        NavigableSet set = set5();
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
        NavigableSet set = set5();
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

    /**
     * size changes when elements added and removed
     */
    public void testDescendingSize() {
        NavigableSet q = populatedSet(SIZE);
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
     * Add of comparable element succeeds
     */
    public void testDescendingAdd() {
        NavigableSet q = dset0();
        assertTrue(q.add(m6));
    }

    /**
     * Add of duplicate element fails
     */
    public void testDescendingAddDup() {
        NavigableSet q = dset0();
        assertTrue(q.add(m6));
        assertFalse(q.add(m6));
    }

    /**
     * Add of non-Comparable throws CCE
     */
    public void testDescendingAddNonComparable() {
        NavigableSet q = dset0();
        try {
            q.add(new Object());
            q.add(new Object());
            shouldThrow();
        } catch (ClassCastException success) {}
    }

    /**
     * addAll(null) throws NPE
     */
    public void testDescendingAddAll1() {
        NavigableSet q = dset0();
        try {
            q.addAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll of a collection with null elements throws NPE
     */
    public void testDescendingAddAll2() {
        NavigableSet q = dset0();
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
    public void testDescendingAddAll3() {
        NavigableSet q = dset0();
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i + SIZE);
        try {
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Set contains all elements of successful addAll
     */
    public void testDescendingAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(SIZE - 1 - i);
        NavigableSet q = dset0();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(new Integer(i), q.pollFirst());
    }

    /**
     * poll succeeds unless empty
     */
    public void testDescendingPoll() {
        NavigableSet q = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.pollFirst());
        }
        assertNull(q.pollFirst());
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testDescendingRemoveElement() {
        NavigableSet q = populatedSet(SIZE);
        for (int i = 1; i < SIZE; i += 2) {
            assertTrue(q.remove(new Integer(i)));
        }
        for (int i = 0; i < SIZE; i += 2) {
            assertTrue(q.remove(new Integer(i)));
            assertFalse(q.remove(new Integer(i + 1)));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testDescendingContains() {
        NavigableSet q = populatedSet(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.pollFirst();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    /**
     * clear removes all elements
     */
    public void testDescendingClear() {
        NavigableSet q = populatedSet(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertTrue(q.add(new Integer(1)));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testDescendingContainsAll() {
        NavigableSet q = populatedSet(SIZE);
        NavigableSet p = dset0();
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
    public void testDescendingRetainAll() {
        NavigableSet q = populatedSet(SIZE);
        NavigableSet p = populatedSet(SIZE);
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
    public void testDescendingRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            NavigableSet q = populatedSet(SIZE);
            NavigableSet p = populatedSet(i);
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
    public void testDescendingLower() {
        NavigableSet q = dset5();
        Object e1 = q.lower(m3);
        assertEquals(m2, e1);

        Object e2 = q.lower(m6);
        assertEquals(m5, e2);

        Object e3 = q.lower(m1);
        assertNull(e3);

        Object e4 = q.lower(zero);
        assertNull(e4);
    }

    /**
     * higher returns next element
     */
    public void testDescendingHigher() {
        NavigableSet q = dset5();
        Object e1 = q.higher(m3);
        assertEquals(m4, e1);

        Object e2 = q.higher(zero);
        assertEquals(m1, e2);

        Object e3 = q.higher(m5);
        assertNull(e3);

        Object e4 = q.higher(m6);
        assertNull(e4);
    }

    /**
     * floor returns preceding element
     */
    public void testDescendingFloor() {
        NavigableSet q = dset5();
        Object e1 = q.floor(m3);
        assertEquals(m3, e1);

        Object e2 = q.floor(m6);
        assertEquals(m5, e2);

        Object e3 = q.floor(m1);
        assertEquals(m1, e3);

        Object e4 = q.floor(zero);
        assertNull(e4);
    }

    /**
     * ceiling returns next element
     */
    public void testDescendingCeiling() {
        NavigableSet q = dset5();
        Object e1 = q.ceiling(m3);
        assertEquals(m3, e1);

        Object e2 = q.ceiling(zero);
        assertEquals(m1, e2);

        Object e3 = q.ceiling(m5);
        assertEquals(m5, e3);

        Object e4 = q.ceiling(m6);
        assertNull(e4);
    }

    /**
     * toArray contains all elements
     */
    public void testDescendingToArray() {
        NavigableSet q = populatedSet(SIZE);
        Object[] o = q.toArray();
        Arrays.sort(o);
        for (int i = 0; i < o.length; i++)
            assertEquals(o[i], q.pollFirst());
    }

    /**
     * toArray(a) contains all elements
     */
    public void testDescendingToArray2() {
        NavigableSet q = populatedSet(SIZE);
        Integer[] ints = new Integer[SIZE];
        assertSame(ints, q.toArray(ints));
        Arrays.sort(ints);
        for (int i = 0; i < ints.length; i++)
            assertEquals(ints[i], q.pollFirst());
    }

    /**
     * iterator iterates through all elements
     */
    public void testDescendingIterator() {
        NavigableSet q = populatedSet(SIZE);
        int i = 0;
        Iterator it = q.iterator();
        while (it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, SIZE);
    }

    /**
     * iterator of empty set has no elements
     */
    public void testDescendingEmptyIterator() {
        NavigableSet q = dset0();
        int i = 0;
        Iterator it = q.iterator();
        while (it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(0, i);
    }

    /**
     * iterator.remove removes current element
     */
    public void testDescendingIteratorRemove() {
        final NavigableSet q = dset0();
        q.add(new Integer(2));
        q.add(new Integer(1));
        q.add(new Integer(3));

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertEquals(2, it.next());
        assertEquals(3, it.next());
        assertFalse(it.hasNext());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testDescendingToString() {
        NavigableSet q = populatedSet(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * A deserialized/reserialized set equals original
     */
    public void testDescendingSerialization() throws Exception {
        NavigableSet x = dset5();
        NavigableSet y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
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
    public void testDescendingSubSetContents() {
        NavigableSet set = dset5();
        SortedSet sm = set.subSet(m2, m4);
        assertEquals(m2, sm.first());
        assertEquals(m3, sm.last());
        assertEquals(2, sm.size());
        assertFalse(sm.contains(m1));
        assertTrue(sm.contains(m2));
        assertTrue(sm.contains(m3));
        assertFalse(sm.contains(m4));
        assertFalse(sm.contains(m5));
        Iterator i = sm.iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(m2, k);
        k = (Integer)(i.next());
        assertEquals(m3, k);
        assertFalse(i.hasNext());
        Iterator j = sm.iterator();
        j.next();
        j.remove();
        assertFalse(set.contains(m2));
        assertEquals(4, set.size());
        assertEquals(1, sm.size());
        assertEquals(m3, sm.first());
        assertEquals(m3, sm.last());
        assertTrue(sm.remove(m3));
        assertTrue(sm.isEmpty());
        assertEquals(3, set.size());
    }

    public void testDescendingSubSetContents2() {
        NavigableSet set = dset5();
        SortedSet sm = set.subSet(m2, m3);
        assertEquals(1, sm.size());
        assertEquals(m2, sm.first());
        assertEquals(m2, sm.last());
        assertFalse(sm.contains(m1));
        assertTrue(sm.contains(m2));
        assertFalse(sm.contains(m3));
        assertFalse(sm.contains(m4));
        assertFalse(sm.contains(m5));
        Iterator i = sm.iterator();
        Object k;
        k = (Integer)(i.next());
        assertEquals(m2, k);
        assertFalse(i.hasNext());
        Iterator j = sm.iterator();
        j.next();
        j.remove();
        assertFalse(set.contains(m2));
        assertEquals(4, set.size());
        assertEquals(0, sm.size());
        assertTrue(sm.isEmpty());
        assertFalse(sm.remove(m3));
        assertEquals(4, set.size());
    }

    /**
     * headSet returns set with keys in requested range
     */
    public void testDescendingHeadSetContents() {
        NavigableSet set = dset5();
        SortedSet sm = set.headSet(m4);
        assertTrue(sm.contains(m1));
        assertTrue(sm.contains(m2));
        assertTrue(sm.contains(m3));
        assertFalse(sm.contains(m4));
        assertFalse(sm.contains(m5));
        Iterator i = sm.iterator();
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
        assertEquals(2, set.size());
        assertEquals(m4, set.first());
    }

    /**
     * tailSet returns set with keys in requested range
     */
    public void testDescendingTailSetContents() {
        NavigableSet set = dset5();
        SortedSet sm = set.tailSet(m2);
        assertFalse(sm.contains(m1));
        assertTrue(sm.contains(m2));
        assertTrue(sm.contains(m3));
        assertTrue(sm.contains(m4));
        assertTrue(sm.contains(m5));
        Iterator i = sm.iterator();
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

        SortedSet ssm = sm.tailSet(m4);
        assertEquals(m4, ssm.first());
        assertEquals(m5, ssm.last());
        assertTrue(ssm.remove(m4));
        assertEquals(1, ssm.size());
        assertEquals(3, sm.size());
        assertEquals(4, set.size());
    }

    /**
     * addAll is idempotent
     */
    public void testAddAll_idempotent() throws Exception {
        Set x = populatedSet(SIZE);
        Set y = new TreeSet(x);
        y.addAll(x);
        assertEquals(x, y);
        assertEquals(y, x);
    }

}
