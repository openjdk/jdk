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
 * @summary Test that cancelling a delayed task doesn't impact the ordering that other
 *     delayed tasks execute
 */

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class AscendingOrderAfterReplace {

    private static final int[] DELAYS_IN_MS = { 3000, 3400, 3900, 3800, 3700, 3600, 3430, 3420, 3310, 3500, 3200 };

    public static void main(String[] args) throws Exception {
        for (int i = 1; i < DELAYS_IN_MS.length; i++) {
            System.out.println("=== Test " + i + " ===");
            while (!testCancel(DELAYS_IN_MS, i)) { }
        }
    }

    /**
     * Schedule the delayed tasks, cancel one of them, and check that the remaining tasks
     * execute in the ascending order of delay.
     * @return true if the test passed, false if a retry is needed
     * @throws RuntimeException if the test fails
     */
    private static boolean testCancel(int[] delays, int indexToCancel) throws Exception {
        log("Delayed tasks: " + toString(delays));

        // delayed tasks add to this queue when they execute
        var queue = new LinkedTransferQueue<Integer>();

        // pool with one thread to ensure that delayed tasks don't execute concurrently
        try (var pool = new ForkJoinPool(1)) {
            long startNanos = System.nanoTime();
            Future<?>[] futures = Arrays.stream(delays)
                    .mapToObj(d -> pool.schedule(() -> {
                        log("Triggered " + d);
                        queue.add(d);
                    }, d, MILLISECONDS))
                    .toArray(Future[]::new);
            long endNanos = System.nanoTime();
            log("Delayed tasks submitted");

            // check submit took < min diffs between two delays
            long submitTime = Duration.ofNanos(endNanos - startNanos).toMillis();
            long minDiff = minDifference(delays);
            if (submitTime >= minDiff) {
                log("Submit took >= " + minDiff + " ms, need to retry");
                pool.shutdownNow();
                return false;
            }

            // give a bit of time for -delayScheduler thread to process pending tasks
            Thread.sleep(minValue(delays) / 2);
            log("Cancel " + delays[indexToCancel]);
            futures[indexToCancel].cancel(true);
        }

        // delayed tasks should have executed in ascending order of their delay
        int[] executed = queue.stream().mapToInt(Integer::intValue).toArray();
        log("Executed: " + toString(executed));
        if (!isAscendingOrder(executed)) {
            throw new RuntimeException("Not in ascending order!");
        }
        return true;
    }

    /**
     * Return the minimum element.
     */
    private static int minValue(int[] array) {
        return IntStream.of(array).min().orElseThrow();
    }

    /**
     * Return the minimum difference between any two elements.
     */
    private static int minDifference(int[] array) {
        int[] sorted = array.clone();
        Arrays.sort(sorted);
        return IntStream.range(1, sorted.length)
                .map(i -> sorted[i] - sorted[i - 1])
                .min()
                .orElse(0);
    }

    /**
     * Return true if the array is in ascending order.
     */
    private static boolean isAscendingOrder(int[] array) {
        return IntStream.range(1, array.length)
                .allMatch(i -> array[i - 1] <= array[i]);
    }

    /**
     * Returns a String containing the elements of an array in index order.
     */
    private static String toString(int[] array) {
        return IntStream.of(array)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static void log(String message) {
        System.out.println(Instant.now() + " " + message);
    }
}

