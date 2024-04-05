/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestMismatch
 */

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class TestMismatch {

    // stores an increasing sequence of values into the memory of the given segment
    static MemorySegment initializeSegment(MemorySegment segment) {
        for (int i = 0 ; i < segment.byteSize() ; i++) {
            segment.set(ValueLayout.JAVA_BYTE, i, (byte)i);
        }
        return segment;
    }

    @Test(dataProvider = "slices", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeSrcFromOffset(MemorySegment s1, MemorySegment s2) {
        MemorySegment.mismatch(s1, -1, 0, s2, 0, 0);
    }

    @Test(dataProvider = "slices", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeDstFromOffset(MemorySegment s1, MemorySegment s2) {
        MemorySegment.mismatch(s1, 0, 0, s2, -1, 0);
    }

    @Test(dataProvider = "slices", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeSrcToOffset(MemorySegment s1, MemorySegment s2) {
        MemorySegment.mismatch(s1, 0, -1, s2, 0, 0);
    }

    @Test(dataProvider = "slices", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeDstToOffset(MemorySegment s1, MemorySegment s2) {
        MemorySegment.mismatch(s1, 0, 0, s2, 0, -1);
    }

    @Test(dataProvider = "slices", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeSrcLength(MemorySegment s1, MemorySegment s2) {
        MemorySegment.mismatch(s1, 3, 2, s2, 0, 0);
    }

    @Test(dataProvider = "slices", expectedExceptions = IndexOutOfBoundsException.class)
    public void testNegativeDstLength(MemorySegment s1, MemorySegment s2) {
        MemorySegment.mismatch(s1, 0, 0, s2, 3, 2);
    }

    @Test(dataProvider = "slices")
    public void testSameValues(MemorySegment ss1, MemorySegment ss2) {
        out.format("testSameValues s1:%s, s2:%s\n", ss1, ss2);
        MemorySegment s1 = initializeSegment(ss1);
        MemorySegment s2 = initializeSegment(ss2);

        if (s1.byteSize() == s2.byteSize()) {
            assertEquals(s1.mismatch(s2), -1);  // identical
            assertEquals(s2.mismatch(s1), -1);
        } else if (s1.byteSize() > s2.byteSize()) {
            assertEquals(s1.mismatch(s2), s2.byteSize());  // proper prefix
            assertEquals(s2.mismatch(s1), s2.byteSize());
        } else {
            assert s1.byteSize() < s2.byteSize();
            assertEquals(s1.mismatch(s2), s1.byteSize());  // proper prefix
            assertEquals(s2.mismatch(s1), s1.byteSize());
        }
    }

    @Test(dataProvider = "slicesStatic")
    public void testSameValuesStatic(SliceOffsetAndSize ss1, SliceOffsetAndSize ss2) {
        out.format("testSameValuesStatic s1:%s, s2:%s\n", ss1, ss2);
        MemorySegment s1 = initializeSegment(ss1.toSlice());
        MemorySegment s2 = initializeSegment(ss2.toSlice());

        for (long i = ss2.offset ; i < ss2.size ; i++) {
            long bytes = i - ss2.offset;
            long expected = (bytes == ss1.size) ?
                    -1 : Long.min(ss1.size, bytes);
            assertEquals(MemorySegment.mismatch(ss1.segment, ss1.offset, ss1.endOffset(), ss2.segment, ss2.offset, i), expected);
        }
        for (long i = ss1.offset ; i < ss1.size ; i++) {
            long bytes = i - ss1.offset;
            long expected = (bytes == ss2.size) ?
                    -1 : Long.min(ss2.size, bytes);
            assertEquals(MemorySegment.mismatch(ss2.segment, ss2.offset, ss2.endOffset(), ss1.segment, ss1.offset, i), expected);
        }
    }

    @Test(dataProvider = "slices")
    public void testDifferentValues(MemorySegment s1, MemorySegment s2) {
        out.format("testDifferentValues s1:%s, s2:%s\n", s1, s2);
        s1 = initializeSegment(s1);
        s2 = initializeSegment(s2);

        for (long i = s2.byteSize() -1 ; i >= 0; i--) {
            long expectedMismatchOffset = i;
            s2.set(ValueLayout.JAVA_BYTE, i, (byte) 0xFF);

            if (s1.byteSize() == s2.byteSize()) {
                assertEquals(s1.mismatch(s2), expectedMismatchOffset);
                assertEquals(s2.mismatch(s1), expectedMismatchOffset);
            } else if (s1.byteSize() > s2.byteSize()) {
                assertEquals(s1.mismatch(s2), expectedMismatchOffset);
                assertEquals(s2.mismatch(s1), expectedMismatchOffset);
            } else {
                assert s1.byteSize() < s2.byteSize();
                var off = Math.min(s1.byteSize(), expectedMismatchOffset);
                assertEquals(s1.mismatch(s2), off);  // proper prefix
                assertEquals(s2.mismatch(s1), off);
            }
        }
    }

    @Test(dataProvider = "slicesStatic")
    public void testDifferentValuesStatic(SliceOffsetAndSize ss1, SliceOffsetAndSize ss2) {
        out.format("testDifferentValues s1:%s, s2:%s\n", ss1, ss2);

        for (long i = ss2.size - 1 ; i >= 0; i--) {
            if (i >= ss1.size) continue;
            initializeSegment(ss1.toSlice());
            initializeSegment(ss2.toSlice());
            long expectedMismatchOffset = i;
            ss2.toSlice().set(ValueLayout.JAVA_BYTE, i, (byte) 0xFF);

            for (long j = expectedMismatchOffset + 1 ; j < ss2.size ; j++) {
                assertEquals(MemorySegment.mismatch(ss1.segment, ss1.offset, ss1.endOffset(), ss2.segment, ss2.offset, j + ss2.offset), expectedMismatchOffset);
            }
            for (long j = expectedMismatchOffset + 1 ; j < ss1.size ; j++) {
                assertEquals(MemorySegment.mismatch(ss2.segment, ss2.offset, ss2.endOffset(), ss1.segment, ss1.offset, j + ss1.offset), expectedMismatchOffset);
            }
        }
    }

    @Test
    public void testEmpty() {
        var s1 = MemorySegment.ofArray(new byte[0]);
        assertEquals(s1.mismatch(s1), -1);
        try (Arena arena = Arena.ofConfined()) {
            var nativeSegment = arena.allocate(4, 4);;
            var s2 = nativeSegment.asSlice(0, 0);
            assertEquals(s1.mismatch(s2), -1);
            assertEquals(s2.mismatch(s1), -1);
        }
    }

    @Test
    public void testLarge() {
        // skip if not on 64 bits
        if (ValueLayout.ADDRESS.byteSize() > 32) {
            try (Arena arena = Arena.ofConfined()) {
                var s1 = arena.allocate((long) Integer.MAX_VALUE + 10L, 8);;
                var s2 = arena.allocate((long) Integer.MAX_VALUE + 10L, 8);;
                assertEquals(s1.mismatch(s1), -1);
                assertEquals(s1.mismatch(s2), -1);
                assertEquals(s2.mismatch(s1), -1);

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
            assertEquals(s3.mismatch(s3), -1);
            assertEquals(s3.mismatch(s4), -1);
            assertEquals(s4.mismatch(s3), -1);
            // static
            assertEquals(MemorySegment.mismatch(s1, 0, s1.byteSize(), s1, 0, i), -1);
            assertEquals(MemorySegment.mismatch(s2, 0, s1.byteSize(), s1, 0, i), -1);
            assertEquals(MemorySegment.mismatch(s1, 0, s1.byteSize(), s2, 0, i), -1);
        }
    }

    private void testLargeMismatchAcrossMaxBoundary(MemorySegment s1, MemorySegment s2) {
        for (long i = s2.byteSize() -1 ; i >= Integer.MAX_VALUE - 10L; i--) {
            s2.set(ValueLayout.JAVA_BYTE, i, (byte) 0xFF);
            long expectedMismatchOffset = i;
            assertEquals(s1.mismatch(s2), expectedMismatchOffset);
            assertEquals(s2.mismatch(s1), expectedMismatchOffset);
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
        assertEquals(match, -1);

        long noMatch = MemorySegment.mismatch(
                segment, 0L, 4L,
                segment, 1L, 5L);
        assertEquals(noMatch, 0);

        long noMatchEnd = MemorySegment.mismatch(
                segment, 0L, 2L,
                segment, 8L, 10L);
        assertEquals(noMatchEnd, 1);

        long same = MemorySegment.mismatch(
                segment, 0L, 8L,
                segment, 0L, 8L);
        assertEquals(same, -1);
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

    @DataProvider(name = "slicesStatic")
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

    @DataProvider(name = "slices")
    static Object[][] slices() {
        Object[][] slicesStatic = slicesStatic();
        return Stream.of(slicesStatic)
                .map(arr -> new Object[]{
                        ((SliceOffsetAndSize) arr[0]).toSlice(),
                        ((SliceOffsetAndSize) arr[1]).toSlice()
                }).toArray(Object[][]::new);
    }
}
