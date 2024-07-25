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
 * @summary Basic test for jcmd Thread.vthread_summary
 * @enablePreview
 * @library /test/lib
 * @run main/othervm VThreadSummaryTest
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

public class VThreadSummaryTest {
    public static void main(String[] args) throws Exception {

        // ensure common pool is initialized
        CompletableFuture.runAsync(() -> { });

        // ensure at least one thread pool executor and one thread-per-task executor
        try (var pool = Executors.newFixedThreadPool(1);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // ensure poller mechanism is initialized by doing blocking I/O on a virtual thread
            executor.submit(() -> {
                try (var listener = new ServerSocket()) {
                    InetAddress lb = InetAddress.getLoopbackAddress();
                    listener.bind(new InetSocketAddress(lb, 0));
                    listener.setSoTimeout(1000);
                    try {
                        Socket s = listener.accept();
                        throw new RuntimeException("Connection from " + s + " ???");
                    } catch (SocketTimeoutException e) {
                        // expected
                    }
                }
                return null;
            }).get();

            OutputAnalyzer output = new PidJcmdExecutor().execute("Thread.vthread_summary");

            // thread groupings
            output.shouldContain("<root>")
                    .shouldContain("java.util.concurrent.ThreadPoolExecutor")
                    .shouldContain("java.util.concurrent.ThreadPerTaskExecutor")
                    .shouldContain("ForkJoinPool.commonPool");

            // virtual thread schedulers
            output.shouldContain("Default virtual thread scheduler:")
                    .shouldContain("Running, parallelism")
                    .shouldContain("Timeout schedulers:");

            // I/O pollers
            output.shouldContain("Read I/O pollers:")
                    .shouldContain("Write I/O pollers:");
        }
    }
}
