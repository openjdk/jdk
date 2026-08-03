/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.quic.QuicEndpoint.QuicDatagram;
import jdk.internal.net.http.quic.packets.QuicPacket;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

/**
 * The {@code QuicPacketReceiver} is an abstraction that defines the
 * interface between a {@link QuicEndpoint} and a {@link QuicConnection}.
 * This defines the minimum set of methods that the endpoint will need
 * in order to be able to dispatch a received {@link jdk.internal.net.http.quic.packets.QuicPacket}
 * to its destination. This abstraction is typically useful when dealing with
 * {@linkplain QuicEndpoint.ClosedConnection
 * closed connections, which need to remain alive for a certain time
 * after being closed in order to satisfy the requirement of the quic
 * protocol (typically for retransmitting the CLOSE_CONNECTION frame
 * if needed).
 */
public interface QuicPacketReceiver {

    /**
     * {@return a list of local connectionIds for this connection)
     */
    List<QuicConnectionId> connectionIds();

    /**
     * {@return a list of active peer stateless reset tokens for this connection)
     */
    List<byte[]> activeResetTokens();

    /**
     * {@return the initial connection id assigned by the peer}
     * On the client side, this is always {@link Optional#empty()}.
     * On the server side, it contains the initial connection id
     * that was assigned by the client in the first INITIAL packet.
     *
     * @implSpec
     * The default implementation of this method returns {@link Optional#empty()}
     */
    default Optional<QuicConnectionId> initialConnectionId() {
        return Optional.empty();
    }

    /**
     * Called when an incoming datagram is received.
     * <p>
     * The buffer is positioned at the start of the datagram to process.
     * The buffer may contain more than one QUIC packet.
     *
     * @param source     The peer address, as received from the UDP stack
     * @param destConnId    Destination connection id bytes included in the packet
     * @param headersType The quic packet type
     * @param buffer     A buffer positioned at the start of the quic packet,
     *                   not yet decrypted, and possibly containing coalesced
     *                   packets.
     */
     void processIncoming(SocketAddress source, ByteBuffer destConnId,
                          QuicPacket.HeadersType headersType, ByteBuffer buffer);

    /**
     * Called when a datagram scheduled for writing by this connection
     * could not be written to the network.
     * @param t the error that occurred
     */
    void onWriteError(Throwable t);

    /**
     * Called when a stateless reset token is received.
     */
    void processStatelessReset();

    /**
     * Called to shut a closed connection down.
     * This is the last step when closing a connection, and typically
     * only release resources held by all packet spaces.
     */
    void shutdown();

    /**
     * Called after a datagram has been written to the socket.
     * At this point the datagram's ByteBuffer can typically be released,
     * or returned to a buffer pool.
     * @implSpec
     * The default implementation of this method does nothing.
     * @param datagram the datagram that was sent
     */
    default void datagramSent(QuicDatagram datagram) { }

    /**
     * Called after a datagram has been discarded as a result of
     * some error being raised, for instance, when an attempt
     * to write it to the socket has failed, or if the encryption
     * of a packet in the datagram has failed.
     * At this point the datagram's ByteBuffer can typically be released,
     * or returned to a buffer pool.
     * @implSpec
     * The default implementation of this method does nothing.
     * @param datagram the datagram that was discarded
     */
    default void datagramDiscarded(QuicDatagram datagram) { }

    /**
     * Called after a datagram has been dropped. Typically, this
     * could happen if the datagram was only partly written, or if
     * the connection was closed before the datagram could be sent.
     * At this point the datagram's ByteBuffer can typically be released,
     * or returned to a buffer pool.
     * @implSpec
     * The default implementation of this method does nothing.
     * @param datagram the datagram that was dropped
     */
    default void datagramDropped(QuicDatagram datagram) { }

    /**
     * {@return whether this receiver accepts packets from the given source}
     * @param source the sender address
     */
    default boolean accepts(SocketAddress source) {
        return true;
    }
}
