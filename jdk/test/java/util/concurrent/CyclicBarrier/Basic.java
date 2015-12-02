/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6253848 6366811
 * @summary Basic tests for CyclicBarrier
 * @author Martin Buchholz, David Holmes
 */

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class Basic {

    private static void checkBroken(final CyclicBarrier barrier) {
        check(barrier.isBroken());
        equal(barrier.getNumberWaiting(), 0);

        THROWS(BrokenBarrierException.class,
               () -> barrier.await(),
               () -> barrier.await(100, MILLISECONDS));
    }

    private static void reset(CyclicBarrier barrier) {
        barrier.reset();
        check(! barrier.isBroken());
        equal(barrier.getNumberWaiting(), 0);
    }

    private static void checkResult(Awaiter a, Class<? extends Throwable> c) {
        Throwable t = a.result();
        if (! ((t == null && c == null) || (c != null && c.isInstance(t)))) {
            //      t.printStackTrace();
            fail("Mismatch in thread " +
                 a.getName() + ": " +
                 t + ", " +
                 (c == null ? "<null>" : c.getName()));
        } else {
            pass();
        }
    }

    //----------------------------------------------------------------
    // Mechanism to get all victim threads into "running" mode.
    // The fact that this also uses CyclicBarrier is entirely coincidental.
    //----------------------------------------------------------------
    private static final CyclicBarrier atTheStartingGate = new CyclicBarrier(3);

    private static void toTheStartingGate() {
        try { atTheStartingGate.await(10, SECONDS); pass(); }
        catch (Throwable t) {
            unexpected(t);
            reset(atTheStartingGate);
            throw new Error(t);
        }
    }

    //----------------------------------------------------------------
    // Convenience methods for creating threads that call CyclicBarrier.await
    //----------------------------------------------------------------
    private abstract static class Awaiter extends Thread {
        static AtomicInteger count = new AtomicInteger(1);

        {
            this.setName("Awaiter:"+count.getAndIncrement());
            this.setDaemon(true);
        }

        private volatile Throwable result = null;
        protected void result(Throwable result) { this.result = result; }
        public Throwable result() { return this.result; }
    }

    private static Awaiter awaiter(final CyclicBarrier barrier) {
        return new Awaiter() { public void run() {
            toTheStartingGate();

            try { barrier.await(); }
            catch (Throwable result) { result(result); }}};
    }

    private static Awaiter awaiter(final CyclicBarrier barrier,
                                   final long millis) {
        return new Awaiter() { public void run() {
            toTheStartingGate();

            try { barrier.await(millis, MILLISECONDS); }
            catch (Throwable result) { result(result); }}};
    }

    // Returns an infinite lazy list of all possible awaiter pair combinations.
    private static Iterator<Awaiter> awaiterIterator(final CyclicBarrier barrier) {
        return new Iterator<Awaiter>() {
            int i = 0;
            public boolean hasNext() { return true; }
            public Awaiter next() {
                switch ((i++)&7) {
                case 0: case 2: case 4: case 5:
                    return awaiter(barrier);
                default:
                    return awaiter(barrier, 10 * 1000); }}
            public void remove() {throw new UnsupportedOperationException();}};
    }

    private static void realMain(String[] args) throws Throwable {

        Thread.currentThread().setName("mainThread");

        //----------------------------------------------------------------
        // Normal use
        //----------------------------------------------------------------
        try {
            CyclicBarrier barrier = new CyclicBarrier(3);
            equal(barrier.getParties(), 3);
            Iterator<Awaiter> awaiters = awaiterIterator(barrier);
            for (boolean doReset : new boolean[] {false, true})
                for (int i = 0; i < 4; i++) {
                    Awaiter a1 = awaiters.next(); a1.start();
                    Awaiter a2 = awaiters.next(); a2.start();
                    toTheStartingGate();
                    barrier.await();
                    a1.join();
                    a2.join();
                    checkResult(a1, null);
                    checkResult(a2, null);
                    check(! barrier.isBroken());
                    equal(barrier.getParties(), 3);
                    equal(barrier.getNumberWaiting(), 0);
                    if (doReset) reset(barrier);
                }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // One thread interrupted
        //----------------------------------------------------------------
        try {
            CyclicBarrier barrier = new CyclicBarrier(3);
            Iterator<Awaiter> awaiters = awaiterIterator(barrier);
            for (int i = 0; i < 4; i++) {
                Awaiter a1 = awaiters.next(); a1.start();
                Awaiter a2 = awaiters.next(); a2.start();
                toTheStartingGate();
                a1.interrupt();
                a1.join();
                a2.join();
                checkResult(a1, InterruptedException.class);
                checkResult(a2, BrokenBarrierException.class);
                checkBroken(barrier);
                reset(barrier);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Barrier is reset while threads are waiting
        //----------------------------------------------------------------
        try {
            CyclicBarrier barrier = new CyclicBarrier(3);
            Iterator<Awaiter> awaiters = awaiterIterator(barrier);
            for (int i = 0; i < 4; i++) {
                Awaiter a1 = awaiters.next(); a1.start();
                Awaiter a2 = awaiters.next(); a2.start();
                toTheStartingGate();
                while (barrier.getNumberWaiting() < 2) Thread.yield();
                barrier.reset();
                a1.join();
                a2.join();
                checkResult(a1, BrokenBarrierException.class);
                checkResult(a2, BrokenBarrierException.class);
                check(! barrier.isBroken());
                equal(barrier.getParties(), 3);
                equal(barrier.getNumberWaiting(), 0);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // One thread timed out
        //----------------------------------------------------------------
        try {
            CyclicBarrier barrier = new CyclicBarrier(3);
            Iterator<Awaiter> awaiters = awaiterIterator(barrier);
            for (long timeout : new long[] { 0L, 10L }) {
                for (int i = 0; i < 2; i++) {
                    Awaiter a1 = awaiter(barrier, timeout); a1.start();
                    Awaiter a2 = awaiters.next();           a2.start();
                    toTheStartingGate();
                    a1.join();
                    a2.join();
                    checkResult(a1, TimeoutException.class);
                    checkResult(a2, BrokenBarrierException.class);
                    checkBroken(barrier);
                    equal(barrier.getParties(), 3);
                    reset(barrier);
                }
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Barrier action completed normally
        //----------------------------------------------------------------
        try {
            final AtomicInteger count = new AtomicInteger(0);
            final CyclicBarrier[] kludge = new CyclicBarrier[1];
            Runnable action = new Runnable() { public void run() {
                count.incrementAndGet();
                equal(kludge[0].getNumberWaiting(),
                      kludge[0].getParties());
                System.out.println("OK!"); }};
            CyclicBarrier barrier = new CyclicBarrier(3, action);
            kludge[0] = barrier;
            equal(barrier.getParties(), 3);
            Iterator<Awaiter> awaiters = awaiterIterator(barrier);
            for (int i = 0; i < 4; i++) {
                Awaiter a1 = awaiters.next(); a1.start();
                Awaiter a2 = awaiters.next(); a2.start();
                toTheStartingGate();
                while (barrier.getNumberWaiting() < 2) Thread.yield();
                try { barrier.await(); }
                catch (Throwable t) { unexpected(t); }
                a1.join();
                a2.join();
                checkResult(a1, null);
                checkResult(a2, null);
                check(! barrier.isBroken());
                equal(barrier.getNumberWaiting(), 0);
                reset(barrier);
                equal(count.get(), i+1);
            }
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Barrier action threw exception
        //----------------------------------------------------------------
        try {
            Runnable action = new Runnable() {
                    public void run() { throw new Error(); }};
            CyclicBarrier barrier = new CyclicBarrier(3, action);
            Iterator<Awaiter> awaiters = awaiterIterator(barrier);
            for (int i = 0; i < 4; i++) {
                Awaiter a1 = awaiters.next(); a1.start();
                Awaiter a2 = awaiters.next(); a2.start();
                toTheStartingGate();
                while (barrier.getNumberWaiting() < 2) Thread.yield();
                try {
                    barrier.await();
                    fail("Expected Error not thrown"); }
                catch (Error e) { pass(); }
                catch (Throwable t) { unexpected(t); }
                a1.join();
                a2.join();
                checkResult(a1, BrokenBarrierException.class);
                checkResult(a2, BrokenBarrierException.class);
                checkBroken(barrier);
                reset(barrier);
            }
        } catch (Throwable t) { unexpected(t); }

        testInterrupts();
    }

    /**
     * Handling of extra interrupts while waiting - tests for bug 6366811
     */
    private static void testInterrupts() {
        final int N = 10;
        final CyclicBarrier startingGate = new CyclicBarrier(N+1);

        /**
         * A version of Awaiter that also records interrupted state.
         */
        class Waiter extends CheckedThread {
            private boolean timed;
            private CyclicBarrier barrier;
            private CountDownLatch doneSignal;
            private Throwable throwable;
            private boolean interrupted;

            public Waiter(boolean timed,
                          CountDownLatch doneSignal,
                          CyclicBarrier barrier) {
                this.timed = timed;
                this.doneSignal = doneSignal;
                this.barrier = barrier;
            }
            Throwable throwable() { return this.throwable; }
            boolean interruptBit() { return this.interrupted; }
            void realRun() throws Throwable {
                startingGate.await(10, SECONDS);
                try {
                    if (timed) barrier.await(10, SECONDS);
                    else barrier.await(); }
                catch (Throwable throwable) { this.throwable = throwable; }

                try { doneSignal.await(10, SECONDS); }
                catch (InterruptedException e) { interrupted = true; }
            }
        }

        //----------------------------------------------------------------
        // Interrupt occurs during barrier trip
        //----------------------------------------------------------------
        try {
            final CountDownLatch doneSignal = new CountDownLatch(1);
            final List<Waiter> waiters = new ArrayList<Waiter>(N);

            // work around finality of closed-over variables
            final Runnable[] realAction = new Runnable[1];
            final Runnable delegateAction =
                new Runnable() {public void run() {realAction[0].run();}};
            final CyclicBarrier barrier = new CyclicBarrier(N+1, delegateAction);

            realAction[0] = new Runnable() { public void run() {
                try {
                    for (int i = 0; i < N/2; i++)
                        waiters.get(i).interrupt();
                    // we need to try and ensure that the waiters get
                    // to process their interruption before we do the
                    // signalAll that trips the barrier. Using sleep
                    // seems to work reliably while yield does not.
                    Thread.sleep(100);
                } catch (Throwable t) { unexpected(t); }
            }};
            for (int i = 0; i < N; i++) {
                Waiter waiter = new Waiter(i < N/2, doneSignal, barrier);
                waiter.start();
                waiters.add(waiter);
            }
            startingGate.await(10, SECONDS);
            while (barrier.getNumberWaiting() < N) Thread.yield();
            barrier.await();
            doneSignal.countDown();
            int countInterrupted = 0;
            int countInterruptedException = 0;
            int countBrokenBarrierException = 0;
            for (Waiter waiter : waiters) {
                waiter.join();
                equal(waiter.throwable(), null);
                if (waiter.interruptBit())
                    countInterrupted++;
            }
            equal(countInterrupted, N/2);
            check(! barrier.isBroken());
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Multiple interrupts occur during barrier await
        //----------------------------------------------------------------
        try {
            final CountDownLatch doneSignal = new CountDownLatch(1);
            final CyclicBarrier barrier = new CyclicBarrier(N+1);
            final List<Waiter> waiters = new ArrayList<Waiter>(N);
            for (int i = 0; i < N; i++) {
                Waiter waiter = new Waiter(i < N/2, doneSignal, barrier);
                waiter.start();
                waiters.add(waiter);
            }
            startingGate.await(10, SECONDS);
            while (barrier.getNumberWaiting() < N) Thread.yield();
            for (int i = 0; i < N/2; i++)
                waiters.get(i).interrupt();
            doneSignal.countDown();
            int countInterrupted = 0;
            int countInterruptedException = 0;
            int countBrokenBarrierException = 0;
            for (Waiter waiter : waiters) {
                waiter.join();
                if (waiter.throwable() instanceof InterruptedException)
                    countInterruptedException++;
                if (waiter.throwable() instanceof BrokenBarrierException)
                    countBrokenBarrierException++;
                if (waiter.interruptBit())
                    countInterrupted++;
            }
            equal(countInterrupted, N/2-1);
            equal(countInterruptedException, 1);
            equal(countBrokenBarrierException, N-1);
            checkBroken(barrier);
            reset(barrier);
        } catch (Throwable t) { unexpected(t); }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
    interface Fun {void f() throws Throwable;}
    private static void THROWS(Class<? extends Throwable> k, Fun... fs) {
        for (Fun f : fs)
            try { f.f(); fail("Expected " + k.getName() + " not thrown"); }
            catch (Throwable t) {
                if (k.isAssignableFrom(t.getClass())) pass();
                else unexpected(t);}}
    private abstract static class CheckedThread extends Thread {
        abstract void realRun() throws Throwable;
        public void run() {
            try {realRun();} catch (Throwable t) {unexpected(t);}}}
}
