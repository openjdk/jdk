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
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ConcurrentLinkedDequeTest extends JSR166TestCase {

    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(ConcurrentLinkedDequeTest.class);
    }

    /**
     * Returns a new deque of given size containing consecutive
     * Integers 0 ... n.
     */
    private ConcurrentLinkedDeque<Integer> populatedDeque(int n) {
        ConcurrentLinkedDeque<Integer> q = new ConcurrentLinkedDeque<Integer>();
        assertTrue(q.isEmpty());
        for (int i = 0; i < n; ++i)
            assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
        assertEquals(n, q.size());
        return q;
    }

    /**
     * new deque is empty
     */
    public void testConstructor1() {
        assertTrue(new ConcurrentLinkedDeque().isEmpty());
        assertEquals(0, new ConcurrentLinkedDeque().size());
    }

    /**
     * Initializing from null Collection throws NPE
     */
    public void testConstructor3() {
        try {
            new ConcurrentLinkedDeque((Collection)null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws NPE
     */
    public void testConstructor4() {
        try {
            new ConcurrentLinkedDeque(Arrays.asList(new Integer[SIZE]));
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
            new ConcurrentLinkedDeque(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Deque contains all elements of collection used to initialize
     */
    public void testConstructor6() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(i);
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * isEmpty is true before add, false after
     */
    public void testEmpty() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        assertTrue(q.isEmpty());
        q.add(one);
        assertFalse(q.isEmpty());
        q.add(two);
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    /**
     * size() changes when elements added and removed
     */
    public void testSize() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(SIZE - i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    /**
     * push(null) throws NPE
     */
    public void testPushNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.push(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * peekFirst() returns element inserted with push
     */
    public void testPush() {
        ConcurrentLinkedDeque q = populatedDeque(3);
        q.pollLast();
        q.push(four);
        assertSame(four, q.peekFirst());
    }

    /**
     * pop() removes first element, or throws NSEE if empty
     */
    public void testPop() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.pop());
        }
        try {
            q.pop();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * offer(null) throws NPE
     */
    public void testOfferNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.offer(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * offerFirst(null) throws NPE
     */
    public void testOfferFirstNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.offerFirst(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * offerLast(null) throws NPE
     */
    public void testOfferLastNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.offerLast(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * offer(x) succeeds
     */
    public void testOffer() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        assertTrue(q.offer(zero));
        assertTrue(q.offer(one));
        assertSame(zero, q.peekFirst());
        assertSame(one, q.peekLast());
    }

    /**
     * offerFirst(x) succeeds
     */
    public void testOfferFirst() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        assertTrue(q.offerFirst(zero));
        assertTrue(q.offerFirst(one));
        assertSame(one, q.peekFirst());
        assertSame(zero, q.peekLast());
    }

    /**
     * offerLast(x) succeeds
     */
    public void testOfferLast() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        assertTrue(q.offerLast(zero));
        assertTrue(q.offerLast(one));
        assertSame(zero, q.peekFirst());
        assertSame(one, q.peekLast());
    }

    /**
     * add(null) throws NPE
     */
    public void testAddNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.add(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addFirst(null) throws NPE
     */
    public void testAddFirstNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.addFirst(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addLast(null) throws NPE
     */
    public void testAddLastNull() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.addLast(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * add(x) succeeds
     */
    public void testAdd() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        assertTrue(q.add(zero));
        assertTrue(q.add(one));
        assertSame(zero, q.peekFirst());
        assertSame(one, q.peekLast());
    }

    /**
     * addFirst(x) succeeds
     */
    public void testAddFirst() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        q.addFirst(zero);
        q.addFirst(one);
        assertSame(one, q.peekFirst());
        assertSame(zero, q.peekLast());
    }

    /**
     * addLast(x) succeeds
     */
    public void testAddLast() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        q.addLast(zero);
        q.addLast(one);
        assertSame(zero, q.peekFirst());
        assertSame(one, q.peekLast());
    }

    /**
     * addAll(null) throws NPE
     */
    public void testAddAll1() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.addAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll(this) throws IAE
     */
    public void testAddAllSelf() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        try {
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with null elements throws NPE
     */
    public void testAddAll2() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        try {
            q.addAll(Arrays.asList(new Integer[SIZE]));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll of a collection with any null elements throws NPE after
     * possibly adding some elements
     */
    public void testAddAll3() {
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        try {
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Deque contains all elements, in traversal order, of successful addAll
     */
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(i);
        ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * pollFirst() succeeds unless empty
     */
    public void testPollFirst() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.pollFirst());
        }
        assertNull(q.pollFirst());
    }

    /**
     * pollLast() succeeds unless empty
     */
    public void testPollLast() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = SIZE - 1; i >= 0; --i) {
            assertEquals(i, q.pollLast());
        }
        assertNull(q.pollLast());
    }

    /**
     * poll() succeeds unless empty
     */
    public void testPoll() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.poll());
        }
        assertNull(q.poll());
    }

    /**
     * peek() returns next element, or null if empty
     */
    public void testPeek() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.peek());
            assertEquals(i, q.poll());
            assertTrue(q.peek() == null ||
                       !q.peek().equals(i));
        }
        assertNull(q.peek());
    }

    /**
     * element() returns first element, or throws NSEE if empty
     */
    public void testElement() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.element());
            assertEquals(i, q.poll());
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove() removes next element, or throws NSEE if empty
     */
    public void testRemove() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.remove());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testRemoveElement() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
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
     * peekFirst() returns next element, or null if empty
     */
    public void testPeekFirst() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.peekFirst());
            assertEquals(i, q.pollFirst());
            assertTrue(q.peekFirst() == null ||
                       !q.peekFirst().equals(i));
        }
        assertNull(q.peekFirst());
    }

    /**
     * peekLast() returns next element, or null if empty
     */
    public void testPeekLast() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = SIZE - 1; i >= 0; --i) {
            assertEquals(i, q.peekLast());
            assertEquals(i, q.pollLast());
            assertTrue(q.peekLast() == null ||
                       !q.peekLast().equals(i));
        }
        assertNull(q.peekLast());
    }

    /**
     * getFirst() returns first element, or throws NSEE if empty
     */
    public void testFirstElement() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.getFirst());
            assertEquals(i, q.pollFirst());
        }
        try {
            q.getFirst();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * getLast() returns last element, or throws NSEE if empty
     */
    public void testLastElement() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = SIZE - 1; i >= 0; --i) {
            assertEquals(i, q.getLast());
            assertEquals(i, q.pollLast());
        }
        try {
            q.getLast();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        assertNull(q.peekLast());
    }

    /**
     * removeFirst() removes first element, or throws NSEE if empty
     */
    public void testRemoveFirst() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.removeFirst());
        }
        try {
            q.removeFirst();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        assertNull(q.peekFirst());
    }

    /**
     * removeLast() removes last element, or throws NSEE if empty
     */
    public void testRemoveLast() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = SIZE - 1; i >= 0; --i) {
            assertEquals(i, q.removeLast());
        }
        try {
            q.removeLast();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        assertNull(q.peekLast());
    }

    /**
     * removeFirstOccurrence(x) removes x and returns true if present
     */
    public void testRemoveFirstOccurrence() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 1; i < SIZE; i += 2) {
            assertTrue(q.removeFirstOccurrence(new Integer(i)));
        }
        for (int i = 0; i < SIZE; i += 2) {
            assertTrue(q.removeFirstOccurrence(new Integer(i)));
            assertFalse(q.removeFirstOccurrence(new Integer(i + 1)));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * removeLastOccurrence(x) removes x and returns true if present
     */
    public void testRemoveLastOccurrence() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 1; i < SIZE; i += 2) {
            assertTrue(q.removeLastOccurrence(new Integer(i)));
        }
        for (int i = 0; i < SIZE; i += 2) {
            assertTrue(q.removeLastOccurrence(new Integer(i)));
            assertFalse(q.removeLastOccurrence(new Integer(i + 1)));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    /**
     * clear() removes all elements
     */
    public void testClear() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        q.add(one);
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        ConcurrentLinkedDeque p = new ConcurrentLinkedDeque();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true if change
     */
    public void testRetainAll() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        ConcurrentLinkedDeque p = populatedDeque(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0)
                assertFalse(changed);
            else
                assertTrue(changed);

            assertTrue(q.containsAll(p));
            assertEquals(SIZE - i, q.size());
            p.remove();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            ConcurrentLinkedDeque q = populatedDeque(SIZE);
            ConcurrentLinkedDeque p = populatedDeque(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer x = (Integer)(p.remove());
                assertFalse(q.contains(x));
            }
        }
    }

    /**
     * toArray() contains all elements in FIFO order
     */
    public void testToArray() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        Object[] o = q.toArray();
        for (int i = 0; i < o.length; i++)
            assertSame(o[i], q.poll());
    }

    /**
     * toArray(a) contains all elements in FIFO order
     */
    public void testToArray2() {
        ConcurrentLinkedDeque<Integer> q = populatedDeque(SIZE);
        Integer[] ints = new Integer[SIZE];
        Integer[] array = q.toArray(ints);
        assertSame(ints, array);
        for (int i = 0; i < ints.length; i++)
            assertSame(ints[i], q.poll());
    }

    /**
     * toArray(null) throws NullPointerException
     */
    public void testToArray_NullArg() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        try {
            q.toArray(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * toArray(incompatible array type) throws ArrayStoreException
     */
    public void testToArray1_BadArg() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        try {
            q.toArray(new String[10]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * Iterator iterates through all elements
     */
    public void testIterator() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        Iterator it = q.iterator();
        int i;
        for (i = 0; it.hasNext(); i++)
            assertTrue(q.contains(it.next()));
        assertEquals(i, SIZE);
        assertIteratorExhausted(it);
    }

    /**
     * iterator of empty collection has no elements
     */
    public void testEmptyIterator() {
        Deque c = new ConcurrentLinkedDeque();
        assertIteratorExhausted(c.iterator());
        assertIteratorExhausted(c.descendingIterator());
    }

    /**
     * Iterator ordering is FIFO
     */
    public void testIteratorOrdering() {
        final ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        q.add(one);
        q.add(two);
        q.add(three);

        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            assertEquals(++k, it.next());
        }

        assertEquals(3, k);
    }

    /**
     * Modifications do not cause iterators to fail
     */
    public void testWeaklyConsistentIteration() {
        final ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        q.add(one);
        q.add(two);
        q.add(three);

        for (Iterator it = q.iterator(); it.hasNext();) {
            q.remove();
            it.next();
        }

        assertEquals("deque should be empty again", 0, q.size());
    }

    /**
     * iterator.remove() removes current element
     */
    public void testIteratorRemove() {
        final ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        final Random rng = new Random();
        for (int iters = 0; iters < 100; ++iters) {
            int max = rng.nextInt(5) + 2;
            int split = rng.nextInt(max - 1) + 1;
            for (int j = 1; j <= max; ++j)
                q.add(new Integer(j));
            Iterator it = q.iterator();
            for (int j = 1; j <= split; ++j)
                assertEquals(it.next(), new Integer(j));
            it.remove();
            assertEquals(it.next(), new Integer(split + 1));
            for (int j = 1; j <= split; ++j)
                q.remove(new Integer(j));
            it = q.iterator();
            for (int j = split + 1; j <= max; ++j) {
                assertEquals(it.next(), new Integer(j));
                it.remove();
            }
            assertFalse(it.hasNext());
            assertTrue(q.isEmpty());
        }
    }

    /**
     * Descending iterator iterates through all elements
     */
    public void testDescendingIterator() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        int i = 0;
        Iterator it = q.descendingIterator();
        while (it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, SIZE);
        assertFalse(it.hasNext());
        try {
            it.next();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * Descending iterator ordering is reverse FIFO
     */
    public void testDescendingIteratorOrdering() {
        final ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        for (int iters = 0; iters < 100; ++iters) {
            q.add(new Integer(3));
            q.add(new Integer(2));
            q.add(new Integer(1));
            int k = 0;
            for (Iterator it = q.descendingIterator(); it.hasNext();) {
                assertEquals(++k, it.next());
            }

            assertEquals(3, k);
            q.remove();
            q.remove();
            q.remove();
        }
    }

    /**
     * descendingIterator.remove() removes current element
     */
    public void testDescendingIteratorRemove() {
        final ConcurrentLinkedDeque q = new ConcurrentLinkedDeque();
        final Random rng = new Random();
        for (int iters = 0; iters < 100; ++iters) {
            int max = rng.nextInt(5) + 2;
            int split = rng.nextInt(max - 1) + 1;
            for (int j = max; j >= 1; --j)
                q.add(new Integer(j));
            Iterator it = q.descendingIterator();
            for (int j = 1; j <= split; ++j)
                assertEquals(it.next(), new Integer(j));
            it.remove();
            assertEquals(it.next(), new Integer(split + 1));
            for (int j = 1; j <= split; ++j)
                q.remove(new Integer(j));
            it = q.descendingIterator();
            for (int j = split + 1; j <= max; ++j) {
                assertEquals(it.next(), new Integer(j));
                it.remove();
            }
            assertFalse(it.hasNext());
            assertTrue(q.isEmpty());
        }
    }

    /**
     * toString() contains toStrings of elements
     */
    public void testToString() {
        ConcurrentLinkedDeque q = populatedDeque(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * A deserialized serialized deque has same elements in same order
     */
    public void testSerialization() throws Exception {
        Queue x = populatedDeque(SIZE);
        Queue y = serialClone(x);

        assertNotSame(x, y);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertTrue(Arrays.equals(x.toArray(), y.toArray()));
        while (!x.isEmpty()) {
            assertFalse(y.isEmpty());
            assertEquals(x.remove(), y.remove());
        }
        assertTrue(y.isEmpty());
    }

    /**
     * contains(null) always return false.
     * remove(null) always throws NullPointerException.
     */
    public void testNeverContainsNull() {
        Deque<?>[] qs = {
            new ConcurrentLinkedDeque<Object>(),
            populatedDeque(2),
        };

        for (Deque<?> q : qs) {
            assertFalse(q.contains(null));
            try {
                assertFalse(q.remove(null));
                shouldThrow();
            } catch (NullPointerException success) {}
            try {
                assertFalse(q.removeFirstOccurrence(null));
                shouldThrow();
            } catch (NullPointerException success) {}
            try {
                assertFalse(q.removeLastOccurrence(null));
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }
}
