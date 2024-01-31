/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestDereferencePath
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;

import java.lang.foreign.ValueLayout;

import org.testng.annotations.*;

import java.lang.invoke.VarHandle;
import static org.testng.Assert.*;

public class TestDereferencePath {

    static final MemoryLayout C = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("x")
    );

    static final MemoryLayout B = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("c")
                               .withTargetLayout(C)
    );

    static final MemoryLayout A = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("b")
                               .withTargetLayout(B)
    );

    static final VarHandle abcx = A.varHandle(
            PathElement.groupElement("b"), PathElement.dereferenceElement(),
            PathElement.groupElement("c"), PathElement.dereferenceElement(),
            PathElement.groupElement("x"));

    @Test
    public void testSingle() {
        try (Arena arena = Arena.ofConfined()) {
            // init structs
            MemorySegment a = arena.allocate(A);
            MemorySegment b = arena.allocate(B);
            MemorySegment c = arena.allocate(C);
            // init struct fields
            a.set(ValueLayout.ADDRESS, 0, b);
            b.set(ValueLayout.ADDRESS, 0, c);
            c.set(ValueLayout.JAVA_INT, 0, 42);
            // dereference
            int val = (int) abcx.get(a, 0L);
            assertEquals(val, 42);
        }
    }

    static final MemoryLayout B_MULTI = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("cs")
                    .withTargetLayout(MemoryLayout.sequenceLayout(2, C))
    );

    static final MemoryLayout A_MULTI = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("bs")
                    .withTargetLayout(MemoryLayout.sequenceLayout(2, B_MULTI))
    );

    static final VarHandle abcx_multi = A_MULTI.varHandle(
            PathElement.groupElement("bs"), PathElement.dereferenceElement(), PathElement.sequenceElement(),
            PathElement.groupElement("cs"), PathElement.dereferenceElement(), PathElement.sequenceElement(),
            PathElement.groupElement("x"));

    @Test
    public void testMulti() {
        try (Arena arena = Arena.ofConfined()) {
            // init structs
            MemorySegment a = arena.allocate(A);
            MemorySegment b = arena.allocate(B, 2);
            MemorySegment c = arena.allocate(C, 4);
            // init struct fields
            a.set(ValueLayout.ADDRESS, 0, b);
            b.set(ValueLayout.ADDRESS, 0, c);
            b.setAtIndex(ValueLayout.ADDRESS, 1, c.asSlice(C.byteSize() * 2));
            c.setAtIndex(ValueLayout.JAVA_INT, 0, 1);
            c.setAtIndex(ValueLayout.JAVA_INT, 1, 2);
            c.setAtIndex(ValueLayout.JAVA_INT, 2, 3);
            c.setAtIndex(ValueLayout.JAVA_INT, 3, 4);
            // dereference
            int val00 = (int) abcx_multi.get(a, 0L, 0, 0); // a->b[0]->c[0] = 1
            assertEquals(val00, 1);
            int val10 = (int) abcx_multi.get(a, 0L, 1, 0); // a->b[1]->c[0] = 3
            assertEquals(val10, 3);
            int val01 = (int) abcx_multi.get(a, 0L, 0, 1); // a->b[0]->c[1] = 2
            assertEquals(val01, 2);
            int val11 = (int) abcx_multi.get(a, 0L, 1, 1); // a->b[1]->c[1] = 4
            assertEquals(val11, 4);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadDerefInSelect() {
        A.select(PathElement.groupElement("b"), PathElement.dereferenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadDerefInOffset() {
        A.byteOffset(PathElement.groupElement("b"), PathElement.dereferenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadDerefInSlice() {
        A.sliceHandle(PathElement.groupElement("b"), PathElement.dereferenceElement());
    }

    static final MemoryLayout A_MULTI_NO_TARGET = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("bs")
    );

    @Test(expectedExceptions = IllegalArgumentException.class)
    void badDerefAddressNoTarget() {
        A_MULTI_NO_TARGET.varHandle(PathElement.groupElement("bs"), PathElement.dereferenceElement());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void badDerefMisAligned() {
        MemoryLayout struct = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_INT).withName("x"));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(struct.byteSize() + 1).asSlice(1);
            VarHandle vhX = struct.varHandle(PathElement.groupElement("x"), PathElement.dereferenceElement());
            vhX.set(segment, 0L, 42); // should throw
        }
    }
}
