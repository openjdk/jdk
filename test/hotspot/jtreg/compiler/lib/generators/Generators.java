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

package compiler.lib.generators;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import jdk.test.lib.Utils;

/**
 * The generators class provides a set of generator functions for testing.
 * The goal is to cover many special cases, such as NaNs in Floats or values
 * close to overflow in ints. They should produce values from specific
 * "intersting" distributions which might trigger various behaviours in
 * optimizations.
 */
public final class Generators {
    static final Random RANDOM = Utils.getRandomInstance();

    private Generators() {
        throw new AssertionError();
    }

    /**
     * Randomly pick an int generator.
     *
     * @return Random int generator.
     */
    public static Generator<Integer> ints() {
        switch(RANDOM.nextInt(6)) {
            case 0  -> { return new UniformIntGenerator(); }
            case 1  -> { return specialInts(0); }
            case 2  -> { return specialInts(2); }
            case 3  -> { return specialInts(16); }
            case 4  -> { return intsMixedWithSpecial(1, 1, 16); }
            case 5  -> { return intsMixedWithSpecial(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    public static Generator<Integer> specialInts(int range) {
        HashSet<Long> set = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            long pow2 = 1L << i;
            for (int j = -range; j <= range; j++) {
                set.add(+pow2 + j);
                set.add(-pow2 + j);
            }
        }
        var values = set.stream().mapToLong(Number::longValue).toArray();
        Arrays.sort(values);
        return SpecialIntegralGenerator(values, new UniformIntGenerator());
    }

    public static Generator<Integer> intsMixedWithSpecial(int weightA, int weightB, int rangeSpecial) {
        return new MixedGenerator<>(new UniformLongGenerator(), specialInts(rangeSpecial), weightA, weightB);
    }

    /**
     * Randomly pick a long generator.
     *
     * @return Random long generator.
     */
    public static Generator<Long> longs() {
        switch(RANDOM.nextInt(6)) {
            case 0  -> { return new UniformLongGenerator(); }
            case 1  -> { return specialLongs(0); }
            case 2  -> { return specialLongs(2); }
            case 3  -> { return specialLongs(16); }
            case 4  -> { return longsMixedWithSpecial(1, 1, 16); }
            case 5  -> { return longsMixedWithSpecial(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    public static Generator<Long> specialLongs(int rangeSpecial) {}

    public static Generator<Long> longsMixedWithSpecial(int weightA, int weightB, int rangeSpecial) {
        return new MixedGenerator<>(new UniformLongGenerator(), specialLongs(rangeSpecial), weightA, weightB);
    }

    /**
     * Randomly pick a float generator.
     *
     * @return Random float generator.
     */
    public static Generator<Float> floats() {
        switch(RANDOM.nextInt(5)) {
            case 0  -> { return new UniformFloatGenerator(-1, 1); }
            // Well balanced, so that multiplication reduction never explodes or collapses to zero:
            case 1  -> { return new UniformFloatGenerator(0.999f, 1.001f); }
            case 2  -> { return new AnyBitsFloatGenerator(); }
            // A tame distribution, mixed in with the occasional special float value:
            case 3  -> { return specialFloats(new UniformFloatGenerator(0.999f, 1.001f), 10, 1000); }
            // Generating any bits, but special values are more frequent.
            case 4  -> { return specialFloats(new AnyBitsFloatGenerator(), 100, 200); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * Randomly pick a double generator.
     *
     * @return Random double generator.
     */
    public static Generator<Double> doubles() {
        switch(RANDOM.nextInt(5)) {
            case 0  -> { return new UniformDoubleGenerator(-1, 1); }
            // Well balanced, so that multiplication reduction never explodes or collapses to zero:
            case 1  -> { return new UniformDoubleGenerator(0.999f, 1.001f); }
            case 2  -> { return new AnyBitsDoubleGenerator(); }
            // A tame distribution, mixed in with the occasional special double value:
            case 3  -> { return specialDoubles(new UniformDoubleGenerator(0.999f, 1.001f), 10, 1000); }
            // Generating any bits, but special values are more frequent.
            case 4  -> { return specialDoubles(new AnyBitsDoubleGenerator(), 100, 200); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /*
     * Pre-generated values we can choose from.
     */
    private static final Double[] SPECIAL_DOUBLES = new Double[] {
        0d,
        1d,
        -1d,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NaN,
        Double.MAX_VALUE,
        Double.MIN_NORMAL,
        Double.MIN_VALUE,
    };

    public static Generator<Double> specialDoubles(Generator<Double> background, int specialMinDistance, int specialMaxDistance) {
        return new SpecialFloatingGenerator<>(background, SPECIAL_DOUBLES, specialMinDistance, specialMaxDistance);
    }

    /*
     * Pre-generated values we can choose from.
     */
    private static final Float[] SPECIAL_FLOATS = new Float[] {
        0f,
        1f,
        -1f,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Float.NaN,
        Float.MAX_VALUE,
        Float.MIN_NORMAL,
        Float.MIN_VALUE,
    };

    public static Generator<Float> specialFloats(Generator<Float> background, int specialMinDistance, int specialMaxDistance) {
        return new SpecialFloatingGenerator<>(background, SPECIAL_FLOATS, specialMinDistance, specialMaxDistance);
    }

    /**
     * Fills the memory segments with doubles obtained by calling next on the generator.
     *
     * @param generator The generator from which to source the values.
     * @param ms Memory segment to be filled with random values.
     */
    public void fill(Generator<Double> generator, MemorySegment ms) {
        var layout = ValueLayout.JAVA_DOUBLE_UNALIGNED;
        for (long i = 0; i < ms.byteSize() / layout.byteSize(); i++) {
            ms.setAtIndex(layout, i, generator.next());
        }
    }

    /**
     * Fill the array with doubles using the distribution of nextDouble.
     *
     * @param a Array to be filled with random values.
     */
    public void fill(double[] a) {
        fill(doubles(), a);
    }

    /**
     * Fill the array with doubles using the distribution of nextDouble.
     *
     * @param a Array to be filled with random values.
     */
    public void fill(Generator<Double> generator, double[] a) {
        fill(generator, MemorySegment.ofArray(a));
    }

}
