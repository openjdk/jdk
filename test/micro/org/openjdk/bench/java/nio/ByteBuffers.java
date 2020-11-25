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
import java.util.concurrent.TimeUnit;

/**
 * Benchmark operations on java.nio.Buffer.
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(3)
public class ByteBuffers {

    @Param({"10", "1000", "100000"})
    private int size;

    public byte dummyByte;
    public char dummyChar;
    public short dummyShort;
    public int dummyInt;
    public long dummyLong;
    public float dummyFloat;
    public double dummyDouble;

    public ByteBuffer heapBuffer;
    public ByteBuffer directBuffer;
    public byte[] dummyByteArray;

    @Setup
    public void setup() {
        heapBuffer = ByteBuffer.allocate(size);
        directBuffer = ByteBuffer.allocateDirect(size);
        dummyByteArray = new byte[size];
    }

    // ---------------- BULK GET TESTS

    @Benchmark
    public byte[] testBulkGet() {
        heapBuffer.get(0, dummyByteArray);
        return dummyByteArray;
    }

    @Benchmark
    public byte[] testDirectBulkGet() {
        directBuffer.get(0, dummyByteArray);
        return dummyByteArray;
    }

    // ---------------- BULK PUT TESTS

    @Benchmark
    public byte[] testBulkPut() {
        heapBuffer.put(0, dummyByteArray);
        return dummyByteArray;
    }

    @Benchmark
    public byte[] testDirectBulkPut() {
        directBuffer.put(0, dummyByteArray);
        return dummyByteArray;
    }

    // ---------------- SINGLE GET TESTS

    @Benchmark
    public int testSingleGetByte() {
        return innerSingleGetByte(heapBuffer);
    }

    @Benchmark
    public int testSingleGetChar() {
        return innerSingleGetChar(heapBuffer);
    }

    @Benchmark
    public int testSingleGetShort() {
        return innerSingleGetShort(heapBuffer);
    }

    @Benchmark
    public int testSingleGetInt() {
        return innerSingleGetInt(heapBuffer);
    }

    @Benchmark
    public long testSingleGetLong() {
        return innerSingleGetLong(heapBuffer);
    }

    @Benchmark
    public float testSingleGetFloat() {
        return innerSingleGetFloat(heapBuffer);
    }

    @Benchmark
    public double testSingleGetDouble() {
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
    public void testSinglePutByte() {
        innerSinglePutByte(heapBuffer);
    }

    @Benchmark
    public void testSinglePutChar() {
        innerSinglePutChar(heapBuffer);
    }

    @Benchmark
    public void testSinglePutShort() {
        innerSinglePutShort(heapBuffer);
    }

    @Benchmark
    public void testSinglePutInt() {
        innerSinglePutInt(heapBuffer);
    }

    @Benchmark
    public void testSinglePutLong() {
        innerSinglePutLong(heapBuffer);
    }

    @Benchmark
    public void testSinglePutFloat() {
        innerSinglePutFloat(heapBuffer);
    }

    @Benchmark
    public void testSinglePutDouble() {
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

    private int innerSingleGetShort(ByteBuffer bb) {
        int r = 0;
        for (int i = 0; i < bb.capacity(); i += 2) {
            r += bb.getShort(i);
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

    private long innerSingleGetLong(ByteBuffer bb) {
        long r = 0;
        for (int i = 0; i < bb.capacity(); i += 8) {
            r += bb.getLong(i);
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

    private double innerSingleGetDouble(ByteBuffer bb) {
        double d = 0;
        for (int i = 0; i < bb.capacity(); i += 8) {
            d += bb.getDouble(i);
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

    private void innerSinglePutShort(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 2) {
            bb.putShort(i, dummyShort);
        }
    }

    private void innerSinglePutInt(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 4) {
            bb.putInt(i, dummyInt);
        }
    }

    private void innerSinglePutLong(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 8) {
            bb.putLong(i, dummyLong);
        }
    }

    private void innerSinglePutFloat(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 4) {
            bb.putFloat(i, dummyFloat);
        }
    }

    private void innerSinglePutDouble(ByteBuffer bb) {
        for (int i = 0; i < bb.capacity(); i += 8) {
            bb.putDouble(i, dummyDouble);
        }
    }
}
