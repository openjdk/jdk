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

package jdk.internal.net.http.quic.packets;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.PeerConnectionId;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.CodingContext;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.http.quic.VariableLengthEncoder;

import javax.crypto.AEADBadTagException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;
import java.util.List;
import java.nio.BufferUnderflowException;

/**
 * A {@code QuicPacketDecoder} encapsulates the logic to decode a
 * quic packet. A {@code QuicPacketDecoder} is typically tied to
 * a particular version of the QUIC protocol.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9001
 *      RFC 9001: Using TLS to Secure QUIC
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public class QuicPacketDecoder {

    private static final Logger debug = Utils.getDebugLogger(() -> "QuicPacketDecoder");

    private final QuicVersion quicVersion;
    private QuicPacketDecoder(final QuicVersion quicVersion) {
        this.quicVersion = quicVersion;
    }

    /**
     * Reads the headers type from the given byte.
     * @param first the first byte of a quic packet
     * @return the headers type encoded in the given byte.
     */
    private static QuicPacket.HeadersType headersType(byte first) {
        int type = first & 0x80;
        return type == 0 ? QuicPacket.HeadersType.SHORT : QuicPacket.HeadersType.LONG;
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
    public static QuicPacket.HeadersType peekHeaderType(ByteBuffer buffer, int offset) {
        if (offset < 0 || offset >= buffer.limit()) return QuicPacket.HeadersType.NONE;
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
    public static ByteBuffer peekLongConnectionId(ByteBuffer buffer) {
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
    public static LongHeader peekLongHeader(ByteBuffer buffer) {
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
    public static LongHeader peekLongHeader(ByteBuffer buffer, int offset) {
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
    public static ByteBuffer peekInitialPacketToken(ByteBuffer buffer) {

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
    public static ByteBuffer peekShortConnectionId(ByteBuffer buffer, int length) {
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
     * {@code peekVersion(buffer, buffer.position())}.
     *
     * @param buffer the buffer containing the packet.
     *
     * @return the version of the packet in the buffer, or 0.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *     RFC 8999: Version-Independent Properties of QUIC</a>
     */
    public static int peekVersion(ByteBuffer buffer) {
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
    public static int peekVersion(ByteBuffer buffer, int offset) {
        int limit = buffer.limit();
        assert offset >= 0;
        if (limit - offset < 5) return 0;
        QuicPacket.HeadersType headersType = peekHeaderType(buffer, offset);
        if (headersType == QuicPacket.HeadersType.LONG) {
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
    private static boolean isVersionNegotiation(ByteBuffer buffer, int offset) {
        int limit = buffer.limit();
        if (limit - offset < 5) return false;
        QuicPacket.HeadersType headersType = peekHeaderType(buffer, offset);
        if (headersType == QuicPacket.HeadersType.LONG) {
            assert buffer.order() == ByteOrder.BIG_ENDIAN;
            return buffer.getInt(offset+1) == 0;
        }
        return false;
    }

    public abstract static class IncomingQuicPacket implements QuicPacket {
        private final QuicConnectionId destinationId;

        protected IncomingQuicPacket(QuicConnectionId destinationId) {
            this.destinationId = destinationId;
        }

        @Override
        public final QuicConnectionId destinationId() { return destinationId; }
    }

    private abstract static class IncomingLongHeaderPacket
            extends IncomingQuicPacket implements LongHeaderPacket {

        private final QuicConnectionId sourceId;
        private final int version;
        IncomingLongHeaderPacket(QuicConnectionId sourceId,
                                 QuicConnectionId destinationId,
                                 int version) {
            super(destinationId);
            this.sourceId = sourceId;
            this.version = version;
        }

        @Override
        public final QuicConnectionId sourceId() { return sourceId; }

        @Override
        public final int version() { return version; }
    }

    private abstract static class IncomingShortHeaderPacket
            extends IncomingQuicPacket implements ShortHeaderPacket {

        IncomingShortHeaderPacket(QuicConnectionId destinationId) {
            super(destinationId);
        }
    }

    private static final class IncomingRetryPacket
            extends IncomingLongHeaderPacket implements RetryPacket {
        final int size;
        final byte[] retryToken;

        private IncomingRetryPacket(QuicConnectionId sourceId, QuicConnectionId destinationId,
                                    int version, int size, byte[] retryToken) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.retryToken = retryToken;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public byte[] retryToken() {
            return retryToken;
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingRetryPacket}.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *                the bytes of this packet
         * @param context the decoding context
         *
         * @return an {@code IncomingRetryPacket} with its contents set
         *         according to the packets fields
         *
         * @throws IOException if decoding fails for any reason
         * @throws BufferUnderflowException if buffer does not have enough bytes
         */
        static IncomingRetryPacket decode(PacketReader reader, CodingContext context)
                throws IOException {
            try {
                reader.verifyRetry();
            } catch (AEADBadTagException e) {
                throw new IOException("Bad integrity tag", e);
            }

            int size = reader.remaining();
            if (debug.on()) {
                debug.log("IncomingRetryPacket.decode(%s)", reader);
            }

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            if (debug.on()) {
                debug.log("IncomingRetryPacket.decode(headers(%x), version(%d), %s)",
                        headers, version, reader);
            }

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingRetryPacket.decode(dcid(%d), %s)",
                        destinationID.length(), reader);
            }
            var sourceID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingRetryPacket.decode(scid(%d), %s)",
                        sourceID.length(), reader);
            }

            // Retry Token
            byte[] retryToken = reader.readRetryToken();
            if (debug.on()) {
                debug.log("IncomingRetryPacket.decode(retryToken(%d), %s)",
                        retryToken.length, reader);
            }

            // Retry Integrity Tag
            assert reader.remaining() == 16;
            byte[] retryIntegrityTag = reader.readRetryIntegrityTag();
            if (debug.on()) {
                debug.log("IncomingRetryPacket.decode(retryIntegrityTag(%d), %s)",
                        retryIntegrityTag.length, reader);
            }
            assert size == reader.bytesRead();

            return new IncomingRetryPacket(sourceID, destinationID, version,
                    size, retryToken);
        }
    }

    private static final class IncomingHandshakePacket
            extends IncomingLongHeaderPacket implements HandshakePacket {

        final int size;
        final int length;
        final long packetNumber;
        final List<QuicFrame> frames;

        IncomingHandshakePacket(QuicConnectionId sourceId, QuicConnectionId destinationId,
                                int version, int length, long packetNumber, List<QuicFrame> frames, int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.length = length;
            this.packetNumber = packetNumber;
            this.frames = List.copyOf(frames);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() { return frames; }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingHandshakePacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingHandshakePacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *                the bytes of this packet
         * @param context the decoding context
         *
         * @return an {@code IncomingHandshakePacket} with its contents set
         *         according to the packets fields
         *
         * @throws IOException if decoding fails for any reason
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException if packet is correctly signed but malformed
         */
        static IncomingHandshakePacket decode(PacketReader reader, CodingContext context)
                throws IOException, QuicKeyUnavailableException, QuicTransportException {
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(%s)", reader);
            }

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(headers(%x), version(%d), %s)",
                        headers, version, reader);
            }

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(dcid(%d), %s)",
                        destinationID.length(), reader);
            }
            var sourceID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(scid(%d), %s)",
                        sourceID.length(), reader);
            }

            // Get length of packet number and payload
            var packetLength = reader.readPacketLength();
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(length(%d), %s)",
                        packetLength, reader);
            }

            // Remove protection before reading packet number
            reader.unprotectLong(packetLength);

            // re-read headers, now that protection is removed
            headers = reader.headers();
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode([unprotected]headers(%x), %s)",
                        headers, reader);
            }

            // Packet Number
            var packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(" +
                                "packetNumberLength(%d), packetNumber(%d), %s)",
                        packetNumberLength, packetNumber, reader);
            }

            // Calculate payload length and retrieve payload
            int payloadLen = (int) (packetLength - packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingHandshakePacket.decode(payloadLen(%d), %s)",
                        payloadLen, reader);
            }
            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, -1 /* key phase */);
            } catch (AEADBadTagException e) {
                Log.logError("[Quic] Failed to decrypt HANDSHAKE packet (Bad AEAD tag; discarding packet): " + e);
                Log.logError(e);
                throw new IOException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.2
            if ((headers & 0xc) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                        QuicTLSEngine.KeySpace.HANDSHAKE, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }

            List<QuicFrame> frames = reader.parsePayloadSlice(payload);
            assert !payload.hasRemaining() : "remaining bytes in payload: " + payload.remaining();

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();
            assert size == reader.position() - reader.offset();

            assert packetLength == (int)packetLength;
            return new IncomingHandshakePacket(sourceID, destinationID,
                    version, (int)packetLength, packetNumber, frames, size);
        }
    }

    private static final class IncomingZeroRttPacket
            extends IncomingLongHeaderPacket implements ZeroRttPacket {

        final int size;
        final int length;
        final long packetNumber;
        final List<QuicFrame> frames;

        IncomingZeroRttPacket(QuicConnectionId sourceId, QuicConnectionId destinationId,
                              int version, int length, long packetNumber, List<QuicFrame> frames, int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.length = length;
            this.packetNumber = packetNumber;
            this.frames = List.copyOf(frames);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() { return frames; }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingZeroRttPacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingZeroRttPacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *                the bytes of this packet
         * @param context the decoding context
         *
         * @return an {@code IncomingZeroRttPacket} with its contents set
         *         according to the packets fields
         *
         * @throws IOException if decoding fails for any reason
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException if packet is correctly signed but malformed
         */
        static IncomingZeroRttPacket decode(PacketReader reader, CodingContext context)
                throws IOException, QuicKeyUnavailableException, QuicTransportException {

            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(%s)", reader);
            }

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(headers(%x), version(%d), %s)",
                        headers, version, reader);
            }

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(dcid(%d), %s)",
                        destinationID.length(), reader);
            }
            var sourceID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(scid(%d), %s)",
                        sourceID.length(), reader);
            }

            // Get length of packet number and payload
            var length = reader.readPacketLength();
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(length(%d), %s)",
                        length, reader);
            }

            // Remove protection before reading packet number
            reader.unprotectLong(length);

            // re-read headers, now that protection is removed
            headers = reader.headers();
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode([unprotected]headers(%x), %s)",
                        headers, reader);
            }

            // Packet Number
            var packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(" +
                                "packetNumberLength(%d), packetNumber(%d), %s)",
                        packetNumberLength, packetNumber, reader);
            }

            // Calculate payload length and retrieve payload
            int payloadLen = (int) (length - packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingZeroRttPacket.decode(payloadLen(%d), %s)",
                        payloadLen, reader);
            }
            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, -1 /* key phase */);
            } catch (AEADBadTagException e) {
                Log.logError("[Quic] Failed to decrypt ZERORTT packet (Bad AEAD tag; discarding packet): " + e);
                Log.logError(e);
                throw new IOException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.2
            if ((headers & 0xc) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                        QuicTLSEngine.KeySpace.ZERO_RTT, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            List<QuicFrame> frames = reader.parsePayloadSlice(payload);
            assert !payload.hasRemaining() : "remaining bytes in payload: " + payload.remaining();

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();

            assert length == (int)length;
            return new IncomingZeroRttPacket(sourceID, destinationID,
                    version, (int)length, packetNumber, frames, size);
        }
    }

    private static final class IncomingOneRttPacket
            extends IncomingShortHeaderPacket implements OneRttPacket {

        final int size;
        final long packetNumber;
        final List<QuicFrame> frames;
        final int keyPhase;
        final int spin;

        IncomingOneRttPacket(QuicConnectionId destinationId,
                             long packetNumber, List<QuicFrame> frames,
                             int spin, int keyPhase, int size) {
            super(destinationId);
            this.keyPhase = keyPhase;
            this.spin = spin;
            this.size = size;
            this.packetNumber = packetNumber;
            this.frames = frames;
        }

        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int keyPhase() {
            return keyPhase;
        }

        @Override
        public int spin() {
            return spin;
        }

        @Override
        public List<QuicFrame> frames() { return frames; }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingOneRttPacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingOneRttPacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *                the bytes of this packet
         * @param context the decoding context
         *
         * @return an {@code IncomingOneRttPacket} with its contents set
         *         according to the packets fields
         *
         * @throws IOException if decoding fails for any reason
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException if packet is correctly signed but malformed
         */
        static IncomingOneRttPacket decode(PacketReader reader, CodingContext context)
                throws IOException, QuicKeyUnavailableException, QuicTransportException {

            if (debug.on()) {
                debug.log("IncomingOneRttPacket.decode(%s)", reader);
            }

            byte headers = reader.readHeaders(); // read headers
            if (debug.on()) {
                debug.log("IncomingOneRttPacket.decode(headers(%x), %s)",
                        headers, reader);
            }

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readShortConnectionId();
            if (debug.on()) {
                debug.log("IncomingOneRttPacket.decode(dcid(%d), %s)",
                        destinationID.length(), reader);
            }

            // Remove protection before reading packet number
            reader.unprotectShort();

            // re-read headers, now that protection is removed
            headers = reader.headers();
            if (debug.on()) {
                debug.log("IncomingOneRttPacket.decode([unprotected]headers(%x), %s)",
                        headers, reader);
            }
            // Packet Number
            var packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingOneRttPacket.decode(" +
                                "packetNumberLength(%d), packetNumber(%d), %s)",
                        packetNumberLength, packetNumber, reader);
            }

            // Calculate payload length and retrieve payload
            int payloadLen = reader.remaining();
            if (debug.on()) {
                debug.log("IncomingOneRttPacket.decode(payloadLen(%d), %s)",
                        payloadLen, reader);
            }
            final int keyPhase = (headers & 0x04) >> 2;
            // keyphase is a 1 bit structure, so only 0 or 1 are valid values
            assert keyPhase == 0 || keyPhase == 1 : "unexpected key phase: " + keyPhase;
            final int spin = (headers & 0x20) >> 5;
            assert spin == 0 || spin == 1 : "unexpected spin bit: " + spin;

            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, keyPhase);
            } catch (AEADBadTagException e) {
                Log.logError("[Quic] Failed to decrypt ONERTT packet (Bad AEAD tag; discarding packet): " + e);
                Log.logError(e);
                throw new IOException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.3.1
            if ((headers & 0x18) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                        QuicTLSEngine.KeySpace.ONE_RTT, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            List<QuicFrame> frames = reader.parsePayloadSlice(payload);
            assert !payload.hasRemaining() : "remaining bytes in payload: " + payload.remaining();

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();

            return new IncomingOneRttPacket(destinationID, packetNumber, frames, spin, keyPhase, size);
        }
    }

    private static final class IncomingInitialPacket
            extends IncomingLongHeaderPacket implements InitialPacket {

        final int size;
        final int length;
        final int tokenLength;
        final long packetNumber;
        final byte[] token;
        final List<QuicFrame> frames;

        IncomingInitialPacket(QuicConnectionId sourceId,
                              QuicConnectionId destinationId, int version,
                              int tokenLength, byte[] token, int length,
                              long packetNumber, List<QuicFrame> frames, int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.length = length;
            this.tokenLength = tokenLength;
            this.token = token;
            this.packetNumber = packetNumber;
            this.frames = List.copyOf(frames);
        }

        @Override
        public int tokenLength() { return tokenLength; }

        @Override
        public byte[] token() { return token; }

        @Override
        public int length() { return length; }

        @Override
        public long packetNumber() { return packetNumber; }

        @Override
        public int size() { return size; }

        @Override
        public List<QuicFrame> frames() { return frames; }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingInitialPacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingInitialPacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *                the bytes of this packet
         * @param context the decoding context
         *
         * @return an {@code IncomingInitialPacket} with its contents set
         *         according to the packets fields
         *
         * @throws IOException if decoding fails for any reason
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException if packet is correctly signed but malformed
         */
        static IncomingInitialPacket decode(PacketReader reader, CodingContext context)
                throws IOException, QuicKeyUnavailableException, QuicTransportException {

            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(%s)", reader);
            }

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode([protected]headers(%x), version(%d), %s)",
                        headers, version, reader);
            }

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(dcid(%d), %s)",
                        destinationID.length(), reader);
            }
            var sourceID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(scid(%d), %s)",
                        sourceID.length(), reader);
            }

            // Get number of bytes needed to store the length of the token
            var tokenLength = (int) reader.readTokenLength();
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(token-length(%d), %s)",
                        tokenLength, reader);
            }
            var token = reader.readToken(tokenLength);
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(token(%d), %s)",
                        token == null ? 0 : token.length, reader);
            }

            // Get length of packet number and payload
            var packetLength = reader.readPacketLength();
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(packetLength(%d), %s)",
                        packetLength, reader);
            }
            assert packetLength == (int)packetLength;
            if (packetLength > reader.remaining()) {
                if (debug.on()) {
                    debug.log("IncomingInitialPacket rejected, invalid length(%d/%d), %s)",
                            packetLength, reader.remaining(), reader);
                }
                throw new BufferUnderflowException();
            }


            // get the size (in bytes) of new packet
            int size = reader.bytesRead() + (int)packetLength;

            if (!context.verifyToken(destinationID, token)) {
                if (debug.on()) {
                    debug.log("IncomingInitialPacket rejected, invalid token(%s), %s)",
                            HexFormat.of().formatHex(token), reader);
                }
                return null;
            }

            // Remove protection before reading packet number
            reader.unprotectLong(packetLength);

            // re-read headers, now that protection is removed
            headers = reader.headers();
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode([unprotected]headers(%x), %s)",
                        headers, reader);
            }

            // Packet Number
            int packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(" +
                                "packetNumberLength(%d), packetNumber(%d), %s)",
                        packetNumberLength, packetNumber, reader);
            }

            // Calculate payload length and retrieve payload
            int payloadLen = (int) (packetLength - packetNumberLength);
            if (debug.on()) {
                debug.log("IncomingInitialPacket.decode(payloadLen(%d), %s)",
                        payloadLen, reader);
            }
            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, -1 /* key phase */);
            } catch (AEADBadTagException e) {
                Log.logError("[Quic] Failed to decrypt INITIAL packet (Bad AEAD tag; discarding packet): " + e);
                Log.logError(e);
                throw new IOException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.2
            if ((headers & 0xc) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                        QuicTLSEngine.KeySpace.INITIAL, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            List<QuicFrame> frames = reader.parsePayloadSlice(payload);
            assert !payload.hasRemaining() : "remaining bytes in payload: " + payload.remaining();

            assert size == reader.bytesRead() : size - reader.bytesRead();

            return new IncomingInitialPacket(sourceID, destinationID,
                    version, tokenLength, token, (int)packetLength, packetNumber, frames, size);
        }

    }

    private static final class IncomingVersionNegotiationPacket
            extends IncomingLongHeaderPacket
            implements VersionNegotiationPacket {

        final int size;
        final int[] versions;

        IncomingVersionNegotiationPacket(QuicConnectionId sourceId,
                                         QuicConnectionId destinationId,
                                         int version, int[] versions,
                                         int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.versions = Objects.requireNonNull(versions);
        }

        @Override
        public int size() { return size; }

        @Override
        public List<QuicFrame> frames() { return List.of(); }

        @Override
        public int payloadSize() { return versions.length << 2; }

        @Override
        public int[] supportedVersions() {
            return versions;
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingVersionNegotiationPacket}.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *                the bytes of this packet
         * @param context the decoding context
         *
         * @return an {@code IncomingVersionNegotiationPacket} with its contents set
         *         according to the packets fields
         *
         * @throws IOException if decoding fails for any reason
         * @throws BufferUnderflowException if buffer does not have enough bytes
         */
        static IncomingVersionNegotiationPacket decode(PacketReader reader, CodingContext context)
                throws IOException {

            if (debug.on()) {
                debug.log("IncomingVersionNegotiationPacket.decode(%s)", reader);
            }

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            if (debug.on()) {
                debug.log("IncomingVersionNegotiationPacket.decode(headers(%x), version(%d), %s)",
                        headers, version, reader);
            }
            // The long header bit should be set. We should ignore the other 7 bits
            assert QuicPacketDecoder.headersType(headers) == HeadersType.LONG || (headers & 0x80) == 0x80;

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingVersionNegotiationPacket.decode(dcid(%d), %s)",
                        destinationID.length(), reader);
            }
            var sourceID = reader.readLongConnectionId();
            if (debug.on()) {
                debug.log("IncomingVersionNegotiationPacket.decode(scid(%d), %s)",
                        sourceID.length(), reader);
            }

            // Calculate payload length and retrieve payload
            final int payloadLen = reader.remaining();
            final int versionsCount = payloadLen >> 2;
            if (debug.on()) {
                debug.log("IncomingVersionNegotiationPacket.decode(payloadLen(%d), %s)",
                        payloadLen, reader);
            }
            int[] versions = reader.readSupportedVersions();

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();
            assert !reader.hasRemaining() : "%s superfluous bytes in buffer"
                    .formatted(reader.remaining());

            // sanity checks:
            var msg = "Bad version negotiation packet";
            if (payloadLen != versionsCount << 2) {
                throw new IOException("%s: %s bytes after %s versions"
                        .formatted(msg, payloadLen % 4, versionsCount));
            }
            if (versionsCount == 0) {
                throw new IOException("%s: no supported versions in packet"
                        .formatted(msg));
            }

            return new IncomingVersionNegotiationPacket(sourceID, destinationID,
                    version, versions, size);
        }
    }

    /**
     * Decode the contents of the given {@code ByteBuffer} and, depending on the
     * {@link PacketType}, return a {@link QuicPacket} with the corresponding type.
     * This method removes packet protection and decrypt the packet encoded into
     * the provided byte buffer as appropriate.
     *
     * <p> If successful, an {@code IncomingQuicPacket} instance is returned.
     * The position of the buffer is moved to the first byte following the last
     * decoded byte. The buffer limit is unchanged.
     *
     * <p> Otherwise, an exception is thrown. The position of the buffer is unspecified,
     * but is usually set at the place where the error occurred.
     *
     * @apiNote If successful, and the limit was not reached, this method should be
     * called again to decode the next packet contained in the buffer. Otherwise, if
     * an exception occurs, the remaining bytes in the buffer should be dropped, since
     * the position of the next packet in the buffer cannot be determined with
     * certainty.
     *
     * @param buffer       the buffer with the bytes to be decoded
     * @param context      the decoding context
     *
     * @throws IOException if decoding fails for any reason
     * @throws BufferUnderflowException if buffer does not have enough bytes
     * @throws QuicTransportException if packet is correctly signed but malformed
     *
     * @spec https://www.rfc-editor.org/info/rfc9000
     *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
     * @spec https://www.rfc-editor.org/info/rfc9001
     *      RFC 9001: Using TLS to Secure QUIC
     * @spec https://www.rfc-editor.org/info/rfc9369
     *      RFC 9369: QUIC Version 2
     */
    public IncomingQuicPacket decode(ByteBuffer buffer, CodingContext context)
            throws IOException, QuicKeyUnavailableException, QuicTransportException {
        Objects.requireNonNull(buffer);

        // Save in case we need to discard packet
        int limit = buffer.limit();
        int pos = buffer.position();
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        PacketType type = peekPacketType(buffer);
        PacketReader packetReader = new PacketReader(buffer, context, type);

        QuicTLSEngine.KeySpace keySpace = type.keySpace().orElse(null);
        if (keySpace != null && !context.getTLSEngine().keysAvailable(keySpace)) {
            if (debug.on()) {
                debug.log("QuicPacketDecoder.decode(%s): no keys, skipping", packetReader);
            }
            return null;
        }

        return switch (type) {
            case RETRY     -> IncomingRetryPacket.decode(packetReader, context);
            case ONERTT    -> IncomingOneRttPacket.decode(packetReader, context);
            case ZERORTT   -> IncomingZeroRttPacket.decode(packetReader, context);
            case HANDSHAKE -> IncomingHandshakePacket.decode(packetReader, context);
            case INITIAL   -> IncomingInitialPacket.decode(packetReader, context);
            case VERSIONS  -> IncomingVersionNegotiationPacket.decode(packetReader, context);
            case NONE      -> throw new IOException("Unknown type: " + type); // if junk received
            default -> throw new IOException("Not implemented: " + type); // if has type but not recognised
        };
    }

    private static QuicConnectionId decodeConnectionID(ByteBuffer buffer) {
        if (!buffer.hasRemaining())
            throw new BufferUnderflowException();

        int len = buffer.get() & 0xFF;
        if (len > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        byte[] destinationConnectionID = new byte[len];

        // Save buffer position ahead of time to check after read
        int pos = buffer.position();
        buffer.get(destinationConnectionID);
        // Ensure all bytes have been read correctly
        assert pos + len == buffer.position();

        return new PeerConnectionId(destinationConnectionID);
    }

    /**
     * Peek at the size of the first packet present in the buffer.
     * The position of the buffer must be at the first byte of the
     * first packet. This method doesn't advance the buffer position.
     * @param buffer A byte buffer containing quic packets
     * @return the size of the first packet present in the buffer.
     */
    public int peekPacketSize(ByteBuffer buffer) {
        int pos = buffer.position();
        int limit = buffer.limit();
        int available = limit - pos;
        assert available >= 0 : available;
        if (available <= 0) return available;
        PacketType type = peekPacketType(buffer);
        return switch (type) {
            case HANDSHAKE, INITIAL, ZERORTT -> {
                assert peekVersion(buffer, pos) == quicVersion.versionNumber();
                int end = peekPacketEnd(type, buffer);
                assert end <= limit;
                yield end - pos;
            }
            // ONERTT, RETRY, VERSIONS, NONE:
            default -> available;
        };
    }

    /**
     * Reads the Quic V1 packet type from the given byte.
     *
     * @param headerByte the first byte of a quic packet
     * @return the packet type encoded in the given byte.
     */
    private PacketType packetType(byte headerByte) {
        int htype = headerByte & 0xC0;
        int ptype = headerByte & 0xF0;
        return switch (htype) {
            case 0xC0 -> switch (quicVersion) {
                case QUIC_V1 -> switch (ptype) {
                    case 0xC0 -> PacketType.INITIAL;
                    case 0xD0 -> PacketType.ZERORTT;
                    case 0xE0 -> PacketType.HANDSHAKE;
                    case 0xF0 -> PacketType.RETRY;
                    default -> PacketType.NONE;
                };
                case QUIC_V2 -> switch (ptype) {
                    case 0xD0 -> PacketType.INITIAL;
                    case 0xE0 -> PacketType.ZERORTT;
                    case 0xF0 -> PacketType.HANDSHAKE;
                    case 0xC0 -> PacketType.RETRY;
                    default -> PacketType.NONE;
                };
            };
            case 0x40 -> PacketType.ONERTT; // may be a stateless reset too
            default -> PacketType.NONE;
        };
    }

    public PacketType peekPacketType(ByteBuffer buffer) {
        int offset = buffer.position();
        return peekPacketType(buffer, offset);
    }

    public PacketType peekPacketType(ByteBuffer buffer, int offset) {
        if (offset < 0 || offset >= buffer.limit()) return PacketType.NONE;
        var headers = buffer.get(offset);
        var headersType = headersType(headers);
        if (headersType == QuicPacket.HeadersType.LONG) {
            if (isVersionNegotiation(buffer, offset)) {
                return PacketType.VERSIONS;
            }
            var version = peekVersion(buffer, offset);
            if (version != quicVersion.versionNumber()) {
                return PacketType.NONE;
            }
        }
        return packetType(headers);
    }

    /**
     * Returns the position just after the first packet present in the buffer.
     * @param type    the first packet type. Must be INITIAL, HANDSHAKE, or ZERORTT.
     * @param buffer  the byte buffer containing the packet
     * @return the position just after the first packet present in the buffer.
     */
    private int peekPacketEnd(PacketType type, ByteBuffer buffer) {
        // Store initial position to calculate size of packet decoded
        int initialPosition = buffer.position();
        int limit = buffer.limit();
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        assert type == PacketType.HANDSHAKE
                || type == PacketType.INITIAL
                || type == PacketType.ZERORTT : type;
        // This case should have been handled by the caller
        assert buffer.getInt(initialPosition + 1) != 0 : "version is 0";

        int pos = initialPosition;     // header bits
        pos = pos + 4;                 // version
        pos = pos + 1;                 // dcid length
        if (pos <= 0 || pos >= limit) return limit;
        int dcidlen = buffer.get(pos) & 0xFF;
        pos = pos + dcidlen + 1;       // scid length
        if (pos <= 0 || pos >= limit) return limit;
        int scidlen = buffer.get(pos) & 0xFF;
        pos = pos + scidlen + 1;       // token length or packet length
        if (pos <= 0 || pos >= limit) return limit;

        if (type == PacketType.INITIAL) {
            int tksize = VariableLengthEncoder.peekEncodedValueSize(buffer, pos);
            if (tksize <= 0 || tksize > 8) return limit;
            if (limit - tksize < pos) return limit;
            long tklen = VariableLengthEncoder.peekEncodedValue(buffer, pos);
            if (tklen < 0 || tklen > limit - pos) return limit;
            pos = pos + tksize + (int)tklen; // packet length
            if (pos <= 0 || pos >= limit) return limit;
        }

        int lensize = VariableLengthEncoder.peekEncodedValueSize(buffer, pos);
        if (lensize <= 0 || lensize > 8) return limit;
        long len = VariableLengthEncoder.peekEncodedValue(buffer, pos);
        if (len < 0 || len > limit - pos) return limit;
        pos = pos + lensize + (int)len; // end of packet
        if (pos <= 0 || pos >= limit) return limit;
        return pos;
    }

    /**
     * Find the length of the next packet in the buffer, and return
     * the next packet bytes as a slice of the original packet.
     * Advances the original buffer position to after the returned
     * packet.
     * @param buffer a buffer containing coalesced packets
     * @param offset the offset at which the next packet starts
     * @return the next packet.
     */
    public ByteBuffer nextPacketSlice(ByteBuffer buffer, int offset) {
        assert offset >= 0;
        assert offset <= buffer.limit();
        int pos = buffer.position();
        int limit = buffer.limit();
        buffer.position(offset);
        ByteBuffer next = null;
        try {
            int size = peekPacketSize(buffer);
            if (debug.on()) {
                debug.log("next packet bytes from %d (%d/%d)",
                        offset, size, buffer.remaining());
            }
            next = buffer.slice(offset, size);
            buffer.position(offset + size);
        } catch (Throwable tt) {
            if (debug.on()) {
                debug.log("failed to peek packet size: " + tt, tt);
                debug.log("dropping all remaining bytes (%d)", limit - pos);
            }
            buffer.position(limit);
            next = buffer;
        }
        return next;
    }

    /**
     * Advance the bytebuffer position to the end of the packet
     * @param buffer A byte buffer containing quic packets
     * @param offset The offset at which the packet starts
     */
    public void skipPacket(ByteBuffer buffer, int offset) {
        assert offset >= 0;
        assert offset <= buffer.limit();
        int pos = buffer.position();
        int limit = buffer.limit();
        buffer.position(offset);
        try {
            int size = peekPacketSize(buffer);
            if (debug.on()) {
                debug.log("dropping packet bytes from %d (%d/%d)",
                        offset, size, buffer.remaining());
            }
            buffer.position(offset + size);
        } catch (Throwable tt) {
            if (debug.on()) {
                debug.log("failed to peek packet size: " + tt, tt);
                debug.log("dropping all remaining bytes (%d)", limit - pos);
            }
            buffer.position(limit);
        }
    }

    /**
     * Returns a decoder for the given Quic version.
     * Returns {@code null} if no decoder for that version exists.
     *
     * @param quicVersion the Quic protocol version number
     * @return a decoder for the given Quic version or {@code null}
     */
    public static QuicPacketDecoder of(QuicVersion quicVersion) {
        return switch (quicVersion) {
            case QUIC_V1 -> Decoders.QUIC_V1_DECODER;
            case QUIC_V2 -> Decoders.QUIC_V2_DECODER;
            default -> throw new IllegalArgumentException("No packet decoder for Quic version " + quicVersion);
        };
    }

    /**
     * Returns a {@code QuicPacketDecoder} to decode the packet
     * starting at the specified offset in the buffer.
     * This method will attempt to read the quic version in the
     * packet in order to return the proper decoder.
     * If the version is 0, then the decoder for Quic Version 1
     * is returned.
     *
     * @param buffer A buffer containing a Quic packet
     * @param offset The offset at which the packet starts
     * @return A {@code QuicPacketDecoder} instance to decode the
     *         packet starting at the given offset.
     */
    public static QuicPacketDecoder of(ByteBuffer buffer, int offset) {
         var version = peekVersion(buffer, offset);
         final QuicVersion quicVersion = version == 0 ? QuicVersion.QUIC_V1
                 : QuicVersion.of(version).orElse(null);
         if (quicVersion == null) {
             return null;
         }
         return of(quicVersion);
    }

    /**
     * A {@code PacketReader} to read a Quic packet.
     * A {@code PacketReader} may have version specific code, and therefore
     * has an implicit pointer to a {@code QuicPacketDecoder} instance.
     * <p>
     * A {@code PacketReader} offers high level helper methods to read
     * data (such as Connection IDs or Packet Numbers) from a Quic packet.
     * It has however no or little knowledge of the actual packet structure.
     * It is driven by the {@code decode} method of the appropriate
     * {@code IncomingQuicPacket} type.
     * <p>
     * A {@code PacketReader} is stateful: it encapsulates a {@code ByteBuffer}
     * (or possibly a list of byte buffers - as a future enhancement) and
     * advances the position on the buffer it is reading.
     *
     */
    public class PacketReader {
        private static final int PACKET_NUMBER_MASK = 0x03;
        final ByteBuffer buffer;
        final int offset;
        final int initialLimit;
        final CodingContext context;
        final PacketType packetType;

        PacketReader(ByteBuffer buffer, CodingContext context) {
            this(buffer, context, peekPacketType(buffer));
        }

        PacketReader(ByteBuffer buffer, CodingContext context, PacketType packetType) {
            assert buffer.order() == ByteOrder.BIG_ENDIAN;
            int pos = buffer.position();
            int limit = buffer.limit();
            this.buffer = buffer;
            this.offset = pos;
            this.initialLimit = limit;
            this.context = context;
            this.packetType = packetType;
        }

        public int offset() {
            return offset;
        }

        public int position() {
            return buffer.position();
        }

        public int remaining() {
            return buffer.remaining();
        }

        public boolean hasRemaining() {
            return buffer.hasRemaining();
        }

        public int bytesRead() {
            return position() - offset;
        }

        public void reset() {
            buffer.position(offset);
            buffer.limit(initialLimit);
        }

        public byte headers() {
            return buffer.get(offset);
        }

        public void headers(byte headers) {
            buffer.put(offset, headers);
        }

        public PacketType packetType() {
            return packetType;
        }

        public int packetNumberLength() {
            return (headers() & PACKET_NUMBER_MASK) + 1;
        }

        public byte readHeaders() {
            return buffer.get();
        }

        public int readVersion() {
            return buffer.getInt();
        }

        public int[] readSupportedVersions() {
            // Calculate payload length and retrieve payload
            final int payloadLen = buffer.remaining();
            final int versionsCount = payloadLen >> 2;

            int[] versions = new int[versionsCount];
            for (int i=0 ; i<versionsCount; i++) {
                versions[i] = buffer.getInt();
            }
            return versions;
        }

        public long readPacketLength() {
            var packetLength = readVariableLength();
            assert packetLength >= 0 && packetLength <= VariableLengthEncoder.MAX_ENCODED_INTEGER
                    : packetLength;
            if (packetLength > remaining()) {
                throw new BufferUnderflowException();
            }
            return packetLength;
        }

        public long readTokenLength() {
            return readVariableLength();
        }

        public byte[] readToken() {
            // Get number of bytes needed to store the length of the token
            // and read it.
            long tokenLength = readTokenLength();
            assert tokenLength >= 0 && tokenLength <= Integer.MAX_VALUE : tokenLength;
            return readToken((int) tokenLength);
        }

        public byte[] readToken(int tokenLength) {
            // Check to ensure that tokenLength is within valid range
            if (tokenLength < 0 || tokenLength > buffer.remaining()) {
                throw new BufferUnderflowException();
            }
            byte[] token = tokenLength > 0 ? new byte[tokenLength] : null;
            if (tokenLength > 0) {
                buffer.get(token);
            }
            return token;
        }

        public long readVariableLength() {
            return VariableLengthEncoder.decode(buffer);
        }

        public void maskPacketNumber(int packetNumberLength, ByteBuffer mask) {
            int pos = buffer.position();
            for (int i = 0; i < packetNumberLength; i++) {
                buffer.put(pos + i, (byte)(buffer.get(pos + i) ^ mask.get()));
            }
        }

        public long readPacketNumber(int packetNumberLength) {
            var packetNumberSpace = PacketNumberSpace.of(packetType);
            var largestProcessedPN = context.largestProcessedPN(packetNumberSpace);
            return QuicPacketNumbers.decodePacketNumber(largestProcessedPN, buffer, packetNumberLength);
        }

        public long readPacketNumber() {
            return readPacketNumber(packetNumberLength());
        }

        private ByteBuffer peekPayloadSlice(int relativeOffset, int length) {
            int payloadStart = buffer.position() + relativeOffset;
            return buffer.slice(payloadStart, length);
        }

        private ByteBuffer decryptPayload(long packetNumber, int payloadLen, int keyPhase)
                throws AEADBadTagException, QuicKeyUnavailableException, QuicTransportException {
            // Calculate payload length and retrieve payload
            ByteBuffer output = buffer.slice();
            // output's position is on the first byte of encrypted data
            output.mark();
            int payloadStart = buffer.position();
            buffer.position(offset);
            buffer.limit(payloadStart + payloadLen);
            // buffer's position and limit are set to the boundaries of the encrypted packet
            context.getTLSEngine().decryptPacket(packetType.keySpace().get(), packetNumber, keyPhase,
                    buffer, payloadStart - offset, output);
            // buffer's position and limit are both at end of the packet
            output.limit(output.position());
            output.reset();
            // output's position and limit are set to the boundaries of decrypted frame data
            buffer.limit(initialLimit);
            return output;
        }

        public List<QuicFrame> parsePayloadSlice(ByteBuffer payload)
                throws QuicTransportException {
            if (!payload.hasRemaining()) {
                throw new QuicTransportException("Packet with no frames",
                        packetType().keySpace().get(), 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            try {
                List<QuicFrame> frames = new ArrayList<>();
                while (payload.hasRemaining()) {
                    int start = payload.position();
                    frames.add(QuicFrame.decode(payload));
                    int end = payload.position();
                    assert start < end : "bytes remaining at offset %s: %s"
                            .formatted(start, payload.remaining());
                }
                return frames;
            } catch (RuntimeException e) {
                throw new QuicTransportException(e.getMessage(),
                        packetType().keySpace().get(), 0, QuicTransportErrors.INTERNAL_ERROR);
            }
        }

        byte[] readRetryToken() {
            var tokenLength = buffer.limit() - buffer.position() - 16;
            assert tokenLength > 0;
            byte[] retryToken = new byte[tokenLength];
            buffer.get(retryToken);
            return retryToken;
        }

        byte[] readRetryIntegrityTag() {
            // The 16 last bytes in the datagram payload
            assert remaining() == 16;
            byte[] retryIntegrityTag = new byte[16];
            buffer.get(retryIntegrityTag);
            return retryIntegrityTag;
        }

        public void verifyRetry() throws AEADBadTagException {
            // assume the buffer position and limit are set to packet boundaries
            QuicTLSEngine tlsEngine = context.getTLSEngine();
            tlsEngine.verifyRetryPacket(quicVersion,
                    context.originalServerConnId().asReadOnlyBuffer(), buffer.asReadOnlyBuffer());
        }

        public QuicConnectionId readLongConnectionId() {
            return decodeConnectionID(buffer);
        }

        public QuicConnectionId readShortConnectionId() {
            if (!buffer.hasRemaining())
                throw new BufferUnderflowException();

            // Retrieve connection ID length from endpoint via context
            int len = context.connectionIdLength();
            if (len > buffer.remaining()) {
                throw new BufferUnderflowException();
            }
            byte[] destinationConnectionID = new byte[len];

            // Save buffer position ahead of time to check after read
            int pos = buffer.position();
            buffer.get(destinationConnectionID);
            // Ensure all bytes have been read correctly
            assert pos + len == buffer.position();

            return new PeerConnectionId(destinationConnectionID);
        }

        @Override
        public String toString() {
            return "PacketReader(offset=%s, pos=%s, remaining=%s)"
                    .formatted(offset, position(), remaining());
        }

        public void unprotectLong(long packetLength) throws QuicKeyUnavailableException {
            unprotect(packetLength, (byte) 0x0f);
        }

        public void unprotectShort() throws QuicKeyUnavailableException {
            unprotect(buffer.remaining(), (byte) 0x1f);
        }

        private void unprotect(long packetLength, byte headerMask) throws QuicKeyUnavailableException {
            QuicTLSEngine tlsEngine = context.getTLSEngine();
            int sampleSize = tlsEngine.getHeaderProtectionSampleSize(packetType.keySpace().get());
            if (packetLength > buffer.remaining() || packetLength < sampleSize + 4) {
                throw new BufferUnderflowException();
            }
            ByteBuffer sample = peekPayloadSlice(4, sampleSize);
            ByteBuffer encryptedSample = tlsEngine.computeHeaderProtectionMask(packetType.keySpace().get(), true, sample);
            byte headers = headers();
            headers ^= (byte) (encryptedSample.get() & headerMask);
            headers(headers);
            maskPacketNumber(packetNumberLength(), encryptedSample);
        }
    }


    private static final class Decoders {
        static final QuicPacketDecoder QUIC_V1_DECODER = new QuicPacketDecoder(QuicVersion.QUIC_V1);
        static final QuicPacketDecoder QUIC_V2_DECODER = new QuicPacketDecoder(QuicVersion.QUIC_V2);
    }
}
