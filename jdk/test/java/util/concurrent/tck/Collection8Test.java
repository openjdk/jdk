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
 */

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import junit.framework.Test;

/**
 * Contains tests applicable to all jdk8+ Collection implementations.
 * An extension of CollectionTest.
 */
public class Collection8Test extends JSR166TestCase {
    final CollectionImplementation impl;

    /** Tests are parameterized by a Collection implementation. */
    Collection8Test(CollectionImplementation impl, String methodName) {
        super(methodName);
        this.impl = impl;
    }

    public static Test testSuite(CollectionImplementation impl) {
        return parameterizedTestSuite(Collection8Test.class,
                                      CollectionImplementation.class,
                                      impl);
    }

    Object bomb() {
        return new Object() {
                public boolean equals(Object x) { throw new AssertionError(); }
                public int hashCode() { throw new AssertionError(); }
            };
    }

    /** Checks properties of empty collections. */
    public void testEmptyMeansEmpty() throws Throwable {
        Collection c = impl.emptyCollection();
        emptyMeansEmpty(c);

        if (c instanceof java.io.Serializable) {
            try {
                emptyMeansEmpty(serialClonePossiblyFailing(c));
            } catch (java.io.NotSerializableException ex) {
                // excusable when we have a serializable wrapper around
                // a non-serializable collection, as can happen with:
                // Vector.subList() => wrapped AbstractList$RandomAccessSubList
                if (testImplementationDetails
                    && (! c.getClass().getName().matches(
                                "java.util.Collections.*")))
                    throw ex;
            }
        }

        Collection clone = cloneableClone(c);
        if (clone != null)
            emptyMeansEmpty(clone);
    }

    void emptyMeansEmpty(Collection c) throws InterruptedException {
        assertTrue(c.isEmpty());
        assertEquals(0, c.size());
        assertEquals("[]", c.toString());
        {
            Object[] a = c.toArray();
            assertEquals(0, a.length);
            assertSame(Object[].class, a.getClass());
        }
        {
            Object[] a = new Object[0];
            assertSame(a, c.toArray(a));
        }
        {
            Integer[] a = new Integer[0];
            assertSame(a, c.toArray(a));
        }
        {
            Integer[] a = { 1, 2, 3};
            assertSame(a, c.toArray(a));
            assertNull(a[0]);
            assertSame(2, a[1]);
            assertSame(3, a[2]);
        }
        assertIteratorExhausted(c.iterator());
        Consumer alwaysThrows = e -> { throw new AssertionError(); };
        c.forEach(alwaysThrows);
        c.iterator().forEachRemaining(alwaysThrows);
        c.spliterator().forEachRemaining(alwaysThrows);
        assertFalse(c.spliterator().tryAdvance(alwaysThrows));
        if (c.spliterator().hasCharacteristics(Spliterator.SIZED))
            assertEquals(0, c.spliterator().estimateSize());
        assertFalse(c.contains(bomb()));
        assertFalse(c.remove(bomb()));
        if (c instanceof Queue) {
            Queue q = (Queue) c;
            assertNull(q.peek());
            assertNull(q.poll());
        }
        if (c instanceof Deque) {
            Deque d = (Deque) c;
            assertNull(d.peekFirst());
            assertNull(d.peekLast());
            assertNull(d.pollFirst());
            assertNull(d.pollLast());
            assertIteratorExhausted(d.descendingIterator());
            d.descendingIterator().forEachRemaining(alwaysThrows);
            assertFalse(d.removeFirstOccurrence(bomb()));
            assertFalse(d.removeLastOccurrence(bomb()));
        }
        if (c instanceof BlockingQueue) {
            BlockingQueue q = (BlockingQueue) c;
            assertNull(q.poll(0L, MILLISECONDS));
        }
        if (c instanceof BlockingDeque) {
            BlockingDeque q = (BlockingDeque) c;
            assertNull(q.pollFirst(0L, MILLISECONDS));
            assertNull(q.pollLast(0L, MILLISECONDS));
        }
    }

    public void testNullPointerExceptions() throws InterruptedException {
        Collection c = impl.emptyCollection();
        assertThrows(
            NullPointerException.class,
            () -> c.addAll(null),
            () -> c.containsAll(null),
            () -> c.retainAll(null),
            () -> c.removeAll(null),
            () -> c.removeIf(null),
            () -> c.forEach(null),
            () -> c.iterator().forEachRemaining(null),
            () -> c.spliterator().forEachRemaining(null),
            () -> c.spliterator().tryAdvance(null),
            () -> c.toArray(null));

        if (!impl.permitsNulls()) {
            assertThrows(
                NullPointerException.class,
                () -> c.add(null));
        }
        if (!impl.permitsNulls() && c instanceof Queue) {
            Queue q = (Queue) c;
            assertThrows(
                NullPointerException.class,
                () -> q.offer(null));
        }
        if (!impl.permitsNulls() && c instanceof Deque) {
            Deque d = (Deque) c;
            assertThrows(
                NullPointerException.class,
                () -> d.addFirst(null),
                () -> d.addLast(null),
                () -> d.offerFirst(null),
                () -> d.offerLast(null),
                () -> d.push(null),
                () -> d.descendingIterator().forEachRemaining(null));
        }
        if (c instanceof BlockingQueue) {
            BlockingQueue q = (BlockingQueue) c;
            assertThrows(
                NullPointerException.class,
                () -> {
                    try { q.offer(null, 1L, HOURS); }
                    catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }},
                () -> {
                    try { q.put(null); }
                    catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }});
        }
        if (c instanceof BlockingDeque) {
            BlockingDeque q = (BlockingDeque) c;
            assertThrows(
                NullPointerException.class,
                () -> {
                    try { q.offerFirst(null, 1L, HOURS); }
                    catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }},
                () -> {
                    try { q.offerLast(null, 1L, HOURS); }
                    catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }},
                () -> {
                    try { q.putFirst(null); }
                    catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }},
                () -> {
                    try { q.putLast(null); }
                    catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }});
        }
    }

    public void testNoSuchElementExceptions() {
        Collection c = impl.emptyCollection();
        assertThrows(
            NoSuchElementException.class,
            () -> c.iterator().next());

        if (c instanceof Queue) {
            Queue q = (Queue) c;
            assertThrows(
                NoSuchElementException.class,
                () -> q.element(),
                () -> q.remove());
        }
        if (c instanceof Deque) {
            Deque d = (Deque) c;
            assertThrows(
                NoSuchElementException.class,
                () -> d.getFirst(),
                () -> d.getLast(),
                () -> d.removeFirst(),
                () -> d.removeLast(),
                () -> d.pop(),
                () -> d.descendingIterator().next());
        }
    }

    public void testRemoveIf() {
        Collection c = impl.emptyCollection();
        boolean ordered =
            c.spliterator().hasCharacteristics(Spliterator.ORDERED);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = rnd.nextInt(6);
        for (int i = 0; i < n; i++) c.add(impl.makeElement(i));
        AtomicReference threwAt = new AtomicReference(null);
        List orig = rnd.nextBoolean()
            ? new ArrayList(c)
            : Arrays.asList(c.toArray());

        // Merely creating an iterator can change ArrayBlockingQueue behavior
        Iterator it = rnd.nextBoolean() ? c.iterator() : null;

        ArrayList survivors = new ArrayList();
        ArrayList accepts = new ArrayList();
        ArrayList rejects = new ArrayList();

        Predicate randomPredicate = e -> {
            assertNull(threwAt.get());
            switch (rnd.nextInt(3)) {
            case 0: accepts.add(e); return true;
            case 1: rejects.add(e); return false;
            case 2: threwAt.set(e); throw new ArithmeticException();
            default: throw new AssertionError();
            }
        };
        try {
            try {
                boolean modified = c.removeIf(randomPredicate);
                assertNull(threwAt.get());
                assertEquals(modified, accepts.size() > 0);
                assertEquals(modified, rejects.size() != n);
                assertEquals(accepts.size() + rejects.size(), n);
                if (ordered) {
                    assertEquals(rejects,
                                 Arrays.asList(c.toArray()));
                } else {
                    assertEquals(new HashSet(rejects),
                                 new HashSet(Arrays.asList(c.toArray())));
                }
            } catch (ArithmeticException ok) {
                assertNotNull(threwAt.get());
                assertTrue(c.contains(threwAt.get()));
            }
            if (it != null && impl.isConcurrent())
                // check for weakly consistent iterator
                while (it.hasNext()) assertTrue(orig.contains(it.next()));
            switch (rnd.nextInt(4)) {
            case 0: survivors.addAll(c); break;
            case 1: survivors.addAll(Arrays.asList(c.toArray())); break;
            case 2: c.forEach(survivors::add); break;
            case 3: for (Object e : c) survivors.add(e); break;
            }
            assertTrue(orig.containsAll(accepts));
            assertTrue(orig.containsAll(rejects));
            assertTrue(orig.containsAll(survivors));
            assertTrue(orig.containsAll(c));
            assertTrue(c.containsAll(rejects));
            assertTrue(c.containsAll(survivors));
            assertTrue(survivors.containsAll(rejects));
            if (threwAt.get() == null) {
                assertEquals(n - accepts.size(), c.size());
                for (Object x : accepts) assertFalse(c.contains(x));
            } else {
                // Two acceptable behaviors: entire removeIf call is one
                // transaction, or each element processed is one transaction.
                assertTrue(n == c.size() || n == c.size() + accepts.size());
                int k = 0;
                for (Object x : accepts) if (c.contains(x)) k++;
                assertTrue(k == accepts.size() || k == 0);
            }
        } catch (Throwable ex) {
            System.err.println(impl.klazz());
            // c is at risk of corruption if we got here, so be lenient
            try { System.err.printf("c=%s%n", c); }
            catch (Throwable t) { t.printStackTrace(); }
            System.err.printf("n=%d%n", n);
            System.err.printf("orig=%s%n", orig);
            System.err.printf("accepts=%s%n", accepts);
            System.err.printf("rejects=%s%n", rejects);
            System.err.printf("survivors=%s%n", survivors);
            System.err.printf("threwAt=%s%n", threwAt.get());
            throw ex;
        }
    }

    /**
     * Various ways of traversing a collection yield same elements
     */
    public void testIteratorEquivalence() {
        Collection c = impl.emptyCollection();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = rnd.nextInt(6);
        for (int i = 0; i < n; i++) c.add(impl.makeElement(i));
        ArrayList iterated = new ArrayList();
        ArrayList iteratedForEachRemaining = new ArrayList();
        ArrayList tryAdvanced = new ArrayList();
        ArrayList spliterated = new ArrayList();
        ArrayList splitonced = new ArrayList();
        ArrayList forEached = new ArrayList();
        ArrayList streamForEached = new ArrayList();
        ConcurrentLinkedQueue parallelStreamForEached = new ConcurrentLinkedQueue();
        ArrayList removeIfed = new ArrayList();
        for (Object x : c) iterated.add(x);
        c.iterator().forEachRemaining(iteratedForEachRemaining::add);
        for (Spliterator s = c.spliterator();
             s.tryAdvance(tryAdvanced::add); ) {}
        c.spliterator().forEachRemaining(spliterated::add);
        {                       // trySplit returns "strict prefix"
            Spliterator s1 = c.spliterator(), s2 = s1.trySplit();
            if (s2 != null) s2.forEachRemaining(splitonced::add);
            s1.forEachRemaining(splitonced::add);
        }
        c.forEach(forEached::add);
        c.stream().forEach(streamForEached::add);
        c.parallelStream().forEach(parallelStreamForEached::add);
        c.removeIf(e -> { removeIfed.add(e); return false; });
        boolean ordered =
            c.spliterator().hasCharacteristics(Spliterator.ORDERED);
        if (c instanceof List || c instanceof Deque)
            assertTrue(ordered);
        HashSet cset = new HashSet(c);
        assertEquals(cset, new HashSet(parallelStreamForEached));
        if (ordered) {
            assertEquals(iterated, iteratedForEachRemaining);
            assertEquals(iterated, tryAdvanced);
            assertEquals(iterated, spliterated);
            assertEquals(iterated, splitonced);
            assertEquals(iterated, forEached);
            assertEquals(iterated, streamForEached);
            assertEquals(iterated, removeIfed);
        } else {
            assertEquals(cset, new HashSet(iterated));
            assertEquals(cset, new HashSet(iteratedForEachRemaining));
            assertEquals(cset, new HashSet(tryAdvanced));
            assertEquals(cset, new HashSet(spliterated));
            assertEquals(cset, new HashSet(splitonced));
            assertEquals(cset, new HashSet(forEached));
            assertEquals(cset, new HashSet(streamForEached));
            assertEquals(cset, new HashSet(removeIfed));
        }
        if (c instanceof Deque) {
            Deque d = (Deque) c;
            ArrayList descending = new ArrayList();
            ArrayList descendingForEachRemaining = new ArrayList();
            for (Iterator it = d.descendingIterator(); it.hasNext(); )
                descending.add(it.next());
            d.descendingIterator().forEachRemaining(
                e -> descendingForEachRemaining.add(e));
            Collections.reverse(descending);
            Collections.reverse(descendingForEachRemaining);
            assertEquals(iterated, descending);
            assertEquals(iterated, descendingForEachRemaining);
        }
    }

    /**
     * Calling Iterator#remove() after Iterator#forEachRemaining
     * should (maybe) remove last element
     */
    public void testRemoveAfterForEachRemaining() {
        Collection c = impl.emptyCollection();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        testCollection: {
            int n = 3 + rnd.nextInt(2);
            for (int i = 0; i < n; i++) c.add(impl.makeElement(i));
            Iterator it = c.iterator();
            assertTrue(it.hasNext());
            assertEquals(impl.makeElement(0), it.next());
            assertTrue(it.hasNext());
            assertEquals(impl.makeElement(1), it.next());
            it.forEachRemaining(e -> assertTrue(c.contains(e)));
            if (testImplementationDetails) {
                if (c instanceof java.util.concurrent.ArrayBlockingQueue) {
                    assertIteratorExhausted(it);
                } else {
                    try { it.remove(); }
                    catch (UnsupportedOperationException ok) {
                        break testCollection;
                    }
                    assertEquals(n - 1, c.size());
                    for (int i = 0; i < n - 1; i++)
                        assertTrue(c.contains(impl.makeElement(i)));
                    assertFalse(c.contains(impl.makeElement(n - 1)));
                }
            }
        }
        if (c instanceof Deque) {
            Deque d = (Deque) impl.emptyCollection();
            int n = 3 + rnd.nextInt(2);
            for (int i = 0; i < n; i++) d.add(impl.makeElement(i));
            Iterator it = d.descendingIterator();
            assertTrue(it.hasNext());
            assertEquals(impl.makeElement(n - 1), it.next());
            assertTrue(it.hasNext());
            assertEquals(impl.makeElement(n - 2), it.next());
            it.forEachRemaining(e -> assertTrue(c.contains(e)));
            if (testImplementationDetails) {
                it.remove();
                assertEquals(n - 1, d.size());
                for (int i = 1; i < n; i++)
                    assertTrue(d.contains(impl.makeElement(i)));
                assertFalse(d.contains(impl.makeElement(0)));
            }
        }
    }

    /**
     * stream().forEach returns elements in the collection
     */
    public void testStreamForEach() throws Throwable {
        final Collection c = impl.emptyCollection();
        final AtomicLong count = new AtomicLong(0L);
        final Object x = impl.makeElement(1);
        final Object y = impl.makeElement(2);
        final ArrayList found = new ArrayList();
        Consumer<Object> spy = o -> found.add(o);
        c.stream().forEach(spy);
        assertTrue(found.isEmpty());

        assertTrue(c.add(x));
        c.stream().forEach(spy);
        assertEquals(Collections.singletonList(x), found);
        found.clear();

        assertTrue(c.add(y));
        c.stream().forEach(spy);
        assertEquals(2, found.size());
        assertTrue(found.contains(x));
        assertTrue(found.contains(y));
        found.clear();

        c.clear();
        c.stream().forEach(spy);
        assertTrue(found.isEmpty());
    }

    public void testStreamForEachConcurrentStressTest() throws Throwable {
        if (!impl.isConcurrent()) return;
        final Collection c = impl.emptyCollection();
        final long testDurationMillis = timeoutMillis();
        final AtomicBoolean done = new AtomicBoolean(false);
        final Object elt = impl.makeElement(1);
        final Future<?> f1, f2;
        final ExecutorService pool = Executors.newCachedThreadPool();
        try (PoolCleaner cleaner = cleaner(pool, done)) {
            final CountDownLatch threadsStarted = new CountDownLatch(2);
            Runnable checkElt = () -> {
                threadsStarted.countDown();
                while (!done.get())
                    c.stream().forEach(x -> assertSame(x, elt)); };
            Runnable addRemove = () -> {
                threadsStarted.countDown();
                while (!done.get()) {
                    assertTrue(c.add(elt));
                    assertTrue(c.remove(elt));
                }};
            f1 = pool.submit(checkElt);
            f2 = pool.submit(addRemove);
            Thread.sleep(testDurationMillis);
        }
        assertNull(f1.get(0L, MILLISECONDS));
        assertNull(f2.get(0L, MILLISECONDS));
    }

    /**
     * collection.forEach returns elements in the collection
     */
    public void testForEach() throws Throwable {
        final Collection c = impl.emptyCollection();
        final AtomicLong count = new AtomicLong(0L);
        final Object x = impl.makeElement(1);
        final Object y = impl.makeElement(2);
        final ArrayList found = new ArrayList();
        Consumer<Object> spy = o -> found.add(o);
        c.forEach(spy);
        assertTrue(found.isEmpty());

        assertTrue(c.add(x));
        c.forEach(spy);
        assertEquals(Collections.singletonList(x), found);
        found.clear();

        assertTrue(c.add(y));
        c.forEach(spy);
        assertEquals(2, found.size());
        assertTrue(found.contains(x));
        assertTrue(found.contains(y));
        found.clear();

        c.clear();
        c.forEach(spy);
        assertTrue(found.isEmpty());
    }

    /**
     * Motley crew of threads concurrently randomly hammer the collection.
     */
    public void testDetectRaces() throws Throwable {
        if (!impl.isConcurrent()) return;
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final Collection c = impl.emptyCollection();
        final long testDurationMillis
            = expensiveTests ? LONG_DELAY_MS : timeoutMillis();
        final AtomicBoolean done = new AtomicBoolean(false);
        final Object one = impl.makeElement(1);
        final Object two = impl.makeElement(2);
        final Consumer checkSanity = x -> assertTrue(x == one || x == two);
        final Consumer<Object[]> checkArraySanity = array -> {
            // assertTrue(array.length <= 2); // duplicates are permitted
            for (Object x : array) assertTrue(x == one || x == two);
        };
        final Object[] emptyArray =
            (Object[]) java.lang.reflect.Array.newInstance(one.getClass(), 0);
        final List<Future<?>> futures;
        final Phaser threadsStarted = new Phaser(1); // register this thread
        final Runnable[] frobbers = {
            () -> c.forEach(checkSanity),
            () -> c.stream().forEach(checkSanity),
            () -> c.parallelStream().forEach(checkSanity),
            () -> c.spliterator().trySplit(),
            () -> {
                Spliterator s = c.spliterator();
                s.tryAdvance(checkSanity);
                s.trySplit();
            },
            () -> {
                Spliterator s = c.spliterator();
                do {} while (s.tryAdvance(checkSanity));
            },
            () -> { for (Object x : c) checkSanity.accept(x); },
            () -> checkArraySanity.accept(c.toArray()),
            () -> checkArraySanity.accept(c.toArray(emptyArray)),
            () -> {
                assertTrue(c.add(one));
                assertTrue(c.contains(one));
                assertTrue(c.remove(one));
                assertFalse(c.contains(one));
            },
            () -> {
                assertTrue(c.add(two));
                assertTrue(c.contains(two));
                assertTrue(c.remove(two));
                assertFalse(c.contains(two));
            },
        };
        final List<Runnable> tasks =
            Arrays.stream(frobbers)
            .filter(task -> rnd.nextBoolean()) // random subset
            .map(task -> (Runnable) () -> {
                     threadsStarted.arriveAndAwaitAdvance();
                     while (!done.get())
                         task.run();
                 })
            .collect(Collectors.toList());
        final ExecutorService pool = Executors.newCachedThreadPool();
        try (PoolCleaner cleaner = cleaner(pool, done)) {
            threadsStarted.bulkRegister(tasks.size());
            futures = tasks.stream()
                .map(pool::submit)
                .collect(Collectors.toList());
            threadsStarted.arriveAndDeregister();
            Thread.sleep(testDurationMillis);
        }
        for (Future future : futures)
            assertNull(future.get(0L, MILLISECONDS));
    }

    /**
     * Spliterators are either IMMUTABLE or truly late-binding or, if
     * concurrent, use the same "late-binding style" of returning
     * elements added between creation and first use.
     */
    public void testLateBindingStyle() {
        if (!testImplementationDetails) return;
        if (impl.klazz() == ArrayList.class) return; // for jdk8
        // Immutable (snapshot) spliterators are exempt
        if (impl.emptyCollection().spliterator()
            .hasCharacteristics(Spliterator.IMMUTABLE))
            return;
        final Object one = impl.makeElement(1);
        {
            final Collection c = impl.emptyCollection();
            final Spliterator split = c.spliterator();
            c.add(one);
            assertTrue(split.tryAdvance(e -> { assertSame(e, one); }));
            assertFalse(split.tryAdvance(e -> { throw new AssertionError(); }));
            assertTrue(c.contains(one));
        }
        {
            final AtomicLong count = new AtomicLong(0);
            final Collection c = impl.emptyCollection();
            final Spliterator split = c.spliterator();
            c.add(one);
            split.forEachRemaining(
                e -> { assertSame(e, one); count.getAndIncrement(); });
            assertEquals(1L, count.get());
            assertFalse(split.tryAdvance(e -> { throw new AssertionError(); }));
            assertTrue(c.contains(one));
        }
    }

//     public void testCollection8DebugFail() {
//         fail(impl.klazz().getSimpleName());
//     }
}
