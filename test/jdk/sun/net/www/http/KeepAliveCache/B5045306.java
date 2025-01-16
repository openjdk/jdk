/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5045306 6356004 6993490 8255124
 * @summary Http keep-alive implementation is not efficient
 * @library /test/lib
 * @run main/othervm B5045306
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import jdk.test.lib.net.URIBuilder;

/* Part 1:
 * The http client makes a connection to a URL whose content contains a lot of
 * data, more than can fit in the socket buffer. The client only reads
 * 1 byte of the data from the InputStream leaving behind more data than can
 * fit in the socket buffer. The client then makes a second call to the http
 * server. If the connection port used by the client is the same as for the
 * first call then that means that the connection is being reused.
 *
 * Part 2:
 * Test buggy webserver that sends less data than it specifies in its
 * Content-length header.
 */

public class B5045306 {
    static HttpServer server;
    static ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void startHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10, "/", new SimpleHttpTransactionHandler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.setExecutor(executor);
        server.start();
        System.out.println("http server listens on: " + server.getAddress());
    }

    public static void stopHttpServer() {
        server.stop(1);
        executor.shutdown();
    }

    public static void clientHttpCalls() throws Exception {
        List<Throwable> uncaught = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            uncaught.add(ex);
        });

        URL bigDataURL = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/firstCall")
                .toURL();

        URL smallDataURL = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/secondCall")
                .toURL();

        HttpURLConnection uc = (HttpURLConnection)bigDataURL.openConnection(Proxy.NO_PROXY);

        // Only read 1 byte of response data and close the stream
        try (InputStream is = uc.getInputStream()) {
            byte[] ba = new byte[1];
            is.read(ba);
        }

        // Allow the KeepAliveStreamCleaner thread to read the data left behind and cache the connection.
        try { Thread.sleep(2000); } catch (Exception e) {}

        uc = (HttpURLConnection)smallDataURL.openConnection(Proxy.NO_PROXY);
        uc.getResponseCode();

        if (SimpleHttpTransactionHandler.failed)
            throw new RuntimeException("Failed: Initial Keep Alive Connection is not being reused");

        // Part 2
        URL part2Url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/part2")
                .toURL();

        uc = (HttpURLConnection)part2Url.openConnection(Proxy.NO_PROXY);
        try (InputStream is = uc.getInputStream()) {}

        // Allow the KeepAliveStreamCleaner thread to try and read the data left behind and cache the connection.
        try { Thread.sleep(2000); } catch (Exception e) {}

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (threadMXBean.isThreadCpuTimeSupported()) {
            long[] threads = threadMXBean.getAllThreadIds();
            ThreadInfo[] threadInfo = threadMXBean.getThreadInfo(threads);
            for (int i = 0; i < threadInfo.length; i++) {
                if (threadInfo[i].getThreadName().equals("Keep-Alive-SocketCleaner")) {
                    System.out.println("Found Keep-Alive-SocketCleaner thread");
                    long threadID = threadInfo[i].getThreadId();
                    long before = threadMXBean.getThreadCpuTime(threadID);
                    try { Thread.sleep(2000); } catch (Exception e) {}
                    long after = threadMXBean.getThreadCpuTime(threadID);

                    if (before ==-1 || after == -1)
                        break;  // thread has died, OK

                    // if Keep-Alive-SocketCleaner consumes more than 50% of cpu then we
                    // can assume a recursive loop.
                    long total = after - before;
                    if (total >= 1000000000)  // 1 second, or 1 billion nanoseconds
                        throw new RuntimeException("Failed: possible recursive loop in Keep-Alive-SocketCleaner");
                }
            }
        }
        if (!uncaught.isEmpty()) {
            throw new RuntimeException("Unhandled exception:", uncaught.get(0));
        }
    }

    static class SimpleHttpTransactionHandler implements HttpHandler {
        static volatile boolean failed = false;

        // Need to have enough data here that is too large for the socket buffer to hold.
        // Also http.KeepAlive.remainingData must be greater than this value, default is 256K.
        static final int RESPONSE_DATA_LENGTH = 128 * 1024;

        int port1;

        public void handle(HttpExchange trans) {
            try {
                String path = trans.getRequestURI().getPath();
                if (path.equals("/firstCall")) {
                    port1 = trans.getRemoteAddress().getPort();
                    System.out.println("First connection on client port = " + port1);

                    byte[] responseBody = new byte[RESPONSE_DATA_LENGTH];
                    for (int i=0; i<responseBody.length; i++)
                        responseBody[i] = 0x41;
                    trans.sendResponseHeaders(200, responseBody.length);
                    try (OutputStream os = trans.getResponseBody()) {
                        os.write(responseBody);
                    }
                } else if (path.equals("/secondCall")) {
                    int port2 = trans.getRemoteAddress().getPort();
                    System.out.println("Second connection on client port = " + port2);

                    if (port1 != port2)
                        failed = true;

                     /* Force the server to not respond for more that the timeout
                      * set by the keepalive cleaner (5000 millis). This ensures the
                      * timeout is correctly resets the default read timeout,
                      * infinity. See 6993490. */
                    System.out.println("server sleeping...");
                    try {Thread.sleep(6000); } catch (InterruptedException e) {}
                    trans.sendResponseHeaders(200, -1);
                } else if (path.equals("/part2")) {
                    System.out.println("Call to /part2");
                    byte[] responseBody = new byte[RESPONSE_DATA_LENGTH];
                    for (int i=0; i<responseBody.length; i++)
                        responseBody[i] = 0x41;
                    // override the Content-length header to be greater than the actual response body
                    trans.sendResponseHeaders(200, responseBody.length+1);
                    OutputStream os = trans.getResponseBody();
                    os.write(responseBody);
                    // now close the socket
                    // closing the stream here would throw; close the exchange instead
                    trans.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                failed = true;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        startHttpServer();
        try {
            clientHttpCalls();
        } finally {
            stopHttpServer();
        }
    }
}
