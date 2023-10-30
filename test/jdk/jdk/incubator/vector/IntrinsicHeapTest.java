/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @modules jdk.incubator.vector
 * @run testng/othervm -XX:+UnlockDiagnosticVMOptions
 *                     -XX:+PrintIntrinsics
 *                     -Xbatch
 *                     -XX:CompileCommand=dontinline,UnalignedHeapTest::payload*
 *                     -XX:CompileCommand=PrintCompilation,UnalignedHeapTest::payload*
 *                      IntrinsicHeapTest
 */

import jdk.incubator.vector.*;
import org.testng.annotations.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import static org.testng.Assert.*;

public class IntrinsicHeapTest {

    // Big enough to hold all species variants for all array types
    private static final int ARRAY_LEN = 1024;

    @Test
    public void testByteArray() {
        test(IntrinsicHeapTest::payloadByteArray);
    }

    @Test
    public void testShortArray() {
        test(IntrinsicHeapTest::payloadShortArray);
    }

    @Test
    public void testIntArray() {
        test(IntrinsicHeapTest::payloadIntArray);
    }

    @Test
    public void testFloatArray() {
        test(IntrinsicHeapTest::payloadFloatArray);
    }

    @Test
    public void testLongArray() {
        test(IntrinsicHeapTest::payloadLongArray);
    }

    @Test
    public void testDoubleArray() {
        test(IntrinsicHeapTest::payloadDoubleArray);
    }

    static void test(Runnable test) {
        for (int i = 0; i < 30000 / 5; i++) {
            test.run();
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
    }

    static void payloadByteArray() {
        for (VectorSpecies<Byte> species: Arrays.asList(ByteVector.SPECIES_64, ByteVector.SPECIES_128, ByteVector.SPECIES_256, ByteVector.SPECIES_512, ByteVector.SPECIES_MAX)) {
            byte[] arr = new byte[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (byte) i); // May wrap around
            MemorySegment segment = MemorySegment.ofArray(arr);
            Vector<Byte> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            byte[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_BYTE);
            byte[] actual = (byte[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    static void payloadShortArray() {
        for (VectorSpecies<Short> species: Arrays.asList(ShortVector.SPECIES_64, ShortVector.SPECIES_128, ShortVector.SPECIES_256, ShortVector.SPECIES_512, ShortVector.SPECIES_MAX)) {
            short[] arr = new short[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (short) i);
            MemorySegment segment = MemorySegment.ofArray(arr);
            Vector<Short> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            short[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_SHORT_UNALIGNED);
            short[] actual = (short[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    static void payloadIntArray() {
        for (VectorSpecies<Integer> species: Arrays.asList(IntVector.SPECIES_64, IntVector.SPECIES_128, IntVector.SPECIES_256, IntVector.SPECIES_512, IntVector.SPECIES_MAX)) {
            MemorySegment segment = MemorySegment.ofArray(IntStream.range(0, ARRAY_LEN).toArray());
            Vector<Integer> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            int[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_INT_UNALIGNED);
            int[] actual = vector.toIntArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    static void payloadFloatArray() {
        for (VectorSpecies<Float> species: Arrays.asList(FloatVector.SPECIES_64, FloatVector.SPECIES_128, FloatVector.SPECIES_256, FloatVector.SPECIES_512, FloatVector.SPECIES_MAX)) {
            float[] arr = new float[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (float) i);
            MemorySegment segment = MemorySegment.ofArray(arr);
            Vector<Float> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            float[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_FLOAT_UNALIGNED);
            float[] actual = (float[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    static void payloadLongArray() {
        for (VectorSpecies<Long> species: Arrays.asList(LongVector.SPECIES_64, LongVector.SPECIES_128, LongVector.SPECIES_256, LongVector.SPECIES_512, LongVector.SPECIES_MAX)) {
            long[] arr = new long[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = i);
            MemorySegment segment = MemorySegment.ofArray(arr);
            Vector<Long> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            long[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_LONG_UNALIGNED);
            long[] actual = (long[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    static void payloadDoubleArray() {
        for (VectorSpecies<Double> species: Arrays.asList(DoubleVector.SPECIES_64, DoubleVector.SPECIES_128, DoubleVector.SPECIES_256, DoubleVector.SPECIES_512, DoubleVector.SPECIES_MAX)) {
            double[] arr = new double[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (double) i);
            MemorySegment segment = MemorySegment.ofArray(arr);
            Vector<Double> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            double[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_DOUBLE_UNALIGNED);
            double[] actual = (double[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

}
