/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8245462 8229822 8254786 8297075 8297149 8298340 8302635
 * @summary Tests cancelling the request.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @key randomness
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker CancelRequestTest
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.enableAllMethodRetry=true
 *                     CancelRequestTest
 */
// *                     -Dseed=3582896013206826205L
// *                     -Dseed=5784221742235559231L
import jdk.internal.net.http.common.OperationTrackers.Tracker;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.out;
import static java.lang.System.err;
import static java.net.http.HttpClient.Version.*;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class CancelRequestTest implements HttpServerAdapters {

    private static final Random random = RandomFactory.getRandom();
    private static final ConcurrentHashMap<String, CountDownLatch> latches
            = new ConcurrentHashMap<>();

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    HttpTestServer httpTestServer;    // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    HttpTestServer h2h3TestServer;        // HTTP/3 ( h2 + h3 )
    HttpTestServer h3TestServer;          // HTTP/3 ( h3 )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String h2h3URI;
    String h2h3Head;
    String h3URI;

    static final long SERVER_LATENCY = 75;
    static final int MAX_CLIENT_DELAY = 75;
    static final int ITERATION_COUNT = 3;
    // a shared executor helps reduce the amount of threads created by the test
    static final Executor executor = new TestExecutor(Executors.newCachedThreadPool());
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    static volatile boolean tasksFailed;
    static final AtomicLong serverCount = new AtomicLong();
    static final AtomicLong clientCount = new AtomicLong();
    static final AtomicLong requestCount = new AtomicLong();
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    private volatile HttpClient sharedClient;

    static class TestExecutor implements Executor {
        final AtomicLong tasks = new AtomicLong();
        Executor executor;
        TestExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public void execute(Runnable command) {
            long id = tasks.incrementAndGet();
            executor.execute(() -> {
                try {
                    command.run();
                } catch (Throwable t) {
                    tasksFailed = true;
                    System.out.printf(now() + "Task %s failed: %s%n", id, t);
                    System.err.printf(now() + "Task %s failed: %s%n", id, t);
                    FAILURES.putIfAbsent("Task " + id, t);
                    throw t;
                }
            });
        }
    }

    protected boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }

    final AtomicReference<SkipException> skiptests = new AtomicReference<>();
    void checkSkip() {
        var skip = skiptests.get();
        if (skip != null) throw skip;
    }
    static String name(ITestResult result) {
        var params = result.getParameters();
        return result.getName()
                + (params == null ? "()" : Arrays.toString(result.getParameters()));
    }

    @BeforeMethod
    void beforeMethod(ITestContext context) {
        if (stopAfterFirstFailure() && context.getFailedTests().size() > 0) {
            if (skiptests.get() == null) {
                SkipException skip = new SkipException("some tests failed");
                skip.setStackTrace(new StackTraceElement[0]);
                skiptests.compareAndSet(null, skip);
            }
        }
    }

    @AfterClass
    static void printFailedTests(ITestContext context) {
        out.println("\n=========================");
        var failed = context.getFailedTests().getAllResults().stream()
                .collect(Collectors.toMap(CancelRequestTest::name, ITestResult::getThrowable));
        FAILURES.putAll(failed);
        try {
            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.forEach((key, value) -> {
                out.printf("\t%s: %s%n", key, value);
                value.printStackTrace(out);
            });
            if (tasksFailed) {
                System.out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    private String[] uris() {
        return new String[] {
                httpURI,
                httpsURI,
                http2URI,
                https2URI,
                h2h3URI,
                h3URI,
        };
    }

    @DataProvider(name = "asyncurls")
    public Object[][] asyncurls() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2 * 3][];
        //Object[][] result = new Object[uris.length][];
        int i = 0;
        for (boolean mayInterrupt : List.of(true, false, true)) {
            for (boolean sameClient : List.of(false, true)) {
                //if (!sameClient) continue;
                for (String uri : uris()) {
                    String path = sameClient ? "same" : "new";
                    path = path + (mayInterrupt ? "/interrupt" : "/nointerrupt");
                    result[i++] = new Object[]{uri + path, sameClient, mayInterrupt};
                }
            }
        }
        assert i == uris.length * 2 * 3;
        // assert i == uris.length ;
        return result;
    }

    @DataProvider(name = "urls")
    public Object[][] alltests() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        //Object[][] result = new Object[uris.length][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            //if (!sameClient) continue;
            for (String uri : uris()) {
                String path = sameClient ? "same" : "new";
                path = path + "/interruptThread";
                result[i++] = new Object[]{uri + path, sameClient};
            }
        }
        assert i == uris.length * 2;
        // assert i == uris.length ;
        return result;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        return TRACKER.track(newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build());
    }

    HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }

    // set HTTP/3 version on the request when targeting
    // an HTTP/3 server
    private HttpRequest.Builder requestBuilder(String uri) {
        var u = URI.create(uri+"?req="+requestCount.incrementAndGet());
        var builder = HttpRequest.newBuilder(u);
        if (uri.contains("h3")) {
            builder.version(HTTP_3);
        }
        builder.setHeader("X-expect-exception", "true");
        return builder;
    }


    final static String BODY = "Some string | that ? can | be split ? several | ways.";

    // should accept SSLHandshakeException because of the connectionAborter
    // with http/2 and should accept Stream 5 cancelled.
    //  => also examine in what measure we should always
    //     rewrap in "Request Cancelled" when the multi exchange was aborted...
    private static boolean isCancelled(Throwable t) {
        while (t instanceof ExecutionException) t = t.getCause();
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof CancellationException) return true;
            if (cause instanceof IOException && String.valueOf(cause).contains("Request cancelled")) return true;
            cause = cause.getCause();
        }
        out.println("Not a cancellation exception: " + t);
        t.printStackTrace(out);
        return false;
    }

    private static void delay() {
        int delay = random.nextInt(MAX_CLIENT_DELAY);
        try {
            System.out.println("client delay: " + delay);
            Thread.sleep(delay);
        } catch (InterruptedException x) {
            out.println("Unexpected exception: " + x);
        }
    }

    void headRequest(HttpClient client) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(h2h3Head))
                .version(HTTP_2)
                .HEAD()
                .build();
        var resp = client.send(request, BodyHandlers.discarding());
        assertEquals(resp.statusCode(), 200);
    }

    private static void releaseLatches() {
        // release left over latches
        for (var latch : latches.values()) {
            latch.countDown();
        }
        latches.clear();
    }

    private static CountDownLatch addLatchFor(HttpRequest req) {
        // release left over latches
        releaseLatches();
        String key = Objects.requireNonNull(req.uri().getRawQuery(), "query");
        var latch = new CountDownLatch(1);
        latches.put(key, latch);
        out.println(now() + "CountDownLatch " + latch + " added for " + req.uri());
        return latch;
    }

    @Test(dataProvider = "asyncurls")
    public void testGetSendAsync(String uri, boolean sameClient, boolean mayInterruptIfRunning)
            throws Exception {
        checkSkip();
        HttpClient client = null;
        uri = uri + "/get";
        out.printf("%n%s testGetSendAsync(%s, %b, %b)%n", now(), uri, sameClient, mayInterruptIfRunning);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient(sameClient);
            Tracker tracker = TRACKER.getTracker(client);

            // Populate alt-svc registry with h3 service
            if (uri.contains("h2h3")) headRequest(client);
            Http3DiscoveryMode config = uri.contains("h3-only") ? h3TestServer.h3DiscoveryConfig() : null;
            HttpRequest req = requestBuilder(uri)
                    .GET()
                    .setOption(H3_DISCOVERY, config)
                    .build();
            var requestLatch = addLatchFor(req);

            BodyHandler<String> handler = BodyHandlers.ofString();
            CountDownLatch latch = new CountDownLatch(1);
            out.println(now() + "Sending (async): " + req.uri());
            CompletableFuture<HttpResponse<String>> response = client.sendAsync(req, handler);
            var cf1 = response.whenComplete((r,t) -> System.out.println(t));
            CompletableFuture<HttpResponse<String>> cf2 = cf1.whenComplete((r,t) -> latch.countDown());
            out.println(now() + "iteration: " + i + ", req sent: " + req.uri());
            out.println("response: " + response);
            out.println("cf1: " + cf1);
            out.println("cf2: " + cf2);
            delay();
            cf1.cancel(mayInterruptIfRunning);
            out.println(now() + "response after cancel: " + response);
            out.println("cf1 after cancel: " + cf1);
            out.println("cf2 after cancel: " + cf2);
            try {
                String body = cf2.get().body();
                assertEquals(body, String.join("", BODY.split("\\|")));
                throw new AssertionError("Expected CancellationException not received");
            } catch (ExecutionException x) {
                out.println(now() + "Got expected exception: " + x);
                assertTrue(isCancelled(x));
            } finally {
                requestLatch.countDown();
            }

            // Cancelling the request may cause an IOException instead...
            boolean hasCancellationException = false;
            try {
                cf1.get();
            } catch (CancellationException | ExecutionException x) {
                out.println(now() + "Got expected exception: " + x);
                assertTrue(isCancelled(x));
                hasCancellationException = x instanceof CancellationException;
            }

            // because it's cf1 that was cancelled then response might not have
            // completed yet - so wait for it here...
            try {
                String body = response.get().body();
                assertEquals(body, String.join("", BODY.split("\\|")));
                if (mayInterruptIfRunning) {
                    // well actually - this could happen... In which case we'll need to
                    // increase the latency in the server handler...
                    throw new AssertionError("Expected Exception not received");
                }
            } catch (ExecutionException x) {
                assertTrue(response.isDone());
                Throwable wrapped = x.getCause();
                Throwable cause = wrapped;
                if (mayInterruptIfRunning) {
                    if (CancellationException.class.isAssignableFrom(wrapped.getClass())) {
                        cause = wrapped.getCause();
                        out.println(now() + "CancellationException cause: " + x);
                    } else if (!isCancelled(cause)) {
                        throw new RuntimeException("Unexpected cause: " + cause);
                    }
                    if (cause instanceof HttpConnectTimeoutException) {
                        cause.printStackTrace(out);
                        throw new RuntimeException("Unexpected timeout exception", cause);
                    }
                }
                if (!IOException.class.isInstance(cause)) {
                    out.println(now() + "Unexpected cause: " + cause.getClass());
                    cause.printStackTrace(out);
                }
                assertTrue(IOException.class.isAssignableFrom(cause.getClass()));
                if (mayInterruptIfRunning) {
                    out.println(now() + "Got expected exception: " + wrapped);
                    out.println("\tcause: " + cause);
                } else {
                    out.println(now() + "Unexpected exception: " + wrapped);
                    wrapped.printStackTrace(out);
                    throw x;
                }
            }

            assertTrue(response.isDone());
            assertFalse(response.isCancelled());
            assertEquals(cf1.isCancelled(), hasCancellationException);
            assertTrue(cf2.isDone());
            assertFalse(cf2.isCancelled());
            assertEquals(latch.getCount(), 0);

            var error = TRACKER.check(tracker, 1000,
                    (t) -> t.getOutstandingOperations() > 0 || t.getOutstandingSubscribers() > 0,
                    "subscribers for testGetSendAsync(%s)\n\t step [%s]".formatted(req.uri(), i),
                    false);
            Reference.reachabilityFence(client);
            if (error != null) throw error;
        }
        assert client != null;
        if (!sameClient) client.close();
    }

    @Test(dataProvider = "asyncurls")
    public void testPostSendAsync(String uri, boolean sameClient, boolean mayInterruptIfRunning)
            throws Exception {
        checkSkip();
        uri = uri + "/post";
        HttpClient client = null;
        out.printf("%n%s testPostSendAsync(%s, %b, %b)%n", now(), uri, sameClient, mayInterruptIfRunning);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient(sameClient);
            Tracker tracker = TRACKER.getTracker(client);

            CompletableFuture<CompletableFuture<?>> cancelFuture = new CompletableFuture<>();

            Iterable<byte[]> iterable = new Iterable<>() {
                @Override
                public Iterator<byte[]> iterator() {
                    // this is dangerous
                    out.println(now() + "waiting for completion on: " + cancelFuture);
                    boolean async = random.nextBoolean();
                    Runnable cancel = () -> {
                        out.println(now() + "Cancelling from " + Thread.currentThread());
                        var cf1 = cancelFuture.join();
                        cf1.cancel(mayInterruptIfRunning);
                        out.println(now() + "cancelled " + cf1);
                    };
                    if (async) executor.execute(cancel);
                    else cancel.run();
                    return List.of(BODY.getBytes(UTF_8)).iterator();
                }
            };

            // Populate alt-svc registry with h3 service
            if (uri.contains("h2h3")) headRequest(client);
            Http3DiscoveryMode config = uri.contains("h3-only") ? h3TestServer.h3DiscoveryConfig() : null;
            HttpRequest req = requestBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(iterable))
                    .setOption(H3_DISCOVERY, config)
                    .build();
            var requestLatch = addLatchFor(req);

            BodyHandler<String> handler = BodyHandlers.ofString();
            CountDownLatch latch = new CountDownLatch(1);
            out.println(now() + "Sending (async): " + req.uri());
            CompletableFuture<HttpResponse<String>> response = client.sendAsync(req, handler);
            var cf1 = response.whenComplete((r,t) -> System.out.println(now() + t));
            CompletableFuture<HttpResponse<String>> cf2 = cf1.whenComplete((r,t) -> latch.countDown());
            out.println(now() + "iteration: " + i + ", req sent: " + req.uri());
            out.println("response: " + response);
            out.println("cf1: " + cf1);
            out.println("cf2: " + cf2);
            cancelFuture.complete(cf1);
            out.println(now() + "response after cancel: " + response);
            out.println("cf1 after cancel: " + cf1);
            out.println("cf2 after cancel: " + cf2);
            try {
                String body = cf2.get().body();
                assertEquals(body, String.join("", BODY.split("\\|")));
                throw new AssertionError("Expected CancellationException not received");
            } catch (ExecutionException x) {
                out.println(now() + "Got expected exception: " + x);
                assertTrue(isCancelled(x));
            } finally {
                requestLatch.countDown();
            }

            // Cancelling the request may cause an IOException instead...
            boolean hasCancellationException = false;
            try {
                cf1.get();
            } catch (CancellationException | ExecutionException x) {
                out.println(now() + "Got expected exception: " + x);
                assertTrue(isCancelled(x));
                hasCancellationException = x instanceof CancellationException;
            }

            // because it's cf1 that was cancelled then response might not have
            // completed yet - so wait for it here...
            try {
                String body = response.get().body();
                assertEquals(body, String.join("", BODY.split("\\|")));
                if (mayInterruptIfRunning) {
                    // well actually - this could happen... In which case we'll need to
                    // increase the latency in the server handler...
                    throw new AssertionError("Expected Exception not received");
                }
            } catch (ExecutionException x) {
                assertTrue(response.isDone());
                Throwable wrapped = x.getCause();
                Throwable cause = wrapped;
                if (CancellationException.class.isAssignableFrom(wrapped.getClass())) {
                    cause = wrapped.getCause();
                    out.println(now() + "CancellationException cause: " + x);
                } else if (!isCancelled(cause)) {
                    throw new RuntimeException("Unexpected cause: " + cause);
                }
                assertTrue(IOException.class.isAssignableFrom(cause.getClass()));
                if (cause instanceof HttpConnectTimeoutException) {
                    cause.printStackTrace(out);
                    throw new RuntimeException("Unexpected timeout exception", cause);
                }
                if (mayInterruptIfRunning) {
                    out.println(now() + "Got expected exception: " + wrapped);
                    out.println("\tcause: " + cause);
                } else {
                    out.println(now() + "Unexpected exception: " + wrapped);
                    wrapped.printStackTrace(out);
                    throw x;
                }
            }

            assertTrue(response.isDone());
            assertFalse(response.isCancelled());
            assertEquals(cf1.isCancelled(), hasCancellationException);
            assertTrue(cf2.isDone());
            assertFalse(cf2.isCancelled());
            assertEquals(latch.getCount(), 0);

            var error = TRACKER.check(tracker, 1000,
                    (t) -> t.getOutstandingOperations() > 0 || t.getOutstandingSubscribers() > 0,
                    "subscribers for testPostSendAsync(%s)\n\t step [%s]".formatted(req.uri(), i),
                    false);
            Reference.reachabilityFence(client);
            if (error != null) throw error;
        }
        assert client != null;
        if (!sameClient) client.close();
    }

    @Test(dataProvider = "urls")
    public void testPostInterrupt(String uri, boolean sameClient)
            throws Exception {
        checkSkip();
        HttpClient client = null;
        out.printf("%n%s testPostInterrupt(%s, %b)%n", now(), uri, sameClient);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient(sameClient);
            Tracker tracker = TRACKER.getTracker(client);

            Thread main = Thread.currentThread();
            CompletableFuture<Thread> interruptingThread = new CompletableFuture<>();
            var uriStr = uri + "/post/i=" + i;
            Runnable interrupt = () -> {
                Thread current = Thread.currentThread();
                out.printf("%s Interrupting main from: %s (%s)%n", now(), current, uriStr);
                err.printf("%s Interrupting main from: %s (%s)%n", now(), current, uriStr);
                interruptingThread.complete(current);
                main.interrupt();
            };
            Iterable<byte[]> iterable = () -> {
                var async = random.nextBoolean();
                if (async) executor.execute(interrupt);
                else interrupt.run();
                return List.of(BODY.getBytes(UTF_8)).iterator();
            };

            // Populate alt-svc registry with h3 service
            if (uri.contains("h2h3")) headRequest(client);
            Http3DiscoveryMode config = uri.contains("h3-only") ? h3TestServer.h3DiscoveryConfig() : null;
            HttpRequest req = requestBuilder(uriStr)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(iterable))
                    .setOption(H3_DISCOVERY, config)
                    .build();
            var requestLatch = addLatchFor(req);

            String body = null;
            Exception failed = null;
            try {
                out.println(now() + "Sending: " + req.uri());
                body = client.send(req, BodyHandlers.ofString()).body();
            } catch (Exception x) {
                failed = x;
            }
            requestLatch.countDown();
            out.println(now() + req.uri() + ": got result or exception");
            if (failed instanceof InterruptedException) {
                out.println(now() + req.uri() + ": Got expected exception: " + failed);
            } else if (failed instanceof IOException) {
                out.println(now() + req.uri() + ": got IOException: " + failed);
                // that could be OK if the main thread was interrupted
                // from the main thread: the interrupted status could have
                // been caught by writing to the socket from the main
                // thread.
                if (interruptingThread.isDone() && interruptingThread.get() == main) {
                    out.println(now() + req.uri() + ": Accepting IOException: " + failed);
                    failed.printStackTrace(out);
                } else {
                    out.println(now() + req.uri() + ": unexpected exception: " + failed);
                    throw failed;
                }
            } else if (failed != null) {
                out.println(now() + req.uri() + ": unexpected exception: " + failed);
                throw failed;
            } else {
                assert failed == null;
                out.println(now() + req.uri() + ": got body: " + body);
                assertEquals(body, String.join("", BODY.split("\\|")));
            }
            out.println(now() + "next iteration");

            var error = TRACKER.check(tracker, 2000,
                    (t) -> t.getOutstandingOperations() > 0 || t.getOutstandingSubscribers() > 0,
                    "subscribers for testPostInterrupt(%s)\n\t step [%s]".formatted(req.uri(), i),
                    false);
            Reference.reachabilityFence(client);
            if (error != null) throw error;
        }
        assert client != null;
        if (!sameClient) client.close();
    }



    @BeforeTest
    public void setup() throws Exception {
        // HTTP/1.1
        HttpTestHandler h1_chunkHandler = new HTTPSlowHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(h1_chunkHandler, "/http1/x/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/x/";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/x/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/x/";

        // HTTP/2
        HttpTestHandler h2_chunkedHandler = new HTTPSlowHandler();

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/x/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/x/";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/x/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/x/";

        HttpTestHandler h3_chunkedHandler = new HTTPSlowHandler();
        h2h3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        h2h3TestServer.addHandler(h3_chunkedHandler, "/h2h3/exec/");
        h2h3URI = "https://" + h2h3TestServer.serverAuthority() + "/h2h3/exec/retry";
        h2h3TestServer.addHandler(new HttpHeadOrGetHandler(), "/h2h3/head/");
        h2h3Head = "https://" + h2h3TestServer.serverAuthority() + "/h2h3/head/";

        h3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3TestServer.addHandler(h3_chunkedHandler, "/h3-only/exec/");
        h3URI = "https://" + h3TestServer.serverAuthority() + "/h3-only/exec/retry";

        serverCount.addAndGet(6);
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        h2h3TestServer.start();
        h3TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(500);
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            h2h3TestServer.stop();
            h3TestServer.stop();
        } finally {
            if (fail != null) {
                if (sharedClientName != null) {
                    System.err.println(now() + "Shared client name is: " + sharedClientName);
                }
                throw fail;
            }
        }
    }

    private static boolean isThreadInterrupt(HttpTestExchange t) {
        return t.getRequestURI().getPath().contains("/interruptThread");
    }

    /**
     * A handler that slowly sends back a body to give time for the
     * the request to get cancelled before the body is fully received.
     */
    static class HTTPSlowHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try {
                out.println(now() + "HTTPSlowHandler received request to " + t.getRequestURI());
                System.err.println(now() + "HTTPSlowHandler received request to " + t.getRequestURI());
                var requestLatch = latches.get(t.getRequestURI().getRawQuery());
                boolean isThreadInterrupt = isThreadInterrupt(t);
                byte[] req;
                try (InputStream is = t.getRequestBody()) {
                    req = is.readAllBytes();
                }
                t.sendResponseHeaders(200, -1); // chunked/variable
                try (OutputStream os = t.getResponseBody()) {
                    // let's split the response in several chunks...
                    String msg = (req != null && req.length != 0)
                            ? new String(req, UTF_8)
                            : BODY;
                    String[] str = msg.split("\\|");
                    for (var s : str) {
                        req = s.getBytes(UTF_8);
                        os.write(req);
                        os.flush();
                        try {
                            Thread.sleep(SERVER_LATENCY);
                        } catch (InterruptedException x) {
                            // OK
                        }
                        out.printf(now() + "Server wrote %d bytes%n", req.length);
                    }
                    if (requestLatch != null) {
                        out.printf(now() + "Server awaiting latch %s for %s%n",
                                requestLatch, t.getRequestURI());
                        try {
                            requestLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        out.printf(now() + " ...latch released%n");
                    }
                }
            } catch (IOException io) {
                out.println(now() + "HTTPSlowHandler: IOexception is not unexpected: " + io);
                throw io;
            } catch (Throwable e) {
                out.println(now() + "HTTPSlowHandler: unexpected exception: " + e);
                e.printStackTrace();
                throw e;
            } finally {
                out.printf(now() + "HTTPSlowHandler reply sent: %s%n", t.getRequestURI());
                System.err.printf(now() + "HTTPSlowHandler reply sent: %s%n", t.getRequestURI());
            }
        }
    }

}
