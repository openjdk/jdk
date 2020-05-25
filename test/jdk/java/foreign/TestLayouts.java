/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @run testng TestLayouts
 */

import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemoryLayout;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestLayouts {

    @Test(dataProvider = "badLayoutSizes", expectedExceptions = IllegalArgumentException.class)
    public void testBadLayoutSize(SizedLayoutFactory factory, long size) {
        factory.make(size);
    }

    @Test(dataProvider = "badAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadLayoutAlignment(MemoryLayout layout, long alignment) {
        layout.withBitAlignment(alignment);
    }

    @Test
    public void testVLAInStruct() {
        MemoryLayout layout = MemoryLayout.ofStruct(
                MemoryLayouts.JAVA_INT.withName("size"),
                MemoryLayout.ofPaddingBits(32),
                MemoryLayout.ofSequence(MemoryLayouts.JAVA_DOUBLE).withName("arr"));
        assertFalse(layout.hasSize());
        VarHandle size_handle = layout.varHandle(int.class, MemoryLayout.PathElement.groupElement("size"));
        VarHandle array_elem_handle = layout.varHandle(double.class,
                MemoryLayout.PathElement.groupElement("arr"),
                MemoryLayout.PathElement.sequenceElement());
        try (MemorySegment segment = MemorySegment.allocateNative(
                layout.map(l -> ((SequenceLayout)l).withElementCount(4), MemoryLayout.PathElement.groupElement("arr")))) {
            size_handle.set(segment.baseAddress(), 4);
            for (int i = 0 ; i < 4 ; i++) {
                array_elem_handle.set(segment.baseAddress(), i, (double)i);
            }
            //check
            assertEquals(4, (int)size_handle.get(segment.baseAddress()));
            for (int i = 0 ; i < 4 ; i++) {
                assertEquals((double)i, (double)array_elem_handle.get(segment.baseAddress(), i));
            }
        }
    }

    @Test
    public void testVLAInSequence() {
        MemoryLayout layout = MemoryLayout.ofStruct(
                MemoryLayouts.JAVA_INT.withName("size"),
                MemoryLayout.ofPaddingBits(32),
                MemoryLayout.ofSequence(1, MemoryLayout.ofSequence(MemoryLayouts.JAVA_DOUBLE)).withName("arr"));
        assertFalse(layout.hasSize());
        VarHandle size_handle = layout.varHandle(int.class, MemoryLayout.PathElement.groupElement("size"));
        VarHandle array_elem_handle = layout.varHandle(double.class,
                MemoryLayout.PathElement.groupElement("arr"),
                MemoryLayout.PathElement.sequenceElement(0),
                MemoryLayout.PathElement.sequenceElement());
        try (MemorySegment segment = MemorySegment.allocateNative(
                layout.map(l -> ((SequenceLayout)l).withElementCount(4), MemoryLayout.PathElement.groupElement("arr"), MemoryLayout.PathElement.sequenceElement()))) {
            size_handle.set(segment.baseAddress(), 4);
            for (int i = 0 ; i < 4 ; i++) {
                array_elem_handle.set(segment.baseAddress(), i, (double)i);
            }
            //check
            assertEquals(4, (int)size_handle.get(segment.baseAddress()));
            for (int i = 0 ; i < 4 ; i++) {
                assertEquals((double)i, (double)array_elem_handle.get(segment.baseAddress(), i));
            }
        }
    }

    @Test
    public void testIndexedSequencePath() {
        MemoryLayout seq = MemoryLayout.ofSequence(10, MemoryLayouts.JAVA_INT);
        try (MemorySegment segment = MemorySegment.allocateNative(seq)) {
            VarHandle indexHandle = seq.varHandle(int.class, MemoryLayout.PathElement.sequenceElement());
            // init segment
            for (int i = 0 ; i < 10 ; i++) {
                indexHandle.set(segment.baseAddress(), (long)i, i);
            }
            //check statically indexed handles
            for (int i = 0 ; i < 10 ; i++) {
                VarHandle preindexHandle = seq.varHandle(int.class, MemoryLayout.PathElement.sequenceElement(i));
                int expected = (int)indexHandle.get(segment.baseAddress(), (long)i);
                int found = (int)preindexHandle.get(segment.baseAddress());
                assertEquals(expected, found);
            }
        }
    }

    @Test(dataProvider = "unboundLayouts", expectedExceptions = UnsupportedOperationException.class)
    public void testUnboundSize(MemoryLayout layout, long align) {
        layout.bitSize();
    }

    @Test(dataProvider = "unboundLayouts")
    public void testUnboundAlignment(MemoryLayout layout, long align) {
        assertEquals(align, layout.bitAlignment());
    }

    @Test(dataProvider = "unboundLayouts")
    public void testUnboundEquals(MemoryLayout layout, long align) {
        assertTrue(layout.equals(layout));
    }

    @Test(dataProvider = "unboundLayouts")
    public void testUnboundHash(MemoryLayout layout, long align) {
        layout.hashCode();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadUnboundSequenceLayoutResize() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.withElementCount(-1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadBoundSequenceLayoutResize() {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayouts.JAVA_INT);
        seq.withElementCount(-1);
    }

    @Test
    public void testEmptyGroup() {
        MemoryLayout struct = MemoryLayout.ofStruct();
        assertEquals(struct.bitSize(), 0);
        assertEquals(struct.bitAlignment(), 1);

        MemoryLayout union = MemoryLayout.ofUnion();
        assertEquals(union.bitSize(), 0);
        assertEquals(union.bitAlignment(), 1);
    }

    @Test
    public void testStructSizeAndAlign() {
        MemoryLayout struct = MemoryLayout.ofStruct(
                MemoryLayout.ofPaddingBits(8),
                MemoryLayouts.JAVA_BYTE,
                MemoryLayouts.JAVA_CHAR,
                MemoryLayouts.JAVA_INT,
                MemoryLayouts.JAVA_LONG
        );
        assertEquals(struct.byteSize(), 1 + 1 + 2 + 4 + 8);
        assertEquals(struct.byteAlignment(), 8);
    }

    @Test(dataProvider="basicLayouts")
    public void testPaddingNoAlign(MemoryLayout layout) {
        assertEquals(MemoryLayout.ofPaddingBits(layout.bitSize()).bitAlignment(), 1);
    }

    @Test(dataProvider="basicLayouts")
    public void testStructPaddingAndAlign(MemoryLayout layout) {
        MemoryLayout struct = MemoryLayout.ofStruct(
                layout, MemoryLayout.ofPaddingBits(128 - layout.bitSize()));
        assertEquals(struct.bitAlignment(), layout.bitAlignment());
    }

    @Test(dataProvider="basicLayouts")
    public void testUnionPaddingAndAlign(MemoryLayout layout) {
        MemoryLayout struct = MemoryLayout.ofUnion(
                layout, MemoryLayout.ofPaddingBits(128 - layout.bitSize()));
        assertEquals(struct.bitAlignment(), layout.bitAlignment());
    }

    @Test
    public void testUnionSizeAndAlign() {
        MemoryLayout struct = MemoryLayout.ofUnion(
                MemoryLayouts.JAVA_BYTE,
                MemoryLayouts.JAVA_CHAR,
                MemoryLayouts.JAVA_INT,
                MemoryLayouts.JAVA_LONG
        );
        assertEquals(struct.byteSize(), 8);
        assertEquals(struct.byteAlignment(), 8);
    }

    @Test(dataProvider = "layoutKinds")
    public void testPadding(LayoutKind kind) {
        assertEquals(kind == LayoutKind.PADDING, kind.layout.isPadding());
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testAlignmentString(MemoryLayout layout, long bitAlign) {
        long[] alignments = { 8, 16, 32, 64, 128 };
        for (long a : alignments) {
            assertFalse(layout.toString().contains("%"));
            assertEquals(layout.withBitAlignment(a).toString().contains("%"), a != bitAlign);
        }
    }

    @DataProvider(name = "badLayoutSizes")
    public Object[][] factoriesAndSizes() {
        return new Object[][] {
                { SizedLayoutFactory.VALUE_BE, 0 },
                { SizedLayoutFactory.VALUE_BE, -1 },
                { SizedLayoutFactory.VALUE_LE, 0 },
                { SizedLayoutFactory.VALUE_LE, -1 },
                { SizedLayoutFactory.PADDING, 0 },
                { SizedLayoutFactory.PADDING, -1 },
                { SizedLayoutFactory.SEQUENCE, -1 }
        };
    }

    @DataProvider(name = "unboundLayouts")
    public Object[][] unboundLayouts() {
        return new Object[][] {
                { MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT), 32 },
                { MemoryLayout.ofSequence(MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT)), 32 },
                { MemoryLayout.ofSequence(4, MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT)), 32 },
                { MemoryLayout.ofStruct(MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT)), 32 },
                { MemoryLayout.ofStruct(MemoryLayout.ofSequence(MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT))), 32 },
                { MemoryLayout.ofStruct(MemoryLayout.ofSequence(4, MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT))), 32 },
                { MemoryLayout.ofUnion(MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT)), 32 },
                { MemoryLayout.ofUnion(MemoryLayout.ofSequence(MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT))), 32 },
                { MemoryLayout.ofUnion(MemoryLayout.ofSequence(4, MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT))), 32 },
        };
    }

    @DataProvider(name = "badAlignments")
    public Object[][] layoutsAndBadAlignments() {
        LayoutKind[] layoutKinds = LayoutKind.values();
        Object[][] values = new Object[layoutKinds.length * 2][2];
        for (int i = 0; i < layoutKinds.length ; i++) {
            values[i * 2] = new Object[] { layoutKinds[i].layout, 3 }; // smaller than 8
            values[(i * 2) + 1] = new Object[] { layoutKinds[i].layout, 18 }; // not a power of 2
        }
        return values;
    }

    @DataProvider(name = "layoutKinds")
    public Object[][] layoutsKinds() {
        return Stream.of(LayoutKind.values())
                .map(lk -> new Object[] { lk })
                .toArray(Object[][]::new);
    }

    enum SizedLayoutFactory {
        VALUE_LE(size -> MemoryLayout.ofValueBits(size, ByteOrder.LITTLE_ENDIAN)),
        VALUE_BE(size -> MemoryLayout.ofValueBits(size, ByteOrder.BIG_ENDIAN)),
        PADDING(MemoryLayout::ofPaddingBits),
        SEQUENCE(size -> MemoryLayout.ofSequence(size, MemoryLayouts.PAD_8));

        private final LongFunction<MemoryLayout> factory;

        SizedLayoutFactory(LongFunction<MemoryLayout> factory) {
            this.factory = factory;
        }

        MemoryLayout make(long size) {
            return factory.apply(size);
        }
    }

    enum LayoutKind {
        VALUE_LE(MemoryLayouts.BITS_8_LE),
        VALUE_BE(MemoryLayouts.BITS_8_BE),
        PADDING(MemoryLayouts.PAD_8),
        SEQUENCE(MemoryLayout.ofSequence(1, MemoryLayouts.PAD_8)),
        STRUCT(MemoryLayout.ofStruct(MemoryLayouts.PAD_8, MemoryLayouts.PAD_8)),
        UNION(MemoryLayout.ofUnion(MemoryLayouts.PAD_8, MemoryLayouts.PAD_8));

        final MemoryLayout layout;

        LayoutKind(MemoryLayout layout) {
            this.layout = layout;
        }
    }

    @DataProvider(name = "basicLayouts")
    public Object[][] basicLayouts() {
        return Stream.of(basicLayouts)
                .map(l -> new Object[] { l })
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "layoutsAndAlignments")
    public Object[][] layoutsAndAlignments() {
        Object[][] layoutsAndAlignments = new Object[basicLayouts.length * 5][];
        int i = 0;
        //add basic layouts
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { l, l.bitAlignment() };
        }
        //add basic layouts wrapped in a sequence with unspecified size
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.ofSequence(l), l.bitAlignment() };
        }
        //add basic layouts wrapped in a sequence with given size
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.ofSequence(4, l), l.bitAlignment() };
        }
        //add basic layouts wrapped in a struct
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.ofStruct(l), l.bitAlignment() };
        }
        //add basic layouts wrapped in a union
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.ofUnion(l), l.bitAlignment() };
        }
        return layoutsAndAlignments;
    }

    static MemoryLayout[] basicLayouts = {
            MemoryLayouts.JAVA_BYTE,
            MemoryLayouts.JAVA_CHAR,
            MemoryLayouts.JAVA_SHORT,
            MemoryLayouts.JAVA_INT,
            MemoryLayouts.JAVA_FLOAT,
            MemoryLayouts.JAVA_LONG,
            MemoryLayouts.JAVA_DOUBLE,
    };
}
