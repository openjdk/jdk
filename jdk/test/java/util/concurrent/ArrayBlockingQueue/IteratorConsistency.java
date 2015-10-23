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
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

/*
 * @test
 * @bug 7014263
 * @summary White box testing of ArrayBlockingQueue iterators.
 */

/**
 * Highly coupled to the implementation of ArrayBlockingQueue.
 * Uses reflection to inspect queue and iterator state.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class IteratorConsistency {
    final Random rnd = new Random();
    final int CAPACITY = 20;
    Field itrsField;
    Field itemsField;
    Field takeIndexField;
    Field headField;
    Field nextField;
    Field prevTakeIndexField;

    void test(String[] args) throws Throwable {
        itrsField = ArrayBlockingQueue.class.getDeclaredField("itrs");
        itemsField = ArrayBlockingQueue.class.getDeclaredField("items");
        takeIndexField = ArrayBlockingQueue.class.getDeclaredField("takeIndex");
        headField = Class.forName("java.util.concurrent.ArrayBlockingQueue$Itrs").getDeclaredField("head");
        nextField = Class.forName("java.util.concurrent.ArrayBlockingQueue$Itrs$Node").getDeclaredField("next");
        prevTakeIndexField = Class.forName("java.util.concurrent.ArrayBlockingQueue$Itr").getDeclaredField("prevTakeIndex");
        itrsField.setAccessible(true);
        itemsField.setAccessible(true);
        takeIndexField.setAccessible(true);
        headField.setAccessible(true);
        nextField.setAccessible(true);
        prevTakeIndexField.setAccessible(true);
        test(CAPACITY, true);
        test(CAPACITY, false);
    }

    Object itrs(ArrayBlockingQueue q) {
        try {
            return itrsField.get(q);
        } catch (Throwable t) { throw new Error(); }
    }

    int takeIndex(ArrayBlockingQueue q) {
        try {
            return takeIndexField.getInt(q);
        } catch (Throwable t) { throw new Error(); }
    }

    List<Iterator> trackedIterators(Object itrs) {
        try {
            List<Iterator> its = new ArrayList<Iterator>();
            if (itrs != null)
                for (Object p = headField.get(itrs); p != null; p = nextField.get(p))
                    its.add(((WeakReference<Iterator>)(p)).get());
            Collections.reverse(its);
            return its;
        } catch (Throwable t) { throw new Error(); }
    }

    List<Iterator> trackedIterators(ArrayBlockingQueue q) {
        return trackedIterators(itrs(q));
    }

    List<Iterator> attachedIterators(Object itrs) {
        try {
            List<Iterator> its = new ArrayList<Iterator>();
            if (itrs != null)
                for (Object p = headField.get(itrs); p != null; p = nextField.get(p)) {
                    Iterator it = ((WeakReference<Iterator>)(p)).get();
                    if (it != null && !isDetached(it))
                        its.add(it);
                }
            Collections.reverse(its);
            return its;
        } catch (Throwable t) { unexpected(t); return null; }
    }

    List<Iterator> attachedIterators(ArrayBlockingQueue q) {
        return attachedIterators(itrs(q));
    }

    Object[] internalArray(ArrayBlockingQueue q) {
        try {
            return (Object[]) itemsField.get(q);
        } catch (Throwable t) { throw new Error(t); }
    }

    void printInternalArray(ArrayBlockingQueue q) {
        System.err.println(Arrays.toString(internalArray(q)));
    }

    void checkExhausted(Iterator it) {
        if (rnd.nextBoolean()) {
            check(!it.hasNext());
            check(isDetached(it));
        }
        if (rnd.nextBoolean())
            try { it.next(); fail("should throw"); }
            catch (NoSuchElementException success) {}
    }

    boolean isDetached(Iterator it) {
        try {
            return prevTakeIndexField.getInt(it) < 0;
        } catch (IllegalAccessException t) { unexpected(t); return false; }
    }

    void checkDetached(Iterator it) {
        check(isDetached(it));
    }

    void removeUsingIterator(ArrayBlockingQueue q, Object element) {
        Iterator it = q.iterator();
        while (it.hasNext()) {
            Object x = it.next();
            if (element.equals(x))
                it.remove();
            checkRemoveThrowsISE(it);
        }
    }

    void checkRemoveThrowsISE(Iterator it) {
        if (rnd.nextBoolean())
            return;
        try { it.remove(); fail("should throw"); }
        catch (IllegalStateException success) {}
    }

    void checkRemoveHasNoEffect(Iterator it, Collection c) {
        if (rnd.nextBoolean())
            return;
        int size = c.size();
        it.remove(); // no effect
        equal(c.size(), size);
        checkRemoveThrowsISE(it);
    }

    void checkIterationSanity(Queue q) {
        if (rnd.nextBoolean())
            return;
        int size = q.size();
        Object[] a = q.toArray();
        Object[] b = new Object[size+2];
        Arrays.fill(b, Boolean.TRUE);
        Object[] c = q.toArray(b);
        equal(a.length, size);
        check(b == c);
        check(b[size] == null);
        check(b[size+1] == Boolean.TRUE);
        equal(q.toString(), Arrays.toString(a));
        Integer[] xx = null, yy = null;
        if (size > 0) {
            xx = new Integer[size - 1];
            Arrays.fill(xx, 42);
            yy = ((Queue<Integer>)q).toArray(xx);
            for (Integer zz : xx)
                equal(42, zz);
        }
        Iterator it = q.iterator();
        for (int i = 0; i < size; i++) {
            check(it.hasNext());
            Object x = it.next();
            check(x == a[i]);
            check(x == b[i]);
            if (xx != null) check(x == yy[i]);
        }
        check(!it.hasNext());
    }

    private static void waitForFinalizersToRun() {
        for (int i = 0; i < 2; i++)
            tryWaitForFinalizersToRun();
    }

    private static void tryWaitForFinalizersToRun() {
        System.gc();
        final CountDownLatch fin = new CountDownLatch(1);
        new Object() { protected void finalize() { fin.countDown(); }};
        System.gc();
        try { fin.await(); }
        catch (InterruptedException ie) { throw new Error(ie); }
    }

    void test(int capacity, boolean fair) {
        //----------------------------------------------------------------
        // q.clear will clear out itrs.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                check(q.add(i));
            check(itrs(q) == null);
            for (int i = 0; i < capacity; i++) {
                its.add(q.iterator());
                equal(trackedIterators(q), its);
                q.poll();
                q.add(capacity+i);
            }
            q.clear();
            check(itrs(q) == null);
            int j = 0;
            for (Iterator it : its) {
                if (rnd.nextBoolean())
                    check(it.hasNext());
                equal(it.next(), j++);
                checkExhausted(it);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // q emptying will clear out itrs.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                q.add(i);
            check(itrs(q) == null);
            for (int i = 0; i < capacity; i++) {
                its.add(q.iterator());
                equal(trackedIterators(q), its);
                q.poll();
                q.add(capacity+i);
            }
            for (int i = 0; i < capacity; i++)
                q.poll();
            check(itrs(q) == null);
            int j = 0;
            for (Iterator it : its) {
                if (rnd.nextBoolean())
                    check(it.hasNext());
                equal(it.next(), j++);
                checkExhausted(it);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Advancing 2 cycles will remove iterators.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                q.add(i);
            check(itrs(q) == null);
            for (int i = capacity; i < 3 * capacity; i++) {
                its.add(q.iterator());
                equal(trackedIterators(q), its);
                q.poll();
                q.add(i);
            }
            for (int i = 3 * capacity; i < 4 * capacity; i++) {
                equal(trackedIterators(q), its.subList(capacity,2*capacity));
                q.poll();
                q.add(i);
            }
            check(itrs(q) == null);
            int j = 0;
            for (Iterator it : its) {
                if (rnd.nextBoolean())
                    check(it.hasNext());
                equal(it.next(), j++);
                checkExhausted(it);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Interior removal of elements used by an iterator will cause
        // it to be untracked.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            q.add(0);
            for (int i = 1; i < 2 * capacity; i++) {
                q.add(i);
                Integer[] elts = { -1, -2, -3 };
                for (Integer elt : elts) q.add(elt);
                equal(q.remove(), i - 1);
                Iterator it = q.iterator();
                equal(it.next(), i);
                equal(it.next(), elts[0]);
                Collections.shuffle(Arrays.asList(elts));
                check(q.remove(elts[0]));
                check(q.remove(elts[1]));
                equal(trackedIterators(q), Collections.singletonList(it));
                check(q.remove(elts[2]));
                check(itrs(q) == null);
                equal(it.next(), -2);
                if (rnd.nextBoolean()) checkExhausted(it);
                if (rnd.nextBoolean()) checkDetached(it);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check iterators on an empty q
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            for (int i = 0; i < 4; i++) {
                Iterator it = q.iterator();
                check(itrs(q) == null);
                if (rnd.nextBoolean()) checkExhausted(it);
                if (rnd.nextBoolean()) checkDetached(it);
                checkRemoveThrowsISE(it);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check "interior" removal of iterator's last element
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                q.add(i);
            for (int i = 0; i < capacity; i++) {
                Iterator it = q.iterator();
                its.add(it);
                for (int j = 0; j < i; j++)
                    equal(j, it.next());
                equal(attachedIterators(q), its);
            }
            q.remove(capacity - 1);
            equal(attachedIterators(q), its);
            for (int i = 1; i < capacity - 1; i++) {
                q.remove(capacity - i - 1);
                Iterator it = its.get(capacity - i);
                checkDetached(it);
                equal(attachedIterators(q), its.subList(0, capacity - i));
                if (rnd.nextBoolean()) check(it.hasNext());
                equal(it.next(), capacity - i);
                checkExhausted(it);
            }
            equal(attachedIterators(q), its.subList(0, 2));
            q.remove(0);
            check(q.isEmpty());
            check(itrs(q) == null);
            Iterator it = its.get(0);
            equal(it.next(), 0);
            checkRemoveHasNoEffect(it, q);
            checkExhausted(it);
            checkDetached(it);
            checkRemoveHasNoEffect(its.get(1), q);
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check "interior" removal of alternating elements, straddling 2 cycles
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            // Move takeIndex to middle
            for (int i = 0; i < capacity/2; i++) {
                check(q.add(i));
                equal(q.poll(), i);
            }
            check(takeIndex(q) == capacity/2);
            for (int i = 0; i < capacity; i++)
                q.add(i);
            for (int i = 0; i < capacity; i++) {
                Iterator it = q.iterator();
                its.add(it);
                for (int j = 0; j < i; j++)
                    equal(j, it.next());
                equal(attachedIterators(q), its);
            }
            // Remove all even elements, in either direction using
            // q.remove(), or iterator.remove()
            switch (rnd.nextInt(3)) {
            case 0:
                for (int i = 0; i < capacity; i+=2) {
                    check(q.remove(i));
                    equal(attachedIterators(q), its);
                }
                break;
            case 1:
                for (int i = capacity - 2; i >= 0; i-=2) {
                    check(q.remove(i));
                    equal(attachedIterators(q), its);
                }
                break;
            case 2:
                Iterator it = q.iterator();
                while (it.hasNext()) {
                    int i = (Integer) it.next();
                    if ((i & 1) == 0)
                        it.remove();
                }
                equal(attachedIterators(q), its);
                break;
            default: throw new Error();
            }

            for (int i = 0; i < capacity; i++) {
                Iterator it = its.get(i);
                boolean even = ((i & 1) == 0);
                if (even) {
                    if (rnd.nextBoolean()) check(it.hasNext());
                    equal(i, it.next());
                    for (int j = i+1; j < capacity; j += 2)
                        equal(j, it.next());
                    check(!isDetached(it));
                    check(!it.hasNext());
                    check(isDetached(it));
                } else { /* odd */
                    if (rnd.nextBoolean()) check(it.hasNext());
                    checkRemoveHasNoEffect(it, q);
                    equal(i, it.next());
                    for (int j = i+2; j < capacity; j += 2)
                        equal(j, it.next());
                    check(!isDetached(it));
                    check(!it.hasNext());
                    check(isDetached(it));
                }
            }
            equal(trackedIterators(q), Collections.emptyList());
            check(itrs(q) == null);
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check garbage collection of discarded iterators
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                q.add(i);
            for (int i = 0; i < capacity; i++) {
                its.add(q.iterator());
                equal(attachedIterators(q), its);
            }
            its = null;
            waitForFinalizersToRun();
            List<Iterator> trackedIterators = trackedIterators(q);
            equal(trackedIterators.size(), capacity);
            for (Iterator x : trackedIterators)
                check(x == null);
            Iterator it = q.iterator();
            equal(trackedIterators(q), Collections.singletonList(it));
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check garbage collection of discarded iterators,
        // with a randomly retained subset.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            List<Iterator> retained = new ArrayList<Iterator>();
            final int size = 1 + rnd.nextInt(capacity);
            for (int i = 0; i < size; i++)
                q.add(i);
            for (int i = 0; i < size; i++) {
                Iterator it = q.iterator();
                its.add(it);
                equal(attachedIterators(q), its);
            }
            // Leave sufficient gaps in retained
            for (int i = 0; i < size; i+= 2+rnd.nextInt(3))
                retained.add(its.get(i));
            its = null;
            waitForFinalizersToRun();
            List<Iterator> trackedIterators = trackedIterators(q);
            equal(trackedIterators.size(), size);
            for (Iterator it : trackedIterators)
                check((it == null) ^ retained.contains(it));
            Iterator it = q.iterator(); // trigger another sweep
            retained.add(it);
            equal(trackedIterators(q), retained);
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check incremental sweeping of discarded iterators.
        // Excessively white box?!
        //----------------------------------------------------------------
        try {
            final int SHORT_SWEEP_PROBES = 4;
            final int LONG_SWEEP_PROBES = 16;
            final int PROBE_HOP = LONG_SWEEP_PROBES + 6 * SHORT_SWEEP_PROBES;
            final int PROBE_HOP_COUNT = 10;
            // Expect around 8 sweeps per PROBE_HOP
            final int SWEEPS_PER_PROBE_HOP = 8;
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                q.add(i);
            for (int i = 0; i < PROBE_HOP_COUNT * PROBE_HOP; i++) {
                its.add(q.iterator());
                equal(attachedIterators(q), its);
            }
            // make some garbage, separated by PROBE_HOP
            for (int i = 0; i < its.size(); i += PROBE_HOP)
                its.set(i, null);
            waitForFinalizersToRun();
            int retries;
            for (retries = 0;
                 trackedIterators(q).contains(null) && retries < 1000;
                 retries++)
                // one round of sweeping
                its.add(q.iterator());
            check(retries >= PROBE_HOP_COUNT * (SWEEPS_PER_PROBE_HOP - 2));
            check(retries <= PROBE_HOP_COUNT * (SWEEPS_PER_PROBE_HOP + 2));
            Iterator itsit = its.iterator();
            while (itsit.hasNext())
                if (itsit.next() == null)
                    itsit.remove();
            equal(trackedIterators(q), its);
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check safety of iterator.remove while in detached mode.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity/2; i++) {
                q.add(i);
                q.remove();
            }
            check(takeIndex(q) == capacity/2);
            for (int i = 0; i < capacity; i++)
                q.add(i);
            for (int i = 0; i < capacity; i++) {
                Iterator it = q.iterator();
                its.add(it);
                for (int j = 0; j < i; j++)
                    equal(j, it.next());
                equal(attachedIterators(q), its);
            }
            for (int i = capacity - 1; i >= 0; i--) {
                Iterator it = its.get(i);
                equal(i, it.next()); // last element
                check(!isDetached(it));
                check(!it.hasNext()); // first hasNext failure
                check(isDetached(it));
                int size = q.size();
                check(q.contains(i));
                switch (rnd.nextInt(3)) {
                case 0:
                    it.remove();
                    check(!q.contains(i));
                    equal(q.size(), size - 1);
                    break;
                case 1:
                    // replace i with impostor
                    if (q.remainingCapacity() == 0) {
                        check(q.remove(i));
                        check(q.add(-1));
                    } else {
                        check(q.add(-1));
                        check(q.remove(i));
                    }
                    it.remove(); // should have no effect
                    equal(size, q.size());
                    check(q.contains(-1));
                    check(q.remove(-1));
                    break;
                case 2:
                    // replace i with true impostor
                    if (i != 0) {
                        check(q.remove(i));
                        check(q.add(i));
                    }
                    it.remove();
                    check(!q.contains(i));
                    equal(q.size(), size - 1);
                    break;
                default: throw new Error();
                }
                checkRemoveThrowsISE(it);
                check(isDetached(it));
                check(!trackedIterators(q).contains(it));
            }
            check(q.isEmpty());
            check(itrs(q) == null);
            for (Iterator it : its)
                checkExhausted(it);
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check dequeues bypassing iterators' current positions.
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            Queue<Iterator> its0
                = new ArrayDeque<Iterator>();
            Queue<Iterator> itsMid
                = new ArrayDeque<Iterator>();
            List<Iterator> its = new ArrayList<Iterator>();
            for (int i = 0; i < capacity; i++)
                q.add(i);
            for (int i = 0; i < 2 * capacity + 1; i++) {
                Iterator it = q.iterator();
                its.add(it);
                its0.add(it);
            }
            for (int i = 0; i < 2 * capacity + 1; i++) {
                Iterator it = q.iterator();
                for (int j = 0; j < capacity/2; j++)
                    equal(j, it.next());
                its.add(it);
                itsMid.add(it);
            }
            for (int i = capacity; i < 3 * capacity; i++) {
                Iterator it;

                it = its0.remove();
                checkRemoveThrowsISE(it);
                if (rnd.nextBoolean()) check(it.hasNext());
                equal(0, it.next());
                int victim = i - capacity;
                for (int j = victim + (victim == 0 ? 1 : 0); j < i; j++) {
                    if (rnd.nextBoolean()) check(it.hasNext());
                    equal(j, it.next());
                }
                checkExhausted(it);

                it = itsMid.remove();
                if (victim >= capacity/2)
                    checkRemoveHasNoEffect(it, q);
                equal(capacity/2, it.next());
                if (victim > capacity/2)
                    checkRemoveHasNoEffect(it, q);
                for (int j = Math.max(victim, capacity/2 + 1); j < i; j++) {
                    if (rnd.nextBoolean()) check(it.hasNext());
                    equal(j, it.next());
                }
                checkExhausted(it);

                if (rnd.nextBoolean()) {
                    equal(victim, q.remove());
                } else {
                    ArrayList list = new ArrayList(1);
                    q.drainTo(list, 1);
                    equal(list.size(), 1);
                    equal(victim, list.get(0));
                }
                check(q.add(i));
            }
            // takeIndex has wrapped twice.
            Iterator it0 = its0.remove();
            Iterator itMid = itsMid.remove();
            check(isDetached(it0));
            check(isDetached(itMid));
            if (rnd.nextBoolean()) check(it0.hasNext());
            if (rnd.nextBoolean()) check(itMid.hasNext());
            checkRemoveThrowsISE(it0);
            checkRemoveHasNoEffect(itMid, q);
            if (rnd.nextBoolean()) equal(0, it0.next());
            if (rnd.nextBoolean()) equal(capacity/2, itMid.next());
            check(isDetached(it0));
            check(isDetached(itMid));
            equal(capacity, q.size());
            equal(0, q.remainingCapacity());
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check collective sanity of iteration, toArray() and toString()
        //----------------------------------------------------------------
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(capacity, fair);
            for (int i = 0; i < capacity; i++) {
                checkIterationSanity(q);
                equal(capacity, q.size() + q.remainingCapacity());
                q.add(i);
            }
            for (int i = 0; i < (capacity + (capacity >> 1)); i++) {
                checkIterationSanity(q);
                equal(capacity, q.size() + q.remainingCapacity());
                equal(i, q.peek());
                equal(i, q.poll());
                checkIterationSanity(q);
                equal(capacity, q.size() + q.remainingCapacity());
                q.add(capacity + i);
            }
            for (int i = 0; i < capacity; i++) {
                checkIterationSanity(q);
                equal(capacity, q.size() + q.remainingCapacity());
                int expected = i + capacity + (capacity >> 1);
                equal(expected, q.peek());
                equal(expected, q.poll());
            }
            checkIterationSanity(q);
        } catch (Throwable t) { unexpected(t); }

    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new IteratorConsistency().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.err.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
