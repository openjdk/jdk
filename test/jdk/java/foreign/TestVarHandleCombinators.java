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
 * @run testng TestVarHandleCombinators
 */

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jdk.incubator.foreign.MemorySegment;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static org.testng.Assert.assertEquals;

public class TestVarHandleCombinators {

    @Test
    public void testElementAccess() {
        VarHandle vh = MemoryHandles.varHandle(ValueLayout.JAVA_BYTE);

        byte[] arr = { 0, 0, -1, 0 };
        MemorySegment segment = MemorySegment.ofArray(arr);
        assertEquals((byte) vh.get(segment, 2), (byte) -1);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testUnalignedElement() {
        VarHandle vh = MemoryHandles.varHandle(ValueLayout.JAVA_BYTE.withBitAlignment(32));
        MemorySegment segment = MemorySegment.ofArray(new byte[4]);
        vh.get(segment, 2L); //should throw
        //FIXME: the VH only checks the alignment of the segment, which is fine if the VH is derived from layouts,
        //FIXME: but not if the VH is just created from scratch - we need a VH variable to govern this property,
        //FIXME: at least until the VM is fixed
    }

    @Test
    public void testAlign() {
        VarHandle vh = MemoryHandles.varHandle(ValueLayout.JAVA_BYTE.withBitAlignment(16));

        MemorySegment segment = MemorySegment.allocateNative(1, 2, ResourceScope.newImplicitScope());
        vh.set(segment, 0L, (byte) 10); // fine, memory region is aligned
        assertEquals((byte) vh.get(segment, 0L), (byte) 10);
    }

    @Test
    public void testByteOrderLE() {
        VarHandle vh = MemoryHandles.varHandle(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN));
        byte[] arr = new byte[2];
        MemorySegment segment = MemorySegment.ofArray(arr);
        vh.set(segment, 0L, (short) 0xFF);
        assertEquals(arr[0], (byte) 0xFF);
        assertEquals(arr[1], (byte) 0);
    }

    @Test
    public void testByteOrderBE() {
        VarHandle vh = MemoryHandles.varHandle(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN));
        byte[] arr = new byte[2];
        MemorySegment segment = MemorySegment.ofArray(arr);
        vh.set(segment, 0L, (short) 0xFF);
        assertEquals(arr[0], (byte) 0);
        assertEquals(arr[1], (byte) 0xFF);
    }

    @Test
    public void testNestedSequenceAccess() {
        int outer_size = 10;
        int inner_size = 5;

        //[10 : [5 : [x32 i32]]]

        VarHandle vh = MemoryHandles.varHandle(ValueLayout.JAVA_INT.withBitAlignment(32));
        int count = 0;
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(inner_size * outer_size * 8, 4, scope);
            for (long i = 0; i < outer_size; i++) {
                for (long j = 0; j < inner_size; j++) {
                    vh.set(segment, i * 40 + j * 8, count);
                    assertEquals(
                            (int)vh.get(segment.asSlice(i * inner_size * 8), j * 8),
                            count);
                    count++;
                }
            }
        }
    }
}
