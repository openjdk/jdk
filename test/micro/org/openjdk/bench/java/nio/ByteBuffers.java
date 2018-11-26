/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark operations on java.nio.Buffer.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
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

    // ---------------- BULK GET TESTS

    @Benchmark
    public byte[] testBulkGet() {
        return innerBufferBulkGet(ByteBuffer.allocate(size));
    }

    @Benchmark
    public byte[] testDirectBulkGet() {
        return innerBufferBulkGet(ByteBuffer.allocateDirect(size));
    }

    // ---------------- BULK PUT TESTS

    @Benchmark
    public byte[] testBulkPut() {
        return innerBufferBulkPut(ByteBuffer.allocate(size));
    }

    @Benchmark
    public byte[] testDirectBulkPut() {
        return innerBufferBulkPut(ByteBuffer.allocateDirect(size));
    }

    // ---------------- SINGLE GET TESTS

    @Benchmark
    public int testSingleGetByte() {
        return innerSingleGetByte(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public int testSingleGetChar() {
        return innerSingleGetChar(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public int testSingleGetShort() {
        return innerSingleGetShort(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public int testSingleGetInt() {
        return innerSingleGetInt(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public long testSingleGetLong() {
        return innerSingleGetLong(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public float testSingleGetFloat() {
        return innerSingleGetFloat(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public double testSingleGetDouble() {
        return innerSingleGetDouble(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public int testDirectSingleGetByte() {
        return innerSingleGetByte(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public int testDirectSingleGetChar() {
        return innerSingleGetChar(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public int testDirectSingleGetShort() {
        return innerSingleGetShort(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public int testDirectSingleGetInt() {
        return innerSingleGetInt(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public long testDirectSingleGetLong() {
        return innerSingleGetLong(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public float testDirectSingleGetFloat() {
        return innerSingleGetFloat(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public double testDirectSingleGetDouble() {
        return innerSingleGetDouble(ByteBuffer.allocateDirect(1000));
    }

    // ---------------- SINGLE PUT TESTS

    @Benchmark
    public void testSinglePutByte() {
        innerSinglePutByte(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testSinglePutChar() {
        innerSinglePutChar(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testSinglePutShort() {
        innerSinglePutShort(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testSinglePutInt() {
        innerSinglePutInt(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testSinglePutLong() {
        innerSinglePutLong(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testSinglePutFloat() {
        innerSinglePutFloat(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testSinglePutDouble() {
        innerSinglePutDouble(ByteBuffer.allocate(1000));
    }

    @Benchmark
    public void testDirectSinglePutByte() {
        innerSinglePutByte(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public void testDirectSinglePutChar() {
        innerSinglePutChar(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public void testDirectSinglePutShort() {
        innerSinglePutShort(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public void testDirectSinglePutInt() {
        innerSinglePutInt(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public void testDirectSinglePutLong() {
        innerSinglePutLong(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public void testDirectSinglePutFloat() {
        innerSinglePutFloat(ByteBuffer.allocateDirect(1000));
    }

    @Benchmark
    public void testDirectSinglePutDouble() {
        innerSinglePutDouble(ByteBuffer.allocateDirect(1000));
    }

    // ---------------- HELPER METHODS

    private byte[] innerBufferBulkGet(ByteBuffer bb) {
        byte[] dummyByteArray = new byte[bb.capacity()];
        bb.get(dummyByteArray);
        bb.flip();
        return dummyByteArray;
    }

    private byte[] innerBufferBulkPut(ByteBuffer bb) {
        byte[] dummyByteArray = new byte[bb.capacity()];
        bb.put(dummyByteArray);
        bb.flip();
        return dummyByteArray;
    }

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
