/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * @test
 * @run testng/othervm -Xverify:all TestSlices
 */
public class TestSlices {

    static MemoryLayout LAYOUT = MemoryLayout.sequenceLayout(2,
            MemoryLayout.sequenceLayout(5, ValueLayout.JAVA_INT));

    static VarHandle VH_ALL = LAYOUT.varHandle(
            MemoryLayout.PathElement.sequenceElement(), MemoryLayout.PathElement.sequenceElement());

    @Test(dataProvider = "slices")
    public void testSlices(VarHandle handle, int lo, int hi, int[] values) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(LAYOUT);;
            //init
            for (long i = 0 ; i < 2 ; i++) {
                for (long j = 0 ; j < 5 ; j++) {
                    VH_ALL.set(segment, 0L, i, j, (int)j + 1 + ((int)i * 5));
                }
            }

            checkSlice(segment, handle, lo, hi, values);
        }
    }

    @Test(dataProvider = "slices")
    public void testSliceBadIndex(VarHandle handle, int lo, int hi, int[] values) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(LAYOUT);;
            assertThrows(() -> handle.get(segment, 0L, lo, 0));
            assertThrows(() -> handle.get(segment, 0L, 0, hi));
        }
    }

    static void checkSlice(MemorySegment segment, VarHandle handle, long i_max, long j_max, int... values) {
        int index = 0;
        for (long i = 0 ; i < i_max ; i++) {
            for (long j = 0 ; j < j_max ; j++) {
                int x = (int) handle.get(segment, 0L, i, j);
                assertEquals(x, values[index++]);
            }
        }
        assertEquals(index, values.length);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceNegativeOffset() {
        MemorySegment.ofArray(new byte[100]).asSlice(-1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceNegativeOffsetGoodSize() {
        MemorySegment.ofArray(new byte[100]).asSlice(-1, 10);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceGoodOffsetNegativeSize() {
        MemorySegment.ofArray(new byte[100]).asSlice(10, -1);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceNegativeOffsetGoodLayout() {
        MemorySegment.ofArray(new byte[100]).asSlice(-1, ValueLayout.JAVA_INT);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceOffsetTooBig() {
        MemorySegment.ofArray(new byte[100]).asSlice(120);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceOffsetTooBigSizeGood() {
        MemorySegment.ofArray(new byte[100]).asSlice(120, 0);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceOffsetOkSizeTooBig() {
        MemorySegment.ofArray(new byte[100]).asSlice(0, 120);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void testSliceLayoutTooBig() {
        MemorySegment.ofArray(new byte[100])
                .asSlice(0, MemoryLayout.sequenceLayout(120, ValueLayout.JAVA_BYTE));
    }

    @Test(dataProvider = "segmentsAndLayouts")
    public void testSliceAlignment(MemorySegment segment, long alignment, ValueLayout layout) {
        boolean badAlign = layout.byteAlignment() > alignment;
        try {
            segment.asSlice(0, layout);
            assertFalse(badAlign);
        } catch (IllegalArgumentException ex) {
            assertTrue(badAlign);
            assertTrue(ex.getMessage().contains("incompatible with alignment constraints"));
        }
    }

    @Test
    public void testSliceAlignmentPowerOfTwo() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(100, 4096);
            for (int i = 8 ; i < 4096 ; i++) {
                boolean badAlign = Long.bitCount(i) != 1; // not a power of two
                try {
                    segment.asSlice(0, 100, i);
                    assertFalse(badAlign);
                } catch (IllegalArgumentException iae) {
                    assertTrue(badAlign);
                }
            }
        }
    }

    @DataProvider(name = "slices")
    static Object[][] slices() {
        return new Object[][] {
                // x
                { VH_ALL, 2, 5, new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 } },
                // x[0::2]
                { LAYOUT.varHandle(MemoryLayout.PathElement.sequenceElement(),
                        MemoryLayout.PathElement.sequenceElement(0, 2)), 2, 3, new int[] { 1, 3, 5, 6, 8, 10 } },
                // x[1::2]
                { LAYOUT.varHandle(MemoryLayout.PathElement.sequenceElement(),
                        MemoryLayout.PathElement.sequenceElement(1, 2)), 2, 2, new int[] { 2, 4, 7, 9 } },
                // x[4::-2]
                { LAYOUT.varHandle(MemoryLayout.PathElement.sequenceElement(),
                        MemoryLayout.PathElement.sequenceElement(4, -2)), 2, 3, new int[] { 5, 3, 1, 10, 8, 6 } },
                // x[3::-2]
                { LAYOUT.varHandle(MemoryLayout.PathElement.sequenceElement(),
                        MemoryLayout.PathElement.sequenceElement(3, -2)), 2, 2, new int[] { 4, 2, 9, 7 } },
        };
    }

    @DataProvider(name = "segmentsAndLayouts")
    static Object[][] segmentsAndLayouts() {
        List<Object[]> segmentsAndLayouts = new ArrayList<>();
        for (SegmentKind sk : SegmentKind.values()) {
            for (LayoutKind lk : LayoutKind.values()) {
                for (int align : new int[]{ 1, 2, 4, 8 }) {
                    if (align > sk.maxAlign) break;
                    segmentsAndLayouts.add(new Object[] { sk.segment.asSlice(align), align, lk.layout });
                }
            }
        }
        return segmentsAndLayouts.toArray(Object[][]::new);
    }

    enum SegmentKind {
        NATIVE(Arena.ofAuto().allocate(100), 8),
        BYTE_ARRAY(MemorySegment.ofArray(new byte[100]), 1),
        CHAR_ARRAY(MemorySegment.ofArray(new char[100]), 2),
        SHORT_ARRAY(MemorySegment.ofArray(new short[100]), 2),
        INT_ARRAY(MemorySegment.ofArray(new int[100]), 4),
        FLOAT_ARRAY(MemorySegment.ofArray(new float[100]), 4),
        LONG_ARRAY(MemorySegment.ofArray(new long[100]), 8),
        DOUBLE_ARRAY(MemorySegment.ofArray(new double[100]), 8);


        final MemorySegment segment;
        final int maxAlign;

        SegmentKind(MemorySegment segment, int maxAlign) {
            this.segment = segment;
            this.maxAlign = maxAlign;
        }
    }

    enum LayoutKind {
        BOOL(ValueLayout.JAVA_BOOLEAN),
        CHAR(ValueLayout.JAVA_CHAR),
        SHORT(ValueLayout.JAVA_SHORT),
        INT(ValueLayout.JAVA_INT),
        FLOAT(ValueLayout.JAVA_FLOAT),
        LONG(ValueLayout.JAVA_LONG),
        DOUBLE(ValueLayout.JAVA_DOUBLE),
        ADDRESS(ValueLayout.ADDRESS);


        final ValueLayout layout;

        LayoutKind(ValueLayout segment) {
            this.layout = segment;
        }
    }
}
