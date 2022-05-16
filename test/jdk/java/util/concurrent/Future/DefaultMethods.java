/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Future's default methods
 * @library ../lib
 * @run testng DefaultMethods
 */

import java.util.concurrent.*;
import static java.util.concurrent.Future.State.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class DefaultMethods {

    @DataProvider(name = "executors")
    public Object[][] executors() {
        return new Object[][] {
            // ensures that default implementation is tested
            { new DelegatingExecutorService(Executors.newCachedThreadPool()), },

            // executors that may return a Future that overrides the methods
            { new ForkJoinPool(), },
            { Executors.newCachedThreadPool(), }
        };
    }

    /**
     * Test methods when the task has not completed.
     */
    @Test(dataProvider = "executors")
    public void testRunningTask(ExecutorService executor) {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> { latch.await(); return null; });
            try {
                assertTrue(future.state() == RUNNING);
                expectThrows(IllegalStateException.class, future::resultNow);
                expectThrows(IllegalStateException.class, future::exceptionNow);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test methods when the task has already completed with a result.
     */
    @Test(dataProvider = "executors")
    public void testCompletedTask1(ExecutorService executor) {
        try (executor) {
            Future<String> future = executor.submit(() -> "foo");
            awaitDone(future);
            assertTrue(future.state() == SUCCESS);
            assertEquals(future.resultNow(), "foo");
            expectThrows(IllegalStateException.class, future::exceptionNow);
        }
    }

    /**
     * Test methods when the task has already completed with null.
     */
    @Test(dataProvider = "executors")
    public void testCompletedTask2(ExecutorService executor) {
        try (executor) {
            Future<String> future = executor.submit(() -> null);
            awaitDone(future);
            assertTrue(future.state() == SUCCESS);
            assertEquals(future.resultNow(), null);
            expectThrows(IllegalStateException.class, future::exceptionNow);
        }
    }

    /**
     * Test methods when the task has completed with an exception.
     */
    @Test(dataProvider = "executors")
    public void testFailedTask(ExecutorService executor) {
        try (executor) {
            Future<?> future = executor.submit(() -> { throw new ArithmeticException(); });
            awaitDone(future);
            assertTrue(future.state() == FAILED);
            expectThrows(IllegalStateException.class, future::resultNow);
            Throwable ex = future.exceptionNow();
            assertTrue(ex instanceof ArithmeticException);
        }
    }

    /**
     * Test methods when the task has been cancelled (mayInterruptIfRunning=false)
     */
    @Test(dataProvider = "executors")
    public void testCancelledTask1(ExecutorService executor) {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> { latch.await(); return null; });
            future.cancel(false);
            try {
                assertTrue(future.state() == CANCELLED);
                expectThrows(IllegalStateException.class, future::resultNow);
                expectThrows(IllegalStateException.class, future::exceptionNow);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test methods when the task has been cancelled (mayInterruptIfRunning=true)
     */
    @Test(dataProvider = "executors")
    public void testCancelledTask2(ExecutorService executor) {
        try (executor) {
            var latch = new CountDownLatch(1);
            Future<?> future = executor.submit(() -> { latch.await(); return null; });
            future.cancel(true);
            try {
                assertTrue(future.state() == CANCELLED);
                expectThrows(IllegalStateException.class, future::resultNow);
                expectThrows(IllegalStateException.class, future::exceptionNow);
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test CompletableFuture with the task has not completed.
     */
    @Test
    public void testCompletableFuture1() {
        var future = new CompletableFuture<String>();
        assertTrue(future.state() == RUNNING);
        expectThrows(IllegalStateException.class, future::resultNow);
        expectThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Test CompletableFuture with the task that completed with result.
     */
    @Test
    public void testCompletableFuture2() {
        var future = new CompletableFuture<String>();
        future.complete("foo");
        assertTrue(future.state() == SUCCESS);
        assertEquals(future.resultNow(), "foo");
        expectThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Test CompletableFuture with the task that completed with null.
     */
    @Test
    public void testCompletableFuture3() {
        var future = new CompletableFuture<String>();
        future.complete(null);
        assertTrue(future.state() == SUCCESS);
        assertEquals(future.resultNow(), null);
        expectThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Test CompletableFuture with the task that completed with exception.
     */
    @Test
    public void testCompletableFuture4() {
        var future = new CompletableFuture<String>();
        future.completeExceptionally(new ArithmeticException());
        assertTrue(future.state() == FAILED);
        expectThrows(IllegalStateException.class, future::resultNow);
        Throwable ex = future.exceptionNow();
        assertTrue(ex instanceof ArithmeticException);
    }

    /**
     * Test CompletableFuture with the task that was cancelled.
     */
    @Test
    public void testCompletableFuture5() {
        var future = new CompletableFuture<String>();
        future.cancel(false);
        assertTrue(future.state() == CANCELLED);
        expectThrows(IllegalStateException.class, future::resultNow);
        expectThrows(IllegalStateException.class, future::exceptionNow);
    }

    /**
     * Waits for the future to be done.
     */
    private static void awaitDone(Future<?> future) {
        boolean interrupted = false;
        while (!future.isDone()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
