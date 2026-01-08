/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277969
 * @summary Test for edge case where the executor is not accepting
 *          new tasks while the client is still running
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker
 * @run testng/othervm
 *       -Djdk.internal.httpclient.debug=true
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       ExecutorShutdown
 */
// -Djdk.internal.httpclient.debug=true

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import javax.net.ssl.SSLContext;

import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class ExecutorShutdown implements HttpServerAdapters {

    static {
        HttpServerAdapters.enableServerLogging();
    }
    static final Random RANDOM = RandomFactory.getRandom();

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    HttpTestServer httpTestServer;        // HTTP/1.1    [ 6 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c   )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2    )
    HttpTestServer h2h3TestServer;        // HTTP/2 ( h2+h3 )
    HttpTestServer h3TestServer;          // HTTP/2 ( h3    )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String h2h3URI;
    String h3URI;
    String h2h3Head;

    static final String MESSAGE = "ExecutorShutdown message body";
    static final int ITERATIONS = 3;

    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                { h2h3URI,   HTTP_3,   h2h3TestServer.h3DiscoveryConfig() },
                { h3URI,     HTTP_3,   h3TestServer.h3DiscoveryConfig() },
                { httpURI,   HTTP_1_1, null },
                { httpsURI,  HTTP_1_1, null },
                { http2URI,  HTTP_2,   null },
                { https2URI, HTTP_2,   null },
        };
    }

    static final AtomicLong requestCounter = new AtomicLong();
    final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    static Throwable getCause(Throwable t) {
        while (t instanceof CompletionException || t instanceof ExecutionException) {
            t = t.getCause();
        }
        return t;
    }

    static void checkCause(String what, Throwable cause) {
        Throwable t = cause;
        Throwable accepted = null;
        while (t != null) {
            out.println(what + ": checking " + t);
            if (t instanceof  RejectedExecutionException) {
                out.println(what + ": Got expected RejectedExecutionException in cause: " + t);
                return;
            } else if (t instanceof ClosedChannelException) {
                out.println(what + ": Accepting ClosedChannelException as a valid cause: " + t);
                accepted = t;
            } else if (t instanceof IOException io) {
                var msg = io.getMessage();
                // Stream 0 cancelled should also be accepted
                if (msg != null && msg.matches("Stream (0|([1-9][0-9]*)) cancelled")) {
                    out.println(what + ": Accepting Stream cancelled as a valid cause: " + io);
                    accepted = t;
                }
            }
            t = t.getCause();
        }
        if (accepted != null) {
            out.println(what + ": Didn't find expected RejectedExecutionException, " +
                    "but accepting " + accepted.getClass().getSimpleName()
                    + " as a valid cause: " + accepted);
            return;
        }
        throw new AssertionError(what + ": Unexpected exception: " + cause, cause);
    }

    @Test(dataProvider = "positive")
    void testConcurrent(String uriString, Version version, Http3DiscoveryMode config) throws Exception {
        out.printf("%n---- starting (%s) ----%n", uriString);
        ExecutorService executorService = Executors.newCachedThreadPool();
        HttpClient client = newClientBuilderForH3()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .version(version == HTTP_1_1 ? HTTP_2 : version)
                .executor(executorService)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);
        assert client.executor().isPresent();

        int step = RANDOM.nextInt(ITERATIONS);
        int head = Math.min(1, step);
        List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                if (i == head && version == HTTP_3 && config != HTTP_3_URI_ONLY) {
                    // let's the first request go through whatever version,
                    // but ensure that the second will find an AltService
                    // record
                    headRequest(client);
                }
                URI uri = URI.create(uriString + "/concurrent/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .setOption(H3_DISCOVERY, config)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .build();
                out.printf("Iteration %d request: %s%n", i, request.uri());
                CompletableFuture<HttpResponse<String>> responseCF;
                try {
                    responseCF = client.sendAsync(request, BodyHandlers.ofString());
                } catch (RejectedExecutionException x) {
                    out.println(i + ": Got expected exception: " + x);
                    continue;
                }
                long sleep = RANDOM.nextLong(5);
                if (sleep > 0) {
                    out.printf("%d: sleeping %d ms%n", i, sleep);
                    Thread.sleep(sleep);
                }
                if (i == step) {
                    out.printf("%d: shutting down executor now%n", i, sleep);
                    executorService.shutdownNow();
                }
                final int si = i;
                var cf = responseCF.thenApply((response) -> {
                    out.println(si + ":  Got response: " + response);
                    out.println(si + ":  Got body Path: " + response.body());
                    assertEquals(response.statusCode(), 200);
                    if (si >= head) assertEquals(response.version(), version);
                    assertEquals(response.body(), MESSAGE);
                    return response;
                }).exceptionally((t) -> {
                    Throwable cause = getCause(t);
                    out.println(si + ": Got expected exception: " + cause);
                    checkCause(String.valueOf(si), cause);
                    return null;
                });
                responses.add(cf);
            }
            CompletableFuture.allOf(responses.toArray(new CompletableFuture<?>[0])).get();
        } finally {
            client = null;
            executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        }
    }

    @Test(dataProvider = "positive")
    void testSequential(String uriString, Version version, Http3DiscoveryMode config) throws Exception {
        out.printf("%n---- starting (%s, %s, %s) ----%n%n", uriString, version, config);
        ExecutorService executorService = Executors.newCachedThreadPool();
        HttpClient client = newClientBuilderForH3()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .version(version == HTTP_1_1 ? HTTP_2 : version)
                .executor(executorService)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);
        assert client.executor().isPresent();

        int step = RANDOM.nextInt(ITERATIONS);
        out.printf("will shutdown executor in step %d%n", step);
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                URI uri = URI.create(uriString + "/sequential/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .setOption(H3_DISCOVERY, config)
                        .build();
                out.printf("Iteration %d request: %s%n", i, request.uri());
                CompletableFuture<HttpResponse<String>> responseCF;
                try {
                    responseCF = client.sendAsync(request, BodyHandlers.ofString());
                } catch (RejectedExecutionException x) {
                    out.println(i + ": Got expected exception: " + x);
                    continue;
                }
                long sleep = RANDOM.nextLong(5);
                if (sleep > 0) {
                    out.printf("%d: sleeping %d ms%n", i, sleep);
                    Thread.sleep(sleep);
                }
                if (i == step) {
                    out.printf("%d: shutting down executor now%n", i, sleep);
                    executorService.shutdownNow();
                }
                final int si = i;
                responseCF.thenApply((response) -> {
                    out.println(si + ":  Got response: " + response);
                    out.println(si + ":  Got body Path: " + response.body());
                    assertEquals(response.statusCode(), 200);
                    if (si > 0) assertEquals(response.version(), version);
                    assertEquals(response.body(), MESSAGE);
                    return response;
                }).handle((r,t) -> {
                    if (t != null) {
                        try {
                            Throwable cause = getCause(t);
                            out.println(si + ": Got expected exception: " + cause);
                            checkCause(String.valueOf(si), cause);
                         } catch (Throwable ase) {
                            return CompletableFuture.failedFuture(ase);
                        }
                        return CompletableFuture.completedFuture(null);
                    } else {
                        return CompletableFuture.completedFuture(r);
                    }
                }).thenCompose((c) -> c).get();
            }
       } finally {
            client = null;
            executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        }
    }

    // -- Infrastructure

    void headRequest(HttpClient client) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(h2h3Head))
                .version(HTTP_2)
                .HEAD()
                .build();
        var resp = client.send(request, BodyHandlers.discarding());
        assertEquals(resp.statusCode(), 200);
    }

    @BeforeTest
    public void setup() throws Exception {
        out.println("\n**** Setup ****\n");
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new ServerRequestHandler(), "/http1/exec/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/exec/retry";
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new ServerRequestHandler(),"/https1/exec/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/exec/retry";

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new ServerRequestHandler(), "/http2/exec/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/exec/retry";
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new ServerRequestHandler(), "/https2/exec/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/exec/retry";

        h2h3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        h2h3TestServer.addHandler(new ServerRequestHandler(), "/h2h3/exec/");
        h2h3URI = "https://" + h2h3TestServer.serverAuthority() + "/h2h3/exec/retry";
        h2h3TestServer.addHandler(new HttpHeadOrGetHandler(), "/h2h3/head/");
        h2h3Head = "https://" + h2h3TestServer.serverAuthority() + "/h2h3/head/";
        h3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3TestServer.addHandler(new ServerRequestHandler(), "/h3-only/exec/");
        h3URI = "https://" + h3TestServer.serverAuthority() + "/h3-only/exec/retry";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        h2h3TestServer.start();
        h3TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(5000);
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            h2h3TestServer.stop();
            h3TestServer.stop();
        } finally {
            if (fail != null) throw fail;
        }
    }

    static class ServerRequestHandler implements HttpTestHandler {
        ConcurrentHashMap<String,String> closedRequests = new ConcurrentHashMap<>();

        @java.lang.Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("ServerRequestHandler for: " + t.getRequestURI());

            List<String> uuids = t.getRequestHeaders().get("X-uuid");
            if (uuids == null || uuids.size() != 1) {
                readAllRequestData(t);
                try (OutputStream os = t.getResponseBody()) {
                    String msg = "Incorrect uuid header values:[" + uuids + "]";
                    (new RuntimeException(msg)).printStackTrace();
                    t.sendResponseHeaders(500, -1);
                    os.write(msg.getBytes(UTF_8));
                }
                return;
            }

            String uuid = uuids.get(0);
            // retrying
            if (closedRequests.putIfAbsent(uuid, t.getRequestURI().toString()) == null) {
                if (t.getExchangeVersion() == HTTP_1_1) {
                    // Throwing an exception here only causes a retry
                    // with HTTP_1_1 - where it forces the server to close
                    // the connection.
                    // For HTTP/2 then throwing an IOE would cause the server
                    // to close the stream, and throwing anything else would
                    // cause it to close the connection, but neither would
                    // cause the client to retry.
                    // So we simply do not try to retry with HTTP/2.
                    out.println("Server will close connection, client will retry: "
                            + t.getRequestURI().toString());
                    throw new IOException("Closing on first request");
                }
            }

            // not retrying
            readAllRequestData(t);
            try (OutputStream os = t.getResponseBody()) {
                byte[] bytes = MESSAGE.getBytes(UTF_8);
                t.sendResponseHeaders(200, bytes.length);
                for (int i=0; i<bytes.length; i++) {
                    os.write(bytes, i, 1);
                    os.flush();
                    try {
                        Thread.sleep(RANDOM.nextInt(5));
                    } catch (InterruptedException x) { }
                }
            }

            closedRequests.remove(uuid);
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}
