/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.net.http.quic;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreams;
import jdk.internal.net.quic.QuicTLSEngine;

/**
 * This class implements a QUIC connection.
 * A QUIC connection is established between a client and a server
 * over a QuicEndpoint endpoint.
 * A QUIC connection can then multiplex multiple QUIC streams to the
 * same server.
 * This abstract class exposes public methods used by the higher level
 * protocol.
 *
 * <p> A typical call flow to establish a connection would be:
 * {@snippet :
 *    AltService service = ...;
 *    QuicClient client = ...;
 *    QuicConnection connection = client.createConnectionFor(service);
 *    connection.startHandshake()
 *    .thenApply((r) ->  { ... })
 *    ...;
 * }
 *
 */
public abstract class QuicConnection {

    /**
     * Starts the Quic Handshake.
     * @return A completable future which will be completed when the
     *         handshake is completed.
     * @throws UnsupportedOperationException If this connection isn't a client connection
     */
    public abstract CompletableFuture<QuicEndpoint> startHandshake();

    /**
     * Creates a new locally initiated bidirectional stream.
     * <p>
     * Creation of streams is limited to the maximum limit advertised by the peer. If a new stream
     * cannot be created due to this limitation, then this method will use the
     * {@code limitIncreaseDuration} to decide how long to wait for a potential increase in the
     * limit.
     * <p>
     * If the limit has been reached and the {@code limitIncreaseDuration} is not
     * {@link Duration#isPositive() positive} then this method returns a {@code CompletableFuture}
     * which has been completed exceptionally with {@link QuicStreamLimitException}. Else, this
     * method returns a {@code CompletableFuture} which waits for that duration for a potential
     * increase in the limit. If, during this period, the stream creation limit does increase and
     * stream creation succeeds then the returned {@code CompletableFuture} will be completed
     * successfully, else it will complete exceptionally with {@link QuicStreamLimitException}.
     *
     * @param limitIncreaseDuration Amount of time to wait for the bidirectional stream creation
     *                              limit to be increased by the peer, if this connection has
     *                              currently reached its limit
     * @return a CompletableFuture which completes either with a new locally initiated
     * bidirectional stream or exceptionally if the stream creation failed
     */
    public abstract CompletableFuture<QuicBidiStream> openNewLocalBidiStream(
            Duration limitIncreaseDuration);

    /**
     * Creates a new locally initiated unidirectional stream. Locally created unidirectional streams
     * are write-only streams.
     * <p>
     * Creation of streams is limited to the maximum limit advertised by the peer. If a new stream
     * cannot be created due to this limitation, then this method will use the
     * {@code limitIncreaseDuration} to decide how long to wait for a potential increase in the
     * limit.
     * <p>
     * If the limit has been reached and the {@code limitIncreaseDuration} is not
     * {@link Duration#isPositive() positive} then this method returns a {@code CompletableFuture}
     * which has been completed exceptionally with {@link QuicStreamLimitException}. Else, this
     * method returns a {@code CompletableFuture} which waits for that duration for a potential
     * increase in the limit. If, during this period, the stream creation limit does increase and
     * stream creation succeeds then the returned {@code CompletableFuture} will be completed
     * successfully, else it will complete exceptionally with {@link QuicStreamLimitException}.
     *
     * @param limitIncreaseDuration Amount of time to wait for the unidirectional stream creation
     *                              limit to be increased by the peer, if this connection has
     *                              currently reached its limit
     * @return a CompletableFuture which completes either with a new locally initiated
     * unidirectional stream or exceptionally if the stream creation failed
     */
    public abstract CompletableFuture<QuicSenderStream> openNewLocalUniStream(
            Duration limitIncreaseDuration);

    /**
     * Adds a listener that will be invoked when a remote stream is
     * created.
     *
     * @apiNote
     * The listener will be invoked with any remote streams
     * already opened, and not yet acquired by another listener.
     * Any stream passed to the listener is either a {@link QuicBidiStream}
     * or a {@link QuicReceiverStream} depending on the
     * {@linkplain QuicStreams#streamType(long) stream type} of the given
     * streamId.
     * The listener should return {@code true} if it wishes to acquire
     * the stream.
     *
     * @param streamConsumer the listener
     */
    public abstract void addRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer);

    /**
     * Removes a listener previously added with {@link #addRemoteStreamListener(Predicate)}
     * @return {@code true} if the listener was found and removed, {@code false} otherwise
     */
    public abstract boolean removeRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer);

    /**
     * {@return a stream of all currently opened {@link QuicStream} in the connection}
     *
     * @apiNote
     * All current quic streams are included, whether local or remote, and whether they
     * have been acquired or not.
     *
     * @see #addRemoteStreamListener(Predicate)
     */
    public abstract Stream<? extends QuicStream> quicStreams();

    /**
     * {@return true if this connection is open}
     */
    public abstract boolean isOpen();

    /**
     * {@return a long identifier that can be used to uniquely
     * identify a quic connection in the context of the
     * {@link QuicInstance} that created it}
     */
    public long uniqueId() { return 0; }

    /**
     * {@return a debug tag to be used with {@linkplain
     * jdk.internal.net.http.common.Logger lower level logging}}
     * This typically includes both the connection {@link #uniqueId()}
     * and the {@link QuicInstance#instanceId()}.
     */
    public abstract String dbgTag();

    /**
     * {@return a debug tag}
     * Typically used with {@linkplain jdk.internal.net.http.common.Log
     * higher level logging}
     */
    public abstract String logTag();

    /**
     * {@return the {@link TerminationCause} if the connection has
     * closed or is being closed, otherwise returns null}
     */
    public abstract TerminationCause terminationCause();

    public abstract QuicTLSEngine getTLSEngine();

    public abstract InetSocketAddress peerAddress();

    public abstract SocketAddress localAddress();

    /**
     * {@return a {@code CompletableFuture} that gets completed when
     *  the peer has acknowledged, or replied to the first {@link
     *  QuicPacket.PacketType#INITIAL INITIAL}
     *  packet
     */
    public abstract CompletableFuture<Void> handshakeReachedPeer();

    /**
     * Requests to send a PING frame to the peer.
     * An implementation may decide to support sending of out-of-band ping
     * frames (triggered by the application layer) only for a subset of the
     * {@linkplain jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace
     * packet number spaces}. It may complete with -1 if it doesn't want to request
     * sending of a ping frame at the time {@code requestSendPing()} is called.
     * @return A completable future that will be completed with the number of
     * milliseconds it took to get a valid response. It may also complete
     * exceptionally, or with {@code -1L} if the ping was not sent.
     */
    public abstract CompletableFuture<Long> requestSendPing();

    /**
     * {@return this connection {@code QuicConnectionId} or null}
     * @implSpec
     * The default implementation of this method returns null
     */
    public QuicConnectionId localConnectionId() { return null; }

    /**
     * {@return the {@link ConnectionTerminator} for this connection}
     */
    public abstract ConnectionTerminator connectionTerminator();
}
