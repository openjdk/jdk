/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpServer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;

/*
 * @test
 * @summary Tests HttpClient usage when configured with a local address to bind to
 *          when sending requests
 * @bug 8209137
 * @modules jdk.httpserver
 *
 * @run testng/othervm
 *      -Djdk.httpclient.HttpClient.log=frames,ssl,requests,responses,errors
 *      -Djdk.internal.httpclient.debug=true
 *      -Djava.net.preferIPv6Addresses=true
 *      HttpClientLocalAddrTest
 *
 * @run testng/othervm
 *      -Djdk.httpclient.HttpClient.log=frames,ssl,requests,responses,errors
 *      -Djdk.internal.httpclient.debug=true
 *      -Djava.net.preferIPv4Stack=true
 *      HttpClientLocalAddrTest
 *
 */
public class HttpClientLocalAddrTest {

    private static HttpServer server;
    private static URI rootURI;

    @BeforeClass
    public static void beforeClass() throws Exception {
        // create the server
        var serverAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        int backlog = 0;
        server = HttpServer.create(serverAddr, backlog);
        server.createContext("/", (exchange) -> {
            // the handler receives a request and sends back a 200 response with the
            // response body containing the hostname/ip of the client from whom
            // the request was received
            var clientAddr = exchange.getRemoteAddress();
            System.out.println("Received a request from client address " + clientAddr);
            var responseContent = clientAddr.getHostString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseContent.length);
            try (var os = exchange.getResponseBody()) {
                // write out the client address as a response
                os.write(responseContent);
            }
            exchange.close();
        });
        server.start();
        System.out.println("Started server at " + server.getAddress());
        var boundAddr = server.getAddress();
        rootURI = new URI("http://" + boundAddr.getHostName() + ":" + boundAddr.getPort() + "/");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        int maxTimeToWait = 0;
        if (server != null) {
            server.stop(maxTimeToWait);
        }
    }

    @DataProvider(name = "httpClients")
    private Object[][] httpClients() throws Exception {
        List<Object[]> clients = new ArrayList();
        for (HttpClient.Version version : new HttpClient.Version[]{HTTP_1_1, HTTP_2}) {
            var builder = HttpClient.newBuilder().version(version);
            // don't let proxies interfere with the client addresses received on the
            // HTTP request, by the server side handler used in this test.
            builder.proxy(HttpClient.Builder.NO_PROXY);
            // no localAddr set
            clients.add(new Object[]{builder.build(), null});
            // null localAddr set
            clients.add(new Object[]{builder.localAddress(null).build(), null});
            // localAddr set to loopback address
            var loopbackAddr = InetAddress.getLoopbackAddress();
            clients.add(new Object[]{builder.localAddress(new InetSocketAddress(loopbackAddr, 0)).build(), loopbackAddr});
            // anyAddress
            if (Boolean.getBoolean("java.net.preferIPv6Addresses")) {
                // ipv6 wildcard
                var localAddr = InetAddress.getByName("::1");
                clients.add(new Object[]{builder.localAddress(new InetSocketAddress(localAddr,0)).build(), localAddr});
            } else {
                // ipv4 wildcard
                var localAddr = InetAddress.getByName("0.0.0.0");
                clients.add(new Object[]{builder.localAddress(new InetSocketAddress(localAddr, 0)).build(), localAddr});
            }
        }
        return clients.stream().toArray(Object[][]::new);
    }

    /**
     * Sends a GET request using the {@code client} and expects a 200 response.
     * The returned response body is then tested to see if the client address
     * seen by the server side handler is the same one as that is set on the
     * {@code client}
     */
    @Test(dataProvider = "httpClients")
    public void testSend(HttpClient client, InetAddress localAddress) throws Exception {
        System.out.println("Testing using a HTTP client with local address " + localAddress);
        // GET request
        var req = HttpRequest.newBuilder(rootURI).build();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(resp.statusCode(), 200, "Unexpected status code");
        // verify the address only if a specific one was set on the client
        if (localAddress != null && !localAddress.isAnyLocalAddress()) {
            Assert.assertEquals(resp.body(), localAddress.getHostAddress(),
                    "Unexpected client address seen by the server handler");
        }
    }

    /**
     * Sends a GET request using the {@code sendAsync} method on the {@code client} and
     * expects a 200 response. The returned response body is then tested to see if the client address
     * seen by the server side handler is the same one as that is set on the
     * {@code client}
     */
    @Test(dataProvider = "httpClients")
    public void testSendAsync(HttpClient client, InetAddress localAddress) throws Exception {
        System.out.println("Testing using a HTTP client with local address " + localAddress);
        // GET request
        var req = HttpRequest.newBuilder(rootURI).build();
        var cf = client.sendAsync(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var resp = cf.get();
        Assert.assertEquals(resp.statusCode(), 200, "Unexpected status code");
        // verify the address only if a specific one was set on the client
        if (localAddress != null && !localAddress.isAnyLocalAddress()) {
            Assert.assertEquals(resp.body(), localAddress.getHostAddress(),
                    "Unexpected client address seen by the server handler");
        }
    }

    /**
     * Invokes the {@link #testSend(HttpClient)} and {@link #testSendAsync(HttpClient)}
     * tests, concurrently in multiple threads to verify that the correct local address
     * is used when multiple concurrent threads are involved in sending requests from
     * the {@code client}
     */
    @Test(dataProvider = "httpClients")
    public void testMultiSendRequests(HttpClient client, InetAddress localAddress) throws Exception {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Void>> taskResults = new ArrayList<>();
        try {
            for (int i = 0; i < numThreads; i++) {
                final var currentIdx = i;
                var f = executor.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        // test some for send and some for sendAsync
                        if (currentIdx % 2 == 0) {
                            testSend(client, localAddress);
                        } else {
                            testSendAsync(client, localAddress);
                        }
                        return null;
                    }
                });
                taskResults.add(f);
            }
            // wait for results
            for (var r : taskResults) {
                r.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

