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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import jdk.httpclient.test.lib.common.TestUtil;
import jdk.httpclient.test.lib.quic.ClientConnection;
import jdk.httpclient.test.lib.quic.ConnectedBidiStream;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicServerHandler;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.quic.QuicClient;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.QuicTransportParameters;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static jdk.internal.net.http.quic.TerminationCause.forTransportError;
import static jdk.internal.net.quic.QuicTransportErrors.NO_VIABLE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8373877
 * @summary verify that when a QUIC (client) connection receives a stateless reset
 *          from the peer, then the connection is properly terminated
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.quic.QuicStandaloneServer
 *          jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.TestUtil
 * @run junit/othervm -Djdk.internal.httpclient.debug=true StatelessResetReceiptTest
 */
public class StatelessResetReceiptTest {

    private static QuicStandaloneServer server;
    private static SSLContext sslContext;
    private static ExecutorService executor;

    @BeforeAll
    static void beforeAll() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        executor = Executors.newCachedThreadPool();
        server = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(sslContext)
                .build();
        // Use a longer max ack delay to inflate the draining time (3xPTO)
        final QuicTransportParameters transportParameters = new QuicTransportParameters();
        transportParameters.setIntParameter(QuicTransportParameters.ParameterId.max_ack_delay,
                (1 << 14) - 1); // 16 seconds, maximum allowed
        server.start();
        System.out.println("Server started at " + server.getAddress());
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            System.out.println("Stopping server " + server.getAddress());
            server.close();
        }
        if (executor != null) {
            executor.close();
        }
    }

    private QuicClient createClient() {
        var versions = List.of(QuicVersion.QUIC_V1);
        var context = new QuicTLSContext(sslContext);
        var params = new SSLParameters();
        return new QuicClient.Builder()
                .availableVersions(versions)
                .tlsContext(context)
                .sslParameters(params)
                .executor(executor)
                .bindAddress(TestUtil.chooseClientBindAddress().orElse(null))
                .build();
    }

    /**
     * Initiates a connection between client and server. When the connection is still
     * active, this test initiates a stateless reset from the server connection against
     * the client connection. The test then expects that the client connection, which was active
     * until then, is terminated due to this stateless reset.
     */
    @Test
    public void testActiveConnection() throws Exception {
        final CompletableFuture<QuicServerConnection> serverConnCF = new MinimalFuture<>();
        final NotifyingHandler handler = new NotifyingHandler(serverConnCF);
        server.addHandler(handler);
        try (final QuicClient client = createClient()) {
            // create a QUIC connection to the server
            final ClientConnection conn = ClientConnection.establishConnection(client,
                    server.getAddress());
            // write data on the stream
            try (final ConnectedBidiStream bidiStream = conn.initiateNewBidiStream();
                 final OutputStream os = bidiStream.outputStream()) {
                os.write("foobar".getBytes(StandardCharsets.UTF_8));
                System.out.println("client: Client wrote message to bidi stream's output stream");
            }
            // wait for the handler on the server's connection to be invoked
            System.out.println("waiting for the request to be handled by the server connection");
            final QuicServerConnection serverConn = serverConnCF.get();
            System.out.println("request handled by the server connection " + serverConn);
            // verify the connection is still open
            assertTrue(conn.underlyingQuicConnection().isOpen(), "QUIC connection is not open");
            sendStatelessResetFrom(serverConn);
            // now expect the (active) client connection to be terminated
            assertStatelessResetTermination(conn);
        }
    }

    /**
     * Initiates a connection between client and server. The client connection is then
     * closed and it thus moves to the closing state. The test then initiates a stateless reset
     * from the server connection against this closing client connection. The test then verifies
     * that the client connection, which was in closing state, has been completely removed from the
     * endpoint upon receiving this stateless reset.
     */
    @Test
    public void testClosingConnection() throws Exception {
        final CompletableFuture<QuicServerConnection> serverConnCF = new MinimalFuture<>();
        final NotifyingHandler handler = new NotifyingHandler(serverConnCF);
        server.addHandler(handler);
        try (final QuicClient client = createClient()) {
            // create a QUIC connection to the server
            final ClientConnection conn = ClientConnection.establishConnection(client,
                    server.getAddress());
            // write data on the stream
            try (final ConnectedBidiStream bidiStream = conn.initiateNewBidiStream();
                 final OutputStream os = bidiStream.outputStream()) {
                os.write("foobar".getBytes(StandardCharsets.UTF_8));
                System.out.println("client: Client wrote message to bidi stream's output stream");
            }
            // wait for the handler on the server's connection to be invoked
            System.out.println("waiting for the request to be handled by the server connection");
            final QuicServerConnection serverConn = serverConnCF.get();
            System.out.println("request handled by the server connection " + serverConn);
            // now close the client/local connection so that it transitions to closing state
            System.out.println("closing client connection " + conn);
            conn.close();
            // verify connection is no longer open
            assertFalse(conn.underlyingQuicConnection().isOpen(), "QUIC connection is still open");
            // Check that the (closing) connection is still registered with the endpoint
            assertNotEquals(0, conn.endpoint().connectionCount(),
                    "Expected the QUIC connection to be registered");
            // now send a stateless reset from the server connection
            sendStatelessResetFrom(serverConn);
            // wait for the stateless reset to be processed
            final Instant waitEnd = Instant.now().plus(Duration.ofSeconds(2));
            while (Instant.now().isBefore(waitEnd)) {
                if (conn.endpoint().connectionCount() != 0) {
                    // wait for a while
                    Thread.sleep(10);
                }
            }
            // now expect the endpoint to have removed the client connection.
            // this isn't a fool proof verification because the connection could have been
            // moved from the closing state to draining and then removed, without having processed
            // the stateless reset, but we don't have any other credible way of verifying this
            assertEquals(0, conn.endpoint().connectionCount(), "unexpected number of connections" +
                    " known to QUIC endpoint");
        }
    }

    /**
     * Initiates a connection between client and server. The server connection is then
     * closed and the client connection thus moves to the draining state. The test then initiates
     * a stateless reset from the server connection against this draining client connection. The
     * test then verifies that the client connection, which was in draining state, has been
     * completely removed from the endpoint upon receiving this stateless reset.
     */
    @Test
    public void testDrainingConnection() throws Exception {
        final CompletableFuture<QuicServerConnection> serverConnCF = new MinimalFuture<>();
        final NotifyingHandler handler = new NotifyingHandler(serverConnCF);
        server.addHandler(handler);
        try (final QuicClient client = createClient()) {
            // create a QUIC connection to the server
            final ClientConnection conn = ClientConnection.establishConnection(client,
                    server.getAddress());
            // write data on the stream
            try (final ConnectedBidiStream bidiStream = conn.initiateNewBidiStream();
                 final OutputStream os = bidiStream.outputStream()) {
                os.write("foobar".getBytes(StandardCharsets.UTF_8));
                System.out.println("client: Client wrote message to bidi stream's output stream");
            }
            // wait for the handler on the server's connection to be invoked
            System.out.println("waiting for the request to be handled by the server connection");
            final QuicServerConnection serverConn = serverConnCF.get();
            System.out.println("request handled by the server connection " + serverConn);
            // now close the server connection so that the client conn transitions to draining state
            System.out.println("closing server connection " + serverConn);
            // intentionally use a "unique" error to confidently verify the termination cause
            final TerminationCause tc = forTransportError(NO_VIABLE_PATH)
                    .loggedAs("intentionally closed by server to initiate draining state" +
                            " on client connection");
            serverConn.connectionTerminator().terminate(tc);
            // send ping in case the connection_close message gets lost
            conn.underlyingQuicConnection().requestSendPing();
            // wait for client conn to terminate
            final TerminationCause clientTC = ((QuicConnectionImpl) conn.underlyingQuicConnection())
                    .futureTerminationCause().get();
            // verify connection closed for the right reason
            assertEquals(NO_VIABLE_PATH.code(), clientTC.getCloseCode(),
                    "unexpected termination cause");
            // wait for a while to let the connection close completely
            Thread.sleep(10);
            // Check that the (draining) connection is still registered with the endpoint
            assertNotEquals(0, conn.endpoint().connectionCount(),
                    "Expected the QUIC connection to be registered");
            // now send a stateless reset from the server connection
            sendStatelessResetFrom(serverConn);
            // wait for the stateless reset to be processed
            final Instant waitEnd = Instant.now().plus(Duration.ofSeconds(2));
            while (Instant.now().isBefore(waitEnd)) {
                if (conn.endpoint().connectionCount() != 0) {
                    // wait for a while
                    Thread.sleep(10);
                }
            }
            // now expect the endpoint to have removed the client connection.
            // this isn't a fool proof verification because the connection could have been
            // removed after moving out from the draining state, without having processed the
            // stateless reset, but we don't have any other credible way of verifying this
            assertEquals(0, conn.endpoint().connectionCount(), "unexpected number of connections" +
                    " known to QUIC endpoint");
        }
    }

    private static void sendStatelessResetFrom(final QuicServerConnection serverConn)
            throws IOException {
        final QuicConnectionId localConnId = serverConn.localConnectionId();
        final ByteBuffer resetDatagram = serverConn.endpoint().idFactory().statelessReset(
                localConnId.asReadOnlyBuffer(), 43);
        final InetSocketAddress targetAddr = serverConn.peerAddress();
        ((DatagramChannel) serverConn.channel()).send(resetDatagram, targetAddr);
        System.out.println("sent stateless reset from server conn " + serverConn + " to "
                + targetAddr);
    }

    private static void assertStatelessResetTermination(final ClientConnection conn)
            throws Exception {
        final CompletableFuture<TerminationCause> cf =
                ((QuicConnectionImpl) conn.underlyingQuicConnection()).futureTerminationCause();
        final TerminationCause tc = cf.get();
        System.out.println("got termination cause " + tc.getCloseCause() + " - " + tc.getLogMsg());
        final IOException closeCause = tc.getCloseCause();
        assertNotNull(closeCause, "close cause IOException is null");
        final String expectedMsg = "stateless reset from peer";
        if (closeCause.getMessage() != null && closeCause.getMessage().contains(expectedMsg)) {
            // got expected IOException
            return;
        }
        // unexpected IOException. throw it back
        throw closeCause;
    }

    private static final class NotifyingHandler implements QuicServerHandler {

        private final CompletableFuture<QuicServerConnection> serverConnCF;

        private NotifyingHandler(final CompletableFuture<QuicServerConnection> serverConnCF) {
            this.serverConnCF = serverConnCF;
        }

        @Override
        public void handleBidiStream(final QuicServerConnection conn,
                                     final ConnectedBidiStream bidiStream) {
            System.out.println("Handling incoming bidi stream " + bidiStream
                    + " on connection " + conn);
            this.serverConnCF.complete(conn);
        }
    }
}
