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

/* @test
 * @requires vm.continuations
 * @library /test/lib
 * @bug 8345294
 * @run main/othervm/timeout=200/native --enable-native-access=ALL-UNNAMED Starvation 100000
 */

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import jdk.test.lib.thread.VThreadPinner;

public class Starvation {
    public static void main(String[] args) throws Exception {
        int iterations = Integer.parseInt(args[0]);

        for (int i = 0; i < iterations; i++) {
            var exRef = new AtomicReference<Exception>();
            Thread thread =  Thread.startVirtualThread(() -> {
                try {
                    runTest();
                } catch (Exception e) {
                    exRef.set(e);
                }
            });
            while (!thread.join(Duration.ofSeconds(1))) {
                System.out.format("%s iteration %d waiting for %s%n", Instant.now(), i, thread);
            }
            Exception ex = exRef.get();
            if (ex != null) {
                throw ex;
            }
        }
    }

    static void runTest() throws InterruptedException {
        int nprocs = Runtime.getRuntime().availableProcessors();

        var threads = new ArrayList<Thread>();
        Object lock = new Object();
        synchronized (lock) {
            for (int i = 0; i < nprocs - 1; i++) {
                var started = new CountDownLatch(1);
                Thread thread = Thread.startVirtualThread(() -> {
                    started.countDown();
                    VThreadPinner.runPinned(() -> {
                        synchronized (lock) {
                        }
                    });
                });
                started.await();
                threads.add(thread);
            }
        }

        for (Thread t : threads) {
            t.join();
        }
    }
}
