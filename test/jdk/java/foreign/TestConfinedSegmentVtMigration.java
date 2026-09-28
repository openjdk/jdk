/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.foreign java.base/java.lang:+open java.base/jdk.internal.access
 * @library /test/lib
 * @build TestConfinedSegmentPoolUtils
 * @run junit TestConfinedSegmentVtMigration
 */

import jdk.internal.foreign.ConfinedSegmentPool;
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class TestConfinedSegmentVtMigration {

    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void poolSurvivesCarrierMigration() throws Throwable {
        assumeTrue(VThreadScheduler.supportsCustomScheduler());
        assumeTrue(ConfinedSegmentPool.pooledMemorySize() > 0);

        AtomicInteger submissions = new AtomicInteger();
        AtomicReference<Thread> runningCarrier = new AtomicReference<>();
        AtomicReference<Thread> initialCarrier = new AtomicReference<>();
        AtomicReference<Thread> resumedCarrier = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean resume = new AtomicBoolean();
        CountDownLatch readyToPark = new CountDownLatch(1);

        ThreadFactory carrierFactoryA = Thread.ofPlatform()
                .name("TestConfinedSegmentVtMigration-A")
                .factory();
        ThreadFactory carrierFactoryB = Thread.ofPlatform()
                .name("TestConfinedSegmentVtMigration-B")
                .factory();

        try (ExecutorService executorA = Executors.newSingleThreadExecutor(carrierFactoryA);
             ExecutorService executorB = Executors.newSingleThreadExecutor(carrierFactoryB)) {
            Executor scheduler = task -> {
                ExecutorService executor = submissions.getAndIncrement() == 0
                        ? executorA
                        : executorB;
                executor.execute(() -> {
                    runningCarrier.set(Thread.currentThread());
                    task.run();
                });
            };

            Thread vthread = VThreadScheduler.virtualThreadFactory(scheduler)
                    .newThread(() -> {
                        Arena arena = null;
                        try {
                            // Here, we run on carrier thread A
                            Thread carrier = runningCarrier.get();
                            initialCarrier.set(carrier);

                            long pool;
                            try (Arena scratch = Arena.ofConfined()) {
                                pool = scratch.allocate(ValueLayout.JAVA_BYTE).address();
                            }
                            assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());

                            // Keep this arena alive while the virtual thread migrates from carrier A
                            // to carrier B.
                            arena = Arena.ofConfined();
                            MemorySegment segment = arena.allocate(ValueLayout.JAVA_BYTE);
                            assertEquals(pool, segment.address());
                            assertEquals(0, TestConfinedSegmentPoolUtils.currentPool());
                            segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);

                            // Wait until the test thread terminates carrier A and allows this
                            // continuation to resume on carrier B.
                            readyToPark.countDown();
                            while (!resume.get()) {
                                LockSupport.park();
                            }

                            // The continuation is now running on carrier B.
                            resumedCarrier.set(runningCarrier.get());
                            assertNotSame(initialCarrier.get(), resumedCarrier.get());
                            assertEquals((byte) 42, segment.get(ValueLayout.JAVA_BYTE, 0));

                            arena.close();
                            arena = null;
                            assertEquals(pool, TestConfinedSegmentPoolUtils.currentPool());

                            try (Arena verify = Arena.ofConfined()) {
                                MemorySegment verifySegment =
                                        verify.allocate(ValueLayout.JAVA_BYTE);
                                assertEquals(pool, verifySegment.address());
                                // The released pool must be zeroed before reuse.
                                assertEquals((byte) 0, verifySegment.get(ValueLayout.JAVA_BYTE, 0));
                            }
                        } catch (Throwable ex) {
                            failure.set(ex);
                        } finally {
                            readyToPark.countDown();
                            if (arena != null && arena.scope().isAlive()) {
                                try {
                                    arena.close();
                                } catch (Throwable ex) {
                                    failure.compareAndSet(null, ex);
                                }
                            }
                        }
                    });

            vthread.start();
            try {
                assertTrue(readyToPark.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "virtual thread did not reach the park point");
                TestConfinedSegmentPoolUtils.rethrowIfFailed(failure.get());
                awaitState(vthread, Thread.State.WAITING);

                executorA.shutdown();
                assertTrue(executorA.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "initial carrier executor did not terminate");
                Thread carrier = initialCarrier.get();
                carrier.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                assertFalse(carrier.isAlive(), "initial carrier did not terminate");
            } finally {
                resume.set(true);
                LockSupport.unpark(vthread);
                vthread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
            assertFalse(vthread.isAlive(), "virtual thread did not terminate");
            TestConfinedSegmentPoolUtils.rethrowIfFailed(failure.get());
            assertNotSame(initialCarrier.get(), resumedCarrier.get());
        }
    }

    private static void awaitState(Thread thread, Thread.State expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (thread.getState() != expected) {
            if (thread.getState() == Thread.State.TERMINATED) {
                fail("virtual thread terminated before reaching " + expected);
            }
            if (System.nanoTime() >= deadline) {
                fail("timed out waiting for virtual thread state " + expected
                        + ", current state: " + thread.getState());
            }
            Thread.sleep(10);
        }
    }

}
