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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jdk.internal.net.http.quic.PeerConnectionId;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.frames.AckFrame.AckRange;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.DataBlockedFrame;
import jdk.internal.net.http.quic.frames.HandshakeDoneFrame;
import jdk.internal.net.http.quic.frames.MaxDataFrame;
import jdk.internal.net.http.quic.frames.MaxStreamDataFrame;
import jdk.internal.net.http.quic.frames.MaxStreamsFrame;
import jdk.internal.net.http.quic.frames.NewConnectionIDFrame;
import jdk.internal.net.http.quic.frames.NewTokenFrame;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.PathChallengeFrame;
import jdk.internal.net.http.quic.frames.PathResponseFrame;
import jdk.internal.net.http.quic.frames.PingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.ResetStreamFrame;
import jdk.internal.net.http.quic.frames.RetireConnectionIDFrame;
import jdk.internal.net.http.quic.frames.StopSendingFrame;
import jdk.internal.net.http.quic.frames.StreamDataBlockedFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;
import jdk.internal.net.http.quic.frames.StreamsBlockedFrame;
import jdk.internal.net.http.quic.packets.QuicPacketDecoder;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.http.quic.CodingContext;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicTransportParametersConsumer;
import jdk.test.lib.RandomFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.crypto.AEADBadTagException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static jdk.internal.net.http.quic.frames.QuicFrame.*;
import static jdk.internal.net.http.quic.frames.ConnectionCloseFrame.CONNECTION_CLOSE_VARIANT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @test
 * @summary tests the logic to decide whether a packet or
 *          a frame is ACK-eliciting.
 * @library /test/lib
 * @run testng AckElicitingTest
 * @run testng/othervm -Dseed=-7997973196290088038 AckElicitingTest
 */
public class AckElicitingTest {

    static final Random RANDOM = RandomFactory.getRandom();

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
        public void signRetryPacket(QuicVersion quicVersion,
                                    ByteBuffer originalConnectionId, ByteBuffer packet, ByteBuffer output) {
            throw new AssertionError("should not come here!");
        }
        @Override
        public void verifyRetryPacket(QuicVersion quicVersion,
                                      ByteBuffer originalConnectionId, ByteBuffer packet) throws AEADBadTagException {
            throw new AssertionError("should not come here!");
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
        public SSLSession getSession() { throw new AssertionError("should not come here!"); }

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
        public QuicTLSEngine getTLSEngine() {
            return TLS_ENGINE;
        }
    }

    static final int CIDLEN = RANDOM.nextInt(5, QuicConnectionId.MAX_CONNECTION_ID_LENGTH + 1);

    private static final TestCodingContext CONTEXT = new TestCodingContext() {

        @Override
        public long largestProcessedPN(PacketNumberSpace packetSpace) {
            return 0;
        }

        @Override
        public long largestAckedPN(PacketNumberSpace packetSpace) {
            return 0;
        }

        @Override
        public int connectionIdLength() {
            return CIDLEN;
        }

        @Override
        public QuicConnectionId originalServerConnId() {
            return null;
        }
    };

    /**
     * A record to store all the input of a given test case.
     * @param type a concrete frame type or packet type
     * @param describer a function to describe the {@code obj} instance
     *                  for tracing/diagnosis purposes
     * @param ackEliciting the function we want to test. This is either
     *                     {@link QuicFrame#isAckEliciting()
     *                     QuicFrame::isAckEliciting} or
     *                     {@link QuicPacket#isAckEliciting()
     *                     QuicPacket::isAckEliciting}
     * @param obj the instance on which to call the {@code ackEliciting}
     *            function.
     * @param expected the expected result of calling
     *                 {@code obj.ackEliciting()}
     * @param <T> A concrete subclass of {@link QuicFrame} or {@link QuicPacket}
     */
    static record TestCase<T>(Class<? extends T> type,
                       Function<T, String> describer,
                       Predicate<? super T> ackEliciting,
                       T obj,
                       boolean expected) {

        @Override
        public String toString() {
            // shorter & better toString than the default
            return "%s(%s)"
                    .formatted(type.getSimpleName(), describer.apply(obj));
        }

        private static String describeFrame(QuicFrame frame) {
            long type = frame.getTypeField();
            return HexFormat.of().toHexDigits((byte)type);
        }

        /**
         * Creates an instance of {@code TestCase} for a concrete frame type
         * @param type  the concrete frame class
         * @param frame the concrete instance
         * @param expected whether {@link QuicFrame#isAckEliciting()}
         *                 should return true for that instance.
         * @param <T> a concrete subclass of {@code QuicFrame}
         * @return a new instance of {@code TestCase}
         */
        public static <T extends QuicFrame> TestCase<T>
                    of(Class<? extends T> type, T frame, boolean expected) {
            return new TestCase<T>(type, TestCase::describeFrame,
                    QuicFrame::isAckEliciting, frame, expected);
        }

        /**
         * Creates an instance of {@code TestCase} for a concrete frame type
         * @param frame the concrete frame instance
         * @param expected whether {@link QuicFrame#isAckEliciting()}
         *                 should return true for that instance.
         * @param <T> a concrete subclass of {@code QuicFrame}
         * @return a new instance of {@code TestCase}
         */
        public static <T extends QuicFrame> TestCase<T> of(T frame, boolean expected) {
            return new TestCase<T>((Class<T>)frame.getClass(),
                    TestCase::describeFrame,
                    QuicFrame::isAckEliciting,
                    frame, expected);
        }

        /**
         * Creates an instance of {@code TestCase} for a concrete packet type
         * @param packet the concrete packet instance
         * @param expected whether {@link QuicPacket#isAckEliciting()}
         *                 should return true for that instance.
         * @param <T> a concrete subclass of {@code QuicPacket}
         * @return a new instance of {@code TestCase}
         */
        public static <T extends QuicPacket> TestCase<T> of(T packet, boolean expected) {
            return new TestCase<T>((Class<T>)packet.getClass(),
                    (p) -> p.frames().stream()
                            .map(Object::getClass)
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", ")),
                    QuicPacket::isAckEliciting,
                    packet, expected);
        }
    }

    // convenient alias to shorten lines in data providers
    private static <T extends QuicFrame> TestCase<T> of(T frame, boolean ackEliciting) {
        return TestCase.of(frame, ackEliciting);
    }

    // convenient alias to shorten lines in data providers
    private static <T extends QuicPacket> TestCase<T> of(T packet, boolean ackEliciting) {
        return TestCase.of(packet, ackEliciting);
    }

    /**
     * Create a new instance of the given frame type, populated with
     * dummy values.
     * @param frameClass the frame type
     * @param <T> a concrete subclass of {@code QuicFrame}
     * @return a new instance of the given concrete class.
     */
    <T extends QuicFrame> T newFrame(Class<T> frameClass) {
        var frameType = QuicFrame.frameTypeOf(frameClass);
        if (frameType == CONNECTION_CLOSE) {
            if (RANDOM.nextBoolean()) {
                frameType = CONNECTION_CLOSE_VARIANT;
            }
        }
        long streamId = 4;
        long largestAcknowledge = 3;
        long ackDelay = 20;
        long gap = 0;
        long range = largestAcknowledge;
        long offset = 0;
        boolean fin = false;
        int length = 10;
        long errorCode = 1;
        long finalSize = 10;
        int size = 3;
        long maxData = 10;
        boolean maxStreamsBidi = true;
        long maxStreams = 100;
        long maxStreamData = 10;
        long sequenceNumber = 4;
        long retirePriorTo = 3;
        String reason = "none";
        long errorFrameType = ACK;
        int pathChallengeLen = PathChallengeFrame.LENGTH;
        int pathResponseLen  = PathResponseFrame.LENGTH;
        var frame = switch (frameType) {
            case ACK -> new AckFrame(largestAcknowledge, ackDelay, List.of(new AckRange(gap, range)));
            case STREAM -> new StreamFrame(streamId, offset, length, fin, ByteBuffer.allocate(length));
            case RESET_STREAM -> new ResetStreamFrame(streamId, errorCode, finalSize);
            case PADDING -> new PaddingFrame(size);
            case PING -> new PingFrame();
            case STOP_SENDING -> new StopSendingFrame(streamId, errorCode);
            case CRYPTO -> new CryptoFrame(offset, length, ByteBuffer.allocate(length));
            case NEW_TOKEN -> new NewTokenFrame(ByteBuffer.allocate(length));
            case DATA_BLOCKED -> new DataBlockedFrame(maxData);
            case MAX_DATA -> new MaxDataFrame(maxData);
            case MAX_STREAMS -> new MaxStreamsFrame(maxStreamsBidi, maxStreams);
            case MAX_STREAM_DATA -> new MaxStreamDataFrame(streamId, maxStreamData);
            case STREAM_DATA_BLOCKED -> new StreamDataBlockedFrame(streamId, maxStreamData);
            case STREAMS_BLOCKED -> new StreamsBlockedFrame(maxStreamsBidi, maxStreams);
            case NEW_CONNECTION_ID -> new NewConnectionIDFrame(sequenceNumber, retirePriorTo,
                    ByteBuffer.allocate(length), ByteBuffer.allocate(16));
            case RETIRE_CONNECTION_ID -> new RetireConnectionIDFrame(sequenceNumber);
            case PATH_CHALLENGE -> new PathChallengeFrame(ByteBuffer.allocate(pathChallengeLen));
            case PATH_RESPONSE -> new PathResponseFrame(ByteBuffer.allocate(pathResponseLen));
            case CONNECTION_CLOSE -> new ConnectionCloseFrame(errorCode, errorFrameType, reason);
            case CONNECTION_CLOSE_VARIANT -> new ConnectionCloseFrame(errorCode, reason);
            case HANDSHAKE_DONE -> new HandshakeDoneFrame();
            default -> throw new IllegalArgumentException("Unrecognised frame");
        };
        return frameClass.cast(frame);
    }

    /**
     * Creates a list of {@code TestCase} to test all possible concrete
     * subclasses of {@code QuicFrame}.
     *
     * @return a list of {@code TestCase} to test all possible concrete
     *         subclasses of {@code QuicFrame}
     */
    public List<TestCase<? extends QuicFrame>> createFramesTests() {
        List<TestCase<? extends QuicFrame>> frames = new ArrayList<>();
        frames.add(of(newFrame(AckFrame.class), false));
        frames.add(of(newFrame(ConnectionCloseFrame.class), false));
        frames.add(of(newFrame(PaddingFrame.class), false));

        for (var frameType : QuicFrame.class.getPermittedSubclasses()) {
            if (frameType == AckFrame.class) continue;
            if (frameType == ConnectionCloseFrame.class) continue;
            if (frameType == PaddingFrame.class) continue;
            Class<? extends QuicFrame> quicFrameClass = (Class<? extends QuicFrame>)frameType;
            frames.add(of(newFrame(quicFrameClass), true));
        }

        return List.copyOf(frames);
    }

    /**
     * Creates a {@code QuicPacket} containing the given list of frames.
     * @param frames a list of frames
     * @return a new instance of {@code QuicPacket}
     */
    QuicPacket createPacket(List<QuicFrame> frames) {
        PacketType[] values = PacketType.values();
        int index = PacketType.NONE.ordinal();
        while (index == PacketType.NONE.ordinal()) {
            index = RANDOM.nextInt(0, values.length);
        }
        PacketType packetType = values[index];
        QuicPacketEncoder encoder = QuicPacketEncoder.of(QuicVersion.QUIC_V1);
        byte[] scid = new byte[CIDLEN];
        RANDOM.nextBytes(scid);
        byte[] dcid = new byte[CIDLEN];
        RANDOM.nextBytes(dcid);
        QuicConnectionId source = new PeerConnectionId(scid);
        QuicConnectionId dest  = new PeerConnectionId(dcid);
        long largestAckedPacket = CONTEXT.largestAckedPN(packetType);
        QuicPacket packet = switch (packetType) {
            case NONE -> throw new AssertionError("should not come here");
            // TODO: add more packet types
            default -> encoder.newInitialPacket(source, dest, null, largestAckedPacket + 1 ,
                    largestAckedPacket, frames, CONTEXT);
        };
        return packet;

    }

    /**
     * Creates a random instance of {@code QuicPacket} containing a
     * pseudo random list of concrete {@link QuicFrame} instances.
     * @param ackEliciting whether the returned packet should be
     *                     ack eliciting.
     * @return
     */
    QuicPacket createPacket(boolean ackEliciting) {
        List<QuicFrame> frames = new ArrayList<>();
        int mincount = ackEliciting ? 1 : 0;
        int ackCount = RANDOM.nextInt(mincount, 5);
        int nackCount = RANDOM.nextInt(0, 10);

        // TODO: maybe refactor this to make sure the frame
        //       we use are compatible with the packet type.
        List<Class<? extends QuicFrame>> noAckFrames = List.of(AckFrame.class,
                PaddingFrame.class, ConnectionCloseFrame.class);
        for (int i=0; i < nackCount ; i++) {
            frames.add(newFrame(noAckFrames.get(i % noAckFrames.size())));
        }
        if (ackEliciting) {
            // TODO: maybe refactor this to make sure the frame
            //       we use are compatible with the packet type.
            Class<?>[] frameClasses = QuicFrame.class.getPermittedSubclasses();
            for (int i=0; i < ackCount; i++) {
                Class<?> selected;
                do {
                    int fx = RANDOM.nextInt(0, frameClasses.length);
                    selected = frameClasses[fx];
                } while (noAckFrames.contains(selected));
                frames.add(newFrame((Class<? extends QuicFrame>) selected));
            }
        }
        if (!ackEliciting || RANDOM.nextBoolean()) {
            // if !ackEliciting we always shuffle.
            // Otherwise, we only shuffle half the time.
            Collections.shuffle(frames, RANDOM);
        }
        return createPacket(mergeConsecutivePaddingFrames(frames));
    }

    private List<QuicFrame> mergeConsecutivePaddingFrames(List<QuicFrame> frames) {
        var iterator = frames.listIterator();
        QuicFrame previous = null;

        while (iterator.hasNext()) {
            var frame = iterator.next();
            if (previous instanceof PaddingFrame prevPad
                    && frame instanceof PaddingFrame nextPad) {
                int previousIndex = iterator.previousIndex();
                QuicFrame merged = new PaddingFrame(prevPad.size() + nextPad.size());
                frames.set(previousIndex, merged);
                iterator.remove();
            } else {
                previous = frame;
            }
        }
        return frames;
    }

    /**
     * Creates a list of {@code TestCase} to test random instances of
     * {@code QuicPacket} containing random instances of {@link QuicFrame}
     * @return a list of {@code TestCase} to test random instances of
     *         {@code QuicPacket} containing random instances of {@link QuicFrame}
     */
    public List<TestCase<? extends QuicPacket>> createPacketsTests() {
        List<TestCase<? extends QuicPacket>> packets = new ArrayList<>();
        packets.add(of(createPacket(List.of(newFrame(AckFrame.class))), false));
        packets.add(of(createPacket(List.of(newFrame(ConnectionCloseFrame.class))), false));
        packets.add(of(createPacket(List.of(newFrame(PaddingFrame.class))), false));
        var frames = new ArrayList<>(List.of(
                newFrame(PaddingFrame.class),
                newFrame(AckFrame.class),
                newFrame(ConnectionCloseFrame.class)));
        Collections.shuffle(frames, RANDOM);
        packets.add(of(createPacket(List.copyOf(frames)), false));

        int maxPackets = RANDOM.nextInt(5, 11);
        for (int i = 0; i < maxPackets ; i++) {
            packets.add(of(createPacket(true), true));
        }
        return List.copyOf(packets);
    }


    /**
     * A provider of test case to test
     * {@link QuicFrame#isAckEliciting()}.
     * @return test case to test
     *        {@link QuicFrame#isAckEliciting()}
     */
    @DataProvider(name = "frames")
    public Object[][] framesDataProvider() {
        return createFramesTests().stream()
                .map(List::of)
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    /**
     * A provider of test case to test
     * {@link QuicPacket#isAckEliciting()}.
     * @return test case to test
     *        {@link QuicPacket#isAckEliciting()}
     */
    @DataProvider(name = "packets")
    public Object[][] packetsDataProvider() {
        return createPacketsTests().stream()
                .map(List::of)
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    /**
     * Verifies the behavior of {@link QuicFrame#isAckEliciting()}
     * with the given test case inputs.
     * @param test the test inputs
     * @param <T> a concrete subclass of QuicFrame
     */
    @Test(dataProvider = "frames")
    public <T extends QuicFrame> void testFrames(TestCase<T> test) {
        testAckEliciting(test.type(),
                test.describer(),
                test.ackEliciting(),
                test.obj(),
                test.expected());
    }

    /**
     * Verifies the behavior of {@link QuicPacket#isAckEliciting()}
     * with the given test case inputs.
     * @param test the test inputs
     * @param <T> a concrete subclass of QuickPacket
     */
    @Test(dataProvider = "packets")
    public <T extends QuicPacket> void testPackets(TestCase<T> test) {
        testAckEliciting(test.type(),
                test.describer(),
                test.ackEliciting(),
                test.obj(),
                test.expected());
    }


    /**
     * Asserts that {@code ackEliciting.test(obj) == expected}.
     * @param type          the concrete type of {@code obj}
     * @param describer     a function to describe {@code obj}
     * @param ackEliciting  the function being tested
     * @param obj           the instance on which to call the function being tested
     * @param expected      the expected result of {@code ackEliciting.test(obj)}
     * @param <T>           the concrete class being tested
     */
    private <T> void testAckEliciting(Class<? extends T> type,
                                      Function<? super T, String> describer,
                                      Predicate<? super T> ackEliciting,
                                      T obj,
                                      boolean expected) {
        System.out.printf("%ntestAckEliciting: %s(%s) - expecting %s%n",
                type.getSimpleName(),
                describer.apply(obj),
                expected);
        assertEquals(ackEliciting.test(obj), expected, describer.apply(obj));
        if (obj instanceof QuicFrame frame) {
            checkFrame(frame);
        } else if (obj instanceof QuicPacket packet) {
            checkPacket(packet);
        }
    }

    // This is not a full-fledged test for frame encoding/decoding.
    // Just a smoke test to verify that the ACK-eliciting property
    // survives encoding/decoding
    private void checkFrame(QuicFrame frame) {
        int size = frame.size();
        ByteBuffer buffer = ByteBuffer.allocate(size);
        System.out.println("Checking frame: " + frame.getClass());
        try {
            frame.encode(buffer);
            buffer.flip();
            var decoded = QuicFrame.decode(buffer);
            checkFrame(decoded, frame);
        } catch (QuicTransportException x) {
            throw new AssertionError(frame.getClass().getName(), x);
        }
    }

    // This is not a full-fledged test for frame equality:
    // And we still need a proper decoding/encoding test for frames
    private void checkFrame(QuicFrame decoded, QuicFrame expected) {
        System.out.printf("Comparing frames: %s with %s%n",
                decoded.getClass().getSimpleName(),
                expected.getClass().getSimpleName());
        assertEquals(decoded.getClass(), expected.getClass());
        assertEquals(decoded.size(), expected.size());
        assertEquals(decoded.getTypeField(), expected.getTypeField());
        assertEquals(decoded.isAckEliciting(), expected.isAckEliciting());
    }

    // This is not a full-fledged test for packet encoding/decoding.
    // Just a smoke test to verify that the ACK-eliciting property
    // survives encoding/decoding
    private void checkPacket(QuicPacket packet) {
        int size = packet.size();
        ByteBuffer buffer = ByteBuffer.allocate(size);
        System.out.println("Checking packet: " + packet.getClass());
        try {
            var encoder = QuicPacketEncoder.of(QuicVersion.QUIC_V1);
            var decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
            encoder.encode(packet, buffer, CONTEXT);
            buffer.flip();
            var decoded = decoder.decode(buffer, CONTEXT);
            assertEquals(decoded.size(), packet.size());
            assertEquals(decoded.packetType(), packet.packetType());
            assertEquals(decoded.payloadSize(), packet.payloadSize());
            assertEquals(decoded.isAckEliciting(), packet.isAckEliciting());
            var frames = packet.frames();
            var decodedFrames = decoded.frames();
            assertEquals(decodedFrames.size(), frames.size());
        } catch (Exception x) {
            throw new AssertionError(packet.getClass().getName(), x);
        }

    }
}
