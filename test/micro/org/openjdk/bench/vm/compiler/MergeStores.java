/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;


import jdk.internal.misc.Unsafe;
import jdk.internal.util.ByteArrayLittleEndian;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(value = 3, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.util=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class MergeStores {

    public static final int RANGE = 100;

    static Unsafe UNSAFE = Unsafe.getUnsafe();

    @Param("1")
    public static short vS;

    @Param("1")
    public static int vI;

    @Param("1")
    public static long vL;

    public static int offset = 5;
    public static byte[]  aB = new byte[RANGE];
    public static short[] aS = new short[RANGE];
    public static int[]   aI = new int[RANGE];

    // -------------------------------------------
    // -------     Little-Endian API    ----------
    // -------------------------------------------

    // Store a short LE into an array using store bytes in an array
    static void storeShortLE(byte[] bytes, int offset, short value) {
        storeBytes(bytes, offset, (byte)(value >> 0),
                                  (byte)(value >> 8));
    }

    // Store an int LE into an array using store bytes in an array
    static void storeIntLE(byte[] bytes, int offset, int value) {
        storeBytes(bytes, offset, (byte)(value >> 0 ),
                                  (byte)(value >> 8 ),
                                  (byte)(value >> 16),
                                  (byte)(value >> 24));
    }

    // Store an int LE into an array using store bytes in an array
    static void storeLongLE(byte[] bytes, int offset, long value) {
        storeBytes(bytes, offset, (byte)(value >> 0 ),
                                  (byte)(value >> 8 ),
                                  (byte)(value >> 16),
                                  (byte)(value >> 24),
                                  (byte)(value >> 32),
                                  (byte)(value >> 40),
                                  (byte)(value >> 48),
                                  (byte)(value >> 56));
    }

    // Store 2 bytes into an array
    static void storeBytes(byte[] bytes, int offset, byte b0, byte b1) {
        bytes[offset + 0] = b0;
        bytes[offset + 1] = b1;
    }

    // Store 4 bytes into an array
    static void storeBytes(byte[] bytes, int offset, byte b0, byte b1, byte b2, byte b3) {
        bytes[offset + 0] = b0;
        bytes[offset + 1] = b1;
        bytes[offset + 2] = b2;
        bytes[offset + 3] = b3;
    }

    // Store 8 bytes into an array
    static void storeBytes(byte[] bytes, int offset, byte b0, byte b1, byte b2, byte b3,
                                                     byte b4, byte b5, byte b6, byte b7) {
        bytes[offset + 0] = b0;
        bytes[offset + 1] = b1;
        bytes[offset + 2] = b2;
        bytes[offset + 3] = b3;
        bytes[offset + 4] = b4;
        bytes[offset + 5] = b5;
        bytes[offset + 6] = b6;
        bytes[offset + 7] = b7;
    }

    // -------------------------------- BENCHMARKS --------------------------------

    @Benchmark
    public void baseline() {
    }

    @Benchmark
    public byte[] baseline_allocate() {
        byte[] aB = new byte[RANGE];
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_adr0_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[0] = (byte)0x01;
        aB[1] = (byte)0x02;
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_adr1_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[1] = (byte)0x01;
        aB[2] = (byte)0x02;
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)0x01;
        aB[offset + 1] = (byte)0x02;
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putShortUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, (short)0x0201);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setShort(aB, offset, (short)0x0201);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeShortLE(aB, offset, (short)0x0201);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)0x01;
        aB[offset + 1] = (byte)0x02;
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_nonalloc_unsafe() {
        UNSAFE.putShortUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, (short)0x0201);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setShort(aB, offset, (short)0x0201);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_con_offs_nonalloc_leapi() {
        storeShortLE(aB, offset, (short)0x0201);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)(vS >> 0 );
        aB[offset + 1] = (byte)(vS >> 8 );
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putShortUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vS);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setShort(aB, offset, vS);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeShortLE(aB, offset, vS);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)(vS >> 0 );
        aB[offset + 1] = (byte)(vS >> 8 );
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_nonalloc_unsafe() {
        UNSAFE.putShortUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vS);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setShort(aB, offset, vS);
        return aB;
    }

    @Benchmark
    public byte[] store_B2_S_offs_nonalloc_leapi() {
        storeShortLE(aB, offset, vS);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_adr0_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[0] = (byte)0x01;
        aB[1] = (byte)0x02;
        aB[2] = (byte)0x03;
        aB[3] = (byte)0x04;
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_adr1_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[1] = (byte)0x01;
        aB[2] = (byte)0x02;
        aB[3] = (byte)0x03;
        aB[4] = (byte)0x04;
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)0x01;
        aB[offset + 1] = (byte)0x02;
        aB[offset + 2] = (byte)0x03;
        aB[offset + 3] = (byte)0x04;
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putIntUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x04030201);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setInt(aB, offset, 0x04030201);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeIntLE(aB, offset, 0x04030201);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)0x01;
        aB[offset + 1] = (byte)0x02;
        aB[offset + 2] = (byte)0x03;
        aB[offset + 3] = (byte)0x04;
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_nonalloc_unsafe() {
        UNSAFE.putIntUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x04030201);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setInt(aB, offset, 0x04030201);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_con_offs_nonalloc_leapi() {
        storeIntLE(aB, offset, 0x04030201);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)(vI >> 0 );
        aB[offset + 1] = (byte)(vI >> 8 );
        aB[offset + 2] = (byte)(vI >> 16);
        aB[offset + 3] = (byte)(vI >> 24);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putIntUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setInt(aB, offset, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeIntLE(aB, offset, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)(vI >> 0 );
        aB[offset + 1] = (byte)(vI >> 8 );
        aB[offset + 2] = (byte)(vI >> 16);
        aB[offset + 3] = (byte)(vI >> 24);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_nonalloc_unsafe() {
        UNSAFE.putIntUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setInt(aB, offset, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B4_I_offs_nonalloc_leapi() {
        storeIntLE(aB, offset, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_adr0_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[0] = (byte)0x01;
        aB[1] = (byte)0x02;
        aB[2] = (byte)0x03;
        aB[3] = (byte)0x04;
        aB[4] = (byte)0x05;
        aB[5] = (byte)0x06;
        aB[6] = (byte)0x07;
        aB[7] = (byte)0x08;
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_adr1_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[1] = (byte)0x01;
        aB[2] = (byte)0x02;
        aB[3] = (byte)0x03;
        aB[4] = (byte)0x04;
        aB[5] = (byte)0x05;
        aB[6] = (byte)0x06;
        aB[7] = (byte)0x07;
        aB[8] = (byte)0x08;
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)0x01;
        aB[offset + 1] = (byte)0x02;
        aB[offset + 2] = (byte)0x03;
        aB[offset + 3] = (byte)0x04;
        aB[offset + 4] = (byte)0x05;
        aB[offset + 5] = (byte)0x06;
        aB[offset + 6] = (byte)0x07;
        aB[offset + 7] = (byte)0x08;
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x0807060504030201L);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setLong(aB, offset, 0x0807060504030201L);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeLongLE(aB, offset, 0x0807060504030201L);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)0x01;
        aB[offset + 1] = (byte)0x02;
        aB[offset + 2] = (byte)0x03;
        aB[offset + 3] = (byte)0x04;
        aB[offset + 4] = (byte)0x05;
        aB[offset + 5] = (byte)0x06;
        aB[offset + 6] = (byte)0x07;
        aB[offset + 7] = (byte)0x08;
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_nonalloc_unsafe() {
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, 0x0807060504030201L);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setLong(aB, offset, 0x0807060504030201L);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_con_offs_nonalloc_leapi() {
        storeLongLE(aB, offset, 0x0807060504030201L);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)(vL >> 0 );
        aB[offset + 1] = (byte)(vL >> 8 );
        aB[offset + 2] = (byte)(vL >> 16);
        aB[offset + 3] = (byte)(vL >> 24);
        aB[offset + 4] = (byte)(vL >> 32);
        aB[offset + 5] = (byte)(vL >> 40);
        aB[offset + 6] = (byte)(vL >> 48);
        aB[offset + 7] = (byte)(vL >> 56);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vL);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setLong(aB, offset, vL);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeLongLE(aB, offset, vL);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)(vL >> 0 );
        aB[offset + 1] = (byte)(vL >> 8 );
        aB[offset + 2] = (byte)(vL >> 16);
        aB[offset + 3] = (byte)(vL >> 24);
        aB[offset + 4] = (byte)(vL >> 32);
        aB[offset + 5] = (byte)(vL >> 40);
        aB[offset + 6] = (byte)(vL >> 48);
        aB[offset + 7] = (byte)(vL >> 56);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_nonalloc_unsafe() {
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, vL);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setLong(aB, offset, vL);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_L_offs_nonalloc_leapi() {
        storeLongLE(aB, offset, vL);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_allocate_direct() {
        byte[] aB = new byte[RANGE];
        aB[offset + 0] = (byte)(vI >> 0 );
        aB[offset + 1] = (byte)(vI >> 8 );
        aB[offset + 2] = (byte)(vI >> 16);
        aB[offset + 3] = (byte)(vI >> 24);
        aB[offset + 4] = (byte)(vI >> 0 );
        aB[offset + 5] = (byte)(vI >> 8 );
        aB[offset + 6] = (byte)(vI >> 16);
        aB[offset + 7] = (byte)(vI >> 24);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_allocate_unsafe() {
        byte[] aB = new byte[RANGE];
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 0, vI);
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 4, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_allocate_bale() {
        byte[] aB = new byte[RANGE];
        ByteArrayLittleEndian.setInt(aB, offset + 0, vI);
        ByteArrayLittleEndian.setInt(aB, offset + 4, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_allocate_leapi() {
        byte[] aB = new byte[RANGE];
        storeIntLE(aB, offset + 0, vI);
        storeIntLE(aB, offset + 4, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_nonalloc_direct() {
        aB[offset + 0] = (byte)(vI >> 0 );
        aB[offset + 1] = (byte)(vI >> 8 );
        aB[offset + 2] = (byte)(vI >> 16);
        aB[offset + 3] = (byte)(vI >> 24);
        aB[offset + 4] = (byte)(vI >> 0 );
        aB[offset + 5] = (byte)(vI >> 8 );
        aB[offset + 6] = (byte)(vI >> 16);
        aB[offset + 7] = (byte)(vI >> 24);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_nonalloc_unsafe() {
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 0, vI);
        UNSAFE.putLongUnaligned(aB, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset + 4, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_nonalloc_bale() {
        ByteArrayLittleEndian.setInt(aB, offset + 0, vI);
        ByteArrayLittleEndian.setInt(aB, offset + 4, vI);
        return aB;
    }

    @Benchmark
    public byte[] store_B8_I2_offs_nonalloc_leapi() {
        storeIntLE(aB, offset + 0, vI);
        storeIntLE(aB, offset + 4, vI);
        return aB;
    }

    @Benchmark
    public short[] store_S2_con_offs_allocate_direct() {
        short[] aS = new short[RANGE];
        aS[offset + 0] = (short)0x0102;
        aS[offset + 1] = (short)0x0304;
        return aS;
    }

    @Benchmark
    public short[] store_S2_con_offs_nonalloc_direct() {
        aS[offset + 0] = (short)0x0102;
        aS[offset + 1] = (short)0x0304;
        return aS;
    }

    @Benchmark
    public short[] store_S4_con_offs_allocate_direct() {
        short[] aS = new short[RANGE];
        aS[offset + 0] = (short)0x0102;
        aS[offset + 1] = (short)0x0304;
        aS[offset + 2] = (short)0x0506;
        aS[offset + 3] = (short)0x0708;
        return aS;
    }

    @Benchmark
    public short[] store_S4_con_offs_nonalloc_direct() {
        aS[offset + 0] = (short)0x0102;
        aS[offset + 1] = (short)0x0304;
        aS[offset + 2] = (short)0x0506;
        aS[offset + 3] = (short)0x0708;
        return aS;
    }

    @Benchmark
    public int[] store_I2_con_offs_allocate_direct() {
        int[] aI = new int[RANGE];
        aI[offset + 0] = 0x01020304;
        aI[offset + 1] = 0x05060708;
        return aI;
    }

    @Benchmark
    public int[] store_I2_con_offs_nonalloc_direct() {
        aI[offset + 0] = 0x01020304;
        aI[offset + 1] = 0x05060708;
        return aI;
    }

    @Benchmark
    public int[] store_I2_zero_offs_allocate_direct() {
        int[] aI = new int[RANGE];
        aI[offset + 0] = 0;
        aI[offset + 1] = 0;
        return aI;
    }

    @Benchmark
    public int[] store_I2_zero_offs_nonalloc_direct() {
        aI[offset + 0] = 0;
        aI[offset + 1] = 0;
        return aI;
    }
}
