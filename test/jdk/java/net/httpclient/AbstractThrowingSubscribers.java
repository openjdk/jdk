/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.lang.String.format;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

public abstract class AbstractThrowingSubscribers implements HttpServerAdapters {

    static SSLContext sslContext;
    static HttpTestServer httpTestServer;    // HTTP/1.1    [ 4 servers ]
    static HttpTestServer httpsTestServer;   // HTTPS/1.1
    static HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    static HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    static HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    static String httpURI_fixed;
    static String httpURI_chunk;
    static String httpsURI_fixed;
    static String httpsURI_chunk;
    static String http2URI_fixed;
    static String http2URI_chunk;
    static String https2URI_fixed;
    static String https2URI_chunk;
    static String http3URI_fixed;
    static String http3URI_chunk;
    static String http3URI_head;

    static final int ITERATION_COUNT = 1;
    static final int REPEAT_RESPONSE = 3;
    // a shared executor helps reduce the amount of threads created by the test
    static final Executor executor = new TestExecutor(Executors.newCachedThreadPool());
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    static volatile boolean tasksFailed;
    static final AtomicLong serverCount = new AtomicLong();
    static final AtomicLong clientCount = new AtomicLong();
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    static final class TestStopper implements TestWatcher, BeforeEachCallback {
        final AtomicReference<String> failed = new AtomicReference<>();
        TestStopper() { }
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            if (stopAfterFirstFailure()) {
                String msg = "Aborting due to: " + cause;
                failed.compareAndSet(null, msg);
                FAILURES.putIfAbsent(context.getDisplayName(), cause);
                System.out.printf("%nTEST FAILED: %s%s%n\tAborting due to %s%n%n",
                        now(), context.getDisplayName(), cause);
                System.err.printf("%nTEST FAILED: %s%s%n\tAborting due to %s%n%n",
                        now(), context.getDisplayName(), cause);
            }
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            String msg = failed.get();
            Assumptions.assumeTrue(msg == null, msg);
        }
    }

    @RegisterExtension
    static final TestStopper stopper = new TestStopper();

    static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
    private static volatile HttpClient sharedClient;

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

    protected static boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }

    static Version version(String uri) {
        if (uri.contains("/http1/") || uri.contains("/https1/"))
            return HTTP_1_1;
        if (uri.contains("/http2/") || uri.contains("/https2/"))
            return HTTP_2;
        if (uri.contains("/http3/"))
            return HTTP_3;
        return null;
    }

    static HttpRequest.Builder newRequestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        if (version(uri) == HTTP_3) {
            builder.version(HTTP_3);
            builder.setOption(H3_DISCOVERY, http3TestServer.h3DiscoveryConfig());
        }
        return builder;
    }

    static HttpResponse<String> headRequest(HttpClient client)
            throws IOException, InterruptedException
    {
        System.out.println("\n" + now() + "--- Sending HEAD request ----\n");
        System.err.println("\n" + now() + "--- Sending HEAD request ----\n");

        var request = newRequestBuilder(http3URI_head)
                .HEAD().version(HTTP_2).build();
        var response = client.send(request, BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(HTTP_2, response.version());
        System.out.println("\n" + now() + "--- HEAD request succeeded ----\n");
        System.err.println("\n" + now() + "--- HEAD request succeeded ----\n");
        return response;
    }

    @AfterAll
    static final void printFailedTests() {
        out.println("\n=========================");
        try {
            // Exceptions should already have been added to FAILURES
            // var failed = context.getFailedTests().getAllResults().stream()
            //        .collect(Collectors.toMap(r -> name(r), ITestResult::getThrowable));
            // FAILURES.putAll(failed);

            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.entrySet().forEach((e) -> {
                out.printf("\t%s: %s%n", e.getKey(), e.getValue());
                e.getValue().printStackTrace(out);
                e.getValue().printStackTrace();
            });
            if (tasksFailed) {
                System.out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    private static String[] uris() {
        return new String[] {
                http3URI_fixed,
                http3URI_chunk,
                httpURI_fixed,
                httpURI_chunk,
                httpsURI_fixed,
                httpsURI_chunk,
                http2URI_fixed,
                http2URI_chunk,
                https2URI_fixed,
                https2URI_chunk,
        };
    }

    static final AtomicLong URICOUNT = new AtomicLong();

    public static Object[][] sanity() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            for (String uri: uris()) {
                result[i++] = new Object[] {uri, sameClient};
            }
        }
        assert i == uris.length * 2;
        return result;
    }

    public static Object[][] variants() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2 * 2][];
        int i = 0;
        for (Thrower thrower : List.of(
                new UncheckedIOExceptionThrower(),
                new UncheckedCustomExceptionThrower())) {
            for (boolean sameClient : List.of(false, true)) {
                for (String uri : uris()) {
                    result[i++] = new Object[]{uri, sameClient, thrower};
                }
            }
        }
        assert i == uris.length * 2 * 2;
        return result;
    }

    private static HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client =  HttpServerAdapters.createClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return TRACKER.track(client);
    }

    static HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (AbstractThrowingSubscribers.class) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }

    enum SubscriberType {
        INLINE,  // In line subscribers complete their CF on ON_COMPLETE
                 // e.g. BodySubscribers::ofString
        OFFLINE; // Off line subscribers complete their CF immediately
                 // but require the client to pull the data after the
                 // CF completes (e.g. BodySubscribers::ofInputStream)
    }

    static EnumSet<Where> excludes(SubscriberType type) {
        EnumSet<Where> set = EnumSet.noneOf(Where.class);

        if (type == SubscriberType.OFFLINE) {
            // Throwing on onSubscribe needs some more work
            // for the case of InputStream, where the body has already
            // completed by the time the subscriber is subscribed.
            // The only way we have at that point to relay the exception
            // is to call onError on the subscriber, but should we if
            // Subscriber::onSubscribed has thrown an exception and
            // not completed normally?
            set.add(Where.ON_SUBSCRIBE);
        }

        // Don't know how to make the stack reliably cause onError
        // to be called without closing the connection.
        // And how do we get the exception if onError throws anyway?
        set.add(Where.ON_ERROR);

        return set;
    }

    //@Test(dataProvider = "sanity")
    protected void testSanityImpl(String uri, boolean sameClient)
            throws Exception {
        HttpClient client = null;
        String uri2 = uri + "-" + URICOUNT.incrementAndGet() + "/sanity";
        out.printf("%ntestSanity(%s, %b)%n", uri2, sameClient);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }

            HttpRequest req = newRequestBuilder(uri2)
                    .build();
            BodyHandler<String> handler =
                    new ThrowingBodyHandler((w) -> {},
                                            BodyHandlers.ofString());
            HttpResponse<String> response = client.send(req, handler);
            String body = response.body();
            Stream.of(body.split("\n")).forEach(u ->
                assertEquals(URI.create(uri2).getPath(), URI.create(u).getPath()));
            if (!sameClient) {
                // Wait for the client to be garbage collected.
                // we use the ReferenceTracker API rather than HttpClient::close here,
                // because these tests inject faults by throwing inside callbacks, which
                // is more likely to get HttpClient::close wedged until jtreg times out.
                // By using the ReferenceTracker, we will get some diagnosis about what
                // is keeping the client alive if it doesn't get GC'ed within the
                // expected time frame.
                var tracker = TRACKER.getTracker(client);
                client = null;
                System.gc();
                System.out.println(now() + "waiting for client to shutdown: " + tracker.getName());
                System.err.println(now() + "waiting for client to shutdown: " + tracker.getName());
                var error = TRACKER.check(tracker, 10000);
                if (error != null) throw error;
                System.out.println(now() + "client shutdown normally: " + tracker.getName());
                System.err.println(now() + "client shutdown normally: " + tracker.getName());
            }
        }
    }

    //@Test(dataProvider = "variants")
    protected void testThrowingAsStringImpl(String uri,
                                     boolean sameClient,
                                     Thrower thrower)
            throws Exception
    {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String test = format("testThrowingAsString(%s, %b, %s)",
                             uri, sameClient, thrower);
        testThrowing(test, uri, sameClient, BodyHandlers::ofString,
                this::shouldHaveThrown, thrower,false,
                excludes(SubscriberType.INLINE));
    }

    //@Test(dataProvider = "variants")
    protected void testThrowingAsLinesImpl(String uri,
                                    boolean sameClient,
                                    Thrower thrower)
            throws Exception
    {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String test =  format("testThrowingAsLines(%s, %b, %s)",
                uri, sameClient, thrower);
        testThrowing(test, uri, sameClient, BodyHandlers::ofLines,
                this::checkAsLines, thrower,false,
                excludes(SubscriberType.OFFLINE));
    }

    //@Test(dataProvider = "variants")
    protected void testThrowingAsInputStreamImpl(String uri,
                                          boolean sameClient,
                                          Thrower thrower)
            throws Exception
    {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String test = format("testThrowingAsInputStream(%s, %b, %s)",
                uri, sameClient, thrower);
        testThrowing(test, uri, sameClient, BodyHandlers::ofInputStream,
                this::checkAsInputStream,  thrower,false,
                excludes(SubscriberType.OFFLINE));
    }

    //@Test(dataProvider = "variants")
    protected void testThrowingAsStringAsyncImpl(String uri,
                                          boolean sameClient,
                                          Thrower thrower)
            throws Exception
    {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String test = format("testThrowingAsStringAsync(%s, %b, %s)",
                uri, sameClient, thrower);
        testThrowing(test, uri, sameClient, BodyHandlers::ofString,
                     this::shouldHaveThrown, thrower, true,
                excludes(SubscriberType.INLINE));
    }

    //@Test(dataProvider = "variants")
    protected void testThrowingAsLinesAsyncImpl(String uri,
                                         boolean sameClient,
                                         Thrower thrower)
            throws Exception
    {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String test = format("testThrowingAsLinesAsync(%s, %b, %s)",
                uri, sameClient, thrower);
        testThrowing(test, uri, sameClient, BodyHandlers::ofLines,
                this::checkAsLines, thrower,true,
                excludes(SubscriberType.OFFLINE));
    }

    //@Test(dataProvider = "variants")
    protected void testThrowingAsInputStreamAsyncImpl(String uri,
                                               boolean sameClient,
                                               Thrower thrower)
            throws Exception
    {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String test = format("testThrowingAsInputStreamAsync(%s, %b, %s)",
                uri, sameClient, thrower);
        testThrowing(test, uri, sameClient, BodyHandlers::ofInputStream,
                this::checkAsInputStream, thrower,true,
                excludes(SubscriberType.OFFLINE));
    }

    <T,U> void testThrowing(String name, String uri, boolean sameClient,
                                    Supplier<BodyHandler<T>> handlers,
                                    Finisher finisher, Thrower thrower,
                                    boolean async, EnumSet<Where> excludes)
            throws Exception
    {
        out.printf("%n%s%s%n", now(), name);
        try {
            testThrowing(uri, sameClient, handlers, finisher, thrower, async, excludes);
        } catch (Error | Exception x) {
            FAILURES.putIfAbsent(name, x);
            throw x;
        }
    }

    private <T,U> void testThrowing(String uri, boolean sameClient,
                                    Supplier<BodyHandler<T>> handlers,
                                    Finisher finisher, Thrower thrower,
                                    boolean async,
                                    EnumSet<Where> excludes)
            throws Exception
    {
        HttpClient client = null;
        var throwing = thrower;
        for (Where where : EnumSet.complementOf(excludes)) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }

            String uri2 = uri + "-" + where;
            HttpRequest req = newRequestBuilder(uri2)
                    .build();

            thrower = thrower(where, throwing);

            BodyHandler<T> handler =
                    new ThrowingBodyHandler(where.select(thrower), handlers.get());
            System.out.println("try throwing in " + where);
            HttpResponse<T> response = null;
            if (async) {
                try {
                    response = client.sendAsync(req, handler).join();
                } catch (Error | Exception x) {
                    Throwable cause = findCause(x, thrower);
                    if (cause == null) throw causeNotFound(where, x);
                    System.out.println(now() + "Got expected exception: " + cause);
                }
            } else {
                try {
                    response = client.send(req, handler);
                } catch (Error | Exception t) {
                    // synchronous send will rethrow exceptions
                    Throwable throwable = findCause(t, thrower);
                    if (throwable == null) throw causeNotFound(where, t);
                    System.out.println(now() + "Got expected exception: " + throwable);
                }
            }
            if (response != null) {
                finisher.finish(where, response, thrower);
            }
            var tracker = TRACKER.getTracker(client);
            if (!sameClient) {
                // Wait for the client to be garbage collected.
                // we use the ReferenceTracker API rather than HttpClient::close here,
                // because these tests inject faults by throwing inside callbacks, which
                // is more likely to get HttpClient::close wedged until jtreg times out.
                // By using the ReferenceTracker, we will get some diagnosis about what
                // is keeping the client alive if it doesn't get GC'ed within the
                // expected time frame.
                client = null;
                System.gc();
                System.out.println(now() + "waiting for client to shutdown: " + tracker.getName());
                System.err.println(now() + "waiting for client to shutdown: " + tracker.getName());
                var error = TRACKER.check(tracker, 10000);
                if (error != null) throw error;
                System.out.println(now() + "client shutdown normally: " + tracker.getName());
                System.err.println(now() + "client shutdown normally: " + tracker.getName());
            } else {
                System.out.println(now() + "waiting for operation to finish: " + tracker.getName());
                System.err.println(now() + "waiting for operation to finish: " + tracker.getName());
                var error = TRACKER.checkFinished(tracker, 10000);
                if (error != null) throw error;
                System.out.println(now() + "operation finished normally: " + tracker.getName());
                System.err.println(now() + "operation finished normally: " + tracker.getName());
            }
        }
    }

    enum Where {
        BODY_HANDLER, ON_SUBSCRIBE, ON_NEXT, ON_COMPLETE, ON_ERROR, GET_BODY, BODY_CF;
        public Consumer<Where> select(Consumer<Where> consumer) {
            return new Consumer<Where>() {
                @Override
                public void accept(Where where) {
                    if (Where.this == where) {
                        consumer.accept(where);
                    }
                }
            };
        }
    }

    static AssertionError causeNotFound(Where w, Throwable t) {
        return new AssertionError("Expected exception not found in " + w, t);
    }

    interface Thrower extends Consumer<Where>, Predicate<Throwable> {

    }

    interface Finisher<T,U> {
        U finish(Where w, HttpResponse<T> resp, Thrower thrower) throws IOException;
    }

    final <T,U> U shouldHaveThrown(Where w, HttpResponse<T> resp, Thrower thrower) {
        String msg = "Expected exception not thrown in " + w
                + "\n\tReceived: " + resp
                + "\n\tWith body: " + resp.body();
        System.out.println(msg);
        throw new RuntimeException(msg);
    }

    final List<String> checkAsLines(Where w, HttpResponse<Stream<String>> resp, Thrower thrower) {
        switch(w) {
            case BODY_HANDLER: return shouldHaveThrown(w, resp, thrower);
            case GET_BODY: return shouldHaveThrown(w, resp, thrower);
            case BODY_CF: return shouldHaveThrown(w, resp, thrower);
            default: break;
        }
        List<String> result = null;
        try {
            result = resp.body().collect(Collectors.toList());
        } catch (Error | Exception x) {
            Throwable cause = findCause(x, thrower);
            if (cause != null) {
                out.println(now() + "Got expected exception in " + w + ": " + cause);
                return result;
            }
            throw causeNotFound(w, x);
        }
        return shouldHaveThrown(w, resp, thrower);
    }

    final List<String> checkAsInputStream(Where w, HttpResponse<InputStream> resp,
                                    Thrower thrower)
            throws IOException
    {
        switch(w) {
            case BODY_HANDLER: return shouldHaveThrown(w, resp, thrower);
            case GET_BODY: return shouldHaveThrown(w, resp, thrower);
            case BODY_CF: return shouldHaveThrown(w, resp, thrower);
            default: break;
        }
        List<String> result = null;
        try (InputStreamReader r1 = new InputStreamReader(resp.body(), UTF_8);
             BufferedReader r = new BufferedReader(r1)) {
            try {
                result = r.lines().collect(Collectors.toList());
            } catch (Error | Exception x) {
                Throwable cause = findCause(x, thrower);
                if (cause != null) {
                    out.println(now() + "Got expected exception in " + w + ": " + cause);
                    return result;
                }
                throw causeNotFound(w, x);
            }
        }
        return shouldHaveThrown(w, resp, thrower);
    }

    static Throwable findCause(Throwable x, Predicate<Throwable> filter) {
        while (x != null && !filter.test(x)) x = x.getCause();
        return x;
    }

    static final class UncheckedCustomExceptionThrower implements Thrower {
        @Override
        public void accept(Where where) {
            var thread = Thread.currentThread().getName();
            var thrown = new UncheckedCustomException("[" + thread + "] " + where.name());
            out.println(now() + "Throwing in " + where + ": " + thrown);
            err.println(now() + "Throwing in " + where + ": " + thrown);
            thrown.printStackTrace();
            throw thrown;
        }

        @Override
        public boolean test(Throwable throwable) {
            return UncheckedCustomException.class.isInstance(throwable);
        }

        @Override
        public String toString() {
            return "UncheckedCustomExceptionThrower";
        }
    }

    static final class UncheckedIOExceptionThrower implements Thrower {
        @Override
        public void accept(Where where) {
            var thread = Thread.currentThread().getName();
            var cause = new CustomIOException("[" + thread + "] " + where.name());
            var thrown = new UncheckedIOException(cause);
            out.println(now() + "Throwing in " + where + ": " + thrown);
            err.println(now() + "Throwing in " + where + ": " + thrown);
            cause.printStackTrace();
            throw thrown;
        }

        @Override
        public boolean test(Throwable throwable) {
            return UncheckedIOException.class.isInstance(throwable)
                    && CustomIOException.class.isInstance(throwable.getCause());
        }

        @Override
        public String toString() {
            return "UncheckedIOExceptionThrower";
        }
    }

    static final class UncheckedCustomException extends RuntimeException {
        UncheckedCustomException(String message) {
            super(message);
        }
        UncheckedCustomException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class CustomIOException extends IOException {
        CustomIOException(String message) {
            super(message);
        }
        CustomIOException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class ThrowingBodyHandler<T> implements BodyHandler<T> {
        final Consumer<Where> throwing;
        final BodyHandler<T> bodyHandler;
        ThrowingBodyHandler(Consumer<Where> throwing, BodyHandler<T> bodyHandler) {
            this.throwing = throwing;
            this.bodyHandler = bodyHandler;
        }
        @Override
        public BodySubscriber<T> apply(HttpResponse.ResponseInfo rinfo) {
            throwing.accept(Where.BODY_HANDLER);
            BodySubscriber<T> subscriber = bodyHandler.apply(rinfo);
            return new ThrowingBodySubscriber(throwing, subscriber);
        }
    }

    static final class BodyCFThrower implements Thrower {
        final Thrower thrower;
        BodyCFThrower(Thrower thrower) {
            this.thrower = thrower;
        }
        @Override
        public boolean test(Throwable throwable) {
            // In case of BODY_CF we also cancel the stream,
            // which can cause "Stream XX cancelled" to be reported
            return thrower.test(throwable) ||
                    throwable instanceof IOException io && (
                            io.getMessage().matches("Stream [0-9]+ cancelled") ||
                            io.getMessage().equals("subscription cancelled")
                    );
        }
        @Override
        public void accept(Where where) {
            thrower.accept(where);
        }
    }

    static Thrower thrower(Where where, Thrower thrower) {
        return switch (where) {
            case BODY_CF -> new BodyCFThrower(thrower);
            default -> thrower;
        };
    }

    static final class ThrowingBodySubscriber<T> implements BodySubscriber<T> {
        private final BodySubscriber<T> subscriber;
        volatile Subscription subscription;
        final CompletableFuture<Subscription> subscriptionCF = new CompletableFuture<>();
        final Consumer<Where> throwing;
        ThrowingBodySubscriber(Consumer<Where> throwing, BodySubscriber<T> subscriber) {
            this.throwing = throwing;
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            //out.println("onSubscribe ");
            this.subscription = subscription;
            throwing.accept(Where.ON_SUBSCRIBE);
            subscriber.onSubscribe(subscription);
            subscriptionCF.complete(subscription);
        }

        boolean onSubscribeCalled() {
            return subscription != null;
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
           // out.println("onNext " + item);
            assertTrue(onSubscribeCalled(), "onNext called before onSubscribe");
            throwing.accept(Where.ON_NEXT);
            subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            //out.println("onError");
            assertTrue(onSubscribeCalled(), "onError called before onSubscribe");
            throwing.accept(Where.ON_ERROR);
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            //out.println("onComplete");
            assertTrue(onSubscribeCalled(), "onComplete called before onSubscribe");
            throwing.accept(Where.ON_COMPLETE);
            subscriber.onComplete();
        }

        @Override
        public CompletionStage<T> getBody() {
            throwing.accept(Where.GET_BODY);
            boolean shouldCancel = false;
            try {
                throwing.accept(Where.BODY_CF);
            } catch (Throwable t) {
                shouldCancel = true;
                return CompletableFuture.failedFuture(t);
            } finally {
                // if a BodySubscriber returns a failed future, it
                // should take responsibility for cancelling the
                // subscription explicitly if needed.
                if (shouldCancel) {
                    subscriptionCF.thenAccept(Subscription::cancel);
                }
            }
            return subscriber.getBody();
        }
    }


    @BeforeAll
    public static void setup() throws Exception {
        System.out.println(now() + "setup");
        System.err.println(now() + "setup");

        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        System.out.println(now() + "HTTP/1.1 server created");

        // HTTP/1.1
        HttpTestHandler h1_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h1_chunkHandler = new HTTP_ChunkedHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(h1_fixedLengthHandler, "/http1/fixed");
        httpTestServer.addHandler(h1_chunkHandler, "/http1/chunk");
        httpURI_fixed = "http://" + httpTestServer.serverAuthority() + "/http1/fixed/x";
        httpURI_chunk = "http://" + httpTestServer.serverAuthority() + "/http1/chunk/x";

        System.out.println(now() + "TLS HTTP/1.1 server created");

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(h1_fixedLengthHandler, "/https1/fixed");
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/chunk");
        httpsURI_fixed = "https://" + httpsTestServer.serverAuthority() + "/https1/fixed/x";
        httpsURI_chunk = "https://" + httpsTestServer.serverAuthority() + "/https1/chunk/x";

        // HTTP/2
        HttpTestHandler h2_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h2_chunkedHandler = new HTTP_ChunkedHandler();

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(h2_fixedLengthHandler, "/http2/fixed");
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/chunk");
        http2URI_fixed = "http://" + http2TestServer.serverAuthority() + "/http2/fixed/x";
        http2URI_chunk = "http://" + http2TestServer.serverAuthority() + "/http2/chunk/x";

        System.out.println(now() + "HTTP/2 server created");

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(h2_fixedLengthHandler, "/https2/fixed");
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/chunk");
        https2URI_fixed = "https://" + https2TestServer.serverAuthority() + "/https2/fixed/x";
        https2URI_chunk = "https://" + https2TestServer.serverAuthority() + "/https2/chunk/x";

        System.out.println(now() + "TLS HTTP/2 server created");

        // HTTP/3
        HttpTestHandler h3_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h3_chunkedHandler = new HTTP_ChunkedHandler();
        http3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        http3TestServer.addHandler(h3_fixedLengthHandler, "/http3/fixed");
        http3TestServer.addHandler(h3_chunkedHandler, "/http3/chunk");
        http3TestServer.addHandler(new HttpHeadOrGetHandler(), "/http3/head");
        http3URI_fixed = "https://" + http3TestServer.serverAuthority() + "/http3/fixed/x";
        http3URI_chunk = "https://" + http3TestServer.serverAuthority() + "/http3/chunk/x";
        http3URI_head = "https://" + http3TestServer.serverAuthority() + "/http3/head/x";

        System.out.println(now() + "HTTP/3 server created");
        System.err.println(now() + "Starting servers");

        serverCount.addAndGet(5);
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();

        out.println("HTTP/1.1 server (http) listening at: " + httpTestServer.serverAuthority());
        out.println("HTTP/1.1 server (TLS)  listening at: " + httpsTestServer.serverAuthority());
        out.println("HTTP/2   server (h2c)  listening at: " + http2TestServer.serverAuthority());
        out.println("HTTP/2   server (h2)   listening at: " + https2TestServer.serverAuthority());
        out.println("HTTP/3   server (h2)   listening at: " + http3TestServer.serverAuthority());
        out.println(" + alt endpoint (h3)   listening at: " + http3TestServer.getH3AltService()
                .map(Http3TestServer::getAddress));

        headRequest(newHttpClient(true));

        System.out.println(now() + "setup done");
        System.err.println(now() + "setup done");
    }

    @AfterAll
    public static void teardown() throws Exception {
        System.out.println(now() + "teardown");
        System.err.println(now() + "teardown");

        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(5000);
        try {
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
            http3TestServer.stop();
        } finally {
            if (fail != null) {
                if (sharedClientName != null) {
                    System.err.println("Shared client name is: " + sharedClientName);
                }
                throw fail;
            }
        }
        System.out.println(now() + "teardown done");
        System.err.println(now() + "teardown done");
    }

    static class HTTP_FixedLengthHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("HTTP_FixedLengthHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            byte[] resp = (t.getRequestURI() + "\n").getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, resp.length * 3);  //fixed content length
            try (OutputStream os = t.getResponseBody()) {
                for (int i=0 ; i < REPEAT_RESPONSE; i++) {
                    os.write(resp);
                    os.flush();
                }
            }
        }
    }

    static class HTTP_ChunkedHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("HTTP_ChunkedHandler received request to " + t.getRequestURI());
            byte[] resp = (t.getRequestURI() + "\n").getBytes(StandardCharsets.UTF_8);
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, -1); // chunked/variable
            try (OutputStream os = t.getResponseBody()) {
                for (int i=0 ; i < REPEAT_RESPONSE; i++) {
                    os.write(resp);
                    os.flush();
                }
            }
        }
    }

}
