/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.TestLoggerUtil;
import jdk.internal.net.http.common.TimeLine;
import jdk.internal.net.http.quic.CodingContext;
import jdk.internal.net.http.quic.PacketEmitter;
import jdk.internal.net.http.quic.PacketSpaceManager;
import jdk.internal.net.http.quic.PeerConnectionId;
import jdk.internal.net.http.quic.QuicCongestionController;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.QuicRttEstimator;
import jdk.internal.net.http.quic.QuicTimerQueue;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.frames.AckFrame.AckFrameBuilder;
import jdk.internal.net.http.quic.frames.AckFrame.AckRange;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.PingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.InitialPacket;
import jdk.internal.net.http.quic.packets.PacketSpace;
import jdk.internal.net.http.quic.packets.QuicPacketDecoder;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicTransportParametersConsumer;
import jdk.test.lib.RandomFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.crypto.AEADBadTagException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @test
 * @summary tests the logic to build an AckFrame
 * @library /test/lib
 * @library ../debug
 * @build java.net.http/jdk.internal.net.http.common.TestLoggerUtil
 * @run testng/othervm PacketSpaceManagerTest
 * @run testng/othervm -Dseed=-7947549564260911920 PacketSpaceManagerTest
 * @run testng/othervm -Dseed=-5413111674202728207 PacketSpaceManagerTest
 * @run testng/othervm -Dseed=-176652423987357212 PacketSpaceManagerTest
 * @run testng/othervm -Dseed=6550551791799910315 PacketSpaceManagerTest
 * @run testng/othervm -Dseed=-4159871071396382784 PacketSpaceManagerTest
 * @run testng/othervm -Dseed=2252276218459363615 PacketSpaceManagerTest
 * @run testng/othervm -Dseed=-5130588140709404919 PacketSpaceManagerTest
 */
// -Djdk.internal.httpclient.debug=true
public class PacketSpaceManagerTest {

    static final Random RANDOM = RandomFactory.getRandom();

    static final int CIDLEN = RANDOM.nextInt(5, QuicConnectionId.MAX_CONNECTION_ID_LENGTH + 1);

    private record PacketSpaces(PacketSpaceManager initial, PacketSpaceManager handshake, PacketSpaceManager app) {
        PacketSpaceManager get(PacketNumberSpace packetNumberSpace) {
            PacketSpaceManager result = switch (packetNumberSpace) {
                case INITIAL -> initial;
                case HANDSHAKE -> handshake;
                case APPLICATION -> app;
                case NONE -> throw new AssertionError("invalid number space: " + packetNumberSpace);
            };
            if (result == null) {
                throw new AssertionError("invalid number space: " + packetNumberSpace);
            }
            return result;
        }
    }

    private static class DummyQuicTLSEngine implements QuicTLSEngine {
        @Override
        public HandshakeState getHandshakeState() {
            throw new AssertionError("should not come here!");
        }

        @Override
        public boolean isTLSHandshakeComplete() {
            return true;
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
                                  IntFunction<ByteBuffer> headerGenerator,
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
            throw new AssertionError("should not come here!");
        }
        @Override
        public void verifyRetryPacket(QuicVersion version,
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
        public void setUseClientMode(boolean mode) {
            throw new AssertionError("should not come here!");
        }

        @Override
        public boolean getUseClientMode() {
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
        public SSLSession getHandshakeSession() {
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
    private static class TestCodingContext implements CodingContext {
        final QuicPacketEncoder encoder;
        final QuicPacketDecoder decoder;
        final PacketSpaces spaces;
        TestCodingContext(PacketSpaces spaces) {
            this.spaces = spaces;
            this.encoder = QuicPacketEncoder.of(QuicVersion.QUIC_V1);
            this.decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        }

        @Override
        public long largestProcessedPN(PacketNumberSpace packetNumberSpace) {
            return spaces.get(packetNumberSpace).getLargestProcessedPN();
        }

        @Override
        public long largestAckedPN(PacketNumberSpace packetNumberSpace) {
            return spaces.get(packetNumberSpace).getLargestPeerAckedPN();
        }

        @Override
        public int connectionIdLength() {
            return CIDLEN;
        }

        @Override
        public int writePacket(QuicPacket packet, ByteBuffer buffer)
                throws QuicKeyUnavailableException, QuicTransportException {
            int pos = buffer.position();
            encoder.encode(packet, buffer, this);
            return buffer.position() - pos;
        }
        @Override
        public QuicPacket parsePacket(ByteBuffer src)
                throws IOException, QuicKeyUnavailableException, QuicTransportException {
            return decoder.decode(src, this);
        }
        @Override
        public boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
            return true;
        }
        @Override
        public QuicConnectionId originalServerConnId() {
            return null;
        }

        @Override
        public QuicTLSEngine getTLSEngine() {
            return TLS_ENGINE;
        }
    }

    /**
     * An acknowledgement range, where acknowledged packets are [first..last].
     * For instance [9,9] acknowledges only packet 9.
     * @param first the first packet acknowledged, inclusive
     * @param last  the last packet acknowledged, inclusive
     */
    public static record Acknowledged(long first, long last) {
        public Acknowledged {
            assert first >= 0 && first <= last;
        }
        public boolean contains(long packet) {
            return first <= packet && last >= packet;
        }
        public static List<Acknowledged> of(long... numbers) {
            if (numbers == null || numbers.length == 0) return List.of();
            if (numbers.length % 2 != 0) throw new IllegalArgumentException();
            List<Acknowledged> res = new ArrayList<>(numbers.length/2);
            for (int i = 0; i < numbers.length; i += 2) {
                res.add(new Acknowledged(numbers[i], numbers[i+1]));
            }
            return List.copyOf(res);
        }
    }

    /**
     * A packet to be emitted, followed by a pouse of {@code delay} in milliseconds.
     * @param packetNumber the packet number of the packet to send
     * @param delay a delay before the next packet should be emitted
     */
    public static record Packet(long packetNumber, long delay) {
        Packet(long packetNumber) {
            this(packetNumber, RANDOM.nextLong(1, 255));
        }
        static List<Packet> ofAcks(List<Acknowledged> acks) {
            return packets(acks);
        }
        static List<Packet> of(long... numbers) {
            return LongStream.of(numbers).mapToObj(Packet::new).toList();
        }
        static final Comparator<Packet> COMPARE_NUMBERS = Comparator.comparingLong(Packet::packetNumber);
    }

    /**
     * A test case. Composed of a list of acknowledgements, a list of packets,
     * and a list of AckFrames. The list of packets is built from the list of
     * acknowledgement - that is - every packet emitted should eventually be
     * acknowledged. The list of AckFrame is built from the list of Packets,
     * by randomly selecting a few consecutive packets in the packet list
     * to acknowledge. The list of AckFrame is sorted by increasing
     * largestAcknowledged. The list of Packets can be shuffled, which
     * result on having AckFrames with gaps.
     * @param acks A list of acknowledgement ranges
     * @param packets A list of packets generated from the acknowledgement ranges.
     *                The list can be shuffled.
     * @param ackframes A list of AckFrames, derived from the possibly shuffled
     *                  list of packets. The list of AckFrame is sorted by increasing
     *                  largestAcknowledged (since a packet can't be acknowledged
     *                  before it's been emitted).
     * @param shuffled whether the list of packets is shuffled.
     */
    public static record TestCase(List<Acknowledged> acks,
                                  List<Packet> packets,
                                  List<AckFrame> ackframes,
                                  boolean shuffled) {
        public TestCase(List<Acknowledged> acks, List<Packet> packets) {
            this(acks, packets, ackFrames(packets),false);
        }
        public TestCase(List<Acknowledged> acks, List<Packet> packets, boolean shuffled) {
            this(acks, packets, ackFrames(packets), shuffled);
        }
        public TestCase(List<Acknowledged> acks) {
            this(acks, Packet.ofAcks(acks));
        }
        public TestCase shuffle() {
            List<Packet> shuffled = new ArrayList<>(packets);
            Collections.shuffle(shuffled, RANDOM);
            return new TestCase(acks, List.copyOf(shuffled), true);
        }
    }

    /**
     * Construct a list of AckFrames from the possibly shuffled list
     * of Packets.
     * @param packets a list of packets
     * @return a sorted list of AckFrames
     */
    private static List<AckFrame> ackFrames(List<Packet> packets) {
        List<AckFrame> result = new ArrayList<>();
        int remaining = packets.size();
        int i = 0;
        while (remaining > 0) {
            int ackCount = Math.min(RANDOM.nextInt(1, 5), remaining);
            AckFrameBuilder builder = new AckFrameBuilder();
            for (int j=0; j < ackCount; j++) {
                builder.addAck(packets.get(i + j).packetNumber);
            }
            result.add(builder.build());
            i += ackCount;
            remaining -= ackCount;
        }
        result.sort(Comparator.comparingLong(AckFrame::largestAcknowledged));
        return List.copyOf(result);
    }

    /**
     * Generates test cases - by concatenating a list of simple test case,
     * a list of special testcases, and a list of random testcases.
     * @return A list of TestCases to test.
     */
    List<TestCase> generateTests() {
        List<TestCase> tests = new ArrayList<>();
        List<TestCase> simples = List.of(
                new TestCase(List.of(new Acknowledged(5,5))),
                new TestCase(List.of(new Acknowledged(5,7))),
                new TestCase(List.of(new Acknowledged(3, 5), new Acknowledged(7,9))),
                new TestCase(List.of(new Acknowledged(3, 5), new Acknowledged(7,7))),
                new TestCase(List.of(new Acknowledged(3,3), new Acknowledged(5,7)))
                );
        tests.addAll(simples);
        List<TestCase> specials = List.of(
                new TestCase(Acknowledged.of(5,5,7,7), Packet.of(5,7), false),
                new TestCase(Acknowledged.of(5,7), Packet.of(5,7,6), true),
                new TestCase(Acknowledged.of(6,7), Packet.of(6,7), false),
                new TestCase(Acknowledged.of(5,7), Packet.of(6,7,5), true),
                new TestCase(Acknowledged.of(5,7), Packet.of(5,6,7), true),
                new TestCase(Acknowledged.of(5,5,7,8), Packet.of(5, 7, 8), true),
                new TestCase(Acknowledged.of(5,5,8,8), Packet.of(8, 5), true),
                new TestCase(Acknowledged.of(5,5,7,8), Packet.of(8, 5, 7), true),
                new TestCase(Acknowledged.of(3,5,7,9), Packet.of(8,5,7,4,9,3), true),
                new TestCase(Acknowledged.of(27,27,31,31),
                        Packet.of(27, 31), true),
                new TestCase(Acknowledged.of(27,27,29,29,31,31),
                        Packet.of(27, 31, 29), true),
                new TestCase(Acknowledged.of(3,5,7,7,9,9,22,22,27,27,29,29,31,31),
                        Packet.of(4,22,27,31,9,29,7,5,3), true)
             );
        tests.addAll(specials);
        for (int i=0; i < 5; i++) {
            List<Acknowledged> acks = generateAcks();
            List<Packet> packets = packets(acks);
            TestCase test = new TestCase(acks, List.copyOf(packets), false);
            tests.add(test);
            for (int j = 0; j < 5; j++) {
                tests.add(test.shuffle());
            }
        }
        return tests;
    }

    /**
     * Generate a random list of increasing acknowledgement ranges.
     * A packet should only be present once.
     * @return a random list of increasing acknowledgement ranges.
     */
    List<Acknowledged> generateAcks() {
        int count = RANDOM.nextInt(3, 10);
        List<Acknowledged> acks = new ArrayList<>(count);
        long prev = -1;
        for (int i=0; i<count; i++) {
            long first = prev + RANDOM.nextInt(1, 15);
            long next  = first + RANDOM.nextInt(0, 15);
            acks.add(new Acknowledged(first, next));
            prev = next + 1;
        }
        return List.copyOf(acks);
    }

    /**
     * Creates a Packet for each number acknowledged by the list
     * of acknowledgement ranges.
     * @param acks a list of acknowledgement ranges
     * @return a list of packets
     */
    static List<Packet> packets(List<Acknowledged> acks) {
        List<Packet> res = new ArrayList<>();
        for (Acknowledged ack : acks) {
            for (long i = ack.first() ; i<= ack.last() ; i++) {
                var packet = new Packet(i);
                assert !res.contains(packet);
                res.add(packet);
            }
        }
        return res;
    }

    @DataProvider(name = "tests")
    public Object[][] tests() {
        return generateTests().stream()
                .map(List::of)
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    // TODO:
    //     1. TestCase should have an ordered list of packets.
    //     2. packets will be emitted in order - that is -
    //        PacketSpaceManager::packetSent will be called for each packet
    //        in order.
    //     3. acknowledgements of packet should arrive in random order.
    //        a selection of packets should be acknowledged in bunch...
    //        However, a packet shouldn't be acknowledged before it is emitted.
    //     4. some packets should not be acknowledged in time, causing
    //        them to be retransmitted.
    //     5. all of retransmitted packets should eventually be acknowledged,
    //        but which packet number (among the list of numbers under which
    //        a packet is retransmitted should be random).
    //     6. packets that are acknowledged should no longer be retransmitted
    //     7. code an AsynchronousTestDriver that uses the EXECUTOR
    //        this is more difficult as it makes it more difficult to guess
    //        at when exactly a packet will be retransmitted...

    /**
     * A synchronous test driver to drive a TestCase.
     * The method {@link #run()} drives the test.
     */
    static class SynchronousTestDriver implements PacketEmitter {
        final TestCase test;
        final long timeline;
        final QuicTimerQueue timerQueue;
        final PriorityBlockingQueue<Packet> packetQueue;
        final PriorityBlockingQueue<AckFrame> framesQueue;
        final PacketSpaceManager manager;
        final PacketNumberSpace space;
        final Executor executor = this::execute;
        final Logger debug = TestLoggerUtil.getErrOutLogger(this::toString);
        final TestCodingContext codingContext;
        final ConcurrentLinkedQueue<QuicPacket> emittedAckPackets;
        final QuicRttEstimator rttEstimator;
        final QuicCongestionController congestionController;
        final QuicConnectionId localId;
        final QuicConnectionId peerId;
        final TimeSource timeSource = new TimeSource();
        final long maxPacketNumber;
        final AckFrameBuilder allAcks = new AckFrameBuilder();

        SynchronousTestDriver(TestCase test) {
            this.space = PacketNumberSpace.INITIAL;
            this.test = test;

            localId = newId();
            peerId = newId();
            timeline = test.packets().stream()
                    .mapToLong(Packet::delay)
                    .reduce(0, Math::addExact);
            timerQueue = new QuicTimerQueue(this::notifyQueue, debug);
            packetQueue = new PriorityBlockingQueue<>(test.packets.size(), Packet.COMPARE_NUMBERS);
            packetQueue.addAll(test.packets);
            framesQueue = new PriorityBlockingQueue<>(test.ackframes.size(),
                    Comparator.comparingLong(AckFrame::largestAcknowledged));
            framesQueue.addAll(test.ackframes);
            emittedAckPackets = new ConcurrentLinkedQueue<>();
            rttEstimator = new QuicRttEstimator() {
                @Override
                public synchronized Duration getLossThreshold() {
                    return Duration.ofMillis(250);
                }

                @Override
                public synchronized Duration getBasePtoDuration() {
                    return Duration.ofMillis(250);
                }
            };
            congestionController = new QuicCongestionController() {
                @Override
                public boolean canSendPacket() {
                    return true;
                }
                @Override
                public void updateMaxDatagramSize(int newSize) { }
                @Override
                public void packetSent(int packetBytes) { }
                @Override
                public void packetAcked(int packetBytes, Deadline sentTime) { }
                @Override
                public void packetLost(Collection<QuicPacket> lostPackets, Deadline sentTime, boolean persistent) { }
                @Override
                public void packetDiscarded(Collection<QuicPacket> discardedPackets) { }
                @Override
                public long congestionWindow() {
                    return Integer.MAX_VALUE;
                }
                @Override
                public long initialWindow() {
                    return Integer.MAX_VALUE;
                }
                @Override
                public long maxDatagramSize() {
                    return 1200;
                }
                @Override
                public boolean isSlowStart() {
                    return false;
                }
                @Override
                public void updatePacer(Deadline now) { }
                @Override
                public boolean isPacerLimited() {
                    return false;
                }
                @Override
                public boolean isCwndLimited() {
                    return false;
                }
                @Override
                public Deadline pacerDeadline() {
                    return Deadline.MIN;
                }
                @Override
                public void appLimited() { }
            };
            manager = new PacketSpaceManager(space, this, timeSource,
                    rttEstimator, congestionController, new DummyQuicTLSEngine(),
                    this::toString);
            maxPacketNumber = test.packets().stream().mapToLong(Packet::packetNumber)
                    .max().getAsLong();
            manager.getNextPN().set(maxPacketNumber + 1);
            codingContext = new TestCodingContext(new PacketSpaces(manager, null, null));
        }

        static class TimeSource implements TimeLine {
            final Deadline first = jdk.internal.net.http.common.TimeSource.now();
            volatile Deadline current = first;
            public synchronized Deadline advance(long duration, TemporalUnit unit) {
                return current = current.plus(duration, unit);
            }
            public Deadline advanceMillis(long millis) {
                return advance(millis, ChronoUnit.MILLIS);
            }
            @Override
            public Deadline instant() {
                return current;
            }
        }

        void notifyQueue() {
            timerQueue.processEventsAndReturnNextDeadline(now(), executor);
        }

        @Override
        public QuicTimerQueue timer() { return timerQueue;}

        @Override
        public void retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts) {
            if (!(packet instanceof InitialPacket initial))
                throw new AssertionError("unexpected packet type: " + packet);
            long newPacketNumber = packetSpaceManager.allocateNextPN();
            debug.log("Retransmitting packet %d as %d (%d attempts)",
                    packet.packetNumber(), newPacketNumber, attempts);
            assert attempts >= 0;
            QuicPacket newPacket = codingContext.encoder
                    .newInitialPacket(initial.sourceId(), initial.destinationId(),
                    initial.token(), newPacketNumber,
                    packetSpaceManager.getLargestPeerAckedPN(),
                    initial.frames(), codingContext);
            long number = initial.packetNumber();
            Deadline now = now();
            manager.packetSent(newPacket, number, newPacket.packetNumber());
            retransmissions.add(new Retransmission(initial.packetNumber(), now,
                    AckFrame.largestAcknowledgedInPacket(newPacket)));
            expectedRetransmissions.stream()
                    .filter(r -> r.isFor(number))
                    .forEach(r -> {
                assertTrue(r.isDue(now) ||
                                packetSpaceManager.getLargestPeerAckedPN() - 3 > number,
                        "retransmitted packet %d is not yet due".formatted(number));
                successfulExpectations.add(r);
            });
            boolean removed = expectedRetransmissions.removeIf(r -> r.isFor(number));
            if (number <= maxPacketNumber) {
                assertTrue(removed, "retransmission of packet %d was not expected"
                        .formatted(number));
            }
        }

        @Override
        public long emitAckPacket(PacketSpace packetSpaceManager,
                                  AckFrame ackFrame, boolean sendPing) {
            long newPacketNumber = packetSpaceManager.allocateNextPN();
            debug.log("Emitting ack packet %d for %s (sendPing: %s)",
                    newPacketNumber, ackFrame, sendPing);
            List<QuicFrame> frames;
            if (ackFrame != null) {
                frames = sendPing
                        ? List.of(new PingFrame(), ackFrame)
                        : List.of(ackFrame);
            } else {
                assert sendPing;
                frames = List.of(new PingFrame());
            }
            QuicPacket newPacket = codingContext.encoder
                    .newInitialPacket(localId, peerId,
                            null, newPacketNumber,
                            packetSpaceManager.getLargestPeerAckedPN(),
                            frames, codingContext);
            packetSpaceManager.packetSent(newPacket, -1, newPacketNumber);
            emittedAckPackets.offer(newPacket);
            return newPacket.packetNumber();
        }

        @Override
        public void acknowledged(QuicPacket packet) {
            // TODO: nothing to do?
        }

        @Override
        public boolean sendData(PacketNumberSpace packetNumberSpace) {
            return false;
        }

        @Override
        public Executor executor() {
            return this::execute;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void checkAbort(PacketNumberSpace packetNumberSpace) { }

        final CopyOnWriteArrayList<Retransmission> expectedRetransmissions = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Retransmission> retransmissions = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Retransmission> successfulExpectations = new CopyOnWriteArrayList<>();

        static record Retransmission(long packetNumber, Deadline atOrAfter, long largestAckSent) {
            boolean isFor(long number) {
                return number == packetNumber;
            }
            boolean isDue(Deadline now) {
                return !atOrAfter.isAfter(now);
            }
        }

        /**
         * Drives the test by pretending to emit each packet in order,
         * then pretending to receive ack frames (as soon as possible
         * given the largest packet number emitted).
         * The timeline is advanced by chunks as instructed by
         * the test.
         * This method checks that the retransmission logic works as
         * expected.
         * @throws Exception
         */
        // TODO: in the end we need to check that everything that was
        //       expected to happen happened. What is missing is to
        //       check the generation of ACK packets... Also a
        //       retransmitted packet may need to itself retransmitted
        //       again and we have no test for that.
        public void run() throws Exception {
            long timeline = 0;
            long serverPacketNumbers = 0;
            long maxAck;
            Packet packet;
            debug.log("Packets: %s", test.packets.stream().mapToLong(Packet::packetNumber)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(", ", "[", "]")));
            debug.log("Frames: %s", test.ackframes.stream().mapToLong(AckFrame::largestAcknowledged)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(", ", "[", "]")));
            long maxRetransmissionDelay = 250;
            long maxAckDelay = manager.getMaxAckDelay();

            Deadline start = now();
            Deadline nextSendAckDeadline = Deadline.MAX;
            long firstAckPaket = -1, lastAckPacket = -1;
            long largestAckAcked = -1;
            boolean previousAckEliciting = false;
            // simulate sending each packet, ordered by their packet number
            while ((packet = packetQueue.poll()) != null) {
                long offset = packet.packetNumber;
                AckFrame nextAck = framesQueue.peek();
                maxAck = nextAck == null ? Long.MAX_VALUE : nextAck.largestAcknowledged();
                long largestReceivedAckedPN = manager.getLargestPeerAckedPN();
                debug.log("timeline: at %dms", timeline);
                debug.log("sending packet: %d, largest ACK received: %d",
                        packet.packetNumber, largestReceivedAckedPN);

                // randomly decide whether we should attempt to include an ack frame
                // with the next packet we send out...
                boolean sendAck = RANDOM.nextBoolean();
                AckFrame ackFrameToSend = sendAck ? manager.getNextAckFrame(false) : null;
                long largestAckSent = -1;
                if (ackFrameToSend != null) {
                    previousAckEliciting = false;
                    debug.log("including ACK frame: " + ackFrameToSend);
                    nextSendAckDeadline = Deadline.MAX;
                    // assertFalse used on purpose here to make
                    // sure the stack trace can't be confused with one that
                    // originate in another similar lambda that use assertTrue below.
                    LongStream.range(firstAckPaket, lastAckPacket + 1).sequential()
                            .forEach(p -> assertFalse(!ackFrameToSend.isAcknowledging(p),
                                    "frame %s should acknowledge %d"
                                            .formatted(ackFrameToSend, p)));
                    largestAckSent = ackFrameToSend.largestAcknowledged();
                    debug.log("largestAckSent is: " + largestAckAcked);
                }

                // add a crypto frame and build the packet
                CryptoFrame crypto = new CryptoFrame(offset, 1,
                        ByteBuffer.wrap(new byte[] {nextByte(offset)}));
                List<QuicFrame> frames = ackFrameToSend == null ?
                        List.of(crypto) : List.of(crypto, ackFrameToSend);
                QuicPacket newPacket = codingContext.encoder
                        .newInitialPacket(localId, peerId,
                                null,
                                packet.packetNumber,
                                largestReceivedAckedPN,
                                frames, codingContext);
                // pretend that we sent a packet
                manager.packetSent(newPacket, -1, packet.packetNumber);

                // compute next deadline
                var nextDeadline = timerQueue.nextDeadline();
                var nextScheduledDeadline = manager.nextScheduledDeadline();
                var nextComputedDeadline = manager.computeNextDeadline();
                var now = now();
                debugDeadline("nextDeadline", start, now, nextDeadline);
                debugDeadline("nextScheduledDeadline", start, now,  nextScheduledDeadline);
                debugDeadline("nextComputedDeadline", start, now, nextComputedDeadline);
                assertFalse(nextDeadline.isAfter(now.plusMillis(maxRetransmissionDelay * rttEstimator.getPtoBackoff())),
                    "nextDeadline should not be after %dms from now!"
                            .formatted(maxRetransmissionDelay));
                expectedRetransmissions.add(new Retransmission(packet.packetNumber,
                        now.plus(maxRetransmissionDelay, ChronoUnit.MILLIS), largestAckSent));

                List<Long> pending = manager.pendingAcknowledgements((s) ->s.boxed().toList());
                debug.log("pending ack: %s", pending);
                assertContains(Assertion.TRUE, pending, packet.packetNumber,
                        "pending ack");

                pending = manager.pendingRetransmission((s) ->s.boxed().toList());
                debug.log("pending retransmission: %s", pending);
                assertContains(Assertion.FALSE, pending, packet.packetNumber,
                        "pending retransmission");

                pending = manager.triggeredForRetransmission((s) ->s.boxed().toList());
                debug.log("triggered for retransmission: %s", pending);
                assertContains(Assertion.FALSE, pending, packet.packetNumber,
                        "triggered for retransmission");

                if (!nextDeadline.isAfter(now) || !nextSendAckDeadline.isAfter(now)) {
                    var nextI = timerQueue
                            .processEventsAndReturnNextDeadline(now, executor);
                    // this might have triggered sending an ACK packet, unless we
                    // already sent the ack with the initial packet just above.
                    debugDeadline("new deadline after events", start, now, nextI);
                }

                // check generated ack packets, if any should have been generated...
                if (!nextSendAckDeadline.isAfter(now)) {
                    debug.log("checking emitted ack packets: emitted %d", emittedAckPackets.size());
                    var ackPacket = emittedAckPackets.poll();
                    assertNotNull(ackPacket);
                    List<AckFrame> ackFrames = ackPacket.frames()
                            .stream().filter(AckFrame.class::isInstance)
                            .map(AckFrame.class::cast)
                            .toList();
                    assertEquals(frames.size(), 1,
                            "unexpected ack frames: " + frames);
                    AckFrame ackFrame = ackFrames.get(0);
                    LongStream.range(firstAckPaket, lastAckPacket + 1)
                            .forEach(p -> assertTrue(ackFrame.isAcknowledging(p),
                                    "frame %s should acknowledge %d"
                                            .formatted(ackFrame, p)));
                    assertNull(emittedAckPackets.peek(),
                            "emitted ackPacket queue not empty: " + emittedAckPackets);
                    debug.log("Got expected ackFrame for emitted ack packet: %s", ackFrame);
                    previousAckEliciting = false;
                    nextSendAckDeadline = Deadline.MAX;
                }

                // advance the timeline by the instructed delay...
                Deadline next = timeSource.advanceMillis(packet.delay);
                timeline = timeline + packet.delay;
                debug.log("advance deadline by %dms at %dms", packet.delay, timeline);
                // note: beyond this point now > now(); packets between now and now()
                //  may not be retransmitted yet

                // Do not pretend to receive acknowledgement for
                // packets that we haven't sent yet.
                if (packet.packetNumber >= maxAck) {
                    // pretend to be receiving the next ack frame...
                    nextAck = framesQueue.poll();
                    debug.log("Receiving acks for " + nextAck);
                    long spn = serverPacketNumbers++;
                    boolean isAckEliciting = RANDOM.nextBoolean();
                    manager.packetReceived(PacketType.INITIAL, spn, isAckEliciting);

                    // calculate if and when we should send out the ack frame for
                    // the ACK packet we just received
                    if (firstAckPaket == -1) firstAckPaket = spn;
                    lastAckPacket = spn;
                    debug.log("next sent ack should acknowledge [%d..%d]",
                            firstAckPaket, lastAckPacket);
                    if (isAckEliciting) {
                        debug.log("prevEliciting: %s",
                                previousAckEliciting);
                        if (previousAckEliciting) {
                            nextSendAckDeadline = min(now, nextSendAckDeadline);
                        } else {
                            nextSendAckDeadline = min(nextSendAckDeadline, next.plusMillis(maxAckDelay));
                        }
                        debugDeadline("next ack deadline", start, next, nextSendAckDeadline);
                    }
                    previousAckEliciting |= isAckEliciting;

                    // process the ack frame we just received
                    assertNotNull(nextAck);
                    manager.processAckFrame(nextAck);
                    firstAckPaket = Math.max(firstAckPaket, manager.getMinPNThreshold() + 1);

                    // Here we can compute which packets will not be acknowledged yet,
                    // and which packets will be retransmitted.
                    long largestAckAckedBefore = largestAckAcked;
                    for (long number : acknowledgePackets(nextAck)) {
                        allAcks.addAck(number);
                        pending = manager.pendingAcknowledgements((s) -> s.boxed().toList());
                        assertContains(Assertion.FALSE, pending, number,
                                "pending ack");

                        pending = manager.pendingRetransmission((s) -> s.boxed().toList());
                        assertContains(Assertion.FALSE, pending, number,
                                "pending retransmission");

                        pending = manager.triggeredForRetransmission((s) -> s.boxed().toList());
                        assertContains(Assertion.FALSE, pending, number,
                                "triggered for retransmission");
                        // TODO check if we only retransmitted the expected packets
                        // ...need to replicate the logic

                        /*expectedRetransmissions.stream()
                                .filter(r -> r.isFor(number))
                                .forEach(r -> assertFalse(r.isDue(now),
                                    "due packet %d was not retransmitted".formatted(number)));
                        for (Retransmission r : expectedRetransmissions) {
                            if (r.isFor(number)) {
                                largestAckAcked = Math.max(largestAckAcked, r.largestAckSent);
                            }
                        }*/
                        for (Retransmission r : successfulExpectations) {
                            if (r.isFor(number)) {
                                largestAckAcked = Math.max(largestAckAcked, r.largestAckSent);
                            }
                        }
                        if (largestAckAcked != largestAckAckedBefore) {
                            debug.log("largestAckAcked is now %d", largestAckAcked);
                            largestAckAckedBefore = largestAckAcked;
                        }
                        if (largestAckAcked > -1) {
                            boolean changed = false;
                            if (firstAckPaket <= largestAckAcked) {
                                changed = true;
                                if (lastAckPacket > largestAckAcked) {
                                    firstAckPaket = largestAckAcked + 1;
                                } else firstAckPaket = -1;
                            }
                            if (lastAckPacket <= largestAckAcked) {
                                changed = true;
                                lastAckPacket = -1;
                            }
                            if (changed) {
                                debug.log("next sent ack should now be [%d..%d]",
                                        firstAckPaket, lastAckPacket);
                            }
                        }
                        boolean removed = expectedRetransmissions.removeIf(r -> r.isFor(number));
                        successfulExpectations.stream()
                                .filter(r -> r.isFor(number))
                                .forEach( r -> assertTrue(r.isDue(now()) ||
                                                manager.getLargestPeerAckedPN() - 3 > r.packetNumber,
                                    "packet %d was retransmitted too early (deadline was %d)"
                                        .formatted(number, start
                                                .until(r.atOrAfter, ChronoUnit.MILLIS))));
                        retransmissions.stream().filter(r -> r.isFor(number)).forEach( r -> {
                            assertTrue(r.isDue(now()),
                                    "packet %d was retransmitted too early at %d"
                                            .formatted(number, start
                                                    .until(r.atOrAfter, ChronoUnit.MILLIS)));
                            assertFalse(removed, "packet %d was in both lists"
                                    .formatted(number));
                        });
                    }
                }
            }
        }

        Deadline min(Deadline one, Deadline two) {
            return one.isAfter(two) ? two : one;
        }

        /**
         * Should be called after {@link #run()}.
         */
        void check() {
            assertFalse(now().isBefore(timeSource.first.plusMillis(timeline)));
            assertTrue(expectedRetransmissions.isEmpty());
            assertEquals(retransmissions.stream()
                            .map(Retransmission::packetNumber)
                            .filter(pn -> pn <= maxPacketNumber).toList(),
                    successfulExpectations.stream().map(Retransmission::packetNumber)
                            .filter(pn -> pn <= maxPacketNumber).toList());
            for (Retransmission r : retransmissions) {
                if (r.packetNumber > maxPacketNumber ||
                        manager.getLargestPeerAckedPN() - 3 > r.packetNumber) continue;
                List<Retransmission> succesful = successfulExpectations.stream()
                        .filter(s -> s.isFor(r.packetNumber))
                        .toList();
                assertEquals(succesful.size(), 1);
                succesful.forEach(s -> assertFalse(s.atOrAfter.isAfter(r.atOrAfter)));
            }

            List<Long> acknowledged = new ArrayList<>(acknowledgePackets(allAcks.build()));
            Collections.sort(acknowledged);
            assertEquals(acknowledged, test.packets.stream()
                    .map(Packet::packetNumber).sorted().toList());
        }

        // TODO: add a LongStream acknowledged() to AckFrame - write a spliterator
        //       for that.
        List<Long> acknowledgePackets(AckFrame frame) {
            List<Long> list = new ArrayList<>();
            long largest = frame.largestAcknowledged();
            long smallest = largest + 2;
            for (AckRange range : frame.ackRanges()) {
                largest = smallest - range.gap() -2;
                smallest = largest - range.range();
                for (long i = largest; i >= smallest; i--) {
                    assert frame.isAcknowledging(i)
                            : "%s is not acknowledging %d".formatted(frame, i);
                    list.add(i);
                }
            }
            return list;
        }

        interface Assertion {
            void check(boolean result, String message);
            default String negation() {
                return (this == FALSE) ? "doesn't " : "";
            }
            Assertion TRUE = Assert::assertTrue;
            Assertion FALSE = Assert::assertFalse;
        }
        static void assertContains(Assertion assertion, List<Long> list, long number, String desc) {
            assertion.check(list.contains(number),
                    "%s: %s %scontains %d".formatted(desc, list, assertion.negation(), number));
        }

        void debugDeadline(String desc, Deadline start, Deadline now, Deadline deadline) {
            long nextMs = deadline.equals(Deadline.MAX) ? 0 :
                    now.until(deadline, ChronoUnit.MILLIS);
            long at = deadline.equals(Deadline.MAX) ? 0 :
                    start.until(deadline, ChronoUnit.MILLIS);
            String when = deadline.equals(Deadline.MAX) ? "never"
                    : nextMs >= 0 ? ("at %d in %dms".formatted(at, nextMs))
                    : ("at %d due by %dms".formatted(at, (-nextMs)));
            debug.log("%s: %s", desc, when);
        }

        byte nextByte(long offset) {
            long start = 'a';
            int len = 'z' - 'a' + 1;
            long res = start + offset % len;
            assert res >= 'a' && res <= 'z';
            return (byte) res;
        }

        private void execute(Runnable runnable) {
            runnable.run();
        }

        Deadline now() {
            return timeSource.instant();
        }

        static QuicConnectionId newId() {
            byte[] idbites = new byte[CIDLEN];
            RANDOM.nextBytes(idbites);
            return new PeerConnectionId(idbites);
        }
    }

    @Test(dataProvider = "tests")
    public void testPacketSpaceManager(TestCase testCase) throws Exception {
        System.out.printf("%n -------  testPacketSpaceManager ------- %n");
        SynchronousTestDriver driver = new SynchronousTestDriver(testCase);
        driver.run();
        driver.check();
    }

 }
