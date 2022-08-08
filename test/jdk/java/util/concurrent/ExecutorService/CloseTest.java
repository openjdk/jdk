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
 * @summary Test ExecutorService.close, including default implementation
 * @library ../lib
 * @compile --enable-preview -source ${jdk.version} CloseTest.java
 * @run testng/othervm --enable-preview CloseTest
 */

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CloseTest {

    @DataProvider(name = "executors")
    public Object[][] executors() {
        var defaultThreadFactory = Executors.defaultThreadFactory();
        var virtualThreadFactory = Thread.ofVirtual().factory();
        return new Object[][] {
            // ensures that default close method is tested
            { new DelegatingExecutorService(Executors.newCachedThreadPool()), },

            // implementations that may override close
            { new ForkJoinPool(), },
            { Executors.newFixedThreadPool(1), },
            { Executors.newCachedThreadPool(), },
            { Executors.newThreadPerTaskExecutor(defaultThreadFactory), },
            { Executors.newThreadPerTaskExecutor(virtualThreadFactory), },
        };
    }

    /**
     * Test close with no tasks running.
     */
    @Test(dataProvider = "executors")
    public void testCloseWithNoTasks(ExecutorService executor) throws Exception {
        executor.close();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
    }

    /**
     * Test close with tasks running.
     */
    @Test(dataProvider = "executors")
    public void testCloseWithRunningTasks(ExecutorService executor) throws Exception {
        Future<?> future = executor.submit(() -> {
            Thread.sleep(Duration.ofSeconds(1));
            return "foo";
        });
        executor.close();  // waits for task to complete
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
        assertEquals(future.get(), "foo");
    }

    /**
     * Test close when executor is shutdown but not terminated.
     */
    @Test(dataProvider = "executors")
    public void testShutdownBeforeClose(ExecutorService executor) throws Exception {
        Phaser phaser = new Phaser(2);
        Future<?> future = executor.submit(() -> {
            phaser.arriveAndAwaitAdvance();
            Thread.sleep(Duration.ofSeconds(1));
            return "foo";
        });
        phaser.arriveAndAwaitAdvance();   // wait for task to start

        executor.shutdown();  // shutdown, will not immediately terminate

        executor.close();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
        assertEquals(future.get(), "foo");
    }

    /**
     * Test close when terminated.
     */
    @Test(dataProvider = "executors")
    public void testTerminateBeforeClose(ExecutorService executor) throws Exception {
        executor.shutdown();
        assertTrue(executor.isTerminated());

        executor.close();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10,  TimeUnit.MILLISECONDS));
    }

    /**
     * Test invoking close with interrupt status set.
     */
    @Test(dataProvider = "executors")
    public void testInterruptBeforeClose(ExecutorService executor) throws Exception {
        Phaser phaser = new Phaser(2);
        Future<?> future = executor.submit(() -> {
            phaser.arriveAndAwaitAdvance();
            Thread.sleep(Duration.ofDays(1));
            return null;
        });
        phaser.arriveAndAwaitAdvance();  // wait for task to start

        Thread.currentThread().interrupt();
        try {
            executor.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10, TimeUnit.MILLISECONDS));
        expectThrows(ExecutionException.class, future::get);
    }

    /**
     * Test interrupting thread blocked in close.
     */
    @Test(dataProvider = "executors")
    public void testInterruptDuringClose(ExecutorService executor) throws Exception {
        Future<?> future = executor.submit(() -> {
            Thread.sleep(Duration.ofDays(1));
            return null;
        });
        Thread thread = Thread.currentThread();
        new Thread(() -> {
            try { Thread.sleep( Duration.ofMillis(500)); } catch (Exception ignore) { }
            thread.interrupt();
        }).start();
        try {
            executor.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();  // clear interrupt status
        }
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        assertTrue(executor.awaitTermination(10, TimeUnit.MILLISECONDS));
        expectThrows(ExecutionException.class, future::get);
    }
}
