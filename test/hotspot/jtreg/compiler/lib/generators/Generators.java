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
import java.util.*;

import jdk.test.lib.Utils;

/**
 * The generators class provides a set of random generator functions for testing.
 * The goal is to cover many special cases, such as NaNs in Floats or values
 * close to overflow in ints. They should produce values from specific
 * "interesting" distributions which might trigger various behaviours in
 * optimizations.
 * <p>
 * Normally, clients get the default Generators instance by referring to the static variable <code>G</code>.
 * <p>
 * Furthermore, the generators provided by this class are composable and therefore extensible. This allows to easily
 * create random generators even with types and distributions that are not predefined. For example, to create a
 * generator that provides true with 60 percent probably and false with 40 percent probably, one can simply write:
 * <p>
 * <code>G.mixed(G.single(true), G.single(false), 60, 40)</code>
 * <p>
 * For all the generators created by instances of this class, the following rule applies: Integral generators are
 * always inclusive of both the lower and upper bound, while floating point generators are always inclusive of the
 * lower bound but always exclusive of the upper bound. This also applies to all generators obtained by restricting
 * these generators further.
 */
public final class Generators {
    /**
     * This is the default Generators instance that should be used by tests normally.
     */
    public static final Generators G = new Generators(new RandomnessSourceAdapter(Utils.getRandomInstance()));

    final RandomnessSource random;

    public Generators(RandomnessSource random) {
        this.random = random;
    }

    /**
     * Returns a generator that generates integers in the range [lo, hi] (inclusive of both lo and hi).
     */
    public RestrictableGenerator<Integer> uniformInts(int lo, int hi) {
        return new UniformIntGenerator(this, lo, hi);
    }

    /**
     * Returns a generator that generates integers over the entire range of int.
     */
    public RestrictableGenerator<Integer> uniformInts() {
        return uniformInts(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns a generator that generates longs in the range [lo, hi] (inclusive of both lo and hi).
     */
    public RestrictableGenerator<Long> uniformLongs(long lo, long hi) {
        return new UniformLongGenerator(this, lo, hi);
    }

    /**
     * Returns a generator that generates integers over the entire range of int.
     */
    public RestrictableGenerator<Long> uniformLongs() {
        return uniformLongs(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * Generates uniform doubles in the range of [lo, hi) (inclusive of 0, exclusive of 1).
     */
    public RestrictableGenerator<Double> uniformDoubles(double lo, double hi) {
        return new UniformDoubleGenerator(this, lo, hi);
    }

    /**
     * Generates uniform doubles in the range of [0, 1) (inclusive of 0, exclusive of 1).
     */
    public RestrictableGenerator<Double> uniformDoubles() {
        return uniformDoubles(0, 1);
    }

    /**
     * Provides an any-bits double distribution random generator, i.e. the bits are uniformly sampled,
     * thus creating any possible double value, including the multiple different NaN representations.
     */
    public Generator<Double> anyBitsDouble() {
        return new AnyBitsDoubleGenerator(this);
    }

    /**
     * Generates uniform doubles in the range of [lo, hi) (inclusive of 0, exclusive of 1).
     */
    public RestrictableGenerator<Float> uniformFloats(float lo, float hi) {
        return new UniformFloatGenerator(this, lo, hi);
    }

    /**
     * Generates uniform floats in the range of [0, 1) (inclusive of 0, exclusive of 1).
     */
    public RestrictableGenerator<Float> uniformFloats() {
        return uniformFloats(0, 1);
    }

    /**
     * Provides an any-bits float distribution random generator, i.e. the bits are uniformly sampled,
     * thus creating any possible float value, including the multiple different NaN representations.
     */
    public Generator<Float> anyBitsFloats() {
        return new AnyBitsFloatGenerator(this);
    }

    /**
     * Returns a generator that uniformly randomly samples elements from the provided collection.
     * Each element in the collection is treated as a separate, unique value, even if equals might be true.
     * The result is an unrestrictable generator. If you want a restrictable generator that selects values from a
     * list and are working with Comparable values, use randomComparableElement.
     */
    public <T> Generator<T> randomElement(Collection<T> list) {
        return new RandomElementGenerator<>(this, list);
    }

    /**
     * Returns a restrictable generator that uniformly randomly samples elements from the provided collection.
     * Duplicate elements are discarded from the collection.
     */
    public <T extends Comparable<T>> RestrictableGenerator<T> orderedRandomElement(Collection<T> list) {
        NavigableSet<T> set = list instanceof NavigableSet<T> ? (NavigableSet<T>) list : new TreeSet<>(list);
        return new RestrictableRandomElementGenerator<>(this, set);
    }

    /**
     * Returns a generator that always generate the provided value.
     */
    public <T> Generator<T> single(T value) {
        return new SingleValueGenerator<>(value);
    }

    /**
     * Returns a new generator that samples its next element from either generator A or B, with assignable weights.
     */
    public <T> Generator<T> mixed(Generator<T> a, Generator<T> b, int weightA, int weightB) {
        return new MixedGenerator<>(this, a, b, weightA, weightB);
    }

    /**
     * Randomly pick an int generator.
     *
     * @return Random int generator.
     */
    public Generator<Integer> ints() {
        switch(random.nextInt(0, 6)) {
            case 0  -> { return uniformInts(); }
            case 1  -> { return specialInts(0); }
            case 2  -> { return specialInts(2); }
            case 3  -> { return specialInts(16); }
            case 4  -> { return mixedWithSpecialInts(1, 1, 16); }
            case 5  -> { return mixedWithSpecialInts(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * A generator of special ints. Special ints are powers of two or values close to powers of 2, where a value
     * is close to a power of two p if it is in the interval [p - range, p + range]. Note that we also consider negative
     * values as powers of two.
     */
    public RestrictableGenerator<Integer> specialInts(int range) {
        TreeSet<Integer> set = new TreeSet<>();
        for (int i = 0; i < 32; i++) {
            int pow2 = 1 << i;
            for (int j = -range; j <= range; j++) {
                set.add(+pow2 + j);
                set.add(-pow2 + j);
            }
        }
        return orderedRandomElement(set);
    }

    public Generator<Integer> mixedWithSpecialInts(int weightA, int weightB, int rangeSpecial) {
        return mixed(uniformInts(), specialInts(rangeSpecial), weightA, weightB);
    }

    /**
     * Randomly pick a long generator.
     *
     * @return Random long generator.
     */
    public Generator<Long> longs() {
        switch(random.nextInt(0, 6)) {
            case 0  -> { return uniformLongs(); }
            case 1  -> { return specialLongs(0); }
            case 2  -> { return specialLongs(2); }
            case 3  -> { return specialLongs(16); }
            case 4  -> { return mixedWithSpecialLongs(1, 1, 16); }
            case 5  -> { return mixedWithSpecialLongs(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * A generator of special longs. Special longs are powers of two or values close to powers of 2, where a value
     * is close to a power of two p if it is in the interval [p - range, p + range]. Note that we also consider negative
     * values as powers of two.
     */
    public Generator<Long> specialLongs(int range) {
        TreeSet<Long> set = new TreeSet<>();
        for (int i = 0; i < 64; i++) {
            long pow2 = 1L << i;
            for (int j = -range; j <= range; j++) {
                set.add(+pow2 + j);
                set.add(-pow2 + j);
            }
        }
        return orderedRandomElement(set);
    }

    public Generator<Long> mixedWithSpecialLongs(int weightA, int weightB, int rangeSpecial) {
        return mixed(uniformLongs(), specialLongs(rangeSpecial), weightA, weightB);
    }

    /**
     * Randomly pick a float generator.
     *
     * @return Random float generator.
     */
    public Generator<Float> floats() {
        switch(random.nextInt(0, 5)) {
            case 0  -> { return uniformFloats(-1, 1); }
            // Well-balanced, so that multiplication reduction never explodes or collapses to zero:
            case 1  -> { return uniformFloats(0.999f, 1.001f); }
            case 2  -> { return anyBitsFloats(); }
            // A tame distribution, mixed in with the occasional special float value:
            case 3  -> { return mixedWithSpecialFloats(uniformFloats(0.999f, 1.001f), 10, 1000); }
            // Generating any bits, but special values are more frequent.
            case 4  -> { return mixedWithSpecialFloats(anyBitsFloats(), 100, 200); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * Randomly pick a double generator.
     *
     * @return Random double generator.
     */
    public Generator<Double> doubles() {
        switch(random.nextInt(0, 5)) {
            case 0  -> { return uniformDoubles(-1, 1); }
            // Well-balanced, so that multiplication reduction never explodes or collapses to zero:
            case 1  -> { return uniformDoubles(0.999f, 1.001f); }
            case 2  -> { return anyBitsDouble(); }
            // A tame distribution, mixed in with the occasional special double value:
            case 3  -> { return mixedWithSpecialDoubles(uniformDoubles(0.999f, 1.001f), 10, 1000); }
            // Generating any bits, but special values are more frequent.
            case 4  -> { return mixedWithSpecialDoubles(anyBitsDouble(), 100, 200); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /*
     * Generates interesting double values, which often are corner cases such as, 0, 1, -1, NaN, +/- Infinity, Min,
     * Max.
     */
    public final Generator<Double> SPECIAL_DOUBLES = randomElement(List.of(
        0d,
        1d,
        -1d,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NaN,
        Double.MAX_VALUE,
        Double.MIN_NORMAL,
        Double.MIN_VALUE
    ));

    /**
     * Returns a mixed generator that mixes the provided background generator and SPECIAL_DOUBLES with the provided
     * weights.
     */
    public Generator<Double> mixedWithSpecialDoubles(Generator<Double> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_DOUBLES, weightNormal, weightSpecial);
    }

    /*
     * Generates interesting double values, which often are corner cases such as, 0, 1, -1, NaN, +/- Infinity, Min,
     * Max.
     */
    public final Generator<Float> SPECIAL_FLOATS = randomElement(List.of(
        0f,
        1f,
        -1f,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Float.NaN,
        Float.MAX_VALUE,
        Float.MIN_NORMAL,
        Float.MIN_VALUE
    ));

    /**
     * Returns a mixed generator that mixes the provided background generator and SPECIAL_FLOATS with the provided
     * weights.
     */
    public Generator<Float> mixedWithSpecialFloats(Generator<Float> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_FLOATS, weightNormal, weightSpecial);
    }

    /**
     * Fills the memory segments with doubles obtained by calling next on the generator.
     *
     * @param generator The generator from which to source the values.
     * @param ms Memory segment to be filled with random values.
     */
    public void fillDouble(Generator<Double> generator, MemorySegment ms) {
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
    public void fill(Generator<Double> generator, double[] a) {
        fillDouble(generator, MemorySegment.ofArray(a));
    }

    /**
     * Fills the memory segments with floats obtained by calling next on the generator.
     *
     * @param generator The generator from which to source the values.
     * @param ms Memory segment to be filled with random values.
     */
    public void fillFloat(Generator<Float> generator, MemorySegment ms) {
        var layout = ValueLayout.JAVA_FLOAT_UNALIGNED;
        for (long i = 0; i < ms.byteSize() / layout.byteSize(); i++) {
            ms.setAtIndex(layout, i, generator.next());
        }
    }

    /**
     * Fill the array with floats using the distribution of nextDouble.
     *
     * @param a Array to be filled with random values.
     */
    public void fill(Generator<Float> generator, float[] a) {
        fillFloat(generator, MemorySegment.ofArray(a));
    }

    /**
     * Fills the memory segments with ints obtained by calling next on the generator.
     *
     * @param generator The generator from which to source the values.
     * @param ms Memory segment to be filled with random values.
     */
    public void fillInt(Generator<Integer> generator, MemorySegment ms) {
        var layout = ValueLayout.JAVA_INT_UNALIGNED;
        for (long i = 0; i < ms.byteSize() / layout.byteSize(); i++) {
            ms.setAtIndex(layout, i, generator.next());
        }
    }

    /**
     * Fill the array with ints using the distribution of nextDouble.
     *
     * @param a Array to be filled with random values.
     */
    public void fill(Generator<Integer> generator, int[] a) {
        fillInt(generator, MemorySegment.ofArray(a));
    }

    /**
     * Fills the memory segments with longs obtained by calling next on the generator.
     *
     * @param generator The generator from which to source the values.
     * @param ms Memory segment to be filled with random values.
     */
    public void fillLong(Generator<Long> generator, MemorySegment ms) {
        var layout = ValueLayout.JAVA_LONG_UNALIGNED;
        for (long i = 0; i < ms.byteSize() / layout.byteSize(); i++) {
            ms.setAtIndex(layout, i, generator.next());
        }
    }

    /**
     * Fill the array with longs using the distribution of nextDouble.
     *
     * @param a Array to be filled with random values.
     */
    public void fill(Generator<Long> generator, long[] a) {
        fillLong(generator, MemorySegment.ofArray(a));
    }
}
