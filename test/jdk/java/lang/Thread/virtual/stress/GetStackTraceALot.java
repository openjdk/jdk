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
 * @summary Stress test asynchronous Thread.getStackTrace
 * @requires vm.debug != true
 * @modules java.base/java.lang:+open
 * @compile --enable-preview -source ${jdk.version} GetStackTraceALot.java ../ThreadBuilders.java
 * @run main/othervm --enable-preview GetStackTraceALot
 *
 */

/**
 * @test
 * @requires vm.debug == true
 * @modules java.base/java.lang:+open
 * @compile --enable-preview -source ${jdk.version} GetStackTraceALot.java ../ThreadBuilders.java
 * @run main/othervm/timeout=300 --enable-preview GetStackTraceALot 1000
 */

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class GetStackTraceALot {
    static class RoundRobinExecutor implements Executor, AutoCloseable {
        private final ExecutorService[] executors;
        private int next;

        RoundRobinExecutor() {
            var factory = Thread.ofPlatform().name("worker-", 1).daemon(true).factory();
            var executors = new ExecutorService[2];
            for (int i = 0; i < executors.length; i++) {
                executors[i] = Executors.newSingleThreadExecutor(factory);
            }
            this.executors = executors;
        }

        @Override
        public void execute(Runnable task) {
            executors[next].execute(task);
            next = (next + 1) % executors.length;
        }

        @Override
        public void close() {
            for (int i = 0; i < executors.length; i++) {
                executors[i].shutdown();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int iterations = args.length > 0 ? Integer.parseInt(args[0])  : 10_000;

        final int ITERATIONS = iterations;
        final int SPIN_NANOS = 5000;

        AtomicInteger count = new AtomicInteger();

        try (RoundRobinExecutor executor = new RoundRobinExecutor()) {
            Thread thread = ThreadBuilders.virtualThreadBuilder(executor).start(() -> {
                while (count.incrementAndGet() < ITERATIONS) {
                    long start = System.nanoTime();
                    while ((System.nanoTime() - start) < SPIN_NANOS) {
                        Thread.onSpinWait();
                    }
                    LockSupport.parkNanos(500_000);
                }
            });

            long start = System.nanoTime();
            while (thread.isAlive()) {
                StackTraceElement[] stackTrace = thread.getStackTrace();
                // printStackTrace(stackTrace);
                Thread.sleep(5);
                if ((System.nanoTime() - start) > 500_000_000) {
                    System.out.println(count.get());
                    start = System.nanoTime();
                }
            }

            int countValue = count.get();
            if (countValue != ITERATIONS) {
                throw new RuntimeException("count = " + countValue);
            }
        }
    }

    static void printStackTrace(StackTraceElement[] stackTrace) {
        if (stackTrace == null) {
            System.out.println("NULL");
        } else {
            for (var e : stackTrace) {
                System.out.println("\t" + e);
            }
        }
    }
}
