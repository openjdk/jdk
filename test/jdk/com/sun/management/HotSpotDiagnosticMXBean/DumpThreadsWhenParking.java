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
 * @test id=transitions
 * @bug 8364343
 * @summary HotSpotDiagnosticMXBean.dumpThreads while virtual threads are parking and unparking
 * @requires vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @run main/othervm/timeout=200 DumpThreadsWhenParking 1000 1 100
 */

/*
 * @test id=concurrent
 * @summary HotSpotDiagnosticMXBean.dumpThreads from concurrent threads while virtual threads
 *    are parking and unparking
 * @requires vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @run main/othervm/timeout=200 DumpThreadsWhenParking 100 4 100
 */

/*
 * @test id=concurrent_gcstress
 * @summary HotSpotDiagnosticMXBean.dumpThreads from concurrent threads while virtual threads
 *    are parking and unparking
 * @requires vm.debug == true & vm.continuations
 * @modules jdk.management
 * @library /test/lib
 * @run main/othervm/timeout=200 -XX:+UnlockDiagnosticVMOptions -XX:+FullGCALot -XX:FullGCALotInterval=10000 DumpThreadsWhenParking 100 4 100
 */

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.threaddump.ThreadDump;

public class DumpThreadsWhenParking {

    public static void main(String... args) throws Throwable {
        int vthreadCount = Integer.parseInt(args[0]);
        int concurrentDumpers = Integer.parseInt(args[1]);
        int iterations = Integer.parseInt(args[2]);

        // need >=2 carriers to make progress
        VThreadRunner.ensureParallelism(2);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var pool = Executors.newCachedThreadPool()) {

            // start virtual threads that park and unpark
            var done = new AtomicBoolean();
            var phaser = new Phaser(vthreadCount + 1);
            for (int i = 0; i < vthreadCount; i++) {
                executor.submit(() -> {
                    phaser.arriveAndAwaitAdvance();
                    while (!done.get()) {
                        LockSupport.parkNanos(1);
                    }
                });
            }
            // wait for all virtual threads to start so all have a non-empty stack
            System.out.format("Waiting for %d virtual threads to start ...%n", vthreadCount);
            phaser.arriveAndAwaitAdvance();
            System.out.format("%d virtual threads started.%n", vthreadCount);

            // Bash on HotSpotDiagnosticMXBean.dumpThreads from >= 1 threads
            try {
                String containerName = Objects.toIdentityString(executor);
                for (int i = 1; i <= iterations; i++) {
                    System.out.format("%s %d of %d ...%n", Instant.now(), i, iterations);
                    List<Future<Void>> futures = IntStream.of(0, concurrentDumpers)
                            .mapToObj(_ -> pool.submit(() -> dumpThreads(containerName, vthreadCount)))
                            .toList();
                    for (Future<?> future : futures) {
                        future.get();
                    }
                }
            } finally {
                done.set(true);
            }
        }
    }

    /**
     * Invoke HotSpotDiagnosticMXBean.dumpThreads to generate a thread dump to a file in
     * JSON format. Parse the thread dump to ensure it contains a thread grouping with
     * the expected number of virtual threads.
     */
    static Void dumpThreads(String containerName, int expectedVThreadCount) throws Exception {
        long tid = Thread.currentThread().threadId();
        Path file = Path.of("threads-" + tid + ".json").toAbsolutePath();
        Files.deleteIfExists(file);
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);

        // read and parse the dump
        String jsonText = Files.readString(file);
        ThreadDump threadDump = ThreadDump.parse(jsonText);
        var container = threadDump.findThreadContainer(containerName).orElse(null);
        if (container == null) {
            fail(containerName + " not found in thread dump");
        }

        // check expected virtual thread count
        long threadCount = container.threads().count();
        if (threadCount != expectedVThreadCount) {
            fail(threadCount + " virtual threads found, expected " + expectedVThreadCount);
        }

        // check each thread is a virtual thread with stack frames
        container.threads().forEach(t -> {
            if (!t.isVirtual()) {
                fail("#" + t.tid() + "(" + t.name() + ") is not a virtual thread");
            }
            long stackFrameCount = t.stack().count();
            if (stackFrameCount == 0) {
                fail("#" + t.tid() + " has empty stack");
            }
        });
        return null;
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }
}
