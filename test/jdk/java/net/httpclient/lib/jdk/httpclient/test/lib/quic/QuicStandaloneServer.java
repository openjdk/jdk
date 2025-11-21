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
package jdk.httpclient.test.lib.quic;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLContext;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;

public final class QuicStandaloneServer extends QuicServer {
    public static final String ALPN = "quic-standalone-test-alpn";

    private static final AtomicLong IDS = new AtomicLong();

    private final Logger debug = Utils.getDebugLogger(this::name);
    private QuicServerHandler handler;

    private static String nextName() {
        return "quic-standalone-server-" + IDS.incrementAndGet();
    }

    QuicStandaloneServer(String serverId, InetSocketAddress bindAddress,
                         ExecutorService executor, QuicVersion[] supportedQuicVersions,
                         boolean compatible, QuicTLSContext quicTLSContext, SNIMatcher sniMatcher,
                         DatagramDeliveryPolicy incomingDeliveryPolicy,
                         DatagramDeliveryPolicy outgoingDeliveryPolicy, String alpn,
                         LongFunction<String> appErrorCodeToString) {
        super(serverId, bindAddress, executor, supportedQuicVersions, compatible, quicTLSContext, sniMatcher,
                incomingDeliveryPolicy, outgoingDeliveryPolicy, alpn, appErrorCodeToString);
        // set a connection acceptor
        setConnectionAcceptor(QuicStandaloneServer::acceptIncoming);
    }

    public void addHandler(final QuicServerHandler handler) {
        this.handler = handler;
    }

    QuicServerHandler getHandler() {
        return this.handler;
    }

    static boolean acceptIncoming(final SocketAddress source, final QuicServerConnection serverConn) {
        try {
            final QuicStandaloneServer server = (QuicStandaloneServer) serverConn.quicInstance();
            final QuicServerHandler handler = server.getHandler();
            if (handler == null) {
                if (server.debug.on()) {
                    server.debug.log("Handler absent - rejecting new connection "
                            + serverConn + " from source " + source);
                }
                return false;
            }
            if (!handler.acceptIncomingConnection(source, serverConn)) {
                if (server.debug.on()) {
                    server.debug.log("Handler " + handler + " rejected new connection "
                            + serverConn + " from source " + source);
                }
                return false;
            }
            if (server.debug.on()) {
                server.debug.log("New connection " + serverConn + " accepted from " + source);
            }
            serverConn.onSuccessfulHandshake(() -> {
                if (server.debug.on()) {
                    server.debug.log("Registering a listener for remote streams on connection "
                            + serverConn);
                }
                // add a listener for streams that have been created by the remote side
                // (i.e. initiated by the client)
                serverConn.addRemoteStreamListener((stream) -> {
                    if (stream.isBidirectional()) {
                        // invoke the handler (application code) as a async work
                        server.asyncHandleBidiStream(source, serverConn, (QuicBidiStream) stream);
                        return true; // true implies that this listener wishes to acquire the stream
                    } else {
                        if (server.debug.on()) {
                            server.debug.log("Ignoring stream " + stream + " on connection " + serverConn);
                        }
                        return false;
                    }
                });
            });
            return true; // true implies we wish to accept this incoming connection
        } catch (Throwable t) {
            // TODO: re-evaluate why this try/catch block is there. it's likely
            // that in the absence of this block, the call just "disappears"/hangs when an exception
            // occurs in this method
            System.err.println("Exception while accepting incoming connection: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    private void asyncHandleBidiStream(final SocketAddress source, final QuicServerConnection serverConn,
                                       final QuicBidiStream stream) {
        this.executor().execute(() -> {
            try {
                if (debug.on()) {
                    debug.log("Invoking handler " + handler + " for handling bidi stream "
                            + stream + " on connection " + serverConn);
                }
                handler.onClientInitiatedBidiStream(serverConn, stream);
            } catch (Throwable t) {
                System.err.println("Failed to handle client initiated" +
                        " bidi stream for connection " + serverConn +
                        " from source " + source);
                t.printStackTrace();
            }
        });
    }

    public static Builder<QuicStandaloneServer> newBuilder() {
        return new StandaloneBuilder();
    }

    private static final class StandaloneBuilder extends Builder<QuicStandaloneServer> {

        public StandaloneBuilder() {
            this.alpn = ALPN;
        }

        @Override
        public QuicStandaloneServer build() throws IOException {
            QuicVersion[] versions = availableQuicVersions;
            if (versions == null) {
                // default to v1 and v2
                versions = new QuicVersion[]{QuicVersion.QUIC_V1, QuicVersion.QUIC_V2};
            }
            if (versions.length == 0) {
                throw new IllegalStateException("Empty supported QUIC versions");
            }
            InetSocketAddress addr = bindAddress;
            if (addr == null) {
                // default to loopback address and ephemeral port
                addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            }
            SSLContext ctx = sslContext;
            if (ctx == null) {
                try {
                    ctx = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException(e);
                }
            }
            final QuicTLSContext quicTLSContext = new QuicTLSContext(ctx);
            final String name = serverId == null ? nextName() : serverId;
            return new QuicStandaloneServer(name, addr, executor, versions, compatible, quicTLSContext,
                    sniMatcher, incomingDeliveryPolicy, outgoingDeliveryPolicy, alpn, appErrorCodeToString);
        }
    }

}
