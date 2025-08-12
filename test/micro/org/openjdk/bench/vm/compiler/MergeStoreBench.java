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
public class MergeStoreBench {
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
    final char[] chars  = new char[NUMBERS    ];

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
    public void setIntB(Blackhole BH) {
        int sum = 0;
        for (int i = 0; i < ints.length; i++) {
            int v = ints[i];
            setIntB(bytes4, i * 4, v);
            sum += v;
        }
        BH.consume(sum);
    }

    @Benchmark
    public void setIntBU(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntBU(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntBV(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            INT_B.set(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntL(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntL(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntLU(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntLU(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntLV(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            INT_L.set(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntRB(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntRB(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntRBU(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntRBU(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntRL(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntRL(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntRLU(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            setIntRLU(bytes4, off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntRU(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            UNSAFE.putInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, Integer.reverseBytes(ints[i]));
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setIntU(Blackhole BH) {
        int off = 0;
        for (int i = ints.length - 1; i >= 0; i--) {
            UNSAFE.putInt(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, ints[i]);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongB(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongB(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongBU(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongBU(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongBV(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            LONG_B.set(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongL(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongL(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongLU(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongLU(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongLV(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            LONG_L.set(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongRB(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongRB(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongRBU(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongRBU(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongRL(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongRL(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongRLU(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            setLongRLU(bytes8, off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongRU(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            UNSAFE.putLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, Long.reverseBytes(longs[i]));
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setLongU(Blackhole BH) {
        int off = 0;
        for (int i = longs.length - 1; i >= 0; i--) {
            UNSAFE.putLong(bytes8, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, longs[i]);
            off += 8;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setCharBS(Blackhole BH) {
        int off = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            putShortB(bytes4, off, chars[i]);
            off += 2;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setCharBV(Blackhole BH) {
        int off = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            CHAR_B.set(bytes4, off, chars[i]);
            off += 2;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setCharLS(Blackhole BH) {
        int off = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            putShortL(bytes4, off, chars[i]);
            off += 2;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setCharLV(Blackhole BH) {
        int off = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            CHAR_L.set(bytes4, off, chars[i]);
            off += 2;
        }
        BH.consume(off);
    }

    @Benchmark
    public void setCharC(Blackhole BH) {
        int off = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            UNSAFE.putChar(bytes4, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, chars[i]);
            off += 2;
        }
        BH.consume(off);
    }

    /*
     * putChars4 and putBytes4 Test whether four constant chars can be MergeStored
     *
     */
    @Benchmark
    public void putBytes4(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putBytes4(bytes4, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putBytes4X(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putBytes4X(bytes4, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putBytes4U(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putBytes4U(bytes4, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    @SuppressWarnings("deprecation")
    public void putBytes4GetBytes(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            "null".getBytes(0, 4, bytes4, off);
            off += 4;
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4B(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4B(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4BU(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4BU(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4BV(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4BV(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4L(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4L(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4LU(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4LU(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4LV(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4LV(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4C(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4C(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    @Benchmark
    public void putChars4S(Blackhole BH) {
        int off = 0;
        for (int i = 0; i < NUMBERS; i++) {
            off = putChars4S(bytes8, off, 'n', 'u', 'l', 'l');
        }
        BH.consume(off);
    }

    static void setIntB(byte[] array, int offset, int value) {
        array[offset    ] = (byte) (value >> 24);
        array[offset + 1] = (byte) (value >> 16);
        array[offset + 2] = (byte) (value >>  8);
        array[offset + 3] = (byte) (value      );
    }

    static void setIntBU(byte[] array, int offset, int value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address    , (byte) (value >> 24));
        UNSAFE.putByte(array, address + 1, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 2, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 3, (byte) (value      ));
    }

    public static void setIntL(byte[] array, int offset, int value) {
        array[offset    ] = (byte)  value;
        array[offset + 1] = (byte) (value >> 8);
        array[offset + 2] = (byte) (value >> 16);
        array[offset + 3] = (byte) (value >> 24);
    }

    public static void setIntLU(byte[] array, int offset, int value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address    , (byte)  value       );
        UNSAFE.putByte(array, address + 1, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 3, (byte) (value >> 24));
    }

    public static void setIntRL(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntL(array, offset, value);
    }

    public static void setIntRLU(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntLU(array, offset, value);
    }

    public static void setIntRB(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntB(array, offset, value);
    }

    public static void setIntRBU(byte[] array, int offset, int value) {
        value = Integer.reverseBytes(value);
        setIntBU(array, offset, value);
    }

    static void setLongB(byte[] array, int offset, long value) {
        array[offset]     = (byte) (value >> 56);
        array[offset + 1] = (byte) (value >> 48);
        array[offset + 2] = (byte) (value >> 40);
        array[offset + 3] = (byte) (value >> 32);
        array[offset + 4] = (byte) (value >> 24);
        array[offset + 5] = (byte) (value >> 16);
        array[offset + 6] = (byte) (value >>  8);
        array[offset + 7] = (byte) (value      );
    }

    public static void setLongL(byte[] array, int offset, long value) {
        array[offset]     = (byte)  value       ;
        array[offset + 1] = (byte) (value >> 8 );
        array[offset + 2] = (byte) (value >> 16);
        array[offset + 3] = (byte) (value >> 24);
        array[offset + 4] = (byte) (value >> 32);
        array[offset + 5] = (byte) (value >> 40);
        array[offset + 6] = (byte) (value >> 48);
        array[offset + 7] = (byte) (value >> 56);
    }

    public static void setLongRL(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongL(array, offset, value);
    }

    public static void setLongRLU(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongLU(array, offset, value);
    }

    public static void setLongRB(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongB(array, offset, value);
    }

    public static void setLongRBU(byte[] array, int offset, long value) {
        value = Long.reverseBytes(value);
        setLongBU(array, offset, value);
    }

    public static void setLongBU(byte[] array, int offset, long value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address    , (byte) (value >> 56));
        UNSAFE.putByte(array, address + 1, (byte) (value >> 48));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 40));
        UNSAFE.putByte(array, address + 3, (byte) (value >> 32));
        UNSAFE.putByte(array, address + 4, (byte) (value >> 24));
        UNSAFE.putByte(array, address + 5, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 6, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 7, (byte)  value       );
    }

    public static void setLongLU(byte[] array, int offset, long value) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(array, address    , (byte)  value       );
        UNSAFE.putByte(array, address + 1, (byte) (value >>  8));
        UNSAFE.putByte(array, address + 2, (byte) (value >> 16));
        UNSAFE.putByte(array, address + 3, (byte) (value >> 24));
        UNSAFE.putByte(array, address + 4, (byte) (value >> 32));
        UNSAFE.putByte(array, address + 5, (byte) (value >> 40));
        UNSAFE.putByte(array, address + 6, (byte) (value >> 48));
        UNSAFE.putByte(array, address + 7, (byte) (value >> 56));
    }

    public int putBytes4(byte[] bytes, int offset, int c0, int c1, int c2, int c3) {
        bytes[offset    ] = (byte) c0;
        bytes[offset + 1] = (byte) c1;
        bytes[offset + 2] = (byte) c2;
        bytes[offset + 3] = (byte) c3;
        return offset + 4;
    }

    public int putBytes4X(byte[] bytes, int offset, int c0, int c1, int c2, int c3) {
        bytes[offset++] = (byte) c0;
        bytes[offset++] = (byte) c1;
        bytes[offset++] = (byte) c2;
        bytes[offset++] = (byte) c3;
        return offset;
    }

    public int putBytes4U(byte[] bytes, int offset, int c0, int c1, int c2, int c3) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putByte(bytes, address    , (byte) c0);
        UNSAFE.putByte(bytes, address + 1, (byte) c1);
        UNSAFE.putByte(bytes, address + 2, (byte) c2);
        UNSAFE.putByte(bytes, address + 3, (byte) c3);
        return offset + 4;
    }

    public int putChars4B(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        putShortB(bytes, offset    , c0);
        putShortB(bytes, offset + 1, c1);
        putShortB(bytes, offset + 2, c2);
        putShortB(bytes, offset + 3, c3);
        return offset + 4;
    }

    public int putChars4BU(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        putShortBU(bytes, offset    , c0);
        putShortBU(bytes, offset + 1, c1);
        putShortBU(bytes, offset + 2, c2);
        putShortBU(bytes, offset + 3, c3);
        return offset + 4;
    }

    public int putChars4BV(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        CHAR_B.set(bytes, offset     , c0);
        CHAR_B.set(bytes, offset + 2, c1);
        CHAR_B.set(bytes, offset + 4, c2);
        CHAR_B.set(bytes, offset + 6, c3);
        return offset + 8;
    }

    public int putChars4L(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        putShortL(bytes, offset    , c0);
        putShortL(bytes, offset + 1, c1);
        putShortL(bytes, offset + 2, c2);
        putShortL(bytes, offset + 3, c3);
        return offset + 4;
    }

    public int putChars4LV(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        CHAR_L.set(bytes, offset    , c0);
        CHAR_L.set(bytes, offset + 2, c1);
        CHAR_L.set(bytes, offset + 4, c2);
        CHAR_L.set(bytes, offset + 6, c3);
        return offset + 8;
    }

    public int putChars4LU(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        putShortLU(bytes, offset    , c0);
        putShortLU(bytes, offset + 1, c1);
        putShortLU(bytes, offset + 2, c2);
        putShortLU(bytes, offset + 3, c3);
        return offset + 4;
    }

    public int putChars4C(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putChar(bytes, address    , c0);
        UNSAFE.putChar(bytes, address + 2, c1);
        UNSAFE.putChar(bytes, address + 4, c2);
        UNSAFE.putChar(bytes, address + 6, c3);
        return offset + 8;
    }

    public int putChars4S(byte[] bytes, int offset, char c0, char c1, char c2, char c3) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
        UNSAFE.putShort(bytes, address    , (short) c0);
        UNSAFE.putShort(bytes, address + 2, (short) c1);
        UNSAFE.putShort(bytes, address + 4, (short) c2);
        UNSAFE.putShort(bytes, address + 6, (short) c3);
        return offset + 8;
    }

    private static void putShortB(byte[] val, int index, int c) {
        index <<= 1;
        val[index    ] = (byte)(c >> 8);
        val[index + 1] = (byte)(c     );
    }

    public static void putShortBU(byte[] array, int offset, int c) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + (offset << 1);
        UNSAFE.putByte(array, address    , (byte) (c >>  8));
        UNSAFE.putByte(array, address + 1, (byte) (c      ));
    }

    private static void putShortL(byte[] val, int index, int c) {
        index <<= 1;
        val[index    ] = (byte)(c     );
        val[index + 1] = (byte)(c >> 8);
    }

    public static void putShortLU(byte[] array, int offset, int c) {
        final long address = Unsafe.ARRAY_BYTE_BASE_OFFSET + (offset << 1);
        UNSAFE.putByte(array, address    , (byte) (c     ));
        UNSAFE.putByte(array, address + 1, (byte) (c >> 8));
    }

    @Fork(value = 1, jvmArgs = {
            "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-XX:+UnlockDiagnosticVMOptions", "-XX:-MergeStores"
    })
    public static class MergeStoresDisabled extends MergeStoreBench {}
}
