/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8284161 8286788
 * @summary Test java.lang.management.ThreadInfo contains expected information for carrier threads
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit CarrierThreadInfo
 */

/**
 * @test id=LM_LIGHTWEIGHT
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm -XX:LockingMode=2 CarrierThreadInfo
 */

/**
 * @test id=LM_LEGACY
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm -XX:LockingMode=1 CarrierThreadInfo
 */

/**
 * @test id=LM_MONITOR
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm -XX:LockingMode=0 CarrierThreadInfo
 */

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarrierThreadInfo {

    /**
     * Test that ThreadInfo.getLockedMonitors returns information about a lock held by
     * a carrier thread.
     */
    @Test
    void testCarrierThreadHoldsLock() throws Exception {
        Object lock = new Object();
        ThreadFactory factory = task -> Thread.ofPlatform().unstarted(() -> {
            synchronized (lock) {
                task.run();
            }
        });

        try (var scheduler = new CustomScheduler(factory)) {
            var started = new AtomicBoolean();
            var done = new AtomicBoolean();
            Thread vthread = scheduler.forkVirtualThread(() -> {
                started.set(true);
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            });
            try {
                awaitTrue(started);

                // carrier threads holds the lock
                long carrierId = scheduler.carrier().threadId();
                ThreadInfo threadInfo = ManagementFactory.getPlatformMXBean(ThreadMXBean.class)
                        .getThreadInfo(new long[] { carrierId }, true, true)[0];
                boolean holdsLock = Arrays.stream(threadInfo.getLockedMonitors())
                        .anyMatch(mi -> mi.getIdentityHashCode() == System.identityHashCode(lock));
                assertTrue(holdsLock, "Carrier should hold lock");

            } finally {
                done.set(true);
            }
        }
    }

    /**
     * Test that ThreadInfo.getLockedMonitors does not return information about a lock
     * held by mounted virtual thread.
     */
    @Test
    void testVirtualThreadHoldsLock() throws Exception {
        ThreadFactory factory = Executors.defaultThreadFactory();
        try (var scheduler = new CustomScheduler(factory)) {
            var started = new AtomicBoolean();
            var lock = new Object();
            var done = new AtomicBoolean();
            Thread vthread = scheduler.forkVirtualThread(() -> {
                started.set(true);
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            });
            try {
                awaitTrue(started);

                // carrier threads does not hold lock
                long carrierId = scheduler.carrier().threadId();
                ThreadInfo threadInfo = ManagementFactory.getPlatformMXBean(ThreadMXBean.class)
                        .getThreadInfo(new long[] { carrierId }, true, true)[0];
                boolean holdsLock = Arrays.stream(threadInfo.getLockedMonitors())
                        .anyMatch(mi -> mi.getIdentityHashCode() == System.identityHashCode(lock));
                assertFalse(holdsLock, "Carrier should not hold lock");

            } finally {
                done.set(true);
            }
        }
    }

    /**
     * Test that ThreadInfo.getLockOwnerId and getLockInfo return information about a
     * synthetic lock that make it appear that the carrier is blocking waiting on the
     * virtual thread.
     */
    @Test
    void testCarrierThreadWaits() throws Exception {
        ThreadFactory factory = Executors.defaultThreadFactory();
        try (var scheduler = new CustomScheduler(factory)) {
            var started = new AtomicBoolean();
            var done = new AtomicBoolean();
            Thread vthread = scheduler.forkVirtualThread(() -> {
                started.set(true);
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            });
            try {
                awaitTrue(started);

                long carrierId = scheduler.carrier().threadId();
                long vthreadId = vthread.threadId();

                ThreadInfo threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(carrierId);
                assertNotNull(threadInfo);

                // carrier should be blocked waiting for lock owned by virtual thread
                assertEquals(vthreadId, threadInfo.getLockOwnerId());

                // carrier thread should be on blocked waiting on virtual thread
                LockInfo lockInfo = threadInfo.getLockInfo();
                assertNotNull(lockInfo);
                assertEquals(vthread.getClass().getName(), lockInfo.getClassName());
                assertEquals(System.identityHashCode(vthread), lockInfo.getIdentityHashCode());

            } finally {
                done.set(true);
            }
        }
    }

    /**
     * Custom scheduler with a single carrier thread.
     */
    private static class CustomScheduler implements AutoCloseable {
        private final ExecutorService pool;
        private final Executor scheduler;
        private final AtomicReference<Thread> carrierRef = new AtomicReference<>();

        CustomScheduler(ThreadFactory factory) {
            pool = Executors.newSingleThreadExecutor(factory);
            scheduler = task -> {
                pool.submit(() -> {
                    carrierRef.set(Thread.currentThread());
                    try {
                        task.run();
                    } finally {
                        carrierRef.set(null);
                    }
                });
            };
        }

        /**
         * Returns the carrier thread if a virtual thread is mounted.
         */
        Thread carrier() throws InterruptedException {
            return carrierRef.get();
        }

        /**
         * Starts a virtual thread to execute the give task.
         */
        Thread forkVirtualThread(Runnable task) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);
            Thread thread = factory.newThread(task);
            thread.start();
            return thread;
        }

        @Override
        public void close() {
            pool.close();
        }
    }

    /**
     * Waits for the boolean value to become true.
     */
    private static void awaitTrue(AtomicBoolean ref) throws InterruptedException {
        while (!ref.get()) {
            Thread.sleep(20);
        }
    }
}
