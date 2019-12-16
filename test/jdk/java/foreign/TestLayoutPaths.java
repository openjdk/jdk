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
import static org.testng.Assert.*;

public class TestLayoutPaths {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadSelectFromSeq() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.offset(PathElement.groupElement("foo"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadSelectFromStruct() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.offset(PathElement.sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadSelectFromValue() {
        SequenceLayout seq = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT);
        seq.offset(PathElement.sequenceElement(), PathElement.sequenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownStructField() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.offset(PathElement.groupElement("foo"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullGroupElementName() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT);
        g.offset(PathElement.groupElement(null));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOutOfBoundsSeqIndex() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.offset(PathElement.sequenceElement(6));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeSeqIndex() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.offset(PathElement.sequenceElement(-2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOutOfBoundsSeqRange() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.offset(PathElement.sequenceElement(6, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNegativeSeqRange() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayouts.JAVA_INT);
        seq.offset(PathElement.sequenceElement(-2, 2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIncompleteAccess() {
        SequenceLayout seq = MemoryLayout.ofSequence(5, MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT));
        seq.varHandle(int.class, PathElement.sequenceElement());
    }

    @Test
    public void testBadContainerAlign() {
        GroupLayout g = MemoryLayout.ofStruct(MemoryLayouts.JAVA_INT.withBitAlignment(16).withName("foo")).withBitAlignment(8);
        try {
            g.offset(PathElement.groupElement("foo"));
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
            g.offset(PathElement.groupElement("foo"));
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
}

