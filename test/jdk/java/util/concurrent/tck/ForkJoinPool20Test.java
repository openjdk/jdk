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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Tests for ForkJoinPool and ForkJoinWorkerThread additions in JDK 20.
 */
public class ForkJoinPool20Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        return new TestSuite(ForkJoinPool20Test.class);
    }

    /**
     * Test that tasks submitted with externalSubmit execute.
     */
    public void testExternalSubmit1() throws Exception {
        try (var pool = new ForkJoinPool()) {
            // submit from external client
            var task1 = ForkJoinTask.adapt(() -> "foo");
            pool.externalSubmit(task1);
            assertEquals(task1.get(), "foo");

            // submit from worker thread
            Future<Future<String>> task2 = pool.submit(() -> {
                return pool.externalSubmit(ForkJoinTask.adapt(() -> "foo"));
            });
            assertEquals(task2.get().get(), "foo");
        }
    }

    /**
     * Test that tasks submitted with externalSubmit are pushed to a submission queue.
     */
    public void testExternalSubmit2() throws Exception {
        try (var pool = new ForkJoinPool(1)) {
            pool.submit(() -> {
                assertTrue(pool.getQueuedTaskCount() == 0);
                assertTrue(pool.getQueuedSubmissionCount() == 0);

                for (int count = 1; count <= 3; count++) {
                    var task = ForkJoinTask.adapt(() -> { });
                    pool.externalSubmit(task);

                    assertTrue(pool.getQueuedTaskCount() == 0);
                    assertTrue(pool.getQueuedSubmissionCount() == count);
                }
            }).get();
        }
    }

    /**
     * Test externalSubmit return value.
     */
    public void testExternalSubmitReturnsTask() {
        try (var pool = new ForkJoinPool()) {
            var task = ForkJoinTask.adapt(() -> "foo");
            assertTrue(pool.externalSubmit(task) == task);
        }
    }

    /**
     * Test externalSubmit(null) throws NullPointerException.
     */
    public void testExternalSubmitWithNull() {
        try (var pool = new ForkJoinPool()) {
            assertThrows(NullPointerException.class, () -> pool.externalSubmit(null));
        }
    }

    /**
     * Test externalSubmit throws RejectedExecutionException when pool is shutdown.
     */
    public void testExternalSubmitWhenShutdown() {
        try (var pool = new ForkJoinPool()) {
            pool.shutdown();
            var task = ForkJoinTask.adapt(() -> { });
            assertThrows(RejectedExecutionException.class, () -> pool.externalSubmit(task));
        }
    }

    /**
     * Test that tasks submitted with submit(ForkJoinTask) are pushed to a
     * submission queue.
     */
    public void testSubmit() throws Exception {
        try (var pool = new ForkJoinPool(1)) {
            ForkJoinWorkerThread worker = submitBusyTask(pool);
            try {
                assertTrue(worker.getQueuedTaskCount() == 0);
                assertTrue(pool.getQueuedTaskCount() == 0);
                assertTrue(pool.getQueuedSubmissionCount() == 0);

                for (int count = 1; count <= 3; count++) {
                    var task = ForkJoinTask.adapt(() -> { });
                    pool.submit(task);

                    // task should be in submission queue
                    assertTrue(worker.getQueuedTaskCount() == 0);
                    assertTrue(pool.getQueuedTaskCount() == 0);
                    assertTrue(pool.getQueuedSubmissionCount() == count);
                }
            } finally {
                LockSupport.unpark(worker);
            }
        }
    }

    /**
     * Test ForkJoinWorkerThread::getQueuedTaskCount returns the number of tasks in the
     * current thread's queue. This test runs with parallelism of 1 to ensure that tasks
     * aren't stolen.
     */
    public void testGetQueuedTaskCount1() throws Exception {
        try (var pool = new ForkJoinPool(1)) {
            pool.submit(() -> {
                var worker = (ForkJoinWorkerThread) Thread.currentThread();
                assertTrue(worker.getQueuedTaskCount() == 0);

                for (int count = 1; count <= 3; count++) {
                    pool.submit(() -> { });

                    // task should be in this thread's task queue
                    assertTrue(worker.getQueuedTaskCount() == count);
                    assertTrue(pool.getQueuedTaskCount() == count);
                    assertTrue(pool.getQueuedSubmissionCount() == 0);
                }
            }).get();
        }
    }

    /**
     * Test ForkJoinWorkerThread::getQueuedTaskCount returns the number of tasks in the
     * thread's queue. This test runs with parallelism of 2 and one worker active running
     * a task. This gives the test two task queues to sample.
     */
    public void testGetQueuedTaskCount2() throws Exception {
        try (var pool = new ForkJoinPool(2)) {
            // keep one worker thread active
            ForkJoinWorkerThread worker1 = submitBusyTask(pool);
            try {
                pool.submit(() -> {
                    var worker2 = (ForkJoinWorkerThread) Thread.currentThread();
                    for (int count = 1; count <= 3; count++) {
                        pool.submit(() -> { });

                        // task should be in this thread's task queue
                        assertTrue(worker1.getQueuedTaskCount() == 0);
                        assertTrue(worker2.getQueuedTaskCount() == count);
                        assertTrue(pool.getQueuedTaskCount() == count);
                        assertTrue(pool.getQueuedSubmissionCount() == 0);
                    }
                }).get();
            } finally {
                LockSupport.unpark(worker1);  // release worker1
            }
        }
    }

    /**
     * Submits a task to the pool, returning the worker thread that runs the
     * task. The task runs until the thread is unparked.
     */
    static ForkJoinWorkerThread submitBusyTask(ForkJoinPool pool) throws Exception {
        var ref = new AtomicReference<ForkJoinWorkerThread>();
        pool.submit(() -> {
            ref.set((ForkJoinWorkerThread) Thread.currentThread());
            LockSupport.park();
        });
        ForkJoinWorkerThread worker;
        while ((worker = ref.get()) == null) {
            Thread.sleep(20);
        }
        return worker;
    }

    // additions for ScheduledExecutorService


    /**
     * delayed schedule of callable successfully executes after delay
     */
    public void testSchedule1() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Callable<Boolean> task = new CheckedCallable<>() {
                public Boolean realCall() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                    return Boolean.TRUE;
                }};
            Future<Boolean> f = p.schedule(task, timeoutMillis(), MILLISECONDS);
            assertSame(Boolean.TRUE, f.get());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            assertEquals(0L, done.getCount());
        }
    }

    /**
     * delayed schedule of callable successfully executes after delay
     * even if shutdown.
     */
    public void testSchedule1b() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Callable<Boolean> task = new CheckedCallable<>() {
                public Boolean realCall() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                    return Boolean.TRUE;
                }};
            Future<Boolean> f = p.schedule(task, timeoutMillis(), MILLISECONDS);
            p.shutdown();
            assertSame(Boolean.TRUE, f.get());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            assertEquals(0L, done.getCount());
        }
    }

    /**
     * delayed schedule of runnable successfully executes after delay
     */
    public void testSchedule3() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Runnable task = new CheckedRunnable() {
                public void realRun() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }};
            Future<?> f = p.schedule(task, timeoutMillis(), MILLISECONDS);
            await(done);
            assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * scheduleAtFixedRate executes runnable after given initial delay
     */
    public void testSchedule4() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Runnable task = new CheckedRunnable() {
                public void realRun() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }};
            ScheduledFuture<?> f =
                p.scheduleAtFixedRate(task, timeoutMillis(),
                                      LONG_DELAY_MS, MILLISECONDS);
            await(done);
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            f.cancel(true);
        }
    }

    /**
     * scheduleAtFixedRate with 0 initial delay re-rexecutes
     */
    public void testSchedule4a() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(2);
            Runnable task = new Runnable() {
                public void run() {
                    done.countDown();
                }};
            ScheduledFuture<?> f =
                p.scheduleAtFixedRate(task, 0L, timeoutMillis(),
                                      MILLISECONDS);
            await(done);
            f.cancel(true);
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * scheduleWithFixedDelay executes runnable after given initial delay
     */
    public void testSchedule5() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(1);
            Runnable task = new CheckedRunnable() {
                public void realRun() {
                    done.countDown();
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }};
            ScheduledFuture<?> f =
                p.scheduleWithFixedDelay(task, timeoutMillis(),
                                         LONG_DELAY_MS, MILLISECONDS);
            await(done);
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            f.cancel(true);
        }
    }

    /**
     * scheduleWithFixedDelay with 0 initial delay re-rexecutes
     */
    public void testSchedule5a() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final long startTime = System.nanoTime();
            final CountDownLatch done = new CountDownLatch(2);
            Runnable task = new Runnable() {
                public void run() {
                    done.countDown();
                }};
            ScheduledFuture<?> f =
                p.scheduleWithFixedDelay(task, 0L, timeoutMillis(),
                                         MILLISECONDS);
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
     * scheduleAtFixedRate executes series of tasks at given rate.
     * Eventually, it must hold that:
     *   cycles - 1 <= elapsedMillis/delay < cycles
     * Additionally, periodic tasks are not run after shutdown.
     */
    public void testFixedRateSequence() throws InterruptedException {
        final ForkJoinPool p = new ForkJoinPool(4);
        try (PoolCleaner cleaner = cleaner(p)) {
            for (int delay = 1; delay <= LONG_DELAY_MS; delay *= 3) {
                final long startTime = System.nanoTime();
                final int cycles = 8;
                final CountDownLatch done = new CountDownLatch(cycles);
                final Runnable task = new CheckedRunnable() {
                    public void realRun() { done.countDown(); }};
                final ScheduledFuture<?> periodicTask =
                    p.scheduleAtFixedRate(task, 0, delay, MILLISECONDS);
                final int totalDelayMillis = (cycles - 1) * delay;
                await(done, totalDelayMillis + LONG_DELAY_MS);
                final long elapsedMillis = millisElapsedSince(startTime);
                assertTrue(elapsedMillis >= totalDelayMillis);
                if (elapsedMillis <= cycles * delay)
                    return;
                periodicTask.cancel(true); // retry with longer delay
            }
            fail("unexpected execution rate");
        }
    }
    /**
     * scheduleWithFixedDelay executes series of tasks with given
     * period.  Eventually, it must hold that each task starts at
     * least delay and at most 2 * delay after the termination of the
     * previous task. Additionally, periodic tasks are not run after
     * shutdown.
     */
    public void testFixedDelaySequence() throws InterruptedException {
        final ForkJoinPool p = new ForkJoinPool(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            for (int delay = 1; delay <= LONG_DELAY_MS; delay *= 3) {
                final long startTime = System.nanoTime();
                final AtomicLong previous = new AtomicLong(startTime);
                final AtomicBoolean tryLongerDelay = new AtomicBoolean(false);
                final int cycles = 8;
                final CountDownLatch done = new CountDownLatch(cycles);
                final int d = delay;
                final Runnable task = new CheckedRunnable() {
                    public void realRun() {
                        long now = System.nanoTime();
                        long elapsedMillis
                            = NANOSECONDS.toMillis(now - previous.get());
                        if (elapsedMillis >= (done.getCount() == cycles ? d : 2 * d))
                                tryLongerDelay.set(true);
                        previous.set(now);
                        done.countDown();
                    }};
                final ScheduledFuture<?> periodicTask =
                    p.scheduleWithFixedDelay(task, 0, delay, MILLISECONDS);
                final int totalDelayMillis = (cycles - 1) * delay;
                await(done, totalDelayMillis + cycles * LONG_DELAY_MS);
                final long elapsedMillis = millisElapsedSince(startTime);
                assertTrue(elapsedMillis >= totalDelayMillis);
                if (!tryLongerDelay.get())
                    return;
                periodicTask.cancel(true); // retry with longer delay
            }
            fail("unexpected execution rate");
        }
    }

    /**
     * Submitting null tasks throws NullPointerException
     */
    public void testNullTaskSubmission() {
        final ForkJoinPool p = new ForkJoinPool(1);
        try (PoolCleaner cleaner = cleaner(p)) {
            assertNullTaskSubmissionThrowsNullPointerException(p);
        }
    }

    /**
     * Submitted tasks are rejected when shutdown
     */
    public void testSubmittedTasksRejectedWhenShutdown() throws InterruptedException {
        final ForkJoinPool p = new ForkJoinPool(4);
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final CountDownLatch threadsStarted = new CountDownLatch(p.getParallelism());
        final CountDownLatch done = new CountDownLatch(1);
        final Runnable r = () -> {
            threadsStarted.countDown();
            for (;;) {
                try {
                    done.await();
                    return;
                } catch (InterruptedException shutdownNowDeliberatelyIgnored) {}
            }};
        final Callable<Boolean> c = () -> {
            threadsStarted.countDown();
            for (;;) {
                try {
                    done.await();
                    return Boolean.TRUE;
                } catch (InterruptedException shutdownNowDeliberatelyIgnored) {}
            }};

        try (PoolCleaner cleaner = cleaner(p, done)) {
            for (int i = p.getParallelism(); i-- > 0; ) {
                switch (rnd.nextInt(4)) {
                case 0: p.execute(r); break;
                case 1: assertFalse(p.submit(r).isDone()); break;
                case 2: assertFalse(p.submit(r, Boolean.TRUE).isDone()); break;
                case 3: assertFalse(p.submit(c).isDone()); break;
                }
            }

            await(threadsStarted);
            p.shutdown();
            done.countDown();   // release blocking tasks
            assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
        }
    }

    /**
     * A fixed delay task with overflowing period should not prevent a
     * one-shot task from executing.
     * https://bugs.openjdk.org/browse/JDK-8051859
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testScheduleWithFixedDelay_overflow() throws Exception {
        final CountDownLatch delayedDone = new CountDownLatch(1);
        final CountDownLatch immediateDone = new CountDownLatch(1);
        final ForkJoinPool p = new ForkJoinPool(2);
        p.cancelDelayedTasksOnShutdown();
        try (PoolCleaner cleaner = cleaner(p)) {
            final Runnable delayed = () -> {
                delayedDone.countDown();
                p.submit(() -> immediateDone.countDown());
            };
            p.scheduleWithFixedDelay(delayed, 0L, Long.MAX_VALUE, SECONDS);
            await(delayedDone);
            await(immediateDone);
        }
    }

    /**
     * shutdownNow cancels tasks that were not run
     */
    public void testShutdownNow_delayedTasks() throws InterruptedException {
        final ForkJoinPool p = new ForkJoinPool(2);
        List<ScheduledFuture<?>> tasks = new ArrayList<>();
        final int DELAY = 100;

        for (int i = 0; i < 3; i++) {
            Runnable r = new NoOpRunnable();
            tasks.add(p.schedule(r, DELAY, SECONDS));
            tasks.add(p.scheduleAtFixedRate(r, DELAY, DELAY, SECONDS));
            tasks.add(p.scheduleWithFixedDelay(r, DELAY, DELAY, SECONDS));
        }
        p.shutdownNow();
        assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
        for (ScheduledFuture<?> task : tasks) {
            assertTrue(task.isDone());
        }
        assertTrue(p.isTerminated());
    }

    /**
     * submitWithTimeout (eventually) cancels task after timeout
     */
    public void testSubmitWithTimeoutCancels() throws InterruptedException {
        final ForkJoinPool p = ForkJoinPool.commonPool();
        Callable<Boolean> c = new Callable<Boolean>() {
            public Boolean call() throws Exception {
                Thread.sleep(LONGER_DELAY_MS); return Boolean.TRUE; }};
        ForkJoinTask<?> task = p.submitWithTimeout(c, 1, NANOSECONDS, null);
        while(!task.isCancelled())
            Thread.sleep(timeoutMillis());
    }

    static final class SubmitWithTimeoutException extends RuntimeException {}

    /**
     * submitWithTimeout using complete completes after timeout
     */
    public void testSubmitWithCompleterTimeoutCompletes() throws InterruptedException {
        final ForkJoinPool p = ForkJoinPool.commonPool();
        Callable<Item> c = new Callable<Item>() {
            public Item call() throws Exception {
                Thread.sleep(LONGER_DELAY_MS); return one; }};
        ForkJoinTask<Item> task = p.submitWithTimeout(
            c, 1, NANOSECONDS,
            (ForkJoinTask<Item> t) ->
            t.complete(two));
        assertEquals(task.join(), two);
    }

    /**
     * submitWithTimeout using completeExceptionally throws after timeout
     */
    public void testSubmitWithTimeoutThrows() throws InterruptedException {
        final ForkJoinPool p = ForkJoinPool.commonPool();
        Callable<Boolean> c = new Callable<Boolean>() {
            public Boolean call() throws Exception {
                Thread.sleep(LONGER_DELAY_MS); return Boolean.TRUE; }};
        ForkJoinTask<Boolean> task = p.submitWithTimeout(
            c, 1, NANOSECONDS,
            (ForkJoinTask<Boolean> t) ->
            t.completeExceptionally(new SubmitWithTimeoutException()));
        try {
            task.join();
            shouldThrow();
        }
        catch (Exception ex) {
            assertTrue(ex instanceof SubmitWithTimeoutException);
        }
    }

    /**
     * submitWithTimeout doesn't cancel if completed before timeout
     */
    public void testSubmitWithTimeout_NoTimeout() throws InterruptedException {
        final ForkJoinPool p = ForkJoinPool.commonPool();
        Callable<Boolean> c = new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return Boolean.TRUE; }};
        ForkJoinTask<?> task = p.submitWithTimeout(c, LONGER_DELAY_MS, MILLISECONDS, null);
        Thread.sleep(timeoutMillis());
        assertFalse(task.isCancelled());
        assertEquals(task.join(), Boolean.TRUE);
    }

    /**
     * A delayed task completes (possibly abnormally) if shutdown after
     * calling cancelDelayedTasksOnShutdown()
     */
    public void testCancelDelayedTasksOnShutdown() throws Exception {
        final ForkJoinPool p = new ForkJoinPool(2);
        p.cancelDelayedTasksOnShutdown();
        try (PoolCleaner cleaner = cleaner(p)) {
            Callable<Boolean> task = new CheckedCallable<>() {
                public Boolean realCall() {
                    return Boolean.TRUE;
                }};
            Future<Boolean> f = p.schedule(task, LONGER_DELAY_MS, MILLISECONDS);
            p.shutdown();
            assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(f.isDone());
        }
    }

    /**
     * schedule throws RejectedExecutionException if shutdown before
     * first delayed task is submitted
     */
    public void testInitialScheduleAfterShutdown() throws InterruptedException {
        Runnable r = new NoOpRunnable();
        boolean rje = false;
        try (final ForkJoinPool p = new ForkJoinPool(1)) {
            p.shutdown();
            assertTrue(p.awaitTermination(LONG_DELAY_MS, MILLISECONDS));
            try {
                p.schedule(r, 1, MILLISECONDS);
            } catch (RejectedExecutionException ok) {
                rje = true;
            }
        }
        assertTrue(rje);
    }
}
