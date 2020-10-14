/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.util.function.Function;
import java.util.Map;
import java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator;
import java.util.random.RandomGenerator.JumpableGenerator;
import java.util.random.RandomGenerator.LeapableGenerator;
import java.util.random.RandomGenerator.SplittableGenerator;
import java.util.random.RandomGenerator.StreamableGenerator;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@preview Associated with random number generators, a preview feature of
 *           the Java core libraries.
 *
 *           This class is associated with <i>random number generators</i>,
 *           a preview feature of the Java core libraries. Programs can only use
 *           this class when preview features are enabled. Preview features
 *           may be removed in a future release, or upgraded to permanent
 *           features of the Java core libraries.}
 *
 * This is a factory class for generating random number generators of a specific
 * category and algorithm.
 *
 * @since   16
 *
 * @jdk.internal.PreviewFeature(feature= PreviewFeature.Feature.RANDOM_NUMBERS,
 *          essentialAPI=true)
 * @SuppressWarnings("preview")
 */
public class RandomGeneratorFactory<T> {
    /**
     * Map of provider classes.
     */
    private static Map<String, Provider<? extends RandomGenerator>> providerMap;

    /**
     * Instance provider class of random number algorithm.
     */
    private final Provider<? extends RandomGenerator> provider;

    /**
     * Default provider constructor.
     */
    private Constructor<T> ctor;

    /**
     * Provider constructor with long seed.
     */
    private Constructor<T> ctorLong;

    /**
     * Provider constructor with byte[] seed.
     */
    private Constructor<T> ctorBytes;

    /**
     * Private constructor.
     *
     * @param provider  Provider class to wrap.
     */
    private RandomGeneratorFactory(Provider<? extends RandomGenerator> provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider map, lazily constructing map on first call.
     *
     * @return Map of provider classes.
     */
    private static Map<String, Provider<? extends RandomGenerator>> getProviderMap() {
        if (providerMap == null) {
            synchronized (RandomGeneratorFactory.class) {
                if (providerMap == null) {
                    providerMap =
                        ServiceLoader
                            .load(RandomGenerator.class)
                            .stream()
                            .filter(p -> !p.type().isInterface())
                            .collect(Collectors.toMap(p -> p.type().getSimpleName().toUpperCase(),
                                    Function.identity()));
                }
            }
        }
        return providerMap;
    }

    /**
     * Return true if the provider is a subclass of the category.
     *
     * @param category Interface category, sub-interface of {@link RandomGenerator}.
     *
     * @return true if the provider is a subclass of the category.
     */
    private boolean isSubclass(Class<? extends RandomGenerator> category) {
        return isSubclass(category, provider);
    }

    /**
     * Return true if the provider is a subclass of the category.
     *
     * @param category Interface category, sub-interface of {@link RandomGenerator}.
     * @param provider Provider that is being filtered.
     *
     * @return true if the provider is a subclass of the category.
     */
    private static boolean isSubclass(Class<? extends RandomGenerator> category,
                                      Provider<? extends RandomGenerator> provider) {
        return provider != null && category.isAssignableFrom(provider.type());
    }

    /**
     * Returns the provider matching name and category.
     *
     * @param name      Name of RandomGenerator
     * @param category  Interface category, sub-interface of {@link RandomGenerator}.
     *
     * @return A provider matching name and category.
     *
     * @throws IllegalArgumentException if provider is not a subclass of category.
     */
    private static Provider<? extends RandomGenerator> findProvider(String name,
                                                                    Class<? extends RandomGenerator> category)
            throws IllegalArgumentException {
        Map<String, Provider<? extends RandomGenerator>> pm = getProviderMap();
        Provider<? extends RandomGenerator> provider = pm.get(name.toUpperCase());
        if (!isSubclass(category, provider)) {
            throw new IllegalArgumentException(name + " is an unknown random number generator");
        }
        return provider;
    }

    /**
     * Returns a stream of matching Providers.
     *
     * @param category  Sub-interface of {@link RandomGenerator} to type check
     * @param <T>       Sub-interface of {@link RandomGenerator} to produce
     *
     * @return Stream of matching Providers.
     */
    static <T> Stream<RandomGeneratorFactory<T>> all(Class<? extends RandomGenerator> category) {
        Map<String, Provider<? extends RandomGenerator>> pm = getProviderMap();
        return pm.values()
                 .stream()
                 .filter(p -> isSubclass(category, p))
                 .map(RandomGeneratorFactory::new);
    }

    /**
     * Returns a {@link RandomGenerator} that utilizes the {@code name} algorithm.
     *
     * @param name      Name of random number algorithm to use
     * @param category  Sub-interface of {@link RandomGenerator} to type check
     * @param <T>       Sub-interface of {@link RandomGenerator} to produce
     *
     * @return An instance of {@link RandomGenerator}
     *
     * @throws IllegalArgumentException when either the name or category is null
     */
    static <T> T of(String name, Class<? extends RandomGenerator> category)
            throws IllegalArgumentException {
        @SuppressWarnings("unchecked")
        T uncheckedRandomGenerator = (T)findProvider(name, category).get();
        return uncheckedRandomGenerator;
    }

    /**
     * Returns a {@link RandomGeneratorFactory} that will produce instances
     * of {@link RandomGenerator} that utilizes the {@code name} algorithm.
     *
     * @param name  Name of random number algorithm to use
     * @param category Sub-interface of {@link RandomGenerator} to type check
     * @param <T> Sub-interface of {@link RandomGenerator} to produce
     *
     * @return Factory of {@link RandomGenerator}
     *
     * @throws IllegalArgumentException when either the name or category is null
     */
    static <T> RandomGeneratorFactory<T> factoryOf(String name, Class<? extends RandomGenerator> category)
            throws IllegalArgumentException {
        Provider<? extends RandomGenerator> uncheckedProvider = findProvider(name, category);
        return new RandomGeneratorFactory<>(uncheckedProvider);
    }

    /**
     * Fetch the required constructors for class of random number algorithm.
     *
     * @param randomGeneratorClass class of random number algorithm (provider)
     */
    @SuppressWarnings("unchecked")
    private synchronized void getConstructors(Class<? extends RandomGenerator> randomGeneratorClass) {
        if (ctor == null) {
            PrivilegedExceptionAction<Constructor<?>[]> ctorAction = randomGeneratorClass::getConstructors;
            try {
                Constructor<?>[] ctors = AccessController.doPrivileged(ctorAction);
                for (Constructor<?> ctorGeneric : ctors) {
                    Constructor<T> ctorSpecific = (Constructor<T>)ctorGeneric;
                    final Class<?>[] parameterTypes = ctorSpecific.getParameterTypes();

                    if (parameterTypes.length == 0) {
                        ctor = ctorSpecific;
                    } else if (parameterTypes.length == 1) {
                        Class<?> argType = parameterTypes[0];

                        if (argType == long.class) {
                            ctorLong = ctorSpecific;
                        } else if (argType == byte[].class) {
                            ctorBytes = ctorSpecific;
                        }
                    }
                }
            } catch (PrivilegedActionException ex) {
                // Do nothing
            }
        }
    }

    /**
     * Ensure all the required constructors are fetched.
     */
    private void ensureConstructors() {
        if (ctor == null) {
            getConstructors(provider.type());
        }
    }

    /**
     * Return the name of the algorithm used by the random number generator.
     *
     * @return Name of the algorithm.
     */
    public String name() {
        return provider.type().getSimpleName();
    }

    /**
     * Return the group name of the algorithm used by the random number generator.
     *
     * @return Group name of the algorithm.
     */
    public String group() {
        try {
           Field groupField = provider.type().getDeclaredField("GROUP");
           PrivilegedExceptionAction<String> getAction = () -> {
               groupField.setAccessible(true);
               return (String)groupField.get(null);
           };
           return AccessController.doPrivileged(getAction);
        } catch (SecurityException | NoSuchFieldException | PrivilegedActionException ex) {
            return "Legacy";
        }
    }

    /**
     * Returns number of bits used to maintain state of seed.
     *
     * @return number of bits used to maintain state of seed.
     */
    public int stateBits() {
        try {
            Field stateBitsField = provider.type().getDeclaredField("STATE_BITS");
            PrivilegedExceptionAction<Integer> getAction = () -> {
                stateBitsField.setAccessible(true);
                return (Integer)stateBitsField.get(null);
            };
            return AccessController.doPrivileged(getAction);
        } catch (SecurityException | NoSuchFieldException | PrivilegedActionException ex) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Returns the equidistribution of the algorithm.
     *
     * @return the equidistribution of the algorithm.
     */
    public int equidistribution() {
        try {
            Field equidistributionField = provider.type().getDeclaredField("EQUIDISTRIBUTION");
            PrivilegedExceptionAction<Integer> getAction = () -> {
                equidistributionField.setAccessible(true);
                return (Integer)equidistributionField.get(null);
            };
            return AccessController.doPrivileged(getAction);
        } catch (SecurityException | NoSuchFieldException | PrivilegedActionException ex) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Return the period of the algorithm used by the random number generator.
     *
     * @return BigInteger period.
     */
    public BigInteger period() {
        try {
            Field periodField = provider.type().getDeclaredField("PERIOD");
            PrivilegedExceptionAction<BigInteger> getAction = () -> {
                periodField.setAccessible(true);
                return (BigInteger)periodField.get(null);
            };
            return AccessController.doPrivileged(getAction);
        } catch (SecurityException | NoSuchFieldException | PrivilegedActionException ex) {
            return RandomGenerator.HUGE_PERIOD;
        }
    }

    /**
     * Return true if random generator is statistical.
     *
     * @return true if random generator is statistical.
     */
    public boolean isStatistical() {
        return !isSubclass(SecureRandom.class);
    }

    /**
     * Return true if random generator is stochastic.
     *
     * @return true if random generator is stochastic.
     */
    public boolean isStochastic() {
        return isSubclass(SecureRandom.class);
    }

    /**
     * Return true if random generator is generated by hardware.
     *
     * @return true if random generator is generated by hardware.
     */
    public boolean isHardware() {
        return false;
    }

    /**
     * Return true if random generator is arbitrarily jumpable.
     *
     * @return true if random generator is arbitrarily jumpable.
     */
    public boolean isArbitrarilyJumpable() {
        return isSubclass(ArbitrarilyJumpableGenerator.class);
    }

    /**
     * Return true if random generator is jumpable.
     *
     * @return true if random generator is jumpable.
     */
    public boolean isJumpable() {
        return isSubclass(JumpableGenerator.class);
    }

    /**
     * Return true if random generator is leapable.
     *
     * @return true if random generator is leapable.
     */
    public boolean isLeapable() {
        return isSubclass(LeapableGenerator.class);
    }

    /**
     * Return true if random generator is splittable.
     *
     * @return true if random generator is splittable.
     */
    public boolean isSplittable() {
        return isSubclass(SplittableGenerator.class);
    }

    /**
     * Return true if random generator is streamable.
     *
     * @return true if random generator is streamable.
     */
    public boolean isStreamable() {
        return isSubclass(StreamableGenerator.class);
    }

    /**
     * Create an instance of {@link RandomGenerator} based on algorithm chosen.
     *
     * @return new in instance of {@link RandomGenerator}.
     *
     */
    public T create() {
        try {
            ensureConstructors();
            return ctor.newInstance();
        } catch (Exception ex) {
            // Should never happen.
            throw new IllegalStateException("Random algorithm is missing a default constructor");
        }
    }

    /**
     * Create an instance of {@link RandomGenerator} based on algorithm chosen
     * providing a starting long seed. If long seed is not supported by an
     * algorithm then the no argument form of create is used.
     *
     * @param seed long random seed value.
     *
     * @return new in instance of {@link RandomGenerator}.
     */
    public T create(long seed) {
        try {
            ensureConstructors();
            return ctorLong.newInstance(seed);
        } catch (Exception ex) {
            return create();
        }
    }

    /**
     * Create an instance of {@link RandomGenerator} based on algorithm chosen
     * providing a starting byte[] seed. If byte[] seed is not supported by an
     * algorithm then the no argument form of create is used.
     *
     * @param seed byte array random seed value.
     *
     * @return new in instance of {@link RandomGenerator}.
     */
    public T create(byte[] seed) {
        try {
            ensureConstructors();
            return ctorBytes.newInstance((Object)seed);
        } catch (Exception ex) {
            return create();
        }
    }

}


