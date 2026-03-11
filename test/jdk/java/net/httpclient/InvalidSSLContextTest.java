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

/*
 * @test
 * @summary Test to ensure the HTTP client throws an appropriate SSL exception
 *          when SSL context is not valid.
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 * @run junit/othervm -Djdk.internal.httpclient.debug=true InvalidSSLContextTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.net.SocketException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import jdk.test.lib.net.SimpleSSLContext;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class InvalidSSLContextTest {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    static volatile SSLServerSocket sslServerSocket;
    static volatile String uri;

    public static Object[][] versions() {
        return new Object[][]{
                { HTTP_1_1 },
                { HTTP_2   }
        };
    }

    @ParameterizedTest
    @MethodSource("versions")
    public void testSync(Version version) throws Exception {
        // client-side uses a different context to that of the server-side
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .sslContext(SSLContext.getDefault())
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .version(version)
                .build();

        try {
            HttpResponse<?> response = client.send(request, BodyHandlers.discarding());
            Assertions.fail("UNEXPECTED response" + response);
        } catch (IOException ex) {
            System.out.println("Caught expected: " + ex);
            assertExceptionOrCause(SSLException.class, ex);
        }
    }

    @ParameterizedTest
    @MethodSource("versions")
    public void testAsync(Version version) throws Exception {
        // client-side uses a different context to that of the server-side
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .sslContext(SSLContext.getDefault())
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .version(version)
                .build();

        assertExceptionally(SSLException.class,
                            client.sendAsync(request, BodyHandlers.discarding()));
    }

    static void assertExceptionally(Class<? extends Throwable> clazz,
                                    CompletableFuture<?> stage) {
        stage.handle((result, error) -> {
            if (result != null) {
                Assertions.fail("UNEXPECTED result: " + result);
                return null;
            }
            if (error instanceof CompletionException) {
                Throwable cause = error.getCause();
                if (cause == null) {
                    Assertions.fail("Unexpected null cause: " + error);
                }
                assertExceptionOrCause(clazz, cause);
            } else {
                assertExceptionOrCause(clazz, error);
            }
            return null;
        }).join();
    }

    static void assertExceptionOrCause(Class<? extends Throwable> clazz, Throwable t) {
        if (t == null) {
            Assertions.fail("Expected " + clazz + ", caught nothing");
        }
        final Throwable original = t;
        do {
            if (clazz.isInstance(t)) {
                return; // found
            }
        } while ((t = t.getCause()) != null);
        original.printStackTrace(System.out);
        Assertions.fail("Expected " + clazz + "in " + original);
    }

    @BeforeAll
    public static void setup() throws Exception {
        // server-side uses a different context to that of the client-side
        sslServerSocket = (SSLServerSocket)sslContext
                .getServerSocketFactory()
                .createServerSocket();
        sslServerSocket.setReuseAddress(false);
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        sslServerSocket.bind(addr);
        uri = "https://localhost:" + sslServerSocket.getLocalPort() + "/";

        Thread t = new Thread("SSL-Server-Side") {
            @Override
            public void run() {
                while (true) {
                    try {
                        SSLSocket s = (SSLSocket) sslServerSocket.accept();
                        System.out.println("SERVER: accepted: " + s);
                        // artificially slow down the handshake reply to mimic
                        // a slow(ish) network, and hopefully delay the
                        // SequentialScheduler on in the client.
                        Thread.sleep(500);
                        s.startHandshake();
                        s.close();
                        Assertions.fail("SERVER: UNEXPECTED ");
                    } catch (SSLException | SocketException se) {
                        System.out.println("SERVER: caught expected " + se);
                    } catch (IOException e) {
                        System.out.println("SERVER: caught: " + e);
                        if (!sslServerSocket.isClosed()) {
                            throw new UncheckedIOException(e);
                        }
                        break;
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                }
            }
        };
        t.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        sslServerSocket.close();
    }
}
