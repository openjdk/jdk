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

/**
 * This class models Quic Retry Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.5">RFC 9000, Section 17.2.5</a>:
 *
 *  <blockquote><pre>{@code
 *    A Retry packet uses a long packet header with a type value of 0x03.
 *    It carries an address validation token created by the server.
 *    It is used by a server that wishes to perform a retry; see Section 8.1.
 *
 *    Retry Packet {
 *      Header Form (1) = 1,
 *      Fixed Bit (1) = 1,
 *      Long Packet Type (2) = 3,
 *      Unused (4),
 *      Version (32),
 *      Destination Connection ID Length (8),
 *      Destination Connection ID (0..160),
 *      Source Connection ID Length (8),
 *      Source Connection ID (0..160),
 *      Retry Token (..),
 *      Retry Integrity Tag (128),
 *    }
 * }</pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000>Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369>Quic Version 2</a>.
 * Note that Quic Version 2 uses the same Retry Packet structure than
 * Quic Version 1, but uses a different long packet type than that shown above. See
 * <a href="https://www.rfc-editor.org/rfc/rfc9369#section-3.2">RFC 9369, Section 3.2</a>.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.5">RFC 9000, Section 8.1/a>
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public interface RetryPacket extends LongHeaderPacket {
    @Override
    default PacketType packetType() {
        return PacketType.RETRY;
    }

    /**
     * This packet type is not numbered: returns
     * {@link PacketNumberSpace#NONE} always.
     * @return {@link PacketNumberSpace#NONE}
     */
    @Override
    default PacketNumberSpace numberSpace() {
        return PacketNumberSpace.NONE;
    }

    /**
     * This packet type is not numbered: always returns -1L.
     * @return -1L
     */
    @Override
    default long packetNumber() { return -1L; }

    /**
     * {@return the packet's retry token}
     *
     * As per <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.5">RFC 9000, Section 17.2.5</a>:
     * <blockquote><pre>{@code
     *    An opaque token that the server can use to validate the client's address.
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#validate-handshake">
     *     RFC 9000, Section 8.1</a>
     */
    byte[] retryToken();
}
