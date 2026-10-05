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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

public abstract class AbstractThrowingPublishers implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
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

    final static class TestStopper implements TestWatcher, BeforeEachCallback {
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

    public static Object[][] sanity() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        //Object[][] result = new Object[uris.length][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            //if (!sameClient) continue;
            for (String uri: uris()) {
                result[i++] = new Object[] {uri + "/sanity", sameClient};
            }
        }
        assert i == uris.length * 2;
        // assert i == uris.length ;
        return result;
    }

    enum Where {
        BEFORE_SUBSCRIBE, BEFORE_REQUEST, BEFORE_NEXT_REQUEST, BEFORE_CANCEL,
        AFTER_SUBSCRIBE, AFTER_REQUEST, AFTER_NEXT_REQUEST, AFTER_CANCEL;
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

    private static Object[][] variants(List<Thrower> throwers, Set<Where> whereValues) {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2 * throwers.size()][];
        //Object[][] result = new Object[(uris.length/2) * 2 * 2][];
        int i = 0;
        for (Thrower thrower : throwers) {
            for (boolean sameClient : List.of(false, true)) {
                for (String uri : uris()) {
                    // if (uri.contains("http2") || uri.contains("https2")) continue;
                    // if (!sameClient) continue;
                    result[i++] = new Object[]{uri, sameClient, thrower, whereValues};
                }
            }
        }
        assert i == uris.length * 2 * throwers.size();
        //assert Stream.of(result).filter(o -> o != null).count() == result.length;
        return result;
    }

    public static Object[][] subscribeProvider() {
        return  variants(List.of(
                new UncheckedCustomExceptionThrower(),
                new UncheckedIOExceptionThrower()),
                EnumSet.of(Where.BEFORE_SUBSCRIBE, Where.AFTER_SUBSCRIBE));
    }

    public static Object[][] requestProvider() {
        return  variants(List.of(
                new UncheckedCustomExceptionThrower(),
                new UncheckedIOExceptionThrower()),
                EnumSet.of(Where.BEFORE_REQUEST, Where.AFTER_REQUEST));
    }

    public static Object[][] nextRequestProvider() {
        return  variants(List.of(
                new UncheckedCustomExceptionThrower(),
                new UncheckedIOExceptionThrower()),
                EnumSet.of(Where.BEFORE_NEXT_REQUEST, Where.AFTER_NEXT_REQUEST));
    }

    public static Object[][] beforeCancelProviderIO() {
        return  variants(List.of(
                new UncheckedIOExceptionThrower()),
                EnumSet.of(Where.BEFORE_CANCEL));
    }

    public static Object[][] afterCancelProviderIO() {
        return  variants(List.of(
                new UncheckedIOExceptionThrower()),
                EnumSet.of(Where.AFTER_CANCEL));
    }

    public static Object[][] beforeCancelProviderCustom() {
        return  variants(List.of(
                new UncheckedCustomExceptionThrower()),
                EnumSet.of(Where.BEFORE_CANCEL));
    }

    public static Object[][] afterCancelProviderCustom() {
        return  variants(List.of(
                new UncheckedCustomExceptionThrower()),
                EnumSet.of(Where.AFTER_CANCEL));
    }

    private static HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        return TRACKER.track(HttpServerAdapters.createClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build());
    }

    static HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (AbstractThrowingPublishers.class) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }

    final String BODY = "Some string | that ? can | be split ? several | ways.";

    //@Test(dataProvider = "sanity")
    protected void testSanityImpl(String uri, boolean sameClient)
            throws Exception {
        HttpClient client = null;
        out.printf("%n%s testSanity(%s, %b)%n", now(), uri, sameClient);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }

            SubmissionPublisher<ByteBuffer> publisher
                    = new SubmissionPublisher<>(executor,10);
            ThrowingBodyPublisher bodyPublisher = new ThrowingBodyPublisher((w) -> {},
                    BodyPublishers.fromPublisher(publisher));
            CompletableFuture<Void> subscribedCF = bodyPublisher.subscribedCF();
            subscribedCF.whenComplete((r,t) -> System.out.println(now() + " subscribe completed " + t))
                    .thenAcceptAsync((v) -> {
                                Stream.of(BODY.split("\\|"))
                                        .forEachOrdered(s -> {
                                                System.out.println("submitting \"" + s +"\"");
                                                publisher.submit(ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)));
                                        });
                                System.out.println("publishing done");
                                publisher.close();
                            },
                    executor);

            HttpRequest req = newRequestBuilder(uri)
                    .POST(bodyPublisher)
                    .build();
            BodyHandler<String> handler = BodyHandlers.ofString();
            CompletableFuture<HttpResponse<String>> response = client.sendAsync(req, handler);

            String body = response.join().body();
            assertEquals(Stream.of(BODY.split("\\|")).collect(Collectors.joining()), body);
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

    // @Test(dataProvider = "variants")
    protected void testThrowingAsStringImpl(String uri,
                                     boolean sameClient,
                                     Thrower thrower,
                                     Set<Where> whereValues)
            throws Exception
    {
        String test = format("testThrowingAsString(%s, %b, %s, %s)",
                             uri, sameClient, thrower, whereValues);
        List<byte[]> bytes = Stream.of(BODY.split("|"))
                .map(s -> s.getBytes(UTF_8))
                .collect(Collectors.toList());
        testThrowing(test, uri, sameClient, () -> BodyPublishers.ofByteArrays(bytes),
                this::shouldNotThrowInCancel, thrower,false, whereValues);
    }

    private <T,U> void testThrowing(String name, String uri, boolean sameClient,
                                    Supplier<BodyPublisher> publishers,
                                    Finisher finisher, Thrower thrower,
                                    boolean async, Set<Where> whereValues)
            throws Exception
    {
        out.printf("%n%s%s%n", now(), name);
        try {
            testThrowing(uri, sameClient, publishers, finisher, thrower, async, whereValues);
        } catch (Error | Exception x) {
            FAILURES.putIfAbsent(name, x);
            throw x;
        }
    }

    private void testThrowing(String uri, boolean sameClient,
                                    Supplier<BodyPublisher> publishers,
                                    Finisher finisher, Thrower thrower,
                                    boolean async, Set<Where> whereValues)
            throws Exception
    {
        HttpClient client = null;
        for (Where where : whereValues) {
            //if (where == Where.ON_SUBSCRIBE) continue;
            //if (where == Where.ON_ERROR) continue;
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uri) == HTTP_3) {
                    headRequest(client);
                }
            }

            ThrowingBodyPublisher bodyPublisher =
                    new ThrowingBodyPublisher(where.select(thrower), publishers.get());
            HttpRequest req = newRequestBuilder(uri)
                    .header("X-expect-exception", "true")
                    .POST(bodyPublisher)
                    .build();
            BodyHandler<String> handler = BodyHandlers.ofString();
            System.out.println(now() + " try throwing in " + where);
            HttpResponse<String> response = null;
            if (async) {
                try {
                    response = client.sendAsync(req, handler).join();
                } catch (Error | Exception x) {
                    Throwable cause = findCause(where, x, thrower);
                    if (cause == null) throw causeNotFound(where, x);
                    System.out.println(now() + "Got expected exception: " + cause);
                }
            } else {
                try {
                    response = client.send(req, handler);
                } catch (Error | Exception t) {
                    // synchronous send will rethrow exceptions
                    Throwable throwable = t.getCause();
                    assert throwable != null;
                    Throwable cause = findCause(where, throwable, thrower);
                    if (cause == null) {
                        throw causeNotFound(where, t);
                    } else {
                        System.out.println(now() + "Got expected exception: " + cause);
                    }
                }
            }
            if (response != null) {
                finisher.finish(where, response, thrower);
            }
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

    // can be used to reduce the surface of the test when diagnosing
    // some failure
    Set<Where> whereValues() {
        //return EnumSet.of(Where.BEFORE_CANCEL, Where.AFTER_CANCEL);
        return EnumSet.allOf(Where.class);
    }

    interface Thrower extends Consumer<Where>, BiPredicate<Where,Throwable> {

    }

    interface Finisher<T,U> {
        U finish(Where w, HttpResponse<T> resp, Thrower thrower) throws IOException;
    }

    final <T,U> U shouldNotThrowInCancel(Where w, HttpResponse<T> resp, Thrower thrower) {
        switch (w) {
            case BEFORE_CANCEL: return null;
            case AFTER_CANCEL: return null;
            default: break;
        }
        return shouldHaveThrown(w, resp, thrower);
    }


    final <T,U> U shouldHaveThrown(Where w, HttpResponse<T> resp, Thrower thrower) {
        String msg = "Expected exception not thrown in " + w
                + "\n\tReceived: " + resp
                + "\n\tWith body: " + resp.body();
        System.out.println(msg);
        throw new RuntimeException(msg);
    }


    private static Throwable findCause(Where w,
                                       Throwable x,
                                       BiPredicate<Where, Throwable> filter) {
        while (x != null && !filter.test(w,x)) x = x.getCause();
        return x;
    }

    static AssertionError causeNotFound(Where w, Throwable t) {
        return new AssertionError("Expected exception not found in " + w, t);
    }

    static boolean isConnectionClosedLocally(Throwable t) {
        if (t instanceof CompletionException) t = t.getCause();
        if (t instanceof ExecutionException) t = t.getCause();
        if (t instanceof IOException) {
            String msg = t.getMessage();
            return msg == null ? false
                    : msg.contains("connection closed locally");
        }
        return false;
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
        public boolean test(Where w, Throwable throwable) {
            switch (w) {
                case AFTER_REQUEST:
                case BEFORE_NEXT_REQUEST:
                case AFTER_NEXT_REQUEST:
                    if (isConnectionClosedLocally(throwable)) return true;
                    break;
                default:
                    break;
            }
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
        public boolean test(Where w, Throwable throwable) {
            switch (w) {
                case AFTER_REQUEST:
                case BEFORE_NEXT_REQUEST:
                case AFTER_NEXT_REQUEST:
                    if (isConnectionClosedLocally(throwable)) return true;
                    break;
                default:
                    break;
            }
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


    static final class ThrowingBodyPublisher implements BodyPublisher {
        private final BodyPublisher publisher;
        private final CompletableFuture<Void> subscribedCF = new CompletableFuture<>();
        final Consumer<Where> throwing;
        ThrowingBodyPublisher(Consumer<Where> throwing, BodyPublisher publisher) {
            this.throwing = throwing;
            this.publisher = publisher;
        }

        @Override
        public long contentLength() {
            return publisher.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            try {
                throwing.accept(Where.BEFORE_SUBSCRIBE);
                publisher.subscribe(new SubscriberWrapper(subscriber));
                subscribedCF.complete(null);
                throwing.accept(Where.AFTER_SUBSCRIBE);
            } catch (Throwable t) {
                subscribedCF.completeExceptionally(t);
                throw t;
            }
        }

        CompletableFuture<Void> subscribedCF() {
            return subscribedCF;
        }

        class SubscriptionWrapper implements Flow.Subscription {
            final Flow.Subscription subscription;
            final AtomicLong requestCount = new AtomicLong();
            SubscriptionWrapper(Flow.Subscription subscription) {
                this.subscription = subscription;
            }
            @Override
            public void request(long n) {
                long count = requestCount.incrementAndGet();
                System.out.printf("%s request-%d(%d)%n", now(), count, n);
                if (count > 1) throwing.accept(Where.BEFORE_NEXT_REQUEST);
                throwing.accept(Where.BEFORE_REQUEST);
                subscription.request(n);
                throwing.accept(Where.AFTER_REQUEST);
                if (count > 1) throwing.accept(Where.AFTER_NEXT_REQUEST);
            }

            @Override
            public void cancel() {
                throwing.accept(Where.BEFORE_CANCEL);
                subscription.cancel();
                throwing.accept(Where.AFTER_CANCEL);
            }
        }

        class SubscriberWrapper implements Flow.Subscriber<ByteBuffer> {
            final Flow.Subscriber<? super ByteBuffer> subscriber;
            SubscriberWrapper(Flow.Subscriber<? super ByteBuffer> subscriber) {
                this.subscriber = subscriber;
            }
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(new SubscriptionWrapper(subscription));
            }
            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(item);
            }
            @Override
            public void onComplete() {
                subscriber.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }
        }
    }


    @BeforeAll
    public static void setup() throws Exception {
        System.out.println(now() + "setup");
        System.err.println(now() + "setup");

        // HTTP/1.1
        HttpTestHandler h1_fixedLengthHandler = new HTTP_FixedLengthHandler();
        HttpTestHandler h1_chunkHandler = new HTTP_ChunkedHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(h1_fixedLengthHandler, "/http1/fixed");
        httpTestServer.addHandler(h1_chunkHandler, "/http1/chunk");
        httpURI_fixed = "http://" + httpTestServer.serverAuthority() + "/http1/fixed/x";
        httpURI_chunk = "http://" + httpTestServer.serverAuthority() + "/http1/chunk/x";

        System.out.println(now() + "HTTP/1.1 server created");

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(h1_fixedLengthHandler, "/https1/fixed");
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/chunk");
        httpsURI_fixed = "https://" + httpsTestServer.serverAuthority() + "/https1/fixed/x";
        httpsURI_chunk = "https://" + httpsTestServer.serverAuthority() + "/https1/chunk/x";

        System.out.println(now() + "TLS HTTP/1.1 server created");

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
        AssertionError fail = TRACKER.check(10000);
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
            byte[] resp;
            try (InputStream is = t.getRequestBody()) {
                resp = is.readAllBytes();
            }
            t.sendResponseHeaders(200, resp.length);  //fixed content length
            try (OutputStream os = t.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    static class HTTP_ChunkedHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("HTTP_ChunkedHandler received request to " + t.getRequestURI());
            byte[] resp;
            try (InputStream is = t.getRequestBody()) {
                resp = is.readAllBytes();
            }
            t.sendResponseHeaders(200, -1); // chunked/variable
            try (OutputStream os = t.getResponseBody()) {
                os.write(resp);
            }
        }
    }

}
