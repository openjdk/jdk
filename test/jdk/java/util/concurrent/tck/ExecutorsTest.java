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

import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ExecutorsTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(ExecutorsTest.class);
    }

    /**
     * A newCachedThreadPool can execute runnables
     */
    public void testNewCachedThreadPool1() {
        final ExecutorService e = Executors.newCachedThreadPool();
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A newCachedThreadPool with given ThreadFactory can execute runnables
     */
    public void testNewCachedThreadPool2() {
        final ExecutorService e = Executors.newCachedThreadPool(new SimpleThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A newCachedThreadPool with null ThreadFactory throws NPE
     */
    public void testNewCachedThreadPool3() {
        try {
            ExecutorService unused = Executors.newCachedThreadPool(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new SingleThreadExecutor can execute runnables
     */
    public void testNewSingleThreadExecutor1() {
        final ExecutorService e = Executors.newSingleThreadExecutor();
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new SingleThreadExecutor with given ThreadFactory can execute runnables
     */
    public void testNewSingleThreadExecutor2() {
        final ExecutorService e = Executors.newSingleThreadExecutor(new SimpleThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new SingleThreadExecutor with null ThreadFactory throws NPE
     */
    public void testNewSingleThreadExecutor3() {
        try {
            ExecutorService unused = Executors.newSingleThreadExecutor(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new SingleThreadExecutor cannot be casted to concrete implementation
     */
    public void testCastNewSingleThreadExecutor() {
        final ExecutorService e = Executors.newSingleThreadExecutor();
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor)e;
                shouldThrow();
            } catch (ClassCastException success) {}
        }
    }

    /**
     * A new newFixedThreadPool can execute runnables
     */
    public void testNewFixedThreadPool1() {
        final ExecutorService e = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new newFixedThreadPool with given ThreadFactory can execute runnables
     */
    public void testNewFixedThreadPool2() {
        final ExecutorService e = Executors.newFixedThreadPool(2, new SimpleThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new newFixedThreadPool with null ThreadFactory throws
     * NullPointerException
     */
    public void testNewFixedThreadPool3() {
        try {
            ExecutorService unused = Executors.newFixedThreadPool(2, null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new newFixedThreadPool with 0 threads throws IllegalArgumentException
     */
    public void testNewFixedThreadPool4() {
        try {
            ExecutorService unused = Executors.newFixedThreadPool(0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * An unconfigurable newFixedThreadPool can execute runnables
     */
    public void testUnconfigurableExecutorService() {
        final ExecutorService e = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(2));
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * unconfigurableExecutorService(null) throws NPE
     */
    public void testUnconfigurableExecutorServiceNPE() {
        try {
            ExecutorService unused =
                Executors.unconfigurableExecutorService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * unconfigurableScheduledExecutorService(null) throws NPE
     */
    public void testUnconfigurableScheduledExecutorServiceNPE() {
        try {
            ExecutorService unused =
                Executors.unconfigurableScheduledExecutorService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * a newSingleThreadScheduledExecutor successfully runs delayed task
     */
    public void testNewSingleThreadScheduledExecutor() throws Exception {
        final ScheduledExecutorService p = Executors.newSingleThreadScheduledExecutor();
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future<?> f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * a newScheduledThreadPool successfully runs delayed task
     */
    public void testNewScheduledThreadPool() throws Exception {
        final ScheduledExecutorService p = Executors.newScheduledThreadPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future<?> f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * an unconfigurable newScheduledThreadPool successfully runs delayed task
     */
    public void testUnconfigurableScheduledExecutorService() throws Exception {
        final ScheduledExecutorService p =
            Executors.unconfigurableScheduledExecutorService
            (Executors.newScheduledThreadPool(2));
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future<?> f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * Future.get on submitted tasks will time out if they compute too long.
     */
    public void testTimedCallable() throws Exception {
        final ExecutorService[] executors = {
            Executors.newSingleThreadExecutor(),
            Executors.newCachedThreadPool(),
            Executors.newFixedThreadPool(2),
            Executors.newScheduledThreadPool(2),
        };

        final CountDownLatch done = new CountDownLatch(1);

        final Runnable sleeper = new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                done.await(LONG_DELAY_MS, MILLISECONDS);
            }};

        List<Thread> threads = new ArrayList<>();
        for (final ExecutorService executor : executors) {
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() {
                    Future<?> future = executor.submit(sleeper);
                    assertFutureTimesOut(future);
                }}));
        }
        for (Thread thread : threads)
            awaitTermination(thread);
        done.countDown();
        for (ExecutorService executor : executors)
            joinPool(executor);
    }

    /**
     * ThreadPoolExecutor using defaultThreadFactory has
     * specified group, priority, daemon status, and name
     */
    @SuppressWarnings("removal")
    public void testDefaultThreadFactory() throws Exception {
        final ThreadGroup egroup = Thread.currentThread().getThreadGroup();
        final CountDownLatch done = new CountDownLatch(1);
        Runnable r = new CheckedRunnable() {
            public void realRun() {
                try {
                    Thread current = Thread.currentThread();
                    assertFalse(current.isDaemon());
                    assertTrue(current.getPriority() <= Thread.NORM_PRIORITY);
                    assertSame(current.getThreadGroup(), egroup);
                    assertTrue(current.getName().endsWith("thread-1"));
                } catch (SecurityException ok) {
                    // Also pass if not allowed to change setting
                }
                done.countDown();
            }};
        ExecutorService e = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(r);
            await(done);
        }
    }

    /**
     * ThreadPoolExecutor using privilegedThreadFactory has
     * specified group, priority, daemon status, name,
     * and context class loader
     */
    @SuppressWarnings("removal")
    public void testPrivilegedThreadFactory() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        Runnable r = new CheckedRunnable() {
            public void realRun() throws Exception {
                final ThreadGroup egroup = Thread.currentThread().getThreadGroup();
                final ClassLoader thisccl = Thread.currentThread().getContextClassLoader();
                Runnable r = new CheckedRunnable() {
                    public void realRun() {
                        Thread current = Thread.currentThread();
                        assertFalse(current.isDaemon());
                        assertTrue(current.getPriority() <= Thread.NORM_PRIORITY);
                        assertSame(current.getThreadGroup(), egroup);
                        assertTrue(current.getName().endsWith("thread-1"));
                        assertSame(thisccl, current.getContextClassLoader());
                        done.countDown();
                    }};
                ExecutorService e = Executors.newSingleThreadExecutor(Executors.privilegedThreadFactory());
                try (PoolCleaner cleaner = cleaner(e)) {
                    e.execute(r);
                    await(done);
                }
            }};

        r.run();
    }

    /**
     * callable(Runnable) returns null when called
     */
    public void testCallable1() throws Exception {
        Callable<?> c = Executors.callable(new NoOpRunnable());
        assertNull(c.call());
    }

    /**
     * callable(Runnable, result) returns result when called
     */
    public void testCallable2() throws Exception {
        Callable<?> c = Executors.callable(new NoOpRunnable(), one);
        assertSame(one, c.call());
    }

    /**
     * callable(PrivilegedAction) returns its result when called
     */
    public void testCallable3() throws Exception {
        Callable<?> c = Executors.callable(new PrivilegedAction() {
                public Object run() { return one; }});
        assertSame(one, c.call());
    }

    /**
     * callable(PrivilegedExceptionAction) returns its result when called
     */
    public void testCallable4() throws Exception {
        Callable<?> c = Executors.callable(new PrivilegedExceptionAction() {
                public Object run() { return one; }});
        assertSame(one, c.call());
    }

    /**
     * callable(null Runnable) throws NPE
     */
    public void testCallableNPE1() {
        try {
            Callable<?> unused = Executors.callable((Runnable) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null, result) throws NPE
     */
    public void testCallableNPE2() {
        try {
            Callable<?> unused = Executors.callable((Runnable) null, one);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null PrivilegedAction) throws NPE
     */
    public void testCallableNPE3() {
        try {
            Callable<?> unused = Executors.callable((PrivilegedAction) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null PrivilegedExceptionAction) throws NPE
     */
    public void testCallableNPE4() {
        try {
            Callable<?> unused = Executors.callable((PrivilegedExceptionAction) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(runnable, x).toString() contains toString of wrapped task
     */
    public void testCallable_withResult_toString() {
        if (testImplementationDetails) {
            Runnable r = () -> {};
            Callable<String> c = Executors.callable(r, "");
            assertEquals(
                identityString(c) + "[Wrapped task = " + r.toString() + "]",
                c.toString());
        }
    }

    /**
     * callable(runnable).toString() contains toString of wrapped task
     */
    public void testCallable_toString() {
        if (testImplementationDetails) {
            Runnable r = () -> {};
            Callable<Object> c = Executors.callable(r);
            assertEquals(
                identityString(c) + "[Wrapped task = " + r.toString() + "]",
                c.toString());
        }
    }

    /**
     * privilegedCallable
     */
    @SuppressWarnings("removal")
    public void testPrivilegedCallable() throws Exception {
        Callable<String> c = () -> "";
        Callable<String> priv = Executors.privilegedCallable(c);
        assertEquals("", priv.call());
    }

    /**
     * privilegedCallable(callable).toString() contains toString of wrapped task
     */
    @SuppressWarnings("removal")
    public void testPrivilegedCallable_toString() {
        if (testImplementationDetails) {
            Callable<String> c = () -> "";
            Callable<String> priv = Executors.privilegedCallable(c);
            assertEquals(
                identityString(priv) + "[Wrapped task = " + c.toString() + "]",
                priv.toString());
        }
    }

    /**
     * privilegedCallableUsingCurrentClassLoader(callable).toString()
     * contains toString of wrapped task
     */
    @SuppressWarnings("removal")
    public void testPrivilegedCallableUsingCurrentClassLoader_toString() {
        if (testImplementationDetails) {
            Callable<String> c = () -> "";
            Callable<String> priv = Executors.privilegedCallableUsingCurrentClassLoader(c);
            assertEquals(
                identityString(priv) + "[Wrapped task = " + c.toString() + "]",
                priv.toString());
        }
    }
}
