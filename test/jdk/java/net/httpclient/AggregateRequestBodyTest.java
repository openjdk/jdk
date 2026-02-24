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
 * @bug 8252374
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 *       ReferenceTracker AggregateRequestBodyTest
 * @run junit/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors,headers,frames
 *                     AggregateRequestBodyTest
 * @summary Tests HttpRequest.BodyPublishers::concat
 */

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import javax.net.ssl.SSLContext;

import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AggregateRequestBodyTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer http1TestServer;   // HTTP/1.1 ( http )
    private static HttpTestServer https1TestServer;  // HTTPS/1.1 ( https  )
    private static HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    private static HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    private static HttpTestServer http3TestServer;   // HTTP/3 ( h3 )
    private static URI http1URI;
    private static URI https1URI;
    private static URI http2URI;
    private static URI https2URI;
    private static URI http3URI;

    static final int RESPONSE_CODE = 200;
    static final int ITERATION_COUNT = 4;
    static final Class<CompletionException> CE = CompletionException.class;
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

    private static final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;
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

    private static boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
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

    @AfterAll
    static void printFailedTests() {
        out.println("\n=========================");
        try {
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

    private static URI[] uris() {
        return new URI[] {
                http1URI,
                https1URI,
                http2URI,
                https2URI,
                http3URI,
        };
    }

    public static Object[][] variants() {
        URI[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        int i = 0;
        for (boolean sameClient : List.of(false, true)) {
            for (URI uri : uris()) {
                HttpClient.Version version = null;
                if (uri.equals(http1URI) || uri.equals(https1URI)) version = HttpClient.Version.HTTP_1_1;
                else if (uri.equals(http2URI) || uri.equals(https2URI)) version = HttpClient.Version.HTTP_2;
                else if (uri.equals(http3URI)) version = HTTP_3;
                else throw new AssertionError("Unexpected URI: " + uri);

                result[i++] = new Object[]{uri, version, sameClient};
            }
        }
        assert i == uris.length * 2;
        return result;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        HttpClient client =  newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .build();
        return TRACKER.track(client);
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

    static final List<String> BODIES = List.of(
            "Lorem ipsum",
            "dolor sit amet",
            "consectetur adipiscing elit, sed do eiusmod tempor",
            "quis nostrud exercitation ullamco",
            "laboris nisi",
            "ut",
            "aliquip ex ea commodo consequat." +
                    "Duis aute irure dolor in reprehenderit in voluptate velit esse" +
                    "cillum dolore eu fugiat nulla pariatur.",
            "Excepteur sint occaecat cupidatat non proident."
    );

    static BodyPublisher[] publishers(String... content) {
        if (content == null) return null;
        BodyPublisher[] result = new BodyPublisher[content.length];
        for (int i=0; i < content.length ; i++) {
            result[i] = content[i] == null ? null : BodyPublishers.ofString(content[i]);
        }
        return result;
    }

    static String[] strings(String... s) {
        return s;
    }

    static Object[][] nulls() {
        return new Object[][] {
                {"null array", null},
                {"null element", strings((String)null)},
                {"null first element", strings(null, "one")},
                {"null second element", strings( "one", null)},
                {"null third element", strings( "one", "two", null)},
                {"null fourth element", strings( "one", "two", "three", null)},
                {"null random element", strings( "one", "two", "three", null, "five")},
        };
    }

    static List<Long> lengths(long... lengths) {
        return LongStream.of(lengths)
                .mapToObj(Long::valueOf)
                .collect(Collectors.toList());
    }

    static Object[][] contentLengths() {
        return new Object[][] {
                {-1, lengths(-1)},
                {-42, lengths(-42)},
                {42, lengths(42)},
                {42, lengths(10, 0, 20, 0, 12)},
                {-1, lengths(10, 0, 20, -1, 12)},
                {-1, lengths(-1, 0, 20, 10, 12)},
                {-1, lengths(10, 0, 20, 12, -1)},
                {-1, lengths(10, 0, 20, -10, 12)},
                {-1, lengths(-10, 0, 20, 10, 12)},
                {-1, lengths(10, 0, 20, 12, -10)},
                {-1, lengths(10, 0, Long.MIN_VALUE, -1, 12)},
                {-1, lengths(-1, 0, Long.MIN_VALUE, 10, 12)},
                {-1, lengths(10, Long.MIN_VALUE, 20, 12, -1)},
                {Long.MAX_VALUE, lengths(10, Long.MAX_VALUE - 42L, 20, 0, 12)},
                {-1, lengths(10, Long.MAX_VALUE - 40L, 20, 0, 12)},
                {-1, lengths(10, Long.MAX_VALUE - 12L, 20, 0, 12)},
                {-1, lengths(10, Long.MAX_VALUE/2L, Long.MAX_VALUE/2L + 1L, 0, 12)},
                {-1, lengths(10, Long.MAX_VALUE/2L, -1, Long.MAX_VALUE/2L + 1L, 12)},
                {-1, lengths(10, Long.MAX_VALUE, 12, Long.MAX_VALUE, 20)},
                {-1, lengths(10, Long.MAX_VALUE, Long.MAX_VALUE, 12, 20)},
                {-1, lengths(0, Long.MAX_VALUE, Long.MAX_VALUE, 12, 20)},
                {-1, lengths(Long.MAX_VALUE, Long.MAX_VALUE, 12, 0, 20)}
        };
    }

    static Object[][] negativeRequests() {
        return new Object[][] {
                {0L}, {-1L}, {-2L}, {Long.MIN_VALUE + 1L}, {Long.MIN_VALUE}
        };
    }


    static class ContentLengthPublisher implements BodyPublisher {
        final long length;
        ContentLengthPublisher(long length) {
            this.length = length;
        }
        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
        }

        static ContentLengthPublisher[] of(List<Long> lengths) {
            return lengths.stream()
                    .map(ContentLengthPublisher::new)
                    .toArray(ContentLengthPublisher[]::new);
        }
    }

    /**
     * A dummy publisher that allows to call onError on its subscriber (or not...).
     */
    static class PublishWithError implements BodyPublisher {
        final ConcurrentHashMap<Subscriber<?>, ErrorSubscription> subscribers = new ConcurrentHashMap<>();
        final long length;
        final List<String> content;
        final int errorAt;
        final Supplier<? extends Throwable> errorSupplier;
        PublishWithError(List<String> content, int errorAt, Supplier<? extends Throwable> supplier) {
            this.content = content;
            this.errorAt = errorAt;
            this.errorSupplier = supplier;
            length = content.stream().mapToInt(String::length).sum();
        }

        boolean hasErrors() {
            return errorAt < content.size();
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            ErrorSubscription subscription = new ErrorSubscription(subscriber);
            subscribers.put(subscriber, subscription);
            subscriber.onSubscribe(subscription);
        }

        class ErrorSubscription implements Flow.Subscription {
            volatile boolean cancelled;
            volatile int at;
            final Subscriber<? super ByteBuffer> subscriber;
            ErrorSubscription(Subscriber<? super ByteBuffer> subscriber) {
                this.subscriber = subscriber;
            }
            @Override
            public void request(long n) {
                while (!cancelled && --n >= 0 && at < Math.min(errorAt+1, content.size())) {
                    if (at++ == errorAt) {
                        subscriber.onError(errorSupplier.get());
                        return;
                    } else if (at <= content.size()){
                        subscriber.onNext(ByteBuffer.wrap(
                                content.get(at-1).getBytes()));
                        if (at == content.size()) {
                            subscriber.onComplete();
                            return;
                        }
                    }
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }

    static class RequestSubscriber implements Flow.Subscriber<ByteBuffer> {
        final CompletableFuture<Subscription> subscriptionCF = new CompletableFuture<>();
        final ConcurrentLinkedDeque<ByteBuffer> items = new ConcurrentLinkedDeque<>();
        final CompletableFuture<List<ByteBuffer>> resultCF = new CompletableFuture<>();

        final Semaphore semaphore = new Semaphore(0);

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscriptionCF.complete(subscription);
        }

        @Override
        public void onNext(ByteBuffer item) {
            items.addLast(item);
            int available = semaphore.availablePermits();
            if (available > Integer.MAX_VALUE - 8) {
                onError(new IllegalStateException("too many buffers in queue: " + available));
            }
            semaphore.release();
        }

        @Override
        public void onError(Throwable throwable) {
            resultCF.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            resultCF.complete(items.stream().collect(Collectors.toUnmodifiableList()));
        }

        public ByteBuffer take() {
            // it is not guaranteed that the buffer will be added to
            // the queue in the same thread that calls request(1).
            try {
                semaphore.acquire();
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                throw new CompletionException(x);
            }
            return items.pop();
        }

        CompletableFuture<List<ByteBuffer>> resultCF() { return resultCF; }
    }

    static String stringFromBuffer(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes);
    }

    String stringFromBytes(Stream<ByteBuffer> buffers) {
        return buffers.map(AggregateRequestBodyTest::stringFromBuffer)
                .collect(Collectors.joining());
    }

    static PublishWithError withNoError(String content) {
        return new PublishWithError(List.of(content), 1,
                () -> new AssertionError("Should not happen!"));
    }

    static PublishWithError withNoError(List<String> content) {
        return new PublishWithError(content, content.size(),
                () -> new AssertionError("Should not happen!"));
    }

    @ParameterizedTest // checks that NPE is thrown
    @MethodSource("nulls")
    public void testNullPointerException(String description, String[] content) {
        out.printf("%n%s-- testNullPointerException %s%n%n", now(), description);
        BodyPublisher[] publishers = publishers(content);
        Assertions.assertThrows(NullPointerException.class, () -> BodyPublishers.concat(publishers));
    }

    // Verifies that an empty array creates a "noBody" publisher
    @Test
    public void testEmpty() {
        out.printf("%n%s-- testEmpty%n%n", now());
        BodyPublisher publisher = BodyPublishers.concat();
        RequestSubscriber subscriber = new RequestSubscriber();
        assertEquals(0, publisher.contentLength());
        publisher.subscribe(subscriber);
        subscriber.subscriptionCF.thenAccept(s -> s.request(1));
        List<ByteBuffer> result = subscriber.resultCF.join();
        assertEquals(List.of(), result);
        assertTrue(subscriber.items.isEmpty());
    }

    // verifies that error emitted by upstream publishers are propagated downstream.
    @ParameterizedTest // nulls are replaced with error publisher
    @MethodSource("nulls")
    public void testOnError(String description, String[] content) {
        out.printf("%n%s-- testOnError %s%n%n", now(), description);
        final RequestSubscriber subscriber = new RequestSubscriber();
        final PublishWithError errorPublisher;
        final BodyPublisher[] publishers;
        String result = BODIES.stream().collect(Collectors.joining());
        if (content == null) {
            content = List.of(result).toArray(String[]::new);
            errorPublisher = new PublishWithError(BODIES, BODIES.size(),
                    () -> new AssertionError("Unexpected!!"));
            publishers = List.of(errorPublisher).toArray(new BodyPublisher[0]);
            description = "No error";
        } else {
            publishers = publishers(content);
            description = description.replace("null", "error at");
            errorPublisher = new PublishWithError(BODIES, 2, () -> new Exception("expected"));
        }
        result = "";
        boolean hasErrors = false;
        for (int i=0; i < content.length; i++) {
            if (content[i] == null) {
                publishers[i] = errorPublisher;
                if (hasErrors) continue;
                if (!errorPublisher.hasErrors()) {
                    result = result + errorPublisher
                            .content.stream().collect(Collectors.joining());
                } else {
                    result = result + errorPublisher.content
                            .stream().limit(errorPublisher.errorAt)
                            .collect(Collectors.joining());
                    result = result + "<error>";
                    hasErrors = true;
                }
            } else if (!hasErrors) {
                result = result + content[i];
            }
        }
        BodyPublisher publisher = BodyPublishers.concat(publishers);
        publisher.subscribe(subscriber);
        subscriber.subscriptionCF.thenAccept(s -> s.request(Long.MAX_VALUE));
        if (errorPublisher.hasErrors()) {
            CompletionException ce = Assertions.assertThrows(CompletionException.class,
                    () -> subscriber.resultCF.join());
            out.println(description + ": got expected " + ce);
            assertEquals(Exception.class, ce.getCause().getClass());
            assertEquals(result, stringFromBytes(subscriber.items.stream()) + "<error>");
        } else {
            assertEquals(result, stringFromBytes(subscriber.resultCF.join().stream()));
            out.println(description + ": got expected result: " + result);
        }
    }

    // Verifies that if an upstream publisher has an unknown length, the
    // aggregate publisher will have an unknown length as well. Otherwise
    // the length should be known.
    @ParameterizedTest // nulls are replaced with unknown length
    @MethodSource("nulls")
    public void testUnknownContentLength(String description, String[] content) {
        out.printf("%n%s-- testUnknownContentLength %s%n%n", now(), description);
        if (content == null) {
            content = BODIES.toArray(String[]::new);
            description = "BODIES (known length)";
        } else {
            description = description.replace("null", "length(-1)");
        }
        BodyPublisher[] publishers = publishers(content);
        BodyPublisher nolength = new BodyPublisher() {
            final BodyPublisher missing = BodyPublishers.ofString("missing");
            @Override
            public long contentLength() { return -1; }
            @Override
            public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
                missing.subscribe(subscriber);
            }
        };
        long length = 0;
        for (int i=0; i < content.length; i++) {
            if (content[i] == null) {
                publishers[i] = nolength;
                length = -1;
            } else if (length >= 0) {
                length += content[i].length();
            }
        }
        out.printf("%stestUnknownContentLength(%s): %d%n", now(), description, length);
        BodyPublisher publisher = BodyPublishers.concat(publishers);
        assertEquals(length, publisher.contentLength(),
                description.replace("null", "length(-1)"));
    }

    private static final Throwable completionCause(CompletionException x) {
        while (x.getCause() instanceof CompletionException) {
            x = (CompletionException)x.getCause();
        }
        return x.getCause();
    }

    @ParameterizedTest
    @MethodSource("negativeRequests")
    public void testNegativeRequest(long n) {
        out.printf("%n%s-- testNegativeRequest %s%n%n", now(), n);
        assert n <= 0 : "test for negative request called with n > 0 : " + n;
        BodyPublisher[] publishers = ContentLengthPublisher.of(List.of(1L, 2L, 3L));
        BodyPublisher publisher = BodyPublishers.concat(publishers);
        RequestSubscriber subscriber = new RequestSubscriber();
        publisher.subscribe(subscriber);
        Subscription subscription = subscriber.subscriptionCF.join();
        subscription.request(n);
        CompletionException expected = Assertions.assertThrows(CE, () -> subscriber.resultCF.join());
        Throwable cause = completionCause(expected);
        if (cause instanceof IllegalArgumentException) {
            System.out.printf("Got expected IAE for %d: %s%n", n, cause);
        } else {
            throw new AssertionError("Unexpected exception: " + cause,
                    (cause == null) ? expected : cause);
        }
    }

    static BodyPublisher[] ofStrings(String... strings) {
        return Stream.of(strings).map(BodyPublishers::ofString).toArray(BodyPublisher[]::new);
    }

    @Test
    public void testPositiveRequests()  {
        out.printf("%n%s-- testPositiveRequests%n%n", now());
        // A composite array of publishers
        BodyPublisher[] publishers = Stream.of(
                Stream.of(ofStrings("Lorem", " ", "ipsum", " ")),
                Stream.of(BodyPublishers.concat(ofStrings("dolor", " ", "sit", " ", "amet", ", "))),
                Stream.<BodyPublisher>of(withNoError(List.of("consectetur", " ", "adipiscing"))),
                Stream.of(ofStrings(" ")),
                Stream.of(BodyPublishers.concat(ofStrings("elit", ".")))
        ).flatMap((s) -> s).toArray(BodyPublisher[]::new);
        BodyPublisher publisher = BodyPublishers.concat(publishers);

        // Test that we can request all 13 items in a single request call.
        RequestSubscriber requestSubscriber1 = new RequestSubscriber();
        publisher.subscribe(requestSubscriber1);
        Subscription subscription1 = requestSubscriber1.subscriptionCF.join();
        subscription1.request(16);
        // onNext() may not be called in the same thread than request()
        List<ByteBuffer> list1 = requestSubscriber1.resultCF().join();
        assertTrue(requestSubscriber1.resultCF().isDone());
        String result1 = stringFromBytes(list1.stream());
        assertEquals("Lorem ipsum dolor sit amet, consectetur adipiscing elit.", result1);
        System.out.println("Got expected sentence with one request: \"%s\"".formatted(result1));

        // Test that we can split our requests call any which way we want
        // (whether in the 'middle of a publisher' or at the boundaries.
        RequestSubscriber requestSubscriber2 = new RequestSubscriber();
        publisher.subscribe(requestSubscriber2);
        Subscription subscription2 = requestSubscriber2.subscriptionCF.join();
        subscription2.request(1);
        assertFalse(requestSubscriber2.resultCF().isDone());
        subscription2.request(10);
        assertFalse(requestSubscriber2.resultCF().isDone());
        subscription2.request(4);
        assertFalse(requestSubscriber2.resultCF().isDone());
        subscription2.request(1);
        List<ByteBuffer> list2 = requestSubscriber2.resultCF().join();
        assertTrue(requestSubscriber2.resultCF().isDone());
        String result2 = stringFromBytes(list2.stream());
        assertEquals("Lorem ipsum dolor sit amet, consectetur adipiscing elit.", result2);
        System.out.println("Got expected sentence with 4 requests: \"%s\"".formatted(result1));
    }

    @ParameterizedTest
    @MethodSource("contentLengths")
    public void testContentLength(long expected, List<Long> lengths) {
        out.printf("%n%s-- testContentLength expected=%s %s%n%n", now(), expected, lengths);
        BodyPublisher[] publishers = ContentLengthPublisher.of(lengths);
        BodyPublisher aggregate = BodyPublishers.concat(publishers);
        assertEquals(expected, aggregate.contentLength(),
                "Unexpected result for %s".formatted(lengths));
    }

    // Verifies that cancelling the subscription ensure that downstream
    // publishers are no longer subscribed etc...
    @Test
    public void testCancel() {
        out.printf("%n%s-- testCancel%n%n", now());
        BodyPublisher[] publishers = BODIES.stream()
                .map(BodyPublishers::ofString)
                .toArray(BodyPublisher[]::new);
        BodyPublisher publisher = BodyPublishers.concat(publishers);

        assertEquals(BODIES.stream().mapToInt(String::length).sum(), publisher.contentLength());
        Map<RequestSubscriber, String> subscribers = new LinkedHashMap<>();

        for (int n=0; n < BODIES.size(); n++) {

            String description = String.format(
                    "cancel after %d/%d onNext() invocations",
                    n, BODIES.size());
            RequestSubscriber subscriber = new RequestSubscriber();
            publisher.subscribe(subscriber);
            Subscription subscription = subscriber.subscriptionCF.join();
            subscribers.put(subscriber, description);

            // receive half the data
            for (int i = 0; i < n; i++) {
                subscription.request(1);
                ByteBuffer buffer = subscriber.take();
            }

            // cancel subscription
            subscription.cancel();
            // request the rest...
            subscription.request(Long.MAX_VALUE);
        }

        CompletableFuture[] results = subscribers.keySet()
                .stream().map(RequestSubscriber::resultCF)
                .toArray(CompletableFuture[]::new);
        CompletableFuture<?> any = CompletableFuture.anyOf(results);

        // subscription was cancelled, so nothing should be received...
        try {
            TimeoutException x = Assertions.assertThrows(TimeoutException.class,
                    () -> any.get(5, TimeUnit.SECONDS));
            out.println("Got expected " + x);
        } finally {
            subscribers.keySet().stream()
                    .filter(rs -> rs.resultCF.isDone())
                    .forEach(rs -> System.err.printf(
                            "Failed: %s completed with %s",
                            subscribers.get(rs), rs.resultCF));
        }
        Consumer<RequestSubscriber> check = (rs) -> {
            assertTrue(rs.items.isEmpty(), subscribers.get(rs) + " has items");
            assertFalse(rs.resultCF.isDone(), subscribers.get(rs) + " was not cancelled");
            out.println(subscribers.get(rs) + ": PASSED");
        };
        subscribers.keySet().stream().forEach(check);
    }

    // Verifies that cancelling the subscription is propagated downstream
    @Test
    public void testCancelSubscription() {
        out.printf("%n%s-- testCancelSubscription%n%n", now());
        PublishWithError upstream = new PublishWithError(BODIES, BODIES.size(),
                () -> new AssertionError("should not come here"));
        BodyPublisher publisher = BodyPublishers.concat(upstream);

        assertEquals(BODIES.stream().mapToInt(String::length).sum(), publisher.contentLength());
        Map<RequestSubscriber, String> subscribers = new LinkedHashMap<>();

        for (int n=0; n < BODIES.size(); n++) {

            String description = String.format(
                    "cancel after %d/%d onNext() invocations",
                    n, BODIES.size());
            RequestSubscriber subscriber = new RequestSubscriber();
            publisher.subscribe(subscriber);
            Subscription subscription = subscriber.subscriptionCF.join();
            subscribers.put(subscriber, description);

            // receive half the data
            for (int i = 0; i < n; i++) {
                subscription.request(1);
                ByteBuffer buffer = subscriber.items.pop();
            }

            // cancel subscription
            subscription.cancel();
            // request the rest...
            subscription.request(Long.MAX_VALUE);
            assertTrue(upstream.subscribers.get(subscriber).cancelled,
                    description + " upstream subscription not cancelled");
            out.println(description + " upstream subscription was properly cancelled");
        }

        CompletableFuture[] results = subscribers.keySet()
                .stream().map(RequestSubscriber::resultCF)
                .toArray(CompletableFuture[]::new);
        CompletableFuture<?> any = CompletableFuture.anyOf(results);

        // subscription was cancelled, so nothing should be received...
        try {
            TimeoutException x = Assertions.assertThrows(TimeoutException.class,
                    () -> any.get(5, TimeUnit.SECONDS));
            out.println("Got expected " + x);
        } finally {
            subscribers.keySet().stream()
                    .filter(rs -> rs.resultCF.isDone())
                    .forEach(rs -> System.err.printf(
                            "Failed: %s completed with %s",
                            subscribers.get(rs), rs.resultCF));
        }
        Consumer<RequestSubscriber> check = (rs) -> {
            assertTrue(rs.items.isEmpty(), subscribers.get(rs) + " has items");
            assertFalse(rs.resultCF.isDone(), subscribers.get(rs) + " was not cancelled");
            out.println(subscribers.get(rs) + ": PASSED");
        };
        subscribers.keySet().stream().forEach(check);

    }

    @ParameterizedTest
    @MethodSource("variants")
    public void test(URI uri, HttpClient.Version version, boolean sameClient) throws Exception {
        out.printf("%n%s-- test sameClient=%s, version=%s, uri=%s%n%n",
                now(), sameClient, version, uri);
        System.out.printf("Request to %s (sameClient: %s)%n", uri, sameClient);
        System.err.printf("Request to %s (sameClient: %s)%n", uri, sameClient);

        HttpClient client = newHttpClient(sameClient);

        BodyPublisher publisher = BodyPublishers.concat(
                BODIES.stream()
                        .map(BodyPublishers::ofString)
                        .toArray(HttpRequest.BodyPublisher[]::new)
                );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .version(version)
                .POST(publisher)
                .build();

        for (int i = 0; i < ITERATION_COUNT; i++) {
            System.out.println(uri + ": Iteration: " + i);
            System.err.println(uri + ": Iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            int expectedResponse =  RESPONSE_CODE;
            if (response.statusCode() != expectedResponse)
                throw new RuntimeException("wrong response code " + Integer.toString(response.statusCode()));
            assertEquals(BODIES.stream().collect(Collectors.joining()), response.body());
        }
        if (!sameClient) client.close();
        System.out.println("test: DONE");
    }

    private static URI buildURI(String scheme, String path, int port) {
        return URIBuilder.newBuilder()
                .scheme(scheme)
                .loopback()
                .port(port)
                .path(path)
                .buildUnchecked();
    }

    @BeforeAll
    public static void setup() throws Exception {
        HttpTestHandler handler = new HttpTestEchoHandler();
        http1TestServer = HttpTestServer.create(HTTP_1_1);
        http1TestServer.addHandler(handler, "/http1/echo/");
        http1URI = buildURI("http", "/http1/echo/x", http1TestServer.getAddress().getPort());

        https1TestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        https1TestServer.addHandler(handler, "/https1/echo/");
        https1URI = buildURI("https", "/https1/echo/x", https1TestServer.getAddress().getPort());

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(handler, "/http2/echo/");
        http2URI = buildURI("http", "/http2/echo/x", http2TestServer.getAddress().getPort());

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(handler, "/https2/echo/");
        https2URI = buildURI("https", "/https2/echo/x", https2TestServer.getAddress().getPort());

        http3TestServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        http3TestServer.addHandler(handler, "/http3/echo/");
        http3URI = buildURI("https", "/http3/echo/x", http3TestServer.getAddress().getPort());

        serverCount.addAndGet(5);
        http1TestServer.start();
        https1TestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        String sharedClientName =
                sharedClient == null ? null : sharedClient.toString();
        sharedClient.close();
        sharedClient = null;
        Thread.sleep(100);
        AssertionError fail = TRACKER.check(500);
        try {
            http1TestServer.stop();
            https1TestServer.stop();
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
    }
}
