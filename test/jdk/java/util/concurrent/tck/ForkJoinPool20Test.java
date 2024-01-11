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

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

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
}
