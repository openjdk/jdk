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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;

import jdk.httpclient.test.lib.common.TestUtil;
import jdk.httpclient.test.lib.quic.ClientConnection;
import jdk.httpclient.test.lib.quic.ConnectedBidiStream;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicServerHandler;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.quic.QuicClient;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/*
 * @test
 * @summary Test the version negotiation semantics of Quic
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.quic.QuicStandaloneServer
 *        jdk.httpclient.test.lib.common.TestUtil
 *        jdk.httpclient.test.lib.quic.ClientConnection
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.internal.httpclient.debug=true VersionNegotiationTest
 */
public class VersionNegotiationTest {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static ExecutorService executor;

    @BeforeClass
    public static void beforeClass() throws Exception {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (executor != null) executor.shutdown();
    }

    private QuicClient createClient() {
        return new QuicClient.Builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .tlsContext(new QuicTLSContext(sslContext))
                .sslParameters(new SSLParameters())
                .executor(executor)
                .bindAddress(TestUtil.chooseClientBindAddress().orElse(null))
                .build();
    }

    /**
     * Uses a Quic client which is enabled for a specific version and a Quic server
     * which is enabled for a different version. Verifies that the connection attempt fails
     * as noted in RFC-9000, section 6.2
     */
    @Test
    public void testUnsupportedClientVersion() throws Exception {
        try (final var client = createClient()) {
            final QuicVersion serverVersion = QuicVersion.QUIC_V2;
            try (final QuicServer server = createAndStartServer(serverVersion)) {
                System.out.println("Attempting to connect " + client.getAvailableVersions()
                        + " client to a " + server.getAvailableVersions() + " server");
                final IOException thrown = expectThrows(IOException.class,
                        () -> ClientConnection.establishConnection(client, server.getAddress()));
                // a version negotiation failure (since it happens during a QUIC connection
                // handshake) gets thrown as a SSLHandshakeException
                if (!(thrown.getCause() instanceof SSLHandshakeException sslhe)) {
                    throw thrown;
                }
                System.out.println("Received (potentially expected) exception: " + sslhe);
                // additional check to make sure it was thrown for the right reason
                assertEquals(sslhe.getMessage(), "QUIC connection establishment failed");
                // underlying cause of SSLHandshakeException should be version negotiation failure
                final Throwable underlyingCause = sslhe.getCause();
                assertNotNull(underlyingCause, "missing cause in SSLHandshakeException");
                assertNotNull(underlyingCause.getMessage(), "missing message in " + underlyingCause);
                assertTrue(underlyingCause.getMessage().contains("No support for any of the" +
                        " QUIC versions being negotiated"));
            }
        }
    }

    /**
     * Creates a server which supports only the specified Quic version
     */
    private static QuicServer createAndStartServer(final QuicVersion version) throws IOException {
        final QuicStandaloneServer server = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{version})
                .sslContext(sslContext)
                .build();
        server.addHandler(new ExceptionThrowingHandler());
        server.start();
        System.out.println("Quic server with version " + version + " started at " + server.getAddress());
        return server;
    }

    private static final class ExceptionThrowingHandler implements QuicServerHandler {

        @Override
        public void handleBidiStream(final QuicServerConnection conn,
                                     final ConnectedBidiStream bidiStream) throws IOException {
            throw new AssertionError("Handler shouldn't have been called for "
                    + bidiStream + " on connection " + conn);
        }
    }
}
