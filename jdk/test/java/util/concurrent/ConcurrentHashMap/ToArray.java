/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4486658 8010293
 * @summary thread safety of toArray methods of subCollections
 * @author Martin Buchholz
 */

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

public class ToArray {

    public static void main(String[] args) throws Throwable {
        // Execute a number of times to increase the probability of
        // failure if there is an issue
        for (int i = 0; i < 16; i++) {
            executeTest();
        }
    }

    static void executeTest() throws Throwable {
        final Throwable throwable[] = new Throwable[1];
        final ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();

        // Number of workers equal to the number of processors
        // Each worker will put globally unique keys into the map
        final int nWorkers = Runtime.getRuntime().availableProcessors();
        final int sizePerWorker = 1024;
        final int maxSize = nWorkers * sizePerWorker;

        // The foreman keeps checking that the size of the arrays
        // obtained from the key and value sets is never less than the
        // previously observed size and is never greater than the maximum size
        // NOTE: these size constraints are not specific to toArray and are
        // applicable to any form of traversal of the collection views
        CompletableFuture<?> foreman = CompletableFuture.runAsync(new Runnable() {
            private int prevSize = 0;

            private boolean checkProgress(Object[] a) {
                int size = a.length;
                if (size < prevSize) throw new RuntimeException("WRONG WAY");
                if (size > maxSize) throw new RuntimeException("OVERSHOOT");
                if (size == maxSize) return true;
                prevSize = size;
                return false;
            }

            @Override
            public void run() {
                try {
                    Integer[] empty = new Integer[0];
                    while (true) {
                        if (checkProgress(m.values().toArray())) return;
                        if (checkProgress(m.keySet().toArray())) return;
                        if (checkProgress(m.values().toArray(empty))) return;
                        if (checkProgress(m.keySet().toArray(empty))) return;
                    }
                }
                catch (Throwable t) {
                    throwable[0] = t;
                }
            }
        });

        // Create workers
        // Each worker will put globally unique keys into the map
        CompletableFuture<?>[] workers = IntStream.range(0, nWorkers).
                mapToObj(w -> CompletableFuture.runAsync(() -> {
                    for (int i = 0, o = w * sizePerWorker; i < sizePerWorker; i++)
                        m.put(o + i, i);
                })).
                toArray(CompletableFuture<?>[]::new);

        // Wait for workers and then foreman to complete
        CompletableFuture.allOf(workers).join();
        foreman.join();

        if (throwable[0] != null)
            throw throwable[0];
    }
}
