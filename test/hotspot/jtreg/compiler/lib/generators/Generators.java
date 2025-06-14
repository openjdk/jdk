/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static java.lang.Float.floatToFloat16;

import jdk.test.lib.Utils;

/**
 * The Generators class provides a set of random generator functions for testing.
 * The goal is to cover many special cases, such as NaNs in Floats or values
 * close to overflow in ints. They should produce values from specific
 * "interesting" distributions which might trigger various behaviours in
 * optimizations.
 * <p>
 * Normally, clients get the default Generators instance by referring to the static variable {@link #G}.
 * <p>
 * The Generators class offers generators with essential distributions, for example, {@link #uniformInts(int, int)},
 * {@link #uniformLongs(long, long)}, {@link #uniformDoubles(double, double)} or {@link #uniformFloats()}. For floating
 * points, you may choose to get random bit patterns uniformly at random, rather than the values they represent.
 * The Generators class also offers special generators of interesting values such as {@link #powerOfTwoInts(int)},
 * {@link #powerOfTwoLongs(int)}, which are values close to the powers of 2, or {@link #SPECIAL_DOUBLES} and
 * {@link #SPECIAL_FLOATS}, which are values such as infinity, NaN, zero or the maximum and minimum values.
 * <p>
 * Many distributions are <i>restrictable</i>. For example, if you first create a uniform integer generator over [1, 10],
 * you can obtain a new generator by further restricting this range to [1, 5]. This is useful in cases where a function
 * should be tested with different distributions. For example, a function <code>h(int, int, int)</code> under test might
 * be worthwhile to test not only with uniformly sampled integers but might also exhibit interesting behavior if tested
 * specifically with powers of two. Suppose further that each argument has a different range of allowed values. We
 * can write a test function as below:
 *
 * <pre><code>
 * void test(Generator{@literal <Integer>} g) {
 *     h(g.restricted(1, 10).next(), g.next(), g.restricted(-10, 100).next());
 * }
 * </code></pre>
 *
 * Then <code>test</code> can be called with different distributions, for example:
 *
 * <pre><code>
 * test(G.uniformInts());
 * test(G.specialInts(0));
 * </code></pre>
 * <p>
 * If there is a single value that is interesting as an argument to all three parameters, we might even call this
 * method with a single generator, ensuring that the single value is within the restriction ranges:
 *
 * <pre><code>
 * test(G.single(1));
 * </code></pre>
 *
 * <p>
 * Furthermore, this class offers utility generators, such as {@link #randomElement(Collection)} or
 * {@link #orderedRandomElement(Collection)} for sampling from a list of elements; {@link #single(Object)} for a
 * generator that only produces a single value; and {@link #mixed(Generator, Generator, int, int)} which combines
 * two generators with the provided weights.
 * <p>
 * Thus, the generators provided by this class are composable and therefore extensible. This allows to easily
 * create random generators even with types and distributions that are not predefined. For example, to create a
 * generator that provides true with 60 percent probably and false with 40 percent probably, one can simply write:
 * <pre><code>G.mixed(G.single(true), G.single(false), 60, 40)</code></pre>
 * <p>
 * Generators are by no means limited to work with numbers. Restrictable generators can work with any type that
 * implements {@link Comparable} while generators such as {@link #randomElement(Collection)} and {@link #single(Object)}
 * work with any type. Note that there are separate restrictable versions of the last two generators
 * (namely, {@link #orderedRandomElement(Collection)} and {@link #single(Comparable)}) that work with comparable types.
 * For example, you might restrict a generator choosing strings at random:
 * <pre><code>G.orderedRandomElement(List.of("Bob", "Alice", "Carol")).restricted("Al", "Bz")</code></pre>
 * This returns a new generator which only returns elements greater or equal than "Al" and less than or equal to
 * "Bz". Thus, the only two values remaining in the example are "Alice" and "Bob". In general, you should always refer
 * to the method that created the generator to learn about the exact semantics of restricting it.
 * <p>
 * For all the generators created by instances of this class, the following rule applies: Integral generators are
 * always inclusive of both the lower and upper bound, while floating point generators are always inclusive of the
 * lower bound but always exclusive of the upper bound. This also applies to all generators obtained by restricting
 * these generators further.
 * <p>
 * Unless you have reasons to pick a specific distribution, you are encouraged to rely on {@link #ints()},
 * {@link #longs()}, {@link #doubles()} and {@link #floats()}, which will randomly pick an interesting distribution.
 * This is best practice, because that allows the test to be run under different conditions - maybe only a single
 * distribution can trigger a bug.
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
     * Generates uniform doubles in the range of [lo, hi) (inclusive of lo, exclusive of hi).
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
     * Generates uniform float16s in the range of [lo, hi) (inclusive of lo, exclusive of hi).
     */
    public RestrictableGenerator<Short> uniformFloat16s(short lo, short hi) {
        return new UniformFloat16Generator(this, lo, hi);
    }

    /**
     * Generates uniform float16s in the range of [0, 1) (inclusive of 0, exclusive of 1).
     */
    public RestrictableGenerator<Short> uniformFloat16s() {
        return uniformFloat16s(floatToFloat16(0.0f), floatToFloat16(1.0f));
    }

    /**
     * Generates uniform doubles in the range of [lo, hi) (inclusive of lo, exclusive of hi).
     */
    public RestrictableGenerator<Float> uniformFloats(float lo, float hi) {
        return new UniformFloatGenerator(this, lo, hi);
    }

    /**
     * Provides an any-bits float16 distribution random generator, i.e. the bits are uniformly sampled,
     * thus creating any possible float16 value, including the multiple different NaN representations.
     */
    public Generator<Short> anyBitsFloat16s() {
        return new AnyBitsFloat16Generator(this);
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
     * list and are working with Comparable values, use {@link #orderedRandomElement(Collection)}.
     */
    public <T> Generator<T> randomElement(Collection<T> list) {
        return new RandomElementGenerator<>(this, list);
    }

    /**
     * Returns a restrictable generator that uniformly randomly samples elements from the provided collection.
     * Duplicate elements are discarded from the collection. Restrictions are inclusive of both the uppper and lower
     * bound.
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
     * Returns a restrictable generator that always generate the provided value.
     */
    public <T extends Comparable<T>> RestrictableGenerator<T> single(T value) {
        return new RestrictableSingleValueGenerator<>(value);
    }

    /**
     * Returns a new generator that samples its next element from either generator A or B, with assignable weights.
     * An overload for restrictable generators exists.
     */
    public <T> Generator<T> mixed(Generator<T> a, Generator<T> b, int weightA, int weightB) {
        return new MixedGenerator<>(this, List.of(a, b), List.of(weightA, weightB));
    }

    /**
     * Returns a new generator that samples its next element randomly from one of the provided generators with
     * assignable weights.
     * An overload for restrictable generators exists.
     */
    @SafeVarargs
    public final <T> Generator<T> mixed(List<Integer> weights, Generator<T>... generators) {
        return new MixedGenerator<>(this, Arrays.asList(generators), weights);
    }

    /**
     * Returns a new restrictable generator that samples its next element from either generator A or B, with assignable weights.
     * Restricting this generator restricts each subgenerator. Generators which become empty by the restriction are
     * removed from the new mixed generator. Weights stay their original value if a generator is removed. If the mixed
     * generator would become empty by applying a restriction {@link EmptyGeneratorException} is thrown.
     */
    public <T extends Comparable<T>> RestrictableGenerator<T> mixed(RestrictableGenerator<T> a, RestrictableGenerator<T> b, int weightA, int weightB) {
        return new RestrictableMixedGenerator<>(this, List.of(a, b), List.of(weightA, weightB));
    }

    /**
     * Returns a new restrictable generator that samples its next element randomly from one of the provided restrictable
     * generators with assignable weights.
     * See {@link #mixed(RestrictableGenerator, RestrictableGenerator, int, int)} for details about restricting this
     * generator.
     */
    @SafeVarargs
    public final <T extends Comparable<T>> RestrictableGenerator<T> mixed(List<Integer> weights, RestrictableGenerator<T>... generators) {
        return new RestrictableMixedGenerator<>(this, Arrays.asList(generators), weights);
    }

    /**
     * Randomly pick an int generator.
     *
     * @return Random int generator.
     */
    public RestrictableGenerator<Integer> ints() {
        switch(random.nextInt(0, 6)) {
            case 0  -> { return uniformInts(); }
            case 1  -> { return powerOfTwoInts(0); }
            case 2  -> { return powerOfTwoInts(2); }
            case 3  -> { return powerOfTwoInts(16); }
            case 4  -> { return uniformIntsMixedWithPowersOfTwo(1, 1, 16); }
            case 5  -> { return uniformIntsMixedWithPowersOfTwo(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * A generator of special ints. Special ints are powers of two or values close to powers of 2, where a value
     * is close to a power of two p if it is in the interval [p - range, p + range]. Note that we also consider negative
     * values as powers of two. Note that for range >= 1, the set of values includes {@link Integer#MAX_VALUE} and
     * {@link Integer#MIN_VALUE}.
     */
    public RestrictableGenerator<Integer> powerOfTwoInts(int range) {
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

    /**
     * A convenience helper to mix {@link #powerOfTwoInts(int)} with {@link #uniformInts(int, int)}.
     */
    public RestrictableGenerator<Integer> uniformIntsMixedWithPowersOfTwo(int weightUniform, int weightSpecial, int rangeSpecial) {
        return mixed(uniformInts(), powerOfTwoInts(rangeSpecial), weightUniform, weightSpecial);
    }

    /**
     * Randomly pick a long generator.
     *
     * @return Random long generator.
     */
    public RestrictableGenerator<Long> longs() {
        switch(random.nextInt(0, 6)) {
            case 0  -> { return uniformLongs(); }
            case 1  -> { return powerOfTwoLongs(0); }
            case 2  -> { return powerOfTwoLongs(2); }
            case 3  -> { return powerOfTwoLongs(16); }
            case 4  -> { return uniformLongsMixedWithPowerOfTwos(1, 1, 16); }
            case 5  -> { return uniformLongsMixedWithPowerOfTwos(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * A generator of special longs. Special longs are powers of two or values close to powers of 2, where a value
     * is close to a power of two p if it is in the interval [p - range, p + range]. Note that we also consider negative
     * values as powers of two. Note that for range >= 1, the set of values includes {@link Long#MAX_VALUE} and
     * {@link Long#MIN_VALUE}.
     */
    public RestrictableGenerator<Long> powerOfTwoLongs(int range) {
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

    /**
     * A convenience helper to mix {@link #powerOfTwoLongs(int)} with {@link #uniformLongs(long, long)}.
     */
    public RestrictableGenerator<Long> uniformLongsMixedWithPowerOfTwos(int weightUniform, int weightSpecial, int rangeSpecial) {
        return mixed(uniformLongs(), powerOfTwoLongs(rangeSpecial), weightUniform, weightSpecial);
    }

    /**
     * Randomly pick a float16 generator.
     *
     * @return Random float16 generator.
     */
    public Generator<Short> float16s() {
        switch(random.nextInt(0, 5)) {
            case 0  -> { return uniformFloat16s(floatToFloat16(-1.0f), floatToFloat16(1.0f)); }
            // Well-balanced, so that multiplication reduction never explodes or collapses to zero:
            case 1  -> { return uniformFloat16s(floatToFloat16(0.999f), floatToFloat16(1.001f)); }
            case 2  -> { return anyBitsFloat16s(); }
            // A tame distribution, mixed in with the occasional special float value:
            case 3  -> { return mixedWithSpecialFloat16s(uniformFloat16s(floatToFloat16(0.999f), floatToFloat16(1.001f)), 10, 1000); }
            // Generating any bits, but special values are more frequent.
            case 4  -> { return mixedWithSpecialFloat16s(anyBitsFloat16s(), 100, 200); }
            default -> { throw new RuntimeException("impossible"); }
        }
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

    /**
     * Generates interesting double values, which often are corner cases such as, 0, 1, -1, NaN, +/- Infinity, Min,
     * Max.
     */
    public final RestrictableGenerator<Double> SPECIAL_DOUBLES = orderedRandomElement(List.of(
        0d,
        -0d,
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
     * Returns a mixed generator that mixes the provided background generator and {@link #SPECIAL_DOUBLES} with the provided
     * weights.
     */
    public Generator<Double> mixedWithSpecialDoubles(Generator<Double> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_DOUBLES, weightNormal, weightSpecial);
    }

    /**
     * Returns a restrictable mixed generator that mixes the provided background generator and {@link #SPECIAL_DOUBLES} with the provided
     * weights.
     */
    public RestrictableGenerator<Double> mixedWithSpecialDoubles(RestrictableGenerator<Double> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_DOUBLES, weightNormal, weightSpecial);
    }

    /**
     * Generates interesting float values, which often are corner cases such as, 0, 1, -1, NaN, +/- Infinity, Min,
     * Max.
     */
    public final RestrictableGenerator<Float> SPECIAL_FLOATS = orderedRandomElement(List.of(
        0f,
        -0f,
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
     * Generates interesting float16 values, which often are corner cases such as, +/- 0, NaN, +/- Infinity, Min,
     * Max.
     */
    public final RestrictableGenerator<Short> SPECIAL_FLOAT16S = orderedRandomElement(List.of(
        floatToFloat16(0.0f),
        floatToFloat16(-0.0f),
        floatToFloat16(Float.POSITIVE_INFINITY),
        floatToFloat16(Float.NEGATIVE_INFINITY),
        floatToFloat16(Float.NaN),
        floatToFloat16(0x1.ffcP+15f), // MAX_VALUE
        floatToFloat16(0x1.0P-14f),   // MIN_NORMAL
        floatToFloat16(0x1.0P-24f)    // MIN_VALUE
    ));

    /**
     * Returns a mixed generator that mixes the provided background generator and {@link #SPECIAL_FLOAT16S} with the provided
     * weights.
     */
    public Generator<Short> mixedWithSpecialFloat16s(Generator<Short> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_FLOAT16S, weightNormal, weightSpecial);
    }

    /**
     * Returns a mixed generator that mixes the provided background generator and {@link #SPECIAL_FLOATS} with the provided
     * weights.
     */
    public Generator<Float> mixedWithSpecialFloats(Generator<Float> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_FLOATS, weightNormal, weightSpecial);
    }

    /**
     * Returns a restrictable mixed generator that mixes the provided background generator and {@link #SPECIAL_FLOATS} with the provided
     * weights.
     */
    public RestrictableGenerator<Float> mixedWithSpecialFloats(RestrictableGenerator<Float> background, int weightNormal, int weightSpecial) {
        return mixed(background, SPECIAL_FLOATS, weightNormal, weightSpecial);
    }

    /**
     * Trys to restrict the provided restrictable generator to the provided range. If the restriction fails no
     * exception is raised, but instead a uniform int generator for the range is returned.
     */
    public RestrictableGenerator<Integer> safeRestrict(RestrictableGenerator<Integer> g, int lo, int hi) {
        try {
            return g.restricted(lo, hi);
        } catch (EmptyGeneratorException e) {
            return uniformInts(lo, hi);
        }
    }

    /**
     * Trys to restrict the provided restrictable generator to the provided range. If the restriction fails no
     * exception is raised, but instead a uniform long generator for the range is returned.
     */
    public RestrictableGenerator<Long> safeRestrict(RestrictableGenerator<Long> g, long lo, long hi) {
        try {
            return g.restricted(lo, hi);
        } catch (EmptyGeneratorException e) {
            return uniformLongs(lo, hi);
        }
    }

    /**
     * Trys to restrict the provided restrictable generator to the provided range. If the restriction fails no
     * exception is raised, but instead a uniform double generator for the range is returned.
     */
    public RestrictableGenerator<Double> safeRestrict(RestrictableGenerator<Double> g, double lo, double hi) {
        try {
            return g.restricted(lo, hi);
        } catch (EmptyGeneratorException e) {
            return uniformDoubles(lo, hi);
        }
    }

    /**
     * Trys to restrict the provided restrictable generator to the provided range. If the restriction fails no
     * exception is raised, but instead a uniform float generator for the range is returned.
     */
    public RestrictableGenerator<Float> safeRestrict(RestrictableGenerator<Float> g, float lo, float hi) {
        try {
            return g.restricted(lo, hi);
        } catch (EmptyGeneratorException e) {
            return uniformFloats(lo, hi);
        }
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
     * Fills the memory segments with shorts obtained by calling next on the generator.
     *
     * @param generator The generator from which to source the values.
     * @param ms Memory segment to be filled with random values.
     */
    public void fillShort(Generator<Short> generator, MemorySegment ms) {
        var layout = ValueLayout.JAVA_SHORT_UNALIGNED;
        for (long i = 0; i < ms.byteSize() / layout.byteSize(); i++) {
            ms.setAtIndex(layout, i, generator.next());
        }
    }

    /**
     * Fill the array with shorts using the distribution of the generator.
     *
     * @param a Array to be filled with random values.
     */
    public void fill(Generator<Short> generator, short[] a) {
        fillShort(generator, MemorySegment.ofArray(a));
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
