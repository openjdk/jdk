/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161 8288214
 * @summary Verifies that FRAME_POP event is delivered when called from URL.openStream().
 * @requires vm.continuations
 * @enablePreview
 * @modules jdk.httpserver
 * @library /test/lib
 * @run main/othervm/native
 *     -agentlib:VThreadNotifyFramePopTest
 *     -Djdk.defaultScheduler.parallelism=2 -Djdk.defaultScheduler.maxPoolSize=2
 *     VThreadNotifyFramePopTest
 */

/*
 * This test reproduces a bug with NotifyFramePop not working properly when called
 * from URL.openStream() while executing on a virtual thread. The FRAME_POP event is
 * never delivered.
 *
 * The test first sets up a breakpoint on URL.openStream(). Once it is hit the test
 * does a NotifyFramePop on the current frame, and also sets up a breakpoint on
 * the test's brkpoint() method, which is called immediately after URL.openStream()
 * returns. The expaction is that the FRAME_POP event should be delevered before
 * hitting the breakpoint in brkpoint().
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

public class VThreadNotifyFramePopTest {
    private static final String agentLib = "VThreadNotifyFramePopTest";

    static native void enableEvents(Thread thread, Class testClass, Class urlClass);
    static native boolean check();


    /**
     * Creates a HTTP server bound to the loopback address. The server responds to
     * the request "/hello" with a message.
     */
    static HttpServer createHttpServer() throws IOException {
        InetAddress lb = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(lb, 0), 16);
        server.createContext("/hello", e -> {
            byte[] response = "Hello".getBytes("UTF-8");
            e.sendResponseHeaders(200, response.length);
            try (OutputStream out = e.getResponseBody()) {
                out.write(response);
            }
        });
        return server;
    }

    static int brkpoint() {
        return 5;
    }
    static void run() {
        run2();
    }
    static void run2() {
        run3();
    }
    static void run3() {
        run4();
    }
    static void run4() {
        try {
            HttpServer server = createHttpServer();
            server.start();
            try {
                URL url = URIBuilder.newBuilder()
                        .scheme("http")
                        .loopback()
                        .port(server.getAddress().getPort())
                        .path("/hello")
                        .toURL();
                System.out.println("open " + url);
                try (InputStream in = url.openStream()) {
                    brkpoint();
                    System.out.println("reading response");
                    in.readAllBytes();
                }
            } finally {
                System.out.println("stop server");
                server.stop(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("run done");
    }

    void runTest() throws Exception {
        enableEvents(Thread.currentThread(), VThreadNotifyFramePopTest.class, URL.class);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(VThreadNotifyFramePopTest::run);
            System.out.println("virtual thread started");
            future.get();
        }
        System.out.println("Step::main done");
    }

    public static void main(String[] args) throws Exception {
        try {
            System.out.println("loading " + agentLib + " lib");
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        VThreadNotifyFramePopTest obj = new VThreadNotifyFramePopTest();
        obj.runTest();
        if (!check()) {
            System.out.println("VThreadNotifyFramePopTest failed!");
            throw new RuntimeException("VThreadNotifyFramePopTest failed!");
        } else {
            System.out.println("VThreadNotifyFramePopTest passed\n");
        }
        System.out.println("\n#####   main: finished  #####\n");
    }
}
