/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Stress parking with CompletableFuture timed get
 * @requires vm.debug != true & vm.continuations
 * @compile --enable-preview -source ${jdk.version} TimedGet.java
 * @run main/othervm -Xmx1g --enable-preview TimedGet
 */

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimedGet {

    static final String RESULT = "foo";

    public static void main(String... args) throws InterruptedException {
        int threadCount = 250_000;
        if (args.length > 0) {
            threadCount = Integer.parseInt(args[0]);
        }

        // the count of the number of threads that complete successfully
        AtomicInteger completed = new AtomicInteger();

        // list of futures and threads
        List<CompletableFuture<String>> futures = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        // start threads that wait with timeout for a result
        for (int i = 0; i < threadCount; i++) {
            var future = new CompletableFuture<String>();
            futures.add(future);

            // start a thread that uses a timed-get to wait for the result
            Thread thread = Thread.ofVirtual().start(() -> {
                try {
                    String result = future.get(1, TimeUnit.DAYS);
                    if (!RESULT.equals(result)) {
                        throw new RuntimeException("result=" + result);
                    }
                    completed.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads.add(thread);
        }

        // sets the result, which will unpark waiting threads
        futures.forEach(f -> f.complete(RESULT));

        // wait for all threads to terminate
        long lastTimestamp = System.currentTimeMillis();
        int i = 0;
        while (i < threadCount) {
            Thread t = threads.get(i);
            boolean terminated;
            if (t.isAlive()) {
                terminated = t.join(Duration.ofMillis(500));

                // print trace message so the output tracks progress
                long currentTime = System.currentTimeMillis();
                if ((currentTime - lastTimestamp) > 500) {
                    System.out.println(completed.get());
                    lastTimestamp = currentTime;
                }
            } else {
                terminated = true;
            }
            if (terminated) {
                i++;
            }
        }

        // all tasks should have completed successfully
        int completedCount = completed.get();
        System.out.println(completedCount);
        if (completedCount != threadCount) {
            throw new RuntimeException("completed = " + completedCount);
        }
    }
}
