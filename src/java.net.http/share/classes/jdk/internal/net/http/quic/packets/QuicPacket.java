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
package jdk.internal.net.http.quic.packets;

import java.util.List;
import java.util.Optional;

import jdk.internal.net.http.quic.QuicConnectionId;
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
     * {@return this packet's number space}
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

    default String prettyPrint() {
        long pn = packetNumber();
        if (pn >= 0) {
            return String.format("%s(pn:%s, size=%s, frames:%s)", packetType(), pn, size(), frames());
        } else {
            return String.format("%s(size=%s)", packetType(), size());
        }
    }

}
