/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.StackWalker.StackFrame;
import java.net.URI;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscribers;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import javax.net.ssl.SSLContext;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @bug 8193365 8317295
 * @summary Basic tests for Flow adapter Subscribers
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.internal.httpclient.debug=true FlowAdapterSubscriberTest
 */

public class FlowAdapterSubscriberTest implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;     // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;    // HTTPS/1.1
    HttpTestServer http2TestServer;    // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;   // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    static final StackWalker WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    @DataProvider(name = "uris")
    public Object[][] variants() {
        return new Object[][]{
                { httpURI   },
                { httpsURI  },
                { http2URI  },
                { https2URI },
        };
    }

    static final Class<NullPointerException> NPE = NullPointerException.class;

    private static Version version(String uri) {
        if (uri.contains("/http1/")) return Version.HTTP_1_1;
        if (uri.contains("/https1/")) return Version.HTTP_1_1;
        if (uri.contains("/http2/")) return Version.HTTP_2;
        if (uri.contains("/https2/")) return Version.HTTP_2;
        return null;
    }

    private HttpClient newHttpClient(String uri) {
        var builder = HttpClient.newBuilder();
        return builder.sslContext(sslContext).proxy(Builder.NO_PROXY).build();
    }

    private HttpRequest.Builder newRequestBuilder(String uri) {
        return HttpRequest.newBuilder(URI.create(uri));
    }

    @Test
    public void testNull() {
        System.out.printf(now() + "testNull() starting%n");
        assertThrows(NPE, () -> BodyHandlers.fromSubscriber(null));
        assertThrows(NPE, () -> BodyHandlers.fromSubscriber(null, Function.identity()));
        assertThrows(NPE, () -> BodyHandlers.fromSubscriber(new ListSubscriber(), null));
        assertThrows(NPE, () -> BodyHandlers.fromSubscriber(null, null));

        assertThrows(NPE, () -> BodySubscribers.fromSubscriber(null));
        assertThrows(NPE, () -> BodySubscribers.fromSubscriber(null, Function.identity()));
        assertThrows(NPE, () -> BodySubscribers.fromSubscriber(new ListSubscriber(), null));
        assertThrows(NPE, () -> BodySubscribers.fromSubscriber(null, null));

        Subscriber subscriber = BodySubscribers.fromSubscriber(new ListSubscriber());
        assertThrows(NPE, () -> subscriber.onSubscribe(null));
        assertThrows(NPE, () -> subscriber.onNext(null));
        assertThrows(NPE, () -> subscriber.onError(null));
    }

    // List<ByteBuffer>

    @Test(dataProvider = "uris")
    void testListWithFinisher(String uri) {
        System.out.printf(now() + "testListWithFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the luck of the Irish be with you!")).build();

            ListSubscriber subscriber = new ListSubscriber();
            HttpResponse<String> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber, Supplier::get)).join();
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "May the luck of the Irish be with you!");
        }
    }

    @Test(dataProvider = "uris")
    void testListWithoutFinisher(String uri) {
        System.out.printf(now() + "testListWithoutFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the luck of the Irish be with you!")).build();

            ListSubscriber subscriber = new ListSubscriber();
            HttpResponse<Void> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber)).join();
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "May the luck of the Irish be with you!");
        }
    }

    @Test(dataProvider = "uris")
    void testListWithFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testListWithFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the luck of the Irish be with you!")).build();

            ListSubscriber subscriber = new ListSubscriber();
            HttpResponse<String> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber, Supplier::get));
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "May the luck of the Irish be with you!");
        }
    }

    @Test(dataProvider = "uris")
    void testListWithoutFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testListWithoutFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the luck of the Irish be with you!")).build();

            ListSubscriber subscriber = new ListSubscriber();
            HttpResponse<Void> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber));
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "May the luck of the Irish be with you!");
        }
    }

    // Collection<ByteBuffer>

    @Test(dataProvider = "uris")
    void testCollectionWithFinisher(String uri) {
        System.out.printf(now() + "testCollectionWithFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("What's the craic?")).build();

            CollectionSubscriber subscriber = new CollectionSubscriber();
            HttpResponse<String> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber, CollectionSubscriber::get)).join();
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "What's the craic?");
        }
    }

    @Test(dataProvider = "uris")
    void testCollectionWithoutFinisher(String uri) {
        System.out.printf(now() + "testCollectionWithoutFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("What's the craic?")).build();

            CollectionSubscriber subscriber = new CollectionSubscriber();
            HttpResponse<Void> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber)).join();
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "What's the craic?");
        }
    }

    @Test(dataProvider = "uris")
    void testCollectionWithFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testCollectionWithFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("What's the craic?")).build();

            CollectionSubscriber subscriber = new CollectionSubscriber();
            HttpResponse<String> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber, CollectionSubscriber::get));
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "What's the craic?");
        }
    }

    @Test(dataProvider = "uris")
    void testCollectionWithoutFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testCollectionWithoutFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("What's the craic?")).build();

            CollectionSubscriber subscriber = new CollectionSubscriber();
            HttpResponse<Void> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber));
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "What's the craic?");
        }
    }

    // Iterable<ByteBuffer>

    @Test(dataProvider = "uris")
    void testIterableWithFinisher(String uri) {
        System.out.printf(now() + "testIterableWithFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("We're sucking diesel now!")).build();

            IterableSubscriber subscriber = new IterableSubscriber();
            HttpResponse<String> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber, Supplier::get)).join();
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "We're sucking diesel now!");
        }
    }

    @Test(dataProvider = "uris")
    void testIterableWithoutFinisher(String uri) {
        System.out.printf(now() + "testIterableWithoutFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("We're sucking diesel now!")).build();

            IterableSubscriber subscriber = new IterableSubscriber();
            HttpResponse<Void> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber)).join();
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "We're sucking diesel now!");
        }
    }

    @Test(dataProvider = "uris")
    void testIterableWithFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testIterableWithFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("We're sucking diesel now!")).build();

            IterableSubscriber subscriber = new IterableSubscriber();
            HttpResponse<String> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber, Supplier::get));
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "We're sucking diesel now!");
        }
    }

    @Test(dataProvider = "uris")
    void testIterableWithoutFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testIterableWithoutFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("We're sucking diesel now!")).build();

            IterableSubscriber subscriber = new IterableSubscriber();
            HttpResponse<Void> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber));
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertEquals(text, "We're sucking diesel now!");
        }
    }

    // Subscriber<Object>

    @Test(dataProvider = "uris")
    void testObjectWithFinisher(String uri) {
        System.out.printf(now() + "testObjectWithFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the wind always be at your back.")).build();

            ObjectSubscriber subscriber = new ObjectSubscriber();
            HttpResponse<String> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber, ObjectSubscriber::get)).join();
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertTrue(text.length() != 0);  // what else can be asserted!
        }
    }

    @Test(dataProvider = "uris")
    void testObjectWithoutFinisher(String uri) {
        System.out.printf(now() + "testObjectWithoutFinisher(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the wind always be at your back.")).build();

            ObjectSubscriber subscriber = new ObjectSubscriber();
            HttpResponse<Void> response = client.sendAsync(request,
                    BodyHandlers.fromSubscriber(subscriber)).join();
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertTrue(text.length() != 0);  // what else can be asserted!
        }
    }

    @Test(dataProvider = "uris")
    void testObjectWithFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testObjectWithFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the wind always be at your back.")).build();

            ObjectSubscriber subscriber = new ObjectSubscriber();
            HttpResponse<String> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber, ObjectSubscriber::get));
            String text = response.body();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertTrue(text.length() != 0);  // what else can be asserted!
        }
    }

    @Test(dataProvider = "uris")
    void testObjectWithoutFinisherBlocking(String uri) throws Exception {
        System.out.printf(now() + "testObjectWithoutFinisherBlocking(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the wind always be at your back.")).build();

            ObjectSubscriber subscriber = new ObjectSubscriber();
            HttpResponse<Void> response = client.send(request,
                    BodyHandlers.fromSubscriber(subscriber));
            String text = subscriber.get();
            System.out.println(text);
            assertEquals(response.statusCode(), 200);
            assertEquals(response.version(), version(uri));
            assertTrue(text.length() != 0);  // what else can be asserted!
        }
    }


    // -- mapping using convenience handlers

    @Test(dataProvider = "uris")
    void mappingFromByteArray(String uri) throws Exception {
        System.out.printf(now() + "mappingFromByteArray(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("We're sucking diesel now!")).build();

            client.sendAsync(request, BodyHandlers.fromSubscriber(BodySubscribers.ofByteArray(),
                            bas -> new String(bas.getBody().toCompletableFuture().join(), UTF_8)))
                    .thenApply(FlowAdapterSubscriberTest::assert200ResponseCode)
                    .thenApply(HttpResponse::body)
                    .thenAccept(body -> assertEquals(body, "We're sucking diesel now!"))
                    .join();
        }
    }

    @Test(dataProvider = "uris")
    void mappingFromInputStream(String uri) throws Exception {
        System.out.printf(now() + "mappingFromInputStream(%s) starting%n", uri);
        try (HttpClient client = newHttpClient(uri)) {
            HttpRequest request = newRequestBuilder(uri)
                    .POST(BodyPublishers.ofString("May the wind always be at your back.")).build();

            var adaptee = BodySubscribers.ofInputStream();
            var exec = Executors.newSingleThreadExecutor();

            // Use an executor to pull on the InputStream in order to reach the
            // point where the Subscriber gets completed and the finisher function
            // is called. If we didn't use an executor here, the finisher function
            // may never get called.
            var futureResult = exec.submit(() -> uncheckedReadAllBytes(
                    adaptee.getBody().toCompletableFuture().join()));
            Supplier<byte[]> bytes = () -> {
                try {
                    return futureResult.get();
                } catch (InterruptedException e) {
                    throw new CompletionException(e);
                } catch (ExecutionException e) {
                    throw new CompletionException(e.getCause());
                }
            };

            AtomicReference<AssertionError> failed = new AtomicReference<>();
            Function<? super Flow.Subscriber<List<ByteBuffer>>, String> finisher = (s) -> {
                failed.set(checkThreadAndStack());
                return new String(bytes.get(), UTF_8);
            };

            try {
                var cf = client.sendAsync(request, BodyHandlers.fromSubscriber(adaptee,
                                finisher))
                        .thenApply(FlowAdapterSubscriberTest::assert200ResponseCode)
                        .thenApply(HttpResponse::body)
                        .thenAccept(body -> assertEquals(body, "May the wind always be at your back."))
                        .join();
                var error = failed.get();
                if (error != null) throw error;
            } finally {
                exec.close();
            }
        }
    }

    static final Predicate<StackFrame> DAT = sfe ->
            sfe.getClassName().startsWith("FlowAdapterSubscriberTest");
    static final Predicate<StackFrame> JUC = sfe ->
            sfe.getClassName().startsWith("java.util.concurrent");
    static final Predicate<StackFrame> JLT = sfe ->
            sfe.getClassName().startsWith("java.lang.Thread");
    static final Predicate<StackFrame> RSP = sfe ->
            sfe.getClassName().startsWith("jdk.internal.net.http.ResponseSubscribers");
    static final Predicate<StackFrame> NotDATorJUCorJLT = Predicate.not(DAT.or(JUC).or(JLT).or(RSP));


    AssertionError checkThreadAndStack() {
        System.out.println("Check stack trace");
        List<StackFrame> otherFrames = WALKER.walk(s -> s.filter(NotDATorJUCorJLT).toList());
        if (!otherFrames.isEmpty()) {
            System.out.println("Found unexpected trace: ");
            otherFrames.forEach(f -> System.out.printf("\t%s%n", f));
            return new AssertionError("Dependant action has unexpected frame in " +
                    Thread.currentThread() + ": " + otherFrames.get(0));
        }
        return null;
    }

    /** An abstract Subscriber that converts all received data into a String. */
    static abstract class AbstractSubscriber implements Supplier<String> {
        protected volatile Flow.Subscription subscription;
        protected volatile ByteArrayOutputStream baos = new ByteArrayOutputStream();
        protected volatile String text;

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }
        public void onError(Throwable throwable) {
            throw new RuntimeException(throwable);
        }
        public void onComplete() {
            text = new String(baos.toByteArray(), UTF_8);
        }
        @Override public String get() { return text; }
    }

    static class ListSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<List<ByteBuffer>>, Supplier<String>
    {
        @Override public void onNext(List<ByteBuffer> item) {
            for (ByteBuffer bb : item) {
                byte[] ba = new byte[bb.remaining()];
                bb.get(ba);
                uncheckedWrite(baos, ba);
            }
        }
    }

    static class CollectionSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<Collection<ByteBuffer>>, Supplier<String>
    {
        @Override public void onNext(Collection<ByteBuffer> item) {
            for (ByteBuffer bb : item) {
                byte[] ba = new byte[bb.remaining()];
                bb.get(ba);
                uncheckedWrite(baos, ba);
            }
        }
    }

    static class IterableSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<Iterable<ByteBuffer>>, Supplier<String>
    {
        @Override public void onNext(Iterable<ByteBuffer> item) {
            for (ByteBuffer bb : item) {
                byte[] ba = new byte[bb.remaining()];
                bb.get(ba);
                uncheckedWrite(baos, ba);
            }
        }
    }

    static class ObjectSubscriber extends AbstractSubscriber
        implements Flow.Subscriber<Object>, Supplier<String>
    {
        @Override public void onNext(Object item) {
            // What can anyone do with Object, cast or toString it ?
            uncheckedWrite(baos, item.toString().getBytes(UTF_8));
        }
    }

    static void uncheckedWrite(ByteArrayOutputStream baos, byte[] ba) {
        try {
            baos.write(ba);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] uncheckedReadAllBytes(InputStream is) {
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final <T> HttpResponse<T> assert200ResponseCode(HttpResponse<T> response) {
        assertEquals(response.statusCode(), 200);
        assertEquals(response.version(), version(response.request().uri().toString()));
        return response;
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        httpTestServer = HttpTestServer.create(Version.HTTP_1_1);
        httpTestServer.addHandler(new HttpEchoHandler(), "/http1/echo");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/echo";

        httpsTestServer = HttpTestServer.create(Version.HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new HttpEchoHandler(), "/https1/echo");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/echo";

        http2TestServer = HttpTestServer.create(Version.HTTP_2);
        http2TestServer.addHandler(new HttpEchoHandler(), "/http2/echo");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/echo";

        https2TestServer = HttpTestServer.create(Version.HTTP_2, sslContext);
        https2TestServer.addHandler(new HttpEchoHandler(), "/https2/echo");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/echo";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static class HttpEchoHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
