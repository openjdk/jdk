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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.CodingContext;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTLSEngine.KeySpace;
import jdk.internal.net.http.quic.VariableLengthEncoder;

import javax.crypto.ShortBufferException;

import static jdk.internal.net.http.quic.packets.QuicPacketNumbers.computePacketNumberLength;
import static jdk.internal.net.http.quic.packets.QuicPacketNumbers.encodePacketNumber;
import static jdk.internal.net.http.quic.QuicConnectionId.MAX_CONNECTION_ID_LENGTH;

/**
 * A {@code QuicPacketEncoder} encapsulates the logic to encode a
 * quic packet. A {@code QuicPacketEncoder} is typically tied to
 * a particular version of the QUIC protocol.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9001
 *      RFC 9001: Using TLS to Secure QUIC
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public class QuicPacketEncoder {

    private static final Logger debug = Utils.getDebugLogger(() -> "QuicPacketEncoder");

    private final QuicVersion quicVersion;
    private QuicPacketEncoder(final QuicVersion quicVersion) {
        this.quicVersion = quicVersion;
    }

    /**
     * Computes the packet's header byte, which also encodes
     * the packetNumber length.
     *
     * @param packetTypeTag quic-dependent packet type encoding
     * @param pnsize  the number of bytes needed to encode the packet number
     * @return the packet's header byte
     */
    private static byte headers(byte packetTypeTag, int pnsize) {
        int pnprefix = pnsize - 1;
        assert pnprefix >= 0;
        assert pnprefix <= 3;
        return (byte)(packetTypeTag | pnprefix);
    }

    /**
     * Returns the headers tag for the given packet type.
     * Returns 0 if the packet type is NONE or unknown.
     * <p>
     * For version negotiations packet, this method returns 0x80.
     * The other 7 bits must be ignored by a client.
     * When emitting a version negotiation packet the server should
     * also set the fix bit (0x40) to 1.
     * What distinguishes a version negotiation packet from other
     * long header packet types is not the packet type found in the
     * header's byte, but the fact that a. it is a long header and
     * b. the version number in the packet (the 4 bytes following
     * the header) is 0.
     * @param packetType the packet type
     * @return the headers tag for the given packet type.
     *
     * @see <a href="https://www.rfc-editor.org/info/rfc9000">
     *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
     * @see  <a href="https://www.rfc-editor.org/info/rfc9369">
     *      RFC 9369: QUIC Version 2</a>
     */
    private byte packetHeadersTag(PacketType packetType) {
        return (byte) switch (quicVersion) {
            case QUIC_V1 -> switch (packetType) {
                case ONERTT    -> 0x40;
                case INITIAL   -> 0xC0;
                case ZERORTT   -> 0xD0;
                case HANDSHAKE -> 0xE0;
                case RETRY     -> 0xF0;
                case VERSIONS  -> 0x80; // remaining bits are ignored
                case NONE      -> 0x00;
            };
            case QUIC_V2 -> switch (packetType) {
                case ONERTT    -> 0x40;
                case INITIAL   -> 0xD0;
                case ZERORTT   -> 0xE0;
                case HANDSHAKE -> 0xF0;
                case RETRY     -> 0xC0;
                case VERSIONS  -> 0x80; // remaining bits are ignored
                case NONE      -> 0x00;
            };
        };
    }

    /**
     * Encode the OneRttPacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into
     * @param context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingOneRttPacket packet,
                                     ByteBuffer buffer,
                                     CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {
        QuicConnectionId destination = packet.destinationId();

        if (debug.on()) {
            debug.log("OneRttPacket::encodePacket(ByteBuffer(%d,%d)," +
                            " dst=%s, packet=%d, encodedPacket=%s," +
                            " payload=QuicFrames(frames: %s, bytes: %d)," +
                            " size=%d",
                    buffer.position(), buffer.limit(), destination,
                    packet.packetNumber, Arrays.toString(packet.encodedPacketNumber),
                    packet.frames, packet.payloadSize, packet.size);
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;

        int encodedLength = packet.encodedPacketNumber.length;
        assert encodedLength >= 1 && encodedLength <= 4 : encodedLength;
        int pnprefix = encodedLength - 1;

        byte headers = headers(packetHeadersTag(packet.packetType()),
                packet.encodedPacketNumber.length);
        assert (headers & 0x03) == pnprefix : "incorrect packet number prefix in headers: " + headers;

        final PacketWriter writer = new PacketWriter(buffer, context, PacketType.ONERTT);
        writer.writeHeaders(headers);
        writer.writeShortConnectionId(destination);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        assert writer.bytesWritten() == packet.size : writer.bytesWritten() - packet.size;
        writer.protectHeaderShort(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the ZeroRttPacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into.
     * @param context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingZeroRttPacket packet,
                                     ByteBuffer buffer,
                                     CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                    .formatted(quicVersion, version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();
        if (packet.size > buffer.remaining()) {
            throw new BufferOverflowException();
        }

        if (debug.on()) {
            debug.log("ZeroRttPacket::encodePacket(ByteBuffer(%d,%d)," +
                            " src=%s, dst=%s, version=%d, packet=%d, " +
                            "encodedPacket=%s, payload=QuicFrame(frames: %s, bytes: %d), size=%d",
                    buffer.position(), buffer.limit(), source, destination,
                    version, packet.packetNumber, Arrays.toString(packet.encodedPacketNumber),
                    packet.frames, packet.payloadSize, packet.size);
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;

        int encodedLength = packet.encodedPacketNumber.length;
        assert encodedLength >= 1 && encodedLength <= 4 : encodedLength;
        int pnprefix = encodedLength - 1;

        byte headers = headers(packetHeadersTag(packet.packetType()),
                packet.encodedPacketNumber.length);
        assert (headers & 0x03) == pnprefix : headers;

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.ZERORTT);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writePacketLength(packet.length);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        assert writer.bytesWritten() == packet.size : writer.bytesWritten() - packet.size;
        writer.protectHeaderLong(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the VersionNegotiationPacket into the provided
     * buffer.
     *
     * @param packet
     * @param buffer A buffer to encode the packet into.
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private static void encodePacket(OutgoingVersionNegotiationPacket packet,
                                     ByteBuffer buffer) {
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();

        if (debug.on()) {
            debug.log("VersionNegotiationPacket::encodePacket(ByteBuffer(%d,%d)," +
                            " src=%s, dst=%s, versions=%s, size=%d",
                    buffer.position(), buffer.limit(), source, destination,
                    Arrays.toString(packet.versions), packet.size);
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;

        int offset = buffer.position();
        int limit = buffer.limit();
        assert buffer.capacity() >= packet.size;
        assert limit - offset >= packet.size;

        int typeTag = 0x80;
        int rand = Encoders.RANDOM.nextInt() & 0x7F;
        int headers = typeTag | rand;
        if (debug.on()) {
            debug.log("VersionNegotiationPacket::encodePacket:" +
                            " type: 0x%02x, unused: 0x%02x, headers: 0x%02x",
                    typeTag, rand & ~0x80, headers);
        }
        assert (headers & typeTag) == typeTag : headers;
        assert (headers ^ typeTag) == rand : headers;

        // headers(1 byte), version(4 bytes)
        buffer.put((byte)headers); // 1
        putInt32(buffer, 0); // 4

        // DCID: 1 byte for length, + destination id bytes
        var dcidlen = destination.length();
        assert dcidlen <= MAX_CONNECTION_ID_LENGTH && dcidlen >= 0 : dcidlen;
        buffer.put((byte)dcidlen); // 1
        buffer.put(destination.asReadOnlyBuffer());
        assert buffer.position() == offset + 6 + dcidlen : buffer.position();

        // SCID: 1 byte for length, + source id bytes
        var scidlen = source.length();
        assert scidlen <= MAX_CONNECTION_ID_LENGTH && scidlen >= 0 : scidlen;
        buffer.put((byte) scidlen);
        buffer.put(source.asReadOnlyBuffer());
        assert buffer.position() == offset + 7 + dcidlen + scidlen : buffer.position();

        // Put payload (= supported versions)
        int versionsStart = buffer.position();
        for (int i = 0; i < packet.versions.length; i++) {
            putInt32(buffer, packet.versions[i]);
        }
        int versionsEnd = buffer.position();
        if (debug.on()) {
            debug.log("VersionNegotiationPacket::encodePacket:" +
                            " encoded %d bytes", offset - versionsEnd);
        }

        assert versionsEnd - offset == packet.size;
        assert versionsEnd - versionsStart == packet.versions.length << 2;
    }

    /**
     * Encode the HandshakePacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into.
     * @param context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingHandshakePacket packet,
                                     ByteBuffer buffer,
                                     CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                    .formatted(quicVersion, version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();
        if (packet.size > buffer.remaining()) {
            throw new BufferOverflowException();
        }

        if (debug.on()) {
            debug.log("HandshakePacket::encodePacket(ByteBuffer(%d,%d)," +
                            " src=%s, dst=%s, version=%d, packet=%d, " +
                            "encodedPacket=%s, payload=QuicFrame(frames: %s, bytes: %d)," +
                            " size=%d",
                    buffer.position(), buffer.limit(), source, destination,
                    version, packet.packetNumber, Arrays.toString(packet.encodedPacketNumber),
                    packet.frames, packet.payloadSize, packet.size);
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;

        int encodedLength = packet.encodedPacketNumber.length;
        assert encodedLength >= 1 && encodedLength <= 4 : encodedLength;
        int pnprefix = encodedLength - 1;

        byte headers = headers(packetHeadersTag(packet.packetType()),
                packet.encodedPacketNumber.length);
        assert (headers & 0x03) == pnprefix : headers;

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.HANDSHAKE);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writePacketLength(packet.length);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        assert writer.bytesWritten() == packet.size : writer.bytesWritten() - packet.size;
        writer.protectHeaderLong(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the InitialPacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into.
     * @param context coding context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingInitialPacket packet,
                                     ByteBuffer buffer,
                                     CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                    .formatted(quicVersion, version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();
        if (packet.size > buffer.remaining()) {
            throw new BufferOverflowException();
        }

        if (debug.on()) {
            debug.log("InitialPacket::encodePacket(ByteBuffer(%d,%d)," +
                    " src=%s, dst=%s, version=%d, packet=%d, " +
                    "encodedPacket=%s, token=%s, " +
                    "payload=QuicFrame(frames: %s, bytes: %d), size=%d",
                    buffer.position(), buffer.limit(), source, destination,
                    version, packet.packetNumber, Arrays.toString(packet.encodedPacketNumber),
                    packet.token == null ? null : "byte[%s]".formatted(packet.token.length),
                    packet.frames, packet.payloadSize, packet.size);
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;

        int encodedLength = packet.encodedPacketNumber.length;
        assert encodedLength >= 1 && encodedLength <= 4 : encodedLength;
        int pnprefix = encodedLength - 1;

        byte headers = headers(packetHeadersTag(packet.packetType()),
                packet.encodedPacketNumber.length);
        assert (headers & 0x03) == pnprefix : headers;

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.INITIAL);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writeToken(packet.token);
        writer.writePacketLength(packet.length);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        assert writer.bytesWritten() == packet.size : writer.bytesWritten() - packet.size;
        writer.protectHeaderLong(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the RetryPacket into the provided buffer.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into.
     * @param context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingRetryPacket packet,
                                     ByteBuffer buffer,
                                     CodingContext context) {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                    .formatted(quicVersion, version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();

        if (debug.on()) {
            debug.log("RetryPacket::encodePacket(ByteBuffer(%d,%d)," +
                            " src=%s, dst=%s, version=%d, retryToken=%d," +
                            " size=%d",
                    buffer.position(), buffer.limit(), source, destination,
                    version, packet.retryToken.length, packet.size);
        }
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        assert packet.retryToken.length > 0;
        assert buffer.remaining() >= packet.size;

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.RETRY);

        byte headers = packetHeadersTag(packet.packetType());
        headers |= (byte)Encoders.RANDOM.nextInt(0x10);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writeRetryToken(packet.retryToken);
        assert writer.remaining() >= 16; // 128 bits
        writer.signRetry(version);

        assert writer.bytesWritten() == packet.size : writer.bytesWritten() - packet.size;
    }

    public abstract static class OutgoingQuicPacket implements QuicPacket {
        private final QuicConnectionId destinationId;

        protected OutgoingQuicPacket(QuicConnectionId destinationId) {
            this.destinationId = destinationId;
        }

        @Override
        public final QuicConnectionId destinationId() { return destinationId; }

        @Override
        public String toString() {

            return this.getClass().getSimpleName() + "[pn=" + this.packetNumber()
                    + ", frames=" + frames() + "]";
        }
    }

    private abstract static class OutgoingShortHeaderPacket
            extends OutgoingQuicPacket implements ShortHeaderPacket {

        OutgoingShortHeaderPacket(QuicConnectionId destinationId) {
            super(destinationId);
        }
    }

    private abstract static class OutgoingLongHeaderPacket
            extends OutgoingQuicPacket implements LongHeaderPacket {

        private final QuicConnectionId sourceId;
        private final int version;
        OutgoingLongHeaderPacket(QuicConnectionId sourceId,
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

    private static final class OutgoingRetryPacket
            extends OutgoingLongHeaderPacket implements RetryPacket {

        final int size;
        final byte[] retryToken;

        OutgoingRetryPacket(QuicConnectionId sourceId,
                            QuicConnectionId destinationId,
                            int version,
                            byte[] retryToken) {
            super(sourceId, destinationId, version);
            this.retryToken = retryToken;
            this.size = computeSize(retryToken.length);
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the end of the retry integrity tag. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @return the total packet size.
         */
        private int computeSize(int tokenLength) {
            assert tokenLength > 0;

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte),
            //   retryTokenIntegrity(128 bits) => 7 + 16 = 23 bytes
            int size = Math.addExact(23, tokenLength);
            size = Math.addExact(size, sourceId().length());
            size = Math.addExact(size, destinationId().length());

            return size;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public byte[] retryToken() {
            return retryToken;
        }
    }

    private static final class OutgoingHandshakePacket
            extends OutgoingLongHeaderPacket implements HandshakePacket {

        final long packetNumber;
        final int length;
        final int size;
        final byte[] encodedPacketNumber;
        final List<QuicFrame> frames;
        final int payloadSize;
        private final boolean containsConnClose;
        private int tagSize;

        OutgoingHandshakePacket(QuicConnectionId sourceId,
                                QuicConnectionId destinationId,
                                int version,
                                long packetNumber,
                                byte[] encodedPacketNumber,
                                List<? extends QuicFrame> frames, int tagSize) {
            super(sourceId, destinationId, version);
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            boolean hasConnClose = false;
            for (var f : this.frames) {
                if (f instanceof ConnectionCloseFrame) {
                    hasConnClose = true;
                    break;
                }
            }
            this.containsConnClose = hasConnClose;
            this.payloadSize = frames.stream().mapToInt(QuicFrame::size).reduce(0, Math::addExact);
            this.tagSize = tagSize;
            this.length = computeLength(payloadSize, encodedPacketNumber.length, tagSize);
            this.size = computeSize(length);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

        @Override
        public boolean containsConnectionClose() {
            return this.containsConnClose;
        }

        /**
         * Computes the value for the packet length field.
         * This is the number of bytes needed to encode the packetNumber
         * and the payload.
         *
         * @param payloadSize The payload size
         * @param pnsize The number of bytes needed to encode the packet number
         * @param tagSize The size of the authentication tag added during encryption
         * @return the value for the packet length field.
         */
        private int computeLength(int payloadSize, int pnsize, int tagSize) {
            assert payloadSize >= 0;
            assert pnsize > 0 && pnsize <= 4 : pnsize;

            return Math.addExact(Math.addExact(pnsize, payloadSize), tagSize);
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param length The value of the length header
         *
         * @return the total packet size.
         */
        private int computeSize(int length) {
            assert length >= 0;

            // how many bytes are needed to encode the packet length
            //   the packet length is the number of bytes needed to encode
            //   the remainder of the packet: packet number + payload bytes
            int lnsize = VariableLengthEncoder.getEncodedSize(length);

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte), => 7 bytes
            int size = Math.addExact(7, sourceId().length());
            size = Math.addExact(size, destinationId().length());

            size = Math.addExact(size, lnsize);
            size = Math.addExact(size, length);
            return size;
        }

        @Override
        public List<QuicFrame> frames() { return frames; }

    }

    private static final class OutgoingZeroRttPacket
            extends OutgoingLongHeaderPacket implements ZeroRttPacket {

        final long packetNumber;
        final int length;
        final int size;
        final byte[] encodedPacketNumber;
        final List<QuicFrame> frames;
        private int tagSize;
        final int payloadSize;

        OutgoingZeroRttPacket(QuicConnectionId sourceId,
                              QuicConnectionId destinationId,
                              int version,
                              long packetNumber,
                              byte[] encodedPacketNumber,
                              List<? extends QuicFrame> frames, int tagSize) {
            super(sourceId, destinationId, version);
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            this.tagSize = tagSize;
            this.payloadSize = this.frames.stream().mapToInt(QuicFrame::size)
                    .reduce(0, Math::addExact);
            this.length = computeLength(payloadSize, encodedPacketNumber.length, tagSize);
            this.size = computeSize(length);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() {
            return size;
        }

        /**
         * Computes the value for the packet length field.
         * This is the number of bytes needed to encode the packetNumber
         * and the payload.
         *
         * @param payloadSize The payload size
         * @param pnsize The number of bytes needed to encode the packet number
         * @param tagSize The size of the authentication tag added during encryption
         * @return the value for the packet length field.
         */
        private int computeLength(int payloadSize, int pnsize, int tagSize) {
            assert payloadSize >= 0;
            assert pnsize > 0 && pnsize <= 4 : pnsize;

            return Math.addExact(Math.addExact(pnsize, payloadSize), tagSize);
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param length The value of the length header
         *
         * @return the total packet size.
         */
        private int computeSize(int length) {
            assert length >= 0;

            // how many bytes are needed to encode the packet length
            //   the packet length is the number of bytes needed to encode
            //   the remainder of the packet: packet number + payload bytes
            int lnsize = VariableLengthEncoder.getEncodedSize(length);

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte), => 7 bytes
            int size = Math.addExact(7, sourceId().length());
            size = Math.addExact(size, destinationId().length());

            size = Math.addExact(size, lnsize);
            size = Math.addExact(size, length);
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

    }

    private static final class OutgoingOneRttPacket
            extends OutgoingShortHeaderPacket implements OneRttPacket {

        final long packetNumber;
        final int size;
        final byte[] encodedPacketNumber;
        final List<QuicFrame> frames;
        private int tagSize;
        final int payloadSize;
        private final boolean containsConnClose;

        OutgoingOneRttPacket(QuicConnectionId destinationId,
                             long packetNumber,
                             byte[] encodedPacketNumber,
                             List<? extends QuicFrame> frames, int tagSize) {
            super(destinationId);
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            boolean hasConnClose = false;
            for (var f : this.frames) {
                if (f instanceof ConnectionCloseFrame) {
                    hasConnClose = true;
                    break;
                }
            }
            this.containsConnClose = hasConnClose;
            this.tagSize = tagSize;
            this.payloadSize = this.frames.stream().mapToInt(QuicFrame::size)
                    .reduce(0, Math::addExact);
            this.size = computeSize(payloadSize, encodedPacketNumber.length, tagSize);
        }

        public long packetNumber() {
            return packetNumber;
        }

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean containsConnectionClose() {
            return this.containsConnClose;
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param payloadSize The size of the packet's payload
         * @param pnsize The number of bytes needed to encode the packet number
         * @param tagSize The size of the authentication tag
         * @return the total packet size.
         */
        private int computeSize(int payloadSize, int pnsize, int tagSize) {
            assert payloadSize >= 0;
            assert pnsize > 0 && pnsize <= 4 : pnsize;

            // Fixed size bits:
            //   headers(1 byte)
            int size = Math.addExact(1, destinationId().length());

            size = Math.addExact(size, payloadSize);
            size = Math.addExact(size, pnsize);
            size = Math.addExact(size, tagSize);
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

    }

    private static final class OutgoingInitialPacket
            extends OutgoingLongHeaderPacket implements InitialPacket {

        final byte[] token;
        final long packetNumber;
        final int length;
        final int size;
        final byte[] encodedPacketNumber;
        final List<QuicFrame> frames;
        private int tagSize;
        final int payloadSize;
        private final boolean containsConnClose;

        private record InitialPacketVariableComponents(int length, byte[] token, QuicConnectionId sourceId,
                                                       QuicConnectionId destinationId) {

        }

        public OutgoingInitialPacket(QuicConnectionId sourceId,
                                     QuicConnectionId destinationId,
                                     int version,
                                     byte[] token,
                                     long packetNumber,
                                     byte[] encodedPacketNumber,
                                     List<QuicFrame> frames, int tagSize) {
            super(sourceId, destinationId, version);
            this.token = token;
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            boolean hasConnClose = false;
            for (var f : this.frames) {
                if (f instanceof ConnectionCloseFrame) {
                    hasConnClose = true;
                    break;
                }
            }
            this.containsConnClose = hasConnClose;
            this.tagSize = tagSize;
            this.payloadSize = this.frames.stream()
                    .mapToInt(QuicFrame::size)
                    .reduce(0, Math::addExact);
            this.length = computeLength(payloadSize, encodedPacketNumber.length, tagSize);
            this.size = computePacketSize(new InitialPacketVariableComponents(length, token, sourceId,
                    destinationId));
        }

        @Override
        public int tokenLength() { return token == null ? 0 : token.length; }

        @Override
        public byte[] token() { return token; }

        @Override
        public int length() { return length; }

        @Override
        public long packetNumber() { return packetNumber; }

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() { return size; }

        @Override
        public boolean containsConnectionClose() {
            return this.containsConnClose;
        }

        /**
         * Computes the value for the packet length field.
         * This is the number of bytes needed to encode the packetNumber
         * and the payload.
         *
         * @param payloadSize The payload size
         * @param pnsize The number of bytes needed to encode the packet number
         * @param tagSize The size of the authentication tag added during encryption
         * @return the value for the packet length field.
         */
        private static int computeLength(int payloadSize, int pnsize, int tagSize) {
            assert payloadSize >= 0;
            assert pnsize > 0 && pnsize <= 4 : pnsize;

            return Math.addExact(Math.addExact(pnsize, payloadSize), tagSize);
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param variableComponents The variable components of the packet
         *
         * @return the total packet size.
         */
        private static int computePacketSize(InitialPacketVariableComponents variableComponents) {
            assert variableComponents.length >= 0;

            // how many bytes are needed to encode the length of the token
            final byte[] token = variableComponents.token;
            int tkLenSpecifierSize = token == null || token.length == 0
                    ? 1 : VariableLengthEncoder.getEncodedSize(token.length);

            // how many bytes are needed to encode the packet length
            //   the packet length is the number of bytes needed to encode
            //   the remainder of the packet: packet number + payload bytes
            int lnsize = VariableLengthEncoder.getEncodedSize(variableComponents.length);

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID length specifier(1 byte),
            //   SCID length specifier(1 byte), => 7 bytes
            int size = Math.addExact(7, variableComponents.sourceId.length());
            size = Math.addExact(size, variableComponents.destinationId.length());
            size = Math.addExact(size, tkLenSpecifierSize);
            if (token != null) {
                size = Math.addExact(size, token.length);
            }
            size = Math.addExact(size, lnsize);
            size = Math.addExact(size, variableComponents.length);
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

    }

    private static final class OutgoingVersionNegotiationPacket
            extends OutgoingLongHeaderPacket
            implements VersionNegotiationPacket {

        final int[] versions;
        final int size;
        final int payloadSize;

        public OutgoingVersionNegotiationPacket(QuicConnectionId sourceId,
                                     QuicConnectionId destinationId,
                                     int[] versions) {
            super(sourceId, destinationId, 0);
            this.versions = versions.clone();
            this.payloadSize = versions.length << 2;
            this.size = computeSize(payloadSize);
        }

        @Override
        public int[] supportedVersions() {
            return versions.clone();
        }

        @Override
        public int size() { return size; }

        @Override
        public int payloadSize() { return payloadSize; }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param payloadSize The size of the packet's payload
         * @return the total packet size.
         */
        private int computeSize(int payloadSize) {
            assert payloadSize > 0;
            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte), => 7 bytes
            int size = Math.addExact(7, payloadSize);
            size = Math.addExact(size, sourceId().length());
            size = Math.addExact(size, destinationId().length());
            return size;
        }

    }

    /**
     * Create a new unencrypted InitialPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source       The source connection ID
     * @param destination  The destination connection ID
     * @param token        The token field (may be null if no token)
     * @param packetNumber The packet number
     * @param ackedPacketNumber The largest acknowledged packet number
     * @param frames       The initial packet payload
     *
     * @param codingContext
     * @return the new initial packet
     */
    public OutgoingQuicPacket newInitialPacket(QuicConnectionId source,
                                               QuicConnectionId destination,
                                               byte[] token,
                                               long packetNumber,
                                               long ackedPacketNumber,
                                               List<QuicFrame> frames,
                                               CodingContext codingContext) {
        if (debug.on()) {
            debug.log("newInitialPacket: fullPN=%d ackedPN=%d",
                    packetNumber, ackedPacketNumber);
        }
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, ackedPacketNumber);
        QuicTLSEngine tlsEngine = codingContext.getTLSEngine();
        int tagSize = tlsEngine.getAuthTagSize();
        // https://www.rfc-editor.org/rfc/rfc9000#section-14.1
        // A client MUST expand the payload of all UDP datagrams carrying Initial packets
        // to at least the smallest allowed maximum datagram size of 1200 bytes
        // by adding PADDING frames to the Initial packet or by coalescing the Initial packet

        // first compute the packet size
        final int originalPayloadSize = frames.stream()
                .mapToInt(QuicFrame::size)
                .reduce(0, Math::addExact);
        final int originalLength = OutgoingInitialPacket.computeLength(originalPayloadSize,
                encodedPacketNumber.length, tagSize);
        final int originalPacketSize = OutgoingInitialPacket.computePacketSize(
                new OutgoingInitialPacket.InitialPacketVariableComponents(originalLength, token,
                        source, destination));
        if (originalPacketSize >= 1200) {
            return new OutgoingInitialPacket(source, destination, this.quicVersion.versionNumber(),
                    token, packetNumber, encodedPacketNumber, frames, tagSize);
        } else {
            // add padding
            int numPaddingBytesNeeded = 1200 - originalPacketSize;
            if (originalLength < 64 && originalLength + numPaddingBytesNeeded > 64) {
                // if originalLength + numPaddingBytesNeeded == 64, will send
                //  1201 bytes
                numPaddingBytesNeeded--;
            }
            final List<QuicFrame> newFrames = new ArrayList<>();
            for (QuicFrame frame : frames) {
                if (frame instanceof PaddingFrame) {
                    // a padding frame already exists, instead of including this and the new padding
                    // frame in the new frames, we just include 1 single padding frame whose
                    // combined size will be the sum of all existing padding frames and the
                    // additional padding bytes needed
                    numPaddingBytesNeeded += frame.size();
                    continue;
                }
                // non-padding frame, include it in the new frames
                newFrames.add(frame);
            }
            // add the padding frame as the first frame
            newFrames.add(0, new PaddingFrame(numPaddingBytesNeeded));
            return new OutgoingInitialPacket(
                    source, destination, this.quicVersion.versionNumber(),
                    token, packetNumber, encodedPacketNumber, newFrames, tagSize);
        }
    }

    /**
     * Create a new unencrypted VersionNegotiationPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source       The source connection ID
     * @param destination  The destination connection ID
     * @param versions     The supported quic versions
     * @return the new initial packet
     */
    public static OutgoingQuicPacket newVersionNegotiationPacket(QuicConnectionId source,
                                                                 QuicConnectionId destination,
                                                                 int[] versions) {
        return new OutgoingVersionNegotiationPacket(source, destination, versions);
    }

    /**
     * Create a new unencrypted RetryPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source                The source connection ID
     * @param destination           The destination connection ID
     * @param retryToken            The retry token
     * @return the new retry packet
     */
    public OutgoingQuicPacket newRetryPacket(QuicConnectionId source,
                                             QuicConnectionId destination,
                                             byte[] retryToken) {
        return new OutgoingRetryPacket(
                source, destination, this.quicVersion.versionNumber(), retryToken);
    }

    /**
     * Create a new unencrypted ZeroRttPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source       The source connection ID
     * @param destination  The destination connection ID
     * @param packetNumber The packet number
     * @param ackedPacketNumber The largest acknowledged packet number
     * @param frames       The zero RTT packet payload
     * @param codingContext
     * @return the new zero RTT packet
     */
    public OutgoingQuicPacket newZeroRttPacket(QuicConnectionId source,
                                               QuicConnectionId destination,
                                               long packetNumber,
                                               long ackedPacketNumber,
                                               List<? extends QuicFrame> frames,
                                               CodingContext codingContext) {
        if (debug.on()) {
            debug.log("newZeroRttPacket: fullPN=%d ackedPN=%d",
                    packetNumber, ackedPacketNumber);
        }
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, ackedPacketNumber);
        QuicTLSEngine tlsEngine = codingContext.getTLSEngine();
        int tagSize = tlsEngine.getAuthTagSize();
        int protectionSampleSize = tlsEngine.getHeaderProtectionSampleSize(KeySpace.ZERO_RTT);
        int minLength = 4 + protectionSampleSize - encodedPacketNumber.length - tagSize;

        return new OutgoingZeroRttPacket(
                source, destination, this.quicVersion.versionNumber(), packetNumber,
                encodedPacketNumber, padFrames(frames, minLength), tagSize);
    }

    /**
     * Create a new unencrypted HandshakePacket to be transmitted over the wire
     * after encryption.
     *
     * @param source       The source connection ID
     * @param destination  The destination connection ID
     * @param packetNumber The packet number
     * @param frames       The handshake packet payload
     * @param codingContext
     * @return the new handshake packet
     */
    public OutgoingQuicPacket newHandshakePacket(QuicConnectionId source,
                                                 QuicConnectionId destination,
                                                 long packetNumber,
                                                 long largestAckedPN,
                                                 List<QuicFrame> frames, CodingContext codingContext) {
        if (debug.on()) {
            debug.log("newHandshakePacket: fullPN=%d ackedPN=%d",
                    packetNumber, largestAckedPN);
        }
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, largestAckedPN);
        QuicTLSEngine tlsEngine = codingContext.getTLSEngine();
        int tagSize = tlsEngine.getAuthTagSize();
        int protectionSampleSize = tlsEngine.getHeaderProtectionSampleSize(KeySpace.HANDSHAKE);
        int minLength = 4 + protectionSampleSize - encodedPacketNumber.length - tagSize;

        return new OutgoingHandshakePacket(
                source, destination, this.quicVersion.versionNumber(),
                packetNumber, encodedPacketNumber, padFrames(frames, minLength), tagSize);
    }

    /**
     * Create a new unencrypted OneRttPacket to be transmitted over the wire
     * after encryption.
     *
     * @param destination  The destination connection ID
     * @param packetNumber The packet number
     * @param ackedPacketNumber The largest acknowledged packet number
     * @param frames       The one RTT packet payload
     * @param codingContext
     * @return the new one RTT packet
     */
    public OneRttPacket newOneRttPacket(QuicConnectionId destination,
                                              long packetNumber,
                                              long ackedPacketNumber,
                                              List<? extends QuicFrame> frames,
                                              CodingContext codingContext) {
        if (debug.on()) {
            debug.log("newOneRttPacket: fullPN=%d ackedPN=%d",
                    packetNumber, ackedPacketNumber);
        }
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, ackedPacketNumber);
        QuicTLSEngine tlsEngine = codingContext.getTLSEngine();
        int tagSize = tlsEngine.getAuthTagSize();
        int protectionSampleSize = tlsEngine.getHeaderProtectionSampleSize(KeySpace.ONE_RTT);
        // packets should be at least 22 bytes longer than the local connection id length.
        // we ensure that by padding the frames to the necessary size
        int minPayloadSize = codingContext.minShortPacketPayloadSize(destination.length());
        assert protectionSampleSize == tagSize;
        int minLength = Math.max(5, minPayloadSize) - encodedPacketNumber.length;
        return new OutgoingOneRttPacket(
                destination, packetNumber,
                encodedPacketNumber, padFrames(frames, minLength), tagSize);
    }

    /**
     * Creates a packet in the given keyspace for the purpose of sending
     * a CONNECTION_CLOSE, or a generic list of frames.
     * The {@code initialToken} parameter is ignored if the key
     * space is not INITIAL.
     *
     * @param keySpace       the sending key space
     * @param packetSpace    the packet space
     * @param sourceId       the source connection id
     * @param destinationId  the destination connection id
     * @param initialToken   the initial token for INITIAL packets
     * @param frames         the list of frames
     * @param codingContext  the coding context
     * @return a packet in the given key space
     * @throws IllegalArgumentException if the packet number space is
     *         not one of INITIAL, HANDSHAKE, or APPLICATION
     */
    public OutgoingQuicPacket newOutgoingPacket(
            KeySpace keySpace,
            PacketSpace packetSpace,
            QuicConnectionId sourceId,
            QuicConnectionId destinationId,
            byte[] initialToken,
            List<QuicFrame> frames, CodingContext codingContext) {
        long largestAckedPN = packetSpace.getLargestPeerAckedPN();
        return switch (packetSpace.packetNumberSpace()) {
            case APPLICATION -> {
                long newPacketNumber = packetSpace.allocateNextPN();
                if (keySpace == KeySpace.ZERO_RTT) {
                    assert !frames.stream().anyMatch(f -> !f.isValidIn(PacketType.ZERORTT))
                            : "%s contains frames not valid in %s"
                            .formatted(frames, keySpace);
                    yield newZeroRttPacket(sourceId,
                            destinationId,
                            newPacketNumber,
                            largestAckedPN,
                            frames,
                            codingContext);
                } else {
                    assert keySpace == KeySpace.ONE_RTT;
                    assert !frames.stream().anyMatch(f -> !f.isValidIn(PacketType.ONERTT))
                            : "%s contains frames not valid in %s"
                            .formatted(frames, keySpace);
                    final OneRttPacket oneRttPacket = newOneRttPacket(destinationId,
                            newPacketNumber,
                            largestAckedPN,
                            frames,
                            codingContext);
                    assert oneRttPacket instanceof OutgoingOneRttPacket :
                            "unexpected 1-RTT packet type: " + oneRttPacket.getClass();
                    yield (OutgoingQuicPacket) oneRttPacket;
                }
            }
            case HANDSHAKE -> {
                assert keySpace == KeySpace.HANDSHAKE;
                assert !frames.stream().anyMatch(f -> !f.isValidIn(PacketType.HANDSHAKE))
                        : "%s contains frames not valid in %s"
                        .formatted(frames, keySpace);
                long newPacketNumber = packetSpace.allocateNextPN();
                yield newHandshakePacket(sourceId, destinationId,
                        newPacketNumber, largestAckedPN,
                        frames, codingContext);
            }
            case INITIAL -> {
                assert keySpace == KeySpace.INITIAL;
                assert !frames.stream().anyMatch(f -> !f.isValidIn(PacketType.INITIAL))
                        : "%s contains frames not valid in %s"
                        .formatted(frames, keySpace);
                long newPacketNumber = packetSpace.allocateNextPN();
                yield newInitialPacket(sourceId, destinationId,
                        initialToken, newPacketNumber,
                        largestAckedPN,
                        frames, codingContext);
            }
            case NONE -> {
                throw new IllegalArgumentException("packetSpace: %s, keySpace: %s"
                        .formatted(packetSpace.packetNumberSpace(), keySpace));
            }
        };
    }

    /**
     * Encodes the given QuicPacket.
     *
     * @param packet the packet to encode
     * @param buffer the byte buffer to write the packet into
     * @param context context for encoding
     * @throws IllegalArgumentException if the packet is not an OutgoingQuicPacket,
     *          or if the packet version does not match the encoder version
     * @throws BufferOverflowException if the buffer is not large enough
     * @throws QuicKeyUnavailableException if the packet could not be encrypted
     *          because the required encryption key is not available
     * @throws QuicTransportException if encrypting the packet resulted
     *          in an error that requires closing the connection
     */
    public void encode(QuicPacket packet, ByteBuffer buffer, CodingContext context)
            throws QuicKeyUnavailableException, QuicTransportException {
        switch (packet) {
            case OutgoingOneRttPacket p -> encodePacket(p, buffer, context);
            case OutgoingZeroRttPacket p -> encodePacket(p, buffer, context);
            case OutgoingVersionNegotiationPacket p -> encodePacket(p, buffer);
            case OutgoingHandshakePacket p -> encodePacket(p, buffer, context);
            case OutgoingInitialPacket p -> encodePacket(p, buffer, context);
            case OutgoingRetryPacket p -> encodePacket(p, buffer, context);
            default -> throw new IllegalArgumentException("packet is not an outgoing packet: "
                    + packet.getClass());
        }
    }

    /**
     * Compute the max size of the usable payload of an initial
     * packet, given the max size of the datagram.
     * <pre>
     * Initial Packet {
     *     Header (1 byte),
     *     Version (4 bytes),
     *     Destination Connection ID Length (1 byte),
     *     Destination Connection ID (0..20 bytes),
     *     Source Connection ID Length (1 byte),
     *     Source Connection ID (0..20 bytes),
     *     Token Length (variable int),
     *     Token (..),
     *     Length (variable int),
     *     Packet Number (1..4 bytes),
     *     Packet Payload (1 to ... bytes),
     * }
     * </pre>
     *
     * @param codingContext   the coding context, used to compute the
     *                        encoded packet number
     * @param pnsize          packet number length
     * @param tokenLength     the length of the token (or {@code 0})
     * @param scidLength      the length of the source connection id
     * @param dstidLength     the length of the destination connection id
     * @param maxDatagramSize the desired total maximum size
     *                        of the packet after encryption
     * @return the maximum size of the payload that can be fit into this
     * initial packet
     */
    public static int computeMaxInitialPayloadSize(CodingContext codingContext,
                                                   int pnsize,
                                                   int tokenLength,
                                                   int scidLength,
                                                   int dstidLength,
                                                   int maxDatagramSize) {
        // header=1, version=4, len(scidlen)+len(dstidlen)=2
        int overhead = 1 + 4 + 2 + scidLength + dstidLength + tokenLength +
                VariableLengthEncoder.getEncodedSize(tokenLength);
        // encryption tag, included in the payload, but not usable for frames
        int tagSize = codingContext.getTLSEngine().getAuthTagSize();
        int length = maxDatagramSize - overhead - 1; // at least 1 byte for length encoding
        if (length <= 0) return 0;
        int lenbefore = VariableLengthEncoder.getEncodedSize(length);
        length = length - lenbefore + 1; // discount length encoding
        // int lenafter = VariableLengthEncoder.getEncodedSize(length); // check
        // assert lenafter == lenbefore : "%s -> %s (before:%s, after:%s)"
        //        .formatted(maxDatagramSize - overhead -1, length, lenbefore, lenafter);
        if (length <= 0) return 0;
        int available = length - pnsize - tagSize;
        if (available < 0) return 0;
        return available;
    }

    /**
     * Compute the max size of the usable payload of a handshake
     * packet, given the max size of the datagram.
     * <pre>
     * Initial Packet {
     *     Header (1 byte),
     *     Version (4 bytes),
     *     Destination Connection ID Length (1 byte),
     *     Destination Connection ID (0..20 bytes),
     *     Source Connection ID Length (1 byte),
     *     Source Connection ID (0..20 bytes),
     *     Length (variable int),
     *     Packet Number (1..4 bytes),
     *     Packet Payload (1 to ... bytes),
     * }
     * </pre>
     * @param codingContext the coding context, used to compute the
     *                      encoded packet number
     * @param packetNumber the full packet number
     * @param scidLength   the length of the source connection id
     * @param dstidLength  the length of the destination connection id
     * @param maxDatagramSize the desired total maximum size
     *                                       of the packet after encryption
     * @return the maximum size of the payload that can be fit into this
     * initial packet
     */
    public static int computeMaxHandshakePayloadSize(CodingContext codingContext,
                                            long packetNumber,
                                            int scidLength,
                                            int dstidLength,
                                            int maxDatagramSize) {
        // header=1, version=4, len(scidlen)+len(dstidlen)=2
        int overhead = 1 + 4 + 2 + scidLength + dstidLength;
        int pnsize = computePacketNumberLength(packetNumber,
                codingContext.largestAckedPN(PacketNumberSpace.HANDSHAKE));
        // encryption tag, included in the payload, but not usable for frames
        int tagSize = codingContext.getTLSEngine().getAuthTagSize();
        int length = maxDatagramSize - overhead -1; // at least 1 byte for length encoding
        if (length < 0) return 0;
        int lenbefore = VariableLengthEncoder.getEncodedSize(length);
        length = length - lenbefore + 1; // discount length encoding
        int available = length - pnsize - tagSize;
        return available;
    }

    /**
     * Computes the maximum usable payload that can be carried on in a
     * {@link OneRttPacket} given the max datagram size before
     * encryption.
     * @param codingContext  the coding context
     * @param packetNumber   the packet number
     * @param dstidLength    the peer connection id length
     * @param maxDatagramSizeBeforeEncryption the maximum size of the datagram
     * @return the maximum payload that can be carried on in a
     *      {@link OneRttPacket} given the max datagram size before
     *      encryption
     */
    public static int computeMaxOneRTTPayloadSize(final CodingContext codingContext,
                                                  final long packetNumber,
                                                  final int dstidLength,
                                                  final int maxDatagramSizeBeforeEncryption,
                                                  final long largestPeerAckedPN) {
        // header=1
        final int overhead = 1 + dstidLength;
        // always reserve four bytes for packet number to avoid issues with packet
        // sizes when retransmitting. This is a hack, but it avoids having to
        // repack StreamFrames.
        final int pnsize = 4; //computePacketNumberLength(packetNumber, largestPeerAckedPN);
        // encryption tag, included in the payload, but not usable for frames
        final int tagSize = codingContext.getTLSEngine().getAuthTagSize();
        final int available = maxDatagramSizeBeforeEncryption - overhead - pnsize - tagSize;
        if (available < 0) return 0;
        return available;
    }

    private static ByteBuffer putInt32(ByteBuffer buffer, int value) {
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        return buffer.putInt(value);
    }


    /**
     * A {@code PacketWriter} to write a Quic packet.
     * <p>
     * A {@code PacketWriter} offers high level helper methods to write
     * data (such as Connection IDs or Packet Numbers) from a Quic packet.
     * It has however no or little knowledge of the actual packet structure.
     * It is driven by the {@code encode} method of the appropriate
     * {@code OutgoingQuicPacket} type.
     * <p>
     * A {@code PacketWriter} is stateful: it encapsulates a {@code ByteBuffer}
     * (or possibly a list of byte buffers - as a future enhancement) and
     * advances the position on the buffer it is writing.
     *
     */
    public static class PacketWriter {
        final ByteBuffer buffer;
        final int offset;
        final int initialLimit;
        final CodingContext context;
        final PacketType packetType;

        PacketWriter(ByteBuffer buffer, CodingContext context, PacketType packetType) {
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

        public int bytesWritten() {
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

        public void writeHeaders(byte headers) {
            buffer.put(headers);
        }

        public void writeVersion(int version) {
            buffer.putInt(version);
        }

        public void writeSupportedVersions(int[] versions) {
            for (int i=0 ; i<versions.length; i++) {
                buffer.putInt(versions[i]);
            }
        }

        public void writePacketLength(long packetLength) {
            assert packetLength >= 0 && packetLength <= VariableLengthEncoder.MAX_ENCODED_INTEGER
                    : packetLength;
            writeVariableLength(packetLength);
        }

        private void writeTokenLength(long tokenLength) {
            writeVariableLength(tokenLength);
        }

        public void writeToken(byte[] token) {
            if (token == null) {
                buffer.put((byte)0);
            } else {
                writeTokenLength(token.length);
                buffer.put(token);
            }
        }

        public void writeVariableLength(long value) {
            VariableLengthEncoder.encode(buffer, value);
        }

        private void maskPacketNumber(int packetNumberStart, int packetNumberLength, ByteBuffer mask) {
            for (int i = 0; i < packetNumberLength; i++) {
                buffer.put(packetNumberStart + i, (byte)(buffer.get(packetNumberStart + i) ^ mask.get()));
            }
        }

        public void writeEncodedPacketNumber(byte[] packetNumber) {
            buffer.put(packetNumber);
        }

        public void encryptPayload(final long packetNumber, final int payloadstart)
                throws QuicTransportException, QuicKeyUnavailableException {
            final int payloadend = buffer.position();
            buffer.position(payloadstart); // position the output buffer
            final int payloadLength = payloadend - payloadstart;
            final int headersLength = payloadstart - offset;
            final ByteBuffer packetHeader = buffer.slice(offset, headersLength);
            final ByteBuffer packetPayload = buffer.slice(payloadstart, payloadLength)
                    .asReadOnlyBuffer();
            context.getTLSEngine().encryptPacket(packetType.keySpace().get(), packetNumber,
                    new HeaderGenerator(this.packetType, packetHeader), packetPayload, buffer);
        }

        public void writePayload(List<QuicFrame> frames) {
            for (var frame : frames) frame.encode(buffer);
        }

        public void writeLongConnectionId(QuicConnectionId connId) {
            ByteBuffer src = connId.asReadOnlyBuffer();
            assert src.remaining() <= MAX_CONNECTION_ID_LENGTH;
            buffer.put((byte)src.remaining());
            buffer.put(src);
        }

        public void writeShortConnectionId(QuicConnectionId connId) {
            ByteBuffer src = connId.asReadOnlyBuffer();
            assert src.remaining() <= MAX_CONNECTION_ID_LENGTH;
            buffer.put(src);
        }

        public void writeRetryToken(byte[] retryToken) {
            buffer.put(retryToken);
        }

        @Override
        public String toString() {
            return "PacketWriter(offset=%s, pos=%s, remaining=%s)"
                    .formatted(offset, position(), remaining());
        }

        public void protectHeaderLong(int packetNumberStart, int packetNumberLength)
                throws QuicKeyUnavailableException {
            protectHeader(packetNumberStart, packetNumberLength, (byte) 0x0f);
        }

        public void protectHeaderShort(int packetNumberStart, int packetNumberLength)
                throws QuicKeyUnavailableException {
            protectHeader(packetNumberStart, packetNumberLength, (byte) 0x1f);
        }

        private void protectHeader(int packetNumberStart, int packetNumberLength, byte headerMask)
                throws QuicKeyUnavailableException {
            // expect position at the end of packet
            QuicTLSEngine tlsEngine = context.getTLSEngine();
            int sampleSize = tlsEngine.getHeaderProtectionSampleSize(packetType.keySpace().get());
            assert buffer.position() - packetNumberStart >= sampleSize + 4 : buffer.position() - packetNumberStart - sampleSize - 4;

            ByteBuffer sample = buffer.slice(packetNumberStart + 4, sampleSize);
            ByteBuffer encryptedSample = tlsEngine.computeHeaderProtectionMask(packetType.keySpace().get(), false, sample);
            byte headers = headers();
            headers ^= (byte) (encryptedSample.get() & headerMask);
            headers(headers);
            maskPacketNumber(packetNumberStart, packetNumberLength, encryptedSample);
        }

        private void signRetry(final int version) {
            final QuicVersion retryVersion = QuicVersion.of(version)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown Quic version 0x"
                            + Integer.toHexString(version)));
            int payloadend = buffer.position();
            ByteBuffer temp = buffer.asReadOnlyBuffer();
            temp.position(offset);
            temp.limit(payloadend);
            try {
                context.getTLSEngine().signRetryPacket(retryVersion,
                        context.originalServerConnId().asReadOnlyBuffer(), temp, buffer);
            } catch (ShortBufferException e) {
                throw new AssertionError("Should not happen", e);
            }
        }

        // generates packet header and is capable of inserting a key phase into the header
        // when appropriate
        private static final class HeaderGenerator implements Function<Integer, ByteBuffer> {
            private final PacketType packetType;
            private final ByteBuffer header;

            private HeaderGenerator(final PacketType packetType, final ByteBuffer header) {
                this.packetType = packetType;
                this.header = header;
            }

            @Override
            public ByteBuffer apply(final Integer keyPhase) {
                // we use key phase only in 1-RTT packet header
                if (packetType != PacketType.ONERTT) {
                    assert keyPhase == 0 : "unexpected key phase " + keyPhase
                            + " for packet type " + packetType;
                    // return the packet header without setting any key phase bit
                    return header;
                }
                // update the key phase bit in the packet header
                setKeyPhase(keyPhase);
                return header.position(0).asReadOnlyBuffer();
            }

            private void setKeyPhase(final int kp) {
                if (kp != 0 && kp != 1) {
                    throw new IllegalArgumentException("Invalid key phase: " + kp);
                }
                final byte headerFirstByte = this.header.get();
                final byte updated = (byte) (headerFirstByte | (kp << 2));
                this.header.put(0, updated);
            }
        }
    }


    /**
     * Adds required padding frames if necessary.
     * Needed to make sure there's enough bytes to apply header protection
     * @param frames    requested list of frames
     * @param minLength requested minimum length
     * @return list of frames that meets the minimum length requirement
     */
    private static List<? extends QuicFrame> padFrames(List<? extends QuicFrame> frames, int minLength) {
        if (frames.size() >= minLength) {
            return frames;
        }
        int size = frames.stream().mapToInt(QuicFrame::size).reduce(0, Math::addExact);
        if (size >= minLength) {
            return frames;
        }
        List<QuicFrame> result = new ArrayList<>(frames.size() + 1);
        // add padding frame in front - some frames extend to end of packet
        result.add(new PaddingFrame(minLength - size));
        result.addAll(frames);
        return result;
    }

    /**
     * Returns an encoder for the given Quic version.
     * Returns {@code null} if no encoder for that version exists.
     *
     * @param quicVersion the Quic protocol version number
     * @return an encoder for the given Quic version or {@code null}
     */
    public static QuicPacketEncoder of(final QuicVersion quicVersion) {
        return switch (quicVersion) {
            case QUIC_V1 -> Encoders.QUIC_V1_ENCODER;
            case QUIC_V2 -> Encoders.QUIC_V2_ENCODER;
            default -> throw new IllegalArgumentException("No packet encoder for Quic version " + quicVersion);
        };
    }

    private static final class Encoders {
        static final Random RANDOM = new Random();
        static final QuicPacketEncoder QUIC_V1_ENCODER = new QuicPacketEncoder(QuicVersion.QUIC_V1);
        static final QuicPacketEncoder QUIC_V2_ENCODER = new QuicPacketEncoder(QuicVersion.QUIC_V2);
    }
}
