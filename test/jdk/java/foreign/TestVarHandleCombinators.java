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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static org.testng.Assert.assertEquals;

public class TestVarHandleCombinators {

    @Test
    public void testElementAccess() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, ByteOrder.nativeOrder());
        vh = MemoryHandles.withStride(vh, 1);

        byte[] arr = { 0, 0, -1, 0 };
        MemorySegment segment = MemorySegment.ofArray(arr);
        MemoryAddress addr = segment.baseAddress();

        assertEquals((byte) vh.get(addr, 2), (byte) -1);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testUnalignedElement() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, 4, ByteOrder.nativeOrder());
        vh = MemoryHandles.withStride(vh, 2);
        MemorySegment segment = MemorySegment.ofArray(new byte[4]);
        vh.get(segment.baseAddress(), 1L); //should throw
    }

    public void testZeroStrideElement() {
        VarHandle vh = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());
        VarHandle strided_vh = MemoryHandles.withStride(vh, 0);
        MemorySegment segment = MemorySegment.ofArray(new int[] { 42 });
        for (int i = 0 ; i < 100 ; i++) {
            assertEquals((int)vh.get(segment.baseAddress()), strided_vh.get(segment.baseAddress(), (long)i));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testStrideWrongHandle() {
        VarHandle vh = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
        MemoryHandles.withStride(vh, 10);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAlignNotPowerOf2() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, 3, ByteOrder.nativeOrder());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAlignNegative() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, -1, ByteOrder.nativeOrder());
    }

    @Test
    public void testAlign() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, 2, ByteOrder.nativeOrder());

        MemorySegment segment = MemorySegment.allocateNative(1, 2);
        MemoryAddress address = segment.baseAddress();

        vh.set(address, (byte) 10); // fine, memory region is aligned
        assertEquals((byte) vh.get(address), (byte) 10);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testAlignBadAccess() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, 2, ByteOrder.nativeOrder());
        vh = MemoryHandles.withOffset(vh, 1); // offset by 1 byte

        MemorySegment segment = MemorySegment.allocateNative(2, 2);
        MemoryAddress address = segment.baseAddress();

        vh.set(address, (byte) 10); // should be bad align
    }

    public void testZeroOffsetElement() {
        VarHandle vh = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());
        VarHandle offset_vh = MemoryHandles.withOffset(vh, 0);
        MemorySegment segment = MemorySegment.ofArray(new int[] { 42 });
        for (int i = 0 ; i < 100 ; i++) {
            assertEquals((int)vh.get(segment.baseAddress()), offset_vh.get(segment.baseAddress(), (long)i));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testOffsetWrongHandle() {
        VarHandle vh = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
        MemoryHandles.withOffset(vh, 1);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testUnalignedOffset() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, 4, ByteOrder.nativeOrder());
        vh = MemoryHandles.withOffset(vh, 2);
        MemorySegment segment = MemorySegment.ofArray(new byte[4]);
        vh.get(segment.baseAddress()); //should throw
    }

    @Test
    public void testOffset() {
        VarHandle vh = MemoryHandles.varHandle(byte.class, ByteOrder.nativeOrder());
        vh = MemoryHandles.withOffset(vh, 1);

        MemorySegment segment = MemorySegment.ofArray(new byte[2]);
        MemoryAddress address = segment.baseAddress();

        vh.set(address, (byte) 10);
        assertEquals((byte) vh.get(address), (byte) 10);
    }

    @Test
    public void testByteOrderLE() {
        VarHandle vh = MemoryHandles.varHandle(short.class, 2, ByteOrder.LITTLE_ENDIAN);
        byte[] arr = new byte[2];
        MemorySegment segment = MemorySegment.ofArray(arr);
        MemoryAddress address = segment.baseAddress();

        vh.set(address, (short) 0xFF);
        assertEquals(arr[0], (byte) 0xFF);
        assertEquals(arr[1], (byte) 0);
    }

    @Test
    public void testByteOrderBE() {
        VarHandle vh = MemoryHandles.varHandle(short.class, 2, ByteOrder.BIG_ENDIAN);
        byte[] arr = new byte[2];
        MemorySegment segment = MemorySegment.ofArray(arr);
        MemoryAddress address = segment.baseAddress();

        vh.set(address, (short) 0xFF);
        assertEquals(arr[0], (byte) 0);
        assertEquals(arr[1], (byte) 0xFF);
    }

    @Test
    public void testNestedSequenceAccess() {
        int outer_size = 10;
        int inner_size = 5;

        //[10 : [5 : [x32 i32]]]

        VarHandle vh = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());
        vh = MemoryHandles.withOffset(vh, 4);
        VarHandle inner_vh = MemoryHandles.withStride(vh, 8);
        VarHandle outer_vh = MemoryHandles.withStride(inner_vh, 5 * 8);
        int count = 0;
        try (MemorySegment segment = MemorySegment.allocateNative(inner_size * outer_size * 8)) {
            for (long i = 0; i < outer_size; i++) {
                for (long j = 0; j < inner_size; j++) {
                    outer_vh.set(segment.baseAddress(), i, j, count);
                    assertEquals(
                            (int)inner_vh.get(segment.baseAddress().addOffset(i * inner_size * 8), j),
                            count);
                    count++;
                }
            }
        }
    }

    @Test(dataProvider = "badCarriers", expectedExceptions = IllegalArgumentException.class)
    public void testBadCarrier(Class<?> carrier) {
        MemoryHandles.varHandle(carrier, ByteOrder.nativeOrder());
    }

    @DataProvider(name = "badCarriers")
    public Object[][] createBadCarriers() {
        return new Object[][] {
                { void.class },
                { boolean.class },
                { Object.class },
                { int[].class },
                { MemoryAddress.class }
        };
    }

}
