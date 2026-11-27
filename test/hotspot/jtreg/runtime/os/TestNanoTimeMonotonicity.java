/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @test
 * @bug 8397817
 * @summary Check that System.nanoTime is monotonic across threads
 * @run main/othervm TestNanoTimeMonotonicity
 */
class TestNanoTimeMonotonicity {

    static void main() throws Exception {
        var numWorkers = Math.max(Runtime.getRuntime().availableProcessors(), 2);
        var futures = new ArrayList<Future<Void>>();
        var start = new CyclicBarrier(numWorkers);
        var deadline = System.nanoTime() + 1_000_000_000L;
        var sharedTime = new AtomicLong(System.nanoTime());
        try (var executor = Executors.newFixedThreadPool(numWorkers)) {
            for (int i = 0; i < numWorkers; i++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();

                            long now;
                            long lastOwn = System.nanoTime();
                            long lastShared;
                            do {
                                lastShared = sharedTime.getAcquire();
                                now = System.nanoTime();
                                if (now - lastOwn < 0) {
                                    throw new AssertionError(
                                            "System.nanoTime is not monotonic on a single thread: %d < %d"
                                                    .formatted(now, lastOwn));
                                }
                                if (now - lastShared < 0) {
                                    throw new AssertionError(
                                            "System.nanoTime is not monotonic across threads: %d < %d"
                                                    .formatted(now, lastShared));
                                }
                                sharedTime.setRelease(now);
                                lastOwn = now;
                            } while (now < deadline);
                            return null;
                        }));
            }
        }
        for (var future : futures) {
            future.get();
        }
    }
}
