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
 *   test(Direct|Heap)(Bulk|Single)(Get|Put)(Byte|Char|Short|Int|Long|Float|Double)(View)?(Swap)?
 *
 * This allows to easily run a subset of particular interest. For example:
 *   Direct only :- "org.openjdk.bench.java.nio.ByteBuffers.testDirect.*"
 *   Char only   :- "org.openjdk.bench.java.nio.ByteBuffers.test.*Char.*"
 *   Bulk only   :- "org.openjdk.bench.java.nio.ByteBuffers.test.*Bulk.*"
 *   Put with Int or Long carrier :-
 *      test(Direct|Heap)(Single)(Put)(Int|Long)(View)?(Swap)?"
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

    // ---------------- SINGLE GET TESTS

    @Benchmark
    public int testHeapSingleGetByte() {
        return innerSingleGetByte(heapBuffer);
    }

    @Benchmark
    public int testHeapSingleGetChar() {
        return innerSingleGetChar(heapBuffer);
    }

    @Benchmark
    public int testHeapSingleGetShort() {
        return innerSingleGetShort(heapBuffer);
    }

    @Benchmark
    public int testHeapSingleGetInt() {
        return innerSingleGetInt(heapBuffer);
    }

    @Benchmark
    public long testHeapSingleGetLong() {
        return innerSingleGetLong(heapBuffer);
    }

    @Benchmark
    public float testHeapSingleGetFloat() {
        return innerSingleGetFloat(heapBuffer);
    }

    @Benchmark
    public double testHeapSingleGetDouble() {
        return innerSingleGetDouble(heapBuffer);
    }

    @Benchmark
    public int testDirectSingleGetByte() {
        return innerSingleGetByte(directBuffer);
    }

    @Benchmark
    public int testDirectSingleGetChar() {
        return innerSingleGetChar(directBuffer);
    }

    @Benchmark
    public int testDirectSingleGetShort() {
        return innerSingleGetShort(directBuffer);
    }

    @Benchmark
    public int testDirectSingleGetInt() {
        return innerSingleGetInt(directBuffer);
    }

    @Benchmark
    public long testDirectSingleGetLong() {
        return innerSingleGetLong(directBuffer);
    }

    @Benchmark
    public float testDirectSingleGetFloat() {
        return innerSingleGetFloat(directBuffer);
    }

    @Benchmark
    public double testDirectSingleGetDouble() {
        return innerSingleGetDouble(directBuffer);
    }

    // ---------------- SINGLE PUT TESTS

    @Benchmark
    public void testHeapSinglePutByte() {
        innerSinglePutByte(heapBuffer);
    }

    @Benchmark
    public void testHeapSinglePutChar() {
        innerSinglePutChar(heapBuffer);
    }

    @Benchmark
    public void testHeapSinglePutShort() {
        innerSinglePutShort(heapBuffer);
    }

    @Benchmark
    public void testHeapSinglePutInt() {
        innerSinglePutInt(heapBuffer);
    }

    @Benchmark
    public void testHeapSinglePutLong() {
        innerSinglePutLong(heapBuffer);
    }

    @Benchmark
    public void testHeapSinglePutFloat() {
        innerSinglePutFloat(heapBuffer);
    }

    @Benchmark
    public void testHeapSinglePutDouble() {
        innerSinglePutDouble(heapBuffer);
    }

    @Benchmark
    public void testDirectSinglePutByte() {
        innerSinglePutByte(directBuffer);
    }

    @Benchmark
    public void testDirectSinglePutChar() {
        innerSinglePutChar(directBuffer);
    }

    @Benchmark
    public void testDirectSinglePutShort() {
        innerSinglePutShort(directBuffer);
    }

    @Benchmark
    public void testDirectSinglePutInt() {
        innerSinglePutInt(directBuffer);
    }

    @Benchmark
    public void testDirectSinglePutLong() {
        innerSinglePutLong(directBuffer);
    }

    @Benchmark
    public void testDirectSinglePutFloat() {
        innerSinglePutFloat(directBuffer);
    }

    @Benchmark
    public void testDirectSinglePutDouble() {
        innerSinglePutDouble(directBuffer);
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

    // ---------------- SINGLE GET TESTS (Views)

    @Benchmark
    public int testHeapSingleGetCharView() {
        return innerSingleGetChar(heapCharBufferView);
    }

    @Benchmark
    public int testHeapSingleGetShortView() {
        return innerSingleGetShort(heapShortBufferView);
    }

    @Benchmark
    public int testHeapSingleGetIntView() {
        return innerSingleGetInt(heapIntBufferView);
    }

    @Benchmark
    public long testHeapSingleGetLongView() {
        return innerSingleGetLong(heapLongBufferView);
    }

    @Benchmark
    public float testHeapSingleGetFloatView() {
        return innerSingleGetFloat(heapFloatBufferView);
    }

    @Benchmark
    public double testHeapSingleGetDoubleView() {
        return innerSingleGetDouble(heapDoubleBufferView);
    }

    @Benchmark
    public int testDirectSingleGetCharView() {
        return innerSingleGetChar(directCharBufferView);
    }

    @Benchmark
    public int testDirectSingleGetShortView() {
        return innerSingleGetShort(directShortBufferView);
    }

    @Benchmark
    public int testDirectSingleGetIntView() {
        return innerSingleGetInt(directIntBufferView);
    }

    @Benchmark
    public long testDirectSingleGetLongView() {
        return innerSingleGetLong(directLongBufferView);
    }

    @Benchmark
    public float testDirectSingleGetFloatView() {
        return innerSingleGetFloat(directFloatBufferView);
    }

    @Benchmark
    public double testDirectSingleGetDoubleView() {
        return innerSingleGetDouble(directDoubleBufferView);
    }

    // ---------------- SINGLE PUT TESTS (Views)

    @Benchmark
    public void testHeapSinglePutCharView() {
        innerSinglePutChar(heapCharBufferView);
    }

    @Benchmark
    public void testHeapSinglePutShortView() {
        innerSinglePutShort(heapShortBufferView);
    }

    @Benchmark
    public void testHeapSinglePutIntView() {
        innerSinglePutInt(heapIntBufferView);
    }

    @Benchmark
    public void testHeapSinglePutLongView() {
        innerSinglePutLong(heapLongBufferView);
    }

    @Benchmark
    public void testHeapSinglePutFloatView() {
        innerSinglePutFloat(heapFloatBufferView);
    }

    @Benchmark
    public void testHeapSinglePutDoubleView() {
        innerSinglePutDouble(heapDoubleBufferView);
    }

    @Benchmark
    public void testDirectSinglePutCharView() {
        innerSinglePutChar(directCharBufferView);
    }

    @Benchmark
    public void testDirectSinglePutShortView() {
        innerSinglePutShort(directShortBufferView);
    }

    @Benchmark
    public void testDirectSinglePutIntView() {
        innerSinglePutInt(directIntBufferView);
    }

    @Benchmark
    public void testDirectSinglePutLongView() {
        innerSinglePutLong(directLongBufferView);
    }

    @Benchmark
    public void testDirectSinglePutFloatView() {
        innerSinglePutFloat(directFloatBufferView);
    }

    @Benchmark
    public void testDirectSinglePutDoubleView() {
        innerSinglePutDouble(directDoubleBufferView);
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

    // ---------------- SINGLE GET TESTS (swap)

    @Benchmark
    public int testHeapSingleGetByteSwap() {
        return innerSingleGetByte(heapBufferSwap);
    }

    @Benchmark
    public int testHeapSingleGetCharSwap() {
        return innerSingleGetChar(heapBufferSwap);
    }

    @Benchmark
    public int testHeapSingleGetShortSwap() {
        return innerSingleGetShort(heapBufferSwap);
    }

    @Benchmark
    public int testHeapSingleGetIntSwap() {
        return innerSingleGetInt(heapBufferSwap);
    }

    @Benchmark
    public long testHeapSingleGetLongSwap() {
        return innerSingleGetLong(heapBufferSwap);
    }

    @Benchmark
    public float testHeapSingleGetFloatSwap() {
        return innerSingleGetFloat(heapBufferSwap);
    }

    @Benchmark
    public double testHeapSingleGetDoubleSwap() {
        return innerSingleGetDouble(heapBufferSwap);
    }

    @Benchmark
    public int testDirectSingleGetByteSwap() {
        return innerSingleGetByte(directBufferSwap);
    }

    @Benchmark
    public int testDirectSingleGetCharSwap() {
        return innerSingleGetChar(directBufferSwap);
    }

    @Benchmark
    public int testDirectSingleGetShortSwap() {
        return innerSingleGetShort(directBufferSwap);
    }

    @Benchmark
    public int testDirectSingleGetIntSwap() {
        return innerSingleGetInt(directBufferSwap);
    }

    @Benchmark
    public long testDirectSingleGetLongSwap() {
        return innerSingleGetLong(directBufferSwap);
    }

    @Benchmark
    public float testDirectSingleGetFloatSwap() {
        return innerSingleGetFloat(directBufferSwap);
    }

    @Benchmark
    public double testDirectSingleGetDoubleSwap() {
        return innerSingleGetDouble(directBufferSwap);
    }

    // ---------------- SINGLE PUT TESTS (swap)

    @Benchmark
    public void testHeapSinglePutByteSwap() {
        innerSinglePutByte(heapBufferSwap);
    }

    @Benchmark
    public void testHeapSinglePutCharSwap() {
        innerSinglePutChar(heapBufferSwap);
    }

    @Benchmark
    public void testHeapSinglePutShortSwap() {
        innerSinglePutShort(heapBufferSwap);
    }

    @Benchmark
    public void testHeapSinglePutIntSwap() {
        innerSinglePutInt(heapBufferSwap);
    }

    @Benchmark
    public void testHeapSinglePutLongSwap() {
        innerSinglePutLong(heapBufferSwap);
    }

    @Benchmark
    public void testHeapSinglePutFloatSwap() {
        innerSinglePutFloat(heapBufferSwap);
    }

    @Benchmark
    public void testHeapSinglePutDoubleSwap() {
        innerSinglePutDouble(heapBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutByteSwap() {
        innerSinglePutByte(directBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutCharSwap() {
        innerSinglePutChar(directBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutShortSwap() {
        innerSinglePutShort(directBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutIntSwap() {
        innerSinglePutInt(directBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutLongSwap() {
        innerSinglePutLong(directBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutFloatSwap() {
        innerSinglePutFloat(directBufferSwap);
    }

    @Benchmark
    public void testDirectSinglePutDoubleSwap() {
        innerSinglePutDouble(directBufferSwap);
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

    // ---------------- SINGLE GET TESTS (Views) (swap)

    @Benchmark
    public int testHeapSingleGetCharViewSwap() {
        return innerSingleGetChar(heapCharBufferViewSwap);
    }

    @Benchmark
    public int testHeapSingleGetShortViewSwap() {
        return innerSingleGetShort(heapShortBufferViewSwap);
    }

    @Benchmark
    public int testHeapSingleGetIntViewSwap() {
        return innerSingleGetInt(heapIntBufferViewSwap);
    }

    @Benchmark
    public long testHeapSingleGetLongViewSwap() {
        return innerSingleGetLong(heapLongBufferViewSwap);
    }

    @Benchmark
    public float testHeapSingleGetFloatViewSwap() {
        return innerSingleGetFloat(heapFloatBufferViewSwap);
    }

    @Benchmark
    public double testHeapSingleGetDoubleViewSwap() {
        return innerSingleGetDouble(heapDoubleBufferViewSwap);
    }

    @Benchmark
    public int testDirectSingleGetCharViewSwap() {
        return innerSingleGetChar(directCharBufferViewSwap);
    }

    @Benchmark
    public int testDirectSingleGetShortViewSwap() {
        return innerSingleGetShort(directShortBufferViewSwap);
    }

    @Benchmark
    public int testDirectSingleGetIntViewSwap() {
        return innerSingleGetInt(directIntBufferViewSwap);
    }

    @Benchmark
    public long testDirectSingleGetLongViewSwap() {
        return innerSingleGetLong(directLongBufferViewSwap);
    }

    @Benchmark
    public float testDirectSingleGetFloatViewSwap() {
        return innerSingleGetFloat(directFloatBufferViewSwap);
    }

    @Benchmark
    public double testDirectSingleGetDoubleViewSwap() {
        return innerSingleGetDouble(directDoubleBufferViewSwap);
    }

    // ---------------- SINGLE PUT TESTS (Views) (swap)

    @Benchmark
    public void testHeapSinglePutCharViewSwap() {
        innerSinglePutChar(heapCharBufferViewSwap);
    }

    @Benchmark
    public void testHeapSinglePutShortViewSwap() {
        innerSinglePutShort(heapShortBufferViewSwap);
    }

    @Benchmark
    public void testHeapSinglePutIntViewSwap() {
        innerSinglePutInt(heapIntBufferViewSwap);
    }

    @Benchmark
    public void testHeapSinglePutLongViewSwap() {
        innerSinglePutLong(heapLongBufferViewSwap);
    }

    @Benchmark
    public void testHeapSinglePutFloatViewSwap() {
        innerSinglePutFloat(heapFloatBufferViewSwap);
    }

    @Benchmark
    public void testHeapSinglePutDoubleViewSwap() {
        innerSinglePutDouble(heapDoubleBufferViewSwap);
    }

    @Benchmark
    public void testDirectSinglePutCharViewSwap() {
        innerSinglePutChar(directCharBufferViewSwap);
    }

    @Benchmark
    public void testDirectSinglePutShortViewSwap() {
        innerSinglePutShort(directShortBufferViewSwap);
    }

    @Benchmark
    public void testDirectSinglePutIntViewSwap() {
        innerSinglePutInt(directIntBufferViewSwap);
    }

    @Benchmark
    public void testDirectSinglePutLongViewSwap() {
        innerSinglePutLong(directLongBufferViewSwap);
    }

    @Benchmark
    public void testDirectSinglePutFloatViewSwap() {
        innerSinglePutFloat(directFloatBufferViewSwap);
    }

    @Benchmark
    public void testDirectSinglePutDoubleViewSwap() {
        innerSinglePutDouble(directDoubleBufferViewSwap);
    }

    // ---------------- HELPER METHODS

    private int innerSingleGetByte(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i++) {
            r += bb.get(i);
        }
        return r;
    }

    private int innerSingleGetChar(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 2) {
            r += bb.getChar(i);
        }
        return r;
    }

    private int innerSingleGetChar(CharBuffer cb) {
        int r = 0;
        for (int i = 0; i < cb.capacity(); i++) {
            r += cb.get(i);
        }
        return r;
    }

    private int innerSingleGetShort(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 2) {
            r += bb.getShort(i);
        }
        return r;
    }

    private int innerSingleGetShort(ShortBuffer sb) {
        int r = 0;
        for (int i = 0; i < sb.capacity(); i++) {
            r += sb.get(i);
        }
        return r;
    }

    private int innerSingleGetInt(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 4) {
            r += bb.getInt(i);
        }
        return r;
    }

    private int innerSingleGetInt(IntBuffer ib) {
        int r = 0;
        for (int i = 0; i < ib.capacity(); i++) {
            r += ib.get(i);
        }
        return r;
    }

    private long innerSingleGetLong(ByteBuffer bb) {
        long r = 0;
        for (int i = 0; i < bb.capacity(); i += 8) {
            r += bb.getLong(i);
        }
        return r;
    }

    private long innerSingleGetLong(LongBuffer lb) {
        long r = 0;
        for (int i = 0; i < lb.capacity(); i++) {
            r += lb.get(i);
        }
        return r;
    }

    private float innerSingleGetFloat(ByteBuffer bb) {
        float r = 0;
        for (int i = 0; i < bb.capacity(); i += 4) {
            r += bb.getFloat(i);
        }
        return r;
    }

    private float innerSingleGetFloat(FloatBuffer fb) {
        float r = 0;
        for (int i = 0; i < fb.capacity(); i++) {
            r += fb.get(i);
        }
        return r;
    }

    private double innerSingleGetDouble(ByteBuffer bb) {
        double d = 0;
        for (int i = 0; i < bb.capacity(); i += 8) {
            d += bb.getDouble(i);
        }
        return d;
    }

    private double innerSingleGetDouble(DoubleBuffer db) {
        double d = 0;
        for (int i = 0; i < db.capacity(); i++) {
            d += db.get(i);
        }
        return d;
    }

    private void innerSinglePutByte(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i++) {
            bb.put(i, dummyByte);
        }
    }

    private void innerSinglePutChar(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 2) {
            bb.putChar(i, dummyChar);
        }
    }

    private void innerSinglePutChar(CharBuffer cb) {
        for (int i = 0; i < cb.capacity(); i++) {
            cb.put(i, dummyChar);
        }
    }

    private void innerSinglePutShort(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 2) {
            bb.putShort(i, dummyShort);
        }
    }

    private void innerSinglePutShort(ShortBuffer sb) {
        for (int i = 0; i < sb.capacity(); i++) {
            sb.put(i, dummyShort);
        }
    }

    private void innerSinglePutInt(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 4) {
            bb.putInt(i, dummyInt);
        }
    }

    private void innerSinglePutInt(IntBuffer ib) {
        for (int i = 0; i < ib.capacity(); i++) {
            ib.put(i, dummyInt);
        }
    }

    private void innerSinglePutLong(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 8) {
            bb.putLong(i, dummyLong);
        }
    }

    private void innerSinglePutLong(LongBuffer lb) {
        for (int i = 0; i < lb.capacity(); i++) {
            lb.put(i, dummyLong);
        }
    }

    private void innerSinglePutFloat(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 4) {
            bb.putFloat(i, dummyFloat);
        }
    }
    private void innerSinglePutFloat(FloatBuffer fb) {
        for (int i = 0; i < fb.capacity(); i++) {
            fb.put(i, dummyFloat);
        }
    }

    private void innerSinglePutDouble(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 8) {
            bb.putDouble(i, dummyDouble);
        }
    }

    private void innerSinglePutDouble(DoubleBuffer db) {
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
        test.testHeapSinglePutByte();
        int x = test.testHeapSingleGetByte();
        assertTrue(x == (0x01 * 16));

        test.dummyByte = 0x03;
        test.testDirectSinglePutByte();
        x = test.testDirectSingleGetByte();
        assertTrue(x == (0x03* 16));

        // char
        test.dummyCharArray = "FFEEFFEE".toCharArray();
        test.testHeapBulkPutCharView();
        var ca = test.testHeapBulkGetCharView();
        assertTrue(Arrays.equals(ca, test.dummyCharArray));

        test.dummyChar = 0x03;
        test.testHeapSinglePutChar();
        var v = test.testHeapSingleGetChar();
        assertTrue(v == 0x03 * 8);

        test.dummyChar = 0x05;
        test.testHeapSinglePutCharView();
        v = test.testHeapSingleGetCharView();
        assertTrue(v == 0x05 * 8);

        test.dummyChar = 0x07;
        test.testDirectSinglePutCharView();
        v = test.testDirectSingleGetCharView();
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
