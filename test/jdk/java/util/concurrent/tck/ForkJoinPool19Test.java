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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.HashSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Callable;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Tests for ForkJoinPool and corresponding ForkJoinTask additions.
 */
public class ForkJoinPool19Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(ForkJoinPool19Test.class);
    }

    /**
     * SetParallelism sets reported parallellism and returns previous value
     */
    public void testSetParallelism() {
        final ForkJoinPool p = new ForkJoinPool(2);
        assertEquals(2, p.getParallelism());
        assertEquals(2, p.setParallelism(3));
        assertEquals(3, p.setParallelism(2));
        p.shutdown();
    }
    /**
     * SetParallelism throws exception if argument out of bounds
     */
    public void testSetParallelismBadArgs() {
        final ForkJoinPool p = new ForkJoinPool(2);
        try {
            p.setParallelism(0);
            shouldThrow();
        } catch (Exception success) {
        }
        try {
            p.setParallelism(Integer.MAX_VALUE);
            shouldThrow();
        } catch (Exception success) {
        }
        assertEquals(2, p.getParallelism());
        p.shutdown();
    }


    /*
     * Some test methods adapted from RecursiveAction
     */
    private static ForkJoinPool mainPool() {
        return new ForkJoinPool();
    }

    private void testInvokeOnPool(ForkJoinPool pool, RecursiveAction a) {
        try (PoolCleaner cleaner = cleaner(pool)) {
            checkNotDone(a);
            assertNull(pool.invoke(a));
            checkCompletedNormally(a);
        }
    }

    private void checkInvoke(ForkJoinTask<?> a) {
        checkNotDone(a);
        assertNull(a.invoke());
        checkCompletedNormally(a);
    }

    void checkNotDone(ForkJoinTask<?> a) {
        assertFalse(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());

        if (! ForkJoinTask.inForkJoinPool()) {
            Thread.currentThread().interrupt();
            try {
                a.get();
                shouldThrow();
            } catch (InterruptedException success) {
            } catch (Throwable fail) { threadUnexpectedException(fail); }

            Thread.currentThread().interrupt();
            try {
                a.get(randomTimeout(), randomTimeUnit());
                shouldThrow();
            } catch (InterruptedException success) {
            } catch (Throwable fail) { threadUnexpectedException(fail); }
        }

        try {
            a.get(randomExpiredTimeout(), randomTimeUnit());
            shouldThrow();
        } catch (TimeoutException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedNormally(ForkJoinTask<?> a) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertTrue(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertNull(a.getException());
        assertNull(a.getRawResult());
        assertNull(a.join());
        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));

        Object v1 = null, v2 = null;
        try {
            v1 = a.get();
            v2 = a.get(randomTimeout(), randomTimeUnit());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertNull(v1);
        assertNull(v2);
    }

    void checkCancelled(ForkJoinTask<?> a) {
        assertTrue(a.isDone());
        assertTrue(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertTrue(a.getException() instanceof CancellationException);
        assertNull(a.getRawResult());

        try {
            a.join();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(randomTimeout(), randomTimeUnit());
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedAbnormally(ForkJoinTask<?> a, Throwable t) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertSame(t.getClass(), a.getException().getClass());
        assertNull(a.getRawResult());
        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));

        try {
            a.join();
            shouldThrow();
        } catch (Throwable expected) {
            assertSame(expected.getClass(), t.getClass());
        }

        try {
            a.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(randomTimeout(), randomTimeUnit());
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    public static final class FJException extends RuntimeException {
        public FJException() { super(); }
        public FJException(Throwable cause) { super(cause); }
    }

    /** A simple recursive action for testing. */
    final class FibAction extends CheckedRecursiveAction {
        final int number;
        int result;
        FibAction(int n) { number = n; }
        protected void realCompute() {
            int n = number;
            if (n <= 1)
                result = n;
            else {
                FibAction f1 = new FibAction(n - 1);
                FibAction f2 = new FibAction(n - 2);
                invokeAll(f1, f2);
                result = f1.result + f2.result;
            }
        }
    }

    /** A recursive action failing in base case. */
    static final class FailingFibAction extends RecursiveAction {
        final int number;
        int result;
        FailingFibAction(int n) { number = n; }
        public void compute() {
            int n = number;
            if (n > 1) {
                try {
                    FailingFibAction f1 = new FailingFibAction(n - 1);
                    FailingFibAction f2 = new FailingFibAction(n - 2);
                    invokeAll(f1, f2);
                    result = f1.result + f2.result;
                    return;
                } catch (CancellationException fallthrough) {
                }
            }
            throw new FJException();
        }
    }

    /**
     * lazySubmit submits a task that is not executed until new
     * workers are created or it is explicitly joined by a worker.
     */
    @SuppressWarnings("removal")
    public void testLazySubmit() {
        ForkJoinPool p;
        try {
            p = new ForkJoinPool();
        } catch (java.security.AccessControlException e) {
            return;
        }
        FibAction f = new FibAction(8);
        RecursiveAction j = new RecursiveAction() {
                protected void compute() {
                    f.join();
                }};
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                p.invoke(new FibAction(8));
                p.lazySubmit(f);
                p.invoke(new FibAction(8));
                p.invoke(j);
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(p, a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                f.quietlyInvoke();
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        checkInvoke(a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        checkInvoke(a);
    }

    /**
     * timed quietlyJoinUninterruptibly of a forked task succeeds in
     * the presence of interrupts
     */
    public void testTimedQuietlyJoinUninterruptiblyInterrupts() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f;
                final Thread currentThread = Thread.currentThread();

                // test quietlyJoin()
                f = new FibAction(8);
                assertSame(f, f.fork());
                currentThread.interrupt();
                f.quietlyJoinUninterruptibly(LONG_DELAY_MS, MILLISECONDS);
                Thread.interrupted();
                assertEquals(21, f.result);
                checkCompletedNormally(f);

                f = new FibAction(8);
                f.cancel(true);
                assertSame(f, f.fork());
                currentThread.interrupt();
                f.quietlyJoinUninterruptibly(LONG_DELAY_MS, MILLISECONDS);
                Thread.interrupted();
                checkCancelled(f);

                f = new FibAction(8);
                f.completeExceptionally(new FJException());
                assertSame(f, f.fork());
                currentThread.interrupt();
                f.quietlyJoinUninterruptibly(LONG_DELAY_MS, MILLISECONDS);
                Thread.interrupted();
                checkCompletedAbnormally(f, f.getException());
            }};
        checkInvoke(a);
        a.reinitialize();
        checkInvoke(a);
    }

    /**
     * timed quietlyJoin throws IE in the presence of interrupts
     */
    public void testTimedQuietlyJoinInterrupts() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f;
                final Thread currentThread = Thread.currentThread();

                f = new FibAction(8);
                assertSame(f, f.fork());
                currentThread.interrupt();
                try {
                    f.quietlyJoin(LONG_DELAY_MS, MILLISECONDS);
                } catch (InterruptedException success) {
                }
                Thread.interrupted();
                f.quietlyJoin();

                f = new FibAction(8);
                f.cancel(true);
                assertSame(f, f.fork());
                currentThread.interrupt();
                try {
                    f.quietlyJoin(LONG_DELAY_MS, MILLISECONDS);
                } catch (InterruptedException success) {
                }
                f.quietlyJoin();
                checkCancelled(f);
                Thread.interrupted();
            }};
        checkInvoke(a);
        a.reinitialize();
        checkInvoke(a);
    }

    /**
     * timed quietlyJoin of a forked task returns when task completes
     */
    public void testForkTimedQuietlyJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertTrue(f.quietlyJoin(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        checkInvoke(a);
    }

    /**
     * timed quietlyJoin with null time unit throws NPE
     */
    public void testForkTimedQuietlyJoinNPE() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                try {
                    f.quietlyJoin(randomTimeout(), null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        checkInvoke(a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalTimedQuietlyJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                assertTrue(f.quietlyJoin(LONG_DELAY_MS, MILLISECONDS));
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        checkInvoke(a);
    }

    /**
     * timed quietlyJoinUninterruptibly of a forked task returns when task completes
     */
    public void testForkTimedQuietlyJoinUninterruptibly() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertTrue(f.quietlyJoinUninterruptibly(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        checkInvoke(a);
    }

    /**
     * timed quietlyJoinUninterruptibly with null time unit throws NPE
     */
    public void testForkTimedQuietlyJoinUninterruptiblyNPE() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                try {
                    f.quietlyJoinUninterruptibly(randomTimeout(), null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        checkInvoke(a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalTimedQuietlyJoinUninterruptibly() {
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                assertTrue(f.quietlyJoinUninterruptibly(LONG_DELAY_MS, MILLISECONDS));
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        checkInvoke(a);
    }

    /**
     * adaptInterruptible(callable).toString() contains toString of wrapped task
     */
    public void testAdaptInterruptible_Callable_toString() {
        if (testImplementationDetails) {
            Callable<String> c = () -> "";
            ForkJoinTask<String> task = ForkJoinTask.adaptInterruptible(c);
            assertEquals(
                identityString(task) + "[Wrapped task = " + c.toString() + "]",
                task.toString());
        }
    }

    /**
     * Implicitly closing a new pool using try-with-resources terminates it
     */
    public void testClose() {
        Thread t = newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    FibAction f = new FibAction(1);
                    ForkJoinPool pool = null;
                    try (ForkJoinPool p = new ForkJoinPool()) {
                        pool = p;
                        p.execute(f);
                    }
                    assertTrue(pool != null && pool.isTerminated());
                    f.join();
                    assertEquals(1, f.result);
                }});
        awaitTermination(t);
    }

    /**
     * Explicitly closing a new pool terminates it
     */
    public void testClose2() {
        Thread t = newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    ForkJoinPool pool = new ForkJoinPool();
                    FibAction f = new FibAction(1);
                    pool.execute(f);
                    pool.close();
                    assertTrue(pool.isTerminated());
                    f.join();
                    assertEquals(1, f.result);
                }});
        awaitTermination(t);
    }

    /**
     * Explicitly closing a shutdown pool awaits termination
     */
    public void testClose3() {
        Thread t = newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    ForkJoinPool pool = new ForkJoinPool();
                    FibAction f = new FibAction(1);
                    pool.execute(f);
                    pool.shutdown();
                    pool.close();
                    assertTrue(pool.isTerminated());
                    f.join();
                    assertEquals(1, f.result);
                }});
        awaitTermination(t);
    }

    /**
     * Implicitly closing common pool using try-with-resources has no effect.
     */
    public void testCloseCommonPool() {
        String prop = System.getProperty(
            "java.util.concurrent.ForkJoinPool.common.parallelism");
        boolean nothreads = "0".equals(prop);
        Thread t = newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    ForkJoinTask f = new FibAction(8);
                    ForkJoinPool pool;
                    try (ForkJoinPool p = pool = ForkJoinPool.commonPool()) {
                        p.execute(f);
                    }
                    assertFalse(pool.isShutdown());
                    assertFalse(pool.isTerminating());
                    assertFalse(pool.isTerminated());
                    if (!nothreads) {
                        f.join();
                        checkCompletedNormally(f);
                    }
                }});
       awaitTermination(t);
    }
}
