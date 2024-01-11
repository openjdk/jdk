/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run main/othervm GetStackTraceALotWhenPinned 500000
 */

/*
 * @test
 * @requires vm.debug == true
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run main/othervm/timeout=300 GetStackTraceALotWhenPinned 200000
 */

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import jdk.test.lib.thread.VThreadRunner;

public class GetStackTraceALotWhenPinned {

    public static void main(String[] args) throws Exception {
        // need at least two carrier threads when main thread is a virtual thread
        if (Thread.currentThread().isVirtual()) {
            VThreadRunner.ensureParallelism(2);
        }

        int iterations = Integer.parseInt(args[0]);
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
                synchronized (GetStackTraceALotWhenPinned.class) {
                    if (timed) {
                        LockSupport.parkNanos(Long.MAX_VALUE);
                    } else {
                        LockSupport.park();
                    }
                }
                timed = !timed;
            }
        });

        long lastTimestamp = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            // wait for virtual thread to arrive
            barrier.await();

            thread.getStackTrace();
            LockSupport.unpark(thread);

            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastTimestamp) > 500) {
                System.out.format("%s %d remaining ...%n", Instant.now(), (iterations - i));
                lastTimestamp = currentTime;
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
