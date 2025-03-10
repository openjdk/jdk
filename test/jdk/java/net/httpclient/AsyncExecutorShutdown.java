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
 * @bug 8277969 8299338 8349702
 * @summary Test for edge case where the executor is not accepting
 *          new tasks while the client is still running
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker
 * @run testng/othervm
 *       -Djdk.internal.httpclient.debug=true
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       AsyncExecutorShutdown
 */
// -Djdk.internal.httpclient.debug=true

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Function;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class AsyncExecutorShutdown implements HttpServerAdapters {

    static {
        HttpServerAdapters.enableServerLogging();
    }
    static final Random RANDOM = RandomFactory.getRandom();

    SSLContext sslContext;
    HttpTestServer httpTestServer;        // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    static final String MESSAGE = "AsyncExecutorShutdown message body";
    static final int ITERATIONS = 3;

    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                { httpURI,    },
                { httpsURI,   },
                { http2URI,   },
                { https2URI,  },
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

    static String readBody(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
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
    void testConcurrent(String uriString) throws Exception {
        out.printf("%n---- starting (%s) ----%n", uriString);
        ExecutorService executorService = Executors.newCachedThreadPool();
        ExecutorService readerService = Executors.newCachedThreadPool();
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .executor(executorService)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);
        assert client.executor().isPresent();

        int step = RANDOM.nextInt(ITERATIONS);
        try {
            List<CompletableFuture<String>> bodies = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                URI uri = URI.create(uriString + "/concurrent/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .build();
                out.printf("Iteration %d request: %s%n", i, request.uri());
                CompletableFuture<HttpResponse<InputStream>> responseCF;
                CompletableFuture<String> bodyCF;
                final int si = i;
                try {
                    responseCF = client.sendAsync(request, BodyHandlers.ofInputStream())
                            .thenApply((response) -> {
                                out.println(si + ":  Got response: " + response);
                                assertEquals(response.statusCode(), 200);
                                return response;
                            });
                    bodyCF = responseCF.thenApplyAsync(HttpResponse::body, readerService)
                            .thenApply(AsyncExecutorShutdown::readBody)
                            .thenApply((s) -> { assertEquals(s, MESSAGE); return s;});
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
                var cf = bodyCF.exceptionally((t) -> {
                    Throwable cause = getCause(t);
                    out.println(si + ": Got expected exception: " + cause);
                    if (UncheckedIOException.class.isAssignableFrom(cause.getClass())) {
                        if (cause.getCause() != null) {
                            out.println(si + ": Got expected exception: " + cause);
                            cause = cause.getCause();
                        }
                    }
                    checkCause(String.valueOf(si), cause);
                    return null;
                });
                bodies.add(cf);
            }
            CompletableFuture.allOf(bodies.toArray(new CompletableFuture<?>[0])).get();
        } finally {
            client = null;
            executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
            readerService.shutdown();
            readerService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        }
    }

    @Test(dataProvider = "positive")
    void testSequential(String uriString) throws Exception {
        out.printf("%n---- starting (%s) ----%n", uriString);
        ExecutorService executorService = Executors.newCachedThreadPool();
        ExecutorService readerService = Executors.newCachedThreadPool();
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
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
                            .build();
                out.printf("Iteration %d request: %s%n", i, request.uri());
                final int si = i;
                CompletableFuture<HttpResponse<InputStream>> responseCF;
                CompletableFuture<String> bodyCF;
                try {
                    responseCF = client.sendAsync(request, BodyHandlers.ofInputStream())
                            .thenApply((response) -> {
                                out.println(si + ":  Got response: " + response);
                                assertEquals(response.statusCode(), 200);
                                return response;
                            });
                    bodyCF = responseCF.thenApplyAsync(HttpResponse::body, readerService)
                            .thenApply(AsyncExecutorShutdown::readBody)
                            .thenApply((s) -> {assertEquals(s, MESSAGE); return s;})
                            .thenApply((s) -> {out.println(si + ":  Got body: " + s); return s;});
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
                bodyCF.handle((r,t) -> {
                    if (t != null) {
                        try {
                            Throwable cause = getCause(t);
                            out.println(si + ": Got expected exception: " + cause);
                            if (UncheckedIOException.class.isAssignableFrom(cause.getClass())) {
                                if (cause.getCause() != null) {
                                    out.println(si + ": Got expected exception: " + cause);
                                    cause = cause.getCause();
                                }
                            }
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
            readerService.shutdown();
            readerService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        }
    }

    // -- Infrastructure

    @BeforeTest
    public void setup() throws Exception {
        out.println("\n**** Setup ****\n");
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

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

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        Thread.sleep(100);
        AssertionError fail = TRACKER.checkShutdown(5000);
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
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
