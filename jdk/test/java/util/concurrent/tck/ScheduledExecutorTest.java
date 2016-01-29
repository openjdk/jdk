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
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ScheduledExecutorTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(ScheduledExecutorTest.class);
    }

    /**
     * execute successfully executes a runnable
     */
    public void testExecute() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch done = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() { done.countDown(); }};
            p.execute(task);
            assertTrue(done.await(LONG_DELAY_MS, MILLISECONDS));
        }
    }

    /**
     * delayed schedule of callable successfully executes after delay
     */
    public void testSchedule1() throws Exception {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Callable task = new CheckedCallable<Boolean>() {
                public Boolean realCall() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                    return Boolean.TRUE;
                }};
            Future f = p.schedule(task, timeoutMillis(), MILLISECONDS);
            assertSame(Boolean.TRUE, f.get());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            assertTrue(done.await(0L, MILLISECONDS));
        }
    }

    /**
     * delayed schedule of runnable successfully executes after delay
     */
    public void testSchedule3() throws Exception {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Runnable task = new CheckedRunnable() {
                public void realRun() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }};
            Future f = p.schedule(task, timeoutMillis(), MILLISECONDS);
            await(done);
            assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * scheduleAtFixedRate executes runnable after given initial delay
     */
    public void testSchedule4() throws Exception {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Runnable task = new CheckedRunnable() {
                public void realRun() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }};
            ScheduledFuture f =
                p.scheduleAtFixedRate(task, timeoutMillis(),
                                      LONG_DELAY_MS, MILLISECONDS);
            await(done);
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            f.cancel(true);
        }
    }

    /**
     * scheduleWithFixedDelay executes runnable after given initial delay
     */
    public void testSchedule5() throws Exception {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Runnable task = new CheckedRunnable() {
                public void realRun() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }};
            ScheduledFuture f =
                p.scheduleWithFixedDelay(task, timeoutMillis(),
                                         LONG_DELAY_MS, MILLISECONDS);
            await(done);
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            f.cancel(true);
        }
    }

    static class RunnableCounter implements Runnable {
        AtomicInteger count = new AtomicInteger(0);
        public void run() { count.getAndIncrement(); }
    }

    /**
     * scheduleAtFixedRate executes series of tasks at given rate
     */
    public void testFixedRateSequence() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            for (int delay = 1; delay <= LONG_DELAY_MS; delay *= 3) {
                long startTime = System.nanoTime();
                int cycles = 10;
                final CountDownLatch done = new CountDownLatch(cycles);
                Runnable task = new CheckedRunnable() {
                    public void realRun() { done.countDown(); }};
                ScheduledFuture h =
                    p.scheduleAtFixedRate(task, 0, delay, MILLISECONDS);
                await(done);
                h.cancel(true);
                double normalizedTime =
                    (double) millisElapsedSince(startTime) / delay;
                if (normalizedTime >= cycles - 1 &&
                    normalizedTime <= cycles)
                    return;
            }
            fail("unexpected execution rate");
        }
    }

    /**
     * scheduleWithFixedDelay executes series of tasks with given period
     */
    public void testFixedDelaySequence() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            for (int delay = 1; delay <= LONG_DELAY_MS; delay *= 3) {
                long startTime = System.nanoTime();
                int cycles = 10;
                final CountDownLatch done = new CountDownLatch(cycles);
                Runnable task = new CheckedRunnable() {
                    public void realRun() { done.countDown(); }};
                ScheduledFuture h =
                    p.scheduleWithFixedDelay(task, 0, delay, MILLISECONDS);
                await(done);
                h.cancel(true);
                double normalizedTime =
                    (double) millisElapsedSince(startTime) / delay;
                if (normalizedTime >= cycles - 1 &&
                    normalizedTime <= cycles)
                    return;
            }
            fail("unexpected execution rate");
        }
    }

    /**
     * execute(null) throws NPE
     */
    public void testExecuteNull() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.execute(null);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * schedule(null) throws NPE
     */
    public void testScheduleNull() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                TrackedCallable callable = null;
                Future f = p.schedule(callable, SHORT_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * execute throws RejectedExecutionException if shutdown
     */
    public void testSchedule1_RejectedExecutionException() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.shutdown();
                p.schedule(new NoOpRunnable(),
                           MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (RejectedExecutionException success) {
            } catch (SecurityException ok) {}
        }
    }

    /**
     * schedule throws RejectedExecutionException if shutdown
     */
    public void testSchedule2_RejectedExecutionException() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.shutdown();
                p.schedule(new NoOpCallable(),
                           MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (RejectedExecutionException success) {
            } catch (SecurityException ok) {}
        }
    }

    /**
     * schedule callable throws RejectedExecutionException if shutdown
     */
    public void testSchedule3_RejectedExecutionException() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.shutdown();
                p.schedule(new NoOpCallable(),
                           MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (RejectedExecutionException success) {
            } catch (SecurityException ok) {}
        }
    }

    /**
     * scheduleAtFixedRate throws RejectedExecutionException if shutdown
     */
    public void testScheduleAtFixedRate1_RejectedExecutionException() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.shutdown();
                p.scheduleAtFixedRate(new NoOpRunnable(),
                                      MEDIUM_DELAY_MS, MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (RejectedExecutionException success) {
            } catch (SecurityException ok) {}
        }
    }

    /**
     * scheduleWithFixedDelay throws RejectedExecutionException if shutdown
     */
    public void testScheduleWithFixedDelay1_RejectedExecutionException() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.shutdown();
                p.scheduleWithFixedDelay(new NoOpRunnable(),
                                         MEDIUM_DELAY_MS, MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (RejectedExecutionException success) {
            } catch (SecurityException ok) {}
        }
    }

    /**
     * getActiveCount increases but doesn't overestimate, when a
     * thread becomes active
     */
    public void testGetActiveCount() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(p, done)) {
            final CountDownLatch threadStarted = new CountDownLatch(1);
            assertEquals(0, p.getActiveCount());
            p.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadStarted.countDown();
                    assertEquals(1, p.getActiveCount());
                    await(done);
                }});
            await(threadStarted);
            assertEquals(1, p.getActiveCount());
        }
    }

    /**
     * getCompletedTaskCount increases, but doesn't overestimate,
     * when tasks complete
     */
    public void testGetCompletedTaskCount() throws InterruptedException {
        final ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch threadStarted = new CountDownLatch(1);
            final CountDownLatch threadProceed = new CountDownLatch(1);
            final CountDownLatch threadDone = new CountDownLatch(1);
            assertEquals(0, p.getCompletedTaskCount());
            p.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadStarted.countDown();
                    assertEquals(0, p.getCompletedTaskCount());
                    threadProceed.await();
                    threadDone.countDown();
                }});
            await(threadStarted);
            assertEquals(0, p.getCompletedTaskCount());
            threadProceed.countDown();
            threadDone.await();
            long startTime = System.nanoTime();
            while (p.getCompletedTaskCount() != 1) {
                if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                    fail("timed out");
                Thread.yield();
            }
        }
    }

    /**
     * getCorePoolSize returns size given in constructor if not otherwise set
     */
    public void testGetCorePoolSize() throws InterruptedException {
        ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            assertEquals(1, p.getCorePoolSize());
        }
    }

    /**
     * getLargestPoolSize increases, but doesn't overestimate, when
     * multiple threads active
     */
    public void testGetLargestPoolSize() throws InterruptedException {
        final int THREADS = 3;
        final ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(THREADS);
        final CountDownLatch threadsStarted = new CountDownLatch(THREADS);
        final CountDownLatch done = new CountDownLatch(1);
        try (PoolCleaner cleaner = cleaner(p, done)) {
            assertEquals(0, p.getLargestPoolSize());
            for (int i = 0; i < THREADS; i++)
                p.execute(new CheckedRunnable() {
                    public void realRun() throws InterruptedException {
                        threadsStarted.countDown();
                        await(done);
                        assertEquals(THREADS, p.getLargestPoolSize());
                    }});
            await(threadsStarted);
            assertEquals(THREADS, p.getLargestPoolSize());
        }
        assertEquals(THREADS, p.getLargestPoolSize());
    }

    /**
     * getPoolSize increases, but doesn't overestimate, when threads
     * become active
     */
    public void testGetPoolSize() throws InterruptedException {
        final ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        try (PoolCleaner cleaner = cleaner(p, done)) {
            assertEquals(0, p.getPoolSize());
            p.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadStarted.countDown();
                    assertEquals(1, p.getPoolSize());
                    await(done);
                }});
            await(threadStarted);
            assertEquals(1, p.getPoolSize());
        }
    }

    /**
     * getTaskCount increases, but doesn't overestimate, when tasks
     * submitted
     */
    public void testGetTaskCount() throws InterruptedException {
        final int TASKS = 3;
        final CountDownLatch done = new CountDownLatch(1);
        final ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p, done)) {
            final CountDownLatch threadStarted = new CountDownLatch(1);
            assertEquals(0, p.getTaskCount());
            assertEquals(0, p.getCompletedTaskCount());
            p.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    threadStarted.countDown();
                    await(done);
                }});
            await(threadStarted);
            assertEquals(1, p.getTaskCount());
            assertEquals(0, p.getCompletedTaskCount());
            for (int i = 0; i < TASKS; i++) {
                assertEquals(1 + i, p.getTaskCount());
                p.execute(new CheckedRunnable() {
                    public void realRun() throws InterruptedException {
                        threadStarted.countDown();
                        assertEquals(1 + TASKS, p.getTaskCount());
                        await(done);
                    }});
            }
            assertEquals(1 + TASKS, p.getTaskCount());
            assertEquals(0, p.getCompletedTaskCount());
        }
        assertEquals(1 + TASKS, p.getTaskCount());
        assertEquals(1 + TASKS, p.getCompletedTaskCount());
    }

    /**
     * getThreadFactory returns factory in constructor if not set
     */
    public void testGetThreadFactory() throws InterruptedException {
        final ThreadFactory threadFactory = new SimpleThreadFactory();
        final ScheduledThreadPoolExecutor p =
            new ScheduledThreadPoolExecutor(1, threadFactory);
        try (PoolCleaner cleaner = cleaner(p)) {
            assertSame(threadFactory, p.getThreadFactory());
        }
    }

    /**
     * setThreadFactory sets the thread factory returned by getThreadFactory
     */
    public void testSetThreadFactory() throws InterruptedException {
        ThreadFactory threadFactory = new SimpleThreadFactory();
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            p.setThreadFactory(threadFactory);
            assertSame(threadFactory, p.getThreadFactory());
        }
    }

    /**
     * setThreadFactory(null) throws NPE
     */
    public void testSetThreadFactoryNull() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            try {
                p.setThreadFactory(null);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * isShutdown is false before shutdown, true after
     */
    public void testIsShutdown() {

        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try {
            assertFalse(p.isShutdown());
        }
        finally {
            try { p.shutdown(); } catch (SecurityException ok) { return; }
        }
        assertTrue(p.isShutdown());
    }

    /**
     * isTerminated is false before termination, true after
     */
    public void testIsTerminated() throws InterruptedException {
        final ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch threadStarted = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(1);
            assertFalse(p.isTerminated());
            p.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertFalse(p.isTerminated());
                    threadStarted.countDown();
                    await(done);
                }});
            await(threadStarted);
            assertFalse(p.isTerminating());
            done.countDown();
            try { p.shutdown(); } catch (SecurityException ok) { return; }
            assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(p.isTerminated());
        }
    }

    /**
     * isTerminating is not true when running or when terminated
     */
    public void testIsTerminating() throws InterruptedException {
        final ThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            assertFalse(p.isTerminating());
            p.execute(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertFalse(p.isTerminating());
                    threadStarted.countDown();
                    await(done);
                }});
            await(threadStarted);
            assertFalse(p.isTerminating());
            done.countDown();
            try { p.shutdown(); } catch (SecurityException ok) { return; }
            assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(p.isTerminated());
            assertFalse(p.isTerminating());
        }
    }

    /**
     * getQueue returns the work queue, which contains queued tasks
     */
    public void testGetQueue() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p, done)) {
            final CountDownLatch threadStarted = new CountDownLatch(1);
            ScheduledFuture[] tasks = new ScheduledFuture[5];
            for (int i = 0; i < tasks.length; i++) {
                Runnable r = new CheckedRunnable() {
                    public void realRun() throws InterruptedException {
                        threadStarted.countDown();
                        await(done);
                    }};
                tasks[i] = p.schedule(r, 1, MILLISECONDS);
            }
            await(threadStarted);
            BlockingQueue<Runnable> q = p.getQueue();
            assertTrue(q.contains(tasks[tasks.length - 1]));
            assertFalse(q.contains(tasks[0]));
        }
    }

    /**
     * remove(task) removes queued task, and fails to remove active task
     */
    public void testRemove() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p, done)) {
            ScheduledFuture[] tasks = new ScheduledFuture[5];
            final CountDownLatch threadStarted = new CountDownLatch(1);
            for (int i = 0; i < tasks.length; i++) {
                Runnable r = new CheckedRunnable() {
                    public void realRun() throws InterruptedException {
                        threadStarted.countDown();
                        await(done);
                    }};
                tasks[i] = p.schedule(r, 1, MILLISECONDS);
            }
            await(threadStarted);
            BlockingQueue<Runnable> q = p.getQueue();
            assertFalse(p.remove((Runnable)tasks[0]));
            assertTrue(q.contains((Runnable)tasks[4]));
            assertTrue(q.contains((Runnable)tasks[3]));
            assertTrue(p.remove((Runnable)tasks[4]));
            assertFalse(p.remove((Runnable)tasks[4]));
            assertFalse(q.contains((Runnable)tasks[4]));
            assertTrue(q.contains((Runnable)tasks[3]));
            assertTrue(p.remove((Runnable)tasks[3]));
            assertFalse(q.contains((Runnable)tasks[3]));
        }
    }

    /**
     * purge eventually removes cancelled tasks from the queue
     */
    public void testPurge() throws InterruptedException {
        final ScheduledFuture[] tasks = new ScheduledFuture[5];
        final Runnable releaser = new Runnable() { public void run() {
            for (ScheduledFuture task : tasks)
                if (task != null) task.cancel(true); }};
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p, releaser)) {
            for (int i = 0; i < tasks.length; i++)
                tasks[i] = p.schedule(new SmallPossiblyInterruptedRunnable(),
                                      LONG_DELAY_MS, MILLISECONDS);
            int max = tasks.length;
            if (tasks[4].cancel(true)) --max;
            if (tasks[3].cancel(true)) --max;
            // There must eventually be an interference-free point at
            // which purge will not fail. (At worst, when queue is empty.)
            long startTime = System.nanoTime();
            do {
                p.purge();
                long count = p.getTaskCount();
                if (count == max)
                    return;
            } while (millisElapsedSince(startTime) < LONG_DELAY_MS);
            fail("Purge failed to remove cancelled tasks");
        }
    }

    /**
     * shutdownNow returns a list containing tasks that were not run,
     * and those tasks are drained from the queue
     */
    public void testShutdownNow() throws InterruptedException {
        final int poolSize = 2;
        final int count = 5;
        final AtomicInteger ran = new AtomicInteger(0);
        final ScheduledThreadPoolExecutor p =
            new ScheduledThreadPoolExecutor(poolSize);
        final CountDownLatch threadsStarted = new CountDownLatch(poolSize);
        Runnable waiter = new CheckedRunnable() { public void realRun() {
            threadsStarted.countDown();
            try {
                MILLISECONDS.sleep(2 * LONG_DELAY_MS);
            } catch (InterruptedException success) {}
            ran.getAndIncrement();
        }};
        for (int i = 0; i < count; i++)
            p.execute(waiter);
        await(threadsStarted);
        assertEquals(poolSize, p.getActiveCount());
        assertEquals(0, p.getCompletedTaskCount());
        final List<Runnable> queuedTasks;
        try {
            queuedTasks = p.shutdownNow();
        } catch (SecurityException ok) {
            return; // Allowed in case test doesn't have privs
        }
        assertTrue(p.isShutdown());
        assertTrue(p.getQueue().isEmpty());
        assertEquals(count - poolSize, queuedTasks.size());
        assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
        assertTrue(p.isTerminated());
        assertEquals(poolSize, ran.get());
        assertEquals(poolSize, p.getCompletedTaskCount());
    }

    /**
     * shutdownNow returns a list containing tasks that were not run,
     * and those tasks are drained from the queue
     */
    public void testShutdownNow_delayedTasks() throws InterruptedException {
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        List<ScheduledFuture> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Runnable r = new NoOpRunnable();
            tasks.add(p.schedule(r, 9, SECONDS));
            tasks.add(p.scheduleAtFixedRate(r, 9, 9, SECONDS));
            tasks.add(p.scheduleWithFixedDelay(r, 9, 9, SECONDS));
        }
        if (testImplementationDetails)
            assertEquals(new HashSet(tasks), new HashSet(p.getQueue()));
        final List<Runnable> queuedTasks;
        try {
            queuedTasks = p.shutdownNow();
        } catch (SecurityException ok) {
            return; // Allowed in case test doesn't have privs
        }
        assertTrue(p.isShutdown());
        assertTrue(p.getQueue().isEmpty());
        if (testImplementationDetails)
            assertEquals(new HashSet(tasks), new HashSet(queuedTasks));
        assertEquals(tasks.size(), queuedTasks.size());
        for (ScheduledFuture task : tasks) {
            assertFalse(task.isDone());
            assertFalse(task.isCancelled());
        }
        assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
        assertTrue(p.isTerminated());
    }

    /**
     * By default, periodic tasks are cancelled at shutdown.
     * By default, delayed tasks keep running after shutdown.
     * Check that changing the default values work:
     * - setExecuteExistingDelayedTasksAfterShutdownPolicy
     * - setContinueExistingPeriodicTasksAfterShutdownPolicy
     */
    public void testShutdown_cancellation() throws Exception {
        Boolean[] allBooleans = { null, Boolean.FALSE, Boolean.TRUE };
        for (Boolean policy : allBooleans)
    {
        final int poolSize = 2;
        final ScheduledThreadPoolExecutor p
            = new ScheduledThreadPoolExecutor(poolSize);
        final boolean effectiveDelayedPolicy = (policy != Boolean.FALSE);
        final boolean effectivePeriodicPolicy = (policy == Boolean.TRUE);
        final boolean effectiveRemovePolicy = (policy == Boolean.TRUE);
        if (policy != null) {
            p.setExecuteExistingDelayedTasksAfterShutdownPolicy(policy);
            p.setContinueExistingPeriodicTasksAfterShutdownPolicy(policy);
            p.setRemoveOnCancelPolicy(policy);
        }
        assertEquals(effectiveDelayedPolicy,
                     p.getExecuteExistingDelayedTasksAfterShutdownPolicy());
        assertEquals(effectivePeriodicPolicy,
                     p.getContinueExistingPeriodicTasksAfterShutdownPolicy());
        assertEquals(effectiveRemovePolicy,
                     p.getRemoveOnCancelPolicy());
        // Strategy: Wedge the pool with poolSize "blocker" threads
        final AtomicInteger ran = new AtomicInteger(0);
        final CountDownLatch poolBlocked = new CountDownLatch(poolSize);
        final CountDownLatch unblock = new CountDownLatch(1);
        final CountDownLatch periodicLatch1 = new CountDownLatch(2);
        final CountDownLatch periodicLatch2 = new CountDownLatch(2);
        Runnable task = new CheckedRunnable() { public void realRun()
                                                    throws InterruptedException {
            poolBlocked.countDown();
            assertTrue(unblock.await(LONG_DELAY_MS, MILLISECONDS));
            ran.getAndIncrement();
        }};
        List<Future<?>> blockers = new ArrayList<>();
        List<Future<?>> periodics = new ArrayList<>();
        List<Future<?>> delayeds = new ArrayList<>();
        for (int i = 0; i < poolSize; i++)
            blockers.add(p.submit(task));
        assertTrue(poolBlocked.await(LONG_DELAY_MS, MILLISECONDS));

        periodics.add(p.scheduleAtFixedRate(countDowner(periodicLatch1),
                                            1, 1, MILLISECONDS));
        periodics.add(p.scheduleWithFixedDelay(countDowner(periodicLatch2),
                                               1, 1, MILLISECONDS));
        delayeds.add(p.schedule(task, 1, MILLISECONDS));

        assertTrue(p.getQueue().containsAll(periodics));
        assertTrue(p.getQueue().containsAll(delayeds));
        try { p.shutdown(); } catch (SecurityException ok) { return; }
        assertTrue(p.isShutdown());
        assertFalse(p.isTerminated());
        for (Future<?> periodic : periodics) {
            assertTrue(effectivePeriodicPolicy ^ periodic.isCancelled());
            assertTrue(effectivePeriodicPolicy ^ periodic.isDone());
        }
        for (Future<?> delayed : delayeds) {
            assertTrue(effectiveDelayedPolicy ^ delayed.isCancelled());
            assertTrue(effectiveDelayedPolicy ^ delayed.isDone());
        }
        if (testImplementationDetails) {
            assertEquals(effectivePeriodicPolicy,
                         p.getQueue().containsAll(periodics));
            assertEquals(effectiveDelayedPolicy,
                         p.getQueue().containsAll(delayeds));
        }
        // Release all pool threads
        unblock.countDown();

        for (Future<?> delayed : delayeds) {
            if (effectiveDelayedPolicy) {
                assertNull(delayed.get());
            }
        }
        if (effectivePeriodicPolicy) {
            assertTrue(periodicLatch1.await(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(periodicLatch2.await(LONG_DELAY_MS, MILLISECONDS));
            for (Future<?> periodic : periodics) {
                assertTrue(periodic.cancel(false));
                assertTrue(periodic.isCancelled());
                assertTrue(periodic.isDone());
            }
        }
        assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
        assertTrue(p.isTerminated());
        assertEquals(2 + (effectiveDelayedPolicy ? 1 : 0), ran.get());
    }}

    /**
     * completed submit of callable returns result
     */
    public void testSubmitCallable() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            Future<String> future = e.submit(new StringTask());
            String result = future.get();
            assertSame(TEST_STRING, result);
        }
    }

    /**
     * completed submit of runnable returns successfully
     */
    public void testSubmitRunnable() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            Future<?> future = e.submit(new NoOpRunnable());
            future.get();
            assertTrue(future.isDone());
        }
    }

    /**
     * completed submit of (runnable, result) returns result
     */
    public void testSubmitRunnable2() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            Future<String> future = e.submit(new NoOpRunnable(), TEST_STRING);
            String result = future.get();
            assertSame(TEST_STRING, result);
        }
    }

    /**
     * invokeAny(null) throws NPE
     */
    public void testInvokeAny1() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                e.invokeAny(null);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * invokeAny(empty collection) throws IAE
     */
    public void testInvokeAny2() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                e.invokeAny(new ArrayList<Callable<String>>());
                shouldThrow();
            } catch (IllegalArgumentException success) {}
        }
    }

    /**
     * invokeAny(c) throws NPE if c has null elements
     */
    public void testInvokeAny3() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(latchAwaitingStringTask(latch));
            l.add(null);
            try {
                e.invokeAny(l);
                shouldThrow();
            } catch (NullPointerException success) {}
            latch.countDown();
        }
    }

    /**
     * invokeAny(c) throws ExecutionException if no task completes
     */
    public void testInvokeAny4() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            try {
                e.invokeAny(l);
                shouldThrow();
            } catch (ExecutionException success) {
                assertTrue(success.getCause() instanceof NullPointerException);
            }
        }
    }

    /**
     * invokeAny(c) returns result of some task
     */
    public void testInvokeAny5() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            String result = e.invokeAny(l);
            assertSame(TEST_STRING, result);
        }
    }

    /**
     * invokeAll(null) throws NPE
     */
    public void testInvokeAll1() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                e.invokeAll(null);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * invokeAll(empty collection) returns empty collection
     */
    public void testInvokeAll2() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Future<String>> r = e.invokeAll(new ArrayList<Callable<String>>());
            assertTrue(r.isEmpty());
        }
    }

    /**
     * invokeAll(c) throws NPE if c has null elements
     */
    public void testInvokeAll3() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(null);
            try {
                e.invokeAll(l);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * get of invokeAll(c) throws exception on failed task
     */
    public void testInvokeAll4() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            List<Future<String>> futures = e.invokeAll(l);
            assertEquals(1, futures.size());
            try {
                futures.get(0).get();
                shouldThrow();
            } catch (ExecutionException success) {
                assertTrue(success.getCause() instanceof NullPointerException);
            }
        }
    }

    /**
     * invokeAll(c) returns results of all completed tasks
     */
    public void testInvokeAll5() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            List<Future<String>> futures = e.invokeAll(l);
            assertEquals(2, futures.size());
            for (Future<String> future : futures)
                assertSame(TEST_STRING, future.get());
        }
    }

    /**
     * timed invokeAny(null) throws NPE
     */
    public void testTimedInvokeAny1() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                e.invokeAny(null, MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * timed invokeAny(,,null) throws NPE
     */
    public void testTimedInvokeAnyNullTimeUnit() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            try {
                e.invokeAny(l, MEDIUM_DELAY_MS, null);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * timed invokeAny(empty collection) throws IAE
     */
    public void testTimedInvokeAny2() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                e.invokeAny(new ArrayList<Callable<String>>(), MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (IllegalArgumentException success) {}
        }
    }

    /**
     * timed invokeAny(c) throws NPE if c has null elements
     */
    public void testTimedInvokeAny3() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(latchAwaitingStringTask(latch));
            l.add(null);
            try {
                e.invokeAny(l, MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (NullPointerException success) {}
            latch.countDown();
        }
    }

    /**
     * timed invokeAny(c) throws ExecutionException if no task completes
     */
    public void testTimedInvokeAny4() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            long startTime = System.nanoTime();
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            try {
                e.invokeAny(l, LONG_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (ExecutionException success) {
                assertTrue(success.getCause() instanceof NullPointerException);
            }
            assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        }
    }

    /**
     * timed invokeAny(c) returns result of some task
     */
    public void testTimedInvokeAny5() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            long startTime = System.nanoTime();
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            String result = e.invokeAny(l, LONG_DELAY_MS, MILLISECONDS);
            assertSame(TEST_STRING, result);
            assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        }
    }

    /**
     * timed invokeAll(null) throws NPE
     */
    public void testTimedInvokeAll1() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                e.invokeAll(null, MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * timed invokeAll(,,null) throws NPE
     */
    public void testTimedInvokeAllNullTimeUnit() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            try {
                e.invokeAll(l, MEDIUM_DELAY_MS, null);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * timed invokeAll(empty collection) returns empty collection
     */
    public void testTimedInvokeAll2() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Future<String>> r = e.invokeAll(new ArrayList<Callable<String>>(),
                                                 MEDIUM_DELAY_MS, MILLISECONDS);
            assertTrue(r.isEmpty());
        }
    }

    /**
     * timed invokeAll(c) throws NPE if c has null elements
     */
    public void testTimedInvokeAll3() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(null);
            try {
                e.invokeAll(l, MEDIUM_DELAY_MS, MILLISECONDS);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * get of element of invokeAll(c) throws exception on failed task
     */
    public void testTimedInvokeAll4() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            List<Future<String>> futures =
                e.invokeAll(l, LONG_DELAY_MS, MILLISECONDS);
            assertEquals(1, futures.size());
            try {
                futures.get(0).get();
                shouldThrow();
            } catch (ExecutionException success) {
                assertTrue(success.getCause() instanceof NullPointerException);
            }
        }
    }

    /**
     * timed invokeAll(c) returns results of all completed tasks
     */
    public void testTimedInvokeAll5() throws Exception {
        final ExecutorService e = new ScheduledThreadPoolExecutor(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            List<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            List<Future<String>> futures =
                e.invokeAll(l, LONG_DELAY_MS, MILLISECONDS);
            assertEquals(2, futures.size());
            for (Future<String> future : futures)
                assertSame(TEST_STRING, future.get());
        }
    }

    /**
     * timed invokeAll(c) cancels tasks not completed by timeout
     */
    public void testTimedInvokeAll6() throws Exception {
        for (long timeout = timeoutMillis();;) {
            final CountDownLatch done = new CountDownLatch(1);
            final Callable<String> waiter = new CheckedCallable<String>() {
                public String realCall() {
                    try { done.await(LONG_DELAY_MS, MILLISECONDS); }
                    catch (InterruptedException ok) {}
                    return "1"; }};
            final ExecutorService p = new ScheduledThreadPoolExecutor(2);
            try (PoolCleaner cleaner = cleaner(p, done)) {
                List<Callable<String>> tasks = new ArrayList<>();
                tasks.add(new StringTask("0"));
                tasks.add(waiter);
                tasks.add(new StringTask("2"));
                long startTime = System.nanoTime();
                List<Future<String>> futures =
                    p.invokeAll(tasks, timeout, MILLISECONDS);
                assertEquals(tasks.size(), futures.size());
                assertTrue(millisElapsedSince(startTime) >= timeout);
                for (Future future : futures)
                    assertTrue(future.isDone());
                assertTrue(futures.get(1).isCancelled());
                try {
                    assertEquals("0", futures.get(0).get());
                    assertEquals("2", futures.get(2).get());
                    break;
                } catch (CancellationException retryWithLongerTimeout) {
                    timeout *= 2;
                    if (timeout >= LONG_DELAY_MS / 2)
                        fail("expected exactly one task to be cancelled");
                }
            }
        }
    }

    /**
     * A fixed delay task with overflowing period should not prevent a
     * one-shot task from executing.
     * https://bugs.openjdk.java.net/browse/JDK-8051859
     */
    public void testScheduleWithFixedDelay_overflow() throws Exception {
        final CountDownLatch delayedDone = new CountDownLatch(1);
        final CountDownLatch immediateDone = new CountDownLatch(1);
        final ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            final Runnable immediate = new Runnable() { public void run() {
                immediateDone.countDown();
            }};
            final Runnable delayed = new Runnable() { public void run() {
                delayedDone.countDown();
                p.submit(immediate);
            }};
            p.scheduleWithFixedDelay(delayed, 0L, Long.MAX_VALUE, SECONDS);
            await(delayedDone);
            await(immediateDone);
        }
    }

}
