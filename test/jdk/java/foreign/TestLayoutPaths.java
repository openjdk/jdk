/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.SequenceLayout;

import org.testng.annotations.*;

import java.util.List;

import static org.testng.Assert.*;

public class TestLayoutPaths {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadBitSelectFromSeq() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.bitOffset(PathElement.groupElement("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadByteSelectFromSeq() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.byteOffset(PathElement.groupElement("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadBitSelectFromStruct() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.bitOffset(PathElement.sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadByteSelectFromStruct() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.byteOffset(PathElement.sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadBitSelectFromValue() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.bitOffset(PathElement.sequenceElement(), PathElement.sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadByteSelectFromValue() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.byteOffset(PathElement.sequenceElement(), PathElement.sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownBitStructField() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.bitOffset(PathElement.groupElement("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownByteStructField() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.byteOffset(PathElement.groupElement("foo"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullGroupElementName() {
        PathElement.groupElement(null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBitNullGroupElementName() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.bitOffset(PathElement.groupElement(null));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testByteNullGroupElementName() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.byteOffset(PathElement.groupElement(null));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBitOutOfBoundsSeqIndex() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.bitOffset(PathElement.sequenceElement(6));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteOutOfBoundsSeqIndex() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.byteOffset(PathElement.sequenceElement(6));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeSeqIndex() {
       PathElement.sequenceElement(-2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBitNegativeSeqIndex() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.bitOffset(PathElement.sequenceElement(-2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteNegativeSeqIndex() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.byteOffset(PathElement.sequenceElement(-2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOutOfBoundsSeqRange() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.bitOffset(PathElement.sequenceElement(6, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeSeqRange() {
        PathElement.sequenceElement(-2, 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBitNegativeSeqRange() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.bitOffset(PathElement.sequenceElement(-2, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testByteNegativeSeqRange() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.byteOffset(PathElement.sequenceElement(-2, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIncompleteAccess() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT));
        seq.varHandle(int.class, PathElement.sequenceElement());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testBadMultiple() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayout.ofPaddingBits(3), MemoryLayouts.JAVA_INT.withName("foo"));
        g.byteOffset(PathElement.groupElement("foo"));
    }

    @Test
    public void testBadContainerAlign() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT.withBitAlignment(16).withName("foo")).withBitAlignment(8);
        try {
            g.bitOffset(PathElement.groupElement("foo"));
            g.byteOffset(PathElement.groupElement("foo"));
        } catch (Throwable ex) {
            throw new AssertionError(ex); // should be ok!
        }
        try {
            g.varHandle(int.class, PathElement.groupElement("foo")); //ok
            assertTrue(false); //should fail!
        } catch (UnsupportedOperationException ex) {
            //ok
        } catch (Throwable ex) {
            throw new AssertionError(ex); //should fail!
        }
    }

    @Test
    public void testBadAlignOffset() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.PAD_8, MemoryLayouts.JAVA_INT.withBitAlignment(16).withName("foo"));
        try {
            g.bitOffset(PathElement.groupElement("foo"));
            g.byteOffset(PathElement.groupElement("foo"));
        } catch (Throwable ex) {
            throw new AssertionError(ex); // should be ok!
        }
        try {
            g.varHandle(int.class, PathElement.groupElement("foo")); //ok
            assertTrue(false); //should fail!
        } catch (UnsupportedOperationException ex) {
            //ok
        } catch (Throwable ex) {
            throw new AssertionError(ex); //should fail!
        }
    }

    @Test
    public void testBadSequencePathInOffset() {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayouts.JAVA_INT);
        // bad path elements
        for (PathElement e : List.of( PathElement.sequenceElement(), PathElement.sequenceElement(0, 2) )) {
            try {
                seq.bitOffset(e);
                fail();
            } catch (IllegalArgumentException ex) {
                assertTrue(true);
            }
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
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayouts.JAVA_INT);
        for (PathElement e : List.of( PathElement.sequenceElement(0), PathElement.sequenceElement(0, 2) )) {
            try {
                seq.select(e);
                fail();
            } catch (IllegalArgumentException ex) {
                assertTrue(true);
            }
        }
    }

    @Test
    public void testBadSequencePathInMap() {
        SequenceLayout seq = MemoryLayout.ofSequence(10, MemoryLayouts.JAVA_INT);
        for (PathElement e : List.of( PathElement.sequenceElement(0), PathElement.sequenceElement(0, 2) )) {
            try {
                seq.map(l -> l, e);
                fail();
            } catch (IllegalArgumentException ex) {
                assertTrue(true);
            }
        }
    }

    @Test
    public void testStructPaths() {
        long[] offsets = { 0, 8, 24, 56 };
        GroupLayout g = MemoryLayout.ofStruct(
                MemoryLayouts.JAVA_BYTE.withName("1"),
                MemoryLayouts.JAVA_CHAR.withName("2"),
                MemoryLayouts.JAVA_FLOAT.withName("3"),
                MemoryLayouts.JAVA_LONG.withName("4")
        );

        // test select

        for (int i = 1 ; i <= 4 ; i++) {
            MemoryLayout selected = g.select(PathElement.groupElement(String.valueOf(i)));
            assertTrue(selected == g.memberLayouts().get(i - 1));
        }

        // test offset

        for (int i = 1 ; i <= 4 ; i++) {
            long bitOffset = g.bitOffset(PathElement.groupElement(String.valueOf(i)));
            assertEquals(offsets[i - 1], bitOffset);
            long byteOffset = g.byteOffset(PathElement.groupElement(String.valueOf(i)));
            assertEquals((offsets[i - 1]) >>> 3, byteOffset);
        }

        // test map

        for (int i = 1 ; i <= 4 ; i++) {
            GroupLayout g2 = (GroupLayout)g.map(l -> MemoryLayouts.JAVA_DOUBLE, PathElement.groupElement(String.valueOf(i)));
            assertTrue(g2.isStruct());
            for (int j = 0 ; j < 4 ; j++) {
                if (j == i - 1) {
                    assertEquals(g2.memberLayouts().get(j), MemoryLayouts.JAVA_DOUBLE);
                } else {
                    assertEquals(g2.memberLayouts().get(j), g.memberLayouts().get(j));
                }
            }
        }
    }

    @Test
    public void testUnionPaths() {
        long[] offsets = { 0, 0, 0, 0 };
        GroupLayout g = MemoryLayout.ofUnion(
                MemoryLayouts.JAVA_BYTE.withName("1"),
                MemoryLayouts.JAVA_CHAR.withName("2"),
                MemoryLayouts.JAVA_FLOAT.withName("3"),
                MemoryLayouts.JAVA_LONG.withName("4")
        );

        // test select

        for (int i = 1 ; i <= 4 ; i++) {
            MemoryLayout selected = g.select(PathElement.groupElement(String.valueOf(i)));
            assertTrue(selected == g.memberLayouts().get(i - 1));
        }

        // test offset

        for (int i = 1 ; i <= 4 ; i++) {
            long bitOffset = g.bitOffset(PathElement.groupElement(String.valueOf(i)));
            assertEquals(offsets[i - 1], bitOffset);
            long byteOffset = g.byteOffset(PathElement.groupElement(String.valueOf(i)));
            assertEquals((offsets[i - 1]) >>> 3, byteOffset);
        }

        // test map

        for (int i = 1 ; i <= 4 ; i++) {
            GroupLayout g2 = (GroupLayout)g.map(l -> MemoryLayouts.JAVA_DOUBLE, PathElement.groupElement(String.valueOf(i)));
            assertTrue(g2.isUnion());
            for (int j = 0 ; j < 4 ; j++) {
                if (j == i - 1) {
                    assertEquals(g2.memberLayouts().get(j), MemoryLayouts.JAVA_DOUBLE);
                } else {
                    assertEquals(g2.memberLayouts().get(j), g.memberLayouts().get(j));
                }
            }
        }
    }

    @Test
    public void testSequencePaths() {
        long[] offsets = { 0, 8, 16, 24 };
        SequenceLayout g = MemoryLayout.ofSequence(4, MemoryLayouts.JAVA_BYTE);

        // test select

        MemoryLayout selected = g.select(PathElement.sequenceElement());
        assertTrue(selected == MemoryLayouts.JAVA_BYTE);

        // test offset

        for (int i = 0 ; i < 4 ; i++) {
            long bitOffset = g.bitOffset(PathElement.sequenceElement(i));
            assertEquals(offsets[i], bitOffset);
            long byteOffset = g.byteOffset(PathElement.sequenceElement(i));
            assertEquals((offsets[i]) >>> 3, byteOffset);
        }

        // test map

        SequenceLayout seq2 = (SequenceLayout)g.map(l -> MemoryLayouts.JAVA_DOUBLE, PathElement.sequenceElement());
        assertTrue(seq2.elementLayout() == MemoryLayouts.JAVA_DOUBLE);
    }
}

