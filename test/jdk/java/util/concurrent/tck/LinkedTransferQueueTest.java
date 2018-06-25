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
 * Other contributors include John Vint
 */

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;

import junit.framework.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
public class LinkedTransferQueueTest extends JSR166TestCase {
    public static class Generic extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new LinkedTransferQueue();
        }
    }

    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        class Implementation implements CollectionImplementation {
            public Class<?> klazz() { return LinkedTransferQueue.class; }
            public Collection emptyCollection() { return new LinkedTransferQueue(); }
            public Object makeElement(int i) { return i; }
            public boolean isConcurrent() { return true; }
            public boolean permitsNulls() { return false; }
        }
        return newTestSuite(LinkedTransferQueueTest.class,
                            new Generic().testSuite(),
                            CollectionTest.testSuite(new Implementation()));
    }

    /**
     * Constructor builds new queue with size being zero and empty
     * being true
     */
    public void testConstructor1() {
        assertEquals(0, new LinkedTransferQueue().size());
        assertTrue(new LinkedTransferQueue().isEmpty());
    }

    /**
     * Initializing constructor with null collection throws
     * NullPointerException
     */
    public void testConstructor2() {
        try {
            new LinkedTransferQueue(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws
     * NullPointerException
     */
    public void testConstructor3() {
        Collection<Integer> elements = Arrays.asList(new Integer[SIZE]);
        try {
            new LinkedTransferQueue(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing constructor with a collection containing some null elements
     * throws NullPointerException
     */
    public void testConstructor4() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = i;
        Collection<Integer> elements = Arrays.asList(ints);
        try {
            new LinkedTransferQueue(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements of the collection it is initialized by
     */
    public void testConstructor5() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            ints[i] = i;
        }
        List intList = Arrays.asList(ints);
        LinkedTransferQueue q
            = new LinkedTransferQueue(intList);
        assertEquals(q.size(), intList.size());
        assertEquals(q.toString(), intList.toString());
        assertTrue(Arrays.equals(q.toArray(),
                                     intList.toArray()));
        assertTrue(Arrays.equals(q.toArray(new Object[0]),
                                 intList.toArray(new Object[0])));
        assertTrue(Arrays.equals(q.toArray(new Object[SIZE]),
                                 intList.toArray(new Object[SIZE])));
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(ints[i], q.poll());
        }
    }

    /**
     * remainingCapacity() always returns Integer.MAX_VALUE
     */
    public void testRemainingCapacity() {
        BlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
            assertEquals(SIZE - i, q.size());
            assertEquals(i, q.remove());
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
            assertEquals(i, q.size());
            assertTrue(q.add(i));
        }
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    public void testAddAllSelf() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with any null elements throws
     * NullPointerException after possibly adding some elements
     */
    public void testAddAll3() {
        LinkedTransferQueue q = new LinkedTransferQueue();
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = i;
        try {
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements, in traversal order, of successful addAll
     */
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            ints[i] = i;
        }
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(ints[i], q.poll());
        }
    }

    /**
     * all elements successfully put are contained
     */
    public void testPut() {
        LinkedTransferQueue<Integer> q = new LinkedTransferQueue<>();
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.size());
            q.put(i);
            assertTrue(q.contains(i));
        }
    }

    /**
     * take retrieves elements in FIFO order
     */
    public void testTake() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.take());
        }
    }

    /**
     * take removes existing elements until empty, then blocks interruptibly
     */
    public void testBlockingTake() throws InterruptedException {
        final BlockingQueue q = populatedQueue(SIZE);
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
    public void testPoll() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll());
        }
        assertNull(q.poll());
        checkEmpty(q);
    }

    /**
     * timed poll with zero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll0() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll(0, MILLISECONDS));
        }
        assertNull(q.poll(0, MILLISECONDS));
        checkEmpty(q);
    }

    /**
     * timed poll with nonzero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        long startTime = System.nanoTime();
        for (int i = 0; i < SIZE; ++i)
            assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);

        startTime = System.nanoTime();
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
     * timed poll after thread interrupted throws InterruptedException
     * instead of returning timeout status
     */
    public void testTimedPollAfterInterrupt() throws InterruptedException {
        final BlockingQueue<Integer> q = populatedQueue(SIZE);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                Thread.currentThread().interrupt();
                for (int i = 0; i < SIZE; ++i)
                    assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
                assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
            }});

        awaitTermination(t);
        checkEmpty(q);
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.peek());
            assertEquals(i, (int) q.poll());
            assertTrue(q.peek() == null ||
                       i != (int) q.peek());
        }
        assertNull(q.peek());
        checkEmpty(q);
    }

    /**
     * element returns next element, or throws NoSuchElementException if empty
     */
    public void testElement() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.element());
            assertEquals(i, (int) q.poll());
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        checkEmpty(q);
    }

    /**
     * remove removes next element, or throws NoSuchElementException if empty
     */
    public void testRemove() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.remove());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        checkEmpty(q);
    }

    /**
     * An add following remove(x) succeeds
     */
    public void testRemoveElementAndAdd() throws InterruptedException {
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.add(one));
        assertTrue(q.add(two));
        assertTrue(q.remove(one));
        assertTrue(q.remove(two));
        assertTrue(q.add(three));
        assertSame(q.take(), three);
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(i));
            assertEquals(i, (int) q.poll());
            assertFalse(q.contains(i));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
        q.clear();
        checkEmpty(q);
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        assertEquals(1, q.size());
        assertTrue(q.contains(one));
        q.clear();
        checkEmpty(q);
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        LinkedTransferQueue<Integer> p = new LinkedTransferQueue<>();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(i);
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true
     * if changed
     */
    public void testRetainAll() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        LinkedTransferQueue p = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0) {
                assertFalse(changed);
            } else {
                assertTrue(changed);
            }
            assertTrue(q.containsAll(p));
            assertEquals(SIZE - i, q.size());
            p.remove();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true
     * if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            LinkedTransferQueue q = populatedQueue(SIZE);
            LinkedTransferQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                assertFalse(q.contains(p.remove()));
            }
        }
    }

    /**
     * toArray() contains all elements in FIFO order
     */
    public void testToArray() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Object[] a = q.toArray();
        assertSame(Object[].class, a.getClass());
        for (Object o : a)
            assertSame(o, q.poll());
        assertTrue(q.isEmpty());
    }

    /**
     * toArray(a) contains all elements in FIFO order
     */
    public void testToArray2() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        Integer[] array = q.toArray(ints);
        assertSame(ints, array);
        for (Integer o : ints)
            assertSame(o, q.poll());
        assertTrue(q.isEmpty());
    }

    /**
     * toArray(incompatible array type) throws ArrayStoreException
     */
    public void testToArray1_BadArg() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.toArray(new String[10]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
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
        assertIteratorExhausted(new LinkedTransferQueue().iterator());
    }

    /**
     * iterator.remove() removes current element
     */
    public void testIteratorRemove() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
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
        final LinkedTransferQueue<Integer> q = new LinkedTransferQueue<>();
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        q.add(one);
        q.add(two);
        q.add(three);
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        int k = 0;
        for (Integer n : q) {
            assertEquals(++k, (int) n);
        }
        assertEquals(3, k);
    }

    /**
     * Modifications do not cause iterators to fail
     */
    public void testWeaklyConsistentIteration() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
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
        LinkedTransferQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * offer transfers elements across Executor tasks
     */
    public void testOfferInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(executor)) {

            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadsStarted.await();
                    long startTime = System.nanoTime();
                    assertTrue(q.offer(one, LONG_DELAY_MS, MILLISECONDS));
                    assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
                }});

            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadsStarted.await();
                    assertSame(one, q.take());
                    checkEmpty(q);
                }});
        }
    }

    /**
     * timed poll retrieves elements across Executor threads
     */
    public void testPollInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(executor)) {

            executor.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertNull(q.poll());
                    threadsStarted.await();
                    long startTime = System.nanoTime();
                    assertSame(one, q.poll(LONG_DELAY_MS, MILLISECONDS));
                    assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
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

        assertNotSame(y, x);
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
        LinkedTransferQueue q = populatedQueue(SIZE);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(SIZE, l.size());
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, l.get(i));
        }
        q.add(zero);
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(zero));
        assertTrue(q.contains(one));
        l.clear();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(2, l.size());
        for (int i = 0; i < 2; ++i) {
            assertEquals(i, l.get(i));
        }
    }

    /**
     * drainTo(c) empties full queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut() throws InterruptedException {
        final LinkedTransferQueue q = populatedQueue(SIZE);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                q.put(SIZE + 1);
            }});
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertTrue(l.size() >= SIZE);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(i, l.get(i));
        awaitTermination(t);
        assertTrue(q.size() + l.size() >= SIZE);
    }

    /**
     * drainTo(c, n) empties first min(n, size) elements of queue into c
     */
    public void testDrainToN() {
        LinkedTransferQueue q = new LinkedTransferQueue();
        for (int i = 0; i < SIZE + 2; ++i) {
            for (int j = 0; j < SIZE; j++) {
                assertTrue(q.offer(j));
            }
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(k, l.size());
            assertEquals(SIZE - k, q.size());
            for (int j = 0; j < k; ++j)
                assertEquals(j, l.get(j));
            do {} while (q.poll() != null);
        }
    }

    /**
     * timed poll() or take() increments the waiting consumer count;
     * offer(e) decrements the waiting consumer count
     */
    public void testWaitingConsumer() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertEquals(0, q.getWaitingConsumerCount());
        assertFalse(q.hasWaitingConsumer());
        final CountDownLatch threadStarted = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadStarted.countDown();
                long startTime = System.nanoTime();
                assertSame(one, q.poll(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(0, q.getWaitingConsumerCount());
                assertFalse(q.hasWaitingConsumer());
                assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
            }});

        threadStarted.await();
        Callable<Boolean> oneConsumer
            = new Callable<Boolean>() { public Boolean call() {
                return q.hasWaitingConsumer()
                && q.getWaitingConsumerCount() == 1; }};
        waitForThreadToEnterWaitState(t, oneConsumer);

        assertTrue(q.offer(one));
        assertEquals(0, q.getWaitingConsumerCount());
        assertFalse(q.hasWaitingConsumer());

        awaitTermination(t);
    }

    /**
     * transfer(null) throws NullPointerException
     */
    public void testTransfer1() throws InterruptedException {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.transfer(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * transfer waits until a poll occurs. The transferred element
     * is returned by the associated poll.
     */
    public void testTransfer2() throws InterruptedException {
        final LinkedTransferQueue<Integer> q = new LinkedTransferQueue<>();
        final CountDownLatch threadStarted = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadStarted.countDown();
                q.transfer(five);
                checkEmpty(q);
            }});

        threadStarted.await();
        Callable<Boolean> oneElement
            = new Callable<Boolean>() { public Boolean call() {
                return !q.isEmpty() && q.size() == 1; }};
        waitForThreadToEnterWaitState(t, oneElement);

        assertSame(five, q.poll());
        checkEmpty(q);
        awaitTermination(t);
    }

    /**
     * transfer waits until a poll occurs, and then transfers in fifo order
     */
    public void testTransfer3() throws InterruptedException {
        final LinkedTransferQueue<Integer> q = new LinkedTransferQueue<>();

        Thread first = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.transfer(four);
                assertFalse(q.contains(four));
                assertEquals(1, q.size());
            }});

        Thread interruptedThread = newStartedThread(
            new CheckedInterruptedRunnable() {
                public void realRun() throws InterruptedException {
                    while (q.isEmpty())
                        Thread.yield();
                    q.transfer(five);
                }});

        while (q.size() < 2)
            Thread.yield();
        assertEquals(2, q.size());
        assertSame(four, q.poll());
        first.join();
        assertEquals(1, q.size());
        interruptedThread.interrupt();
        interruptedThread.join();
        checkEmpty(q);
    }

    /**
     * transfer waits until a poll occurs, at which point the polling
     * thread returns the element
     */
    public void testTransfer4() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.transfer(four);
                assertFalse(q.contains(four));
                assertSame(three, q.poll());
            }});

        while (q.isEmpty())
            Thread.yield();
        assertFalse(q.isEmpty());
        assertEquals(1, q.size());
        assertTrue(q.offer(three));
        assertSame(four, q.poll());
        awaitTermination(t);
    }

    /**
     * transfer waits until a take occurs. The transferred element
     * is returned by the associated take.
     */
    public void testTransfer5() throws InterruptedException {
        final LinkedTransferQueue<Integer> q = new LinkedTransferQueue<>();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.transfer(four);
                checkEmpty(q);
            }});

        while (q.isEmpty())
            Thread.yield();
        assertFalse(q.isEmpty());
        assertEquals(1, q.size());
        assertSame(four, q.take());
        checkEmpty(q);
        awaitTermination(t);
    }

    /**
     * tryTransfer(null) throws NullPointerException
     */
    public void testTryTransfer1() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        try {
            q.tryTransfer(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * tryTransfer returns false and does not enqueue if there are no
     * consumers waiting to poll or take.
     */
    public void testTryTransfer2() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertFalse(q.tryTransfer(new Object()));
        assertFalse(q.hasWaitingConsumer());
        checkEmpty(q);
    }

    /**
     * If there is a consumer waiting in timed poll, tryTransfer
     * returns true while successfully transfering object.
     */
    public void testTryTransfer3() throws InterruptedException {
        final Object hotPotato = new Object();
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                while (! q.hasWaitingConsumer())
                    Thread.yield();
                assertTrue(q.hasWaitingConsumer());
                checkEmpty(q);
                assertTrue(q.tryTransfer(hotPotato));
            }});

        long startTime = System.nanoTime();
        assertSame(hotPotato, q.poll(LONG_DELAY_MS, MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        checkEmpty(q);
        awaitTermination(t);
    }

    /**
     * If there is a consumer waiting in take, tryTransfer returns
     * true while successfully transfering object.
     */
    public void testTryTransfer4() throws InterruptedException {
        final Object hotPotato = new Object();
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                while (! q.hasWaitingConsumer())
                    Thread.yield();
                assertTrue(q.hasWaitingConsumer());
                checkEmpty(q);
                assertTrue(q.tryTransfer(hotPotato));
            }});

        assertSame(q.take(), hotPotato);
        checkEmpty(q);
        awaitTermination(t);
    }

    /**
     * tryTransfer blocks interruptibly if no takers
     */
    public void testTryTransfer5() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        assertTrue(q.isEmpty());

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                Thread.currentThread().interrupt();
                try {
                    q.tryTransfer(new Object(), LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.tryTransfer(new Object(), LONG_DELAY_MS, MILLISECONDS);
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
     * tryTransfer gives up after the timeout and returns false
     */
    public void testTryTransfer6() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                assertFalse(q.tryTransfer(new Object(),
                                          timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                checkEmpty(q);
            }});

        awaitTermination(t);
        checkEmpty(q);
    }

    /**
     * tryTransfer waits for any elements previously in to be removed
     * before transfering to a poll or take
     */
    public void testTryTransfer7() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.offer(four));

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                assertTrue(q.tryTransfer(five, LONG_DELAY_MS, MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
                checkEmpty(q);
            }});

        while (q.size() != 2)
            Thread.yield();
        assertEquals(2, q.size());
        assertSame(four, q.poll());
        assertSame(five, q.poll());
        checkEmpty(q);
        awaitTermination(t);
    }

    /**
     * tryTransfer attempts to enqueue into the queue and fails
     * returning false not enqueueing and the successive poll is null
     */
    public void testTryTransfer8() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.offer(four));
        assertEquals(1, q.size());
        long startTime = System.nanoTime();
        assertFalse(q.tryTransfer(five, timeoutMillis(), MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        assertEquals(1, q.size());
        assertSame(four, q.poll());
        assertNull(q.poll());
        checkEmpty(q);
    }

    private LinkedTransferQueue<Integer> populatedQueue(int n) {
        LinkedTransferQueue<Integer> q = new LinkedTransferQueue<>();
        checkEmpty(q);
        for (int i = 0; i < n; i++) {
            assertEquals(i, q.size());
            assertTrue(q.offer(i));
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        }
        assertFalse(q.isEmpty());
        return q;
    }

    /**
     * remove(null), contains(null) always return false
     */
    public void testNeverContainsNull() {
        Collection<?>[] qs = {
            new LinkedTransferQueue<Object>(),
            populatedQueue(2),
        };

        for (Collection<?> q : qs) {
            assertFalse(q.contains(null));
            assertFalse(q.remove(null));
        }
    }
}
