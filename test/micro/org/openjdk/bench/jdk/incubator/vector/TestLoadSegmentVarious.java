/*
 *  Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {
        "--add-modules=jdk.incubator.vector",
        "--enable-native-access", "ALL-UNNAMED"})
public class TestLoadSegmentVarious {

    private static final VectorSpecies<Byte> BYTE_SPECIES = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Integer> INTEGER_SPECIES = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Double> DOUBLE_SPECIES = VectorSpecies.ofLargestShape(double.class);

    private static final VectorMask<Integer> INTEGER_MASK = VectorMask.fromLong(INTEGER_SPECIES, (1 << (INTEGER_SPECIES.length() / 2)) - 1);
    private static final VectorMask<Double> DOUBLE_MASK = VectorMask.fromLong(DOUBLE_SPECIES, (1 << (DOUBLE_SPECIES.length() / 2)) - 1);

    // Must be evenly dividable by Double.BYTES
    @Param("1024")
    private int size;

    private byte[] byteSrcArray;
    private MemorySegment byteSegment;
    private int[] intSrcArray;
    private MemorySegment intSegment;
    private double[] doubleSrcArray;
    private MemorySegment doubleSegment;

    @Setup
    public void setup() {
        byteSrcArray = new byte[size];
        for (int i = 0; i < byteSrcArray.length; i++) {
            byteSrcArray[i] = (byte) i;
        }
        byteSegment = MemorySegment.ofArray(byteSrcArray);

        intSrcArray = new int[size / Integer.BYTES];
        for (int i = 0; i < intSrcArray.length; i++) {
            intSrcArray[i] = i;
        }
        intSegment = MemorySegment.ofArray(intSrcArray);

        doubleSrcArray = new double[size / Double.BYTES];
        for (int i = 0; i < doubleSrcArray.length; i++) {
            intSrcArray[i] = i;
        }
        doubleSegment = MemorySegment.ofArray(doubleSrcArray);
    }

    // Scalar conversion

    @Benchmark
    public void scalarByteVectorFromByteSegment(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            byte[] arr = new byte[BYTE_SPECIES.length()];
            arr[i % BYTE_SPECIES.length()] = byteSegment.get(ValueLayout.JAVA_BYTE, i);
            var v = ByteVector.fromArray(BYTE_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarByteVectorFromIntSegment(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            byte[] arr = new byte[BYTE_SPECIES.length()];
            arr[i % BYTE_SPECIES.length()] = intSegment.get(ValueLayout.JAVA_BYTE, i);
            var v = ByteVector.fromArray(BYTE_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarByteVectorFromDoubleSegment(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            byte[] arr = new byte[BYTE_SPECIES.length()];
            arr[i % BYTE_SPECIES.length()] = doubleSegment.get(ValueLayout.JAVA_BYTE, i);
            var v = ByteVector.fromArray(BYTE_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarIntVectorFromByteSegment(Blackhole bh) {
        for (int i = 0; i < size / Integer.BYTES; i++) {
            int[] arr = new int[INTEGER_SPECIES.length()];
            arr[i % INTEGER_SPECIES.length()] = byteSegment.get(ValueLayout.JAVA_INT_UNALIGNED, i * Integer.BYTES);
            var v = IntVector.fromArray(INTEGER_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarIntVectorFromIntSegment(Blackhole bh) {
        for (int i = 0; i < size / Integer.BYTES; i++) {
            int[] arr = new int[INTEGER_SPECIES.length()];
            arr[i % INTEGER_SPECIES.length()] = intSegment.get(ValueLayout.JAVA_INT, i * Integer.BYTES);
            var v = IntVector.fromArray(INTEGER_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarIntVectorFromDoubleSegment(Blackhole bh) {
        for (int i = 0; i < size / Integer.BYTES; i++) {
            int[] arr = new int[INTEGER_SPECIES.length()];
            arr[i % INTEGER_SPECIES.length()] = doubleSegment.get(ValueLayout.JAVA_INT, i * Integer.BYTES);
            var v = IntVector.fromArray(INTEGER_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarDoubleVectorFromByteSegment(Blackhole bh) {
        for (int i = 0; i < size / Double.BYTES; i ++) {
            double[] arr = new double[DOUBLE_SPECIES.length()];
            arr[i % DOUBLE_SPECIES.length()] = byteSegment.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, i * Double.BYTES);
            var v = DoubleVector.fromArray(DOUBLE_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarDoubleVectorFromIntSegment(Blackhole bh) {
        for (int i = 0; i < size / Double.BYTES; i ++) {
            double[] arr = new double[DOUBLE_SPECIES.length()];
            arr[i % DOUBLE_SPECIES.length()] = intSegment.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, i * Double.BYTES);
            var v = DoubleVector.fromArray(DOUBLE_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    @Benchmark
    public void scalarDoubleVectorFromDoubleSegment(Blackhole bh) {
        for (int i = 0; i < size / Double.BYTES; i ++) {
            double[] arr = new double[DOUBLE_SPECIES.length()];
            arr[i % DOUBLE_SPECIES.length()] = doubleSegment.get(ValueLayout.JAVA_DOUBLE, i * Double.BYTES);
            var v = DoubleVector.fromArray(DOUBLE_SPECIES, arr, 0);
            bh.consume(v);
        }
    }

    // Vector conversion

    @Benchmark
    public void byteVectorFromByteBackedSegment(Blackhole bh) {
        for (int i = 0; i < BYTE_SPECIES.loopBound(byteSrcArray.length); i += BYTE_SPECIES.vectorByteSize()) {
            var v = ByteVector.fromMemorySegment(BYTE_SPECIES, byteSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void byteVectorFromIntBackedSegment(Blackhole bh) {
        for (int i = 0; i < BYTE_SPECIES.loopBound(byteSrcArray.length); i += BYTE_SPECIES.vectorByteSize()) {
            var v = ByteVector.fromMemorySegment(BYTE_SPECIES, intSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void byteVectorFromDoubleBackedSegment(Blackhole bh) {
        for (int i = 0; i < BYTE_SPECIES.loopBound(byteSrcArray.length); i += BYTE_SPECIES.vectorByteSize()) {
            var v = ByteVector.fromMemorySegment(BYTE_SPECIES, doubleSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void intVectorFromByteBackedSegment(Blackhole bh) {
        for (int i = 0; i < INTEGER_SPECIES.loopBound(intSrcArray.length); i += INTEGER_SPECIES.vectorByteSize()) {
            var v = IntVector.fromMemorySegment(INTEGER_SPECIES, byteSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void intVectorFromIntBackedSegment(Blackhole bh) {
        for (int i = 0; i < INTEGER_SPECIES.loopBound(intSrcArray.length); i += INTEGER_SPECIES.vectorByteSize()) {
            var v = IntVector.fromMemorySegment(INTEGER_SPECIES, intSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void intVectorFromDoubleBackedSegment(Blackhole bh) {
        for (int i = 0; i < INTEGER_SPECIES.loopBound(intSrcArray.length); i += INTEGER_SPECIES.vectorByteSize()) {
            var v = IntVector.fromMemorySegment(INTEGER_SPECIES, doubleSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void doubleVectorFromByteBackedSegment(Blackhole bh) {
        for (int i = 0; i < DOUBLE_SPECIES.loopBound(doubleSrcArray.length); i += DOUBLE_SPECIES.vectorByteSize()) {
            var v = DoubleVector.fromMemorySegment(DOUBLE_SPECIES, byteSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void doubleVectorFromIntBackedSegment(Blackhole bh) {
        for (int i = 0; i < DOUBLE_SPECIES.loopBound(doubleSrcArray.length); i += DOUBLE_SPECIES.vectorByteSize()) {
            var v = DoubleVector.fromMemorySegment(DOUBLE_SPECIES, intSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void doubleVectorFromDoubleBackedSegment(Blackhole bh) {
        for (int i = 0; i < DOUBLE_SPECIES.loopBound(doubleSrcArray.length); i += DOUBLE_SPECIES.vectorByteSize()) {
            var v = DoubleVector.fromMemorySegment(DOUBLE_SPECIES, doubleSegment, i, ByteOrder.nativeOrder());
            bh.consume(v);
        }
    }

    @Benchmark
    public void intVectorFromIntBackedSegmentMasked(Blackhole bh) {
        for (int i = 0; i < INTEGER_SPECIES.loopBound(intSrcArray.length); i += INTEGER_SPECIES.vectorByteSize()) {
            var v = IntVector.fromMemorySegment(INTEGER_SPECIES, doubleSegment, i, ByteOrder.nativeOrder(), INTEGER_MASK);
            bh.consume(v);
        }
    }

    @Benchmark
    public void intVectorFromDoubleBackedSegmentMasked(Blackhole bh) {
        for (int i = 0; i < INTEGER_SPECIES.loopBound(intSrcArray.length); i += INTEGER_SPECIES.vectorByteSize()) {
            var v = IntVector.fromMemorySegment(INTEGER_SPECIES, doubleSegment, i, ByteOrder.nativeOrder(), INTEGER_MASK);
            bh.consume(v);
        }
    }

    @Benchmark
    public void doubleVectorFromIntBackedSegmentMasked(Blackhole bh) {
        for (int i = 0; i < DOUBLE_SPECIES.loopBound(doubleSrcArray.length); i += DOUBLE_SPECIES.vectorByteSize()) {
            var v = DoubleVector.fromMemorySegment(DOUBLE_SPECIES, intSegment, i, ByteOrder.nativeOrder(), DOUBLE_MASK);
            bh.consume(v);
        }
    }
}
