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

/*
 * @test
 * @bug 8267140
 * @summary Test for HttpClient::shutdown. Any running operation should
 *          succeed but new operations will be rejected. The client
 *          should eventually exit.
 *          This test tests shutdown, awaitTermination, and
 *          isTerminated.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker
 * @run testng/othervm
 *       -Djdk.internal.httpclient.debug=true
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       HttpClientShutdown
 */
// -Djdk.internal.httpclient.debug=true

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
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

import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class HttpClientShutdown implements HttpServerAdapters {

    static {
        HttpServerAdapters.enableServerLogging();
    }
    static final Random RANDOM = RandomFactory.getRandom();

    ExecutorService readerService;
    SSLContext sslContext;
    HttpTestServer httpTestServer;        // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;       // HTTPS/1.1
    HttpTestServer http2TestServer;       // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;      // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    static final String MESSAGE = "HttpClientShutdown message body";
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
    static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    static volatile long start = System.nanoTime();

    static final String now() {
        var duration = Duration.ofNanos(System.nanoTime() - start);
        var secs = duration.toSeconds();
        var ms = duration.toMillisPart();
        if (secs > 0) {
            return String.format("[%ss %sms] ", secs, ms);
        } else {
            return String.format("[%sms] ", ms);
        }
    }

    static Throwable getCause(Throwable t) {
        while (t instanceof CompletionException || t instanceof ExecutionException) {
            t = t.getCause();
        }
        return t;
    }

    static String readBody(InputStream body) {
        try (InputStream in = body) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    private static record CancellingSubscriber<U>(ExchangeResult<?> result)
            implements Subscriber<U> {
        @Override
        public void onSubscribe(Subscription subscription) {
            out.printf(now() + "%s:  cancelling subscription", result.step());
            subscription.cancel();
        }
        @Override
        public void onNext(U item) {}
        @Override
        public void onError(Throwable throwable) {}
        @Override
        public void onComplete() {}
    }

    private static <U> void ensureClosed(ExchangeResult<U> result) {
        var response = result.response;
        if (response == null) return;
        var body = response.body();
        try {
            if (body instanceof Closeable cl) {
                cl.close();
            } else if (body instanceof Publisher<?> pub) {
                pub.subscribe(new CancellingSubscriber<Object>(result));
            }
        } catch (IOException io) {
            out.printf(now() + "%s:  Failed to close body: %s", result.step(), io);
            io.printStackTrace(out);
        }
    }

    private record ExchangeResult<T>(int step, HttpResponse<T> response) {
        public static <U> ExchangeResult<U> ofStep(int step) {
            return new ExchangeResult<U>(step, null);
        }
        ExchangeResult<T> withResponse(HttpResponse<T> response) {
            return new ExchangeResult(step, response);
        }
        ExchangeResult<T> assertResponseState() {
            try {
                out.println(now() + step + ":  Got response: " + response);
                assertEquals(response.statusCode(), 200);
            } catch (AssertionError error) {
                out.printf(now() + "%s:  Closing body due to assertion - %s", step, error);
                ensureClosed(this);
                throw error;
            }
            return this;
        }
    }

    static boolean hasExpectedMessage(IOException io) {
        String message = io.getMessage();
        if (message == null) return false;
        // exception from sendAsync()
        if (message.equals("closed")) return true;
        return false;
    }

    static void checkCause(String what, Throwable cause) {
        Throwable t = cause;
        Throwable accepted = null;
        while (t != null) {
            out.println(now() + what + ": checking " + t);
            if (t instanceof IOException io && hasExpectedMessage(io)) {
                out.println(now() + what + ": Got expected message in cause: " + io);
                return;
            } else if (t instanceof ClosedChannelException) {
                out.println(now() + what + ": Accepting ClosedChannelException as a valid cause: " + t);
                accepted = t;
            }
            t = t.getCause();
        }
        if (accepted != null) {
            out.println(now() + what + ": Didn't find expected closed, " +
                    "but accepting " + accepted.getClass().getSimpleName()
                    + " as a valid cause: " + accepted);
            return;
        }
        throw new AssertionError(what + ": Unexpected exception: " + cause, cause);
    }

    @Test(dataProvider = "positive")
    void testConcurrent(String uriString) throws Exception {
        out.printf("%n---- %sstarting concurrent (%s) ----%n%n", now(), uriString);
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);

        int step = RANDOM.nextInt(ITERATIONS);
        Throwable failed = null;
        List<CompletableFuture<String>> bodies = new ArrayList<>();
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                URI uri = URI.create(uriString + "/concurrent/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .build();
                out.printf(now() + "Iteration %d request: %s%n", i, request.uri());
                CompletableFuture<HttpResponse<InputStream>> responseCF;
                CompletableFuture<String> bodyCF;
                final int si = i;
                ExchangeResult<InputStream> result = ExchangeResult.ofStep(si);
                responseCF = client.sendAsync(request, BodyHandlers.ofInputStream())
                        .thenApply(result::withResponse)
                        .thenApplyAsync(ExchangeResult::assertResponseState, readerService)
                        .thenApply(ExchangeResult::response);
                bodyCF = responseCF.thenApplyAsync(HttpResponse::body, readerService)
                        .thenApply(HttpClientShutdown::readBody)
                        .thenApply((s) -> {
                            assertEquals(s, MESSAGE);
                            out.println(now() + si +": Got expected message: " + s);
                            return s;
                        });
                long sleep = RANDOM.nextLong(5);
                if (sleep > 0) {
                    out.printf(now() + "%d: sleeping %d ms%n", i, sleep);
                    Thread.sleep(sleep);
                }
                if (i < step) {
                    bodies.add(bodyCF);
                    continue;
                }
                if (i == step) {
                    out.printf(now() + "%d: shutting down client%n", i);
                    client.shutdown();
                }
                var cf = bodyCF.exceptionally((t) -> {
                    Throwable cause = getCause(t);
                    if (UncheckedIOException.class.isInstance(cause)) {
                        if (cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                    }
                    out.println(now() + si + ": Got expected exception: " + cause);
                    checkCause(String.valueOf(si), cause);
                    return null;
                });
                bodies.add(cf);
            }
        } catch (Throwable throwable) {
            failed = throwable;
        } finally {
            failed = cleanup(client, failed);
        }
        if (failed instanceof Exception ex) throw ex;
        if (failed instanceof Error e) throw e;
        assertTrue(client.isTerminated());
        // ensure all tasks have been successfully completed
        CompletableFuture.allOf(bodies.toArray(new CompletableFuture<?>[0])).get();
    }

    static Throwable cleanup(HttpClient client, Throwable failed) {
        try {
            out.println(now() + "awaiting termination...");
            if (client.awaitTermination(Duration.ofMinutes(3))) {
                out.println(now() + "Client terminated within expected delay");
            } else {
                String msg = "Client %s still running: %s".formatted(
                        client,
                        TRACKER.diagnose(client));
                out.println(now() + msg);
                AssertionError error = new AssertionError(msg);
                if (failed != null) {
                    failed.addSuppressed(error);
                } else failed = error;
            }
        } catch (InterruptedException ie) {
            if (failed != null) {
                failed.addSuppressed(ie);
            } else failed = ie;
        }
        return failed;
    }

    @Test(dataProvider = "positive")
    void testSequential(String uriString) throws Exception {
        out.printf("%n---- %sstarting sequential (%s) ----%n%n", now(), uriString);
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .sslContext(sslContext)
                .build();
        TRACKER.track(client);

        int step = RANDOM.nextInt(ITERATIONS);
        out.printf(now() + "will shutdown client in step %d%n", step);
        Throwable failed = null;
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                URI uri = URI.create(uriString + "/sequential/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .build();
                out.printf(now() + "Iteration %d request: %s%n", i, request.uri());
                final int si = i;
                CompletableFuture<HttpResponse<InputStream>> responseCF;
                CompletableFuture<String> bodyCF;
                ExchangeResult<InputStream> result = ExchangeResult.ofStep(si);
                responseCF = client.sendAsync(request, BodyHandlers.ofInputStream())
                        .thenApply(result::withResponse)
                        .thenApplyAsync(ExchangeResult::assertResponseState, readerService)
                        .thenApply(ExchangeResult::response);
                bodyCF = responseCF.thenApplyAsync(HttpResponse::body, readerService)
                        .thenApply(HttpClientShutdown::readBody)
                        .thenApply((s) -> {
                            assertEquals(s, MESSAGE);
                            return s;
                        })
                        .thenApply((s) -> {
                            out.println(now() + si + ":  Got body: " + s);
                            return s;
                        });
                long sleep = RANDOM.nextLong(5);
                if (sleep > 0) {
                    out.printf(now() + "%d: sleeping %d ms%n", i, sleep);
                    Thread.sleep(sleep);
                }
                if (i < step) {
                    bodyCF.get();
                    continue;
                }
                if (i == step) {
                    out.printf(now() + "%d: shutting down client%n", i);
                    client.shutdown();
                }
                bodyCF.handle((r, t) -> {
                    if (t != null) {
                        try {
                            Throwable cause = getCause(t);
                            if (UncheckedIOException.class.isInstance(cause)) {
                                if (cause.getCause() != null) {
                                    cause = cause.getCause();
                                }
                            }
                            out.println(now() + si + ": Got expected exception: " + cause);
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
        } catch (Throwable throwable) {
            failed = throwable;
        } finally {
            failed = cleanup(client, failed);
        }
        if (failed instanceof Exception ex) throw ex;
        if (failed instanceof Error e) throw e;
        assertTrue(client.isTerminated());
    }

    // -- Infrastructure

    @BeforeTest
    public void setup() throws Exception {
        out.println("\n**** Setup ****\n");
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");
        readerService = Executors.newCachedThreadPool();

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
        start = System.nanoTime();
    }

    @AfterTest
    public void teardown() throws Exception {
        Thread.sleep(100);
        AssertionError fail = TRACKER.checkShutdown(5000);
        try {
            shutdown(readerService);
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
        } finally {
            if (fail != null) throw fail;
        }
    }

    static void shutdown(ExecutorService executorService) {
        try {
            executorService.shutdown();
            executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
        }
    }

    static class ServerRequestHandler implements HttpTestHandler {
        ConcurrentHashMap<String,String> closedRequests = new ConcurrentHashMap<>();

        @java.lang.Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println(now() + "ServerRequestHandler for: " + t.getRequestURI());

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
                    out.println(now() + "Server will close connection, client will retry: "
                            + t.getRequestURI().toString());
                    throw new IOException("Closing on first request");
                }
            }

            long previous;
            long begin = previous = System.nanoTime();
            // not retrying
            readAllRequestData(t);
            try (OutputStream os = t.getResponseBody()) {
                byte[] bytes = MESSAGE.getBytes(UTF_8);
                t.sendResponseHeaders(200, bytes.length);
                out.println(now() + "Start sending body for: " + t.getRequestURI());
                for (int i=0; i<bytes.length; i++) {
                    long now = System.nanoTime();
                    long sincePrevious = Duration.ofNanos(now - previous).toMillis();
                    long sinceBegin = Duration.ofNanos(now - begin).toMillis();
                    if (i > 0 && (sincePrevious > 25)) {
                        previous = now;
                        out.printf("%s%s/%s bytes sent in %sms for: %s%n", now(),
                                i, bytes.length, sinceBegin, t.getRequestURI());
                    }
                    os.write(bytes, i, 1);
                    os.flush();
                    try {
                        Thread.sleep(RANDOM.nextInt(5));
                    } catch (InterruptedException x) { }
                }
                out.println(now() + "Body sent (" + bytes.length + " bytes) for: " + t.getRequestURI());
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
