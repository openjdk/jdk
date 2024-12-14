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
 * @summary Stress test parking and unparking
 * @requires vm.debug != true
 * @library /test/lib
 * @run main/othervm/timeout=300 ParkALot 300000
 */

/*
 * @test
 * @requires vm.debug == true
 * @library /test/lib
 * @run main/othervm/timeout=300 ParkALot 100000
 */

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import jdk.test.lib.Platform;

public class ParkALot {

    public static void main(String[] args) throws Exception {
        int iterations;
        int value = Integer.parseInt(args[0]);
        if (Platform.isOSX() && Platform.isX64()) {
            // reduced iterations on macosx-x64
            iterations = Math.max(value / 4, 1);
        } else {
            iterations = value;
        }

        int maxThreads = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
        for (int nthreads = 1; nthreads <= maxThreads; nthreads++) {
            System.out.format("%s %d thread(s) ...%n", Instant.now(), nthreads);
            ThreadFactory factory = Thread.ofPlatform().factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                var totalIterations = new AtomicInteger();
                for (int i = 0; i < nthreads; i++) {
                    executor.submit(() -> parkALot(iterations, totalIterations::incrementAndGet));
                }

                // shutdown, await for all threads to finish with progress output
                executor.shutdown();
                boolean terminated = false;
                while (!terminated) {
                    terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
                    System.out.format("%s => %d of %d%n",
                            Instant.now(), totalIterations.get(), iterations * nthreads);
                }
            }
            System.out.format("%s %d thread(s) done%n", Instant.now(), nthreads);
        }
    }

    /**
     * Creates a virtual thread that alternates between untimed and timed parking.
     * A platform thread spins unparking the virtual thread.
     * @param iterations number of iterations
     * @param afterIteration the task to run after each iteration
     */
    private static void parkALot(int iterations, Runnable afterIteration) {
        Thread vthread = Thread.ofVirtual().start(() -> {
            int i = 0;
            boolean timed = false;
            while (i < iterations) {
                if (timed) {
                    LockSupport.parkNanos(Long.MAX_VALUE);
                    timed = false;
                } else {
                    LockSupport.park();
                    timed = true;
                }
                i++;
                afterIteration.run();
            }
        });

        Thread.State state;
        while ((state = vthread.getState()) != Thread.State.TERMINATED) {
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                LockSupport.unpark(vthread);
            } else {
                Thread.yield();
            }
        }
    }
}
