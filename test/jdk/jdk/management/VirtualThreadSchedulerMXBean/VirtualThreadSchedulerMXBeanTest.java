/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338890
 * @summary Basic test for jdk.management.VirtualThreadSchedulerMXBean
 * @requires vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @run junit/othervm VirtualThreadSchedulerMXBeanTest
 */

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import javax.management.MBeanServer;
import jdk.management.VirtualThreadSchedulerMXBean;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class VirtualThreadSchedulerMXBeanTest {

    /**
     * VirtualThreadSchedulerMXBean objects to test.
     */
    private static Stream<VirtualThreadSchedulerMXBean> managedBeans() throws Exception {
        var bean1 = ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        var bean2 = ManagementFactory.newPlatformMXBeanProxy(server,
                "jdk.management:type=VirtualThreadScheduler",
                VirtualThreadSchedulerMXBean.class);

        return Stream.of(bean1, bean2);
    }

    /**
     * Test default parallelism.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testDefaultParallelism(VirtualThreadSchedulerMXBean bean) {
        assertEquals(Runtime.getRuntime().availableProcessors(), bean.getParallelism());
    }

    /**
     * Test increasing parallelism.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testIncreaseParallelism(VirtualThreadSchedulerMXBean bean) throws Exception {
        assumeFalse(Thread.currentThread().isVirtual(), "Main thread is a virtual thread");

        final int parallelism = bean.getParallelism();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var done = new AtomicBoolean();
            Runnable busyTask = () -> {
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            };

            try {
                // saturate
                IntStream.range(0, parallelism).forEach(_ -> executor.submit(busyTask));
                awaitPoolSizeGte(bean, parallelism);
                awaitMountedVirtualThreadCountGte(bean, parallelism);

                // increase parallelism
                for (int k = 1; k <= 4; k++) {
                    int newParallelism = parallelism + k;
                    bean.setParallelism(newParallelism);
                    executor.submit(busyTask);

                    // pool size and mounted virtual thread should increase
                    awaitPoolSizeGte(bean, newParallelism);
                    awaitMountedVirtualThreadCountGte(bean, newParallelism);
                }
            } finally {
                done.set(true);
            }
        } finally {
            bean.setParallelism(parallelism);   // restore
        }
    }

    /**
     * Test reducing parallelism.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testReduceParallelism(VirtualThreadSchedulerMXBean bean) throws Exception {
        assumeFalse(Thread.currentThread().isVirtual(), "Main thread is a virtual thread");

        final int parallelism = bean.getParallelism();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var done = new AtomicBoolean();
            var sleep = new AtomicBoolean();

            // spin when !sleep
            Runnable busyTask = () -> {
                while (!done.get()) {
                    if (sleep.get()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) { }
                    } else {
                        Thread.onSpinWait();
                    }
                }
            };

            try {
                // increase parallelism + saturate
                int newParallelism = parallelism + 4;
                bean.setParallelism(newParallelism);
                IntStream.range(0, newParallelism).forEach(_ -> executor.submit(busyTask));
                awaitMountedVirtualThreadCountGte(bean, newParallelism);

                // reduce parallelism and workload
                newParallelism = Math.clamp(parallelism / 2, 1, parallelism);
                bean.setParallelism(newParallelism);
                sleep.set(true);
                // mounted virtual thread count should reduce
                awaitMountedVirtualThreadCountLte(bean, newParallelism);

                // increase workload, the mounted virtual thread count should not increase
                sleep.set(false);
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(100);
                    assertTrue(bean.getMountedVirtualThreadCount() <= newParallelism);
                }

            } finally {
                done.set(true);
            }
        } finally {
            bean.setParallelism(parallelism);  // restore
        }
    }

    /**
     * Test getPoolSize.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testPoolSize(VirtualThreadSchedulerMXBean bean) {
        assertTrue(bean.getPoolSize() >= 0);
        VThreadRunner.run(() -> {
            assertTrue(Thread.currentThread().isVirtual());
            assertTrue(bean.getPoolSize() >= 1);
        });
    }

    /**
     * Test getMountedVirtualThreadCount.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testMountedVirtualThreadCount(VirtualThreadSchedulerMXBean bean) {
        assertTrue(bean.getMountedVirtualThreadCount() >= 0);
        VThreadRunner.run(() -> {
            assertTrue(Thread.currentThread().isVirtual());
            assertTrue(bean.getMountedVirtualThreadCount() >= 1);
        });
    }

    /**
     * Test getQueuedVirtualThreadCount.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testQueuedVirtualThreadCount(VirtualThreadSchedulerMXBean bean) throws Exception {
        assumeFalse(Thread.currentThread().isVirtual(), "Main thread is a virtual thread");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var done = new AtomicBoolean();
            Runnable busyTask = () -> {
                while (!done.get()) {
                    Thread.onSpinWait();
                }
            };

            try {
                // saturate
                int parallelism = bean.getParallelism();
                IntStream.range(0, parallelism).forEach(_ -> executor.submit(busyTask));
                awaitMountedVirtualThreadCountGte(bean, parallelism);

                // start 5 virtual threads, their tasks will be queued to execute
                for (int i = 0; i < 5; i++) {
                    executor.submit(() -> { });
                }
                assertTrue(bean.getQueuedVirtualThreadCount() >= 5);
            } finally {
                done.set(true);
            }
        }
    }

    /**
     * Waits for pool size >= target to be true.
     */
    void awaitPoolSizeGte(VirtualThreadSchedulerMXBean bean, int target) throws InterruptedException {
        System.err.format("await pool size >= %d ...%n", target);
        while (bean.getPoolSize() < target) {
            Thread.sleep(10);
        }
    }

    /**
     * Waits for the mounted virtual thread count >= target to be true.
     */
    void awaitMountedVirtualThreadCountGte(VirtualThreadSchedulerMXBean bean,
                                           int target) throws InterruptedException {
        System.err.format("await mounted virtual thread count >= %d ...%n", target);
        while (bean.getMountedVirtualThreadCount() < target) {
            Thread.sleep(10);
        }
    }

    /**
     * Waits for the mounted virtual thread count <= target to be true.
     */
    void awaitMountedVirtualThreadCountLte(VirtualThreadSchedulerMXBean bean,
                                           int target) throws InterruptedException {
        System.err.format("await mounted virtual thread count <= %d ...%n", target);
        while (bean.getMountedVirtualThreadCount() > target) {
            Thread.sleep(10);
        }
    }
}