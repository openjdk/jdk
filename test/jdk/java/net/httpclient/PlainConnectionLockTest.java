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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;

/*
 * @test
 * @bug 8372198
 * @summary Attempt to check that no deadlock occurs when
 *          connections are closed by the ConnectionPool.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.httpclient.HttpClient.log=requests,responses,headers,errors
 *              -Djdk.httpclient.connectionPoolSize=1
 *              ${test.main.class}
 */

// -Djava.security.debug=all
class PlainConnectionLockTest implements HttpServerAdapters {

    private SSLContext sslContext;
    private HttpTestServer http1Server;
    private HttpTestServer https1Server;
    private String http1URI;
    private String https1URI;
    private ExecutorService serverExecutor;
    private final Semaphore responseSemaphore = new Semaphore(0);
    private final Semaphore requestSemaphore = new Semaphore(0);
    private static final int MANY = 100;

    static {
        HttpServerAdapters.enableServerLogging();
    }

    private boolean blockResponse() {
        try {
            requestSemaphore.release();
            responseSemaphore.acquire();
            return true;
        } catch (InterruptedException x) {
            return false;
        }
    }


    @BeforeEach
    void beforeTest() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        serverExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("Http1Server", 0).factory());
        // create a h2 server
        https1Server = HttpTestServer.create(HTTP_1_1, sslContext, serverExecutor);
        https1Server.addHandler((exchange) -> {
            if (blockResponse()) {
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(500, 0);
            }
        }, "/PlainConnectionLockTest/");
        https1Server.start();
        System.out.println("HTTPS Server started at " + https1Server.getAddress());
        https1URI = "https://" + https1Server.serverAuthority() + "/PlainConnectionLockTest/https1";

        http1Server = HttpTestServer.create(HTTP_1_1, null, serverExecutor);
        http1Server.addHandler((exchange) -> {
            if (blockResponse()) {
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(500, 0);
            }
        }, "/PlainConnectionLockTest/");
        http1Server.start();
        System.out.println("HTTP Server started at " + http1Server.getAddress());
        http1URI = "http://" + http1Server.serverAuthority() + "/PlainConnectionLockTest/http1";
    }

    @AfterEach
    void afterTest() throws Exception {
        if (http1Server != null) {
            System.out.println("Stopping HTTP server " + http1Server.getAddress());
            http1Server.stop();
        }
        if (https1Server != null) {
            System.out.println("Stopping HTTPS server " + https1Server.getAddress());
            https1Server.stop();
        }
        serverExecutor.close();
    }

    @Test
    void sendManyHttpRequestsNoShutdown() throws Exception {
        sendManyRequests(http1URI, MANY, false);
    }
    @Test
    void sendManyHttpRequestsShutdownNow() throws Exception {
        sendManyRequests(http1URI, MANY, true);
    }
    @Test
    void sendManyHttpsRequestsNoShutdown() throws Exception {
        sendManyRequests(https1URI, MANY, false);
    }
    @Test
    void sendManyHttpsRequestsShutdownNow() throws Exception {
        sendManyRequests(https1URI, MANY, true);
    }

    private static void throwCause(CompletionException x) throws Exception {
        var cause = x.getCause();
        if (cause instanceof Exception ex) throw ex;
        if (cause instanceof Error err) throw err;
        throw x;
    }

    private void sendManyRequests(final String requestURI, final int many, boolean shutdown) throws Exception {
        System.out.println("\nSending %s requests to %s, shutdown=%s\n".formatted(many, requestURI, shutdown));
        System.err.println("\nSending %s requests to %s, shutdown=%s\n".formatted(many, requestURI, shutdown));
        assert many > 0;
        try (final HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .sslContext(sslContext).build()) {
            List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
            final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().version(HTTP_1_1);
            for (int i = 0; i < many; i++) {
                // GET
                final URI reqURI = new URI(requestURI + "?i=" + i);
                final HttpRequest req = reqBuilder.copy().uri(reqURI).GET().build();
                System.out.println("\nIssuing request: " + req);
                var cf = client.sendAsync(req, BodyHandlers.ofString());
                futures.add(cf);
            }
            // wait for all exchanges to be handled
            requestSemaphore.acquire(many);
            // allow one request to proceed
            responseSemaphore.release();
            try {
                // wait for the first response.
                CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).join();
                if (shutdown) {
                    client.shutdownNow();
                    client.awaitTermination(Duration.ofSeconds(1));
                }
            } finally {
                // now release the others.
                responseSemaphore.release(many - 1);
            }
            // wait for all responses
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(t -> null).join();

            // check
            Set<String> conns = new HashSet<>();
            int exceptionCount = 0;
            int success = 0;
            for (var respCF : futures) {
                try {
                    var resp = respCF.join();
                    Assertions.assertEquals(200, resp.statusCode(),
                            "unexpected response code for GET request: " + resp);
                    Assertions.assertTrue(conns.add(resp.connectionLabel().get()),
                            "unexepected reuse of connection: "
                                    + resp.connectionLabel().get() + " found in " + conns);
                    success++;
                } catch (CompletionException x) {
                    if (shutdown) exceptionCount++;
                    else throwCause(x);
                }
            }
            if (shutdown) {
                if (success == 0) {
                    throw new AssertionError(("%s: shutdownNow=%s: Expected at least one response, " +
                            "got success=%s, exceptions=%s").formatted(requestURI, shutdown, success, exceptionCount));
                }
            }
            System.out.println("Success: %s: shutdownNow:%s, success=%s, exceptions:%s"
                    .formatted(requestURI, shutdown, success, exceptionCount));
        }
    }
}
