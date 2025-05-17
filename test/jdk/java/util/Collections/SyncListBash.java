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
 * @bug     8351230
 * @summary Test that List's new SequencedCollection methods are properly
 *          synchronized on a synchronized list.
 * @library /test/lib
 * @build   jdk.test.lib.Utils
 * @run     main SyncListBash f
 * @run     main SyncListBash r
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import jdk.test.lib.Utils;

public class SyncListBash {
    static final int LOOP_COUNT = 1000;
    static final int NUM_WORKERS = 4;
    static List<Integer> list;

    /**
     * Test for race conditions with synchronized lists. Several worker threads
     * add and remove elements from the front of the list, while the main thread
     * gets the last element. The last element should never change. However, if
     * the list isn't properly synchronized, getLast() might return the wrong
     * element or throw IndexOutOfBoundsException.
     *
     * On an unsynchronized list, this fails 200-500 out of 1000 times, so this
     * seems like a fairly reliable way to test for a race condition.
     *
     * @param args there must be one arg, "f" or "r", which determines whether the
     *             forward (original) list or reversed view of the list is tested
     * @throws InterruptedException if the main thread is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        int wrongElement = 0;
        int numExceptions = 0;

        boolean reversed = switch (args[0]) {
            case "f" -> false;
            case "r" -> true;
            default -> throw new IllegalArgumentException();
        };

        list = IntStream.range(0, 10)
                        .boxed()
                        .collect(Collectors.toCollection(ArrayList::new));
        list = Collections.synchronizedList(list);
        if (reversed)
            list = list.reversed();
        Integer expectedLast = list.getLast();

        ExecutorService pool = Executors.newFixedThreadPool(NUM_WORKERS);
        for (int i = 0; i < NUM_WORKERS; i++)
            pool.submit(() -> {
                while (! Thread.currentThread().isInterrupted()) {
                    list.add(0, -1);
                    list.remove(0);
                }
            });

        for (int i = 0; i < LOOP_COUNT; i++) {
            Thread.sleep(1L);
            try {
                Integer actualLast = list.getLast();
                if (! expectedLast.equals(actualLast)) {
                    ++wrongElement;
                }
            } catch (IndexOutOfBoundsException ioobe) {
                ++numExceptions;
            }
        }

        pool.shutdownNow();
        pool.awaitTermination(Utils.adjustTimeout(60L), TimeUnit.SECONDS);

        System.out.printf("LOOP_COUNT=%d wrongElement=%d numExceptions=%d%n",
            LOOP_COUNT, wrongElement, numExceptions);
        if (wrongElement == 0 && numExceptions == 0) {
            System.out.println("Test passed.");
        } else {
            throw new AssertionError("TEST FAILED!");
        }
    }
}
