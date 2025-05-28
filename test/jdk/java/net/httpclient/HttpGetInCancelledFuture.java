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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jdk.internal.net.http.common.OperationTrackers.Tracker;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8316580
 * @library /test/lib
 * @run junit/othervm -DuseReferenceTracker=false
 *                   HttpGetInCancelledFuture
 * @run junit/othervm -DuseReferenceTracker=true
 *                   HttpGetInCancelledFuture
 * @summary This test verifies that cancelling a future that
 * does an HTTP request using the HttpClient doesn't cause
 * HttpClient::close to block forever.
 */
public class HttpGetInCancelledFuture {

    static final boolean useTracker = Boolean.getBoolean("useReferenceTracker");

    static final class TestException extends RuntimeException {
        public TestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    HttpClient makeClient(URI uri, Version version, Executor executor) {
        var builder = HttpClient.newBuilder();
        if (uri.getScheme().equalsIgnoreCase("https")) {
            try {
                builder.sslContext(new SimpleSSLContext().get());
            } catch (IOException io) {
                throw new UncheckedIOException(io);
            }
        }
        return builder.connectTimeout(Duration.ofSeconds(1))
                .executor(executor)
                .version(version)
                .build();
    }

    record TestCase(String url, int reqCount, Version version) {}
    // A server that doesn't accept
    static volatile ServerSocket NOT_ACCEPTING;

    static List<TestCase> parameters() {
        ServerSocket ss = NOT_ACCEPTING;
        if (ss == null) {
            synchronized (HttpGetInCancelledFuture.class) {
                if ((ss = NOT_ACCEPTING) == null) {
                    try {
                        ss = new ServerSocket();
                        var loopback = InetAddress.getLoopbackAddress();
                        ss.bind(new InetSocketAddress(loopback, 0), 10);
                        NOT_ACCEPTING = ss;
                    } catch (IOException io) {
                        throw new UncheckedIOException(io);
                    }
                }
            }
        }
        URI http = URIBuilder.newBuilder()
                .loopback()
                .scheme("http")
                .port(ss.getLocalPort())
                .path("/not-accepting/")
                .buildUnchecked();
        URI https = URIBuilder.newBuilder()
                .loopback()
                .scheme("https")
                .port(ss.getLocalPort())
                .path("/not-accepting/")
                .buildUnchecked();
        // use all HTTP versions, without and with TLS
        return List.of(
                new TestCase(http.toString(), 200, Version.HTTP_2),
                new TestCase(http.toString(), 200, Version.HTTP_1_1),
                new TestCase(https.toString(), 200, Version.HTTP_2),
                new TestCase(https.toString(), 200, Version.HTTP_1_1)
                );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void runTest(TestCase test) {
        System.out.println("Testing with: " + test);
        runTest(test.url, test.reqCount, test.version);
    }

    static class TestTaskScope implements AutoCloseable {
        final ExecutorService pool = new ForkJoinPool();
        final Map<Task<?>, Future<?>> tasks = new ConcurrentHashMap<>();
        final AtomicReference<ExecutionException> failed = new AtomicReference<>();

        class Task<T> implements Callable<T> {
            final Callable<T> task;
            final CompletableFuture<T> cf = new CompletableFuture<>();
            Task(Callable<T> task) {
                this.task = task;
            }
            @Override
            public T call() throws Exception {
                try {
                    var res = task.call();
                    cf.complete(res);
                    return res;
                } catch (Throwable t) {
                    cf.completeExceptionally(t);
                    throw t;
                }
            }
            CompletableFuture<T> cf() {
                return cf;
            }
        }


        static class ShutdownOnFailure extends TestTaskScope {
            public ShutdownOnFailure() {}

            @Override
            protected <T> void completed(Task<T> task, T result, Throwable throwable) {
                super.completed(task, result, throwable);
                if (throwable != null) {
                    if (failed.get() == null) {
                        ExecutionException ex = throwable instanceof ExecutionException x
                                ? x : new ExecutionException(throwable);
                        failed.compareAndSet(null, ex);
                    }
                    tasks.entrySet().forEach(this::cancel);
                }
            }

            void cancel(Map.Entry<Task<?>, Future<?>> entry) {
                entry.getValue().cancel(true);
                entry.getKey().cf().cancel(true);
                tasks.remove(entry.getKey(), entry.getValue());
            }

            @Override
            public <T> CompletableFuture<T> fork(Callable<T> callable) {
                var ex = failed.get();
                if (ex == null) {
                    return super.fork(callable);
                } // otherwise do nothing
                return CompletableFuture.failedFuture(new RejectedExecutionException());
            }
        }

        public <T> CompletableFuture<T> fork(Callable<T> callable) {
            var task = new Task<>(callable);
            var res = pool.submit(task);
            tasks.put(task, res);
            task.cf.whenComplete((r,t) -> completed(task, r, t));
            return task.cf;
        }

        protected <T> void completed(Task<T> task, T result, Throwable throwable) {
            tasks.remove(task);
        }

        public void join() throws InterruptedException {
            try {
                var cfs = tasks.keySet().stream()
                        .map(Task::cf).toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(cfs).get();
            } catch (InterruptedException it) {
                throw it;
            } catch (ExecutionException ex) {
                failed.compareAndSet(null, ex);
            }
        }

        public void throwIfFailed() throws ExecutionException {
            ExecutionException x = failed.get();
            if (x != null) throw x;
        }

        public void close() {
            pool.close();
        }
    }

    ExecutorService testExecutor() {
        return Executors.newCachedThreadPool();
    }

    void runTest(String url, int reqCount, Version version) {
        final var dest = URI.create(url);
        try (final var executor = testExecutor()) {
            var httpClient = makeClient(dest, version, executor);
            TRACKER.track(httpClient);
            Tracker tracker = TRACKER.getTracker(httpClient);
            Throwable failed = null;
            try {
                try (final var scope = new TestTaskScope.ShutdownOnFailure()) {
                    launchAndProcessRequests(scope, httpClient, reqCount, dest);
                } finally {
                    System.out.printf("StructuredTaskScope closed: STARTED=%s, SUCCESS=%s, INTERRUPT=%s, FAILED=%s%n",
                            STARTED.get(), SUCCESS.get(), INTERRUPT.get(), FAILED.get());
                }
                System.out.println("ERROR: Expected TestException not thrown");
                throw new AssertionError("Expected TestException not thrown");
            } catch (TestException x) {
                System.out.println("Got expected exception: " + x);
            } catch (Throwable t) {
                System.out.println("ERROR: Unexpected exception: " + t);
                failed = t;
                throw t;
            } finally {
                // we can either use the tracker or call HttpClient::close
                if (useTracker) {
                    // using the tracker depends on GC but will give us some diagnostic
                    // if some operations are not properly cancelled and prevent the client
                    // from terminating
                    httpClient = null;
                    System.gc();
                    System.out.println(TRACKER.diagnose(tracker));
                    var error = TRACKER.check(tracker, 10000);
                    if (error != null) {
                        if (failed != null) error.addSuppressed(failed);
                        EXCEPTIONS.forEach(x -> {
                            System.out.println("FAILED: " + x);
                        });
                        EXCEPTIONS.forEach(x -> {
                            x.printStackTrace(System.out);
                        });
                        throw error;
                    }
                } else {
                    // if not all operations terminate, close() will block
                    // forever and the test will fail in jtreg timeout.
                    // there will be no diagnostic.
                    httpClient.close();
                }
                System.out.println("HttpClient closed");
            }
        } finally {
            System.out.println("ThreadExecutor closed");
        }
        // not all tasks may have been started before the scope was cancelled
        // due to the first connect/timeout exception, but all tasks that started
        // must have either succeeded, be interrupted, or failed
        assertTrue(STARTED.get() > 0);
        assertEquals(STARTED.get(), SUCCESS.get() + INTERRUPT.get() + FAILED.get());
        if (SUCCESS.get() > 0) {
            // we don't expect any server to be listening and responding
            System.out.println("WARNING: got some unexpected successful responses from "
                    + "\"" + NOT_ACCEPTING.getLocalSocketAddress() + "\": " + SUCCESS.get());
        }
    }

    private void launchAndProcessRequests(
            TestTaskScope.ShutdownOnFailure scope,
            HttpClient httpClient,
            int reqCount,
            URI dest) {
        for (int counter = 0; counter < reqCount; counter++) {
            scope.fork(() ->
                    getAndCheck(httpClient, dest)
            );
        }
        try {
            scope.join();
        } catch (InterruptedException e) {
            throw new AssertionError("scope.join() was interrupted", e);
        }
        try {
            scope.throwIfFailed();
        } catch (ExecutionException e) {
            throw new TestException("something threw an exception in StructuredTaskScope", e);
        }
    }

    final static AtomicLong ID = new AtomicLong();
    final AtomicLong SUCCESS = new AtomicLong();
    final AtomicLong INTERRUPT = new AtomicLong();
    final AtomicLong FAILED = new AtomicLong();
    final AtomicLong STARTED = new AtomicLong();
    final CopyOnWriteArrayList<Exception> EXCEPTIONS = new CopyOnWriteArrayList<>();
    private String getAndCheck(HttpClient httpClient, URI url) {
        STARTED.incrementAndGet();
        final var response = sendRequest(httpClient, url);
        String res = response.body();
        int statusCode = response.statusCode();
        assertEquals(200, statusCode);
        return res;
    }

    private HttpResponse<String> sendRequest(HttpClient httpClient, URI url) {
        var id = ID.incrementAndGet();
        try {
            var request = HttpRequest.newBuilder(url).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // System.out.println("Got response for " + id + ": " + response);
            SUCCESS.incrementAndGet();
            return response;
        } catch (InterruptedException e) {
            INTERRUPT.incrementAndGet();
            // System.out.println("Got interrupted for " + id + ": " + e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            FAILED.incrementAndGet();
            EXCEPTIONS.add(e);
            //System.out.println("Got exception for " + id + ": " + e);
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void tearDown() {
        try {
            System.gc();
            var error = TRACKER.check(5000);
            if (error != null) throw error;
        } finally {
            ServerSocket ss;
            synchronized (HttpGetInCancelledFuture.class) {
                ss = NOT_ACCEPTING;
                NOT_ACCEPTING = null;
            }
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException io) {
                    throw new UncheckedIOException(io);
                }
            }
        }
    }
}

