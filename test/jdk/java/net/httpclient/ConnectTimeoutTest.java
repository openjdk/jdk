/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.time.Duration.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8208391 8375352
 * @summary Verifies behavior on `connect()` timeouts
 * @run junit/othervm ${test.main.class}
 * @run junit/othervm -Dtest.proxy ${test.main.class}
 * @run junit/othervm -Dtest.async ${test.main.class}
 * @run junit/othervm -Dtest.async -Dtest.proxy ${test.main.class}
 */

class ConnectTimeoutTest {

    private static final int BACKLOG = 1;

    /**
     * A {@link ServerSocket} whose admission will be blocked by exhausting all its backlog.
     */
    private static final ServerSocket SERVER_SOCKET = createServerSocket();

    /**
     * Client sockets exhausting the admission to {@link #SERVER_SOCKET}.
     */
    private static final List<Socket> CLIENT_SOCKETS = createClientSockets();

    private static ServerSocket createServerSocket() {
        try {
            System.err.println("Creating server socket");
            return new ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static List<Socket> createClientSockets() {
        int socketCount = BACKLOG   // to fill up the backlog
                + 1;                // to connect
        return IntStream
                .range(0, socketCount)
                .mapToObj(socketIndex -> {
                    try {
                        System.err.printf(
                                "Creating client socket %s/%s to exhaust the server socket admission%n",
                                (socketIndex + 1), socketCount);
                        return new Socket(SERVER_SOCKET.getInetAddress(), SERVER_SOCKET.getLocalPort());
                    } catch (IOException ioe) {
                        String message = String.format(
                                "Failed creating client socket %s/%s",
                                (socketIndex + 1), socketCount);
                        throw new RuntimeException(message, ioe);
                    }
                }).toList();
    }

    @AfterAll
    public static void closeSockets() throws IOException {
        for (Socket CLIENT_SOCKET : CLIENT_SOCKETS) {
            CLIENT_SOCKET.close();
        }
        SERVER_SOCKET.close();
    }

    /**
     * {@link ProxySelector} <em>always</em> pointing to {@link #SERVER_SOCKET}.
     */
    private static final ProxySelector PROXY_SELECTOR = new ProxySelector() {

        private static final List<Proxy> PROXIES =
                List.of(new Proxy(Proxy.Type.HTTP, SERVER_SOCKET.getLocalSocketAddress()));

        @Override
        public List<Proxy> select(URI uri) {
            return PROXIES;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // Do nothing
        }

    };

    private static final Duration NO_DURATION = null;

    private static List<List<Duration>> TIMEOUTS = List.of(
                    // connectTimeout   HttpRequest timeout
            Arrays.asList( NO_DURATION,   ofMillis(100) ),
            Arrays.asList( NO_DURATION,   ofNanos(1)    ),

            Arrays.asList( ofMillis(100), NO_DURATION   ),
            Arrays.asList( ofNanos(1),    NO_DURATION   ),

            Arrays.asList( ofMillis(100), ofMinutes(1)  ),
            Arrays.asList( ofNanos(1),    ofMinutes(1)  )
    );

    private static final List<String> METHODS = List.of("GET", "POST");
    private static final List<Version> VERSIONS = List.of(HTTP_2, HTTP_1_1);
    private static final List<String> SCHEMES = List.of("https", "http");

    static Object[][] variants() {
        List<Object[]> l = new ArrayList<>();
        for (List<Duration> timeouts : TIMEOUTS) {
           Duration connectTimeout = timeouts.get(0);
           Duration requestTimeout = timeouts.get(1);
           for (String method: METHODS) {
            for (String scheme : SCHEMES) {
             for (Version requestVersion : VERSIONS) {
              l.add(new Object[] {requestVersion, scheme, method, connectTimeout, requestTimeout});
        }}}}
        return l.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("variants")
    void test(
            Version requestVersion,
            String scheme,
            String method,
            Duration connectTimeout,
            Duration requestTimeout)
            throws Exception {
        ProxySelector proxySelector = System.getProperty("test.proxy") != null ? PROXY_SELECTOR : NO_PROXY;
        boolean async = System.getProperty("test.async") != null;
        if (async) {
            timeoutAsync(requestVersion, scheme, method, connectTimeout, requestTimeout, proxySelector);
        } else {
            timeoutSync(requestVersion, scheme, method, connectTimeout, requestTimeout, proxySelector);
        }
    }

    private void timeoutSync(Version requestVersion,
                             String scheme,
                             String method,
                             Duration connectTimeout,
                             Duration requestTimeout,
                             ProxySelector proxy)
        throws Exception
    {
        HttpClient client = newClient(connectTimeout, proxy);
        HttpRequest request = newRequest(scheme, requestVersion, method, requestTimeout);

        for (int i = 0; i < 2; i++) {
            System.err.printf("iteration %d%n", i);
            long startTime = System.nanoTime();
            try {
                HttpResponse<?> resp = client.send(request, BodyHandlers.ofString());
                printResponse(resp);
                fail("Unexpected response: " + resp);
            } catch (HttpConnectTimeoutException expected) { // blocking thread-specific exception
                long elapsedTime = NANOSECONDS.toMillis(System.nanoTime() - startTime);
                System.err.printf("Client: received in %d millis%n", elapsedTime);
                assertExceptionTypeAndCause(expected.getCause());
            } catch (ConnectException e) {
                long elapsedTime = NANOSECONDS.toMillis(System.nanoTime() - startTime);
                System.err.printf("Client: received in %d millis%n", elapsedTime);
                Throwable t = e.getCause().getCause();  // blocking thread-specific exception
                if (!isAcceptableCause(t)) { // tolerate only NRTHE or UAE
                    e.printStackTrace(System.err);
                    fail("Unexpected exception:" + e);
                } else {
                    System.err.printf("Caught ConnectException with "
                            + " cause: %s - skipping%n", t.getCause());
                }
            }
        }
    }

    private void timeoutAsync(Version requestVersion,
                              String scheme,
                              String method,
                              Duration connectTimeout,
                              Duration requestTimeout,
                              ProxySelector proxy) {
        HttpClient client = newClient(connectTimeout, proxy);
        HttpRequest request = newRequest(scheme, requestVersion, method, requestTimeout);
        for (int i = 0; i < 2; i++) {
            System.err.printf("iteration %d%n", i);
            long startTime = System.nanoTime();
            try {
                HttpResponse<?> resp = client.sendAsync(request, BodyHandlers.ofString()).join();
                printResponse(resp);
                fail("Unexpected response: " + resp);
            } catch (CompletionException e) {
                long elapsedTime = NANOSECONDS.toMillis(System.nanoTime() - startTime);
                System.err.printf("Client: received in %d millis%n", elapsedTime);
                Throwable t = e.getCause();
                if (t instanceof ConnectException && isAcceptableCause(t.getCause())) {
                    // tolerate only NRTHE and UAE
                    System.err.printf("Caught ConnectException with "
                            + "cause: %s - skipping%n", t.getCause());
                } else {
                    assertExceptionTypeAndCause(t);
                }
            }
        }
    }

    private static boolean isAcceptableCause(Throwable cause) {
        if (cause instanceof NoRouteToHostException) return true;
        if (cause instanceof UnresolvedAddressException) return true;
        return false;
    }

    private static HttpClient newClient(Duration connectTimeout, ProxySelector proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder().proxy(proxy);
        if (connectTimeout != NO_DURATION)
            builder.connectTimeout(connectTimeout);
        return builder.build();
    }

    private static HttpRequest newRequest(String scheme,
                                  Version reqVersion,
                                  String method,
                                  Duration requestTimeout) {
        String hostAddress = SERVER_SOCKET.getInetAddress().getHostAddress();
        int hostPort = SERVER_SOCKET.getLocalPort();
        URI uri = URI.create(scheme + "://" + hostAddress + ':' + hostPort);
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri);
        reqBuilder = reqBuilder.version(reqVersion);
        switch (method) {
            case "GET"   : reqBuilder.GET();                         break;
            case "POST"  : reqBuilder.POST(BodyPublishers.noBody()); break;
            default: throw new AssertionError("Unknown method:" + method);
        }
        if (requestTimeout != NO_DURATION)
            reqBuilder.timeout(requestTimeout);
        return reqBuilder.build();
    }

    private static void assertExceptionTypeAndCause(Throwable t) {
        if (!(t instanceof HttpConnectTimeoutException)) {
            t.printStackTrace(System.err);
            fail("Expected HttpConnectTimeoutException, got:" + t);
        }
        Throwable connEx = t.getCause();
        if (!(connEx instanceof ConnectException)) {
            t.printStackTrace(System.err);
            fail("Expected ConnectException cause in:" + connEx);
        }
        System.err.printf("Caught expected HttpConnectTimeoutException with ConnectException"
                + " cause: %n%s%n%s%n", t, connEx);
        final String EXPECTED_MESSAGE = "HTTP connect timed out"; // impl dependent
        if (!connEx.getMessage().equals(EXPECTED_MESSAGE))
            fail("Expected: \"" + EXPECTED_MESSAGE + "\", got: \"" + connEx.getMessage() + "\"");

    }

    private static void printResponse(HttpResponse<?> response) {
        System.err.println("Unexpected response: " + response);
        System.err.println("Headers: " + response.headers());
        System.err.println("Body: " + response.body());
    }
}
