/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.util.random;

import jdk.internal.random.L128X1024MixRandom;
import jdk.internal.random.L128X128MixRandom;
import jdk.internal.random.L128X256MixRandom;
import jdk.internal.random.L32X64MixRandom;
import jdk.internal.random.L64X1024MixRandom;
import jdk.internal.random.L64X128MixRandom;
import jdk.internal.random.L64X128StarStarRandom;
import jdk.internal.random.L64X256MixRandom;
import jdk.internal.random.Xoroshiro128PlusPlus;
import jdk.internal.random.Xoshiro256PlusPlus;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator;
import java.util.random.RandomGenerator.JumpableGenerator;
import java.util.random.RandomGenerator.LeapableGenerator;
import java.util.random.RandomGenerator.SplittableGenerator;
import java.util.random.RandomGenerator.StreamableGenerator;
import java.util.stream.Stream;

/**
 * This is a factory class for generating multiple random number generators
 * of a specific <a href="package-summary.html#algorithms">algorithm</a>.
 * {@link RandomGeneratorFactory} also provides
 * methods for selecting random number generator algorithms.
 *
 * A specific {@link RandomGeneratorFactory} can be located by using the
 * {@link RandomGeneratorFactory#of(String)} method, where the argument string
 * is the name of the <a href="package-summary.html#algorithms">algorithm</a>
 * required. The method
 * {@link RandomGeneratorFactory#all()} produces a non-empty {@link Stream} of all available
 * {@link RandomGeneratorFactory RandomGeneratorFactorys} that can be searched
 * to locate a {@link RandomGeneratorFactory} suitable to the task.
 *
 * There are three methods for constructing a RandomGenerator instance,
 * depending on the type of initial seed required.
 * {@link RandomGeneratorFactory#create(long)} is used for long
 * seed construction,
 * {@link RandomGeneratorFactory#create(byte[])} is used for byte[]
 * seed construction, and
 * {@link RandomGeneratorFactory#create()} is used for random seed
 * construction. Example;
 *
 * {@snippet :
 *    RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of("Random");
 *
 *     for (int i = 0; i < 10; i++) {
 *         new Thread(() -> {
 *             RandomGenerator random = factory.create(100L);
 *             System.out.println(random.nextDouble());
 *         }).start();
 *     }
 * }
 *
 * RandomGeneratorFactory also provides methods describing the attributes (or properties)
 * of a generator and can be used to select random number generator
 * <a href="package-summary.html#algorithms">algorithms</a>.
 * These methods are typically used in
 * conjunction with {@link RandomGeneratorFactory#all()}. In this example, the code
 * locates the {@link RandomGeneratorFactory} that produces
 * {@link RandomGenerator RandomGenerators}
 * with the highest number of state bits.
 *
 * {@snippet :
 *     RandomGeneratorFactory<RandomGenerator> best = RandomGeneratorFactory.all()
 *         .filter(rgf -> !rgf.name().equals("SecureRandom")) // SecureRandom has MAX_VALUE stateBits.
 *         .sorted(Comparator.comparingInt(RandomGeneratorFactory<RandomGenerator>::stateBits).reversed())
 *         .findFirst()
 *         .orElse(RandomGeneratorFactory.of("Random"));
 *     System.out.println(best.name() + " in " + best.group() + " was selected");
 *
 *     RandomGenerator rng = best.create();
 *     System.out.println(rng.nextLong());
 * }
 *
 * @param <T> type of created random generator
 *
 * @since 17
 *
 * @see java.util.random
 *
 */
public final class RandomGeneratorFactory<T extends RandomGenerator> {

    private static final String DEFAULT_ALGORITHM = "L32X64MixRandom";

    private record RandomGeneratorProperties(
            Class<? extends RandomGenerator> rgClass,
            String name,
            String group,
            int i,
            int j,
            int k,
            int equidistribution,
            int flags) {

        /* single bit masks composable with operator | */
        private static final int INSTANTIABLE       = 1 << 0;
        private static final int LONG_SEED          = 1 << 1;
        private static final int BYTE_ARRAY_SEED    = 1 << 2;
        private static final int STOCHASTIC         = 1 << 3;
        private static final int HARDWARE           = 1 << 4;
        private static final int DEPRECATED         = 1 << 5;

        private static final int ALL_CONSTRUCTORS = INSTANTIABLE | LONG_SEED | BYTE_ARRAY_SEED;

        private static final Map<String, RandomGeneratorProperties> FACTORY_MAP = createFactoryMap();

        /**
         * Returns the factory map, lazily constructing it on first use.
         * <p> Although {@link ThreadLocalRandom} can only be accessed via
         * {@link ThreadLocalRandom#current()}, a map entry is added nevertheless
         * to record its properties that are otherwise not documented
         * anywhere else.
         * <p> Currently, no algorithm is deprecated.
         *
         * @return Map of RandomGeneratorProperties.
         */
        private static Map<String, RandomGeneratorProperties> createFactoryMap() {
            return Map.ofEntries(
                    entry(SecureRandom.class, "SecureRandom", "Legacy",
                            0, 0, 0, Integer.MAX_VALUE,
                            INSTANTIABLE | BYTE_ARRAY_SEED | STOCHASTIC | deprecationBit(SecureRandom.class)),
                    entry(Random.class, "Random", "Legacy",
                            48, 0, 0, 0,
                            INSTANTIABLE | LONG_SEED | deprecationBit(Random.class)),
                    entry(SplittableRandom.class, "SplittableRandom", "Legacy",
                            64, 0, 0, 1,
                            INSTANTIABLE | LONG_SEED | deprecationBit(SplittableRandom.class)),
                    entry(L32X64MixRandom.class, "L32X64MixRandom", "LXM",
                            64, 1, 32, 1,
                            ALL_CONSTRUCTORS),
                    entry(L64X128MixRandom.class, "L64X128MixRandom", "LXM",
                            128, 1, 64, 2,
                            ALL_CONSTRUCTORS),
                    entry(L64X128StarStarRandom.class, "L64X128StarStarRandom", "LXM",
                            128, 1, 64, 2,
                            ALL_CONSTRUCTORS),
                    entry(L64X256MixRandom.class, "L64X256MixRandom", "LXM",
                            256, 1, 64, 4,
                            ALL_CONSTRUCTORS),
                    entry(L64X1024MixRandom.class, "L64X1024MixRandom", "LXM",
                            1024, 1, 64, 16,
                            ALL_CONSTRUCTORS),
                    entry(L128X128MixRandom.class, "L128X128MixRandom", "LXM",
                            128, 1, 128, 1,
                            ALL_CONSTRUCTORS),
                    entry(L128X256MixRandom.class, "L128X256MixRandom", "LXM",
                            256, 1, 128, 1,
                            ALL_CONSTRUCTORS),
                    entry(L128X1024MixRandom.class, "L128X1024MixRandom", "LXM",
                            1024, 1, 128, 1,
                            ALL_CONSTRUCTORS),
                    entry(Xoroshiro128PlusPlus.class, "Xoroshiro128PlusPlus", "Xoroshiro",
                            128, 1, 0, 1,
                            ALL_CONSTRUCTORS),
                    entry(Xoshiro256PlusPlus.class, "Xoshiro256PlusPlus", "Xoshiro",
                            256, 1, 0, 3,
                            ALL_CONSTRUCTORS),
                    entry(ThreadLocalRandom.class, "ThreadLocalRandom", "Legacy",
                            64, 0, 0, 1,
                            deprecationBit(ThreadLocalRandom.class))
            );
        }

        private static Map.Entry<String, RandomGeneratorProperties> entry(
                Class<? extends RandomGenerator> rgClass, String name, String group,
                int i, int j, int k, int equidistribution,
                int flags) {
            return Map.entry(name,
                    new RandomGeneratorProperties(rgClass, name, group,
                            i, j, k, equidistribution,
                            flags));
        }

        private static int deprecationBit(Class<? extends RandomGenerator> rgClass) {
            return rgClass.isAnnotationPresent(Deprecated.class) ? DEPRECATED : 0;
        }

        private RandomGenerator create() {
            return switch (name) {
                case "Random" ->                new Random();
                case "SecureRandom" ->          new SecureRandom();
                case "SplittableRandom" ->      new SplittableRandom();
                case "L32X64MixRandom" ->       new L32X64MixRandom();
                case "L64X128MixRandom" ->      new L64X128MixRandom();
                case "L64X128StarStarRandom" -> new L64X128StarStarRandom();
                case "L64X256MixRandom" ->      new L64X256MixRandom();
                case "L64X1024MixRandom" ->     new L64X1024MixRandom();
                case "L128X128MixRandom" ->     new L128X128MixRandom();
                case "L128X256MixRandom" ->     new L128X256MixRandom();
                case "L128X1024MixRandom" ->    new L128X1024MixRandom();
                case "Xoroshiro128PlusPlus" ->  new Xoroshiro128PlusPlus();
                case "Xoshiro256PlusPlus" ->    new Xoshiro256PlusPlus();
                default -> throw new InternalError("should not happen");
            };
        }

        private RandomGenerator create(long seed) {
            if (isInstantiable() && (flags & LONG_SEED) == 0) {
                throw new UnsupportedOperationException("Random algorithm "
                        + name + " does not support a long seed");
            }
            return switch (name) {
                case "Random" ->                new Random(seed);
                case "SplittableRandom" ->      new SplittableRandom(seed);
                case "L32X64MixRandom" ->       new L32X64MixRandom(seed);
                case "L64X128MixRandom" ->      new L64X128MixRandom(seed);
                case "L64X128StarStarRandom" -> new L64X128StarStarRandom(seed);
                case "L64X256MixRandom" ->      new L64X256MixRandom(seed);
                case "L64X1024MixRandom" ->     new L64X1024MixRandom(seed);
                case "L128X128MixRandom" ->     new L128X128MixRandom(seed);
                case "L128X256MixRandom" ->     new L128X256MixRandom(seed);
                case "L128X1024MixRandom" ->    new L128X1024MixRandom(seed);
                case "Xoroshiro128PlusPlus" ->  new Xoroshiro128PlusPlus(seed);
                case "Xoshiro256PlusPlus" ->    new Xoshiro256PlusPlus(seed);
                default -> throw new InternalError("should not happen");
            };
        }

        private RandomGenerator create(byte[] seed) {
            if (isInstantiable() && (flags & BYTE_ARRAY_SEED) == 0) {
                throw new UnsupportedOperationException("Random algorithm "
                        + name + " does not support a byte[] seed");
            }
            return switch (name) {
                case "SecureRandom" ->          new SecureRandom(seed);
                case "L32X64MixRandom" ->       new L32X64MixRandom(seed);
                case "L64X128MixRandom" ->      new L64X128MixRandom(seed);
                case "L64X128StarStarRandom" -> new L64X128StarStarRandom(seed);
                case "L64X256MixRandom" ->      new L64X256MixRandom(seed);
                case "L64X1024MixRandom" ->     new L64X1024MixRandom(seed);
                case "L128X128MixRandom" ->     new L128X128MixRandom(seed);
                case "L128X256MixRandom" ->     new L128X256MixRandom(seed);
                case "L128X1024MixRandom" ->    new L128X1024MixRandom(seed);
                case "Xoroshiro128PlusPlus" ->  new Xoroshiro128PlusPlus(seed);
                case "Xoshiro256PlusPlus" ->    new Xoshiro256PlusPlus(seed);
                default -> throw new InternalError("should not happen");
            };
        }

        private boolean isStochastic() {
            return (flags & STOCHASTIC) != 0;
        }

        private boolean isHardware() {
            return (flags & HARDWARE) != 0;
        }

        private boolean isInstantiable() {
            return (flags & INSTANTIABLE) != 0;
        }

        private boolean isDeprecated() {
            return (flags & DEPRECATED) != 0;
        }

        private BigInteger period() {
            /*
             * 0                if i = j = k = 0
             * (2^i - j) 2^k    otherwise
             */
            return i == 0 && j == 0 && k == 0
                    ? BigInteger.ZERO
                    : BigInteger.ONE.shiftLeft(i).subtract(BigInteger.valueOf(j)).shiftLeft(k);
        }

        private int stateBits() {
            return i == 0 && k == 0 ? Integer.MAX_VALUE : i + k;
        }
    }

    /**
     * Random generator properties.
     */
    private final RandomGeneratorProperties properties;

    /**
     * Private constructor.
     *
     * @param properties Random generator properties.
     */
    private RandomGeneratorFactory(RandomGeneratorProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns the factory map, lazily constructing the map on first call.
     *
     * @return Map of random generator classes.
     */
    private static Map<String, RandomGeneratorProperties> getFactoryMap() {
        return RandomGeneratorProperties.FACTORY_MAP;
    }

    /**
     * Return true if the random generator class is a subclass of the category.
     *
     * @param category Interface category, sub-interface of {@link RandomGenerator}.
     *
     * @return true if the random generator class is a subclass of the category.
     */
    private boolean isSubclass(Class<? extends RandomGenerator> category) {
        return isSubclass(category, properties.rgClass());
    }

    /**
     * Return true if rgClass is a subclass of the category.
     *
     * @param category Interface category, sub-interface of {@link RandomGenerator}.
     * @param rgClass Class that is being filtered.
     *
     * @return true if rgClass is a subclass of the category.
     */
    private static boolean isSubclass(Class<? extends RandomGenerator> category,
            Class<? extends RandomGenerator> rgClass) {
        return rgClass != null && category.isAssignableFrom(rgClass);
    }

    /**
     * Returns a RandomGeneratorProperties instance matching name and category.
     *
     * @param name     Name of RandomGenerator
     * @param category Interface category, sub-interface of {@link RandomGenerator}.
     * @return A RandomGeneratorProperties instance matching name and category.
     * @throws IllegalArgumentException if the resulting type is not a subclass of category.
     */
    private static RandomGeneratorProperties findClass(String name,
            Class<? extends RandomGenerator> category) throws IllegalArgumentException {
        RandomGeneratorProperties properties = name != null
                ? getFactoryMap().get(name)
                : null;
        if (properties == null || !properties.isInstantiable()) {
            throw new IllegalArgumentException("No implementation of the random number generator algorithm \"" +
                    name +
                    "\" is available");
        }
        if (!isSubclass(category, properties.rgClass())) {
            throw new IllegalArgumentException("The random number generator algorithm \"" +
                    name +
                    "\" is not implemented with the interface \"" +
                    category.getSimpleName() +
                    "\"");
        }
        return properties;
    }

    /**
     * Returns a {@link RandomGenerator} that utilizes the {@code name}
     * <a href="package-summary.html#algorithms">algorithm</a>.
     *
     * @param name      Name of random number algorithm to use
     * @param category  Sub-interface of {@link RandomGenerator} to type check
     * @param <T>       Sub-interface of {@link RandomGenerator} to produce
     *
     * @return An instance of {@link RandomGenerator}
     *
     * @throws IllegalArgumentException when either the name or category is null
     */
    static <T extends RandomGenerator> T of(String name, Class<T> category)
            throws IllegalArgumentException {
        @SuppressWarnings("unchecked")
        T instance = (T) findClass(name, category).create();
        return instance;
    }

    /**
     * Returns a {@link RandomGeneratorFactory} that will produce instances
     * of {@link RandomGenerator} that utilizes the named algorithm.
     *
     * @param name  Name of random number algorithm to use
     * @param category Sub-interface of {@link RandomGenerator} to type check
     * @param <T> Sub-interface of {@link RandomGenerator} to produce
     *
     * @return Factory of {@link RandomGenerator}
     *
     * @throws IllegalArgumentException when either the name or category is null
     */
    static <T extends RandomGenerator> RandomGeneratorFactory<T> factoryOf(String name, Class<T> category)
            throws IllegalArgumentException {
        return new RandomGeneratorFactory<>(findClass(name, category));
    }

    /**
     * Returns a {@link RandomGeneratorFactory} that can produce instances of
     * {@link RandomGenerator} that utilize the {@code name}
     * <a href="package-summary.html#algorithms">algorithm</a>.
     *
     * @param name  Name of random number generator
     * <a href="package-summary.html#algorithms">algorithm</a>
     * @param <T> Sub-interface of {@link RandomGenerator} to produce
     *
     * @return {@link RandomGeneratorFactory} of {@link RandomGenerator}
     *
     * @throws NullPointerException if name is null
     * @throws IllegalArgumentException if the named algorithm is not found
     */
    public static <T extends RandomGenerator> RandomGeneratorFactory<T> of(String name) {
        Objects.requireNonNull(name);
        @SuppressWarnings("unchecked")
        RandomGeneratorFactory<T> factory =
                (RandomGeneratorFactory<T>) factoryOf(name, RandomGenerator.class);
        return factory;
    }

    /**
     * Returns a {@link RandomGeneratorFactory} meeting the minimal requirement
     * of having an algorithm whose state bits are greater than or equal 64.
     *
     * @implSpec  Since algorithms will improve over time, there is no
     * guarantee that this method will return the same algorithm over time.
     *
     * @return a {@link RandomGeneratorFactory}
     */
    public static RandomGeneratorFactory<RandomGenerator> getDefault() {
        return factoryOf(DEFAULT_ALGORITHM, RandomGenerator.class);
    }

    /**
     * Returns a non-empty stream of available {@link RandomGeneratorFactory RandomGeneratorFactory(s)}.
     *
     * RandomGenerators that are marked as deprecated are not included in the result.
     *
     * @return a non-empty stream of all available {@link RandomGeneratorFactory RandomGeneratorFactory(s)}.
     */
    public static Stream<RandomGeneratorFactory<RandomGenerator>> all() {
        return getFactoryMap().values()
                .stream()
                .filter(p -> p.isInstantiable() && !p.isDeprecated())
                .map(RandomGeneratorFactory::new);
    }

    /**
     * Return the name of the <a href="package-summary.html#algorithms">algorithm</a>
     * used by the random number generator.
     *
     * @return Name of the <a href="package-summary.html#algorithms">algorithm</a>.
     */
    public String name() {
        return properties.name();
    }

    /**
     * Return the group name of the <a href="package-summary.html#algorithms">algorithm</a>
     * used by the random number generator.
     *
     * @return Group name of the <a href="package-summary.html#algorithms">algorithm</a>.
     */
    public String group() {
        return properties.group();
    }

    /**
     * Returns number of bits used by the <a href="package-summary.html#algorithms">algorithm</a>
     * to maintain state of seed.
     *
     * @return number of bits used by the <a href="package-summary.html#algorithms">algorithm</a>
     *         to maintain state of seed.
     */
    public int stateBits() {
        return properties.stateBits();
    }

    /**
     * Returns the equidistribution of the <a href="package-summary.html#algorithms">algorithm</a>.
     *
     * @return the equidistribution of the <a href="package-summary.html#algorithms">algorithm</a>.
     */
    public int equidistribution() {
        return properties.equidistribution();
    }

    /**
     * Return the period of the <a href="package-summary.html#algorithms">algorithm</a>
     * used by the random number generator.
     * Returns BigInteger.ZERO if period is not determinable.
     *
     * @return BigInteger period.
     */
    public BigInteger period() {
        return properties.period();
    }

    /**
     * Return true if random generator is computed using an arithmetic
     * <a href="package-summary.html#algorithms">algorithm</a>
     * and is statistically deterministic.
     *
     * @return true if random generator is statistical.
     */
    public boolean isStatistical() {
        return !properties.isStochastic();
    }

    /**
     * Return true if random generator is computed using external or entropic
     * sources as inputs.
     *
     * @return true if random generator is stochastic.
     */
    public boolean isStochastic() {
        return properties.isStochastic();
    }

    /**
     * Return true if random generator uses a hardware device (HRNG) to produce
     * entropic input.
     *
     * @return true if random generator is generated by hardware.
     */
    public boolean isHardware() {
        return properties.isHardware();
    }

    /**
     * Return true if random generator can jump an arbitrarily specified distant
     * point in the state cycle.
     *
     * @return true if random generator is arbitrarily jumpable.
     */
    public boolean isArbitrarilyJumpable() {
        return isSubclass(ArbitrarilyJumpableGenerator.class);
    }

    /**
     * Return true if random generator can jump a specified distant point in
     * the state cycle.
     *
     * @return true if random generator is jumpable.
     */
    public boolean isJumpable() {
        return isSubclass(JumpableGenerator.class);
    }

    /**
     * Return true if random generator is jumpable and can leap to a very distant
     * point in the state cycle.
     *
     * @return true if random generator is leapable.
     */
    public boolean isLeapable() {
        return isSubclass(LeapableGenerator.class);
    }

    /**
     * Return true if random generator can be cloned into a separate object with
     * the same properties but positioned further in the state cycle.
     *
     * @return true if random generator is splittable.
     */
    public boolean isSplittable() {
        return isSubclass(SplittableGenerator.class);
    }

    /**
     * Return true if random generator can be used to create
     * {@link java.util.stream.Stream Streams} of random numbers.
     *
     * @return true if random generator is streamable.
     */
    public boolean isStreamable() {
        return isSubclass(StreamableGenerator.class);
    }

    /**
     * Return true if the implementation of RandomGenerator (algorithm) has been
     * marked for deprecation.
     *
     * @implNote Random number generator algorithms evolve over time; new
     *           algorithms will be introduced and old algorithms will
     *           lose standing. If an older algorithm is deemed unsuitable
     *           for continued use, it will be marked as deprecated to indicate
     *           that it may be removed at some point in the future.
     *
     * @return true if the implementation of RandomGenerator (algorithm) has been
     *         marked for deprecation
     */
     public boolean isDeprecated() {
        return properties.isDeprecated();
     }

    /**
     * Create an instance of {@link RandomGenerator} based on the
     * <a href="package-summary.html#algorithms">algorithm</a> chosen.
     *
     * @return new instance of {@link RandomGenerator}.
     */
    public T create() {
        @SuppressWarnings("unchecked")
        T instance = (T) properties.create();
        return instance;
    }

    /**
     * Create an instance of {@link RandomGenerator} based on the
     * <a href="package-summary.html#algorithms">algorithm</a> chosen,
     * and the provided {@code seed}.
     * If the {@link RandomGenerator} doesn't support instantiation through
     * a {@code seed} of type {@code long} then this method throws
     * an {@link UnsupportedOperationException}.
     *
     * @param seed long random seed value.
     *
     * @return new instance of {@link RandomGenerator}.
     *
     * @throws UnsupportedOperationException
     *      if a {@code seed} of type {@code long} in not supported.
     */
    public T create(long seed) {
        @SuppressWarnings("unchecked")
        T instance = (T) properties.create(seed);
        return instance;
    }

    /**
     * Create an instance of {@link RandomGenerator} based on the
     * <a href="package-summary.html#algorithms">algorithm</a> chosen,
     * and the provided {@code seed}.
     * If the {@link RandomGenerator} doesn't support instantiation through
     * a {@code seed} of type {@code byte[]} then this method throws
     * an {@link UnsupportedOperationException}.
     *
     * @param seed byte array random seed value.
     *
     * @return new instance of {@link RandomGenerator}.
     *
     * @throws UnsupportedOperationException
     *      if a {@code seed} of type {@code byte[]} in not supported.
     *
     * @throws NullPointerException if seed is null.
     */
    public T create(byte[] seed) {
        Objects.requireNonNull(seed, "seed must not be null");
        @SuppressWarnings("unchecked")
        T instance = (T) properties.create(seed);
        return instance;
    }

}
