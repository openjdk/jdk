/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.internal.misc.Unsafe;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgs = {"--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"})
public class MergeLoadBench {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final static VarHandle
            INT_L  = MethodHandles.byteArrayViewVarHandle(int[].class , ByteOrder.LITTLE_ENDIAN),
            INT_B  = MethodHandles.byteArrayViewVarHandle(int[].class , ByteOrder.BIG_ENDIAN),
            LONG_L = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN),
            LONG_B = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN),
            CHAR_L = MethodHandles.byteArrayViewVarHandle(char[].class, ByteOrder.LITTLE_ENDIAN),
            CHAR_B = MethodHandles.byteArrayViewVarHandle(char[].class, ByteOrder.BIG_ENDIAN);

    final static int NUMBERS = 8192;

    final byte[] bytes4 = new byte[NUMBERS * 4];
    final byte[] bytes8 = new byte[NUMBERS * 8];
    final int [] ints   = new int [NUMBERS    ];
    final long[] longs  = new long[NUMBERS    ];

    @Setup
    public void setup() {
        Random r = new Random();
        for (int i = 0; i < ints.length; i++) {
            ints[i] = r.nextInt();
            INT_L.set(bytes4, i * 4, i);
        }

        for (int i = 0; i < longs.length; i++) {
            longs[i] = r.nextLong();
            LONG_L.set(bytes8, i * 8, i);
        }
    }

    /*
     * The names of these cases have the following `B/L/V/U` suffixes, which are:
     * ```
     * B BigEndian
     * L LittleEndian
     * V VarHandle
     * U Unsafe
     * R ReverseBytes
     * C Unsafe.getChar & putChar
     * S Unsafe.getShort & putShort
     * ```
     */

    @Benchmark
    public void getIntB(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntB(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntBU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntBU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntBV(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (int) INT_B.get(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntL(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntL(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntLU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntLU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntLV(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (int) INT_L.get(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRB(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRB(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRBU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRBU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRL(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRL(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRLU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += getIntRLU(bytes4, i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntRU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += Integer.reverseBytes(
                    UNSAFE.getInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 4));
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getIntU(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += UNSAFE.getInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 4);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongB(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongBU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongBV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (long) LONG_B.get(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongL(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongLU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongLV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < ints.length; i++) {
            sum += (long) LONG_L.get(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRB(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRBU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRL(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += getLongRLU(bytes8, i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongRU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += Long.reverseBytes(
                    UNSAFE.getLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 8));
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getLongU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            sum += UNSAFE.getLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 8);
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getCharB(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = getCharB(bytes4, i);
            sum += c;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getCharBV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = (char) CHAR_B.get(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 2);
            sum += c;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getCharBU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = getCharBU(bytes4, i);
            sum += c;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getCharL(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = getCharL(bytes4, i);
            sum += c;
        }
        BH.consume(sum);
    }
    @Benchmark
    public void getCharLU(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = getCharLU(bytes4, i);
            sum += c;
        }
        BH.consume(sum);
    }


    @Benchmark
    public void getCharLV(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = (char) CHAR_L.get(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 2);
            sum += c;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void getCharC(Blackhole BH) {
        long sum = 0;
        for (int i = 0; i < longs.length; i++) {
            char c = UNSAFE.getChar(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * 2);
            sum += c;
        }
        BH.consume(sum);
    }

    static int getIntB(byte[] array, int offset) {
        return ((array[offset    ] & 0xff) << 24)
             | ((array[offset + 1] & 0xff) << 16)
             | ((array[offset + 2] & 0xff) <<  8)
             | ((array[offset + 3] & 0xff)      );
    }

    static int getIntBU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return ((UNSAFE.getByte(array, address    ) & 0xff) << 24)
             | ((UNSAFE.getByte(array, address + 1) & 0xff) << 16)
             | ((UNSAFE.getByte(array, address + 2) & 0xff) <<  8)
             | ((UNSAFE.getByte(array, address + 3) & 0xff)      );
    }

    static int getIntL(byte[] array, int offset) {
        return ((array[offset       ] & 0xff)      )
                | ((array[offset + 1] & 0xff) <<  8)
                | ((array[offset + 2] & 0xff) << 16)
                | ((array[offset + 3] & 0xff) << 24);
    }

    static int getIntRB(byte[] array, int offset) {
        return Integer.reverseBytes(getIntB(array, offset));
    }

    static int getIntRBU(byte[] array, int offset) {
        return Integer.reverseBytes(getIntBU(array, offset));
    }

    static int getIntRL(byte[] array, int offset) {
        return Integer.reverseBytes(getIntL(array, offset));
    }

    static int getIntRLU(byte[] array, int offset) {
        return Integer.reverseBytes(getIntLU(array, offset));
    }

    static long getLongB(byte[] array, int offset) {
        return (((long) array[offset    ] & 0xff) << 56)
             | (((long) array[offset + 1] & 0xff) << 48)
             | (((long) array[offset + 2] & 0xff) << 40)
             | (((long) array[offset + 3] & 0xff) << 32)
             | (((long) array[offset + 4] & 0xff) << 24)
             | (((long) array[offset + 5] & 0xff) << 16)
             | (((long) array[offset + 6] & 0xff) << 8)
             | (((long) array[offset + 7] & 0xff)     );
    }

    static long getLongBU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return (((long)(UNSAFE.getByte(array, address)     & 0xff)) << 56)
             | (((long)(UNSAFE.getByte(array, address + 1) & 0xff)) << 48)
             | (((long)(UNSAFE.getByte(array, address + 2) & 0xff)) << 40)
             | (((long)(UNSAFE.getByte(array, address + 3) & 0xff)) << 32)
             | (((long)(UNSAFE.getByte(array, address + 4) & 0xff)) << 24)
             | (((long)(UNSAFE.getByte(array, address + 5) & 0xff)) << 16)
             | (((long)(UNSAFE.getByte(array, address + 6) & 0xff)) <<  8)
             | (((long)(UNSAFE.getByte(array, address + 7) & 0xff))      );
    }

    public static long getLongL(byte[] array, int offset) {
        return (((long) array[offset    ] & 0xff)      )
             | (((long) array[offset + 1] & 0xff) <<  8)
             | (((long) array[offset + 2] & 0xff) << 16)
             | (((long) array[offset + 3] & 0xff) << 24)
             | (((long) array[offset + 4] & 0xff) << 32)
             | (((long) array[offset + 5] & 0xff) << 40)
             | (((long) array[offset + 6] & 0xff) << 48)
             | (((long) array[offset + 7] & 0xff) << 56);
    }

    static long getLongLU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return (((long)(UNSAFE.getByte(array, address    ) & 0xff))      )
             | (((long)(UNSAFE.getByte(array, address + 1) & 0xff)) <<  8)
             | (((long)(UNSAFE.getByte(array, address + 2) & 0xff)) << 16)
             | (((long)(UNSAFE.getByte(array, address + 3) & 0xff)) << 24)
             | (((long)(UNSAFE.getByte(array, address + 4) & 0xff)) << 32)
             | (((long)(UNSAFE.getByte(array, address + 5) & 0xff)) << 40)
             | (((long)(UNSAFE.getByte(array, address + 6) & 0xff)) << 48)
             | (((long)(UNSAFE.getByte(array, address + 7) & 0xff)) << 56);
    }

    static long getLongRB(byte[] array, int offset) {
        return getLongB(array, offset);
    }

    static long getLongRBU(byte[] array, int offset) {
        return getLongBU(array, offset);
    }

    static long getLongRL(byte[] array, int offset) {
        return getLongL(array, offset);
    }

    static long getLongRLU(byte[] array, int offset) {
        return getLongLU(array, offset);
    }

    public static int getIntLU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        return ((UNSAFE.getByte(array, address    ) & 0xff)      )
             | ((UNSAFE.getByte(array, address + 1) & 0xff) <<  8)
             | ((UNSAFE.getByte(array, address + 2) & 0xff) << 16)
             | ((UNSAFE.getByte(array, address + 3) & 0xff) << 24);
    }

    public static char getCharB(byte[] val, int index) {
        index <<= 1;
        return (char)(((val[index    ] & 0xff) << 8)
                    | ((val[index + 1] & 0xff)));
    }

    public static char getCharBR(byte[] val, int index) {
        return Character.reverseBytes(getCharB(val, index));
    }

    public static char getCharL(byte[] val, int index) {
        index <<= 1;
        return (char)(((val[index    ] & 0xff))
                    | ((val[index + 1] & 0xff) << 8));
    }

    public static char getCharLR(byte[] val, int index) {
        return Character.reverseBytes(getCharL(val, index));
    }

    public static char getCharBU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + (offset << 1);
        return (char) (((UNSAFE.getByte(array, address    ) & 0xff) << 8)
                     | ((UNSAFE.getByte(array, address + 1) & 0xff)     ));
    }

    public static char getCharLU(byte[] array, int offset) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + (offset << 1);
        return (char) (((UNSAFE.getByte(array, address    ) & 0xff)     )
                     | ((UNSAFE.getByte(array, address + 1) & 0xff) << 8));
    }
}
