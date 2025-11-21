/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import jdk.internal.net.http.quic.OrderedFlow;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * @test
 * @summary tests the reordering logic implemented by OrderedFlow
 *          and its two concrete subclasses
 * @library /test/lib
 * @run testng OrderedFlowTest
 * @run testng/othervm -Dseed=-2680947227866359853 OrderedFlowTest
 * @run testng/othervm -Dseed=-273117134353023275 OrderedFlowTest
 * @run testng/othervm -Dseed=3649132517916066643 OrderedFlowTest
 * @run testng/othervm -Dseed=4568737726943220431 OrderedFlowTest
 */
public class OrderedFlowTest {

    static final int WITH_DUPS = 1;
    static final int WITH_OVERLAPS = 2;

    record TestData<T extends QuicFrame>(Class<T> frameType,
                    Supplier<OrderedFlow<T>> flowSupplier,
                    Function<T, String> payloadAccessor,
                    Comparator<T> framesComparator,
                    List<T> frames,
                    String expectedResult,
                    boolean duplicates,
                    boolean shuffled) {

        boolean hasEmptyFrames() {
            return frames.stream().map(payloadAccessor)
                    .mapToInt(String::length)
                    .anyMatch((i) -> i == 0);
        }

        @Override
        public String toString() {
            return frameType.getSimpleName() +
                    "(frames=" + frames.size() +
                    ", duplicates=" + duplicates +
                    ", shuffled=" + shuffled +
                    ", hasEmptyFrames=" + hasEmptyFrames() +
                    ")";
        }
    }

    static final Random RANDOM = jdk.test.lib.RandomFactory.getRandom();
    static final String LOREM = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer
            id elementum sem. In rhoncus nisi a ante convallis, at iaculis augue
            elementum. Ut eget imperdiet justo, sed sodales est. In nec laoreet
            lorem. Integer et arcu nibh. Quisque quis felis consectetur, luctus
            libero eu, facilisis risus. Aliquam at viverra diam. Sed nec lacus
            eget dui hendrerit porttitor et nec ligula. Suspendisse rutrum,
            augue non ultricies vestibulum, metus orci faucibus est, et tempus
            ante diam a quam. Proin venenatis justo eleifend vestibulum tincidunt.

            Nullam nec elementum sem. Class aptent taciti sociosqu ad litora
            torquent per conubia nostra, per inceptos himenaeos. Integer euismod,
            purus ut sollicitudin semper, quam turpis condimentum arcu, sit amet
            suscipit sapien elit ac nisi. Orci varius natoque penatibus et magnis
            dis parturient montes, nascetur ridiculus mus. Duis vel tortor non purus
            scelerisque iaculis at efficitur dolor. Nam dapibus tellus non aliquet
            suscipit. Nulla facilisis mi eget ex blandit sodales. Pellentesque enim
            sem, aliquet non luctus id, feugiat in eros. Aliquam molestie felis
            lorem, eget tristique nisi mollis lobortis.

            Suspendisse aliquam vitae purus nec mollis. Quisque et urna nec nunc
            porttitor blandit quis a magna. Maecenas porta est velit, in volutpat
            felis suscipit eget. Vivamus porta semper ipsum, et sodales nibh molestie
            eget. Vivamus tincidunt quam id ante efficitur tincidunt. Suspendisse
            potenti. Integer posuere felis ut semper feugiat. Vivamus id dui quam.

            Pellentesque accumsan quam non est pretium faucibus. Donec vel euismod
            magna, ac scelerisque mauris. Nullam vitae varius diam, hendrerit semper
            velit. Vestibulum et nisl felis. Orci varius natoque penatibus et magnis
            dis parturient montes, nascetur ridiculus mus. Cras elementum auctor lacus,
            vel tempor erat lobortis sed. Suspendisse sed felis ut mi condimentum
            eleifend. Proin et arcu cursus, fermentum arcu non, tristique nulla.
            Suspendisse tristique volutpat elit, et blandit metus aliquet id. Nunc non
            dapibus dui. Nam sagittis justo magna. Nulla pharetra ex nec sem porta
            consequat.

            Nam sit amet luctus ante, nec eleifend nunc. Phasellus lobortis lorem a
            auctor ornare. Sed venenatis fermentum arcu, ut tincidunt turpis auctor
            at. Praesent felis mi, tincidunt a sem et, luctus condimentum libero.
            Phasellus egestas ac lectus vitae tincidunt. Etiam eu lobortis felis.
            Nulla semper est ac nisl placerat, vitae sollicitudin diam lobortis.
            Cras pellentesque semper purus at rutrum. Suspendisse a pellentesque
            orci, ac tincidunt libero. Integer ex augue, ultrices sit amet aliquam
            eget, laoreet eget elit.
            """;

    interface FramesFactory<T extends QuicFrame> {
        public T create(int offset, String payload, boolean fin);
        public int length(T frame);
        public long offset(T frame);
        public String getPayload(T frame);
        public OrderedFlow<T> flow();
        public Class<T> frameType();
        public Comparator<T> comparator();
    }

    static class StreamFrameFactory implements FramesFactory<StreamFrame> {
        final long streamId = RANDOM.nextInt(0, Integer.MAX_VALUE);

        @Override
        public StreamFrame create(int offset, String payload, boolean fin) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            int length = RANDOM.nextBoolean() ? bytes.length : -1;
            return new StreamFrame(streamId, offset, length,
                    fin, ByteBuffer.wrap(bytes));
        }

        @Override
        public int length(StreamFrame frame) {
            return frame.dataLength();
        }

        @Override
        public long offset(StreamFrame frame) {
            return frame.offset();
        }

        @Override
        public String getPayload(StreamFrame frame) {
            int length = frame.dataLength();
            byte[] bytes = new byte[length];
            frame.payload().get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public OrderedFlow<StreamFrame> flow() {
            return new OrderedFlow.StreamDataFlow();
        }

        @Override
        public Class<StreamFrame> frameType() {
            return StreamFrame.class;
        }

        @Override
        public Comparator<StreamFrame> comparator() {
            return StreamFrame::compareOffsets;
        }
    }

    static class CryptoFrameFactory implements FramesFactory<CryptoFrame> {
        final long streamId = RANDOM.nextInt(0, Integer.MAX_VALUE);

        @Override
        public CryptoFrame create(int offset, String payload, boolean fin) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            int length = bytes.length;
            return new CryptoFrame(offset, length, ByteBuffer.wrap(bytes));
        }

        @Override
        public int length(CryptoFrame frame) {
            return frame.length();
        }

        @Override
        public long offset(CryptoFrame frame) {
            return frame.offset();
        }

        @Override
        public String getPayload(CryptoFrame frame) {
            int length = frame.length();
            byte[] bytes = new byte[length];
            frame.payload().get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public OrderedFlow<CryptoFrame> flow() {
            return new OrderedFlow.CryptoDataFlow();
        }

        @Override
        public Class<CryptoFrame> frameType() {
            return CryptoFrame.class;
        }

        @Override
        public Comparator<CryptoFrame> comparator() {
            return CryptoFrame::compareOffsets;
        }
    }

    static <T extends QuicFrame> TestData<T> generateData(FramesFactory<T> factory, int options) {
        int length = LOREM.length();
        int chunks = length/20;
        int offset = 0;
        int remaining = length;
        List<T> frames = new ArrayList<>();
        T first = null;
        T second = null;
        boolean duplicates = (options & WITH_DUPS) == WITH_DUPS;
        boolean overlaps = (options & WITH_OVERLAPS) == WITH_OVERLAPS;
        while (remaining > 0) {
            int len = remaining < 20
                    ? remaining
                    : RANDOM.nextInt(Math.min(19, remaining - 1), Math.min(chunks, remaining));
            remaining -= len;
            String data = LOREM.substring(offset, offset + len);
            T frame;
            if (overlaps && len > 4) {
                int start = RANDOM.nextInt(0, len/4);
                int end = RANDOM.nextInt(3*len/4, len);
                for (int i=start; i < end; i+=2) {
                    frame = factory.create(offset+i, data.substring(i, i+1),
                            i == len-1 && remaining == 0);
                    frames.add(frame);
                }
            }
            frame = factory.create(offset, data, remaining == 0);
            frames.add(frame);
            if (first == null) first = frame;
            else if (second == null) second = frame;
            if (duplicates && RANDOM.nextInt(1, 5) > 3) {
                frames.add(factory.create(offset, data, remaining == 0));
            } else if (overlaps && RANDOM.nextInt(1, 5) > 3 && second != null && len > 1) {
                // next frame will overlap with this one.
                offset -= len / 2; remaining += len / 2;
            }
            offset += len;
        }
        if (duplicates) frames.add(first);
        if (overlaps && frames.size() > 1) {
            if (factory.length(first) > 0) {
                frames.remove(second);
                String firstpayload = factory.getPayload(first);
                String secondpayload = factory.getPayload(second);
                String newpayload = firstpayload.charAt(firstpayload.length() - 1)
                        + secondpayload;
                long newoffset = factory.offset(second) - 1;
                assert newoffset >= 0;
                frames.add(1, factory.create((int) newoffset, newpayload, frames.size() == 2));
            }
        }
        return new TestData<>(factory.frameType(), factory::flow,
                factory::getPayload, factory.comparator(),
                List.copyOf(frames), LOREM,
                duplicates, false);
    }

    // Returns a new data set where all frames have been shuffled randomly.
    // This should help flush bugs with buffering of frames that come out of order.
    static <T extends QuicFrame> TestData<T> shuffle(TestData<T> data) {
        List<T> shuffled = new ArrayList<>(data.frames());
        Collections.shuffle(shuffled, RANDOM);
        return new TestData<>(data.frameType(),data.flowSupplier(), data.payloadAccessor(),
                data.framesComparator(), List.copyOf(shuffled), data.expectedResult(),
                data.duplicates(), true);
    }

    // Returns a new data set where all frames have been sorted in reverse
    // order: largest offset first. This is the worst case scenario for
    // buffering. This should help checking that the amount of data buffered
    // never exceeds the length of the stream, as duplicates and overlaps should
    // not be buffered.
    static <T extends QuicFrame> TestData<T> reversed(TestData<T> data) {
        List<T> sorted = new ArrayList<>(data.frames());
        Collections.sort(sorted, data.framesComparator().reversed());
        return new TestData<>(data.frameType(),data.flowSupplier(), data.payloadAccessor(),
                data.framesComparator(), List.copyOf(sorted), data.expectedResult(),
                data.duplicates(), true);
    }

    static <T extends QuicFrame> List<TestData<T>> generateData(FramesFactory<T> factory) {
        List<TestData<T>> result = new ArrayList<>();
        TestData<T> data = generateData(factory, 0);
        TestData<T> withdups = generateData(factory, WITH_DUPS);
        TestData<T> withoverlaps = generateData(factory, WITH_OVERLAPS);
        TestData<T> withall = generateData(factory, WITH_DUPS | WITH_OVERLAPS);
        result.add(data);
        result.add(withdups);
        result.add(withoverlaps);
        result.add(withall);
        result.add(reversed(data));
        result.add(reversed(withdups));
        result.add(reversed(withoverlaps));
        result.add(reversed(withall));
        for (int i=0; i<5; i++) {
            result.add(shuffle(data));
            result.add(shuffle(withdups));
            result.add(shuffle(withoverlaps));
            result.add(shuffle(withall));
        }
        return List.copyOf(result);
    }

    @DataProvider(name="CryptoFrame")
    Object[][] generateCryptoFrames() {
        return generateData(new CryptoFrameFactory())
                .stream()
                .map(List::of)
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    @DataProvider(name="StreamFrame")
    Object[][] generateStreanFrames() {
        return generateData(new StreamFrameFactory())
                .stream()
                .map(List::of)
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    private <T extends QuicFrame> void testOrderedFlow(TestData<T> testData, ToLongFunction<T> offset) {
        System.out.println("\n    ---------------- "
                + testData.frameType().getName()
                + " ----------------   \n");
        System.out.println("testOrderedFlow: " + testData);
        String offsets = testData.frames().stream().mapToLong(offset)
                .mapToObj(Long::toString).collect(Collectors.joining(", "));
        System.out.println("offsets: " + offsets);

        // we should not have empty frames, but maybe we do?
        // if we do - should we make allowance for that?
        var hasEmptyFrames = testData.hasEmptyFrames();
        assertFalse(hasEmptyFrames, "generated data has empty frames");

        var flow = testData.flowSupplier().get();
        var size = LOREM.length();
        StringBuilder result = new StringBuilder(size);
        long maxBuffered = 0;
        for (var f : testData.frames()) {
            T received = flow.receive(f);
            var buffered = flow.buffered();
            maxBuffered = Math.max(buffered, maxBuffered);
            assertTrue(buffered < size,
                    "buffered data %s exceeds or equals payload size %s".formatted(buffered, size));
            while (received != null) {
                var payload = testData.payloadAccessor.apply(received);
                assertNotEquals(payload, "", "empty frames not expected: " + received);
                result.append(payload);
                received = flow.poll();
            }
        }
        assertEquals(result.toString(), testData.expectedResult);
    }

    @Test(dataProvider = "CryptoFrame")
    public void testCryptoFlow(TestData<CryptoFrame> testData) {
        testOrderedFlow(testData, CryptoFrame::offset);
    }

    @Test(dataProvider = "StreamFrame")
    public void testStreamFlow(TestData<StreamFrame> testData) {
        testOrderedFlow(testData, StreamFrame::offset);
    }

}
