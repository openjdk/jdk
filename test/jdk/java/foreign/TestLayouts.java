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

import jdk.incubator.foreign.*;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import org.testng.annotations.*;

import static jdk.incubator.foreign.ValueLayout.JAVA_BYTE;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;
import static jdk.incubator.foreign.ValueLayout.JAVA_LONG;
import static jdk.incubator.foreign.ValueLayout.JAVA_SHORT;
import static org.testng.Assert.*;

public class TestLayouts {

    @Test(dataProvider = "badAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadLayoutAlignment(MemoryLayout layout, long alignment) {
        layout.withBitAlignment(alignment);
    }

    @Test
    public void testVLAInStruct() {
        MemoryLayout layout = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("size"),
                MemoryLayout.paddingLayout(32),
                MemoryLayout.sequenceLayout(ValueLayout.JAVA_DOUBLE).withName("arr"));
        assertFalse(layout.hasSize());
        VarHandle size_handle = layout.varHandle(MemoryLayout.PathElement.groupElement("size"));
        VarHandle array_elem_handle = layout.varHandle(
                MemoryLayout.PathElement.groupElement("arr"),
                MemoryLayout.PathElement.sequenceElement());
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(
                    layout.map(l -> ((SequenceLayout)l).withElementCount(4), MemoryLayout.PathElement.groupElement("arr")), scope);
            size_handle.set(segment, 4);
            for (int i = 0 ; i < 4 ; i++) {
                array_elem_handle.set(segment, i, (double)i);
            }
            //check
            assertEquals(4, (int)size_handle.get(segment));
            for (int i = 0 ; i < 4 ; i++) {
                assertEquals((double)i, (double)array_elem_handle.get(segment, i));
            }
        }
    }

    @Test
    public void testVLAInSequence() {
        MemoryLayout layout = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("size"),
                MemoryLayout.paddingLayout(32),
                MemoryLayout.sequenceLayout(1, MemoryLayout.sequenceLayout(ValueLayout.JAVA_DOUBLE)).withName("arr"));
        assertFalse(layout.hasSize());
        VarHandle size_handle = layout.varHandle(MemoryLayout.PathElement.groupElement("size"));
        VarHandle array_elem_handle = layout.varHandle(
                MemoryLayout.PathElement.groupElement("arr"),
                MemoryLayout.PathElement.sequenceElement(0),
                MemoryLayout.PathElement.sequenceElement());
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(
                    layout.map(l -> ((SequenceLayout)l).withElementCount(4), MemoryLayout.PathElement.groupElement("arr"), MemoryLayout.PathElement.sequenceElement()), scope);
            size_handle.set(segment, 4);
            for (int i = 0 ; i < 4 ; i++) {
                array_elem_handle.set(segment, i, (double)i);
            }
            //check
            assertEquals(4, (int)size_handle.get(segment));
            for (int i = 0 ; i < 4 ; i++) {
                assertEquals((double)i, (double)array_elem_handle.get(segment, i));
            }
        }
    }

    @Test
    public void testIndexedSequencePath() {
        MemoryLayout seq = MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(seq, scope);
            VarHandle indexHandle = seq.varHandle(MemoryLayout.PathElement.sequenceElement());
            // init segment
            for (int i = 0 ; i < 10 ; i++) {
                indexHandle.set(segment, (long)i, i);
            }
            //check statically indexed handles
            for (int i = 0 ; i < 10 ; i++) {
                VarHandle preindexHandle = seq.varHandle(MemoryLayout.PathElement.sequenceElement(i));
                int expected = (int)indexHandle.get(segment, (long)i);
                int found = (int)preindexHandle.get(segment);
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
        SequenceLayout seq = MemoryLayout.sequenceLayout(ValueLayout.JAVA_INT);
        seq.withElementCount(-1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadBoundSequenceLayoutResize() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
        seq.withElementCount(-1);
    }

    @Test
    public void testEmptyGroup() {
        MemoryLayout struct = MemoryLayout.structLayout();
        assertEquals(struct.bitSize(), 0);
        assertEquals(struct.bitAlignment(), 1);

        MemoryLayout union = MemoryLayout.unionLayout();
        assertEquals(union.bitSize(), 0);
        assertEquals(union.bitAlignment(), 1);
    }

    @Test
    public void testStructSizeAndAlign() {
        MemoryLayout struct = MemoryLayout.structLayout(
                MemoryLayout.paddingLayout(8),
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_CHAR,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG
        );
        assertEquals(struct.byteSize(), 1 + 1 + 2 + 4 + 8);
        assertEquals(struct.byteAlignment(), ValueLayout.ADDRESS.byteAlignment());
    }

    @Test(dataProvider="basicLayouts")
    public void testPaddingNoAlign(MemoryLayout layout) {
        assertEquals(MemoryLayout.paddingLayout(layout.bitSize()).bitAlignment(), 1);
    }

    @Test(dataProvider="basicLayouts")
    public void testStructPaddingAndAlign(MemoryLayout layout) {
        MemoryLayout struct = MemoryLayout.structLayout(
                layout, MemoryLayout.paddingLayout(128 - layout.bitSize()));
        assertEquals(struct.bitAlignment(), layout.bitAlignment());
    }

    @Test(dataProvider="basicLayouts")
    public void testUnionPaddingAndAlign(MemoryLayout layout) {
        MemoryLayout struct = MemoryLayout.unionLayout(
                layout, MemoryLayout.paddingLayout(128 - layout.bitSize()));
        assertEquals(struct.bitAlignment(), layout.bitAlignment());
    }

    @Test
    public void testUnionSizeAndAlign() {
        MemoryLayout struct = MemoryLayout.unionLayout(
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_CHAR,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG
        );
        assertEquals(struct.byteSize(), 8);
        assertEquals(struct.byteAlignment(), ValueLayout.ADDRESS.byteAlignment());
    }

    @Test(dataProvider = "layoutKinds")
    public void testPadding(LayoutKind kind) {
        assertEquals(kind == LayoutKind.PADDING, kind.layout.isPadding());
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testAlignmentString(MemoryLayout layout, long bitAlign) {
        long[] alignments = { 8, 16, 32, 64, 128 };
        for (long a : alignments) {
            if (layout.bitAlignment() == layout.bitSize()) {
                assertFalse(layout.toString().contains("%"));
                assertEquals(layout.withBitAlignment(a).toString().contains("%"), a != bitAlign);
            }
        }
    }

    @DataProvider(name = "unboundLayouts")
    public Object[][] unboundLayouts() {
        ValueLayout alignedInt = JAVA_INT.withBitAlignment(32);
        return new Object[][] {
                { MemoryLayout.sequenceLayout(alignedInt), 32 },
                { MemoryLayout.sequenceLayout(MemoryLayout.sequenceLayout(alignedInt)), 32 },
                { MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(alignedInt)), 32 },
                { MemoryLayout.structLayout(MemoryLayout.sequenceLayout(alignedInt)), 32 },
                { MemoryLayout.structLayout(MemoryLayout.sequenceLayout(MemoryLayout.sequenceLayout(alignedInt))), 32 },
                { MemoryLayout.structLayout(MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(alignedInt))), 32 },
                { MemoryLayout.unionLayout(MemoryLayout.sequenceLayout(alignedInt)), 32 },
                { MemoryLayout.unionLayout(MemoryLayout.sequenceLayout(MemoryLayout.sequenceLayout(alignedInt))), 32 },
                { MemoryLayout.unionLayout(MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(alignedInt))), 32 },
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
        VALUE_LE(size -> valueLayoutForSize((int)size).withOrder(ByteOrder.LITTLE_ENDIAN)),
        VALUE_BE(size -> valueLayoutForSize((int)size).withOrder(ByteOrder.BIG_ENDIAN)),
        PADDING(MemoryLayout::paddingLayout),
        SEQUENCE(size -> MemoryLayout.sequenceLayout(size, MemoryLayout.paddingLayout(8)));

        private final LongFunction<MemoryLayout> factory;

        SizedLayoutFactory(LongFunction<MemoryLayout> factory) {
            this.factory = factory;
        }

        MemoryLayout make(long size) {
            return factory.apply(size);
        }
    }

    static ValueLayout valueLayoutForSize(int size) {
        return switch (size) {
            case 1 -> JAVA_BYTE;
            case 2 -> JAVA_SHORT;
            case 4 -> JAVA_INT;
            case 8 -> JAVA_LONG;
            default -> throw new UnsupportedOperationException();
        };
    }

    enum LayoutKind {
        VALUE(ValueLayout.JAVA_BYTE),
        PADDING(MemoryLayout.paddingLayout(8)),
        SEQUENCE(MemoryLayout.sequenceLayout(1, MemoryLayout.paddingLayout(8))),
        STRUCT(MemoryLayout.structLayout(MemoryLayout.paddingLayout(8), MemoryLayout.paddingLayout(8))),
        UNION(MemoryLayout.unionLayout(MemoryLayout.paddingLayout(8), MemoryLayout.paddingLayout(8)));

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
        Object[][] layoutsAndAlignments = new Object[basicLayouts.length * 4][];
        int i = 0;
        //add basic layouts
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { l, l.bitAlignment() };
        }
        //add basic layouts wrapped in a sequence with given size
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.sequenceLayout(4, l), l.bitAlignment() };
        }
        //add basic layouts wrapped in a struct
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.structLayout(l), l.bitAlignment() };
        }
        //add basic layouts wrapped in a union
        for (MemoryLayout l : basicLayouts) {
            layoutsAndAlignments[i++] = new Object[] { MemoryLayout.unionLayout(l), l.bitAlignment() };
        }
        return layoutsAndAlignments;
    }

    static MemoryLayout[] basicLayouts = {
            ValueLayout.JAVA_BYTE,
            ValueLayout.JAVA_CHAR,
            ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_DOUBLE,
    };
}
