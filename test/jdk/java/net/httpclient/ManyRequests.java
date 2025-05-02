/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087112 8180044 8256459
 * @modules java.net.http
 *          java.logging
 *          jdk.httpserver
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/EchoHandler.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @run main/othervm/timeout=40 -Djdk.tracePinnedThreads=full
 *                              -Djdk.httpclient.HttpClient.log=ssl,channel ManyRequests
 * @run main/othervm/timeout=40 -Djdk.tracePinnedThreads=full
 *                              -Djdk.httpclient.HttpClient.log=channel -Dtest.insertDelay=true ManyRequests
 * @run main/othervm/timeout=40 -Djdk.tracePinnedThreads=full
 *                              -Djdk.httpclient.HttpClient.log=channel -Dtest.chunkSize=64 ManyRequests
 * @run main/othervm/timeout=40 -Djdk.tracePinnedThreads=full
 *                              -Djdk.httpclient.HttpClient.log=channel -Dtest.insertDelay=true -Dtest.chunkSize=64 ManyRequests
 * @summary Send a large number of requests asynchronously
 */
 // * @run main/othervm/timeout=40 -Djdk.httpclient.HttpClient.log=ssl,channel ManyRequests

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;

import jdk.test.lib.Platform;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

public class ManyRequests {

    static final int MAX_COUNT = 50;
    static final int MAX_LIMIT = 40;
    static final AtomicInteger COUNT = new AtomicInteger();
    static final AtomicInteger LIMIT = new AtomicInteger(MAX_LIMIT);
    static final Random RANDOM = RandomFactory.getRandom();

    public static void main(String[] args) throws Exception {
        Logger logger = Logger.getLogger("com.sun.net.httpserver");
        logger.setLevel(Level.ALL);
        logger.info("TEST");
        Stream.of(Logger.getLogger("").getHandlers()).forEach((h) -> h.setLevel(Level.ALL));
        String osName = System.getProperty("os.name", "");
        System.out.println("Running on: " + osName);
        System.out.println("Sending " + REQUESTS
                         + " requests; delay=" + INSERT_DELAY
                         + ", chunks=" + CHUNK_SIZE
                         + ", XFixed=" + XFIXED);
        SSLContext ctx = new SimpleSSLContext().get();

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        HttpsServer server = HttpsServer.create(addr, 0);
        ExecutorService executor = executorFor("HTTPS/1.1 Server Thread");
        server.setHttpsConfigurator(new Configurator(ctx));
        server.setExecutor(executor);
        ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("HttpClient-Worker", 0).factory());

        HttpClient client = HttpClient.newBuilder()
                                      .proxy(Builder.NO_PROXY)
                                      .sslContext(ctx)
                                      .executor(virtualExecutor)
                                      .connectTimeout(Duration.ofMillis(120_000)) // 2mins
                                      .build();
        try {
            test(server, client);
            System.out.println("OK");
        } finally {
            client.close();
            server.stop(0);
            virtualExecutor.close();
            executor.shutdownNow();
        }
    }

    //static final int REQUESTS = 1000;
    static final int REQUESTS = MAX_COUNT;
    static final boolean INSERT_DELAY = Boolean.getBoolean("test.insertDelay");
    static final int CHUNK_SIZE = Math.max(0,
           Integer.parseInt(System.getProperty("test.chunkSize", "0")));
    static final boolean XFIXED = Boolean.getBoolean("test.XFixed");

    static class TestEchoHandler extends EchoHandler {
        final Random rand = RANDOM;
        @Override
        public void handle(HttpExchange e) throws IOException {
            System.out.println("Server: received " + e.getRequestURI());
            super.handle(e);
        }
        @Override
        protected void close(HttpExchange t, OutputStream os) throws IOException {
            if (INSERT_DELAY) {
                try { Thread.sleep(rand.nextInt(200)); }
                catch (InterruptedException e) {}
            }
            System.out.println("Server: close outbound: " + t.getRequestURI());
            super.close(t, os);
        }
        @Override
        protected void close(HttpExchange t, InputStream is) throws IOException {
            if (INSERT_DELAY) {
                try { Thread.sleep(rand.nextInt(200)); }
                catch (InterruptedException e) {}
            }
            System.out.println("Server: close inbound: " + t.getRequestURI());
            super.close(t, is);
        }
    }

    static String now(long start) {
        long elapsed = System.nanoTime() - start;
        long ms = elapsed / 1000_000L;
        long s = ms / 1000L;
        if (s == 0) return ms + "ms: ";
        return s + "s, " + (ms - s * 1000L) + "ms: ";
    }

    static String failure(Throwable t) {
        String s = "\n\t failed: " + t;
        for (t = t.getCause(); t != null ; t = t.getCause()) {
            s = s + "\n\t\t  Caused by: " + t;
        }
        return s;
    }

    static void test(HttpsServer server, HttpClient client) throws Exception {
        int port = server.getAddress().getPort();

        URI baseURI = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(port)
                .path("/foo/x").build();
        server.createContext("/foo", new TestEchoHandler());
        server.start();

        // This loop implements a retry mechanism to work around an issue
        // on some systems (observed on Windows 10) that seem to be trying to
        // throttle the number of connections that can be made concurrently by
        // rejecting connection attempts.
        // On the first iteration of this loop, we will attempt 20 concurrent
        // requests. If this fails with ConnectException, we will retry the
        // 20 requests, but limiting the concurrency to 10 (LIMIT <- 10).
        // If this fails again, the test will fail.
        boolean done = false;
        LOOP: do {
            RequestLimiter limiter = new RequestLimiter(LIMIT.get());
            Random rand = RANDOM;
            CompletableFuture<?>[] results = new CompletableFuture<?>[REQUESTS];
            Map<HttpRequest,byte[]> bodies = new ConcurrentHashMap<>();

            long start = System.nanoTime();

            for (int i = 0; i < REQUESTS; i++) {
                byte[] buf = new byte[(i + 1) * CHUNK_SIZE + i + 1];  // different size bodies
                rand.nextBytes(buf);
                URI uri = new URI(baseURI.toString() + String.valueOf(i + 1));
                HttpRequest r = HttpRequest.newBuilder(uri)
                        .header("XFixed", "true")
                        .POST(BodyPublishers.ofByteArray(buf))
                        .build();
                bodies.put(r, buf);

                results[i] =
                        limiter.whenOkToSend()
                                .thenCompose((v) -> {
                                    System.out.println("Client: sendAsync: " + r.uri());
                                    return client.sendAsync(r, BodyHandlers.ofByteArray());
                                })
                                .handle((resp, t) -> {
                                    limiter.requestComplete();
                                    CompletionStage<Pair<HttpResponse<byte[]>, byte[]>> res;
                                    String now = now(start);
                                    if (t == null) {
                                        if (resp.statusCode() != 200) {
                                            String s = "Expected 200, got: " + resp.statusCode();
                                            System.out.println(now + s + " from "
                                                    + resp.request().uri().getPath());
                                            res = completedWithIOException(s);
                                            return res;
                                        } else {
                                            int counter = COUNT.incrementAndGet();
                                            System.out.println(now + "Result (" + counter + ") from "
                                                    + resp.request().uri().getPath());
                                        }
                                        res = CompletableFuture.completedStage(resp.body())
                                                .thenApply((b) -> new Pair<>(resp, b));
                                        return res;
                                    } else {
                                        int counter = COUNT.incrementAndGet();
                                        System.out.println(now + "Result (" + counter + ") from "
                                                + r.uri().getPath()
                                                + failure(t));
                                        res = CompletableFuture.failedFuture(t);
                                        return res;
                                    }
                                })
                                .thenCompose(c -> c)
                                .thenAccept((pair) -> {
                                    HttpRequest request = pair.t.request();
                                    byte[] requestBody = bodies.get(request);
                                    check(Arrays.equals(requestBody, pair.u),
                                            "bodies not equal:[" + bytesToHexString(requestBody)
                                                    + "] [" + bytesToHexString(pair.u) + "]");

                                });
            }

            // wait for them all to complete and throw exception in case of err
            try {
                CompletableFuture.allOf(results).join();
                done = true;
            } catch (CompletionException e) {
                if (!Platform.isWindows()) throw e;
                if (LIMIT.get() < MAX_LIMIT) throw e;
                Throwable cause = e;
                while ((cause = cause.getCause()) != null) {
                    if (cause instanceof ConnectException) {
                        // try again, limit concurrency by half
                        COUNT.set(0);
                        LIMIT.set(LIMIT.get()/2);
                        System.out.println("*** Retrying due to " + cause);
                        continue LOOP;
                    }
                }
                throw e;
            }
        } while (!done);

    }

    static <T> CompletableFuture<T> completedWithIOException(String message) {
        return CompletableFuture.failedFuture(new IOException(message));
    }

    static String bytesToHexString(byte[] bytes) {
        if (bytes == null)
            return "null";

        StringBuilder sb = new StringBuilder(bytes.length * 2);

        Formatter formatter = new Formatter(sb);
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }

        return sb.toString();
    }

    record Pair<T,U>(T t, U u) { }

    /**
     * A simple limiter for controlling the number of requests to be run in
     * parallel whenOkToSend() is called which returns a CF<Void> that allows
     * each individual request to proceed, or block temporarily (blocking occurs
     * on the waiters list here. As each request actually completes
     * requestComplete() is called to notify this object, and allow some
     * requests to continue.
     */
    static class RequestLimiter {

        static final CompletableFuture<Void> COMPLETED_FUTURE =
                CompletableFuture.completedFuture(null);

        final int maxnumber;
        final LinkedList<CompletableFuture<Void>> waiters;
        int number;
        boolean blocked;

        RequestLimiter(int maximum) {
            waiters = new LinkedList<>();
            maxnumber = maximum;
        }

        synchronized void requestComplete() {
            number--;
            // don't unblock until number of requests has halved.
            if ((blocked && number <= maxnumber / 2) ||
                        (!blocked && waiters.size() > 0)) {
                int toRelease = Math.min(maxnumber - number, waiters.size());
                for (int i=0; i<toRelease && !waiters.isEmpty(); i++) {
                    CompletableFuture<Void> f = waiters.remove();
                    number ++;
                    f.complete(null);
                }
                blocked = number >= maxnumber;
            }
        }

        synchronized CompletableFuture<Void> whenOkToSend() {
            if (blocked || number + 1 >= maxnumber) {
                blocked = true;
                CompletableFuture<Void> r = new CompletableFuture<>();
                waiters.add(r);
                return r;
            } else {
                number++;
                return COMPLETED_FUTURE;
            }
        }
    }

    static void check(boolean cond, Object... msg) {
        if (cond)
            return;
        StringBuilder sb = new StringBuilder();
        for (Object o : msg)
            sb.append(o);
        throw new RuntimeException(sb.toString());
    }

    static class Configurator extends HttpsConfigurator {
        public Configurator(SSLContext ctx) {
            super(ctx);
        }

        public void configure(HttpsParameters params) {
            params.setSSLParameters(getSSLContext().getSupportedSSLParameters());
        }
    }

    private static ExecutorService executorFor(String serverThreadName) {
        ThreadFactory factory = new ThreadFactory() {
            final AtomicInteger counter = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(serverThreadName + "#" + counter.incrementAndGet());
                return thread;
            }
        };
        return Executors.newCachedThreadPool(factory);
    }

}
