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

/*
 * @test
 *
 * @comment If the VM does not have continuations, then VTs will be scheduled on OS threads.
 * @requires vm.continuations
 *
 * @bug 8356114 8356658
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper TestBufferStackStress2
 * @run junit TestBufferStackStress2
 */

import jdk.internal.foreign.BufferStack;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileDescriptor;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

final class TestBufferStackStress2 {

    private static final long POOL_SIZE = 64;
    private static final long SMALL_ALLOC_SIZE = 8;

    /**
     * The objective with this test is to test the case when a virtual thread VT0 is
     * mounted on a carrier thread CT0; VT0 is then suspended; The pool of carrier threads
     * are then contracted; VT0 is then remounted on another carrier thread C1. VT0 runs
     * for a while when there is a lot of GC activity.
     * In other words, we are trying to establish that there is no use-after-free and that
     * the original arena, from which reusable segments are initially allocated from, is
     * not closed underneath.
     * <p>
     * Unfortunately, this test takes about 30 seconds as that is the time it takes for
     * the pool of carrier threads to be contracted.
     */
    @Test
    void movingVirtualThreadWithGc() throws InterruptedException {

        // If the VM is configured such that the main thread is virtual,
        // this stress test will not work as the main thread is always alive causing
        // us to wait forever for contraction.
        // Hence, we will skip this test if the main thread is virtual.
        Assumptions.assumeFalse(Thread.currentThread().isVirtual(),
                "Skipped because the main thread is a virtual thread");

        final long begin = System.nanoTime();
        var pool = BufferStack.of(POOL_SIZE, 1);

        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");

        var done = new AtomicBoolean();
        var completed = new AtomicBoolean();
        var quiescentLatch = new CountDownLatch(1);

        var executor = Executors.newVirtualThreadPerTaskExecutor();

        executor.submit(() -> {
            while (!done.get()) {
                FileDescriptor.out.sync();
            }
            return null;
        });

        executor.submit(() -> {
            System.out.println(duration(begin) + "ALLOCATING = " + Thread.currentThread());
            try (Arena arena = pool.pushFrame(SMALL_ALLOC_SIZE, 1)) {
                MemorySegment segment = arena.allocate(SMALL_ALLOC_SIZE);
                done.set(true);
                // wait for ForkJoinPool to contract
                try {
                    quiescentLatch.await();
                } catch (Throwable ex) {
                    throw new AssertionError(ex);
                }
                System.out.println(duration(begin) + "ACCESSING SEGMENT");

                for (int i = 0; i < 100_000; i++) {
                    if (i % 100 == 0) {
                        System.gc();
                    }
                    segment.get(ValueLayout.JAVA_BYTE, i % SMALL_ALLOC_SIZE);
                }
                System.out.println(duration(begin) + "DONE ACCESSING SEGMENT");
            }
            System.out.println(duration(begin) + "VT DONE");
            completed.set(true);
        });

        long count;
        do {
            Thread.sleep(1000);
            count = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t instanceof ForkJoinWorkerThread)
                    .count();
        } while (count > 0);

        System.out.println(duration(begin) + "FJP HAS CONTRACTED");
        quiescentLatch.countDown(); // notify the thread that accesses the MemorySegment

        System.out.println(duration(begin) + "CLOSING EXECUTOR");
        executor.close();
        System.out.println(duration(begin) + "EXECUTOR CLOSED");

        assertTrue(completed.get(), "The VT did not complete properly");
    }

    private static String duration(Long begin) {
        var duration = Duration.of(System.nanoTime() - begin, ChronoUnit.NANOS);
        long seconds = duration.toSeconds();
        int nanos = duration.toNanosPart();
        return (Thread.currentThread().isVirtual() ? "VT: " : "PT: ") +
                String.format("%3d:%09d ", seconds, nanos);
    }

}
