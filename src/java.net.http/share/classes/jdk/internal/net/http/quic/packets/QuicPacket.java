/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

import jdk.internal.net.http.quic.PeerConnectionId;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.quic.QuicTLSEngine.KeySpace;

/**
 * A super-interface for all specific Quic packet implementation
 * classes.
 */
public interface QuicPacket {

    /**
     * {@return the packet's Destination Connection ID}
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-7.2">
     *     RFC 9000, Section 7.2</a>
     */
    QuicConnectionId destinationId();

    /**
     * The packet number space.
     * NONE is for packets that don't have a packet number,
     * such as Stateless Reset.
     */
    enum PacketNumberSpace {
        INITIAL, HANDSHAKE, APPLICATION, NONE;

        /**
         * Maps a {@code PacketType} to the corresponding
         * packet number space.
         * <p>
         * For {@link PacketType#RETRY}, {@link PacketType#VERSIONS}, and
         * {@link PacketType#NONE}, {@link PacketNumberSpace#NONE} is returned.
         *
         * @param packetType a packet type
         *
         * @return the packet number space that corresponds to the
         *         given packet type
         */
        public static PacketNumberSpace of(PacketType packetType) {
            return switch (packetType) {
                case ONERTT, ZERORTT -> APPLICATION;
                case INITIAL -> INITIAL;
                case HANDSHAKE -> HANDSHAKE;
                case RETRY, VERSIONS, NONE -> NONE;
            };
        }

        /**
         * Maps a {@code KeySpace} to the corresponding
         * packet number space.
         * <p>
         * For {@link KeySpace#RETRY}, {@link PacketNumberSpace#NONE}
         * is returned.
         *
         * @param keySpace a key space
         *
         * @return the packet number space that corresponds to the given
         *          key space.
         */
        public static PacketNumberSpace of(KeySpace keySpace) {
            return switch (keySpace) {
                case ONE_RTT, ZERO_RTT -> APPLICATION;
                case HANDSHAKE -> HANDSHAKE;
                case INITIAL -> INITIAL;
                case RETRY -> NONE;
            };
        }
    }

    /**
     * The packet type for Quic packets.
     */
    enum PacketType {
        NONE, INITIAL, VERSIONS, ZERORTT, HANDSHAKE, RETRY, ONERTT;
        public boolean isLongHeaderType() {
            return switch (this) {
                case ONERTT, NONE, VERSIONS ->  false;
                default -> true;
            };
        }

        /**
         * {@return true if packets of this type are short-header packets}
         */
        public boolean isShortHeaderType() {
            return this == ONERTT;
        }

        /**
         * {@return the QUIC-TLS key space corresponding to this packet type}
         * Some packet types, such as {@link #VERSIONS}, do not have an associated
         * key space.
         */
        public Optional<KeySpace> keySpace() {
            return switch (this) {
                case INITIAL -> Optional.of(KeySpace.INITIAL);
                case HANDSHAKE -> Optional.of(KeySpace.HANDSHAKE);
                case RETRY -> Optional.of(KeySpace.RETRY);
                case ZERORTT -> Optional.of(KeySpace.ZERO_RTT);
                case ONERTT -> Optional.of(KeySpace.ONE_RTT);
                case VERSIONS -> Optional.empty();
                case NONE -> Optional.empty();
            };
        }
    }

    /**
     * The Headers Type of the packet.
     * This is either SHORT or LONG, or NONE when it can't be
     * determined, or when we know that the packet is a stateless
     * reset packet. A stateless reset packet is indistinguishable
     * from a short header packet, so we only know that a packet
     * is a stateless reset if we built it. In that case, the packet
     * may advertise its header's type as NONE.
     */
    enum HeadersType { NONE, SHORT, LONG}

    /**
     * This packet number space.
     * @return this packet number space.
     */
    PacketNumberSpace numberSpace();

    /**
     * This packet size.
     * @return the number of bytes needed to encode the packet.
     * @see #payloadSize()
     * @see #length()
     */
    int size();

    /**
     * {@return true if this packet is <em>ACK-eliciting</em>}
     * A packet is <em>ACK-eliciting</em> if it contains any
     * {@linkplain QuicFrame#isAckEliciting()
     * <em>ACK-eliciting frame</em>}.
     */
    default boolean isAckEliciting() {
        List<QuicFrame> frames = frames();
        if (frames == null || frames.isEmpty()) return false;
        return frames.stream().anyMatch(QuicFrame::isAckEliciting);
    }

    /**
     * Whether this packet has a length field whose value can be read
     * from the packet bytes.
     * @return whether this packet has a length.
     */
    default boolean hasLength() {
        return switch (packetType()) {
            case INITIAL, ZERORTT, HANDSHAKE -> true;
            default -> false;
        };
    }

    /**
     * Returns the length of the payload and packet number. Includes encryption tag.
     *
     * This is the value stored in the {@code Length} field in Initial,
     * Handshake and 0-RTT packets.
     * @return the length of the payload and packet number.
     * @throws UnsupportedOperationException if this packet type does not have
     *         the {@code Length} field.
     * @see #hasLength()
     * @see #size()
     * @see #payloadSize()
     */
    default int length() {
        throw new UnsupportedOperationException();
    }

    /**
     * This packet header's type. Either SHORT or LONG.
     * @return this packet's header's type.
     */
    HeadersType headersType();

    /**
     * {@return this packet's type}
     */
    PacketType packetType();

    /**
     * {@return this packet's packet number, if applicable, {@code -1L} otherwise}
     */
    default long packetNumber() {
        return -1L;
    }

    /**
     * {@return this packet's frames}
     */
    default List<QuicFrame> frames() {
        return List.of();
    }

    /**
     * {@return the packet's payload size}
     * This is the number of bytes needed to encode the packet's
     * {@linkplain #frames() frames}.
     * @see #size()
     * @see #length()
     */
    default int payloadSize() {
        List<QuicFrame> frames = frames();
        if (frames == null || frames.isEmpty()) return 0;
        return frames.stream()
                .mapToInt(QuicFrame::size)
                .reduce(0, Math::addExact);
    }

    /**
     * {@return true if the packet contains a CONNECTION_CLOSE frame, false otherwise}
     */
    default boolean containsConnectionClose() {
        for (QuicFrame frame : frames()) {
            if (frame instanceof ConnectionCloseFrame) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the headers type from the given byte.
     * @param first the first byte of a quic packet
     * @return the headers type encoded in the given byte.
     */
    static HeadersType headersType(byte first) {
        int type = first & 0x80;
        return type == 0 ? HeadersType.SHORT : HeadersType.LONG;
    }

    /**
     * Peeks at the headers type in the given byte buffer.
     * Does not advance the cursor.
     *
     * @apiNote This method starts reading at the offset but respects
     *          the buffer limit.The provided offset must be less than the buffer
     *          limit in order for this method to read the header
     *          bytes.
     *
     * @param buffer the byte buffer containing a packet.
     * @param offset the offset at which the packet starts.
     *
     * @return the header's type of the packet contained in this
     *    byte buffer. NONE if the header's type cannot be determined.
     */
    static HeadersType peekHeaderType(ByteBuffer buffer, int offset) {
        if (offset < 0 || offset >= buffer.limit()) return HeadersType.NONE;
        return headersType(buffer.get(offset));
    }

    /**
     * Reads a connection ID length from the connection ID length
     * byte.
     * @param length the connection ID length byte.
     * @return the connection ID length
     */
    private static int connectionIdLength(byte length) {
        // length is represented by an unsigned byte.
        return length & 0xFF;
    }

    /**
     * Peeks at the connection id in the long header packet bytes.
     * This method doesn't advance the cursor.
     * The buffer position must be at the start of the long header packet.
     *
     * @param buffer the buffer containing a long headers packet.
     * @return A ByteBuffer slice containing the connection id bytes,
     *    or null if the packet is malformed and the connection id
     *    could not be read.
     */
    static ByteBuffer peekLongConnectionId(ByteBuffer buffer) {
        // the connection id length starts at index 5 (1 byte for headers,
        // 4 bytes for version)
        var pos = buffer.position();
        var remaining = buffer.remaining();
        if (remaining < 6) return null;
        int length = connectionIdLength(buffer.get(pos + 5));
        if (length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) return null;
        if (length > remaining - 6) return null;
        return buffer.slice(pos + 6, length);
    }

    /**
     * Peeks at the header in the long header packet bytes.
     * This method doesn't advance the cursor.
     * The buffer position must be at the start of the long header packet.
     *
     * @param buffer the buffer containing a long header packet.
     * @return A LongHeader containing the packet header data,
     *    or null if the packet is malformed
     */
    static LongHeader peekLongHeader(ByteBuffer buffer) {
        return peekLongHeader(buffer, buffer.position());
    }

    /**
     * Peeks at the header in the long header packet bytes.
     * This method doesn't advance the cursor.
     *
     * @param buffer the buffer containing a long header packet.
     * @param offset the position of the start of the packet
     * @return A LongHeader containing the packet header data,
     *    or null if the packet is malformed
     */
    static LongHeader peekLongHeader(ByteBuffer buffer, int offset) {
        // the destination connection id length starts at index 5
        // (1 byte for headers, 4 bytes for version)
        // Therefore the packet needs at least 6 bytes to contain
        // a DCID length (coded on 1 byte)
        var remaining = buffer.remaining();
        var limit = buffer.limit();
        if (remaining < 7) return null;
        if ((buffer.get(offset) & 0x80) == 0) {
            // short header
            return null;
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        int version = buffer.getInt(offset+1);


        // read the DCID length (coded on 1 byte)
        int length = connectionIdLength(buffer.get(offset + 5));
        if (length < 0 || length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) return null;
        QuicConnectionId destinationId = new PeerConnectionId(buffer.slice(offset + 6, length), null);

        // We need at least 6 + length + 1 byte to have
        // a chance to read the SCID length (coded on 1 byte)
        if (length > remaining - 7) return null;
        int srcPos = offset + 6 + length;

        // read the SCID length
        int srclength = connectionIdLength(buffer.get(srcPos));
        if (srclength < 0 || srclength > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) return null;
        // we need at least pos + srclength + 1 byte in the
        // packet to peek at the SCID
        if (srclength > limit - srcPos - 1) return null;
        QuicConnectionId sourceId = new PeerConnectionId(buffer.slice(srcPos + 1, srclength), null);
        int headerLength = 7 + length + srclength;

        // Return the SCID as a buffer slice.
        // The SCID begins at pos + 1 and has srclength bytes.
        return new LongHeader(version, destinationId, sourceId, headerLength);
    }

    /**
     * Returns a bytebuffer containing the token from initial packet.
     * This method doesn't advance the cursor.
     * The buffer position must be at the start of an initial packet.
     * @apiNote
     * If the initial packet doesn't contain any token, an empty
     * {@code ByteBuffer} is returned.
     * @param buffer the buffer containing an initial packet.
     * @return token or null if packet is malformed
     */
    static ByteBuffer peekInitialPacketToken(ByteBuffer buffer) {

        // the destination connection id length starts at index 5
        // (1 byte for headers, 4 bytes for version)
        // Therefore the packet needs at least 6 bytes to contain
        // a DCID length (coded on 1 byte)
        var pos = buffer.position();
        var remaining = buffer.remaining();
        var limit = buffer.limit();
        if (remaining < 6) return null;

        // read the DCID length (coded on 1 byte)
        int length = connectionIdLength(buffer.get(pos + 5));
        if (length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) return null;
        if (length < 0) return null;

        // skip the DCID, and read the SCID length
        // We need at least 6 + length + 1 byte to have
        // a chance to read the SCID length (coded on 1 byte)
        pos = pos + 6 + length;
        if (pos > limit - 1) return null;

        // read the SCID length
        int srclength = connectionIdLength(buffer.get(pos));
        if (srclength > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) return null;
        if (srclength < 0) return null;
        // we need at least pos + srclength + 1 byte in the
        // packet to peek at the token
        if (srclength > limit - pos - 1) return null;

        //skip the SCID, and read the token length
        pos = pos + srclength + 1;

        // read the token length
        int tokenLengthLength = VariableLengthEncoder.peekEncodedValueSize(buffer, pos);
        assert tokenLengthLength <= 8;
        if (pos > limit - tokenLengthLength -1) return null;
        long tokenLength = VariableLengthEncoder.peekEncodedValue(buffer, pos);
        if (tokenLength < 0 || tokenLength > Integer.MAX_VALUE) return null;
        if (tokenLength > limit - pos - tokenLengthLength) return null;

        // return the token
        return buffer.slice(pos + tokenLengthLength, (int)tokenLength);
    }

    /**
     * Peeks at the connection id in the short header packet bytes.
     * This method doesn't advance the cursor.
     * The buffer position must be at the start of the short header packet.
     *
     * @param buffer the buffer containing a short headers packet.
     * @param length the connection id length.
     *
     * @return A ByteBuffer slice containing the connection id bytes,
     *    or null if the packet is malformed and the connection id
     *    could not be read.
     */
    static ByteBuffer peekShortConnectionId(ByteBuffer buffer, int length) {
        int pos = buffer.position();
        int limit = buffer.limit();
        assert pos >= 0;
        assert length <= QuicConnectionId.MAX_CONNECTION_ID_LENGTH;
        if (limit - pos < length + 1) return null;
        return buffer.slice(pos+1, length);
    }

    /**
     * Returns the version of the first packet in the buffer.
     * This method doesn't advance the cursor.
     * Returns 0 if the version is 0 (version negotiation packet),
     * or if the version cannot be determined.
     * The packet is expected to start at the buffer's current position.
     *
     * @implNote
     * This is equivalent to calling:
     * {@code QuicPacket.peekVersion(buffer, buffer.position())}.
     *
     * @param buffer the buffer containing the packet.
     *
     * @return the version of the packet in the buffer, or 0.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *     RFC 8999: Version-Independent Properties of QUIC</a>
     */
    static int peekVersion(ByteBuffer buffer) {
        return peekVersion(buffer, buffer.position());
    }


    /**
     * Returns the version of the first packet in the buffer.
     * This method doesn't advance the cursor.
     * Returns 0 if the version is 0 (version negotiation packet),
     * or if the version cannot be determined.
     *
     * @apiNote This method starts reading at the offset but respects
     *          the buffer limit. The buffer limit must allow for reading
     *          the header byte and version number starting at the offset.
     *
     * @param buffer the buffer containing the packet.
     * @param offset the offset at which the packet starts.
     *
     * @return the version of the packet in the buffer, or 0.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *     RFC 8999: Version-Independent Properties of QUIC</a>
     */
    static int peekVersion(ByteBuffer buffer, int offset) {
        int limit = buffer.limit();
        assert offset >= 0;
        if (limit - offset < 5) return 0;
        HeadersType headersType = peekHeaderType(buffer, offset);
        if (headersType == HeadersType.LONG) {
            assert buffer.order() == ByteOrder.BIG_ENDIAN;
            return buffer.getInt(offset+1);
        }
        return 0;
    }

    /**
     * Returns true if the first packet in the buffer is a version
     * negotiation packet.
     * This method doesn't advance the cursor.
     *
     * @apiNote This method starts reading at the offset but respects
     *          the buffer limit. If the packet is a long header packet,
     *          the buffer limit must allow for reading
     *          the header byte and version number starting at the offset.
     *
     * @param buffer the buffer containing the packet.
     * @param offset the offset at which the packet starts.
     *
     * @return true if the first packet in the buffer is a version
     *         negotiation packet.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *     RFC 8999: Version-Independent Properties of QUIC</a>
     */
    static boolean isVersionNegotiation(ByteBuffer buffer, int offset) {
        int limit = buffer.limit();
        if (limit - offset < 5) return false;
        HeadersType headersType = peekHeaderType(buffer, offset);
        if (headersType == HeadersType.LONG) {
            assert buffer.order() == ByteOrder.BIG_ENDIAN;
            return buffer.getInt(offset+1) == 0;
        }
        return false;
    }

    default String prettyPrint() {
        long pn = packetNumber();
        if (pn >= 0) {
            return String.format("%s(pn:%s, size=%s, frames:%s)", packetType(), pn, size(), frames());
        } else {
            return String.format("%s(size=%s)", packetType(), size());
        }
    }

}
