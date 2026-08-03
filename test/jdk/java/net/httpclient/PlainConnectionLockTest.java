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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.TestServerConfigurator;
import jdk.test.lib.net.SimpleSSLContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;

/*
 * @test id=default
 * @bug 8372198
 * @requires os.family != "windows"
 * @summary Attempt to check that no deadlock occurs when
 *          connections are closed by the ConnectionPool.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.httpclient.HttpClient.log=errors
 *              -Djdk.httpclient.connectionPoolSize=1
 *              ${test.main.class}
 */
/*
 * @test id=windows
 * @bug 8372198
 * @requires os.family == "windows"
 * @summary Attempt to check that no deadlock occurs when
 *          connections are closed by the ConnectionPool.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @comment A special jtreg id for windows allows for experimentation
 *          with different configuration - for instance, specifying
 *          -Djdk.internal.httpclient.tcp.selector.useVirtualThreads=<always|never>
 *          on the run command line, or specifying a different request count
 *          with -DrequestCount=<integer>.
 *          On windows, it seems important to set the backlog for the HTTP/1.1
 *          server to at least the number of concurrent request. This is done
 *          in the beforeTest() method.
 *          If the test fails waiting for avalaible permits, due to system limitations,
 *          even with the backlog correctly configure, adding a margin to the backlog
 *          or reducing the requestCount could be envisaged.
 * @run junit/othervm
 *              -Djdk.httpclient.HttpClient.log=errors
 *              -Djdk.httpclient.connectionPoolSize=1
 *              ${test.main.class}
 */

// -Djava.security.debug=all
class PlainConnectionLockTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private HttpTestServer http1Server;
    private HttpTestServer https1Server;
    private String http1URI;
    private String https1URI;
    private ExecutorService serverExecutor;
    private Semaphore responseSemaphore;
    private Semaphore requestSemaphore;
    private boolean successfulCompletion;
    private static final int MANY = Integer.getInteger("requestCount", 100);

    static {
        HttpServerAdapters.enableServerLogging();
    }

    private boolean blockResponse(Semaphore request, Semaphore response) {
        try {
            request.release();
            response.acquire();
            return true;
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();  // Restore the interrupt
            return false;
        }
    }


    @BeforeEach
    synchronized void beforeTest() throws Exception {
        requestSemaphore = new Semaphore(0);
        responseSemaphore = new Semaphore(0);
        successfulCompletion = false;
        serverExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("Http1Server", 0).factory());

        // On Windows, sending 100 concurrent requests may
        // fail if the server's connection backlog is less than 100.
        // The default backlog is 50. Just make sure the backlog is
        // big enough.
        int backlog = Math.max(MANY, 50);

        // create a https server for HTTP/1.1
        var loopback = InetAddress.getLoopbackAddress();
        var wrappedHttps1Server = HttpsServer.create(new InetSocketAddress(loopback, 0), backlog);
        wrappedHttps1Server.setHttpsConfigurator(new TestServerConfigurator(loopback, sslContext));
        https1Server = HttpTestServer.of(wrappedHttps1Server, serverExecutor);
        https1Server.addHandler((exchange) -> {
            if (blockResponse(requestSemaphore, responseSemaphore)) {
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(500, 0);
            }
        }, "/PlainConnectionLockTest/");
        https1Server.start();
        System.out.println("HTTPS Server started at " + https1Server.getAddress());
        https1URI = "https://" + https1Server.serverAuthority() + "/PlainConnectionLockTest/https1";

        // create a plain http server for HTTP/1.1
        var wrappedHttp1Server = HttpServer.create(new InetSocketAddress(loopback, 0), backlog);
        http1Server = HttpTestServer.of(wrappedHttp1Server, serverExecutor);
        http1Server.addHandler((exchange) -> {
            if (blockResponse(requestSemaphore, responseSemaphore)) {
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(500, 0);
            }
        }, "/PlainConnectionLockTest/");
        http1Server.start();
        System.out.println("HTTP Server started at " + http1Server.getAddress());
        http1URI = "http://" + http1Server.serverAuthority() + "/PlainConnectionLockTest/http1";
    }

    @AfterEach
    synchronized void afterTest() throws Exception {
        if (http1Server != null) {
            System.out.println("Stopping HTTP server " + http1Server.getAddress());
            http1Server.stop();
        }
        if (https1Server != null) {
            System.out.println("Stopping HTTPS server " + https1Server.getAddress());
            https1Server.stop();
        }
        if (serverExecutor != null) {
            System.out.println("Closing server executor");
            if (successfulCompletion) {
                serverExecutor.close();
            } else {
                // server handlers may be wedged.
                serverExecutor.shutdownNow();
            }
        }
        requestSemaphore = null;
        responseSemaphore = null;
        serverExecutor = null;
        http1Server = null;
        https1Server = null;
        http1URI = null;
        https1URI = null;
        System.out.println("done\n");
    }

    @Test
    void sendManyHttpRequestsNoShutdown() throws Exception {
        try {
            sendManyRequests(http1URI, MANY, false);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            throw t;
        }
    }

    @Test
    void sendManyHttpRequestsShutdownNow() throws Exception {
        try {
            sendManyRequests(http1URI, MANY, true);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            throw t;
        }
    }

    @Test
    void sendManyHttpsRequestsNoShutdown() throws Exception {
        try {
            sendManyRequests(https1URI, MANY, false);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            throw t;
        }
    }

    @Test
    void sendManyHttpsRequestsShutdownNow() throws Exception {
        try {
            sendManyRequests(https1URI, MANY, true);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            throw t;
        }
    }

    private static void throwCause(CompletionException x) throws Exception {
        var cause = x.getCause();
        if (cause instanceof Exception ex) throw ex;
        if (cause instanceof Error err) throw err;
        throw x;
    }

    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    static Throwable getCause(Throwable exception) {
        if (exception instanceof IOException) return exception;
        if (exception instanceof CancellationException) return exception;
        if (exception instanceof CompletionException) return getCause(exception.getCause());
        if (exception instanceof ExecutionException) return getCause(exception.getCause());
        return exception;
    }

    private synchronized void sendManyRequests(final String requestURI, final int many, boolean shutdown) throws Exception {
        System.out.println("\n%sSending %s requests to %s, shutdown=%s\n".formatted(now(), many, requestURI, shutdown));
        System.err.println("\n%sSending %s requests to %s, shutdown=%s\n".formatted(now(), many, requestURI, shutdown));
        assert many > 0;
        try (final HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .sslContext(sslContext).build()) {
            List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
            final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().version(HTTP_1_1);
            for (int i = 0; i < many; i++) {
                // GET
                final URI reqURI = new URI(requestURI + "?i=" + i);
                final HttpRequest req = reqBuilder.copy().uri(reqURI).GET().build();
                System.out.println(now() + "Issuing request: " + req);
                var cf = client.sendAsync(req, BodyHandlers.ofString());
                futures.add(cf);
            }
            System.out.printf("\n%sWaiting for %s requests to be handled on the server%n", now(), many);

            int count = 0;
            // wait for all exchanges to be handled
            while (!requestSemaphore.tryAcquire(many, 5, TimeUnit.SECONDS)) {
                count++;
                System.out.printf("%sFailed to obtain %s permits after %ss - only %s available%n",
                        now(), many, (count * 5), requestSemaphore.availablePermits());
                for (var cf : futures) {
                    if (cf.isDone() || cf.isCancelled()) {
                        System.out.printf("%sFound some completed cf: %s%n", now(), cf);
                        if (cf.isCancelled()) {
                            System.out.printf("%scf is cancelled: %s%n", now(), cf);
                            client.shutdownNow(); // make sure HttpCient::close won't block waiting for server
                            var error = new AssertionError(now() + "A request cf was cancelled" + cf);
                            System.out.printf("%s throwing: %s%n", now(), error);
                            throw error;
                        }
                        if (cf.isCompletedExceptionally()) {
                            System.out.printf("%scf is completed exceptionally: %s%n", now(), cf);
                            var exception = getCause(cf.exceptionNow());
                            System.out.printf("%sexception is: %s%n", now(), exception);
                            client.shutdownNow(); // make sure HttpCient::close won't block waiting for server
                            exception.printStackTrace(System.out);
                            var error = new AssertionError(now() + "A request failed prematurely", exception);
                            System.out.printf("%s throwing: %s%n", now(), error);
                            throw error;
                        }
                        System.out.printf("%scf is completed prematurely: %s%n", now(), cf);
                        client.shutdownNow(); // make sure HttpCient::close won't block waiting for server
                        var error = new AssertionError(now() + "A request succeeded prematurely: " + cf.join());
                        System.out.printf("%s throwing: %s%n", now(), error);
                        throw error;
                    }
                }
                System.out.printf("%sCouldn't acquire %s permits, only %s available - keep on waiting%n",
                        now(), many, requestSemaphore.availablePermits());
            }

            System.out.println(now() + "All requests reached the server: releasing one response");
            // allow one request to proceed
            responseSemaphore.release();
            try {
                // wait for the first response.
                System.out.println(now() + "Waiting for the first response");
                CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).join();
                System.out.println(now() + "Got first response: " + futures.stream().filter(CompletableFuture::isDone)
                        .findFirst().map(CompletableFuture::join));
                if (shutdown) {
                    System.out.println(now() + "Calling HttpClient::shutdownNow");
                    client.shutdownNow();
                    client.awaitTermination(Duration.ofSeconds(1));
                }
            } finally {
                System.out.printf("%s Releasing %s remaining responses%n", now(), many - 1);
                // now release the others.
                responseSemaphore.release(many - 1);
            }

            // wait for all responses
            System.out.printf("%sWaiting for all %s responses to complete%n", now(), many);
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(t -> null).join();

            // check
            System.out.printf("%sAll %s responses completed. Checking...%n%n", now(), many);
            Set<String> conns = new HashSet<>();
            int exceptionCount = 0;
            int success = 0;
            for (var respCF : futures) {
                try {
                    var resp = respCF.join();
                    Assertions.assertEquals(200, resp.statusCode(),
                            now() + "unexpected response code for GET request: " + resp);
                    Assertions.assertTrue(conns.add(resp.connectionLabel().get()),
                            now() + "unexepected reuse of connection: "
                                    + resp.connectionLabel().get() + " found in " + conns);
                    success++;
                } catch (CompletionException x) {
                    if (shutdown) exceptionCount++;
                    else throwCause(x);
                }
            }
            if (shutdown) {
                if (success == 0) {
                    throw new AssertionError(("%s%s: shutdownNow=%s: Expected at least one response, " +
                            "got success=%s, exceptions=%s").formatted(now(), requestURI, shutdown, success, exceptionCount));
                }
            }
            System.out.println("%sSuccess: %s: shutdownNow:%s, success=%s, exceptions:%s\n"
                    .formatted(now(), requestURI, shutdown, success, exceptionCount));
            successfulCompletion = true;
        }
    }
}
