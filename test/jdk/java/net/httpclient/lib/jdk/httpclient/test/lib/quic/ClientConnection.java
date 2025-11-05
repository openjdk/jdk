/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.httpclient.test.lib.quic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.QuicClient;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.QuicEndpoint;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import static jdk.internal.net.http.quic.TerminationCause.forTransportError;
import static jdk.internal.net.quic.QuicTransportErrors.NO_ERROR;

/**
 * A client initiated QUIC connection to a server
 */
public final class ClientConnection implements AutoCloseable {

    private static final Logger debug = Utils.getDebugLogger(() -> ClientConnection.class.getName());

    private static final ByteBuffer EOF = ByteBuffer.allocate(0);
    private static final String ALPN = QuicStandaloneServer.ALPN;

    private final QuicConnection connection;
    private final QuicEndpoint endpoint;

    /**
     * Establishes a connection between a Quic client and a target Quic server. This includes completing
     * the handshake between the client and the server.
     *
     * @param client     The Quic client
     * @param serverAddr the target server address
     * @return a ClientConnection
     * @throws IOException If there was any exception while establishing the connection
     */
    public static ClientConnection establishConnection(final QuicClient client,
                                                       final InetSocketAddress serverAddr)
            throws IOException {
        Objects.requireNonNull(client);
        Objects.requireNonNull(serverAddr);
        final QuicConnection conn = client.createConnectionFor(serverAddr, new String[]{ALPN});
        assert conn instanceof QuicConnectionImpl : "unexpected QUIC connection type: "
                + conn.getClass();
        final CompletableFuture<QuicEndpoint> handshakeCf =
                conn.startHandshake();
        final QuicEndpoint endpoint;
        try {
            endpoint = handshakeCf.get();
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        assert endpoint != null : "null endpoint after handshake completion";
        if (debug.on()) {
            debug.log("Quic connection established for client: " + client.name() +
                    ", local addr: " + conn.localAddress() +
                    ", peer addr: " + serverAddr +
                    ", endpoint: " + endpoint);
        }
        return new ClientConnection(conn, endpoint);
    }

    private ClientConnection(final QuicConnection connection, final QuicEndpoint endpoint) {
        this.connection = Objects.requireNonNull(connection);
        this.endpoint = endpoint;
    }

    /**
     * Creates a new client initiated bidirectional stream to the server. The returned
     * {@code ConnectedBidiStream} will have the reader and writer tasks started, thus
     * allowing the caller of this method to then use the returned {@code ConnectedBidiStream}
     * for sending or received data through the {@link ConnectedBidiStream#outputStream() output stream}
     * or {@link ConnectedBidiStream#inputStream() input stream} respectively.
     *
     * @return
     * @throws IOException
     */
    public ConnectedBidiStream initiateNewBidiStream() throws IOException {
        final QuicBidiStream quicBidiStream;
        try {
            // TODO: review the duration being passed and whether it needs to be something
            // that should be taken as an input to the initiateNewBidiStream() method
            quicBidiStream = this.connection.openNewLocalBidiStream(Duration.ZERO).get();
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        return new ConnectedBidiStream(quicBidiStream);
    }

    public QuicConnection underlyingQuicConnection() {
        return this.connection;
    }

    public QuicEndpoint endpoint() {
        return this.endpoint;
    }

    @Override
    public void close() throws Exception {
        this.connection.connectionTerminator().terminate(forTransportError(NO_ERROR));
    }
}
