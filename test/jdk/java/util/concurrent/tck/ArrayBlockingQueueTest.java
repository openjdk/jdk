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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import junit.framework.Test;

public class ArrayBlockingQueueTest extends JSR166TestCase {

    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        class Implementation implements CollectionImplementation {
            public Class<?> klazz() { return ArrayBlockingQueue.class; }
            public Collection emptyCollection() {
                boolean fair = ThreadLocalRandom.current().nextBoolean();
                return populatedQueue(0, SIZE, 2 * SIZE, fair);
            }
            public Object makeElement(int i) { return i; }
            public boolean isConcurrent() { return true; }
            public boolean permitsNulls() { return false; }
        }

        return newTestSuite(
            ArrayBlockingQueueTest.class,
            new Fair().testSuite(),
            new NonFair().testSuite(),
            CollectionTest.testSuite(new Implementation()));
    }

    public static class Fair extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return populatedQueue(0, SIZE, 2 * SIZE, true);
        }
    }

    public static class NonFair extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return populatedQueue(0, SIZE, 2 * SIZE, false);
        }
    }

    /**
     * Returns a new queue of given size containing consecutive
     * Integers 0 ... n - 1.
     */
    static ArrayBlockingQueue<Integer> populatedQueue(int n) {
        return populatedQueue(n, n, n, false);
    }

    /**
     * Returns a new queue of given size containing consecutive
     * Integers 0 ... n - 1, with given capacity range and fairness.
     */
    static ArrayBlockingQueue<Integer> populatedQueue(
        int size, int minCapacity, int maxCapacity, boolean fair) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int capacity = rnd.nextInt(minCapacity, maxCapacity + 1);
        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(capacity);
        assertTrue(q.isEmpty());
        // shuffle circular array elements so they wrap
        {
            int n = rnd.nextInt(capacity);
            for (int i = 0; i < n; i++) q.add(42);
            for (int i = 0; i < n; i++) q.remove();
        }
        for (int i = 0; i < size; i++)
            assertTrue(q.offer((Integer) i));
        assertEquals(size == 0, q.isEmpty());
        assertEquals(capacity - size, q.remainingCapacity());
        assertEquals(size, q.size());
        if (size > 0)
            assertEquals((Integer) 0, q.peek());
        return q;
    }

    /**
     * A new queue has the indicated capacity
     */
    public void testConstructor1() {
        assertEquals(SIZE, new ArrayBlockingQueue(SIZE).remainingCapacity());
    }

    /**
     * Constructor throws IllegalArgumentException if capacity argument nonpositive
     */
    public void testConstructor_nonPositiveCapacity() {
        for (int i : new int[] { 0, -1, Integer.MIN_VALUE }) {
            try {
                new ArrayBlockingQueue(i);
                shouldThrow();
            } catch (IllegalArgumentException success) {}
            for (boolean fair : new boolean[] { true, false }) {
                try {
                    new ArrayBlockingQueue(i, fair);
                    shouldThrow();
                } catch (IllegalArgumentException success) {}
            }
        }
    }

    /**
     * Initializing from null Collection throws NPE
     */
    public void testConstructor_nullCollection() {
        try {
            new ArrayBlockingQueue(1, true, null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws NPE
     */
    public void testConstructor4() {
        Collection<Integer> elements = Arrays.asList(new Integer[SIZE]);
        try {
            new ArrayBlockingQueue(SIZE, false, elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection with some null elements throws NPE
     */
    public void testConstructor5() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = i;
        Collection<Integer> elements = Arrays.asList(ints);
        try {
            new ArrayBlockingQueue(SIZE, false, elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from too large collection throws IllegalArgumentException
     */
    public void testConstructor_collectionTooLarge() {
        // just barely fits - succeeds
        new ArrayBlockingQueue(SIZE, false,
                               Collections.nCopies(SIZE, ""));
        try {
            new ArrayBlockingQueue(SIZE - 1, false,
                                   Collections.nCopies(SIZE, ""));
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Queue contains all elements of collection used to initialize
     */
    public void testConstructor7() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = i;
        Collection<Integer> elements = Arrays.asList(ints);
        ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE, true, elements);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * Queue transitions from empty to full when elements added
     */
    public void testEmptyFull() {
        BlockingQueue q = populatedQueue(0, 2, 2, false);
        assertTrue(q.isEmpty());
        assertEquals(2, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.offer(two));
        assertFalse(q.isEmpty());
        assertEquals(0, q.remainingCapacity());
        assertFalse(q.offer(three));
    }

    /**
     * remainingCapacity decreases on add, increases on remove
     */
    public void testRemainingCapacity() {
        int size = ThreadLocalRandom.current().nextInt(1, SIZE);
        BlockingQueue q = populatedQueue(size, size, 2 * size, false);
        int spare = q.remainingCapacity();
        int capacity = spare + size;
        for (int i = 0; i < size; i++) {
            assertEquals(spare + i, q.remainingCapacity());
            assertEquals(capacity, q.size() + q.remainingCapacity());
            assertEquals(i, q.remove());
        }
        for (int i = 0; i < size; i++) {
            assertEquals(capacity - i, q.remainingCapacity());
            assertEquals(capacity, q.size() + q.remainingCapacity());
            assertTrue(q.add(i));
        }
    }

    /**
     * Offer succeeds if not full; fails if full
     */
    public void testOffer() {
        ArrayBlockingQueue q = new ArrayBlockingQueue(1);
        assertTrue(q.offer(zero));
        assertFalse(q.offer(one));
    }

    /**
     * add succeeds if not full; throws IllegalStateException if full
     */
    public void testAdd() {
        ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; i++) assertTrue(q.add((Integer) i));
        assertEquals(0, q.remainingCapacity());
        try {
            q.add((Integer) SIZE);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    public void testAddAllSelf() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        try {
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with any null elements throws NPE after
     * possibly adding some elements
     */
    public void testAddAll3() {
        ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = new Integer(i);
        try {
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll throws IllegalStateException if not enough room
     */
    public void testAddAll_insufficientSpace() {
        int size = ThreadLocalRandom.current().nextInt(1, SIZE);
        ArrayBlockingQueue q = populatedQueue(0, size, size, false);
        // Just fits:
        q.addAll(populatedQueue(size, size, 2 * size, false));
        assertEquals(0, q.remainingCapacity());
        assertEquals(size, q.size());
        assertEquals(0, q.peek());
        try {
            q = populatedQueue(0, size, size, false);
            q.addAll(Collections.nCopies(size + 1, 42));
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * Queue contains all elements, in traversal order, of successful addAll
     */
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new Integer(i);
        ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE);
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * all elements successfully put are contained
     */
    public void testPut() throws InterruptedException {
        ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            Integer x = new Integer(i);
            q.put(x);
            assertTrue(q.contains(x));
        }
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * put blocks interruptibly if full
     */
    public void testBlockingPut() throws InterruptedException {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i)
                    q.put(i);
                assertEquals(SIZE, q.size());
                assertEquals(0, q.remainingCapacity());

                Thread.currentThread().interrupt();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadBlocks(t, Thread.State.WAITING);
        t.interrupt();
        awaitTermination(t);
        assertEquals(SIZE, q.size());
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * put blocks interruptibly waiting for take when full
     */
    public void testPutWithTake() throws InterruptedException {
        final int capacity = 2;
        final ArrayBlockingQueue q = new ArrayBlockingQueue(capacity);
        final CountDownLatch pleaseTake = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < capacity; i++)
                    q.put(i);
                pleaseTake.countDown();
                q.put(86);

                Thread.currentThread().interrupt();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseTake);
        assertEquals(0, q.remainingCapacity());
        assertEquals(0, q.take());

        await(pleaseInterrupt);
        assertThreadBlocks(t, Thread.State.WAITING);
        t.interrupt();
        awaitTermination(t);
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * timed offer times out if full and elements not taken
     */
    public void testTimedOffer() {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(new Object());
                q.put(new Object());
                long startTime = System.nanoTime();
                assertFalse(q.offer(new Object(), timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());

                Thread.currentThread().interrupt();
                try {
                    q.offer(new Object(), 2 * LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.offer(new Object(), 2 * LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadBlocks(t, Thread.State.TIMED_WAITING);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * take retrieves elements in FIFO order
     */
    public void testTake() throws InterruptedException {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.take());
        }
    }

    /**
     * Take removes existing elements until empty, then blocks interruptibly
     */
    public void testBlockingTake() throws InterruptedException {
        final ArrayBlockingQueue q = populatedQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; i++) assertEquals(i, q.take());

                Thread.currentThread().interrupt();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadBlocks(t, Thread.State.WAITING);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * poll succeeds unless empty
     */
    public void testPoll() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.poll());
        }
        assertNull(q.poll());
    }

    /**
     * timed poll with zero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll0() throws InterruptedException {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.poll(0, MILLISECONDS));
        }
        assertNull(q.poll(0, MILLISECONDS));
        checkEmpty(q);
    }

    /**
     * timed poll with nonzero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll() throws InterruptedException {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            long startTime = System.nanoTime();
            assertEquals(i, q.poll(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        }
        long startTime = System.nanoTime();
        assertNull(q.poll(timeoutMillis(), MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        checkEmpty(q);
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testInterruptedTimedPoll() throws InterruptedException {
        final BlockingQueue<Integer> q = populatedQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                for (int i = 0; i < SIZE; i++)
                    assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));

                Thread.currentThread().interrupt();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
            }});

        await(pleaseInterrupt);
        assertThreadBlocks(t, Thread.State.TIMED_WAITING);
        t.interrupt();
        awaitTermination(t);
        checkEmpty(q);
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.peek());
            assertEquals(i, q.poll());
            assertTrue(q.peek() == null ||
                       !q.peek().equals(i));
        }
        assertNull(q.peek());
    }

    /**
     * element returns next element, or throws NSEE if empty
     */
    public void testElement() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
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
     * remove removes next element, or throws NSEE if empty
     */
    public void testRemove() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.remove());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        int size = ThreadLocalRandom.current().nextInt(1, SIZE);
        ArrayBlockingQueue q = populatedQueue(size, size, 2 * size, false);
        assertFalse(q.contains(null));
        for (int i = 0; i < size; ++i) {
            assertTrue(q.contains(new Integer(i)));
            assertEquals(i, q.poll());
            assertFalse(q.contains(new Integer(i)));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() {
        int size = ThreadLocalRandom.current().nextInt(1, 5);
        ArrayBlockingQueue q = populatedQueue(size, size, 2 * size, false);
        int capacity = size + q.remainingCapacity();
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(capacity, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(one));
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        ArrayBlockingQueue p = new ArrayBlockingQueue(SIZE);
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
        ArrayBlockingQueue q = populatedQueue(SIZE);
        ArrayBlockingQueue p = populatedQueue(SIZE);
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
            ArrayBlockingQueue q = populatedQueue(SIZE);
            ArrayBlockingQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer x = (Integer)(p.remove());
                assertFalse(q.contains(x));
            }
        }
    }

    void checkToArray(ArrayBlockingQueue<Integer> q) {
        int size = q.size();
        Object[] a1 = q.toArray();
        assertEquals(size, a1.length);
        Integer[] a2 = q.toArray(new Integer[0]);
        assertEquals(size, a2.length);
        Integer[] a3 = q.toArray(new Integer[Math.max(0, size - 1)]);
        assertEquals(size, a3.length);
        Integer[] a4 = new Integer[size];
        assertSame(a4, q.toArray(a4));
        Integer[] a5 = new Integer[size + 1];
        Arrays.fill(a5, 42);
        assertSame(a5, q.toArray(a5));
        Integer[] a6 = new Integer[size + 2];
        Arrays.fill(a6, 42);
        assertSame(a6, q.toArray(a6));
        Object[][] as = { a1, a2, a3, a4, a5, a6 };
        for (Object[] a : as) {
            if (a.length > size) assertNull(a[size]);
            if (a.length > size + 1) assertEquals(42, a[size + 1]);
        }
        Iterator it = q.iterator();
        Integer s = q.peek();
        for (int i = 0; i < size; i++) {
            Integer x = (Integer) it.next();
            assertEquals(s + i, (int) x);
            for (Object[] a : as)
                assertSame(a1[i], x);
        }
    }

    /**
     * toArray() and toArray(a) contain all elements in FIFO order
     */
    public void testToArray() {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int size = rnd.nextInt(6);
        final int capacity = Math.max(1, size + rnd.nextInt(size + 1));
        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < size; i++) {
            checkToArray(q);
            q.add(i);
        }
        // Provoke wraparound
        int added = size * 2;
        for (int i = 0; i < added; i++) {
            checkToArray(q);
            assertEquals((Integer) i, q.poll());
            q.add(size + i);
        }
        for (int i = 0; i < size; i++) {
            checkToArray(q);
            assertEquals((Integer) (added + i), q.poll());
        }
    }

    /**
     * toArray(incompatible array type) throws ArrayStoreException
     */
    public void testToArray_incompatibleArrayType() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        try {
            q.toArray(new String[10]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
        try {
            q.toArray(new String[0]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() throws InterruptedException {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        Iterator it = q.iterator();
        int i;
        for (i = 0; it.hasNext(); i++)
            assertTrue(q.contains(it.next()));
        assertEquals(i, SIZE);
        assertIteratorExhausted(it);

        it = q.iterator();
        for (i = 0; it.hasNext(); i++)
            assertEquals(it.next(), q.take());
        assertEquals(i, SIZE);
        assertIteratorExhausted(it);
    }

    /**
     * iterator of empty collection has no elements
     */
    public void testEmptyIterator() {
        assertIteratorExhausted(new ArrayBlockingQueue(SIZE).iterator());
    }

    /**
     * iterator.remove removes current element
     */
    public void testIteratorRemove() {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(3);
        q.add(two);
        q.add(one);
        q.add(three);

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertSame(it.next(), one);
        assertSame(it.next(), three);
        assertFalse(it.hasNext());
    }

    /**
     * iterator ordering is FIFO
     */
    public void testIteratorOrdering() {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(3);
        q.add(one);
        q.add(two);
        q.add(three);

        assertEquals("queue should be full", 0, q.remainingCapacity());

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
        final ArrayBlockingQueue q = new ArrayBlockingQueue(3);
        q.add(one);
        q.add(two);
        q.add(three);
        for (Iterator it = q.iterator(); it.hasNext();) {
            q.remove();
            it.next();
        }
        assertEquals(0, q.size());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testToString() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * offer transfers elements across Executor tasks
     */
    public void testOfferInExecutor() {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        q.add(one);
        q.add(two);
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(executor)) {
            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertFalse(q.offer(three));
                    threadsStarted.await();
                    assertTrue(q.offer(three, LONG_DELAY_MS, MILLISECONDS));
                    assertEquals(0, q.remainingCapacity());
                }});

            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadsStarted.await();
                    assertEquals(0, q.remainingCapacity());
                    assertSame(one, q.take());
                }});
        }
    }

    /**
     * timed poll retrieves elements across Executor threads
     */
    public void testPollInExecutor() {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(executor)) {
            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertNull(q.poll());
                    threadsStarted.await();
                    assertSame(one, q.poll(LONG_DELAY_MS, MILLISECONDS));
                    checkEmpty(q);
                }});

            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadsStarted.await();
                    q.put(one);
                }});
        }
    }

    /**
     * A deserialized/reserialized queue has same elements in same order
     */
    public void testSerialization() throws Exception {
        Queue x = populatedQueue(SIZE);
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
     * drainTo(c) empties queue into another collection c
     */
    public void testDrainTo() {
        ArrayBlockingQueue q = populatedQueue(SIZE);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(SIZE, l.size());
        for (int i = 0; i < SIZE; ++i)
            assertEquals(l.get(i), new Integer(i));
        q.add(zero);
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(zero));
        assertTrue(q.contains(one));
        l.clear();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(2, l.size());
        for (int i = 0; i < 2; ++i)
            assertEquals(l.get(i), new Integer(i));
    }

    /**
     * drainTo empties full queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut() throws InterruptedException {
        final ArrayBlockingQueue q = populatedQueue(SIZE);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(new Integer(SIZE + 1));
            }});

        t.start();
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertTrue(l.size() >= SIZE);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(l.get(i), new Integer(i));
        t.join();
        assertTrue(q.size() + l.size() >= SIZE);
    }

    /**
     * drainTo(c, n) empties first min(n, size) elements of queue into c
     */
    public void testDrainToN() {
        ArrayBlockingQueue q = new ArrayBlockingQueue(SIZE * 2);
        for (int i = 0; i < SIZE + 2; ++i) {
            for (int j = 0; j < SIZE; j++)
                assertTrue(q.offer(new Integer(j)));
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(k, l.size());
            assertEquals(SIZE - k, q.size());
            for (int j = 0; j < k; ++j)
                assertEquals(l.get(j), new Integer(j));
            do {} while (q.poll() != null);
        }
    }

    /**
     * remove(null), contains(null) always return false
     */
    public void testNeverContainsNull() {
        Collection<?>[] qs = {
            populatedQueue(0, 1, 10, false),
            populatedQueue(2, 2, 10, true),
        };

        for (Collection<?> q : qs) {
            assertFalse(q.contains(null));
            assertFalse(q.remove(null));
        }
    }
}
