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
 * @run testng UnalignedHeapTest
 */

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorSpecies;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.IntStream;

public class UnalignedHeapTest {

    // Big enough to hold all species variants for all array types
    private static final int ARRAY_LEN = 1024;

    @Test
    public void testByteArray() {
        for (VectorSpecies<Byte> species: Arrays.asList(ByteVector.SPECIES_64, ByteVector.SPECIES_128, ByteVector.SPECIES_256, ByteVector.SPECIES_512, ByteVector.SPECIES_MAX)) {
            byte[] arr = new byte[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (byte) i); // May wrap around
            MemorySegment segment = MemorySegment.ofArray(arr).asSlice(1);
            Vector<Byte> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            byte[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_BYTE);
            byte[] actual = (byte[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    @Test
    public void testShortArray() {
        for (VectorSpecies<Short> species: Arrays.asList(ShortVector.SPECIES_64, ShortVector.SPECIES_128, ShortVector.SPECIES_256, ShortVector.SPECIES_512, ShortVector.SPECIES_MAX)) {
            short[] arr = new short[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (short) i);
            MemorySegment segment = MemorySegment.ofArray(arr).asSlice(1);
            Vector<Short> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            short[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_SHORT_UNALIGNED);
            short[] actual = (short[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    @Test
    public void testIntArray() {
        for (VectorSpecies<Integer> species: Arrays.asList(IntVector.SPECIES_64, IntVector.SPECIES_128, IntVector.SPECIES_256, IntVector.SPECIES_512, IntVector.SPECIES_MAX)) {
            MemorySegment segment = MemorySegment.ofArray(IntStream.range(0, ARRAY_LEN).toArray()).asSlice(1);
            Vector<Integer> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            int[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_INT_UNALIGNED);
            int[] actual = vector.toIntArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    @Test
    public void testFloatArray() {
        for (VectorSpecies<Float> species: Arrays.asList(FloatVector.SPECIES_64, FloatVector.SPECIES_128, FloatVector.SPECIES_256, FloatVector.SPECIES_512, FloatVector.SPECIES_MAX)) {
            float[] arr = new float[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (float) i);
            MemorySegment segment = MemorySegment.ofArray(arr).asSlice(1);
            Vector<Float> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            float[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_FLOAT_UNALIGNED);
            float[] actual = (float[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    @Test
    public void testLongArray() {
        for (VectorSpecies<Long> species: Arrays.asList(LongVector.SPECIES_64, LongVector.SPECIES_128, LongVector.SPECIES_256, LongVector.SPECIES_512, LongVector.SPECIES_MAX)) {
            long[] arr = new long[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = i);
            MemorySegment segment = MemorySegment.ofArray(arr).asSlice(1);
            Vector<Long> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            long[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_LONG_UNALIGNED);
            long[] actual = (long[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

    @Test
    public void testDoubleArray() {
        for (VectorSpecies<Double> species: Arrays.asList(DoubleVector.SPECIES_64, DoubleVector.SPECIES_128, DoubleVector.SPECIES_256, DoubleVector.SPECIES_512, DoubleVector.SPECIES_MAX)) {
            double[] arr = new double[ARRAY_LEN];
            IntStream.range(0, ARRAY_LEN).forEach(i -> arr[i] = (double) i);
            MemorySegment segment = MemorySegment.ofArray(arr).asSlice(1);
            Vector<Double> vector = species.fromMemorySegment(segment, 0, ByteOrder.nativeOrder());
            double[] expected = segment.asSlice(0, species.vectorByteSize()).toArray(ValueLayout.JAVA_DOUBLE_UNALIGNED);
            double[] actual = (double[]) vector.toArray();
            assertEquals(actual, expected, species.toString());
        }
    }

}
