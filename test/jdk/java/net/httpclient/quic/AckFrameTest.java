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

import jdk.internal.net.http.quic.CodingContext;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.frames.AckFrame.AckFrameBuilder;
import jdk.internal.net.http.quic.frames.AckFrame.AckRange;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.test.lib.RandomFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

/**
 * @test
 * @summary tests the logic to build an AckFrame
 * @library /test/lib
 * @run testng AckFrameTest
 */
public class AckFrameTest {

    static final Random RANDOM = RandomFactory.getRandom();

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
            throw new AssertionError("should not come here!");
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

    public static record Acknowledged(long first, long last) {
        public boolean contains(long packet) {
            return first <= packet && last >= packet;
        }
        public static List<Acknowledged> of(long... numbers) {
            if (numbers == null || numbers.length == 0) return List.of();
            if (numbers.length%2 != 0) throw new IllegalArgumentException();
            List<Acknowledged> res = new ArrayList<>(numbers.length/2);
            for (int i = 0; i < numbers.length; i += 2) {
                res.add(new Acknowledged(numbers[i], numbers[i+1]));
            }
            return List.copyOf(res);
        }
    }
    public static record Packet(long packetNumber) {
        static List<Packet> ofAcks(List<Acknowledged> acks) {
            return packets(acks);
        }
        static List<Packet> of(long... numbers) {
            return LongStream.of(numbers).mapToObj(Packet::new).toList();
        }
    }

    public static record TestCase(List<Acknowledged> acks, List<Packet> packets, boolean shuffled) {
        public TestCase(List<Acknowledged> acks) {
            this(acks, Packet.ofAcks(acks), false);
        }
        public TestCase shuffle() {
            List<Packet> shuffled = new ArrayList<>();
            shuffled.addAll(packets);
            Collections.shuffle(shuffled, RANDOM);
            return new TestCase(acks, List.copyOf(shuffled), true);
        }
    }

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

    @Test(dataProvider = "tests")
    public void testAckFrames(TestCase testCase) {
        AckFrameBuilder builder = new AckFrameBuilder();
        List<Acknowledged> acks = testCase.acks;
        List<Packet> packets = testCase.packets;
        long largest = packets.stream()
                .mapToLong(Packet::packetNumber)
                .max().getAsLong();
        System.out.printf("%ntestAckFrames(%s, %s)%n", acks, testCase.shuffled);
        builder.ackDelay(250);
        packets.stream().mapToLong(Packet::packetNumber).forEach(builder::addAck);
        AckFrame frame = builder.build();
        System.out.printf("   -> %s%n", frame);
        checkFrame(frame, testCase, packets, frame);
        checkAcknowledging(builder::isAcknowledging, testCase, packets);

        AckFrameBuilder dup = new AckFrameBuilder(frame);
        assertEquals(frame, dup.build());
        assertEquals(frame, builder.build());
        checkAcknowledging(dup::isAcknowledging, testCase, packets);

        packets.stream().mapToLong(Packet::packetNumber).forEach(builder::addAck);
        checkFrame(builder.build(), testCase, packets, frame);
        checkAcknowledging(builder::isAcknowledging, testCase, packets);

        packets.stream().mapToLong(Packet::packetNumber).forEach(dup::addAck);
        checkFrame(dup.build(), testCase, packets, frame);
        checkAcknowledging(dup::isAcknowledging, testCase, packets);

        AckFrameBuilder dupdup = new AckFrameBuilder();
        dupdup.ackDelay(250);
        List<Packet> dups = new ArrayList<>(packets);
        dups.addAll(packets);
        dups.addAll(packets);
        Collections.shuffle(dups, RANDOM);
        dups.stream().mapToLong(Packet::packetNumber).forEach(dupdup::addAck);
        checkFrame(dupdup.build(), testCase, dups, frame);
        checkAcknowledging(dupdup::isAcknowledging, testCase, packets);

    }

    private void checkFrame(AckFrame frame, TestCase testCase, List<Packet> packets, AckFrame reference) {
        long largest = testCase.packets.stream()
                .mapToLong(Packet::packetNumber)
                .max().getAsLong();
        assertEquals(frame.largestAcknowledged(), largest);
        checkAcknowledging(frame::isAcknowledging, testCase, packets);
        for (var ack : testCase.acks) {
            checkRangeAcknowledged(frame, ack.first, ack.last);
        }
        assertEquals(frame, reference);
        int size = frame.size();
        ByteBuffer buffer = ByteBuffer.allocate(size + 10);
        buffer.position(5);
        buffer.limit(size + 5);
        try {
            frame.encode(buffer);
            assertEquals(buffer.position(), buffer.limit());
            buffer.position(5);
            buffer.limit(buffer.capacity());
            var decoded = QuicFrame.decode(buffer);
            assertEquals(buffer.position(), size + 5);
            assertEquals(decoded, frame);
            assertEquals(decoded, reference);
        } catch (Exception e) {
            throw new AssertionError("Can't encode or decode frame: " + frame, e);
        }
    }

    private void checkRangeAcknowledged(AckFrame frame, long first, long last) {
        assertTrue(frame.isRangeAcknowledged(first, last),
                "range [%s, %s] should be acked".formatted(first, last));
        if (first > 0) {
            if (!frame.isAcknowledging(first - 1)) {
                assertFalse(frame.isRangeAcknowledged(first -1, last),
                    "range [%s, %s] should not be acked".formatted(first -1, last));
            } else {
                assertTrue(frame.isRangeAcknowledged(first - 1, last),
                        "range [%s, %s] should be acked".formatted(first - 1, last));
                if (frame.isAcknowledging(last + 1)) {
                    assertTrue(frame.isRangeAcknowledged(first -1, last + 1),
                            "range [%s, %s] should be acked".formatted(first -1, last+1));
                }
            }
        }
        if (!frame.isAcknowledging(last + 1)) {
            assertFalse(frame.isRangeAcknowledged(first, last + 1),
                    "range [%s, %s] should not be acked".formatted(first, last + 1));
        } else {
            assertTrue(frame.isRangeAcknowledged(first, last+1),
                    "range [%s, %s] should be acked".formatted(first, last + 1));
        }
        if (last - 1 >= first) {
            assertTrue(frame.isRangeAcknowledged(first + 1, last),
                    "range [%s, %s] should be acked".formatted(first + 1, last));
            assertTrue(frame.isRangeAcknowledged(first, last - 1),
                    "range [%s, %s] should be acked".formatted(first, last - 1));
        }
        if (last - 2 >= first) {
            assertTrue(frame.isRangeAcknowledged(first + 1, last - 1),
                    "range [%s, %s] should be acked".formatted(first + 1, last - 1));
        }
    }

    private void checkAcknowledging(LongPredicate isAckPredicate,
                                    TestCase testCase,
                                    List<Packet> packets) {
        long largest = testCase.packets.stream()
                .mapToLong(Packet::packetNumber)
                .max().getAsLong();
        for (long i = largest + 10; i >= 0; i--) {
            long pn = i;
            boolean expected = testCase.acks.stream().anyMatch((a) -> a.contains(pn));
            boolean isAcknowledging = isAckPredicate.test(pn);
            if (isAcknowledging != expected && testCase.shuffled) {
                System.out.printf("   -> %s%n", packets);
            }
            assertEquals(isAcknowledging, expected, String.valueOf(pn));
        }
        for (var p : testCase.packets) {
            boolean isAcknowledging = isAckPredicate.test(p.packetNumber);
            if (!isAcknowledging && testCase.shuffled) {
                System.out.printf("   -> %s%n", packets);
            }
            assertEquals(isAcknowledging, true, p.toString());
        }
    }

    @Test
    public void simpleTest() {
        AckFrame frame = new AckFrame(1, 0, List.of(new AckRange(0,0)));
        System.out.println("simpleTest: " + frame);
        assertTrue(frame.isAcknowledging(1), "1 should be acked");
        assertFalse(frame.isAcknowledging(0), "0 should not be acked");
        assertFalse(frame.isAcknowledging(2), "2 should not be acked");
        assertEquals(frame.smallestAcknowledged(), 1);
        assertEquals(frame.largestAcknowledged(), 1);
        assertEquals(frame.acknowledged().toArray(), new long[] {1L});
        assertTrue(frame.isRangeAcknowledged(1,1), "[1,1] should be acked");
        assertFalse(frame.isRangeAcknowledged(0, 1), "[0,1] should not be acked");
        assertFalse(frame.isRangeAcknowledged(1, 2), "[1,2] should not be acked");
        assertFalse(frame.isRangeAcknowledged(0, 2), "[0,2] should not be acked");

        frame = new AckFrame(1, 0, List.of(new AckRange(0,1)));
        System.out.println("simpleTest: " + frame);
        assertTrue(frame.isAcknowledging(1), "1 should be acked");
        assertTrue(frame.isAcknowledging(0), "0 should be acked");
        assertFalse(frame.isAcknowledging(2), "2 should not be acked");
        assertEquals(frame.smallestAcknowledged(), 0);
        assertEquals(frame.largestAcknowledged(), 1);
        assertEquals(frame.acknowledged().toArray(), new long[] {1L, 0L});
        assertTrue(frame.isRangeAcknowledged(0,0), "[0,0] should be acked");
        assertTrue(frame.isRangeAcknowledged(1,1), "[1,1] should be acked");
        assertTrue(frame.isRangeAcknowledged(0, 1), "[0,1] should be acked");
        assertFalse(frame.isRangeAcknowledged(1, 2), "[1,2] should not be acked");
        assertFalse(frame.isRangeAcknowledged(0, 2), "[0,2] should not be acked");

        frame = new AckFrame(10, 0, List.of(new AckRange(0,3), new AckRange(2, 3)));
        System.out.println("simpleTest: " + frame);
        assertTrue(frame.isAcknowledging(10), "10 should be acked");
        assertTrue(frame.isAcknowledging(0), "0 should be acked");
        assertTrue(frame.isRangeAcknowledged(0, 3), "[0,3] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[7,10] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[0,2] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[1,3] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[1,2] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[7,9] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[8,10] should be acked");
        assertTrue(frame.isRangeAcknowledged(7, 10), "[8,9] should be acked");
        assertFalse(frame.isRangeAcknowledged(0, 10), "[0,10] should not be acked");
        assertFalse(frame.isRangeAcknowledged(4, 6), "[4,6] should not be acked");
        assertFalse(frame.isRangeAcknowledged(4, 6), "[3,7] should not be acked");
        assertFalse(frame.isRangeAcknowledged(4, 6), "[2,8] should not be acked");
    }
 }
