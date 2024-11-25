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
 * @test id=timeout
 * @summary Stress test timed-Object.wait
 * @run main/othervm TimedWaitALot 200
 */

/*
 * @test id=timeout-notify
 * @summary Test timed-Object.wait where the waiting thread is awakened with Object.notify
 *     at around the same time that the timeout expires.
 * @run main/othervm TimedWaitALot 200 true false
 */

/*
 * @test id=timeout-interrupt
 * @summary Test timed-Object.wait where the waiting thread is awakened with Thread.interrupt
 *     at around the same time that the timeout expires.
 * @run main/othervm TimedWaitALot 200 false true
 */

/*
 * @test id=timeout-notify-interrupt
 * @summary Test timed-Object.wait where the waiting thread is awakened with Object.notify
 *     and Thread.interrupt at around the same time that the timeout expires.
 * @run main/othervm TimedWaitALot 100 true true
 */

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadLocalRandom;

public class TimedWaitALot {
    public static void main(String[] args) throws Exception {
        int iterations = Integer.parseInt(args[0]);
        boolean notify = args.length >= 2 && "true".equals(args[1]);
        boolean interrupt = args.length >=3 && "true".equals(args[2]);

        // test all timeouts concurrently
        int[] timeouts = { 10, 20, 50, 100 };
        for (int i = 1; i <= iterations; i++) {
            System.out.println(Instant.now() + " => " + i + " of " + iterations);
            test(notify, interrupt, timeouts);
        }
    }

    /**
     * Start a first virtual thread to wait in Object.wait(millis).
     * If {@code notify} is true, start a virtual thread to use Object.notifyAll at around
     * the same time that the timeout expires.
     * If {@code interrupt} is true, start virtual thread to interrupts the first virtual
     * thread at around the same time as the timeout expires.
     */
    static void test(boolean notify, boolean interrupt, int... timeouts) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int timeout : timeouts) {
                var queue = new SynchronousQueue<Thread>();
                var lock = new Object();

                // virtual thread waits with Object.wait(timeout)
                executor.submit(() -> {
                    queue.put(Thread.currentThread());
                    synchronized (lock) {
                        lock.wait(timeout);
                    }
                    return null;
                });

                // wait for thread to start
                Thread thread = queue.take();

                // start thread to Object.notifyAll at around time that the timeout expires
                if (notify) {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        synchronized (lock) {
                            sleepLessThan(timeout);
                            lock.notifyAll();
                        }
                    } else {
                        sleepLessThan(timeout);
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                }

                // start thread to interrupt first thread at around time that the timeout expires
                if (interrupt) {
                    executor.submit(() -> {
                        sleepLessThan(timeout);
                        thread.interrupt();
                        return null;
                    });
                }
            }
        }
    }

    /**
     * Sleeps for just less than the given timeout, in millis.
     */
    private static void sleepLessThan(long timeout) throws InterruptedException {
        int delta = ThreadLocalRandom.current().nextInt(10);
        Thread.sleep(timeout - delta);
    }
}
