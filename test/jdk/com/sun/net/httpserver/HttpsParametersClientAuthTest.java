/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import jdk.internal.util.OperatingSystem;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8326381
 * @summary verifies that the setNeedClientAuth() and setWantClientAuth()
 *          methods on HttpsParameters class work as expected
 * @modules java.base/jdk.internal.util
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.test.lib.net.URIBuilder
 * @run junit HttpsParametersClientAuthTest
 */
public class HttpsParametersClientAuthTest {

    private static final boolean IS_WINDOWS = OperatingSystem.isWindows();
    private static final AtomicInteger TID = new AtomicInteger();
    private static final ThreadFactory SRV_THREAD_FACTORY = (r) -> {
        final Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("server-thread-" + TID.incrementAndGet());
        return t;
    };

    /**
     * verifies default values of {@link HttpsParameters#setNeedClientAuth(boolean)}
     * and {@link HttpsParameters#setWantClientAuth(boolean)} methods
     */
    @Test
    public void testDefaultClientAuth() throws Exception {
        // test default values
        HttpsParameters defaultParams = new Params();
        assertFalse(defaultParams.getNeedClientAuth(),
                "needClientAuth was expected to be false but wasn't");
        assertFalse(defaultParams.getWantClientAuth(),
                "wantClientAuth was expected to be false but wasn't");
    }

    /**
     * sets {@link HttpsParameters#setNeedClientAuth(boolean)} and verifies
     * that subsequent calls to {@link HttpsParameters#getNeedClientAuth()} returns
     * the set value and {@link HttpsParameters#getWantClientAuth()} returns false
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testNeedClientAuth(final boolean initialWantClientAuth) throws Exception {
        HttpsParameters needClientAuthParams = new Params();
        // first set wantClientAuth to an initial value to verify that it later gets reset
        needClientAuthParams.setWantClientAuth(initialWantClientAuth);
        // needClientAuth = true and thus wantClientAuth = false
        needClientAuthParams.setNeedClientAuth(true);
        assertTrue(needClientAuthParams.getNeedClientAuth(),
                "needClientAuth was expected to be true but wasn't");
        assertFalse(needClientAuthParams.getWantClientAuth(),
                "wantClientAuth was expected to be false but wasn't");
        // now set needClientAuth = false and verify that both needClientAuth and wantClientAuth
        // are now false
        needClientAuthParams.setNeedClientAuth(false);
        assertFalse(needClientAuthParams.getNeedClientAuth(),
                "needClientAuth was expected to be false but wasn't");
        assertFalse(needClientAuthParams.getWantClientAuth(),
                "wantClientAuth was expected to be false but wasn't");
    }

    /**
     * sets {@link HttpsParameters#setWantClientAuth(boolean)} and verifies
     * that subsequent calls to {@link HttpsParameters#getWantClientAuth()} returns
     * the set value and {@link HttpsParameters#getNeedClientAuth()} returns false
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testWantClientAuth(final boolean initialNeedClientAuth) throws Exception {
        HttpsParameters wantClientAuthParams = new Params();
        // first set needClientAuth to an initial value to verify that it later gets reset
        wantClientAuthParams.setNeedClientAuth(initialNeedClientAuth);
        // wantClientAuth = true and thus needClientAuth = false
        wantClientAuthParams.setWantClientAuth(true);
        assertTrue(wantClientAuthParams.getWantClientAuth(),
                "wantClientAuth was expected to be true but wasn't");
        assertFalse(wantClientAuthParams.getNeedClientAuth(),
                "needClientAuth was expected to be false but wasn't");
        // now set wantClientAuth = false and verify that both wantClientAuth and needClientAuth
        // are now false
        wantClientAuthParams.setWantClientAuth(false);
        assertFalse(wantClientAuthParams.getWantClientAuth(),
                "wantClientAuth was expected to be false but wasn't");
        assertFalse(wantClientAuthParams.getNeedClientAuth(),
                "needClientAuth was expected to be false but wasn't");
    }

    /**
     * Starts a {@link HttpsServer} by
     * {@linkplain HttpsParameters#setNeedClientAuth(boolean) setting needClientAuth} as
     * {@code true}. The client is then configured either to present the client certificates
     * during the TLS handshake or configured not to present them. In the case where the
     * client presents the client certificates, the HTTP request issued by the client is
     * expected to pass. In the other case where the client doesn't present the certificates,
     * the test verifies that the HTTP request fails due to a connection error
     * (caused by TLS handshake failure)
     *
     * @param presentClientCerts true if the client should present certificates
     *                           during TLS handshake, false otherwise
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testServerNeedClientAuth(final boolean presentClientCerts) throws Exception {
        // SSLContext which contains both the key and the trust material and will be used
        // by the server
        final SSLContext serverSSLCtx = new SimpleSSLContext().get();
        assertNotNull(serverSSLCtx, "could not create SSLContext");
        final HttpsConfigurator configurator = new HttpsConfigurator(serverSSLCtx) {
            @Override
            public void configure(final HttpsParameters params) {
                // we intentionally don't call params.setSSLParameters()
                // and instead call params.setNeedClientAuth()
                params.setNeedClientAuth(true); // "require" the client to present certs
            }
        };
        try (final ExecutorService executor = Executors.newCachedThreadPool(SRV_THREAD_FACTORY)) {
            final HttpsServer server = createHttpsServer(executor, configurator);
            server.start();
            System.out.println("started server at " + server.getAddress());
            try {
                final HttpClient.Builder builder = createClientBuilder();
                // if the client is expected to present client certificates, then
                // we construct a SSLContext which has both key and trust material.
                // otherwise we construct a SSLContext that only has trust material
                // and thus won't present certificates during TLS handshake
                final SSLContext clientSSLCtx = presentClientCerts
                        ? serverSSLCtx : onlyTrustStoreContext();
                // construct the client using the SSLContext
                try (final HttpClient client = builder.sslContext(clientSSLCtx)
                        .build()) {
                    // issue a request
                    final URI reqURI = URIBuilder.newBuilder()
                            .scheme("https")
                            .host(server.getAddress().getAddress())
                            .port(server.getAddress().getPort())
                            .path("/")
                            .build();
                    System.out.println("issuing request to " + reqURI);
                    final HttpResponse<Void> resp;
                    try {
                        resp = client.send(HttpRequest.newBuilder(reqURI).build(),
                                BodyHandlers.discarding());
                        if (!presentClientCerts) {
                            // request was expected to fail since the server was configured to force
                            // the client to present the client cert, but the client didn't
                            // present any
                            fail("request was expected to fail, but didn't");
                        }
                        assertEquals(200, resp.statusCode(), "unexpected response code");
                        // verify the client did present the certs
                        assertTrue(resp.sslSession().isPresent(), "missing SSLSession on response");
                        assertNotNull(resp.sslSession().get().getLocalCertificates(),
                                "client was expected to present certs to the server, but didn't");
                    } catch (IOException ioe) {
                        if (presentClientCerts) {
                            // wasn't expected to fail, just let the exception propagate
                            throw ioe;
                        }
                        // verify it failed due to right reason
                        Throwable cause = ioe.getCause();
                        while (cause != null) {
                            // either of SocketException or SSLHandshakeException are OK.
                            // additionally on Windows we accept even IOException
                            // (caused by WSAECONNABORTED)
                            if (cause instanceof SocketException se) {
                                final String msg = se.getMessage();
                                assertTrue(msg != null && msg.contains("Connection reset"),
                                        "unexpected message in SocketException: " + msg);
                                System.out.println("received the expected exception: " + se);
                                break;
                            } else if (cause instanceof SSLHandshakeException she) {
                                final String msg = she.getMessage();
                                assertTrue(msg != null && msg.contains("certificate_required"),
                                        "unexpected message in SSLHandshakeException: " + msg);
                                System.out.println("received the expected exception: " + she);
                                break;
                            } else if (IS_WINDOWS && cause instanceof IOException winIOE) {
                                // on Windows we sometimes receive this exception, which is
                                // acceptable
                                System.out.println("(windows) received the expected exception: "
                                        + winIOE);
                                break;
                            }
                            cause = cause.getCause();
                        }
                        if (cause == null) {
                            // didn't find expected exception, rethrow original exception
                            throw ioe;
                        }
                    }
                }
            } finally {
                System.out.println("Stopping server at " + server.getAddress());
                server.stop(0 /* delay */);
            }
        }
    }

    /**
     * Starts a {@link HttpsServer} by
     * {@linkplain HttpsParameters#setWantClientAuth(boolean) setting wantClientAuth} as
     * {@code true}. The client is then configured to either present the client certificates
     * during the TLS handshake or configured not to present them. In both these cases the
     * HTTP request issued by the client is expected to pass.
     *
     * @param presentClientCerts true if the client should present certificates
     *                           during TLS handshake, false otherwise
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testServerWantClientAuth(final boolean presentClientCerts) throws Exception {
        // SSLContext which contains both the key and the trust material and will be used
        // by the server
        final SSLContext serverSSLCtx = new SimpleSSLContext().get();
        assertNotNull(serverSSLCtx, "could not create SSLContext");
        final HttpsConfigurator configurator = new HttpsConfigurator(serverSSLCtx) {
            @Override
            public void configure(final HttpsParameters params) {
                // we intentionally don't call params.setSSLParameters()
                // and instead call params.setWantClientAuth()
                params.setWantClientAuth(true); // "request" the client to present certs
            }
        };
        try (final ExecutorService executor = Executors.newCachedThreadPool(SRV_THREAD_FACTORY)) {
            final HttpsServer server = createHttpsServer(executor, configurator);
            server.start();
            System.out.println("started server at " + server.getAddress());
            try {
                final HttpClient.Builder builder = createClientBuilder();
                // if the client is expected to present client certificates, then
                // we construct a SSLContext which has both key and trust material.
                // otherwise we construct a SSLContext that only has trust material
                // and thus won't present certificates during TLS handshake
                final SSLContext clientSSLCtx = presentClientCerts
                        ? serverSSLCtx : onlyTrustStoreContext();
                // construct the client using the SSLContext
                try (final HttpClient client = builder.sslContext(clientSSLCtx)
                        .build()) {
                    // issue a request
                    final URI reqURI = URIBuilder.newBuilder()
                            .scheme("https")
                            .host(server.getAddress().getAddress())
                            .port(server.getAddress().getPort())
                            .path("/")
                            .build();
                    System.out.println("issuing request to " + reqURI);
                    final HttpResponse<Void> resp = client.send(
                            HttpRequest.newBuilder(reqURI).build(), BodyHandlers.discarding());
                    assertEquals(200, resp.statusCode(), "unexpected response code");
                    if (presentClientCerts) {
                        // verify the client did present the certs
                        assertTrue(resp.sslSession().isPresent(), "missing SSLSession on response");
                        assertNotNull(resp.sslSession().get().getLocalCertificates(),
                                "client was expected to present certs to the server, but didn't");
                    }
                }
            } finally {
                System.out.println("Stopping server at " + server.getAddress());
                server.stop(0 /* delay */);
            }
        }
    }

    private static HttpsServer createHttpsServer(final Executor executor,
                                                 final HttpsConfigurator configurator)
            throws IOException {
        final InetAddress loopback = InetAddress.getLoopbackAddress();
        final HttpsServer server = HttpsServer.create(
                new InetSocketAddress(loopback, 0), 0 /* backlog */);
        server.setExecutor(executor);
        server.setHttpsConfigurator(configurator);
        server.createContext("/", new AllOKHandler());
        return server;
    }

    private static HttpClient.Builder createClientBuilder() {
        return HttpClient.newBuilder().version(HTTP_1_1)
                .proxy(NO_PROXY);
    }

    /**
     * Creates and returns a {@link SSLContext} which only has trust material
     * and doesn't have any test specific keys.
     */
    private static SSLContext onlyTrustStoreContext() throws Exception {
        final KeyStore keyStore = loadTestKeyStore();
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(keyStore);
        final SSLContext ctx = SSLContext.getInstance("TLS");
        // initialize with only trust managers
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    private static KeyStore loadTestKeyStore() throws Exception {
        return AccessController.doPrivileged(
                new PrivilegedExceptionAction<KeyStore>() {
                    @Override
                    public KeyStore run() throws Exception {
                        final String testKeys = System.getProperty("test.src")
                                + "/"
                                + "../../../../../../test/lib/jdk/test/lib/net/testkeys";
                        try (final FileInputStream fis = new FileInputStream(testKeys)) {
                            final char[] passphrase = "passphrase".toCharArray();
                            final KeyStore ks = KeyStore.getInstance("PKCS12");
                            ks.load(fis, passphrase);
                            return ks;
                        }
                    }
                });
    }

    // no-op implementations of the abstract methods of HttpsParameters
    private static final class Params extends HttpsParameters {

        @Override
        public HttpsConfigurator getHttpsConfigurator() {
            // no-op
            return null;
        }

        @Override
        public InetSocketAddress getClientAddress() {
            // no-op
            return null;
        }

        @Override
        public void setSSLParameters(SSLParameters params) {
            // no-op
        }
    }

    // A HttpHandler which just returns 200 response code
    private static final class AllOKHandler implements HttpHandler {

        private static final int NO_RESPONSE_BODY = -1;

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            System.out.println("responding to request: " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, NO_RESPONSE_BODY);
        }
    }
}
