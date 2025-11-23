/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.net.SocketAddress;

import jdk.internal.net.http.quic.streams.QuicBidiStream;

/**
 * Used by server side application code to handle incoming Quic connections and streams associated
 * with those connections
 *
 * @see QuicStandaloneServer#addHandler(QuicServerHandler)
 */
// TODO: should we make this an abstract class instead of interface?
public interface QuicServerHandler {

    /**
     * @param source     The (client) source of the incoming connection
     * @param serverConn The {@link QuicServerConnection}, constructed on the server side,
     *                   representing in the incoming connection
     *                   {@return true if the incoming connection should be accepted. false otherwise}
     */
    default boolean acceptIncomingConnection(final SocketAddress source,
                                             final QuicServerConnection serverConn) {
        // by default accept new connections
        return true;
    }

    /**
     * Called whenever a client initiated bidirectional stream has been received on the
     * Quic connection which this {@code QuicServerHandler} previously
     * {@link #acceptIncomingConnection(SocketAddress, QuicServerConnection) accepted}
     *
     * @param conn The connection on which the stream was created
     * @param bidi The client initiated bidirectional stream
     * @throws IOException
     */
    default void onClientInitiatedBidiStream(final QuicServerConnection conn,
                                             final QuicBidiStream bidi) throws IOException {
        // start the reader/writer loops for this stream, by creating a ConnectedBidiStream
        try (final ConnectedBidiStream connectedBidiStream = new ConnectedBidiStream(bidi)) {
            // let the handler use this connected stream to read/write data, if it wants to
            this.handleBidiStream(conn, connectedBidiStream);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }

    }

    /**
     * Called whenever a client initiated bidirectional stream has been received on the connection
     * previously accepted by this handler. This method is called from within
     * {@link #onClientInitiatedBidiStream(QuicBidiStream)} with a {@code ConnectedBidiStream} which
     * has the reader and writer tasks started.
     *
     * @param conn       The connection on which the stream was created
     * @param bidiStream The bidirectional stream which has the reader and writer tasks started
     * @throws IOException
     */
    void handleBidiStream(final QuicServerConnection conn,
                          final ConnectedBidiStream bidiStream) throws IOException;

}
