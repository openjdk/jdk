/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/*
 * @test
 * @bug 8254350
 * @run main SwallowedInterruptedException
 * @key randomness
 */

public class SwallowedInterruptedException {
    static final int ITERATIONS = 100;

    public static void main(String[] args) throws Throwable {
        for (int i = 1; i <= ITERATIONS; i++) {
            System.out.format("Iteration %d%n", i);

            CompletableFuture<Void> future = new CompletableFuture<>();
            CountDownLatch running = new CountDownLatch(1);
            AtomicReference<String> failed = new AtomicReference<>();

            Thread thread = new Thread(() -> {
                // signal main thread that child is running
                running.countDown();

                // invoke Future.get, it complete with the interrupt status set or
                // else throw InterruptedException with the interrupt status not set.
                try {
                    future.get();

                    // interrupt status should be set
                    if (!Thread.currentThread().isInterrupted()) {
                        failed.set("Future.get completed with interrupt status not set");
                    }
                } catch (InterruptedException ex) {
                    // interrupt status should be cleared
                    if (Thread.currentThread().isInterrupted()) {
                        failed.set("InterruptedException with interrupt status set");
                    }
                } catch (Throwable ex) {
                    failed.set("Unexpected exception " + ex);
                }
            });
            thread.setDaemon(true);
            thread.start();

            // wait for thread to run
            running.await();

            // interrupt thread and set result after an optional (random) delay
            thread.interrupt();
            long sleepMillis = ThreadLocalRandom.current().nextLong(10);
            if (sleepMillis > 0)
                Thread.sleep(sleepMillis);
            future.complete(null);

            // wait for thread to terminate and check for failure
            thread.join();
            String failedReason = failed.get();
            if (failedReason != null) {
                throw new RuntimeException("Test failed: " + failedReason);
            }
        }
    }
}
