/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322818
 * @summary Stress test Thread.getStackTrace on a virtual thread that is pinned
 * @requires vm.debug != true
 * @modules jdk.management
 * @library /test/lib
 * @run main/othervm/native/timeout=300 --enable-native-access=ALL-UNNAMED GetStackTraceALotWhenPinned 10000
 */

/*
 * @test
 * @requires vm.debug == true
 * @modules jdk.management
 * @library /test/lib
 * @run main/othervm/native/timeout=300 --enable-native-access=ALL-UNNAMED GetStackTraceALotWhenPinned 5000
 */

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import jdk.test.lib.Platform;
import jdk.test.lib.thread.VThreadRunner;   // ensureParallelism requires jdk.management
import jdk.test.lib.thread.VThreadPinner;

public class GetStackTraceALotWhenPinned {

    public static void main(String[] args) throws Exception {
        // need at least two carrier threads when main thread is a virtual thread
        if (Thread.currentThread().isVirtual()) {
            VThreadRunner.ensureParallelism(2);
        }

        int iterations;
        int value = Integer.parseInt(args[0]);
        if (Platform.isOSX()) {
            // reduced iterations on macosx
            iterations = Math.max(value / 4, 1);
        } else {
            iterations = value;
        }

        var barrier = new Barrier(2);

        // Start a virtual thread that loops doing Thread.yield and parking while pinned.
        // This loop creates the conditions for the main thread to sample the stack trace
        // as it transitions from being unmounted to parking while pinned.
        var thread = Thread.startVirtualThread(() -> {
            boolean timed = false;
            for (int i = 0; i < iterations; i++) {
                // wait for main thread to arrive
                barrier.await();

                Thread.yield();
                boolean b = timed;
                VThreadPinner.runPinned(() -> {
                    if (b) {
                        LockSupport.parkNanos(Long.MAX_VALUE);
                    } else {
                        LockSupport.park();
                    }
                });
                timed = !timed;
            }
        });

        long lastTime = System.nanoTime();
        for (int i = 1; i <= iterations; i++) {
            // wait for virtual thread to arrive
            barrier.await();

            thread.getStackTrace();
            LockSupport.unpark(thread);

            long currentTime = System.nanoTime();
            if (i == iterations || ((currentTime - lastTime) > 1_000_000_000L)) {
                System.out.format("%s => %d of %d%n", Instant.now(), i, iterations);
                lastTime = currentTime;
            }

            if (Thread.currentThread().isInterrupted()) {
                // fail quickly if interrupted by jtreg
                throw new RuntimeException("interrupted");
            }
        }
    }

    /**
     * Alow threads wait for each other to reach a common barrier point. This class does
     * not park threads that are waiting for the barrier to trip, instead it spins. This
     * makes it suitable for tests that use LockSupport.park or Thread.yield.
     */
    private static class Barrier {
        private final int parties;
        private final AtomicInteger count;
        private volatile int generation;

        Barrier(int parties) {
            this.parties = parties;
            this.count = new AtomicInteger(parties);
        }

        void await() {
            int g = generation;
            if (count.decrementAndGet() == 0) {
                count.set(parties);
                generation = g + 1;
            } else {
                while (generation == g) {
                    Thread.onSpinWait();
                }
            }
        }

    }
}
