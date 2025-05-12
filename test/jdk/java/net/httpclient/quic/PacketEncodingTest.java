/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.CodingContext;
import jdk.internal.net.http.quic.PeerConnectionId;
import jdk.internal.net.http.quic.QuicConnectionIdFactory;
import jdk.internal.net.http.quic.packets.LongHeader;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.HandshakePacket;
import jdk.internal.net.http.quic.packets.InitialPacket;
import jdk.internal.net.http.quic.packets.LongHeaderPacket;
import jdk.internal.net.http.quic.packets.OneRttPacket;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.HeadersType;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.http.quic.packets.QuicPacketDecoder;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.http.quic.packets.QuicPacketNumbers;
import jdk.internal.net.http.quic.packets.RetryPacket;
import jdk.internal.net.http.quic.packets.ShortHeaderPacket;
import jdk.internal.net.http.quic.packets.VersionNegotiationPacket;
import jdk.internal.net.http.quic.packets.ZeroRttPacket;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportParametersConsumer;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.crypto.AEADBadTagException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static jdk.internal.net.http.quic.packets.QuicPacketNumbers.computePacketNumberLength;
import static org.testng.Assert.*;

/**
 * @test
 * @library /test/lib
 * @summary test packet encoding and decoding in unencrypted form and without
 *          any network involvement.
 * @run testng/othervm -Dseed=2646683818688275736 PacketEncodingTest
 * @run testng/othervm -Dseed=-3723256402256409075 PacketEncodingTest
 * @run testng/othervm -Dseed=-3689060484817342283 PacketEncodingTest
 * @run testng/othervm -Dseed=2425718686525936108 PacketEncodingTest
 * @run testng/othervm -Dseed=-2996954753243104355 PacketEncodingTest
 * @run testng/othervm -Dseed=8750823652999067800 PacketEncodingTest
 * @run testng/othervm -Dseed=2906555779406889127 PacketEncodingTest
 * @run testng/othervm -Dseed=902801756808168822 PacketEncodingTest
 * @run testng/othervm -Dseed=5643545543196691308 PacketEncodingTest
 * @run testng/othervm -Dseed=2646683818688275736 PacketEncodingTest
 * @run testng/othervm -Djdk.internal.httpclient.debug=true PacketEncodingTest
 */
public class PacketEncodingTest {

    @DataProvider
    public Object[][] longHeaderPacketProvider() {
        final QuicVersion[] quicVersions = QuicVersion.values();
        final List<Object[]> params = new ArrayList<>();
        for (final QuicVersion version : quicVersions) {
            final var p = new Object[][] {
                // quic-version, srcIdLen, dstIdLen, pn, largestAck
                new Object[] {version, 20, 20, 0L, -1L},
                new Object[] {version, 10, 20, 1L, 0L},
                new Object[] {version, 10, 20, 255L, 0L},
                new Object[] {version, 12, 15, 0xFFFFL, 0L},
                new Object[] {version, 9, 8, 0x7FFFFFFFL, 255L},
                new Object[] {version, 13, 11, 0x8FFFFFFFL, 0x10000000L},
                new Object[] {version, 19, 6, 0xFFFFFFFFL, 0xFFFFFFFEL},
                new Object[] {version, 6, 17, 0xFFFFFFFFFFL, 0xFFFFFFFF00L},
                new Object[] {version, 15, 14, 0x7FFFFFFFFFFFL, 0x7FFFFFFFFF00L},
                new Object[] {version, 7, 9, 0xa82f9b32L, 0xa82f30eaL},
                new Object[] {version, 18, 16, 0xace8feL, 0xabe8b3L},
                new Object[] {version, 16, 19, 0xac5c02L, 0xabe8b3L}
            };
            params.addAll(Arrays.asList(p));
        }
        return params.toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] shortHeaderPacketProvider() {
        final QuicVersion[] quicVersions = QuicVersion.values();
        final List<Object[]> params = new ArrayList<>();
        for (final QuicVersion version : quicVersions) {
            final var p = new Object[][] {
                new Object[] {version, 20, 0L, -1L},
                new Object[] {version, 17, 1L, 0L},
                new Object[] {version, 10, 255L, 0L},
                new Object[] {version, 12, 0xFFFFL, 0L},
                new Object[] {version, 9,  0x7FFFFFFFL, 255L},
                new Object[] {version, 13, 0x8FFFFFFFL, 0x10000000L},
                new Object[] {version, 19, 0xFFFFFFFFL, 0xFFFFFFFEL},
                new Object[] {version, 6,  0xFFFFFFFFFFL, 0xFFFFFFFF00L},
                new Object[] {version, 15, 0x7FFFFFFFFFFFL, 0x7FFFFFFFFF00L},
                new Object[] {version, 7,  0xa82f9b32L, 0xa82f30eaL},
                new Object[] {version, 18, 0xace8feL, 0xabe8b3L},
                new Object[] {version, 16, 0xac5c02L, 0xabe8b3L},
            };
            params.addAll(Arrays.asList(p));
        }
        return params.toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] versionAndRetryProvider() {
        final QuicVersion[] quicVersions = QuicVersion.values();
        final List<Object[]> params = new ArrayList<>();
        for (final QuicVersion version : quicVersions) {
            final var p = new Object[][] {
                // quic-version, srcIdLen, dstIdLen, pn, largestAck
                new Object[] {version, 20, 20},
                new Object[] {version, 10, 20},
                new Object[] {version, 12, 15},
                new Object[] {version, 9, 8},
                new Object[] {version, 13, 11},
                new Object[] {version, 19, 6},
                new Object[] {version, 6, 17},
                new Object[] {version, 15, 14},
                new Object[] {version, 7, 9},
                new Object[] {version, 18, 16},
                new Object[] {version, 16, 19},
            };
            params.addAll(Arrays.asList(p));
        }
        return params.toArray(Object[][]::new);
    }

    private static final AtomicLong IDS = new AtomicLong();
    private static final Random RANDOM = jdk.test.lib.RandomFactory.getRandom();
    private static final int MAX_DATAGRAM_IPV6 = 65527;

    byte[] randomIdBytes(int connectionLength) {
        byte[] bytes = new byte[connectionLength];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static class DummyQuicTLSEngine implements QuicTLSEngine {
        @Override
        public HandshakeState getHandshakeState() {
            throw new AssertionError("should not come here!");
        }
        @Override
        public KeySpace getCurrentSendKeySpace() {
            throw new AssertionError("should not come here!");
        }
        @Override
        public boolean keysAvailable(KeySpace keySpace) {
            return true;
        }

        @Override
        public void discardKeys(KeySpace keySpace) {
            // no-op
        }

        @Override
        public void setLocalQuicTransportParameters(ByteBuffer params) {
            throw new AssertionError("should not come here!");
        }

        @Override
        public void restartHandshake() throws IOException {
            throw new AssertionError("should not come here!");
        }

        @Override
        public void setRemoteQuicTransportParametersConsumer(QuicTransportParametersConsumer consumer) {
            throw new AssertionError("should not come here!");
        }
        @Override
        public void deriveInitialKeys(QuicVersion version, ByteBuffer connectionId) { }
        @Override
        public int getHeaderProtectionSampleSize(KeySpace keySpace) {
            return 0;
        }
        @Override
        public ByteBuffer computeHeaderProtectionMask(KeySpace keySpace, boolean incoming, ByteBuffer sample) {
            return ByteBuffer.allocate(5);
        }

        @Override
        public int getAuthTagSize() {
            return 0;
        }

        @Override
        public void encryptPacket(KeySpace keySpace, long packetNumber,
                                  Function<Integer, ByteBuffer> headerGenerator,
                                  ByteBuffer packetPayload, ByteBuffer output)
                throws QuicKeyUnavailableException, QuicTransportException {
            // this dummy QUIC TLS engine doesn't do any encryption.
            // we just copy over the raw packet payload into the output buffer
            output.put(packetPayload);
        }

        @Override
        public void decryptPacket(KeySpace keySpace, long packetNumber, int keyPhase,
                                  ByteBuffer packet, int headerLength, ByteBuffer output) {
            packet.position(packet.position() + headerLength);
            output.put(packet);
        }

        @Override
        public void signRetryPacket(QuicVersion version,
                                    ByteBuffer originalConnectionId, ByteBuffer packet, ByteBuffer output) {
            output.put(ByteBuffer.allocate(16));
        }
        @Override
        public void verifyRetryPacket(QuicVersion version,
                                      ByteBuffer originalConnectionId, ByteBuffer packet) throws AEADBadTagException {
        }
        @Override
        public ByteBuffer getHandshakeBytes(KeySpace keySpace) {
            throw new AssertionError("should not come here!");
        }
        @Override
        public void consumeHandshakeBytes(KeySpace keySpace, ByteBuffer payload) {
            throw new AssertionError("should not come here!");
        }
        @Override
        public Runnable getDelegatedTask() {
            throw new AssertionError("should not come here!");
        }
        @Override
        public boolean tryMarkHandshakeDone() {
            throw new AssertionError("should not come here!");
        }
        @Override
        public boolean tryReceiveHandshakeDone() {
            throw new AssertionError("should not come here!");
        }

        @Override
        public Set<QuicVersion> getSupportedQuicVersions() {
            return Set.of(QuicVersion.QUIC_V1);
        }

        @Override
        public void setClientMode(boolean mode) {
            throw new AssertionError("should not come here!");
        }

        @Override
        public boolean isClientMode() {
            throw new AssertionError("should not come here!");
        }

        @Override
        public SSLParameters getSSLParameters() {
            throw new AssertionError("should not come here!");
        }

        @Override
        public void setSSLParameters(SSLParameters sslParameters) {
            throw new AssertionError("should not come here!");
        }

        @Override
        public String getApplicationProtocol() {
            return null;
        }

        @Override
        public SSLSession getSession() {
            throw new AssertionError("should not come here!");
        }

        @Override
        public void versionNegotiated(QuicVersion quicVersion) {
            // no-op
        }

        @Override
        public void setOneRttContext(QuicOneRttContext ctx) {
            // no-op
        }
    }

    private static final QuicTLSEngine TLS_ENGINE = new DummyQuicTLSEngine();
    private static abstract class TestCodingContext implements CodingContext {
        TestCodingContext() { }
        @Override
        public int writePacket(QuicPacket packet, ByteBuffer buffer) {
            throw new AssertionError("should not come here!");
        }
        @Override
        public QuicPacket parsePacket(ByteBuffer src) throws IOException {
            throw new AssertionError("should not come here!");
        }
        @Override
        public boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
            return true;
        }
        @Override
        public QuicConnectionId originalServerConnId() {
            throw new AssertionError("should not come here!");
        }

        @Override
        public QuicTLSEngine getTLSEngine() {
            return TLS_ENGINE;
        }

        @Override
        public int minShortPacketPayloadSize(int destConnectionIdLength) {
            return 100 - (destConnectionIdLength - connectionIdLength());
        }
    }

    private void checkLongHeaderPacket(LongHeaderPacket packet,
                                       PacketType packetType,
                                       int versionNumber,
                                       PacketNumberSpace packetNumberSpace,
                                       long packetNumber,
                                       QuicConnectionId srcConnectionId,
                                       QuicConnectionId destConnectionId,
                                       List<QuicFrame> payload,
                                       int padding) {
        List<QuicFrame> expected;
        if (padding == 0) {
            expected = payload;
        } else if (payload.get(0) instanceof PaddingFrame pf) {
            expected = new ArrayList<>(payload);
            expected.set(0, new PaddingFrame(padding + pf.size()));
        } else {
            expected = new ArrayList<>(payload.size()+1);
            expected.add(new PaddingFrame(padding));
            expected.addAll(payload);
        }
        checkLongHeaderPacket(packet, packetType, versionNumber, packetNumberSpace, packetNumber,
                srcConnectionId, destConnectionId, expected);

    }

    private void checkLongHeaderPacket(LongHeaderPacket packet,
                                       PacketType packetType,
                                       int versionNumber,
                                       PacketNumberSpace packetNumberSpace,
                                       long packetNumber,
                                        QuicConnectionId srcConnectionId,
                                       QuicConnectionId destConnectionId,
                                       List<QuicFrame> payload) {
        // Check created packet
        assertEquals(packet.headersType(), HeadersType.LONG);
        assertEquals(packet.packetType(), packetType);
        boolean hasLength = switch (packetType) {
            case VERSIONS, RETRY -> false;
            default -> true;
        };
        assertEquals(packet.hasLength(), hasLength);
        assertEquals(packet.numberSpace(), packetNumberSpace);
        if (payload == null) {
            assertTrue(packet.frames().isEmpty());
        } else {
            assertEquals(getBuffers(packet.frames()), getBuffers(payload));
        }
        assertEquals(packet.version(), versionNumber);
        assertEquals(packet.packetNumber(), packetNumber);
        assertEquals(packet.sourceId(), srcConnectionId);
        assertEquals(packet.destinationId(), destConnectionId);

    }

    private static ByteBuffer encodeFrame(QuicFrame frame) {
        ByteBuffer result = ByteBuffer.allocate(frame.size());
        frame.encode(result);
        return result;
    }
    private static List<ByteBuffer> getBuffers(List<QuicFrame> payload) {
        return payload.stream().map(PacketEncodingTest::encodeFrame).toList();
    }
    private static List<ByteBuffer> getBuffers(List<QuicFrame> payload, int minSize) {
        int payloadSize = payload.stream().mapToInt(QuicFrame::size).sum();
        if (payloadSize < minSize) {
            payload = new ArrayList<>(payload);
            payload.add(0, new PaddingFrame(minSize - payloadSize));
        }
        return payload.stream().map(PacketEncodingTest::encodeFrame).toList();
    }

    private static String toHex(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes, 0, buffer.remaining());
        return HexFormat.of().formatHex(bytes);
    }

    private static String toHex(List<ByteBuffer> byteBuffers) {
        return "0x" + byteBuffers.stream()
                .map(PacketEncodingTest::toHex)
                .collect(Collectors.joining(":"));
    }

    private void checkLongHeaderPacketAt(ByteBuffer datagram, int offset,
                                         PacketType packetType, int versionNumber,
                                         QuicConnectionId srcConnectionId,
                                         QuicConnectionId destConnectionId) {
        assertEquals(QuicPacketDecoder.peekHeaderType(datagram, offset), HeadersType.LONG);
        assertEquals(QuicPacketDecoder.of(datagram, offset).peekPacketType(datagram, offset), packetType);
        LongHeader header = QuicPacketDecoder.peekLongHeader(datagram, offset);
        assertNotNull(header, "Could not parse packet header");
        assertEquals(header.version(), versionNumber);
        assertTrue(header.destinationId()
                .matches(destConnectionId.asReadOnlyBuffer()), "Destination ID doesn't match");
        assertTrue(header.sourceId()
                .matches(srcConnectionId.asReadOnlyBuffer()), "Source ID doesn't match");
    }

    private List<QuicFrame> frames(byte[] payload) throws IOException {
        return frames(payload, false);
    }

    private List<QuicFrame> frames(byte[] payload, boolean insert) throws IOException {
        int payloadSize = payload.length;
        ByteBuffer buf = ByteBuffer.wrap(payload);
        List<QuicFrame> frames = new ArrayList<>();
        int remaining = payloadSize;
        while (remaining > 7) {
            int size = RANDOM.nextInt(1, remaining - 6);
            byte[] data = new byte[size];
            RANDOM.nextBytes(data);
            QuicFrame frame = new CryptoFrame(0, size, ByteBuffer.wrap(data));
            int encoded = frame.size();
            assertTrue(encoded > 0, String.valueOf(encoded));
            assertTrue(encoded <= remaining, String.valueOf(encoded));
            if (insert) {
                frames.add(0, frame);
                buf.position(remaining - encoded);
            } else {
                frames.add(frame);
            }
            frame.encode(buf);
            remaining -= encoded;
        }
        if (remaining > 0) {
            var padding = new PaddingFrame(remaining);
            if (insert) {
                frames.add(0, padding);
                buf.position(0);
            } else {
                frames.add(padding);
            }
            padding.encode(buf);
        }
        if (insert) {
            assertEquals(buf.position(), remaining);
            assertEquals(buf.remaining(), payloadSize - remaining);
        } else {
            assertEquals(buf.remaining(), 0);
        }
        return List.copyOf(frames);
    }

    private ByteBuffer toByteBuffer(QuicPacketEncoder encoder, QuicPacket outgoingQuicPacket, CodingContext context)
            throws Exception {
        int size = outgoingQuicPacket.size();
        ByteBuffer buffer = ByteBuffer.allocate(size);
        encoder.encode(outgoingQuicPacket, buffer, context);
        assertEquals(buffer.position(), size, " for " + outgoingQuicPacket);
        buffer.flip();
        return buffer;
    }

    private void checkShortHeaderPacket(ShortHeaderPacket packet,
                                        PacketType packetType,
                                        PacketNumberSpace packetNumberSpace,
                                        long packetNumber,
                                        QuicConnectionId destConnectionId,
                                        List<QuicFrame> payload,
                                        int minSize) {
        // Check created packet
        assertEquals(packet.headersType(), HeadersType.SHORT);
        assertEquals(packet.packetType(), packetType);
        assertEquals(packet.hasLength(), false);
        assertEquals(packet.numberSpace(), packetNumberSpace);
        assertEquals(getBuffers(packet.frames()), getBuffers(payload, minSize));
        assertEquals(packet.packetNumber(), packetNumber);
        assertEquals(packet.destinationId(), destConnectionId);
    }

    private void checkShortHeaderPacketAt(ByteBuffer datagram, int offset,
                                          PacketType packetType,
                                          QuicConnectionId destConnectionId,
                                          CodingContext context) {
        assertEquals(QuicPacketDecoder.peekHeaderType(datagram, offset), HeadersType.SHORT);
        assertEquals(QuicPacketDecoder.of(QuicVersion.QUIC_V1).peekPacketType(datagram, offset), packetType);
        assertEquals(QuicPacketDecoder.peekVersion(datagram, offset), 0);
        int pos = datagram.position();
        if (pos != offset) datagram.position(offset);
        try {
            assertEquals(QuicPacketDecoder.peekShortConnectionId(datagram, destConnectionId.length())
                    .mismatch(destConnectionId.asReadOnlyBuffer()), -1);
        } finally {
            if (pos != offset) datagram.position(pos);
        }
    }

    @Test(dataProvider = "longHeaderPacketProvider")
    public void testInitialPacket(QuicVersion quicVersion, int srcIdLength, int destIdLength,
                                  long packetNumber, long largestAcked) throws Exception {
        System.out.printf("%ntestInitialPacket(qv:%s, scid:%d, dcid:%d, pn:%d, ack:%d)%n",
                quicVersion, srcIdLength, destIdLength, packetNumber, largestAcked);
        QuicPacketEncoder encoder = QuicPacketEncoder.of(quicVersion);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(quicVersion);
        byte[] destid = QuicConnectionIdFactory.getClient()
                .newConnectionID(destIdLength, IDS.incrementAndGet());
        assert destid.length <= 20;
        final QuicConnectionId destConnectionId = new PeerConnectionId(destid);
        assertEquals(destid.length, destConnectionId.length(), "dcid length");
        destIdLength = destid.length;

        byte[] srcid = randomIdBytes(srcIdLength);
        final QuicConnectionId srcConnectionId = new PeerConnectionId(srcid);
        assertEquals(srcid.length, srcConnectionId.length(), "scid length");

        int bound = MAX_DATAGRAM_IPV6 - srcIdLength - destid.length - 7
                - QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked)
                - VariableLengthEncoder.getEncodedSize(MAX_DATAGRAM_IPV6);

        // ensure that bound - tokenLength - 1 > 0
        assert bound > 4;
        int tokenLength = RANDOM.nextInt(bound - 4);

        byte[] token = tokenLength == 0 ? null : new byte[tokenLength];
        if (token != null) RANDOM.nextBytes(token);
        int packetNumberLength =
                QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked);
        int payloadSize = Math.max(RANDOM.nextInt(bound - tokenLength - 1) + 1, 4 - packetNumberLength);
        System.out.printf("testInitialPacket.encode(scid:%s, dcid:%s, token:%d, payload:%d)%n",
                srcIdLength, destIdLength, tokenLength, payloadSize);

        CodingContext context = new TestCodingContext() {
            @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.INITIAL ? largestAcked : -1;
            }
            @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.INITIAL ? largestAcked : -1;
            }
            @Override public int connectionIdLength() {
                return srcIdLength;
            }
        };
        int minsize = encoder.computeMaxInitialPayloadSize(context,
                computePacketNumberLength(packetNumber,
                        context.largestAckedPN(PacketNumberSpace.INITIAL)),
                tokenLength, srcIdLength,
                destIdLength, 1200);
        int padding =  (payloadSize < minsize) ? minsize - payloadSize : 0;
        System.out.println("testInitialPacket: available=%s, payload=%s, padding=%s"
                .formatted(minsize, payloadSize, padding));


        byte[] payload = new byte[payloadSize];
        List<QuicFrame> frames = frames(payload, padding != 0);
        assertEquals(frames.stream().mapToInt(QuicFrame::size)
                .reduce(0, Math::addExact), payloadSize);


        // Create an initial packet
        var packet = encoder.newInitialPacket(srcConnectionId,
                destConnectionId,
                token,
                packetNumber,
                largestAcked,
                frames,
                context);

        if (padding > 0) {
            var frames2 = new ArrayList<>(frames);
            frames2.add(0, new PaddingFrame(padding));
            var packet2 = encoder.newInitialPacket(srcConnectionId,
                    destConnectionId,
                    token,
                    packetNumber,
                    largestAcked,
                    frames2,
                    context);
            assertEquals(padding, padding + (1200 - packet2.size()));
        }

        // Check created packet
        assertTrue(packet instanceof InitialPacket);
        var initialPacket = (InitialPacket) packet;
        System.out.printf("%s: pn:%s, tklen:%s, payloadSize:%s, padding:%s, packet::size:%s, " +
                        "\n\tinputFrames: %s, " +
                        "\n\tencodedFrames:%s%n",
                PacketType.INITIAL, packetNumber, tokenLength, payload.length, padding,
                packet.size(), frames, packet.frames());
        checkLongHeaderPacket(initialPacket, PacketType.INITIAL, quicVersion.versionNumber(),
                PacketNumberSpace.INITIAL, packetNumber,
                srcConnectionId, destConnectionId, frames, padding);
        assertEquals(initialPacket.tokenLength(), tokenLength);
        assertEquals(initialPacket.token(), token);
        assertEquals(initialPacket.hasLength(), true);
        assertEquals(initialPacket.length(), packetNumberLength + payloadSize + padding);

        // Check that peeking at the encoded packet returns correct information

        // Decode the two packets in the datagram
        ByteBuffer encoded = toByteBuffer(encoder, packet, context);
        checkLongHeaderPacketAt(encoded, 0, PacketType.INITIAL, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // coalesce two packets in a single datagram and check
        // the peek methods again
        int offset = RANDOM.nextInt(256);
        int second = offset + encoded.limit();
        System.out.printf("testInitialPacket.encode(offset:%d, second:%d)%n",
                offset, second);
        ByteBuffer datagram = ByteBuffer.allocate(encoded.limit() * 2 + offset * 2);
        datagram.position(offset);
        datagram.put(encoded);
        encoded.flip();
        datagram.put(encoded);
        encoded.flip();
        datagram.flip();

        // check header, type and version of both packets
        System.out.printf("datagram(offset:%d, second:%d, position:%d, limit:%d)%n",
                offset, second, datagram.position(), datagram.limit());
        System.out.printf("reading first datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, offset, PacketType.INITIAL, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);
        System.out.printf("reading second datagram(offset:%d, position:%d, limit:%d)%n",
                second, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, second, PacketType.INITIAL, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // check that skip packet can skip both packets
        datagram.position(0);
        datagram.limit(datagram.capacity());
        decoder.skipPacket(datagram, offset);
        assertEquals(datagram.position(), second);
        decoder.skipPacket(datagram, second);
        assertEquals(datagram.remaining(), offset);

        datagram.position(offset);
        int size = second - offset;
        for (int i=0; i<2; i++) {
            int pos = datagram.position();
            System.out.printf("Decoding packet: %d at %d%n", (i+1), pos);
            var decodedPacket = decoder.decode(datagram, context);
            assertEquals(datagram.position(), pos + size);
            assertTrue(decodedPacket instanceof InitialPacket, "decoded: " + decodedPacket);
            InitialPacket initialDecoded = InitialPacket.class.cast(decodedPacket);
            checkLongHeaderPacket(initialDecoded, PacketType.INITIAL, quicVersion.versionNumber(),
                    PacketNumberSpace.INITIAL, packetNumber,
                    srcConnectionId, destConnectionId, frames, padding);
            assertEquals(decodedPacket.size(), packet.size());
            assertEquals(decodedPacket.size(), size);
            assertEquals(initialDecoded.tokenLength(), tokenLength);
            assertEquals(initialDecoded.token(), token);
            assertEquals(initialDecoded.length(), initialPacket.length());
            assertEquals(initialDecoded.length(), packetNumberLength + payloadSize + padding);
        }
        assertEquals(datagram.position(), second + second - offset);
    }

    @Test(dataProvider = "longHeaderPacketProvider")
    public void testHandshakePacket(QuicVersion quicVersion, int srcIdLength, int destIdLength,
                                  long packetNumber, long largestAcked) throws Exception {
        System.out.printf("%ntestHandshakePacket(qv:%s, scid:%d, dcid:%d, pn:%d, ack:%d)%n",
                quicVersion, srcIdLength, destIdLength, packetNumber, largestAcked);
        QuicPacketEncoder encoder = QuicPacketEncoder.of(quicVersion);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(quicVersion);
        byte[] destid = QuicConnectionIdFactory.getClient()
                .newConnectionID(destIdLength, IDS.incrementAndGet());
        assert destid.length <= 20;
        QuicConnectionId destConnectionId = new PeerConnectionId(destid);
        byte[] srcid = randomIdBytes(srcIdLength);
        QuicConnectionId srcConnectionId = new PeerConnectionId(srcid);
        int bound = MAX_DATAGRAM_IPV6 - srcIdLength - destid.length - 7
                - QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked)
                - VariableLengthEncoder.getEncodedSize(MAX_DATAGRAM_IPV6);

        int packetNumberLength =
                QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked);
        int payloadSize = Math.max(RANDOM.nextInt(bound - 1) + 1, 4 - packetNumberLength);
        byte[] payload = new byte[payloadSize];
        var frames = frames(payload);
        System.out.printf("testHandshakePacket.encode(payload:%d)%n", payloadSize);

        CodingContext context = new TestCodingContext() {
            @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.HANDSHAKE ? largestAcked : -1;
            }
            @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.HANDSHAKE ? largestAcked : -1;
            }
            @Override public int connectionIdLength() {
                return srcIdLength;
            }
        };
        // Create an initial packet
        var packet = encoder.newHandshakePacket(srcConnectionId,
                destConnectionId,
                packetNumber,
                largestAcked,
                frames,
                context);

        // Check created packet
        assertTrue(packet instanceof HandshakePacket);
        var handshakePacket = (HandshakePacket) packet;
        checkLongHeaderPacket(handshakePacket, PacketType.HANDSHAKE, quicVersion.versionNumber(),
                PacketNumberSpace.HANDSHAKE, packetNumber,
                srcConnectionId, destConnectionId, frames);
        assertEquals(handshakePacket.hasLength(), true);
        assertEquals(handshakePacket.length(), packetNumberLength + payloadSize);

        // Decode the two packets in the datagram
        // Check that peeking at the encoded packet returns correct information
        ByteBuffer encoded = toByteBuffer(encoder, packet, context);
        checkLongHeaderPacketAt(encoded, 0, PacketType.HANDSHAKE, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // coalesce two packets in a single datagram and check
        // the peek methods again
        int offset = RANDOM.nextInt(256);
        int second = offset + encoded.limit();
        System.out.printf("testHandshakePacket.encode(offset:%d, second:%d)%n",
                offset, second);
        ByteBuffer datagram = ByteBuffer.allocate(encoded.limit() * 2 + offset * 2);
        datagram.position(offset);
        datagram.put(encoded);
        encoded.flip();
        datagram.put(encoded);
        encoded.flip();
        datagram.flip();

        // check header, type and version of both packets
        System.out.printf("datagram(offset:%d, second:%d, position:%d, limit:%d)%n",
                offset, second, datagram.position(), datagram.limit());
        // set position to first packet to check connection ids
        System.out.printf("reading first datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, offset, PacketType.HANDSHAKE, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);
        System.out.printf("reading second datagram(offset:%d, position:%d, limit:%d)%n",
                second, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, second, PacketType.HANDSHAKE, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // check that skip packet can skip both packets
        datagram.position(0);
        datagram.limit(datagram.capacity());
        decoder.skipPacket(datagram, offset);
        assertEquals(datagram.position(), second);
        decoder.skipPacket(datagram, second);
        assertEquals(datagram.remaining(), offset);

        datagram.position(offset);
        int size = second - offset;
        for (int i=0; i<2; i++) {
            int pos = datagram.position();
            System.out.printf("Decoding packet: %d at %d%n", (i+1), pos);
            var decodedPacket = decoder.decode(datagram, context);
            assertEquals(datagram.position(), pos + size);
            assertTrue(decodedPacket instanceof HandshakePacket, "decoded: " + decodedPacket);
            HandshakePacket handshakeDecoded = HandshakePacket.class.cast(decodedPacket);
            checkLongHeaderPacket(handshakeDecoded, PacketType.HANDSHAKE, quicVersion.versionNumber(),
                    PacketNumberSpace.HANDSHAKE, packetNumber,
                    srcConnectionId, destConnectionId, frames);
            assertEquals(decodedPacket.size(), packet.size());
            assertEquals(decodedPacket.size(), size);
            assertEquals(handshakeDecoded.length(), handshakePacket.length());
            assertEquals(handshakeDecoded.length(), packetNumberLength + payloadSize);
        }
        assertEquals(datagram.position(), second + second - offset);
    }

    @Test(dataProvider = "longHeaderPacketProvider")
    public void testZeroRTTPacket(QuicVersion quicVersion, int srcIdLength, int destIdLength,
                                    long packetNumber, long largestAcked) throws Exception {
        System.out.printf("%ntestZeroRTTPacket(qv:%s, scid:%d, dcid:%d, pn:%d, ack:%d)%n",
                quicVersion, srcIdLength, destIdLength, packetNumber, largestAcked);
        QuicPacketEncoder encoder = QuicPacketEncoder.of(quicVersion);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(quicVersion);
        byte[] destid = QuicConnectionIdFactory.getClient()
                .newConnectionID(destIdLength, IDS.incrementAndGet());
        assert destid.length <= 20;
        QuicConnectionId destConnectionId = new PeerConnectionId(destid);
        byte[] srcid = randomIdBytes(srcIdLength);
        QuicConnectionId srcConnectionId = new PeerConnectionId(srcid);
        int bound = MAX_DATAGRAM_IPV6 - srcIdLength - destid.length - 7
                - QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked)
                - VariableLengthEncoder.getEncodedSize(MAX_DATAGRAM_IPV6);

        int packetNumberLength =
                QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked);
        int payloadSize = Math.max(RANDOM.nextInt(bound - 1) + 1, 4 - packetNumberLength);
        byte[] payload = new byte[payloadSize];
        var frames = frames(payload);
        System.out.printf("testZeroRTTPacket.encode(payload:%d)%n", payloadSize);

        CodingContext context = new TestCodingContext() {
            @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.APPLICATION ? largestAcked : -1;
            }
            @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.APPLICATION ? largestAcked : -1;
            }
            @Override public int connectionIdLength() {
                return srcIdLength;
            }
        };
        // Create an initial packet
        var packet = encoder.newZeroRttPacket(srcConnectionId,
                destConnectionId,
                packetNumber,
                largestAcked,
                frames,
                context);

        // Check created packet
        assertTrue(packet instanceof ZeroRttPacket);
        var zeroRttPacket = (ZeroRttPacket) packet;
        checkLongHeaderPacket(zeroRttPacket, PacketType.ZERORTT, quicVersion.versionNumber(),
                PacketNumberSpace.APPLICATION, packetNumber,
                srcConnectionId, destConnectionId, frames);
        assertEquals(zeroRttPacket.hasLength(), true);
        assertEquals(zeroRttPacket.length(), packetNumberLength + payloadSize);

        // Check that peeking at the encoded packet returns correct information
        ByteBuffer encoded = toByteBuffer(encoder, packet, context);
        checkLongHeaderPacketAt(encoded, 0, PacketType.ZERORTT, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // coalesce two packets in a single datagram and check
        // the peek methods again
        int offset = RANDOM.nextInt(256);
        int second = offset + encoded.limit();
        System.out.printf("testZeroRTTPacket.encode(offset:%d, second:%d)%n",
                offset, second);
        ByteBuffer datagram = ByteBuffer.allocate(encoded.limit() * 2 + offset * 2);
        datagram.position(offset);
        datagram.put(encoded);
        encoded.flip();
        datagram.put(encoded);
        encoded.flip();
        datagram.flip();

        // check header, type and version of both packets
        System.out.printf("datagram(offset:%d, second:%d, position:%d, limit:%d)%n",
                offset, second, datagram.position(), datagram.limit());
        // set position to first packet to check connection ids
        System.out.printf("reading first datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, offset, PacketType.ZERORTT, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);
        System.out.printf("reading second datagram(offset:%d, position:%d, limit:%d)%n",
                second, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, second, PacketType.ZERORTT, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // check that skip packet can skip both packets
        datagram.position(0);
        datagram.limit(datagram.capacity());
        decoder.skipPacket(datagram, offset);
        assertEquals(datagram.position(), second);
        decoder.skipPacket(datagram, second);
        assertEquals(datagram.remaining(), offset);

        // Decode the two packets in the datagram
        datagram.position(offset);
        int size = second - offset;
        for (int i=0; i<2; i++) {
            int pos = datagram.position();
            System.out.printf("Decoding packet: %d at %d%n", (i+1), pos);
            var decodedPacket = decoder.decode(datagram, context);
            assertEquals(datagram.position(), pos + size);
            assertTrue(decodedPacket instanceof ZeroRttPacket, "decoded: " + decodedPacket);
            ZeroRttPacket zeroRttDecoded = ZeroRttPacket.class.cast(decodedPacket);
            checkLongHeaderPacket(zeroRttDecoded, PacketType.ZERORTT, quicVersion.versionNumber(),
                    PacketNumberSpace.APPLICATION, packetNumber,
                    srcConnectionId, destConnectionId, frames);
            assertEquals(decodedPacket.size(), packet.size());
            assertEquals(decodedPacket.size(), size);
            assertEquals(zeroRttDecoded.length(), zeroRttPacket.length());
            assertEquals(zeroRttDecoded.length(), packetNumberLength + payloadSize);
        }
        assertEquals(datagram.position(), second + second - offset);
    }

    @Test(dataProvider = "versionAndRetryProvider")
    public void testVersionNegotiationPacket(QuicVersion quicVersion, int srcIdLength, int destIdLength)
            throws Exception {
        System.out.printf("%ntestVersionNegotiationPacket(qv:%s, scid:%d, dcid:%d, pn:%d, ack:%d)%n",
                quicVersion, srcIdLength, destIdLength, -1, -1);
        QuicPacketEncoder encoder = QuicPacketEncoder.of(quicVersion);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(quicVersion);
        byte[] destid = QuicConnectionIdFactory.getClient()
                .newConnectionID(destIdLength, IDS.incrementAndGet());
        assert destid.length <= 20;
        QuicConnectionId destConnectionId = new PeerConnectionId(destid);
        byte[] srcid = randomIdBytes(srcIdLength);
        QuicConnectionId srcConnectionId = new PeerConnectionId(srcid);

        final List<Integer> versionList = new ArrayList<>();
        for (final QuicVersion qv : QuicVersion.values()) {
            versionList.add(qv.versionNumber());
        }
        System.out.printf("testVersionNegotiationPacket.encode(versions:%d)%n", versionList.size());

        // Create an initial packet
        var packet = QuicPacketEncoder.newVersionNegotiationPacket(srcConnectionId,
                destConnectionId,
                versionList.stream().mapToInt(Integer::intValue).toArray());

        // Check created packet
        assertTrue(packet instanceof VersionNegotiationPacket);
        var versionPacket = (VersionNegotiationPacket) packet;
        checkLongHeaderPacket(versionPacket, PacketType.VERSIONS, 0,
                PacketNumberSpace.NONE, -1,
                srcConnectionId, destConnectionId, null);
        assertEquals(versionPacket.hasLength(), false);
        assertEquals(versionPacket.supportedVersions(),
                versionList.stream().mapToInt(Integer::intValue).toArray());

        CodingContext context = new TestCodingContext() {
            @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
                return -1;
            }
            @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
                return -1;
            }
            @Override public int connectionIdLength() {
                return srcIdLength;
            }
        };
        // Check that peeking at the encoded packet returns correct information
        ByteBuffer encoded = toByteBuffer(encoder, packet, context);
        checkLongHeaderPacketAt(encoded, 0, PacketType.VERSIONS, 0,
                srcConnectionId, destConnectionId);

        // version negotiation packets can't be coalesced
        int offset = RANDOM.nextInt(256);
        int end = offset + encoded.limit();
        System.out.printf("testVersionNegotiationPacket.encode(offset:%d, end:%d)%n",
                offset, end);
        ByteBuffer datagram = ByteBuffer.allocate(encoded.limit() + offset);
        datagram.position(offset);
        datagram.put(encoded);
        encoded.flip();
        datagram.flip();

        // check header, type and version of both packets
        System.out.printf("datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        // set position to first packet to check connection ids
        System.out.printf("reading datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, offset, PacketType.VERSIONS, 0,
                srcConnectionId, destConnectionId);

        // check that skip packet can skip packet
        datagram.position(0);
        datagram.limit(datagram.capacity());
        decoder.skipPacket(datagram, offset);
        assertEquals(datagram.position(), end);
        assertEquals(datagram.remaining(), 0);

        // Decode the two packets in the datagram
        datagram.position(offset);
        int size = end - offset;
        for (int i=0; i<1; i++) {
            int pos = datagram.position();
            System.out.printf("Decoding packet: %d at %d%n", (i+1), pos);
            var decodedPacket = decoder.decode(datagram, context);
            assertEquals(datagram.position(), pos + size);
            assertTrue(decodedPacket instanceof VersionNegotiationPacket, "decoded: " + decodedPacket);
            VersionNegotiationPacket decodedVersion = VersionNegotiationPacket.class.cast(decodedPacket);
            checkLongHeaderPacket(decodedVersion, PacketType.VERSIONS, 0,
                    PacketNumberSpace.NONE, -1,
                    srcConnectionId, destConnectionId, null);
            assertEquals(decodedPacket.size(), packet.size());
            assertEquals(decodedPacket.size(), size);
            assertEquals(decodedVersion.supportedVersions(),
                    versionList.stream().mapToInt(Integer::intValue).toArray());
        }
        assertEquals(datagram.position(), end);
    }

    @Test(dataProvider = "versionAndRetryProvider")
    public void testRetryPacket(QuicVersion quicVersion, int srcIdLength, int destIdLength)
            throws Exception {
        System.out.printf("%ntestRetryPacket(qv:%s, scid:%d, dcid:%d, pn:%d, ack:%d)%n",
                quicVersion, srcIdLength, destIdLength, -1, -1);
        QuicPacketEncoder encoder = QuicPacketEncoder.of(quicVersion);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(quicVersion);
        byte[] destid = QuicConnectionIdFactory.getClient()
                .newConnectionID(destIdLength, IDS.incrementAndGet());
        assert destid.length <= 20;
        QuicConnectionId destConnectionId = new PeerConnectionId(destid);
        byte[] srcid = randomIdBytes(srcIdLength);
        QuicConnectionId srcConnectionId = new PeerConnectionId(srcid);
        byte[] origId = randomIdBytes(destIdLength);
        QuicConnectionId origConnectionId = new PeerConnectionId(origId);
        int bound = (MAX_DATAGRAM_IPV6 - srcIdLength - destid.length - 7);

        int retryTokenLength = RANDOM.nextInt(bound - 16) + 1;
        byte[] retryToken = new byte[retryTokenLength];
        RANDOM.nextBytes(retryToken);
        System.out.printf("testRetryPacket.encode(token:%d)%n", retryTokenLength);
        int expectedSize = 7 + 16 + destid.length + srcIdLength + retryTokenLength;

        // Create an initial packet
        var packet = encoder.newRetryPacket(srcConnectionId,
                destConnectionId,
                retryToken);

        // Check created packet
        assertTrue(packet instanceof RetryPacket);
        var retryPacket = (RetryPacket) packet;
        checkLongHeaderPacket(retryPacket, PacketType.RETRY, quicVersion.versionNumber(),
                PacketNumberSpace.NONE, -1,
                srcConnectionId, destConnectionId, null);
        assertEquals(retryPacket.hasLength(), false);
        assertEquals(retryPacket.retryToken(), retryToken);
        assertEquals(retryPacket.size(), expectedSize);

        CodingContext context = new TestCodingContext() {
            @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
                return -1;
            }
            @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
                return -1;
            }
            @Override public int connectionIdLength() {
                return srcIdLength;
            }
            @Override public QuicConnectionId originalServerConnId() { return origConnectionId; }
        };
        // Check that peeking at the encoded packet returns correct information
        ByteBuffer encoded = toByteBuffer(encoder, packet, context);
        checkLongHeaderPacketAt(encoded, 0, PacketType.RETRY, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // version negotiation packets can't be coalesced
        int offset = RANDOM.nextInt(256);
        int end = offset + encoded.limit();
        System.out.printf("testRetryPacket.encode(offset:%d, end:%d)%n",
                offset, end);
        ByteBuffer datagram = ByteBuffer.allocate(encoded.limit() + offset);
        datagram.position(offset);
        datagram.put(encoded);
        encoded.flip();
        datagram.flip();

        // check header, type and version of both packets
        System.out.printf("datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        // set position to first packet to check connection ids
        System.out.printf("reading datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        checkLongHeaderPacketAt(datagram, offset, PacketType.RETRY, quicVersion.versionNumber(),
                srcConnectionId, destConnectionId);

        // check that skip packet can skip packet
        datagram.position(0);
        datagram.limit(datagram.capacity());
        decoder.skipPacket(datagram, offset);
        assertEquals(datagram.position(), end);
        assertEquals(datagram.remaining(), 0);

        // Decode the two packets in the datagram
        datagram.position(offset);
        int size = end - offset;
        for (int i=0; i<1; i++) {
            int pos = datagram.position();
            System.out.printf("Decoding packet: %d at %d%n", (i+1), pos);
            var decodedPacket = decoder.decode(datagram, context);
            assertEquals(datagram.position(), pos + size);
            assertTrue(decodedPacket instanceof RetryPacket, "decoded: " + decodedPacket);
            RetryPacket decodedRetry = RetryPacket.class.cast(decodedPacket);
            checkLongHeaderPacket(decodedRetry, PacketType.RETRY, quicVersion.versionNumber(),
                    PacketNumberSpace.NONE, -1,
                    srcConnectionId, destConnectionId, null);
            assertEquals(decodedPacket.size(), packet.size());
            assertEquals(decodedPacket.size(), size);
            assertEquals(decodedPacket.size(), expectedSize);
            assertEquals(decodedRetry.retryToken(), retryToken);
        }
        assertEquals(datagram.position(), end);
    }

    @Test(dataProvider = "shortHeaderPacketProvider")
    public void testOneRTTPacket(QuicVersion quicVersion, int destIdLength,
                                  long packetNumber, long largestAcked) throws Exception {
        System.out.printf("%ntestOneRTTPacket(qv:%s, dcid:%d, pn:%d, ack:%d)%n",
                quicVersion, destIdLength, packetNumber, largestAcked);
        QuicPacketEncoder encoder = QuicPacketEncoder.of(quicVersion);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(quicVersion);
        byte[] destid = QuicConnectionIdFactory.getClient()
                .newConnectionID(destIdLength, IDS.incrementAndGet());
        assert destid.length <= 20;
        QuicConnectionId destConnectionId = new PeerConnectionId(destid);
        int bound = MAX_DATAGRAM_IPV6 - destid.length - 7
                - QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked)
                - VariableLengthEncoder.getEncodedSize(MAX_DATAGRAM_IPV6);

        int packetNumberLength =
                QuicPacketNumbers.computePacketNumberLength(packetNumber, largestAcked);
        int payloadSize = Math.max(RANDOM.nextInt(bound - 1) + 1, 4 - packetNumberLength);
        byte[] payload = new byte[payloadSize];
        var frames = frames(payload);

        CodingContext context = new TestCodingContext() {
            @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.APPLICATION ? largestAcked : -1;
            }
            @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
                return packetSpace == PacketNumberSpace.APPLICATION ? largestAcked : -1;
            }
            // since we're going to decode the short packet, we need to return
            // the same length that was used as destination cid in the packet
            @Override public int connectionIdLength() {
                return destid.length;
            }
        };

        int paddedPayLoadSize = Math.max(payloadSize + packetNumberLength, context.minShortPacketPayloadSize(destid.length));
        System.out.printf("testOneRTTPacket.encode(payload:%d, padded:%d, destid.length: %d)%n",
                payloadSize, paddedPayLoadSize, destid.length);
        int expectedSize = 1 + destid.length + paddedPayLoadSize;
        // Create an 1-RTT packet
        OneRttPacket packet = encoder.newOneRttPacket(destConnectionId,
                packetNumber,
                largestAcked,
                frames,
                context);

        int minPayloadSize = context.minShortPacketPayloadSize(destConnectionId.length()) - packetNumberLength;
        checkShortHeaderPacket(packet, PacketType.ONERTT,
                PacketNumberSpace.APPLICATION, packetNumber,
                destConnectionId, frames, minPayloadSize);
        assertEquals(packet.hasLength(), false);
        assertEquals(packet.size(), expectedSize);

        // Check that peeking at the encoded packet returns correct information
        ByteBuffer encoded = toByteBuffer(encoder, packet, context);
        checkShortHeaderPacketAt(encoded, 0, PacketType.ONERTT,
                destConnectionId, context);

        // write packet at an offset in the datagram to simulate
        // short packet coalesced after long packet and check
        // the peek methods again
        int offset = RANDOM.nextInt(256);
        int end = offset + encoded.limit();
        System.out.printf("testOneRTTPacket.encode(offset:%d, end:%d)%n",
                offset, end);
        ByteBuffer datagram = ByteBuffer.allocate(encoded.limit() + offset * 2);
        datagram.position(offset);
        datagram.put(encoded);
        encoded.flip();
        datagram.flip();
        assert datagram.limit() == offset + encoded.remaining();

        // set position to first packet to check connection ids
        System.out.printf("reading datagram(offset:%d, position:%d, limit:%d)%n",
                offset, datagram.position(), datagram.limit());
        checkShortHeaderPacketAt(datagram, offset, PacketType.ONERTT,
                destConnectionId, context);

        // check that skip packet can skip packet at offset
        datagram.position(0);
        datagram.limit(end);
        decoder.skipPacket(datagram, offset);
        assertEquals(datagram.position(), offset + expectedSize);
        assertEquals(datagram.position(), datagram.limit());
        assertEquals(datagram.position(), datagram.capacity() - offset);


        // Decode the packet in the datagram
        datagram.position(offset);
        int size = expectedSize;
        for (int i=0; i<1; i++) {
            int pos = datagram.position();
            System.out.printf("Decoding packet: %d at %d%n", (i+1), pos);
            var decodedPacket = decoder.decode(datagram, context);
            assertEquals(datagram.position(), pos + size);
            assertTrue(decodedPacket instanceof OneRttPacket, "decoded: " + decodedPacket);
            OneRttPacket oneRttDecoded = OneRttPacket.class.cast(decodedPacket);
            List<QuicFrame> expectedFrames = frames;
            if (frames.size() > 0 && frames.get(0) instanceof PaddingFrame) {
                // The first frame should be a crypto frame, except if payloadSize
                // was less than 7.
                int frameSizes = frames.stream().mapToInt(QuicFrame::size).sum();
                assert frameSizes == payloadSize;
                assert frameSizes <= 7;
                // decoder will coalesce padding frames. So instead of finding
                // two padding frames in the decoded packet we will find just one.
                // To make the check pass, we should expect a bigger padding frame.
                if (minPayloadSize > frameSizes) {
                    // replace the first frame with a bigger padding frame
                    expectedFrames = new ArrayList<>(frames);
                    var first = frames.get(0);
                    // replace the first frame with a bigger padding frame that
                    // coalesce the first padding payload frame with the padding that
                    // should have been added by the encoder.
                    // We will then be able to check that the decoded packet contains
                    // that single bigger padding frame.
                    expectedFrames.set(0, new PaddingFrame(minPayloadSize - frameSizes + first.size()));
                }
            }
            checkShortHeaderPacket(oneRttDecoded, PacketType.ONERTT,
                    PacketNumberSpace.APPLICATION, packetNumber,
                    destConnectionId, expectedFrames, minPayloadSize);
            assertEquals(decodedPacket.size(), packet.size());
            assertEquals(decodedPacket.size(), size);
        }
        assertEquals(datagram.position(), offset + size);
        assertEquals(datagram.remaining(), 0);
        assertEquals(datagram.limit(), end);

    }

    @Test
    public void testNoMismatch() {
        List<ByteBuffer> match1 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4}),
                ByteBuffer.wrap(new byte[] {5, 6}),
                ByteBuffer.wrap(new byte[] {7, 8}),
                ByteBuffer.wrap(new byte[] {9}),
                ByteBuffer.wrap(new byte[] {10, 11, 12}),
                ByteBuffer.wrap(new byte[] {13, 14, 15, 16}),
                ByteBuffer.wrap(new byte[] {17, 18, 19, 20})
                );
        List<ByteBuffer> match2 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4, 5}),
                ByteBuffer.wrap(new byte[] {6}),
                ByteBuffer.wrap(new byte[] {7}),
                ByteBuffer.wrap(new byte[] {8, 9}),
                ByteBuffer.wrap(new byte[] {10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20})
        );
        List<ByteBuffer> match3 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}),
                ByteBuffer.wrap(new byte[] {9, 10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20})
        );
        assertEquals(Utils.mismatch(match1, match1), -1);
        assertEquals(Utils.mismatch(match2, match2), -1);
        assertEquals(Utils.mismatch(match3, match3), -1);
        assertEquals(Utils.mismatch(match1, match2), -1);
        assertEquals(Utils.mismatch(match2, match1), -1);
        assertEquals(Utils.mismatch(match1, match3), -1);
        assertEquals(Utils.mismatch(match3, match1), -1);
        assertEquals(Utils.mismatch(match2, match3), -1);
        assertEquals(Utils.mismatch(match3, match2), -1);
    }

    @Test
    public void testMismatch() {
        // match1, match2, match3 match with each others
        List<ByteBuffer> match1 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4}),
                ByteBuffer.wrap(new byte[] {5, 6}),
                ByteBuffer.wrap(new byte[] {7, 8}),
                ByteBuffer.wrap(new byte[] {9}),
                ByteBuffer.wrap(new byte[] {10, 11, 12}),
                ByteBuffer.wrap(new byte[] {13, 14, 15, 16}),
                ByteBuffer.wrap(new byte[] {17, 18, 19, 20})
        );
        List<ByteBuffer> match2 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4, 5}),
                ByteBuffer.wrap(new byte[] {6}),
                ByteBuffer.wrap(new byte[] {7}),
                ByteBuffer.wrap(new byte[] {8, 9}),
                ByteBuffer.wrap(new byte[] {10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20})
        );
        List<ByteBuffer> match3 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}),
                ByteBuffer.wrap(new byte[] {9, 10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20})
        );
        // nomatch0, nomatch10, nomatch19 differ from the previous
        // list at some index in [0..20[
        // nomatch0 mismatches at index 0
        List<ByteBuffer> nomatch0 = List.of(
                ByteBuffer.wrap(new byte[] {21, 2, 3}),
                ByteBuffer.wrap(new byte[] {4}),
                ByteBuffer.wrap(new byte[] {5, 6}),
                ByteBuffer.wrap(new byte[] {7, 8}),
                ByteBuffer.wrap(new byte[] {9}),
                ByteBuffer.wrap(new byte[] {10, 11, 12}),
                ByteBuffer.wrap(new byte[] {13, 14, 15, 16}),
                ByteBuffer.wrap(new byte[] {17, 18, 19, 20})
        );
        // nomatch10 mismatches at index 10
        List<ByteBuffer> nomatch10 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4, 5}),
                ByteBuffer.wrap(new byte[] {6}),
                ByteBuffer.wrap(new byte[] {7}),
                ByteBuffer.wrap(new byte[] {8, 9}),
                ByteBuffer.wrap(new byte[] {10, 31}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20})
        );
        // nomatch19 mismatches at index 19
        List<ByteBuffer> nomatch19 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}),
                ByteBuffer.wrap(new byte[] {9, 10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 40})
        );
        // morematch1 has one more byte at the end
        List<ByteBuffer> morematch1 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4}),
                ByteBuffer.wrap(new byte[] {5, 6}),
                ByteBuffer.wrap(new byte[] {7, 8}),
                ByteBuffer.wrap(new byte[] {9}),
                ByteBuffer.wrap(new byte[] {10, 11, 12}),
                ByteBuffer.wrap(new byte[] {13, 14, 15, 16}),
                ByteBuffer.wrap(new byte[] {17, 18, 19, 20, 41})
        );
        // morematch2 and morematch3 have the same 3 additional
        // bytes at the end
        List<ByteBuffer> morematch2 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                ByteBuffer.wrap(new byte[] {4, 5}),
                ByteBuffer.wrap(new byte[] {6}),
                ByteBuffer.wrap(new byte[] {7}),
                ByteBuffer.wrap(new byte[] {8, 9}),
                ByteBuffer.wrap(new byte[] {10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20}),
                ByteBuffer.wrap(new byte[] {41, 42, 43})
        );
        List<ByteBuffer> morematch3 = List.of(
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}),
                ByteBuffer.wrap(new byte[] {9, 10, 11}),
                ByteBuffer.wrap(new byte[] {12, 13, 14}),
                ByteBuffer.wrap(new byte[] {15}),
                ByteBuffer.wrap(new byte[] {16, 17}),
                ByteBuffer.wrap(new byte[] {18, 19, 20, 41, 42, 43})
        );

        assertEquals(Utils.mismatch(nomatch0, nomatch0), -1L);
        assertEquals(Utils.mismatch(nomatch10, nomatch10), -1L);
        assertEquals(Utils.mismatch(nomatch19, nomatch19), -1L);
        assertEquals(Utils.mismatch(morematch1, morematch1), -1L);
        assertEquals(Utils.mismatch(morematch2, morematch2), -1L);
        assertEquals(Utils.mismatch(morematch3, morematch3), -1L);
        assertEquals(Utils.mismatch(morematch2, morematch3), -1L);
        assertEquals(Utils.mismatch(morematch3, morematch2), -1L);

        for (var match : List.of(match1, match2, match3)) {
            assertEquals(Utils.mismatch(match, nomatch0), 0L);
            assertEquals(Utils.mismatch(match, nomatch10), 10L);
            assertEquals(Utils.mismatch(match, nomatch19), 19L);
            assertEquals(Utils.mismatch(nomatch0, match), 0L);
            assertEquals(Utils.mismatch(nomatch10, match), 10L);
            assertEquals(Utils.mismatch(nomatch19, match), 19L);
            for (var morematch : List.of(morematch1, morematch2, morematch3)) {
                assertEquals(Utils.mismatch(match, morematch), 20L);
                assertEquals(Utils.mismatch(morematch, match), 20L);
            }

        }
    }

}
