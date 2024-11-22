/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8337199
 * @summary Basic test for jcmd Thread.vthread_summary
 * @requires vm.continuations
 * @modules jdk.jcmd
 * @library /test/lib
 * @run junit/othervm VThreadSummaryTest
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.lang.management.ManagementFactory;
import jdk.management.VirtualThreadSchedulerMXBean;

import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VThreadSummaryTest {

    private OutputAnalyzer jcmd() {
        return new PidJcmdExecutor().execute("Thread.vthread_summary");
    }

    /**
     * Test that output includes the default scheduler and timeout schedulers.
     */
    @Test
    void testSchedulers() {
        // ensure default scheduler are timeout schedulers are initialized
        Thread.startVirtualThread(() -> { });

        jcmd().shouldContain("Virtual thread scheduler:")
                .shouldContain(Objects.toIdentityString(defaultScheduler()))
                .shouldContain("Timeout schedulers:")
                .shouldContain("[0] " + ScheduledThreadPoolExecutor.class.getName());
    }

    /**
     * Test that the output includes the read and writer I/O pollers.
     */
    @Test
    void testPollers() throws Exception {
        // do blocking I/O op on a virtual thread to ensure poller mechanism is initialized
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                try (var listener = new ServerSocket()) {
                    InetAddress lb = InetAddress.getLoopbackAddress();
                    listener.bind(new InetSocketAddress(lb, 0));
                    listener.setSoTimeout(1000);
                    try (Socket s = listener.accept()) {
                        fail("Connection from " + s.getRemoteSocketAddress());
                    } catch (SocketTimeoutException e) {
                        // expected
                    }
                }
                return null;
            }).get();
        }

        jcmd().shouldContain("Read I/O pollers:")
                .shouldContain("Write I/O pollers:")
                .shouldContain("[0] sun.nio.ch");
    }

    /**
     * Test that the output includes thread groupings.
     */
    //@Test
    void testThreadGroupings() {
        // ensure common pool is initialized
        CompletableFuture.runAsync(() -> { });

        try (var pool = Executors.newFixedThreadPool(1);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            jcmd().shouldContain("<root>")
                    .shouldContain("ForkJoinPool.commonPool")
                    .shouldContain(Objects.toIdentityString(pool))
                    .shouldContain(Objects.toIdentityString(executor));
        }
    }

    /**
     * Test that output is truncated when there are too many thread groupings.
     */
    //@Test
    void testTooManyThreadGroupings() {
        List<ExecutorService> executors = IntStream.range(0, 1000)
                .mapToObj(_ -> Executors.newCachedThreadPool())
                .toList();
        try {
            jcmd().shouldContain("<root>")
                    .shouldContain("<truncated ...>");
        } finally {
            executors.forEach(ExecutorService::close);
        }
    }

    /**
     * Returns the virtual thread default scheduler. This implementation works by finding
     * all FJ worker threads and mapping them to their pool. VirtualThreadSchedulerMXBean
     * is used to temporarily changing target parallelism to an "unique" value, make it
     * possbile to find the right pool.
     */
    private ForkJoinPool defaultScheduler() {
        var done = new AtomicBoolean();
        Thread vthread = Thread.startVirtualThread(() -> {
            while (!done.get()) {
                Thread.onSpinWait();
            }
        });
        var bean = ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);
        int parallelism = bean.getParallelism();
        try {
            bean.setParallelism(133);
            return Thread.getAllStackTraces()
                    .keySet()
                    .stream()
                    .filter(ForkJoinWorkerThread.class::isInstance)
                    .map(t -> ((ForkJoinWorkerThread) t).getPool())
                    .filter(p -> p.getParallelism() == 133)
                    .findAny()
                    .orElseThrow();
        } finally {
            bean.setParallelism(parallelism);
            done.set(true);
        }
    }
}
