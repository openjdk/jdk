/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.OperationTrackers.Tracker;
import jdk.test.lib.net.SimpleSSLContext;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

/*
 * @test
 * @bug 8156710
 * @summary Check if HttpTimeoutException is thrown if a server doesn't reply
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.TestUtil
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @compile ../ReferenceTracker.java
 * @run main/othervm -Djdk.httpclient.HttpClient.log=ssl,requests,responses,errors H3Timeout
 */
public class H3Timeout implements HttpServerAdapters {

    private static final int TIMEOUT = 2 * 1000; // in millis
    private static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    public static void main(String[] args) throws Exception {
        SSLContext context = SimpleSSLContext.findSSLContext();
        testConnect(context, false);
        testConnect(context, true);
        testTimeout(context, false);
        testTimeout(context, true);
    }

    public static void testConnect(SSLContext context, boolean async) throws Exception {

        InetSocketAddress loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        try (DatagramSocket socket = new DatagramSocket(loopback)) {
            String address = socket.getLocalAddress().getHostAddress();
            if (address.indexOf(':') >= 0) {
                if (!address.startsWith("[") || !address.endsWith("]")) {
                    address = "[" + address + "]";
                }
            }
            String serverAuth = address + ":" + socket.getLocalPort();
            String uri = "https://" + serverAuth + "/";
            HttpTimeoutException expected;
            if (async) {
                System.out.println(uri + ": Trying to connect asynchronously");
                expected = connectAsync(context, uri);
            } else {
                System.out.println(uri + ": Trying to connect synchronously");
                expected = connect(context, uri);
            }
            if (!(expected instanceof HttpConnectTimeoutException)) {
                throw new AssertionError("expected HttpConnectTimeoutException, got: "
                        + expected, expected);
            }
        }
    }

    public static void testTimeout(SSLContext context, boolean async) throws Exception {

        CountDownLatch latch = new CountDownLatch(1);
        HttpTestServer server = HttpTestServer.create(HTTP_3_URI_ONLY, context);
        server.addHandler((exch) -> {
            try {
                System.err.println("server reading request");
                byte[] req = exch.getRequestBody().readAllBytes();
                System.err.printf("server got request: %s bytes", req.length);
                latch.await();
                exch.sendResponseHeaders(500, 0);
            } catch (Exception e) {
                System.err.println("server exception: " + e);
            }
        }, "/");
        server.start();
        try  {
            String serverAuth = server.serverAuthority();
            String uri = "https://" + serverAuth + "/";
            HttpTimeoutException expected;
            if (async) {
                System.out.println(uri + ": Trying to connect asynchronously");
                expected = connectAsync(context, uri);
            } else {
                System.out.println(uri + ": Trying to connect synchronously");
                expected = connect(context, uri);
            }
            assert expected instanceof HttpTimeoutException;
        } finally {
            latch.countDown();
            server.stop();
        }
    }

    private static HttpTimeoutException connect(SSLContext context, String server) throws Exception {
        HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                .version(HTTP_3)
                .sslContext(context)
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI(server))
                    .timeout(Duration.ofMillis(TIMEOUT))
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .POST(BodyPublishers.ofString("body"))
                    .build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            System.out.println("Received unexpected reply: " + response.statusCode());
            throw new RuntimeException("unexpected successful connection");
        } catch (HttpTimeoutException e) {
            System.out.println("expected exception: " + e);
            return e;
        } finally {
            client.shutdown();
            if (!client.awaitTermination(Duration.ofSeconds(5))) {
                Tracker tracker = TRACKER.getTracker(client);
                client = null;
                System.gc();
                AssertionError error = TRACKER.check(tracker, 5000);
                if (error != null) throw error;
            }
        }
    }

    private static HttpTimeoutException connectAsync(SSLContext context, String server) throws Exception {
        try (HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                    .version(HTTP_3)
                    .sslContext(context)
                    .build()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(new URI(server))
                        .timeout(Duration.ofMillis(TIMEOUT))
                        .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                        .POST(BodyPublishers.ofString("body"))
                        .build();
                HttpResponse<String> response = client.sendAsync(request, BodyHandlers.ofString()).join();
                System.out.println("Received unexpected reply: " + response.statusCode());
                throw new RuntimeException("unexpected successful connection");
            } catch (CompletionException e) {
                var cause = e.getCause();
                if (cause instanceof HttpTimeoutException timeout) {
                    System.out.println("expected exception: " + e.getCause());
                    return timeout;
                } else {
                    throw new RuntimeException("Unexpected exception received: " + e.getCause(), e);
                }
            }
        }
    }

}
