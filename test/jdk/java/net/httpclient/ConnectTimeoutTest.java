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

import jdk.test.lib.Utils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.time.Duration.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test id=sync
 * @bug 8208391 8375352
 * @summary Verifies behavior on `connect()` timeouts
 * @requires os.family != "windows"
 * @library /test/lib
 * @run junit/othervm ${test.main.class}
 */

/*
 * @test id=sync-proxy
 * @bug 8208391 8375352
 * @summary Verifies behavior on `connect()` timeouts
 * @requires os.family != "windows"
 * @library /test/lib
 * @run junit/othervm -Dtest.proxy=true ${test.main.class}
 */

/*
 * @test id=async
 * @bug 8208391 8375352
 * @summary Verifies behavior on `connect()` timeouts
 * @requires os.family != "windows"
 * @library /test/lib
 * @run junit/othervm -Dtest.async=true ${test.main.class}
 */

/*
 * @test id=async-proxy
 * @bug 8208391 8375352
 * @summary Verifies behavior on `connect()` timeouts
 * @requires os.family != "windows"
 * @library /test/lib
 * @run junit/othervm -Dtest.async=true -Dtest.proxy=true ${test.main.class}
 */

class ConnectTimeoutTest {

    // This test verifies the `HttpClient` behavior on `connect()` failures.
    //
    // Earlier, the test was trying to connect `example.com:8080` to trigger a `connect()` failure.
    // This worked, until it doesn't — `example.com:8080` started responding in certain test environments.
    //
    // Now we create a `ServerSocket` and exhaust all its "SYN backlog" and "Accept queue".
    // The expectation is that the platform socket in this state will block on `connect()`.
    // Well... It doesn't on Windows, whereas it does on Linux and macOS.
    // Windows doesn't block and immediately responds with `java.net.ConnectException: Connection refused: connect`.
    // Neither it is deterministic how many connections are needed to exhaust a socket admission queue.
    // Hence, we took the following decisions:
    //
    // 1. Skip this test on Windows
    // 2. Exhaust server socket admission queue by going into a loop

    private static final PrintStream LOGGER = System.out;

    private static final int BACKLOG = 1;

    /**
     * A {@link ServerSocket} whose admission will be blocked by exhausting all its "SYN backlog" and "Accept queue".
     */
    private static final ServerSocket SERVER_SOCKET = createServerSocket();

    /**
     * Client sockets exhausting the admission to {@link #SERVER_SOCKET}.
     */
    private static final List<Socket> CLIENT_SOCKETS = createClientSocketsExhaustingServerSocketAdmission();

    private static ServerSocket createServerSocket() {
        try {
            LOGGER.println("Creating server socket");
            return new ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static List<Socket> createClientSocketsExhaustingServerSocketAdmission() {
        List<Socket> sockets = new ArrayList<>();
        int maxSocketCount = BACKLOG   // To fill up the backlog
                + 512;                 // Giving some slack, should be enough to exhaust the admission queue.
        int connectTimeout = Math.toIntExact(Math.addExact(500, Utils.adjustTimeout(500)));
        int socketIndex = 0;
        for (; socketIndex < maxSocketCount; socketIndex++) {
            try {
                LOGGER.printf(
                        "Creating client socket %s/%s to exhaust the server socket admission%n",
                        (socketIndex + 1), maxSocketCount);
                Socket socket = new Socket();
                socket.connect(SERVER_SOCKET.getLocalSocketAddress(), connectTimeout);
                sockets.add(socket);
            } catch (ConnectException | SocketTimeoutException exception) {
                LOGGER.printf(
                        "Received expected `%s` while creating client socket %s/%s%n",
                        exception.getClass().getName(), (socketIndex + 1), maxSocketCount);
                return sockets;
            } catch (IOException ioe) {
                String message = String.format(
                        "Received unexpected exception while creating client socket %s/%s",
                        (socketIndex + 1), maxSocketCount);
                closeSockets(SERVER_SOCKET, sockets);
                throw new RuntimeException(message, ioe);
            }
        }
        String message = String.format(
                "Connected %s sockets, but still could not exhaust the socket admission",
                maxSocketCount);
        closeSockets(SERVER_SOCKET, sockets);
        throw new RuntimeException(message);
    }

    @AfterAll
    public static void closeSockets() {
        closeSockets(SERVER_SOCKET, CLIENT_SOCKETS);
    }

    private static void closeSockets(ServerSocket serverSocket, List<Socket> clientSockets) {
        Throwable[] throwable = {null};
        Stream.concat(clientSockets.stream(), Stream.of(serverSocket)).forEach(closeable -> {
            try {
                closeable.close();
            } catch (Exception exception) {
                if (throwable[0] == null) {
                    throwable[0] = exception;
                } else {
                    throwable[0].addSuppressed(exception);
                }
            }
        });
        if (throwable[0] != null) {
            throwable[0].printStackTrace(System.out);
        }
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
        ProxySelector proxySelector = parseBoolean(System.getProperty("test.proxy")) ? PROXY_SELECTOR : NO_PROXY;
        boolean async = parseBoolean(System.getProperty("test.async"));
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
            LOGGER.printf("iteration %d%n", i);
            long startTime = System.nanoTime();
            try {
                HttpResponse<?> resp = client.send(request, BodyHandlers.ofString());
                printResponse(resp);
                fail("Unexpected response: " + resp);
            } catch (HttpConnectTimeoutException expected) { // blocking thread-specific exception
                long elapsedTime = NANOSECONDS.toMillis(System.nanoTime() - startTime);
                LOGGER.printf("Client: received in %d millis%n", elapsedTime);
                assertExceptionTypeAndCause(expected.getCause());
            } catch (ConnectException e) {
                long elapsedTime = NANOSECONDS.toMillis(System.nanoTime() - startTime);
                LOGGER.printf("Client: received in %d millis%n", elapsedTime);
                Throwable t = e.getCause().getCause();  // blocking thread-specific exception
                e.printStackTrace(LOGGER);
                fail("Unexpected exception:" + e);
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
            LOGGER.printf("iteration %d%n", i);
            long startTime = System.nanoTime();
            try {
                HttpResponse<?> resp = client.sendAsync(request, BodyHandlers.ofString()).join();
                printResponse(resp);
                fail("Unexpected response: " + resp);
            } catch (CompletionException e) {
                long elapsedTime = NANOSECONDS.toMillis(System.nanoTime() - startTime);
                LOGGER.printf("Client: received in %d millis%n", elapsedTime);
                Throwable t = e.getCause();
                assertExceptionTypeAndCause(t);
            }
        }
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
            t.printStackTrace(LOGGER);
            fail("Expected HttpConnectTimeoutException, got:" + t);
        }
        Throwable connEx = t.getCause();
        if (!(connEx instanceof ConnectException)) {
            t.printStackTrace(LOGGER);
            fail("Expected ConnectException cause in:" + connEx);
        }
        LOGGER.printf("Caught expected HttpConnectTimeoutException with ConnectException"
                + " cause: %n%s%n%s%n", t, connEx);
        final String EXPECTED_MESSAGE = "HTTP connect timed out"; // impl dependent
        if (!connEx.getMessage().equals(EXPECTED_MESSAGE))
            fail("Expected: \"" + EXPECTED_MESSAGE + "\", got: \"" + connEx.getMessage() + "\"");

    }

    private static void printResponse(HttpResponse<?> response) {
        LOGGER.println("Unexpected response: " + response);
        LOGGER.println("Headers: " + response.headers());
        LOGGER.println("Body: " + response.body());
    }

}
