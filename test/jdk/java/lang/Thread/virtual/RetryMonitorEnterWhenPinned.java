/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that a virtual thread waiting to enter a monitor, while pinning its
 *   carrier, will retry until it enters the monitor. This avoids starvation when the
 *   monitor is exited, an unmounted thread is the chosen successor, and the successor
 *   can't continue because there are no carriers available.
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED RetryMonitorEnterWhenPinned
 */

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.thread.VThreadPinner;

public class RetryMonitorEnterWhenPinned {
    public static void main(String[] args) throws Exception {
        int iterations = (args.length > 0) ? Integer.parseInt(args[0]) : 10;
        for (int i = 1; i <= iterations; i++) {
            System.out.printf("%s -- iteration %d --%n", Instant.now(), i);
            run();
            System.out.println();
        }
    }

    static void run() throws Exception {
        var threads = new ArrayList<Thread>();

        Object lock = new Object();
        synchronized (lock) {

            // start virtual threads that block on monitorenter
            for (int i = 0; i < 100; i++) {
                var started = new CountDownLatch(1);
                Thread thread = Thread.startVirtualThread(() -> {
                    started.countDown();
                    synchronized (lock) {
                        spin(20);
                    }
                });

                // wait for thread to start and block
                started.await();
                await(thread, Thread.State.BLOCKED);
                threads.add(thread);
            }

            // start virtual threads that block on monitorenter while pinned
            int carriersAvailable = Runtime.getRuntime().availableProcessors();
            if (Thread.currentThread().isVirtual()) {
                carriersAvailable--;
            }
            for (int i = 0; i < 100; i++) {
                var started = new CountDownLatch(1);
                Thread thread = Thread.startVirtualThread(() -> {
                    started.countDown();
                    VThreadPinner.runPinned(() -> {
                        synchronized (lock) {
                            spin(20);
                        }
                    });
                });

                // if there are carriers available when wait until the thread blocks.
                if (carriersAvailable > 0) {
                    System.out.printf("%s waiting for thread #%d to block%n",
                            Instant.now(), thread.threadId());
                    started.await();
                    await(thread, Thread.State.BLOCKED);
                    carriersAvailable--;
                }
                threads.add(thread);
            }

        } // exit monitor

        // wait for all threads to terminate
        int threadsRemaining = threads.size();
        while (threadsRemaining > 0) {
            System.out.printf("%s waiting for %d threads to terminate%n",
                    Instant.now(), threadsRemaining);
            int terminated = 0;
            for (Thread t : threads) {
                if (t.join(Duration.ofSeconds(1))) {
                    terminated++;
                }
            }
            threadsRemaining = threads.size() - terminated;
        }
        System.out.printf("%s done%n", Instant.now());
    }

    /**
     * Spin for the given number of milliseconds.
     */
    static void spin(long millis) {
        long nanos = TimeUnit.MILLISECONDS.toNanos(millis);
        long start = System.nanoTime();
        while ((System.nanoTime() - start) < nanos) {
            Thread.onSpinWait();
        }
    }

    /**
     * Wait for a thread to reach the expected state.
     */
    static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assert state != Thread.State.TERMINATED : "Thread has terminated";
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}
