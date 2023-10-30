/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestLayouts
 */

import java.lang.foreign.*;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import org.testng.annotations.*;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class TestLayouts {

    @Test(dataProvider = "badAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadLayoutAlignment(MemoryLayout layout, long alignment) {
        layout.withByteAlignment(alignment);
    }

    @Test(dataProvider = "basicLayoutsAndAddressAndGroups")
    public void testEqualities(MemoryLayout layout) {

        // Use another Type
        MemoryLayout differentType = MemoryLayout.paddingLayout(1);
        assertFalse(layout.equals(differentType));

        // Use another name
        MemoryLayout differentName = layout.withName("CustomName");
        assertFalse(layout.equals(differentName));

        // Use another alignment
        MemoryLayout differentAlignment = layout.withByteAlignment(layout.byteAlignment() * 2);
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

        MemoryLayout other = layout.withByteAlignment(16).withByteAlignment(layout.byteAlignment());
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
                indexHandle.set(segment, 0L, (long)i, i);
            }
            //check statically indexed handles
            for (int i = 0 ; i < 10 ; i++) {
                VarHandle preindexHandle = seq.varHandle(MemoryLayout.PathElement.sequenceElement(i));
                int expected = (int)indexHandle.get(segment, 0L, (long)i);
                int found = (int)preindexHandle.get(segment, 0L);
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
        layout.withByteAlignment(9);
    }

    @Test(dataProvider = "basicLayoutsAndAddressAndGroups", expectedExceptions = IllegalArgumentException.class)
    public void testGroupIllegalAlignmentNotGreaterOrEqualTo1(MemoryLayout layout) {
        layout.withByteAlignment(0);
    }

    @Test
    public void testEqualsPadding() {
        PaddingLayout paddingLayout = MemoryLayout.paddingLayout(2);
        testEqualities(paddingLayout);
        PaddingLayout paddingLayout2 = MemoryLayout.paddingLayout(4);
        assertNotEquals(paddingLayout, paddingLayout2);
    }

    @Test
    public void testEmptyGroup() {
        MemoryLayout struct = MemoryLayout.structLayout();
        assertEquals(struct.byteSize(), 0);
        assertEquals(struct.byteAlignment(), 1);

        MemoryLayout union = MemoryLayout.unionLayout();
        assertEquals(union.byteSize(), 0);
        assertEquals(union.byteAlignment(), 1);
    }

    @Test
    public void testStructSizeAndAlign() {
        MemoryLayout struct = MemoryLayout.structLayout(
                MemoryLayout.paddingLayout(1),
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_CHAR,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG
        );
        assertEquals(struct.byteSize(), 1 + 1 + 2 + 4 + 8);
        assertEquals(struct.byteAlignment(), ADDRESS.byteSize());
    }

    @Test(dataProvider="basicLayouts")
    public void testPaddingNoAlign(MemoryLayout layout) {
        assertEquals(MemoryLayout.paddingLayout(layout.byteSize()).byteAlignment(), 1);
    }

    @Test(dataProvider="basicLayouts")
    public void testStructPaddingAndAlign(MemoryLayout layout) {
        MemoryLayout struct = MemoryLayout.structLayout(
                layout, MemoryLayout.paddingLayout(16 - layout.byteSize()));
        assertEquals(struct.byteAlignment(), layout.byteAlignment());
    }

    @Test(dataProvider="basicLayouts")
    public void testUnionPaddingAndAlign(MemoryLayout layout) {
        MemoryLayout struct = MemoryLayout.unionLayout(
                layout, MemoryLayout.paddingLayout(16 - layout.byteSize()));
        assertEquals(struct.byteAlignment(), layout.byteAlignment());
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
        assertEquals(struct.byteAlignment(), ADDRESS.byteSize());
    }

    @Test
    public void testSequenceBadCount() {
        assertThrows(IllegalArgumentException.class, // negative
                () -> MemoryLayout.sequenceLayout(-2, JAVA_SHORT));
    }

    @Test
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
        assertThrows(IllegalArgumentException.class, // flip back to positive
                () -> MemoryLayout.sequenceLayout(0, JAVA_LONG).withElementCount(Long.MAX_VALUE));
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
        var padding = MemoryLayout.paddingLayout(1);
        assertEquals(padding.byteAlignment(), 1);
    }

    @Test
    public void testPaddingInStruct() {
        var padding = MemoryLayout.paddingLayout(1);
        var struct = MemoryLayout.structLayout(padding);
        assertEquals(struct.byteAlignment(), 1);
    }

    @Test
    public void testPaddingIllegalByteSize() {
        for (long byteSize : List.of(-1L, 0L)) {
            try {
                MemoryLayout.paddingLayout(byteSize);
                fail("byte size cannot be " + byteSize);
            } catch (IllegalArgumentException ignore) {
                // Happy path
            }
        }
    }

    @Test
    public void testStructToString() {
        for (ByteOrder order : List.of(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            String intRepresentation = (order == ByteOrder.LITTLE_ENDIAN ? "i" : "I");
            StructLayout padding = MemoryLayout.structLayout(JAVA_INT.withOrder(order)).withName("struct");
            assertEquals(padding.toString(), "[" + intRepresentation + "4](struct)");
            var toStringUnaligned = padding.withByteAlignment(8).toString();
            assertEquals(toStringUnaligned, "8%[" + intRepresentation + "4](struct)");
        }
    }

    @Test(dataProvider = "layoutKinds")
    public void testPadding(LayoutKind kind) {
        assertEquals(kind == LayoutKind.PADDING, kind.layout instanceof PaddingLayout);
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testAlignmentString(MemoryLayout layout, long byteAlign) {
        long[] alignments = { 1, 2, 4, 8, 16 };
        for (long a : alignments) {
            if (layout.byteAlignment() == byteAlign) {
                assertFalse(layout.toString().contains("%"));
                if (a >= layout.byteAlignment()) {
                    assertEquals(layout.withByteAlignment(a).toString().contains("%"), a != byteAlign);
                }
            }
        }
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testBadByteAlignment(MemoryLayout layout, long byteAlign) {
        long[] alignments = { 1, 2, 4, 8, 16 };
        for (long a : alignments) {
            if (a < byteAlign && !(layout instanceof ValueLayout)) {
                assertThrows(IllegalArgumentException.class, () -> layout.withByteAlignment(a));
            }
        }
    }

    @Test(dataProvider="layoutsAndAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadSequenceElementAlignmentTooBig(MemoryLayout layout, long byteAlign) {
        layout = layout.withByteAlignment(layout.byteSize() * 2); // hyper-align
        MemoryLayout.sequenceLayout(1, layout);
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testBadSequenceElementSizeNotMultipleOfAlignment(MemoryLayout layout, long byteAlign) {
        boolean shouldFail = layout.byteSize() % layout.byteAlignment() != 0;
        try {
            MemoryLayout.sequenceLayout(1, layout);
            assertFalse(shouldFail);
        } catch (IllegalArgumentException ex) {
            assertTrue(shouldFail);
        }
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testBadSpliteratorElementSizeNotMultipleOfAlignment(MemoryLayout layout, long byteAlign) {
        boolean shouldFail = layout.byteSize() % layout.byteAlignment() != 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout);
            segment.spliterator(layout);
            assertFalse(shouldFail);
        } catch (IllegalArgumentException ex) {
            assertTrue(shouldFail);
        }
    }

    @Test(dataProvider="layoutsAndAlignments")
    public void testBadElementsElementSizeNotMultipleOfAlignment(MemoryLayout layout, long byteAlign) {
        boolean shouldFail = layout.byteSize() % layout.byteAlignment() != 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout);
            segment.elements(layout);
            assertFalse(shouldFail);
        } catch (IllegalArgumentException ex) {
            assertTrue(shouldFail);
        }
    }

    @Test(dataProvider="layoutsAndAlignments", expectedExceptions = IllegalArgumentException.class)
    public void testBadStruct(MemoryLayout layout, long byteAlign) {
        layout = layout.withByteAlignment(layout.byteSize() * 2); // hyper-align
        MemoryLayout.structLayout(layout, layout);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSequenceElement() {
        SequenceLayout layout = MemoryLayout.sequenceLayout(10, JAVA_INT);
        // Step must be != 0
        PathElement.sequenceElement(3, 0);
    }

    @Test
    public void testVarHandleCaching() {
        assertSame(JAVA_INT.varHandle(), JAVA_INT.varHandle());
        assertSame(JAVA_INT.withName("foo").varHandle(), JAVA_INT.varHandle());

        assertNotSame(JAVA_INT_UNALIGNED.varHandle(), JAVA_INT.varHandle());
        assertNotSame(ADDRESS.withTargetLayout(JAVA_INT).varHandle(), ADDRESS.varHandle());
    }

    @Test(expectedExceptions=IllegalArgumentException.class,
        expectedExceptionsMessageRegExp=".*Negative offset.*")
    public void testScaleNegativeOffset() {
        JAVA_INT.scale(-1, 0);
    }

    @Test(expectedExceptions=IllegalArgumentException.class,
        expectedExceptionsMessageRegExp=".*Negative index.*")
    public void testScaleNegativeIndex() {
        JAVA_INT.scale(0, -1);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void testScaleAddOverflow() {
        JAVA_INT.scale(Long.MAX_VALUE, 1);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void testScaleMultiplyOverflow() {
        JAVA_INT.scale(0, Long.MAX_VALUE);
    }

    @DataProvider(name = "badAlignments")
    public Object[][] layoutsAndBadAlignments() {
        LayoutKind[] layoutKinds = LayoutKind.values();
        Object[][] values = new Object[layoutKinds.length * 2][2];
        for (int i = 0; i < layoutKinds.length ; i++) {
            values[i * 2] = new Object[] { layoutKinds[i].layout, 0 }; // smaller than 1
            values[(i * 2) + 1] = new Object[] { layoutKinds[i].layout, 5 }; // not a power of 2
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
        SEQUENCE(size -> MemoryLayout.sequenceLayout(size, MemoryLayout.paddingLayout(1)));

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
        PADDING(MemoryLayout.paddingLayout(1)),
        SEQUENCE(MemoryLayout.sequenceLayout(1, MemoryLayout.paddingLayout(1))),
        STRUCT(MemoryLayout.structLayout(MemoryLayout.paddingLayout(1), MemoryLayout.paddingLayout(1))),
        UNION(MemoryLayout.unionLayout(MemoryLayout.paddingLayout(1), MemoryLayout.paddingLayout(1)));

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
        List<Object[]> layoutsAndAlignments = new ArrayList<>();
        int i = 0;
        //add basic layouts
        for (MemoryLayout l : basicLayoutsNoLongDouble) {
            layoutsAndAlignments.add(new Object[] { l, l.byteAlignment() });
        }
        //add basic layouts wrapped in a sequence with given size
        for (MemoryLayout l : basicLayoutsNoLongDouble) {
            layoutsAndAlignments.add(new Object[] { MemoryLayout.sequenceLayout(4, l), l.byteAlignment() });
        }
        //add basic layouts wrapped in a struct
        for (MemoryLayout l1 : basicLayoutsNoLongDouble) {
            for (MemoryLayout l2 : basicLayoutsNoLongDouble) {
                if (l1.byteSize() % l2.byteAlignment() != 0) continue; // second element is not aligned, skip
                long align = Math.max(l1.byteAlignment(), l2.byteAlignment());
                layoutsAndAlignments.add(new Object[]{MemoryLayout.structLayout(l1, l2), align});
            }
        }
        //add basic layouts wrapped in a union
        for (MemoryLayout l1 : basicLayoutsNoLongDouble) {
            for (MemoryLayout l2 : basicLayoutsNoLongDouble) {
                long align = Math.max(l1.byteAlignment(), l2.byteAlignment());
                layoutsAndAlignments.add(new Object[]{MemoryLayout.unionLayout(l1, l2), align});
            }
        }
        return layoutsAndAlignments.toArray(Object[][]::new);
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
                MemoryLayout.structLayout(JAVA_INT, MemoryLayout.paddingLayout(4), JAVA_LONG),
                MemoryLayout.unionLayout(JAVA_LONG, JAVA_DOUBLE)
        );
    }

    static ValueLayout[] basicLayouts = {
            ValueLayout.JAVA_BYTE,
            ValueLayout.JAVA_CHAR,
            ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_FLOAT,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_DOUBLE,
    };

    static MemoryLayout[] basicLayoutsNoLongDouble = Stream.of(basicLayouts)
            .filter(l -> l.carrier() != long.class && l.carrier() != double.class)
            .toArray(MemoryLayout[]::new);
}
