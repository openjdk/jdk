/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportException;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface CodingContext {

    /**
     * {@return the largest incoming packet number successfully processed
     * in the given packet number space}
     *
     * @apiNote
     * This method is used when decoding the packet number of an incoming packet.
     *
     * @param packetSpace the packet number space
     */
    long largestProcessedPN(QuicPacket.PacketNumberSpace packetSpace);

    /**
     * {@return the largest outgoing packet number acknowledged by the peer
     * in the given packet number space}
     *
     * @apiNote
     * This method is used when encoding the packet number of an outgoing packet.
     *
     * @param packetSpace the packet number space
     */
    long largestAckedPN(QuicPacket.PacketNumberSpace packetSpace);

    /**
     * {@return the length of the local connection ids expected
     *  to be found in incoming short header packets}
     */
    int connectionIdLength();

    /**
     * {@return the largest incoming packet number successfully processed
     * in the packet number space corresponding to the given packet type}
     * <p>
     * This is equivalent to calling:<pre>
     *     {@code largestProcessedPN(QuicPacket.PacketNumberSpace.of(packetType));}
     * </pre>
     *
     * @apiNote
     * This method is used when decoding the packet number of an incoming packet.
     *
     * @param packetType the packet type
     */
    default long largestProcessedPN(QuicPacket.PacketType packetType) {
        return largestProcessedPN(QuicPacket.PacketNumberSpace.of(packetType));
    }

    /**
     * {@return the largest outgoing packet number acknowledged by the peer
     * in the packet number space corresponding to the given packet type}
     * <p>
     * This is equivalent to calling:<pre>
     *     {@code largestAckedPN(QuicPacket.PacketNumberSpace.of(packetType));}
     * </pre>
     *
     * @apiNote
     * This method is used when encoding the packet number of an outgoing packet.
     *
     * @param packetType the packet type
     */
    default long largestAckedPN(QuicPacket.PacketType packetType) {
        return largestAckedPN(QuicPacket.PacketNumberSpace.of(packetType));
    }

    /**
     * Writes the given outgoing packet in the given byte buffer.
     * This method moves the position of the byte buffer.
     * @param packet the outgoing packet to write
     * @param buffer the byte buffer to write the packet into
     * @return the number of bytes written
     * @throws java.nio.BufferOverflowException if the buffer doesn't have
     *         enough space to write the packet
     */
    int writePacket(QuicPacket packet, ByteBuffer buffer)
                throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Reads an encrypted packet from the given byte buffer.
     * This method moves the position of the byte buffer.
     * @param src a byte buffer containing a non encrypted packet
     * @return the packet read
     * @throws IOException if the packet couldn't be read
     * @throws QuicTransportException if packet is correctly signed but malformed
     */
    QuicPacket parsePacket(ByteBuffer src) throws IOException, QuicKeyUnavailableException, QuicTransportException;

    /**
     * Returns the original destination connection id, required for
     * calculating the retry integrity tag.
     * <p>
     * This is only of interest when protecting/unprotecting a {@linkplain
     * QuicPacket.PacketType#RETRY Retry Packet}.
     *
     * @return the original destination connection id, required for calculating
     * the retry integrity tag
     */
    QuicConnectionId originalServerConnId();

    /**
     * Returns the TLS engine associated with this context
     * @return the TLS engine associated with this context
     */
    QuicTLSEngine getTLSEngine();

    /**
     * Checks if the provided token is valid for the given context and connection ID.
     * @param destinationID destination connection ID found in the packet
     * @param token token to verify
     * @return true if token is valid, false otherwise
     */
    boolean verifyToken(QuicConnectionId destinationID, byte[] token);

    /**
     * {@return The minimum payload size for short packet payloads}.
     * Padding will be added to match that size if needed.
     * @param destConnectionIdLength the length of the destination
     *                               connectionId included in the packet
     */
    default int minShortPacketPayloadSize(int destConnectionIdLength) {
        // See RFC 9000, Section 10.3
        // https://www.rfc-editor.org/rfc/rfc9000#section-10.3
        // [..] the endpoint SHOULD ensure that all packets it sends
        // are at least 22 bytes longer than the minimum connection
        // ID length that it requests the peer to include in its
        // packets [...]
        //
        // A 1-RTT packet contains the peer connection id
        // (whose length is destConnectionIdLength), therefore the
        // payload should be at least 5 - (destConnectionIdLength
        // - connectionIdLength()) - where connectionIdLength is the
        // length of the local connection ID.
        return 5 - (destConnectionIdLength - connectionIdLength());
    }
}
