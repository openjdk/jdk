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

/*
 * @test
 * @run testng TestLayoutPaths
 */

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;

import org.testng.annotations.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.testng.Assert.*;

public class TestLayoutPaths {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadByteSelectFromSeq() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, JAVA_INT);
        seq.byteOffset(groupElement("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadByteSelectFromStruct() {
        GroupLayout g = MemoryLayout.structLayout(JAVA_INT);
        g.byteOffset(sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadByteSelectFromValue() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, JAVA_INT);
        seq.byteOffset(sequenceElement(), sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownByteStructField() {
        GroupLayout g = MemoryLayout.structLayout(JAVA_INT);
        g.byteOffset(groupElement("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testTooBigGroupElementIndex() {
        GroupLayout g = MemoryLayout.structLayout(JAVA_INT);
        g.byteOffset(groupElement(1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeGroupElementIndex() {
        GroupLayout g = MemoryLayout.structLayout(JAVA_INT);
        g.byteOffset(groupElement(-1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteOutOfBoundsSeqIndex() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, JAVA_INT);
        seq.byteOffset(sequenceElement(6));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeSeqIndex() {
       sequenceElement(-2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteNegativeSeqIndex() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, JAVA_INT);
        seq.byteOffset(sequenceElement(-2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOutOfBoundsSeqRange() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, JAVA_INT);
        seq.byteOffset(sequenceElement(6, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeSeqRange() {
        sequenceElement(-2, 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteNegativeSeqRange() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, JAVA_INT);
        seq.byteOffset(sequenceElement(-2, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIncompleteAccess() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, MemoryLayout.structLayout(JAVA_INT));
        seq.varHandle(sequenceElement());
    }

    @Test
    public void testByteOffsetHandleRange() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, MemoryLayout.structLayout(JAVA_INT));
        seq.byteOffsetHandle(sequenceElement(0, 1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteOffsetHandleBadRange() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(5, MemoryLayout.structLayout(JAVA_INT));
        seq.byteOffsetHandle(sequenceElement(5, 1)); // invalid range (starting position is outside the sequence)
    }

    @Test
    public void testBadAlignmentOfRoot() {
        MemoryLayout struct = MemoryLayout.structLayout(
            JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
            JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("x"));
        assertEquals(struct.byteAlignment(), 4);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(struct.byteSize() + 2, struct.byteAlignment()).asSlice(2);
            assertEquals(seg.address() % JAVA_SHORT.byteAlignment(), 0); // should be aligned
            assertNotEquals(seg.address() % struct.byteAlignment(), 0); // should not be aligned

            String expectedMessage = "Target offset 0 is incompatible with alignment constraint " + struct.byteAlignment() + " (of [i4s2(x)]) for segment MemorySegment";

            VarHandle vhX = struct.varHandle(groupElement("x"));
            IllegalArgumentException iae = expectThrows(IllegalArgumentException.class, () -> {
                vhX.set(seg, 0L, (short) 42);
            });
            assertTrue(iae.getMessage().startsWith(expectedMessage));

            MethodHandle sliceX = struct.sliceHandle(groupElement("x"));
            iae = expectThrows(IllegalArgumentException.class, () -> {
                MemorySegment slice = (MemorySegment) sliceX.invokeExact(seg, 0L);
            });
            assertTrue(iae.getMessage().startsWith(expectedMessage));
        }
    }

    @Test
    public void testWrongTypeRoot() {
        MemoryLayout struct = MemoryLayout.structLayout(
                JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
                JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN)
        );

        var expectedMessage = "Bad layout path: attempting to select a sequence element from a non-sequence layout: [i4i4]";

        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class, () ->
                struct.select(PathElement.sequenceElement()));
        assertEquals(iae.getMessage(), expectedMessage);
    }

    @Test
    public void testWrongTypeEnclosing() {
        MemoryLayout struct = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(2, MemoryLayout.structLayout(
                                JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("3a"),
                                JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("3b")
                        ).withName("2")
                ).withName("1")
        ).withName("0");

        var expectedMessage = "Bad layout path: attempting to select a sequence element from a non-sequence layout: " +
                "[i4(3a)i4(3b)](2), selected from: " +
                "[2:[i4(3a)i4(3b)](2)](1), selected from: " +
                "[[2:[i4(3a)i4(3b)](2)](1)](0)";

        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class, () ->
                struct.select(PathElement.groupElement("1"),
                        PathElement.sequenceElement(),
                        PathElement.sequenceElement()));
        assertEquals(iae.getMessage(), expectedMessage);
    }

    @Test
    public void testBadSequencePathInOffset() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, JAVA_INT);
        // bad path elements
        for (PathElement e : List.of( sequenceElement(), sequenceElement(0, 2) )) {
            try {
                seq.byteOffset(e);
                fail();
            } catch (IllegalArgumentException ex) {
                assertTrue(true);
            }
        }
    }

    @Test
    public void testBadSequencePathInSelect() {
        SequenceLayout seq = MemoryLayout.sequenceLayout(10, JAVA_INT);
        for (PathElement e : List.of( sequenceElement(0), sequenceElement(0, 2) )) {
            try {
                seq.select(e);
                fail();
            } catch (IllegalArgumentException ex) {
                assertTrue(true);
            }
        }
    }

    @Test(dataProvider = "groupSelectors")
    public void testStructPaths(IntFunction<PathElement> groupSelector) {
        long[] offsets = { 0, 1, 3, 7 };
        GroupLayout g = MemoryLayout.structLayout(
                ValueLayout.JAVA_BYTE.withName("0"),
                ValueLayout.JAVA_CHAR_UNALIGNED.withName("1"),
                ValueLayout.JAVA_FLOAT_UNALIGNED.withName("2"),
                ValueLayout.JAVA_LONG_UNALIGNED.withName("3")
        );

        // test select

        for (int i = 0 ; i < 4 ; i++) {
            MemoryLayout selected = g.select(groupSelector.apply(i));
            assertTrue(selected == g.memberLayouts().get(i));
        }

        // test offset

        for (int i = 0 ; i < 4 ; i++) {
            long byteOffset = g.byteOffset(groupSelector.apply(i));
            assertEquals(offsets[i], byteOffset);
        }
    }

    @Test(dataProvider = "groupSelectors")
    public void testUnionPaths(IntFunction<PathElement> groupSelector) {
        long[] offsets = { 0, 0, 0, 0 };
        GroupLayout g = MemoryLayout.unionLayout(
                ValueLayout.JAVA_BYTE.withName("0"),
                ValueLayout.JAVA_CHAR.withName("1"),
                ValueLayout.JAVA_FLOAT.withName("2"),
                ValueLayout.JAVA_LONG.withName("3")
        );

        // test select

        for (int i = 0 ; i < 4 ; i++) {
            MemoryLayout selected = g.select(groupSelector.apply(i));
            assertTrue(selected == g.memberLayouts().get(i));
        }

        // test offset

        for (int i = 0 ; i < 4 ; i++) {
            long byteOffset = g.byteOffset(groupSelector.apply(i));
            assertEquals(offsets[i], byteOffset);
        }
    }

    @DataProvider
    public static Object[][] groupSelectors() {
        return new Object[][] {
                { (IntFunction<PathElement>) PathElement::groupElement }, // by index
                { (IntFunction<PathElement>) i -> PathElement.groupElement(String.valueOf(i)) } // by name
        };
    }

    @Test
    public void testSequencePaths() {
        long[] offsets = { 0, 1, 2, 3 };
        SequenceLayout g = MemoryLayout.sequenceLayout(4, ValueLayout.JAVA_BYTE);

        // test select

        MemoryLayout selected = g.select(sequenceElement());
        assertTrue(selected == ValueLayout.JAVA_BYTE);

        // test offset

        for (int i = 0 ; i < 4 ; i++) {
            long byteOffset = g.byteOffset(sequenceElement(i));
            assertEquals(offsets[i], byteOffset);
        }
    }

    @Test(dataProvider = "testLayouts")
    public void testOffsetHandle(MemoryLayout layout, PathElement[] pathElements, long[] indexes,
                                 long expectedByteOffset) throws Throwable {
        MethodHandle byteOffsetHandle = layout.byteOffsetHandle(pathElements);
        byteOffsetHandle = byteOffsetHandle.asSpreader(long[].class, indexes.length);
        long actualByteOffset = (long) byteOffsetHandle.invokeExact(0L, indexes);
        assertEquals(actualByteOffset, expectedByteOffset);
    }

    @DataProvider
    public static Object[][] testLayouts() {
        List<Object[]> testCases = new ArrayList<>();

        testCases.add(new Object[] {
            MemoryLayout.sequenceLayout(10, JAVA_INT),
            new PathElement[] { sequenceElement() },
            new long[] { 4 },
            JAVA_INT.byteSize() * 4
        });
        testCases.add(new Object[] {
            MemoryLayout.sequenceLayout(10, MemoryLayout.structLayout(JAVA_INT, JAVA_INT.withName("y"))),
            new PathElement[] { sequenceElement(), groupElement("y") },
            new long[] { 4 },
            (JAVA_INT.byteSize() * 2) * 4 + JAVA_INT.byteSize()
        });
        testCases.add(new Object[] {
            MemoryLayout.sequenceLayout(10, MemoryLayout.structLayout(MemoryLayout.paddingLayout(4), JAVA_INT.withName("y"))),
            new PathElement[] { sequenceElement(), groupElement("y") },
            new long[] { 4 },
            (JAVA_INT.byteSize() + 4) * 4 + 4
        });
        testCases.add(new Object[] {
            MemoryLayout.sequenceLayout(10, JAVA_INT),
            new PathElement[] { sequenceElement() },
            new long[] { 4 },
            JAVA_INT.byteSize() * 4
        });
        testCases.add(new Object[] {
            MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(10, JAVA_INT).withName("data")
            ),
            new PathElement[] { groupElement("data"), sequenceElement() },
            new long[] { 4 },
            JAVA_INT.byteSize() * 4
        });

        MemoryLayout complexLayout = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(10,
                MemoryLayout.sequenceLayout(10,
                    MemoryLayout.structLayout(
                        JAVA_INT.withName("x"),
                        JAVA_INT.withName("y")
                    )
                )
            ).withName("data")
        );

        testCases.add(new Object[] {
            complexLayout,
            new PathElement[] { groupElement("data"), sequenceElement(), sequenceElement(), groupElement("x") },
            new long[] { 0, 1 },
            (JAVA_INT.byteSize() * 2)
        });
        testCases.add(new Object[] {
            complexLayout,
            new PathElement[] { groupElement("data"), sequenceElement(), sequenceElement(), groupElement("x") },
            new long[] { 1, 0 },
            (JAVA_INT.byteSize() * 2) * 10
        });
        testCases.add(new Object[] {
            complexLayout,
            new PathElement[] { groupElement("data"), sequenceElement(), sequenceElement(), groupElement("y") },
            new long[] { 0, 1 },
            (JAVA_INT.byteSize() * 2) + JAVA_INT.byteSize()
        });
        testCases.add(new Object[] {
            complexLayout,
            new PathElement[] { groupElement("data"), sequenceElement(), sequenceElement(), groupElement("y") },
            new long[] { 1, 0 },
            (JAVA_INT.byteSize() * 2) * 10 + JAVA_INT.byteSize()
        });

        return testCases.toArray(Object[][]::new);
    }

    @Test(dataProvider = "testLayouts")
    public void testSliceHandle(MemoryLayout layout, PathElement[] pathElements, long[] indexes,
                                long expectedByteOffset) throws Throwable {
        MemoryLayout selected = layout.select(pathElements);
        MethodHandle sliceHandle = layout.sliceHandle(pathElements);
        sliceHandle = sliceHandle.asSpreader(long[].class, indexes.length);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout);
            MemorySegment slice = (MemorySegment) sliceHandle.invokeExact(segment, 0L, indexes);
            assertEquals(slice.address() - segment.address(), expectedByteOffset);
            assertEquals(slice.byteSize(), selected.byteSize());
        }
    }

}
