/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test virtual threads using a custom scheduler
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run junit CustomScheduler
 */

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class CustomScheduler {
    private static ExecutorService scheduler1;
    private static ExecutorService scheduler2;

    @BeforeAll
    static void setup() {
        scheduler1 = Executors.newFixedThreadPool(1);
        scheduler2 = Executors.newFixedThreadPool(1);
    }

    @AfterAll
    static void shutdown() {
        scheduler1.shutdown();
        scheduler2.shutdown();
    }

    /**
     * Test platform thread creating a virtual thread that uses a custom scheduler.
     */
    @Test
    void testCustomScheduler1() throws Exception {
        AtomicReference<Executor> ref = new AtomicReference<>();
        ThreadBuilders.virtualThreadBuilder(scheduler1).start(() -> {
            ref.set(scheduler(Thread.currentThread()));
        }).join();
        assertTrue(ref.get() == scheduler1);
    }

    /**
     * Test virtual thread creating a virtual thread that uses a custom scheduler.
     */
    @Test
    void testCustomScheduler2() throws Exception {
        AtomicReference<Executor> ref = new AtomicReference<>();
        Thread.ofVirtual().start(() -> {
            try {
                ThreadBuilders.virtualThreadBuilder(scheduler1).start(() -> {
                    ref.set(scheduler(Thread.currentThread()));
                }).join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).join();
        assertTrue(ref.get() == scheduler1);
    }

    /**
     * Test virtual thread using custom scheduler creating a virtual thread.
     * The scheduler should be inherited.
     */
    @Test
    void testCustomScheduler3() throws Exception {
        AtomicReference<Executor> ref = new AtomicReference<>();
        ThreadBuilders.virtualThreadBuilder(scheduler1).start(() -> {
            try {
                Thread.ofVirtual().start(() -> {
                    ref.set(scheduler(Thread.currentThread()));
                }).join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).join();
        assertTrue(ref.get() == scheduler1);
    }

    /**
     * Test virtual thread using custom scheduler creating a virtual thread
     * that uses a different custom scheduler.
     */
    @Test
    void testCustomScheduler4() throws Exception {
        AtomicReference<Executor> ref = new AtomicReference<>();
        ThreadBuilders.virtualThreadBuilder(scheduler1).start(() -> {
            try {
                ThreadBuilders.virtualThreadBuilder(scheduler2).start(() -> {
                    ref.set(scheduler(Thread.currentThread()));
                }).join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).join();
        assertTrue(ref.get() == scheduler2);
    }

    /**
     * Test running task on a virtual thread, should thrown WrongThreadException.
     */
    @Test
    void testBadCarrier() {
        Executor scheduler = (task) -> {
            var exc = new AtomicReference<Throwable>();
            try {
                Thread.ofVirtual().start(() -> {
                    try {
                        task.run();
                        fail();
                    } catch (Throwable e) {
                        exc.set(e);
                    }
                }).join();
            } catch (InterruptedException e) {
                fail();
            }
            assertTrue(exc.get() instanceof WrongThreadException);
        };

        ThreadBuilders.virtualThreadBuilder(scheduler).start(LockSupport::park);
    }

    /**
     * Test parking with the virtual thread interrupt set, should not leak to the
     * carrier thread when the task completes.
     */
    @Test
    void testParkWithInterruptSet() {
        Thread carrier = Thread.currentThread();
        assumeFalse(carrier.isVirtual(), "Main thread is a virtual thread");
        try {
            var builder = ThreadBuilders.virtualThreadBuilder(Runnable::run);
            Thread vthread = builder.start(() -> {
                Thread.currentThread().interrupt();
                Thread.yield();
            });
            assertTrue(vthread.isInterrupted());
            assertFalse(carrier.isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * Test terminating with the virtual thread interrupt set, should not leak to
     * the carrier thread when the task completes.
     */
    @Test
    void testTerminateWithInterruptSet() {
        Thread carrier = Thread.currentThread();
        assumeFalse(carrier.isVirtual(), "Main thread is a virtual thread");
        try {
            var builder = ThreadBuilders.virtualThreadBuilder(Runnable::run);
            Thread vthread = builder.start(() -> {
                Thread.currentThread().interrupt();
            });
            assertTrue(vthread.isInterrupted());
            assertFalse(carrier.isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * Test running task with the carrier interrupt status set.
     */
    @Test
    void testRunWithInterruptSet() throws Exception {
        assumeFalse(Thread.currentThread().isVirtual(), "Main thread is a virtual thread");
        Executor scheduler = (task) -> {
            Thread.currentThread().interrupt();
            task.run();
        };
        try {
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread vthread = ThreadBuilders.virtualThreadBuilder(scheduler).start(() -> {
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            assertFalse(vthread.isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * Returns the scheduler for the given virtual thread.
     */
    private static Executor scheduler(Thread thread) {
        if (!thread.isVirtual())
            throw new IllegalArgumentException("Not a virtual thread");
        try {
            Field scheduler = Class.forName("java.lang.VirtualThread")
                    .getDeclaredField("scheduler");
            scheduler.setAccessible(true);
            return (Executor) scheduler.get(thread);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
