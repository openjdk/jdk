/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.nio;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import static java.nio.ByteOrder.*;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Benchmark for memory access operations on java.nio.Buffer ( and its views )
 *
 * A large number of variants are covered. The individual benchmarks conform to
 * the following convention:
 *   test(Direct|Heap)(Bulk|Loop)(Get|Put)(Byte|Char|Short|Int|Long|Float|Double)(View)?(Swap)?
 *
 * This allows to easily run a subset of particular interest. For example:
 *   Direct only :- "org.openjdk.bench.java.nio.ByteBuffers.testDirect.*"
 *   Char only   :- "org.openjdk.bench.java.nio.ByteBuffers.test.*Char.*"
 *   Bulk only   :- "org.openjdk.bench.java.nio.ByteBuffers.test.*Bulk.*"
 *   Put with Int or Long carrier :-
 *      test(Direct|Heap)(Loop)(Put)(Int|Long)(View)?(Swap)?"
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(3)
public class ByteBuffers {

    @Param({"16", "1024", "131072"})
    private int size;

    public byte dummyByte;
    public char dummyChar;
    public short dummyShort;
    public int dummyInt;
    public long dummyLong;
    public float dummyFloat;
    public double dummyDouble;

    public ByteBuffer   heapBuffer;
    public CharBuffer   heapCharBufferView;
    public ShortBuffer  heapShortBufferView;
    public IntBuffer    heapIntBufferView;
    public LongBuffer   heapLongBufferView;
    public FloatBuffer  heapFloatBufferView;
    public DoubleBuffer heapDoubleBufferView;

    public ByteBuffer   directBuffer;
    public CharBuffer   directCharBufferView;
    public ShortBuffer  directShortBufferView;
    public IntBuffer    directIntBufferView;
    public LongBuffer   directLongBufferView;
    public FloatBuffer  directFloatBufferView;
    public DoubleBuffer directDoubleBufferView;

    public ByteBuffer   heapBufferSwap;
    public CharBuffer   heapCharBufferViewSwap;
    public ShortBuffer  heapShortBufferViewSwap;
    public IntBuffer    heapIntBufferViewSwap;
    public LongBuffer   heapLongBufferViewSwap;
    public FloatBuffer  heapFloatBufferViewSwap;
    public DoubleBuffer heapDoubleBufferViewSwap;

    public ByteBuffer   directBufferSwap;
    public CharBuffer   directCharBufferViewSwap;
    public ShortBuffer  directShortBufferViewSwap;
    public IntBuffer    directIntBufferViewSwap;
    public LongBuffer   directLongBufferViewSwap;
    public FloatBuffer  directFloatBufferViewSwap;
    public DoubleBuffer directDoubleBufferViewSwap;

    public byte[]   dummyByteArray;
    public char[]   dummyCharArray;
    public short[]  dummyShortArray;
    public int[]    dummyIntArray;
    public long[]   dummyLongArray;
    public float[]  dummyFloatArray;
    public double[] dummyDoubleArray;

    @Setup
    public void setup() {
        dummyByteArray   = new byte[size];
        dummyCharArray   = new char[size / 2];
        dummyShortArray  = new short[size / 2];
        dummyIntArray    = new int[size / 4];
        dummyLongArray   = new long[size / 8];
        dummyFloatArray  = new float[size / 4];
        dummyDoubleArray = new double[size / 8];

        heapBuffer = ByteBuffer.allocate(size).order(nativeOrder());
        heapCharBufferView   = heapBuffer.asCharBuffer();
        heapShortBufferView  = heapBuffer.asShortBuffer();
        heapIntBufferView    = heapBuffer.asIntBuffer();
        heapLongBufferView   = heapBuffer.asLongBuffer();
        heapFloatBufferView  = heapBuffer.asFloatBuffer();
        heapDoubleBufferView = heapBuffer.asDoubleBuffer();

        directBuffer = ByteBuffer.allocateDirect(size).order(nativeOrder());
        directCharBufferView   = directBuffer.asCharBuffer();
        directShortBufferView  = directBuffer.asShortBuffer();
        directIntBufferView    = directBuffer.asIntBuffer();
        directLongBufferView   = directBuffer.asLongBuffer();
        directFloatBufferView  = directBuffer.asFloatBuffer();
        directDoubleBufferView = directBuffer.asDoubleBuffer();

        // endianness swapped
        ByteOrder nonNativeOrder = nativeOrder() == BIG_ENDIAN ? LITTLE_ENDIAN : BIG_ENDIAN;

        heapBufferSwap = ByteBuffer.allocate(size).order(nonNativeOrder);
        heapCharBufferViewSwap   = heapBufferSwap.asCharBuffer();
        heapShortBufferViewSwap  = heapBufferSwap.asShortBuffer();
        heapIntBufferViewSwap    = heapBufferSwap.asIntBuffer();
        heapLongBufferViewSwap   = heapBufferSwap.asLongBuffer();
        heapFloatBufferViewSwap  = heapBufferSwap.asFloatBuffer();
        heapDoubleBufferViewSwap = heapBufferSwap.asDoubleBuffer();

        directBufferSwap = ByteBuffer.allocateDirect(size).order(nonNativeOrder);
        directCharBufferViewSwap   = directBufferSwap.asCharBuffer();
        directShortBufferViewSwap  = directBufferSwap.asShortBuffer();
        directIntBufferViewSwap    = directBufferSwap.asIntBuffer();
        directLongBufferViewSwap   = directBufferSwap.asLongBuffer();
        directFloatBufferViewSwap  = directBufferSwap.asFloatBuffer();
        directDoubleBufferViewSwap = directBufferSwap.asDoubleBuffer();
    }

    // ---------------- BULK GET TESTS

    @Benchmark
    public byte[] testHeapBulkGetByte() {
        heapBuffer.get(0, dummyByteArray);
        return dummyByteArray;
    }

    @Benchmark
    public byte[] testDirectBulkGetByte() {
        directBuffer.get(0, dummyByteArray);
        return dummyByteArray;
    }

    // ---------------- BULK PUT TESTS

    @Benchmark
    public byte[] testHeapBulkPutByte() {
        heapBuffer.put(0, dummyByteArray);
        return dummyByteArray;
    }

    @Benchmark
    public byte[] testDirectBulkPutByte() {
        directBuffer.put(0, dummyByteArray);
        return dummyByteArray;
    }

    // ---------------- LOOP GET TESTS

    @Benchmark
    public int testHeapLoopGetByte() {
        return innerLoopGetByte(heapBuffer);
    }

    @Benchmark
    public int testHeapLoopGetChar() {
        return innerLoopGetChar(heapBuffer);
    }

    @Benchmark
    public int testHeapLoopGetShort() {
        return innerLoopGetShort(heapBuffer);
    }

    @Benchmark
    public int testHeapLoopGetInt() {
        return innerLoopGetInt(heapBuffer);
    }

    @Benchmark
    public long testHeapLoopGetLong() {
        return innerLoopGetLong(heapBuffer);
    }

    @Benchmark
    public float testHeapLoopGetFloat() {
        return innerLoopGetFloat(heapBuffer);
    }

    @Benchmark
    public double testHeapLoopGetDouble() {
        return innerLoopGetDouble(heapBuffer);
    }

    @Benchmark
    public int testDirectLoopGetByte() {
        return innerLoopGetByte(directBuffer);
    }

    @Benchmark
    public int testDirectLoopGetChar() {
        return innerLoopGetChar(directBuffer);
    }

    @Benchmark
    public int testDirectLoopGetShort() {
        return innerLoopGetShort(directBuffer);
    }

    @Benchmark
    public int testDirectLoopGetInt() {
        return innerLoopGetInt(directBuffer);
    }

    @Benchmark
    public long testDirectLoopGetLong() {
        return innerLoopGetLong(directBuffer);
    }

    @Benchmark
    public float testDirectLoopGetFloat() {
        return innerLoopGetFloat(directBuffer);
    }

    @Benchmark
    public double testDirectLoopGetDouble() {
        return innerLoopGetDouble(directBuffer);
    }

    // ---------------- LOOP PUT TESTS

    @Benchmark
    public void testHeapLoopPutByte() {
        innerLoopPutByte(heapBuffer);
    }

    @Benchmark
    public void testHeapLoopPutChar() {
        innerLoopPutChar(heapBuffer);
    }

    @Benchmark
    public void testHeapLoopPutShort() {
        innerLoopPutShort(heapBuffer);
    }

    @Benchmark
    public void testHeapLoopPutInt() {
        innerLoopPutInt(heapBuffer);
    }

    @Benchmark
    public void testHeapLoopPutLong() {
        innerLoopPutLong(heapBuffer);
    }

    @Benchmark
    public void testHeapLoopPutFloat() {
        innerLoopPutFloat(heapBuffer);
    }

    @Benchmark
    public void testHeapLoopPutDouble() {
        innerLoopPutDouble(heapBuffer);
    }

    @Benchmark
    public void testDirectLoopPutByte() {
        innerLoopPutByte(directBuffer);
    }

    @Benchmark
    public void testDirectLoopPutChar() {
        innerLoopPutChar(directBuffer);
    }

    @Benchmark
    public void testDirectLoopPutShort() {
        innerLoopPutShort(directBuffer);
    }

    @Benchmark
    public void testDirectLoopPutInt() {
        innerLoopPutInt(directBuffer);
    }

    @Benchmark
    public void testDirectLoopPutLong() {
        innerLoopPutLong(directBuffer);
    }

    @Benchmark
    public void testDirectLoopPutFloat() {
        innerLoopPutFloat(directBuffer);
    }

    @Benchmark
    public void testDirectLoopPutDouble() {
        innerLoopPutDouble(directBuffer);
    }

    // ---------------- Views ----------------

    // ---------------- BULK GET TESTS HEAP (Views)

    @Benchmark
    public char[] testHeapBulkPutCharView() {
        heapCharBufferView.put(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public char[] testHeapBulkGetCharView() {
        heapCharBufferView.get(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public short[] testHeapBulkPutShortView() {
        heapShortBufferView.put(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public short[] testHeapBulkGetShortView() {
        heapShortBufferView.get(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public int[] testHeapBulkPutIntView() {
        heapIntBufferView.put(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public int[] testHeapBulkGetIntView() {
        heapIntBufferView.get(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public long[] testHeapBulkGetLongView() {
        heapLongBufferView.get(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public long[] testHeapBulkPutLongView() {
        heapLongBufferView.put(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public float[] testHeapBulkGetFloatView() {
        heapFloatBufferView.get(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public float[] testHeapBulkPutFloatView() {
        heapFloatBufferView.put(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public double[] testHeapBulkGetDoubleView() {
        heapDoubleBufferView.get(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    @Benchmark
    public double[] testHeapBulkPutDoubleView() {
        heapDoubleBufferView.put(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    // ---------------- BULK GET TESTS Direct (Views)
    @Benchmark
    public char[] testDirectBulkPutCharView() {
        directCharBufferView.put(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public char[] testDirectBulkGetCharView() {
        directCharBufferView.get(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public short[] testDirectBulkPutShortView() {
        directShortBufferView.put(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public short[] testDirectBulkGetShortView() {
        directShortBufferView.get(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public int[] testDirectBulkPutIntView() {
        directIntBufferView.put(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public int[] testDirectBulkGetIntView() {
        directIntBufferView.get(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public long[] testDirectBulkGetLongView() {
        directLongBufferView.get(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public long[] testDirectBulkPutLongView() {
        directLongBufferView.put(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public float[] testDirectBulkGetFloatView() {
        directFloatBufferView.get(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public float[] testDirectBulkPutFloatView() {
        directFloatBufferView.put(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public double[] testDirectBulkGetDoubleView() {
        directDoubleBufferView.get(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    @Benchmark
    public double[] testDirectBulkPutDoubleView() {
        directDoubleBufferView.put(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    // ---------------- LOOP GET TESTS (Views)

    @Benchmark
    public int testHeapLoopGetCharView() {
        return innerLoopGetChar(heapCharBufferView);
    }

    @Benchmark
    public int testHeapLoopGetShortView() {
        return innerLoopGetShort(heapShortBufferView);
    }

    @Benchmark
    public int testHeapLoopGetIntView() {
        return innerLoopGetInt(heapIntBufferView);
    }

    @Benchmark
    public long testHeapLoopGetLongView() {
        return innerLoopGetLong(heapLongBufferView);
    }

    @Benchmark
    public float testHeapLoopGetFloatView() {
        return innerLoopGetFloat(heapFloatBufferView);
    }

    @Benchmark
    public double testHeapLoopGetDoubleView() {
        return innerLoopGetDouble(heapDoubleBufferView);
    }

    @Benchmark
    public int testDirectLoopGetCharView() {
        return innerLoopGetChar(directCharBufferView);
    }

    @Benchmark
    public int testDirectLoopGetShortView() {
        return innerLoopGetShort(directShortBufferView);
    }

    @Benchmark
    public int testDirectLoopGetIntView() {
        return innerLoopGetInt(directIntBufferView);
    }

    @Benchmark
    public long testDirectLoopGetLongView() {
        return innerLoopGetLong(directLongBufferView);
    }

    @Benchmark
    public float testDirectLoopGetFloatView() {
        return innerLoopGetFloat(directFloatBufferView);
    }

    @Benchmark
    public double testDirectLoopGetDoubleView() {
        return innerLoopGetDouble(directDoubleBufferView);
    }

    // ---------------- LOOP PUT TESTS (Views)

    @Benchmark
    public void testHeapLoopPutCharView() {
        innerLoopPutChar(heapCharBufferView);
    }

    @Benchmark
    public void testHeapLoopPutShortView() {
        innerLoopPutShort(heapShortBufferView);
    }

    @Benchmark
    public void testHeapLoopPutIntView() {
        innerLoopPutInt(heapIntBufferView);
    }

    @Benchmark
    public void testHeapLoopPutLongView() {
        innerLoopPutLong(heapLongBufferView);
    }

    @Benchmark
    public void testHeapLoopPutFloatView() {
        innerLoopPutFloat(heapFloatBufferView);
    }

    @Benchmark
    public void testHeapLoopPutDoubleView() {
        innerLoopPutDouble(heapDoubleBufferView);
    }

    @Benchmark
    public void testDirectLoopPutCharView() {
        innerLoopPutChar(directCharBufferView);
    }

    @Benchmark
    public void testDirectLoopPutShortView() {
        innerLoopPutShort(directShortBufferView);
    }

    @Benchmark
    public void testDirectLoopPutIntView() {
        innerLoopPutInt(directIntBufferView);
    }

    @Benchmark
    public void testDirectLoopPutLongView() {
        innerLoopPutLong(directLongBufferView);
    }

    @Benchmark
    public void testDirectLoopPutFloatView() {
        innerLoopPutFloat(directFloatBufferView);
    }

    @Benchmark
    public void testDirectLoopPutDoubleView() {
        innerLoopPutDouble(directDoubleBufferView);
    }

    // -- Swapped endianness follows

    // ---------------- BULK GET TESTS (swap)

    @Benchmark
    public byte[] testHeapBulkGetByteSwap() {
        heapBufferSwap.get(0, dummyByteArray);
        return dummyByteArray;
    }

    @Benchmark
    public byte[] testDirectBulkGetByteSwap() {
        directBufferSwap.get(0, dummyByteArray);
        return dummyByteArray;
    }

    // ---------------- BULK PUT TESTS (swap)

    @Benchmark
    public byte[] testHeapBulkPutByteSwap() {
        heapBufferSwap.put(0, dummyByteArray);
        return dummyByteArray;
    }

    @Benchmark
    public byte[] testDirectBulkPutByteSwap() {
        directBufferSwap.put(0, dummyByteArray);
        return dummyByteArray;
    }

    // ---------------- LOOP GET TESTS (swap)

    @Benchmark
    public int testHeapLoopGetByteSwap() {
        return innerLoopGetByte(heapBufferSwap);
    }

    @Benchmark
    public int testHeapLoopGetCharSwap() {
        return innerLoopGetChar(heapBufferSwap);
    }

    @Benchmark
    public int testHeapLoopGetShortSwap() {
        return innerLoopGetShort(heapBufferSwap);
    }

    @Benchmark
    public int testHeapLoopGetIntSwap() {
        return innerLoopGetInt(heapBufferSwap);
    }

    @Benchmark
    public long testHeapLoopGetLongSwap() {
        return innerLoopGetLong(heapBufferSwap);
    }

    @Benchmark
    public float testHeapLoopGetFloatSwap() {
        return innerLoopGetFloat(heapBufferSwap);
    }

    @Benchmark
    public double testHeapLoopGetDoubleSwap() {
        return innerLoopGetDouble(heapBufferSwap);
    }

    @Benchmark
    public int testDirectLoopGetByteSwap() {
        return innerLoopGetByte(directBufferSwap);
    }

    @Benchmark
    public int testDirectLoopGetCharSwap() {
        return innerLoopGetChar(directBufferSwap);
    }

    @Benchmark
    public int testDirectLoopGetShortSwap() {
        return innerLoopGetShort(directBufferSwap);
    }

    @Benchmark
    public int testDirectLoopGetIntSwap() {
        return innerLoopGetInt(directBufferSwap);
    }

    @Benchmark
    public long testDirectLoopGetLongSwap() {
        return innerLoopGetLong(directBufferSwap);
    }

    @Benchmark
    public float testDirectLoopGetFloatSwap() {
        return innerLoopGetFloat(directBufferSwap);
    }

    @Benchmark
    public double testDirectLoopGetDoubleSwap() {
        return innerLoopGetDouble(directBufferSwap);
    }

    // ---------------- LOOP PUT TESTS (swap)

    @Benchmark
    public void testHeapLoopPutByteSwap() {
        innerLoopPutByte(heapBufferSwap);
    }

    @Benchmark
    public void testHeapLoopPutCharSwap() {
        innerLoopPutChar(heapBufferSwap);
    }

    @Benchmark
    public void testHeapLoopPutShortSwap() {
        innerLoopPutShort(heapBufferSwap);
    }

    @Benchmark
    public void testHeapLoopPutIntSwap() {
        innerLoopPutInt(heapBufferSwap);
    }

    @Benchmark
    public void testHeapLoopPutLongSwap() {
        innerLoopPutLong(heapBufferSwap);
    }

    @Benchmark
    public void testHeapLoopPutFloatSwap() {
        innerLoopPutFloat(heapBufferSwap);
    }

    @Benchmark
    public void testHeapLoopPutDoubleSwap() {
        innerLoopPutDouble(heapBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutByteSwap() {
        innerLoopPutByte(directBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutCharSwap() {
        innerLoopPutChar(directBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutShortSwap() {
        innerLoopPutShort(directBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutIntSwap() {
        innerLoopPutInt(directBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutLongSwap() {
        innerLoopPutLong(directBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutFloatSwap() {
        innerLoopPutFloat(directBufferSwap);
    }

    @Benchmark
    public void testDirectLoopPutDoubleSwap() {
        innerLoopPutDouble(directBufferSwap);
    }

    // ---------------- Views (swap) ----------------

    // ---------------- BULK GET TESTS HEAP (Views) (swap)

    @Benchmark
    public char[] testHeapBulkPutCharViewSwap() {
        heapCharBufferViewSwap.put(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public char[] testHeapBulkGetCharViewSwap() {
        heapCharBufferViewSwap.get(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public short[] testHeapBulkPutShortViewSwap() {
        heapShortBufferViewSwap.put(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public short[] testHeapBulkGetShortViewSwap() {
        heapShortBufferViewSwap.get(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public int[] testHeapBulkPutIntViewSwap() {
        heapIntBufferViewSwap.put(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public int[] testHeapBulkGetIntViewSwap() {
        heapIntBufferViewSwap.get(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public long[] testHeapBulkGetLongViewSwap() {
        heapLongBufferViewSwap.get(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public long[] testHeapBulkPutLongViewSwap() {
        heapLongBufferViewSwap.put(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public float[] testHeapBulkGetFloatViewSwap() {
        heapFloatBufferViewSwap.get(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public float[] testHeapBulkPutFloatViewSwap() {
        heapFloatBufferViewSwap.put(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public double[] testHeapBulkGetDoubleViewSwap() {
        heapDoubleBufferViewSwap.get(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    @Benchmark
    public double[] testHeapBulkPutDoubleViewSwap() {
        heapDoubleBufferViewSwap.put(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    // ---------------- BULK GET TESTS Direct (Views) (swap)
    @Benchmark
    public char[] testDirectBulkPutCharViewSwap() {
        directCharBufferViewSwap.put(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public char[] testDirectBulkGetCharViewSwap() {
        directCharBufferViewSwap.get(0, dummyCharArray);
        return dummyCharArray;
    }

    @Benchmark
    public short[] testDirectBulkPutShortViewSwap() {
        directShortBufferViewSwap.put(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public short[] testDirectBulkGetShortViewSwap() {
        directShortBufferViewSwap.get(0, dummyShortArray);
        return dummyShortArray;
    }

    @Benchmark
    public int[] testDirectBulkPutIntViewSwap() {
        directIntBufferViewSwap.put(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public int[] testDirectBulkGetIntViewSwap() {
        directIntBufferViewSwap.get(0, dummyIntArray);
        return dummyIntArray;
    }

    @Benchmark
    public long[] testDirectBulkGetLongViewSwap() {
        directLongBufferViewSwap.get(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public long[] testDirectBulkPutLongViewSwap() {
        directLongBufferViewSwap.put(0, dummyLongArray);
        return dummyLongArray;
    }

    @Benchmark
    public float[] testDirectBulkGetFloatViewSwap() {
        directFloatBufferViewSwap.get(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public float[] testDirectBulkPutFloatViewSwap() {
        directFloatBufferViewSwap.put(0, dummyFloatArray);
        return dummyFloatArray;
    }

    @Benchmark
    public double[] testDirectBulkGetDoubleViewSwap() {
        directDoubleBufferViewSwap.get(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    @Benchmark
    public double[] testDirectBulkPutDoubleViewSwap() {
        directDoubleBufferViewSwap.put(0, dummyDoubleArray);
        return dummyDoubleArray;
    }

    // ---------------- LOOP GET TESTS (Views) (swap)

    @Benchmark
    public int testHeapLoopGetCharViewSwap() {
        return innerLoopGetChar(heapCharBufferViewSwap);
    }

    @Benchmark
    public int testHeapLoopGetShortViewSwap() {
        return innerLoopGetShort(heapShortBufferViewSwap);
    }

    @Benchmark
    public int testHeapLoopGetIntViewSwap() {
        return innerLoopGetInt(heapIntBufferViewSwap);
    }

    @Benchmark
    public long testHeapLoopGetLongViewSwap() {
        return innerLoopGetLong(heapLongBufferViewSwap);
    }

    @Benchmark
    public float testHeapLoopGetFloatViewSwap() {
        return innerLoopGetFloat(heapFloatBufferViewSwap);
    }

    @Benchmark
    public double testHeapLoopGetDoubleViewSwap() {
        return innerLoopGetDouble(heapDoubleBufferViewSwap);
    }

    @Benchmark
    public int testDirectLoopGetCharViewSwap() {
        return innerLoopGetChar(directCharBufferViewSwap);
    }

    @Benchmark
    public int testDirectLoopGetShortViewSwap() {
        return innerLoopGetShort(directShortBufferViewSwap);
    }

    @Benchmark
    public int testDirectLoopGetIntViewSwap() {
        return innerLoopGetInt(directIntBufferViewSwap);
    }

    @Benchmark
    public long testDirectLoopGetLongViewSwap() {
        return innerLoopGetLong(directLongBufferViewSwap);
    }

    @Benchmark
    public float testDirectLoopGetFloatViewSwap() {
        return innerLoopGetFloat(directFloatBufferViewSwap);
    }

    @Benchmark
    public double testDirectLoopGetDoubleViewSwap() {
        return innerLoopGetDouble(directDoubleBufferViewSwap);
    }

    // ---------------- LOOP PUT TESTS (Views) (swap)

    @Benchmark
    public void testHeapLoopPutCharViewSwap() {
        innerLoopPutChar(heapCharBufferViewSwap);
    }

    @Benchmark
    public void testHeapLoopPutShortViewSwap() {
        innerLoopPutShort(heapShortBufferViewSwap);
    }

    @Benchmark
    public void testHeapLoopPutIntViewSwap() {
        innerLoopPutInt(heapIntBufferViewSwap);
    }

    @Benchmark
    public void testHeapLoopPutLongViewSwap() {
        innerLoopPutLong(heapLongBufferViewSwap);
    }

    @Benchmark
    public void testHeapLoopPutFloatViewSwap() {
        innerLoopPutFloat(heapFloatBufferViewSwap);
    }

    @Benchmark
    public void testHeapLoopPutDoubleViewSwap() {
        innerLoopPutDouble(heapDoubleBufferViewSwap);
    }

    @Benchmark
    public void testDirectLoopPutCharViewSwap() {
        innerLoopPutChar(directCharBufferViewSwap);
    }

    @Benchmark
    public void testDirectLoopPutShortViewSwap() {
        innerLoopPutShort(directShortBufferViewSwap);
    }

    @Benchmark
    public void testDirectLoopPutIntViewSwap() {
        innerLoopPutInt(directIntBufferViewSwap);
    }

    @Benchmark
    public void testDirectLoopPutLongViewSwap() {
        innerLoopPutLong(directLongBufferViewSwap);
    }

    @Benchmark
    public void testDirectLoopPutFloatViewSwap() {
        innerLoopPutFloat(directFloatBufferViewSwap);
    }

    @Benchmark
    public void testDirectLoopPutDoubleViewSwap() {
        innerLoopPutDouble(directDoubleBufferViewSwap);
    }

    // ---------------- HELPER METHODS

    private int innerLoopGetByte(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i++) {
            r += bb.get(i);
        }
        return r;
    }

    private int innerLoopGetChar(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 2) {
            r += bb.getChar(i);
        }
        return r;
    }

    private int innerLoopGetChar(CharBuffer cb) {
        int r = 0;
        for (int i = 0; i < cb.capacity(); i++) {
            r += cb.get(i);
        }
        return r;
    }

    private int innerLoopGetShort(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 2) {
            r += bb.getShort(i);
        }
        return r;
    }

    private int innerLoopGetShort(ShortBuffer sb) {
        int r = 0;
        for (int i = 0; i < sb.capacity(); i++) {
            r += sb.get(i);
        }
        return r;
    }

    private int innerLoopGetInt(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 4) {
            r += bb.getInt(i);
        }
        return r;
    }

    private int innerLoopGetInt(IntBuffer ib) {
        int r = 0;
        for (int i = 0; i < ib.capacity(); i++) {
            r += ib.get(i);
        }
        return r;
    }

    private long innerLoopGetLong(ByteBuffer bb) {
        long r = 0;
        for (int i = 0; i < bb.capacity(); i += 8) {
            r += bb.getLong(i);
        }
        return r;
    }

    private long innerLoopGetLong(LongBuffer lb) {
        long r = 0;
        for (int i = 0; i < lb.capacity(); i++) {
            r += lb.get(i);
        }
        return r;
    }

    private float innerLoopGetFloat(ByteBuffer bb) {
        float r = 0;
        for (int i = 0; i < bb.capacity(); i += 4) {
            r += bb.getFloat(i);
        }
        return r;
    }

    private float innerLoopGetFloat(FloatBuffer fb) {
        float r = 0;
        for (int i = 0; i < fb.capacity(); i++) {
            r += fb.get(i);
        }
        return r;
    }

    private double innerLoopGetDouble(ByteBuffer bb) {
        double d = 0;
        for (int i = 0; i < bb.capacity(); i += 8) {
            d += bb.getDouble(i);
        }
        return d;
    }

    private double innerLoopGetDouble(DoubleBuffer db) {
        double d = 0;
        for (int i = 0; i < db.capacity(); i++) {
            d += db.get(i);
        }
        return d;
    }

    private void innerLoopPutByte(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i++) {
            bb.put(i, dummyByte);
        }
    }

    private void innerLoopPutChar(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 2) {
            bb.putChar(i, dummyChar);
        }
    }

    private void innerLoopPutChar(CharBuffer cb) {
        for (int i = 0; i < cb.capacity(); i++) {
            cb.put(i, dummyChar);
        }
    }

    private void innerLoopPutShort(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 2) {
            bb.putShort(i, dummyShort);
        }
    }

    private void innerLoopPutShort(ShortBuffer sb) {
        for (int i = 0; i < sb.capacity(); i++) {
            sb.put(i, dummyShort);
        }
    }

    private void innerLoopPutInt(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 4) {
            bb.putInt(i, dummyInt);
        }
    }

    private void innerLoopPutInt(IntBuffer ib) {
        for (int i = 0; i < ib.capacity(); i++) {
            ib.put(i, dummyInt);
        }
    }

    private void innerLoopPutLong(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 8) {
            bb.putLong(i, dummyLong);
        }
    }

    private void innerLoopPutLong(LongBuffer lb) {
        for (int i = 0; i < lb.capacity(); i++) {
            lb.put(i, dummyLong);
        }
    }

    private void innerLoopPutFloat(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 4) {
            bb.putFloat(i, dummyFloat);
        }
    }
    private void innerLoopPutFloat(FloatBuffer fb) {
        for (int i = 0; i < fb.capacity(); i++) {
            fb.put(i, dummyFloat);
        }
    }

    private void innerLoopPutDouble(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 8) {
            bb.putDouble(i, dummyDouble);
        }
    }

    private void innerLoopPutDouble(DoubleBuffer db) {
        for (int i = 0; i < db.capacity(); i++) {
            db.put(i, dummyDouble);
        }
    }

    // -- sanity

    // A subset of operations, as a basic correctness sanity
    public static void main(String ...args) {
        var test = new ByteBuffers();
        test.size = 16;
        test.setup();

        // byte
        test.dummyByteArray = "0123456789ABCDEF".getBytes(US_ASCII);
        test.testHeapBulkPutByte();
        var ba = test.testHeapBulkGetByte();
        assertTrue(Arrays.equals(ba, test.dummyByteArray));

        test.dummyByteArray = "FEDCBA9876543210".getBytes(US_ASCII);
        test.testDirectBulkPutByte();
        ba = test.testDirectBulkGetByte();
        assertTrue(Arrays.equals(ba, test.dummyByteArray));

        test.dummyByte = 0x01;
        test.testHeapLoopPutByte();
        int x = test.testHeapLoopGetByte();
        assertTrue(x == (0x01 * 16));

        test.dummyByte = 0x03;
        test.testDirectLoopPutByte();
        x = test.testDirectLoopGetByte();
        assertTrue(x == (0x03 * 16));

        // char
        test.dummyCharArray = "FFEEFFEE".toCharArray();
        test.testHeapBulkPutCharView();
        var ca = test.testHeapBulkGetCharView();
        assertTrue(Arrays.equals(ca, test.dummyCharArray));

        test.dummyChar = 0x03;
        test.testHeapLoopPutChar();
        var v = test.testHeapLoopGetChar();
        assertTrue(v == 0x03 * 8);

        test.dummyChar = 0x05;
        test.testHeapLoopPutCharView();
        v = test.testHeapLoopGetCharView();
        assertTrue(v == 0x05 * 8);

        test.dummyChar = 0x07;
        test.testDirectLoopPutCharView();
        v = test.testDirectLoopGetCharView();
        assertTrue(v == 0x07 * 8);

        // int
        test.dummyIntArray = new int[] { 0x01020304, 0x01020304, 0x01020304, 0x01020304 };
        test.testHeapBulkPutIntView();
        test.testHeapBulkPutIntViewSwap();
        test.testDirectBulkPutIntView();
        test.testDirectBulkPutIntViewSwap();

        byte[] heapLil, heapBig, directLil, directBig;
        if (nativeOrder() == LITTLE_ENDIAN) {
            var b1 = test.testHeapBulkGetByte();       heapLil   = Arrays.copyOf(b1, b1.length);
            var b2 = test.testHeapBulkGetByteSwap();   heapBig   = Arrays.copyOf(b2, b2.length);
            var b3 = test.testDirectBulkGetByte();     directLil = Arrays.copyOf(b3, b3.length);
            var b4 = test.testDirectBulkGetByteSwap(); directBig = Arrays.copyOf(b4, b4.length);
        } else {
            var b1 = test.testHeapBulkGetByteSwap();   heapLil   = Arrays.copyOf(b1, b1.length);
            var b2 = test.testHeapBulkGetByte();       heapBig   = Arrays.copyOf(b2, b2.length);
            var b3 = test.testDirectBulkGetByteSwap(); directLil = Arrays.copyOf(b3, b3.length);
            var b4 = test.testDirectBulkGetByte();     directBig = Arrays.copyOf(b4, b4.length);
        }

        for (int i=0; i<16; i+=4) {
            assertTrue(heapLil[i + 0] == 0x04);
            assertTrue(heapLil[i + 1] == 0x03);
            assertTrue(heapLil[i + 2] == 0x02);
            assertTrue(heapLil[i + 3] == 0x01);
            assertTrue(heapBig[i + 0] == 0x01);
            assertTrue(heapBig[i + 1] == 0x02);
            assertTrue(heapBig[i + 2] == 0x03);
            assertTrue(heapBig[i + 3] == 0x04);

            assertTrue(directLil[i + 0] == 0x04);
            assertTrue(directLil[i + 1] == 0x03);
            assertTrue(directLil[i + 2] == 0x02);
            assertTrue(directLil[i + 3] == 0x01);
            assertTrue(directBig[i + 0] == 0x01);
            assertTrue(directBig[i + 1] == 0x02);
            assertTrue(directBig[i + 2] == 0x03);
            assertTrue(directBig[i + 3] == 0x04);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition)
            throw new AssertionError();
    }
}
