/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import jdk.internal.net.http.HttpClientImpl.DelegatingExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DelegatingExecutorTest {

    @Test
    public void testInlineExecution() {
        Thread callSiteThread = Thread.currentThread();
        Thread[] runSiteThreadRef = {null};
        new DelegatingExecutor(() -> false, null, null)
                .execute(() -> runSiteThreadRef[0] = Thread.currentThread());
        assertSame(callSiteThread, runSiteThreadRef[0]);
    }

    @Test
    public void testDelegateDeferral() {
        ImmediateExecutor delegate = new ImmediateExecutor();
        Runnable task = () -> {};
        new DelegatingExecutor(() -> true, delegate, null).execute(task);
        delegate.assertReception(task);
    }

    @Test
    public void testRejectedExecutionException() throws InterruptedException {

        // Create a deterministically throwing task
        RuntimeException error = new RejectedExecutionException();
        FirstThrowingAndThenCompletingRunnable task = new FirstThrowingAndThenCompletingRunnable(error);

        // Create a recording delegate
        ImmediateExecutor delegate = new ImmediateExecutor();

        // Create a recording error handler
        List<Runnable> reportedTasks = new ArrayList<>();
        List<Throwable> reportedErrors = new ArrayList<>();
        BiConsumer<Runnable, Throwable> errorHandler = (task_, error_) -> {
            synchronized (this) {
                reportedTasks.add(task_);
                reportedErrors.add(error_);
            }
        };

        // Verify the initial failing execution
        new DelegatingExecutor(() -> true, delegate, errorHandler).execute(task);
        delegate.assertReception(task);

        // Verify fallback to the async. pool
        assertEquals(1, reportedTasks.size());
        assertSame(task, reportedTasks.getFirst());
        assertEquals(1, reportedErrors.size());
        assertSame(error, reportedErrors.getFirst());
        boolean completed = task.completionLatch.await(5, TimeUnit.SECONDS);
        assertTrue(completed);

    }

    @Test
    public void testNotRejectedExecutionException() {

        // Create a deterministically throwing task
        RuntimeException error = new RuntimeException();
        FirstThrowingAndThenCompletingRunnable task = new FirstThrowingAndThenCompletingRunnable(error);

        // Create a recording delegate
        ImmediateExecutor delegate = new ImmediateExecutor();

        // Verify the immediate exception propagation
        Throwable thrownError = assertThrows(
                Throwable.class,
                () -> new DelegatingExecutor(() -> true, delegate, null).execute(task));
        delegate.assertReception(task);
        assertSame(error, thrownError);

    }

    private static final class ImmediateExecutor implements Executor {

        private final List<Runnable> receivedTasks = new ArrayList<>();

        @Override
        public synchronized void execute(Runnable task) {
            receivedTasks.add(task);
            task.run();
        }

        private synchronized void assertReception(Runnable... tasks) {
            assertSame(tasks.length, receivedTasks.size());
            for (int taskIndex = 0; taskIndex < tasks.length; taskIndex++) {
                assertSame(tasks[taskIndex], receivedTasks.get(taskIndex));
            }
        }

    }

    private static final class FirstThrowingAndThenCompletingRunnable implements Runnable {

        private final AtomicInteger invocationCounter = new AtomicInteger(0);

        private final CountDownLatch completionLatch = new CountDownLatch(1);

        private final RuntimeException exception;

        private FirstThrowingAndThenCompletingRunnable(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public void run() {
            switch (invocationCounter.getAndIncrement()) {
                case 0: throw exception;
                case 1: { completionLatch.countDown(); break; }
                default: fail();
            }
        }

    }

    @Test
    public void testDelegateShutdown() {
        AtomicInteger invocationCounter = new AtomicInteger();
        try (ExecutorService delegate = new ThreadPoolExecutor(1, 1, 1, TimeUnit.DAYS, new LinkedBlockingQueue<>()) {
            @Override
            public void shutdown() {
                invocationCounter.incrementAndGet();
                super.shutdown();
            }
        }) {
            new DelegatingExecutor(() -> true, delegate, null).shutdown();
            assertEquals(1, invocationCounter.get());
        }
    }

}
