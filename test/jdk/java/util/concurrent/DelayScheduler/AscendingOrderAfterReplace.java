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
 * @bug 8370887
 * @summary DelayScheduler.replace method may break the 4-ary heap
 */

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class AscendingOrderAfterReplace {

    private static final int BASE_DELAY = 2000;
    private static final int[] DELAYS = { 0, 400, 900, 800, 700, 600, 430, 420, 310, 500, 200 };

    public static void main(String[] args) throws Exception {
        String delays = IntStream.of(DELAYS)
                .mapToObj(i -> Integer.valueOf(i).toString())
                .collect(Collectors.joining(", "));
        System.out.println("Delays: " + delays);

        // test cancel doesn't impact the ordering that the remaining delayed tasks execute
        for (int i = 1; i < DELAYS.length; i++) {
            while (!testCancel(i)) { }
        }
    }

    /**
     * Schedule the delayed tasks, cancel one of them, and check that the remaining tasks
     * execute in the ascending order of delay.
     * @return true if the test passed, false if a retry is needed
     * @throws RuntimeException if the test fails
     */
    private static boolean testCancel(int indexToCancel) throws Exception {
        System.out.println("=== Test cancel " + DELAYS[indexToCancel] + " ===");

        var queue = new LinkedTransferQueue<Integer>();

        // pool with one thread to ensure that delayed tasks don't execute concurrently
        try (var pool = new ForkJoinPool(1)) {
            Future<?>[] futures = Arrays.stream(DELAYS)
                    .mapToObj(d -> pool.schedule(() -> queue.add(d),
                            BASE_DELAY + d, MILLISECONDS))
                    .toArray(Future[]::new);

            // give time for -delayScheduler thread to process pending tasks
            Thread.sleep(BASE_DELAY / 2);
            if (futures[0].isDone()) {
                // delay 0 has already triggered, need to retry test
                pool.shutdownNow();
                return false;
            }
            futures[indexToCancel].cancel(true);
        }

        // delayed tasks should have executed in ascending order of their delay
        System.out.println(queue);
        int prev = Integer.MIN_VALUE;
        for (int delay: queue) {
            if (prev > delay) {
                throw new RuntimeException("Not in ascending order!");
            }
            prev = delay;
        }

        return true;
    }
}

