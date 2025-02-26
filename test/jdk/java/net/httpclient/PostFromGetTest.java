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

/*
 * @test
 * @bug 8350451
 * @summary Test posting back the response body input stream
 *          into a POST request body
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker PostFromGetTest
 * @run testng/othervm -Djdk.httpclient.enableAllMethodRetry=true
 *                     -Djdk.httpclient.windowsize=65536
 *                     -Djdk.httpclient.connectionWindowSize=65536
 *                     PostFromGetTest
 */
import jdk.internal.net.http.common.OperationTrackers.Tracker;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.out;
import static java.lang.System.err;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class PostFromGetTest implements HttpServerAdapters {

    static final int K = 1024;
    static final long H2_STREAM_WINDOW =
            Math.max(16L*K, Long.getLong("jdk.httpclient.windowsize", 16*K*K));
    static final long H2_CONN_WINDOW = Math.max(H2_STREAM_WINDOW,
            Long.getLong("jdk.httpclient.connectionWindowSize", 32*K*K));

    SSLContext sslContext;
    HttpTestServer httpTestServer;    // HTTP/1.1    [ 4 servers ]
    HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    ExecutorService serverExecutor;

    static final int ITERATION_COUNT = 3;
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
        return String.format("[%s s, %s ms, %s ns] ", secs, mill, nan);
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
                    out.printf(now() + "Task %s failed: %s%n", id, t);
                    err.printf(now() + "Task %s failed: %s%n", id, t);
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
                .collect(Collectors.toMap(PostFromGetTest::name, ITestResult::getThrowable));
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
                http2URI,
                https2URI,
                httpsURI,
                httpURI,
        };
    }

    @DataProvider(name = "urls")
    public Object[][] alltests() {
        String[] uris = uris();
        Object[][] result = new Object[uris.length * 2][];
        int i = 0;
        for (boolean sameClient : List.of(true, false)) {
            for (String uri : uris()) {
                String path = sameClient ? "same" : "new";
                result[i++] = new Object[]{uri + path, sameClient};
            }
        }
        assert i == uris.length * 2;
        return result;
    }

    private HttpClient makeNewClient() {
        clientCount.incrementAndGet();
        return TRACKER.track(HttpClient.newBuilder()
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

    static class TestPublisher implements Publisher<ByteBuffer> {
        final Publisher<List<ByteBuffer>> publisher;
        volatile BBListSubscriber bblistsubscriber;
        volatile BBListSubscriber.BBSubscription bbSubscription;
        TestPublisher(Publisher<List<ByteBuffer>> publisher) {
            this.publisher = publisher;
        }

        class BBListSubscriber implements Subscriber<List<ByteBuffer>> {
            final Subscriber<? super ByteBuffer> subscriber;
            BBListSubscriber(Subscriber<? super ByteBuffer> subscriber) {
                this.subscriber = subscriber;
                out.printf("%s: BBListSubscriber(%s) created%n", now(), subscriber);
            }

            final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
            final AtomicReference<Throwable> error = new AtomicReference<>();
            volatile boolean completed;
            final SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(this::deliver);

            class BBSubscription implements Subscription {
                Subscription bblistsubscription;
                AtomicLong pending = new AtomicLong();
                BBSubscription(Subscription subscription) {
                    bblistsubscription = subscription;
                }

                @Override
                public void request(long n) {
                    out.printf("%s: BBSubscription.request(%s)%n", now(), n);
                    boolean requestMore;
                    synchronized (this) {
                        requestMore = pending.getAndAdd(n) == 0 && queue.isEmpty();
                    }
                    if (requestMore) {
                        bblistsubscription.request(1);
                    }
                    if (!queue.isEmpty()) {
                        scheduler.runOrSchedule();
                    }
                }

                @Override
                public void cancel() {
                    bblistsubscription.cancel();
                }
            }

            private void deliver() {
                while (!queue.isEmpty() && bbSubscription.pending.get() != 0) {
                    ByteBuffer bb;
                    synchronized (this) {
                        long n = bbSubscription.pending.decrementAndGet();
                        if (n < 0) {
                            bbSubscription.pending.incrementAndGet();
                            continue;
                        }
                        bb = queue.poll();
                        if (bb == null) {
                            bbSubscription.pending.incrementAndGet();
                            continue;
                        }
                    }
                    out.printf("%s: deliver %s%n", now(), bb.remaining());
                    bblistsubscriber.subscriber.onNext(bb);
                }
                if (queue.isEmpty() && error.get() != null) {
                    bblistsubscriber.subscriber.onError(error.get());
                    scheduler.stop();
                }
                if (queue.isEmpty() && completed) {
                    bblistsubscriber.subscriber.onComplete();
                    scheduler.stop();
                }
                if (queue.isEmpty() && bbSubscription.pending.get() > 0) {
                    bbSubscription.bblistsubscription.request(1);
                }
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                out.printf("%s: BBListSubscriber.onSubscribe(%s)%n", now(), subscription);
                bbSubscription = new BBSubscription(subscription);
                bblistsubscriber.subscriber.onSubscribe(bbSubscription);
            }

            @Override
            public void onNext(List<ByteBuffer> item) {
                out.printf("%s: BBListSubscriber.onNext(List[%s]=%s)%n", now(),
                        item.stream().map(ByteBuffer::remaining).toList(), Utils.remaining(item));
                queue.addAll(item);
                scheduler.runOrSchedule();
            }

            @Override
            public void onError(Throwable throwable) {
                out.printf("%s: BBListSubscriber.onError(%s)%n", now(), throwable);
                throwable.printStackTrace(out);
                error.compareAndSet(null, throwable);
                scheduler.runOrSchedule();
            }

            @Override
            public void onComplete() {
                out.printf("%s: BBListSubscriber.onComplete(queue:%s)%n", now(), queue.size());
                completed = true;
                scheduler.runOrSchedule();
            }
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            this.bblistsubscriber = new BBListSubscriber(subscriber);
            publisher.subscribe(bblistsubscriber);
        }
    }

    @Test(dataProvider = "urls")
    public void testGetPostPublisher(String uri, boolean sameClient)
            throws Exception {
        Supplier<BodyHandler<Publisher<List<ByteBuffer>>>> handlerSuplier =
                BodyHandlers::ofPublisher;
        Function<Publisher<List<ByteBuffer>>, BodyPublisher> publisherSupplier =
                (p) -> BodyPublishers.fromPublisher(new TestPublisher(p));
        testGetPost(uri, sameClient, handlerSuplier, publisherSupplier);
    }

    @Test(dataProvider = "urls")
    public void testGetPostInputStream(String uri, boolean sameClient)
            throws Exception {
        Supplier<BodyHandler<InputStream>> handlerSuplier =
                BodyHandlers::ofInputStream;
        Function<InputStream, BodyPublisher> publisherSupplier =
                (p) -> BodyPublishers.ofInputStream(() -> p);
        testGetPost(uri, sameClient, handlerSuplier, publisherSupplier);
    }

    private <T> void testGetPost(String uri, boolean sameClient,
                            Supplier<BodyHandler<T>> handleSupplier,
                            Function<T, BodyPublisher> publisherSupplier)
            throws Exception {
        checkSkip();
        HttpClient client = null;
        int size = 64 * 1024 * 64;
        String geturi = uri + "/get/?" + size;
        String posturi = uri + "/post/?" + size;
        out.printf("%n%n%s testGetSendAsync(%s, %b)%n", now(), uri, sameClient);
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient(sameClient);
            Tracker tracker = TRACKER.getTracker(client);

            HttpRequest getreq = HttpRequest.newBuilder(URI.create(geturi))
                    .GET()
                    .build();
            HttpResponse<T> getresponse =
                    client.send(getreq, handleSupplier.get());

            HttpRequest postreq = HttpRequest.newBuilder(URI.create(posturi))
                    .POST(publisherSupplier.apply(getresponse.body()))
                    .build();
            HttpResponse<InputStream> postresponse = client.send(postreq, BodyHandlers.ofInputStream());
            int count = 0;
            try (var is = postresponse.body()) {
                int c;
                while ((c = is.read()) >= 0) {
                    assertEquals(c, count % 256);
                    count++;
                    if (count % (8*1024) == 0) {
                        out.printf("Post response: %s/%s bytes, %s%%%n",
                                count, size, (long)((((double)count)/((double)size))*100f));
                    }
                }
                assertEquals(count, size);
            }

            var error = TRACKER.check(tracker, 1000,
                    (t) -> t.getOutstandingOperations() > 0 || t.getOutstandingSubscribers() > 0,
                    "subscribers for testGetSendAsync(%s)\n\t step [%s]".formatted(uri, i),
                    false);
            Reference.reachabilityFence(client);
            if (error != null) throw error;
        }
        assert client != null;
        if (!sameClient) client.close();
    }



    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        serverExecutor = Executors.newCachedThreadPool
                (Thread.ofPlatform().daemon().name("Server").factory());
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");


        // HTTP/1.1
        HttpTestHandler h1_chunkHandler = new H1GetPostHandler();
        httpTestServer = HttpTestServer.create(HTTP_1_1, null, serverExecutor);
        httpTestServer.addHandler(h1_chunkHandler, "/http1/PostFromGet/");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/PostFromGet/";

        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext, serverExecutor);
        httpsTestServer.addHandler(h1_chunkHandler, "/https1/PostFromGet/");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/PostFromGet/";

        // HTTP/2
        HttpTestHandler h2_chunkedHandler = new H2GetPostHandler();

        http2TestServer = HttpTestServer.create(HTTP_2, null, serverExecutor);
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/PostFromGet/");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/PostFromGet/";

        https2TestServer = HttpTestServer.create(HTTP_2, sslContext, serverExecutor);
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/PostFromGet/");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/PostFromGet/";

        serverCount.addAndGet(4);
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
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
            serverExecutor.close();
        } finally {
            if (fail != null) {
                if (sharedClientName != null) {
                    err.println("Shared client name is: " + sharedClientName);
                }
                throw fail;
            }
        }
    }

    /**
     * A handler that returns a body of a given size specified in the URI
     * query parameter upon a GET request, and streams back the request
     * body upon a POST request.
     *
     * When using HTTP/2, this test uses a single connection to perform both
     * requests.
     *
     * The test code (client) pumps the GET response, streams it back to the server,
     * and pump the POST response (which is an echo of the request body).
     * What happens is that the GET bytes will only be pumped if the client can
     * send POST-request bytes. The server will only allow the client to send
     * POST-request bytes if it can process them (that's what triggers sending the
     * WINDOW_UPDATE).
     * So if the connection window (credits given by the client to the server) is all used
     * up by pending GET response bytes, the server will not be able to send POST-response
     * bytes, so it will not be able to process POST-request bytes, which in turn means
     * that the client won’t be able to send more POST-request bytes, which means it won’t
     * be able to pump the GET-response bytes, and therefore everything could get wedged
     * because the GET-response bytes are not being processed, so no WINDOW_UPDATE is sent
     * to the server at that point.
     *
     * To avoid this issue, the handler uses semaphore to ensure that GET response will
     * stop sending bytes before it fills up the whole connection window.
     * The get response will be allowed to resume after some bytes from the POST response
     * have been sent.
     *
     * The get response acquires the semaphore before sending each 8K chunks, and the POST
     * response releases it after sending each 8k chunks.
     * The semaphore is initialized with connectionWindowSize/8K to make sure
     * the get response will pause before the connection window is filled.
     */
    static abstract class HTTPGetPostHandler implements HttpTestHandler {
        final long connectionWindow;
        final Semaphore sem;

        HTTPGetPostHandler(long connectionWindow) {
            this.connectionWindow = connectionWindow;
            sem = new Semaphore((int) (connectionWindow/(8*K)));
            assert sem.availablePermits() > 0;
        }

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (var exch = t) {
                var uri = exch.getRequestURI();
                out.printf("%s: Server HTTPGetPostHandler received request to %s%n", now(), uri);
                err.printf("%n%s: Server HTTPGetPostHandler received request to %s%n", now(), uri);
                long size = Long.parseLong(uri.getQuery());
                boolean post = uri.getPath().contains("/post/");
                long count = 0;
                if (post) {
                    exch.sendResponseHeaders(200, -1);
                    try (var os = new BufferedOutputStream(exch.getResponseBody())) {
                        try (var is = exch.getRequestBody()) {
                            int c;
                            while ((c = is.read()) >= 0) {
                                assertEquals(c, (int) (count % 256));
                                os.write(c);
                                count++;
                                if ((count % (K * 8) == 0)) {
                                    sem.release();
                                }
                            }
                        }
                    }
                    out.printf("%s: Server: echoed %s bytes for %s%n", now(), count, uri);
                } else {
                    try (var is = exch.getRequestBody()) {
                        is.readAllBytes();
                    }
                    exch.sendResponseHeaders(200, size);
                    try (var os = new BufferedOutputStream(exch.getResponseBody())) {
                        while (count < size) {
                            if ((count % (K * 8)) == 0) sem.acquire();
                            int c = (int) (count % 256);
                            os.write(c);
                            count++;
                        }
                    } catch (InterruptedException x) {
                        fail("Unexpected interruption", x);
                    }
                    out.printf("%s: Server: replied %s bytes for %s%n", now(), count, uri);
                }
                err.printf("%s: Server: %s: sent %s bytes%n", now(), uri, count);
                if (count != size) {
                    String msg = "Error: count=%s, size=%s for %s".formatted(count, size, uri);
                    fail(msg);
                }
            }
        }
    }

    static final class H1GetPostHandler extends HTTPGetPostHandler {
        H1GetPostHandler() {
            // We could use any value we want here, but to get
            // comparable results we use same settings as H2
            super(H2_CONN_WINDOW);
        }
    }

    static final class H2GetPostHandler extends HTTPGetPostHandler {
        H2GetPostHandler() {
            super(H2_CONN_WINDOW);
        }
    }

}
