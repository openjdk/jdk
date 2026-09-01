/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8323552
 * @run junit/timeout=480 TestMismatch
 */

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static java.lang.System.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestMismatch {

    // stores an increasing sequence of values into the memory of the given segment
    static MemorySegment initializeSegment(MemorySegment segment) {
        for (int i = 0 ; i < segment.byteSize() ; i++) {
            segment.set(ValueLayout.JAVA_BYTE, i, (byte)i);
        }
        return segment;
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testNegativeSrcFromOffset(MemorySegment s1, MemorySegment s2) {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            MemorySegment.mismatch(s1, -1, 0, s2, 0, 0);
        });
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testNegativeDstFromOffset(MemorySegment s1, MemorySegment s2) {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            MemorySegment.mismatch(s1, 0, 0, s2, -1, 0);
        });
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testNegativeSrcToOffset(MemorySegment s1, MemorySegment s2) {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            MemorySegment.mismatch(s1, 0, -1, s2, 0, 0);
        });
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testNegativeDstToOffset(MemorySegment s1, MemorySegment s2) {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            MemorySegment.mismatch(s1, 0, 0, s2, 0, -1);
        });
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testNegativeSrcLength(MemorySegment s1, MemorySegment s2) {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            MemorySegment.mismatch(s1, 3, 2, s2, 0, 0);
        });
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testNegativeDstLength(MemorySegment s1, MemorySegment s2) {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            MemorySegment.mismatch(s1, 0, 0, s2, 3, 2);
        });
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testSameValues(MemorySegment ss1, MemorySegment ss2) {
        out.format("testSameValues s1:%s, s2:%s\n", ss1, ss2);
        MemorySegment s1 = initializeSegment(ss1);
        MemorySegment s2 = initializeSegment(ss2);

        if (s1.byteSize() == s2.byteSize()) {
            assertEquals(-1, s1.mismatch(s2));  // identical
            assertEquals(-1, s2.mismatch(s1));
        } else if (s1.byteSize() > s2.byteSize()) {
            assertEquals(s2.byteSize(), s1.mismatch(s2));  // proper prefix
            assertEquals(s2.byteSize(), s2.mismatch(s1));
        } else {
            assert s1.byteSize() < s2.byteSize();
            assertEquals(s1.byteSize(), s1.mismatch(s2));  // proper prefix
            assertEquals(s1.byteSize(), s2.mismatch(s1));
        }
    }

    @ParameterizedTest
    @MethodSource("slicesStatic")
    public void testSameValuesStatic(SliceOffsetAndSize ss1, SliceOffsetAndSize ss2) {
        out.format("testSameValuesStatic s1:%s, s2:%s\n", ss1, ss2);
        MemorySegment s1 = initializeSegment(ss1.toSlice());
        MemorySegment s2 = initializeSegment(ss2.toSlice());

        for (long i = ss2.offset ; i < ss2.size ; i++) {
            long bytes = i - ss2.offset;
            long expected = (bytes == ss1.size) ?
                    -1 : Long.min(ss1.size, bytes);
            assertEquals(expected, MemorySegment.mismatch(ss1.segment, ss1.offset, ss1.endOffset(), ss2.segment, ss2.offset, i));
        }
        for (long i = ss1.offset ; i < ss1.size ; i++) {
            long bytes = i - ss1.offset;
            long expected = (bytes == ss2.size) ?
                    -1 : Long.min(ss2.size, bytes);
            assertEquals(expected, MemorySegment.mismatch(ss2.segment, ss2.offset, ss2.endOffset(), ss1.segment, ss1.offset, i));
        }
    }

    @Test
    public void random() {
        try (var arena = Arena.ofConfined()) {
            var rnd = new Random(42);
            for (int size = 1; size < 64; size++) {
                // Repeat a fair number of rounds
                for (int i = 0; i < 147; i++) {
                    var src = arena.allocate(size);
                    // The dst segment might be zero to eight bytes longer
                    var dst = arena.allocate(size + rnd.nextInt(8 + 1));
                    // Fill the src with random data
                    for (int j = 0; j < size; j++) {
                        src.set(ValueLayout.JAVA_BYTE, j, randomByte(rnd));
                    }
                    // copy the random data from src to dst
                    dst.copyFrom(src);
                    // Fill the rest (if any) of the dst with random data
                    for (long j = src.byteSize(); j < dst.byteSize(); j++) {
                        dst.set(ValueLayout.JAVA_BYTE, j, randomByte(rnd));
                    }

                    if (rnd.nextBoolean()) {
                        // In this branch, we inject one or more deviating bytes
                        int beginDiff = rnd.nextInt(size);
                        int endDiff = rnd.nextInt(beginDiff, size);
                        for (int d = beginDiff; d <= endDiff; d++) {
                            byte existing = dst.get(ValueLayout.JAVA_BYTE, d);
                            // Make sure we never get back the same value
                            byte mutatedValue;
                            do {
                                mutatedValue = randomByte(rnd);
                            } while (existing == mutatedValue);
                            dst.set(ValueLayout.JAVA_BYTE, d, mutatedValue);
                        }

                        // They are not equal and differs in position beginDiff
                        assertEquals(beginDiff, src.mismatch(dst));
                        assertEquals(beginDiff, dst.mismatch(src));
                    } else {
                        // In this branch, there is no injection

                        if (src.byteSize() == dst.byteSize()) {
                            // The content matches and they are of equal size
                            assertEquals(-1, src.mismatch(dst));
                            assertEquals(-1, dst.mismatch(src));
                        } else {
                            // The content matches but they are of different length
                            // Remember, the size of src is always smaller or equal
                            // to the size of dst.
                            assertEquals(src.byteSize(), src.mismatch(dst));
                            assertEquals(src.byteSize(), dst.mismatch(src));
                        }
                    }
                }
            }
        }
    }

    static byte randomByte(Random rnd) {
        return (byte) rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
    }

    @ParameterizedTest
    @MethodSource("slices")
    public void testDifferentValues(MemorySegment s1, MemorySegment s2) {
        out.format("testDifferentValues s1:%s, s2:%s\n", s1, s2);
        s1 = initializeSegment(s1);
        s2 = initializeSegment(s2);

        for (long i = s2.byteSize() -1 ; i >= 0; i--) {
            long expectedMismatchOffset = i;
            s2.set(ValueLayout.JAVA_BYTE, i, (byte) 0xFF);

            if (s1.byteSize() == s2.byteSize()) {
                assertEquals(expectedMismatchOffset, s1.mismatch(s2));
                assertEquals(expectedMismatchOffset, s2.mismatch(s1));
            } else if (s1.byteSize() > s2.byteSize()) {
                assertEquals(expectedMismatchOffset, s1.mismatch(s2));
                assertEquals(expectedMismatchOffset, s2.mismatch(s1));
            } else {
                assert s1.byteSize() < s2.byteSize();
                var off = Math.min(s1.byteSize(), expectedMismatchOffset);
                assertEquals(off, s1.mismatch(s2));  // proper prefix
                assertEquals(off, s2.mismatch(s1));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("slicesStatic")
    public void testDifferentValuesStatic(SliceOffsetAndSize ss1, SliceOffsetAndSize ss2) {
        out.format("testDifferentValues s1:%s, s2:%s\n", ss1, ss2);

        for (long i = ss2.size - 1 ; i >= 0; i--) {
            if (i >= ss1.size) continue;
            initializeSegment(ss1.toSlice());
            initializeSegment(ss2.toSlice());
            long expectedMismatchOffset = i;
            ss2.toSlice().set(ValueLayout.JAVA_BYTE, i, (byte) 0xFF);

            for (long j = expectedMismatchOffset + 1 ; j < ss2.size ; j++) {
                assertEquals(expectedMismatchOffset, MemorySegment.mismatch(ss1.segment, ss1.offset, ss1.endOffset(), ss2.segment, ss2.offset, j + ss2.offset));
            }
            for (long j = expectedMismatchOffset + 1 ; j < ss1.size ; j++) {
                assertEquals(expectedMismatchOffset, MemorySegment.mismatch(ss2.segment, ss2.offset, ss2.endOffset(), ss1.segment, ss1.offset, j + ss1.offset));
            }
        }
    }

    @Test
    public void testEmpty() {
        var s1 = MemorySegment.ofArray(new byte[0]);
        assertEquals(-1, s1.mismatch(s1));
        try (Arena arena = Arena.ofConfined()) {
            var nativeSegment = arena.allocate(4, 4);;
            var s2 = nativeSegment.asSlice(0, 0);
            assertEquals(-1, s1.mismatch(s2));
            assertEquals(-1, s2.mismatch(s1));
        }
    }

    @Test
    public void testLarge() {
        // skip if not on 64 bits
        if (ValueLayout.ADDRESS.byteSize() > 32) {
            try (Arena arena = Arena.ofConfined()) {
                var s1 = arena.allocate((long) Integer.MAX_VALUE + 10L, 8);;
                var s2 = arena.allocate((long) Integer.MAX_VALUE + 10L, 8);;
                assertEquals(-1, s1.mismatch(s1));
                assertEquals(-1, s1.mismatch(s2));
                assertEquals(-1, s2.mismatch(s1));

                testLargeAcrossMaxBoundary(s1, s2);

                testLargeMismatchAcrossMaxBoundary(s1, s2);
            }
        }
    }

    private void testLargeAcrossMaxBoundary(MemorySegment s1, MemorySegment s2) {
        for (long i = s2.byteSize() -1 ; i >= Integer.MAX_VALUE - 10L; i--) {
            var s3 = s1.asSlice(0, i);
            var s4 = s2.asSlice(0, i);
            // instance
            assertEquals(-1, s3.mismatch(s3));
            assertEquals(-1, s3.mismatch(s4));
            assertEquals(-1, s4.mismatch(s3));
            // static
            assertEquals(-1, MemorySegment.mismatch(s1, 0, s1.byteSize(), s1, 0, i));
            assertEquals(-1, MemorySegment.mismatch(s2, 0, s1.byteSize(), s1, 0, i));
            assertEquals(-1, MemorySegment.mismatch(s1, 0, s1.byteSize(), s2, 0, i));
        }
    }

    private void testLargeMismatchAcrossMaxBoundary(MemorySegment s1, MemorySegment s2) {
        for (long i = s2.byteSize() -1 ; i >= Integer.MAX_VALUE - 10L; i--) {
            s2.set(ValueLayout.JAVA_BYTE, i, (byte) 0xFF);
            long expectedMismatchOffset = i;
            assertEquals(expectedMismatchOffset, s1.mismatch(s2));
            assertEquals(expectedMismatchOffset, s2.mismatch(s1));
        }
    }

    static final Class<IllegalStateException> ISE = IllegalStateException.class;
    static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    @Test
    public void testClosed() {
        MemorySegment s1, s2;
        try (Arena arena = Arena.ofConfined()) {
            s1 = arena.allocate(4, 1);
            s2 = arena.allocate(4, 1);;
        }
        assertThrows(ISE, () -> s1.mismatch(s1));
        assertThrows(ISE, () -> s1.mismatch(s2));
        assertThrows(ISE, () -> s2.mismatch(s1));
    }

    @Test
    public void testThreadAccess() throws Exception {
        try (Arena arena = Arena.ofConfined()) {
            var segment = arena.allocate(4, 1);;
            {
                AtomicReference<RuntimeException> exception = new AtomicReference<>();
                Runnable action = () -> {
                    try {
                        MemorySegment.ofArray(new byte[4]).mismatch(segment);
                    } catch (RuntimeException e) {
                        exception.set(e);
                    }
                };
                Thread thread = new Thread(action);
                thread.start();
                thread.join();

                RuntimeException e = exception.get();
                if (!(e instanceof WrongThreadException)) {
                    throw e;
                }
            }
            {
                AtomicReference<RuntimeException> exception = new AtomicReference<>();
                Runnable action = () -> {
                    try {
                        segment.mismatch(MemorySegment.ofArray(new byte[4]));
                    } catch (RuntimeException e) {
                        exception.set(e);
                    }
                };
                Thread thread = new Thread(action);
                thread.start();
                thread.join();

                RuntimeException e = exception.get();
                if (!(e instanceof WrongThreadException)) {
                    throw e;
                }
            }
        }
    }

    @Test
    public void testSameSegment() {
        var segment = MemorySegment.ofArray(new byte[]{
                1,2,3,4,  1,2,3,4,  1,4});

        long match = MemorySegment.mismatch(
                segment, 0L, 4L,
                segment, 4L, 8L);
        assertEquals(-1, match);

        long noMatch = MemorySegment.mismatch(
                segment, 0L, 4L,
                segment, 1L, 5L);
        assertEquals(0, noMatch);

        long noMatchEnd = MemorySegment.mismatch(
                segment, 0L, 2L,
                segment, 8L, 10L);
        assertEquals(1, noMatchEnd);

        long same = MemorySegment.mismatch(
                segment, 0L, 8L,
                segment, 0L, 8L);
        assertEquals(-1, same);
    }

    enum SegmentKind {
        NATIVE(i -> Arena.ofAuto().allocate(i, 1)),
        ARRAY(i -> MemorySegment.ofArray(new byte[i]));

        final IntFunction<MemorySegment> segmentFactory;

        SegmentKind(IntFunction<MemorySegment> segmentFactory) {
            this.segmentFactory = segmentFactory;
        }

        MemorySegment makeSegment(int elems) {
            return segmentFactory.apply(elems);
        }
    }

    record SliceOffsetAndSize(MemorySegment segment, long offset, long size) {
        MemorySegment toSlice() {
            return segment.asSlice(offset, size);
        }
        long endOffset() {
            return offset + size;
        }
    };

    static Object[][] slicesStatic() {
        int[] sizes = { 16, 8, 1 };
        List<SliceOffsetAndSize> aSliceOffsetAndSizes = new ArrayList<>();
        List<SliceOffsetAndSize> bSliceOffsetAndSizes = new ArrayList<>();
        for (List<SliceOffsetAndSize> slices : List.of(aSliceOffsetAndSizes, bSliceOffsetAndSizes)) {
            for (SegmentKind kind : SegmentKind.values()) {
                MemorySegment segment = kind.makeSegment(16);
                //compute all slices
                for (int size : sizes) {
                    for (int index = 0 ; index < 16 ; index += size) {
                        slices.add(new SliceOffsetAndSize(segment, index, size));
                    }
                }
            }
        }
        assert aSliceOffsetAndSizes.size() == bSliceOffsetAndSizes.size();
        Object[][] sliceArray = new Object[aSliceOffsetAndSizes.size() * bSliceOffsetAndSizes.size()][];
        for (int i = 0 ; i < aSliceOffsetAndSizes.size() ; i++) {
            for (int j = 0 ; j < bSliceOffsetAndSizes.size() ; j++) {
                sliceArray[i * aSliceOffsetAndSizes.size() + j] = new Object[] { aSliceOffsetAndSizes.get(i), bSliceOffsetAndSizes.get(j) };
            }
        }
        return sliceArray;
    }

    static Object[][] slices() {
        Object[][] slicesStatic = slicesStatic();
        return Stream.of(slicesStatic)
                .map(arr -> new Object[]{
                        ((SliceOffsetAndSize) arr[0]).toSlice(),
                        ((SliceOffsetAndSize) arr[1]).toSlice()
                }).toArray(Object[][]::new);
    }
}
