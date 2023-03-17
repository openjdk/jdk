/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @run testng TestLayouts
 */

import java.lang.foreign.*;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import org.testng.annotations.*;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class TestLayouts {

    @Test(dataProvider = "badAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadLayoutAlignment(MemoryLayout layout, long alignment) {
        layout.withBitAlignment(alignment);
    }

    @Test(dataProvider = "basicLayoutsAndAddressAndGroups")
    public void testEqualities(MemoryLayout layout) {

        // Use another Type
        MemoryLayout differentType = MemoryLayout.paddingLayout(8);
        assertFalse(layout.equals(differentType));

        // Use another name
        MemoryLayout differentName = layout.withName("CustomName");
        assertFalse(layout.equals(differentName));

        // Use another alignment
        MemoryLayout differentAlignment = layout.withBitAlignment(layout.bitAlignment() * 2);
        assertFalse(layout.equals(differentAlignment));

        // Swap endian
        MemoryLayout differentOrder = JAVA_INT.withOrder(JAVA_INT.order() == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        assertFalse(layout.equals(differentOrder));

        // Something totally different
        assertFalse(layout.equals("A"));

        // Null
        assertFalse(layout.equals(null));

        // Identity
        assertTrue(layout.equals(layout));

        assertFalse(layout.equals(MemoryLayout.sequenceLayout(13, JAVA_LONG)));

        MemoryLayout other = layout.withBitAlignment(128).withBitAlignment(layout.bitAlignment());
        assertTrue(layout.equals(other));

    }

    public void testTargetLayoutEquals() {
        MemoryLayout differentTargetLayout = ADDRESS.withTargetLayout(JAVA_CHAR);
        assertFalse(ADDRESS.equals(differentTargetLayout));
        var equalButNotSame = ADDRESS.withTargetLayout(JAVA_INT).withTargetLayout(JAVA_CHAR);
        assertTrue(differentTargetLayout.equals(equalButNotSame));
    }

    @Test
    public void testIndexedSequencePath() {
        MemoryLayout seq = MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(seq);;
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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadBoundSequenceLayoutResize() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
        seq.withElementCount(-1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testReshape() {
        SequenceLayout layout = MemoryLayout.sequenceLayout(10, JAVA_INT);
        layout.reshape();
    }

    @Test(dataProvider = "basicLayoutsAndAddressAndGroups", expectedExceptions = IllegalArgumentException.class)
    public void testGroupIllegalAlignmentNotPowerOfTwo(MemoryLayout layout) {
        layout.withBitAlignment(3);
    }

    @Test(dataProvider = "basicLayoutsAndAddressAndGroups", expectedExceptions = IllegalArgumentException.class)
    public void testGroupIllegalAlignmentNotGreaterOrEqualTo8(MemoryLayout layout) {
        layout.withBitAlignment(4);
    }

    @Test
    public void testEqualsPadding() {
        PaddingLayout paddingLayout = MemoryLayout.paddingLayout(16);
        testEqualities(paddingLayout);
        PaddingLayout paddingLayout2 = MemoryLayout.paddingLayout(32);
        assertNotEquals(paddingLayout, paddingLayout2);
    }

    @Test
    public void testEmptyGroup() {
        MemoryLayout struct = MemoryLayout.structLayout();
        assertEquals(struct.bitSize(), 0);
        assertEquals(struct.bitAlignment(), 8);

        MemoryLayout union = MemoryLayout.unionLayout();
        assertEquals(union.bitSize(), 0);
        assertEquals(union.bitAlignment(), 8);
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
        assertEquals(struct.byteAlignment(), 8);
    }

    @Test(dataProvider="basicLayouts")
    public void testPaddingNoAlign(MemoryLayout layout) {
        assertEquals(MemoryLayout.paddingLayout(layout.bitSize()).bitAlignment(), 8);
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
        assertEquals(struct.byteAlignment(), 8);
    }

    @Test
    public void testSequenceBadCount() {
        assertThrows(IllegalArgumentException.class, // negative
                () -> MemoryLayout.sequenceLayout(-2, JAVA_SHORT));
    }

    @Test(dataProvider = "basicLayouts")
    public void testSequenceInferredCount(MemoryLayout layout) {
        assertEquals(MemoryLayout.sequenceLayout(layout),
                     MemoryLayout.sequenceLayout(Long.MAX_VALUE / layout.bitSize(), layout));
    }

    public void testSequenceNegativeElementCount() {
        assertThrows(IllegalArgumentException.class, // negative
                () -> MemoryLayout.sequenceLayout(-1, JAVA_SHORT));
    }

    @Test
    public void testSequenceOverflow() {
        assertThrows(IllegalArgumentException.class, // negative
                () -> MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_SHORT));
        assertThrows(IllegalArgumentException.class, // flip back to positive
                () -> MemoryLayout.sequenceLayout(Long.MAX_VALUE/3, JAVA_LONG));
    }

    @Test
    public void testStructOverflow() {
        assertThrows(IllegalArgumentException.class, // negative
                () -> MemoryLayout.structLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE),
                                                MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE)));
        assertThrows(IllegalArgumentException.class, // flip back to positive
                () -> MemoryLayout.structLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE),
                                                MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE),
                                                MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE)));
    }

    @Test
    public void testPadding() {
        var padding = MemoryLayout.paddingLayout(8);
        assertEquals(padding.byteAlignment(), 1);
    }

    @Test
    public void testPaddingInStruct() {
        var padding = MemoryLayout.paddingLayout(8);
        var struct = MemoryLayout.structLayout(padding);
        assertEquals(struct.byteAlignment(), 1);
    }

    @Test
    public void testPaddingIllegalBitSize() {
        for (long bitSize : List.of(-1L, 1L, 7L)) {
            try {
                MemoryLayout.paddingLayout(bitSize);
                fail("bitSize cannot be " + bitSize);
            } catch (IllegalArgumentException ignore) {
                // Happy path
            }
        }
    }

    @Test
    public void testPaddingZeroBitSize() {
        MemoryLayout.paddingLayout(0);
    }

    @Test
    public void testStructToString() {
        StructLayout padding = MemoryLayout.structLayout(JAVA_INT).withName("struct");
        assertEquals(padding.toString(), "[i32](struct)");
        var toStringUnaligned = padding.withBitAlignment(64).toString();
        assertEquals(toStringUnaligned, "64%[i32](struct)");
    }

    @Test(dataProvider = "layoutKinds")
    public void testPadding(LayoutKind kind) {
        assertEquals(kind == LayoutKind.PADDING, kind.layout instanceof PaddingLayout);
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

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSequenceElement() {
        SequenceLayout layout = MemoryLayout.sequenceLayout(10, JAVA_INT);
        // Step must be != 0
        PathElement.sequenceElement(3, 0);
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

    @DataProvider(name = "basicLayoutsAndAddress")
    public Object[][] basicLayoutsAndAddress() {
        return Stream.concat(Stream.of(basicLayouts), Stream.of(ADDRESS))
                .map(l -> new Object[] { l })
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "basicLayoutsAndAddressAndGroups")
    public Object[][] basicLayoutsAndAddressAndGroups() {
        return Stream.concat(Stream.concat(Stream.of(basicLayouts), Stream.of(ADDRESS)), groupLayoutStream())
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

    @DataProvider(name = "groupLayouts")
    public Object[][] groupLayouts() {
        return groupLayoutStream()
                .map(l -> new Object[] { l })
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "validCarriers")
    public Object[][] validCarriers() {
        return Stream.of(
                        boolean.class,
                        byte.class,
                        char.class,
                        short.class,
                        int.class,
                        long.class,
                        float.class,
                        double.class,
                        MemorySegment.class
                )
                .map(l -> new Object[]{l})
                .toArray(Object[][]::new);
    }

    static Stream<MemoryLayout> groupLayoutStream() {
        return Stream.of(
                MemoryLayout.sequenceLayout(10, JAVA_INT),
                MemoryLayout.sequenceLayout(JAVA_INT),
                MemoryLayout.structLayout(JAVA_INT, JAVA_LONG),
                MemoryLayout.unionLayout(JAVA_LONG, JAVA_DOUBLE)
        );
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
