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
 * @summary Test cancelling a timeout task for Object.wait(millis) when there is
 *     contention on the timer queue
 * @requires vm.continuations
 * @key randomness
 * @run main/othervm
 *     -Djdk.virtualThreadScheduler.parallelism=2
 *     -Djdk.virtualThreadScheduler.timerQueues=1
 *     CancelTimerWithContention
 */

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CancelTimerWithContention {

    // number of threads
    private static final int MIN_THREADS = 100;
    private static final int MAX_THREADS = 10_000;

    // number of monitors to enter
    private static final int MIN_MONITORS = 2;
    private static final int MAX_MONITORS = 8;

    private static final Random RAND = new Random();

    public static void main(String[] args) {
        for (int threadCount = MIN_THREADS; threadCount <= MAX_THREADS; threadCount += 100) {
            System.out.format("%s #threads = %d%n", Instant.now(), threadCount);
            for (int lockCount = MIN_MONITORS; lockCount <= MAX_MONITORS;  lockCount += 2) {
                test(threadCount, lockCount);
            }
        }
    }

    /**
     * Test threads entering monitors and using Object.wait(millis) to wait. This
     * testing scenario leads to a mix of contention (with responsible threads using
     * short timeouts), timed-wait, and cancellation of timer tasks. This scenario
     * can result in contention of the timer queue.
     */
    static void test(int threadCount, int monitorCount) {
        var locks = new Object[monitorCount];
        for (int i = 0; i < monitorCount; i++) {
            locks[i] = new Object();
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var finished = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                Object lock = locks[RAND.nextInt(monitorCount)];    // random lock

                executor.submit(() -> {
                    synchronized (lock) {
                        lock.wait(Long.MAX_VALUE);
                    }
                    finished.incrementAndGet();
                    return null;
                });

                synchronized (lock) {
                    lock.notify();
                }
            }

            // notify at most one thread until all threads are finished
            while (finished.get() < threadCount) {
                for (Object lock : locks) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }
        }
    }
}
