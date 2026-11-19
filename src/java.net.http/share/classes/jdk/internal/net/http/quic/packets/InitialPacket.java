/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.packets;

import java.util.List;

import jdk.internal.net.http.quic.frames.QuicFrame;

/**
 * This class models Quic Initial Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.2">RFC 9000, Section 17.2.2</a>:
 *
 *  <blockquote><pre>{@code
 *    An Initial packet uses long headers with a type value of 0x00.
 *    It carries the first CRYPTO frames sent by the client and server to perform
 *    key exchange, and it carries ACK frames in either direction.
 *
 *    Initial Packet {
 *      Header Form (1) = 1,
 *      Fixed Bit (1) = 1,
 *      Long Packet Type (2) = 0,
 *      Reserved Bits (2),         # Protected
 *      Packet Number Length (2),  # Protected
 *      Version (32),
 *      DCID Len (8),
 *      Destination Connection ID (0..160),
 *      SCID Len (8),
 *      Source Connection ID (0..160),
 *      Token Length (i),
 *      Token (..),
 *      Length (i),
 *      Packet Number (8..32),     # Protected
 *      # Protected Packet Payload (..)
 *      Protected Payload (0..24), # Skipped Part
 *      Protected Payload (128),   # Sampled Part
 *      Protected Payload (..)     # Remainder
 *    }
 * }</pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000>Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369>Quic Version 2</a>.
 * Note that Quic Version 2 uses the same Initial Packet structure than
 * Quic Version 1, but uses a different long packet type than that shown above. See
 * <a href="https://www.rfc-editor.org/rfc/rfc9369#section-3.2">RFC 9369, Section 3.2</a>.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public interface InitialPacket extends LongHeaderPacket {
    @Override
    default PacketType packetType() {
        return PacketType.INITIAL;
    }

    @Override
    default PacketNumberSpace numberSpace() {
        return PacketNumberSpace.INITIAL;
    }

    @Override
    default boolean hasLength() { return true; }

    /**
     * {@return the length of the token field, if present, 0 if not}
     */
    int tokenLength();

    /**
     * {@return the token bytes, if present, {@code null} if not}
     *
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.2">
     *      RFC 9000, Section 17.2.2</a>:
     *
     * <blockquote><pre>{@code
     *    The value of the token that was previously provided
     *    in a Retry packet or NEW_TOKEN frame; see Section 8.1.
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-8.1">
     *     RFC 9000, Section 8.1</a>
     */
    byte[] token();

    /**
     * This packet number.
     * @return this packet number.
     */
    @Override
    long packetNumber();

    @Override
    List<QuicFrame> frames();

    @Override
    default String prettyPrint() {
        return String.format("%s(pn:%s, size=%s, token[%s], frames:%s)", packetType(), packetNumber(),
                size(), tokenLength(), frames());
    }

}
