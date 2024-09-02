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
 * @summary Basic test for jdk.management.VirtualThreadSchedulerMXBean
 * @requires vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED VirtualThreadSchedulerMXBeanTest
 */

import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.management.MBeanServer;
import jdk.management.VirtualThreadSchedulerMXBean;

import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadPinner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

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
     * Test parallelism.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testParallelism(VirtualThreadSchedulerMXBean bean) {
        int parallelism = bean.getParallelism();
        assertTrue(parallelism > 0);
        bean.setParallelism(parallelism + 1);
        try {
            assertEquals(parallelism + 1, bean.getParallelism());
        } finally {
            // restore
            bean.setParallelism(parallelism);
        }
    }

    /**
     * Test getThreadCount and getCarrierThreadCount.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testThreadCounts(VirtualThreadSchedulerMXBean bean) {
        // run test in virtual thread
        VThreadRunner.run(() -> {
            assertTrue(bean.getThreadCount() > 0);
            assertTrue(bean.getCarrierThreadCount() > 0);
        });
    }

    /**
     * Test getQueuedVirtualThreadCount.
     */
    @ParameterizedTest
    @MethodSource("managedBeans")
    void testQueuedVirtualThreadCount(VirtualThreadSchedulerMXBean bean) throws Exception {
        // skip if virtual thread
        if (Thread.currentThread().isVirtual()) {
            return;
        }
        int parallelism = bean.getParallelism();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new CountDownLatch(parallelism);
            var done = new CountDownLatch(1);
            try {

                // start virtual threads to pin all carriers
                for (int i = 0; i < parallelism; i++) {
                    executor.submit(() -> {
                        VThreadPinner.runPinned(() -> {
                            ready.countDown();
                            done.await();
                        });
                        return null;
                    });
                }
                ready.await();

                // start 5 virtual threads, their tasks will be queued to exeucte
                for (int i = 0; i < 5; i++) {
                    Thread.startVirtualThread(() -> { });
                }
                assertTrue(bean.getQueuedVirtualThreadCount() >= 5);

            } finally {
                done.countDown();
            }
        }
    }
}