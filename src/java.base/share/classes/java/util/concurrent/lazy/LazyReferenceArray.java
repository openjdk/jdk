/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.vm.annotation.Stable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An object reference array in which the values are lazily and atomically computed.
 * <p>
 * At most one invocation is made of any provided set of mapper per slot.
 * <p>
 * This contrasts to {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain a slot value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <V> The type of the values to be recorded
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public final class LazyReferenceArray<V> implements IntFunction<V> {

    private static int hi = 0;

    private final IntFunction<? extends V> presetMapper;

    @Stable
    private final LazyReference<V>[] lazyReferences;

    private final LazyReference<ListView> listView = Lazy.of(() -> new ListView(null));

    // Todo: use an array of V and a bit-set (3 bits per element or perhaps an entire int)
    // Todo: Bit CAS granularity. Perhaps int[] or several arrays (@Stable and non-@Stable)

    @SuppressWarnings("unchecked")
    LazyReferenceArray(int size,
                       IntFunction<? extends V> presetMapper) {
        lazyReferences = IntStream.range(0, size)
                .mapToObj(i -> Lazy.<V>of(toSupplier(i, presetMapper)))
                .toArray(LazyReference[]::new);
        this.presetMapper = presetMapper;
    }

    @SuppressWarnings("unchecked")
    LazyReferenceArray(int size) {
        lazyReferences = IntStream.range(0, size)
                .mapToObj(i -> Lazy.<V>ofEmpty())
                .toArray(LazyReference[]::new);
        this.presetMapper = null;
    }

    /**
     * {@return the length of the array}.
     */
    public int length() {
        return lazyReferences.length;
    }

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>pre-set {@linkplain Lazy#ofArray(int, IntFunction) mapper}</em>.
     * If no pre-set {@linkplain Lazy#ofArray(int, IntFunction) mapper} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set mapper itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<V> lazy = Lazy.ofArray(64, Value::new);
     *    // ...
     *    V value = lazy.apply(42);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index to the slot to be used
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     * @throws IllegalStateException          if a value was not already present and no
     *                                        pre-set mapper was specified.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    @Override
    public V apply(int index) {
        return lazyReferences[index]
                .get();
    }

    /**
     * {@return if a value is present at the provided {@code index}}.
     * <p>
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.isPresent(index)) {
     *         V value = lazy.get(index);
     *         // perform action on the value
     *     }
     *}
     *
     * @param index to the slot to be used
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     */
    public Lazy.State state(int index) {
        return lazyReferences[index]
                .state();
    }

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>provided {@code mappper}</em>.
     *
     * <p>If the mapper returns {@code null}, an exception is thrown.
     * If the provided {@code ,mapper} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReferenceArray.ofEmpty();
     *    // ...
     *    V value = lazy.supplyIfAbsent(42, Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index   to the slot to be used
     * @param mappper to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     * @throws NullPointerException           if the provided {@code mappper} is {@code null}.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    public V computeIfEmpty(int index,
                            IntFunction<? extends V> mappper) {
        Objects.requireNonNull(mappper);
        return lazyReferences[index]
                .supplyIfEmpty(
                        toSupplier(index, mappper));
    }

    /**
     * {@return the excption thrown by the mapper invoked at the provided
     * {@code index} or {@link Optional#empty()} if no exception was thrown}.
     *
     * @param index to the slot to be accessed
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     */
    public Optional<Throwable> exception(int index) {
        return lazyReferences[index]
                .exception();
    }

    /**
     * Returns an unmodifiable view of the elements in this LazyReferenceArray
     * where the empty elements will be replaced with {@code null}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @return a view of the elements
     */
    public List<V> asList() {
        return listView.get();
    }

    /**
     * Returns an unmodifiable view of the elements in this LazyReferenceArray
     * where the empty elements will be replaced with the provided {@code defaulValue}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @param defaulValue to use for elements not yet created
     * @return a view of the elements
     */
    public List<V> asList(V defaulValue) {
        return new ListView(defaulValue);
    }

    /**
     * {@return A Stream with the lazy elements in this LazyReferenceArray}.
     * <p>
     * Upon encountering a state at position {@code index} in the array, the following actions
     * will be taken:
     * <ul>
     *     <li><b>EMPTY</b>
     *     <p>An Optional.empty() element is selected.</p></li>
     *     <li><b>CONSTRUCTING</b>
     *     <p>An Optional.empty() element is selected.</p></li>
     *     <li><b>PRESENT</b>
     *     <p>An Optional.ofNullable(lazy.get(index)) element is selected.</p></li>
     *     <li><b>ERROR</b>
     *     <p>A NoSuchElementException is thrown.</p></li>
     * </ul>
     *
     * @throws NoSuchElementException if a slot is in state ERROR and is being accessed.
     */
    public Stream<Optional<V>> stream() {
        return IntStream.range(0, length())
                .mapToObj(i -> {
                    var lazy = lazyReferences[i];
                    return switch (lazy.state()) {
                        case EMPTY, CONSTRUCTING -> Optional.empty();
                        case PRESENT -> Optional.ofNullable(lazy.get());
                        case ERROR -> throw new NoSuchElementException("At index: " + i);
                    };
                });
    }

    /**
     * {@return A Stream with the lazy elements in this LazyReferenceArray}.
     * <p>
     * Upon encountering a state at position {@code index} in the array, the following actions
     * will be taken:
     * <ul>
     *     <li><b>EMPTY</b>
     *     <p>The provided {@code defaultValue} is selected.</p></li>
     *     <li><b>CONSTRUCTING</b>
     *     <p>The provided {@code defaultValue} is selected.</p></li>
     *     <li><b>PRESENT</b>
     *     <p>lazy.get(index)) is selected.</p></li>
     *     <li><b>ERROR</b>
     *     <p>A NoSuchElementException is thrown.</p></li>
     * </ul>
     *
     * @param defaultValue the default value to use for empty/contructing slots.
     * @throws NoSuchElementException if a slot is in state ERROR and is being accessed.
     */
    public Stream<V> stream(V defaultValue) {
        return IntStream.range(0, length())
                .mapToObj(i -> {
                    var lazy = lazyReferences[i];
                    return switch (lazy.state()) {
                        case EMPTY, CONSTRUCTING -> defaultValue;
                        case PRESENT -> lazy.get();
                        case ERROR -> throw new NoSuchElementException("At index: " + i);
                    };
                });
    }

    /**
     * Forces computation of all {@link java.util.concurrent.lazy.Lazy.State#EMPTY} slots in
     * slot order.
     * <p>
     * If the pre-set mapper throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. This means, subsequent slots
     * are not computed.
     *
     * @throws IllegalStateException if no pre-set mapper was specified.
     */
    public void force() {
        if (presetMapper == null) {
            throw new IllegalStateException();
        }
        for (LazyReference<V> lazy : lazyReferences) {
            lazy.get();
        }
    }

    /**
     * Returns the lazy value associated with the provided {@code key} via the provided
     * {@code intKeyMapper}, or, if the key is not {@linkplain IntKeyMapper#isMappable(int) mappable}, applies
     * the provided {@code unmappableHandler} using the provided {@code key}.
     * <p>
     * If the underlying lazy pre-set mapper throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. If the provided {@code unmappableHandler}
     * throws an (unchecked) exception, the exeption is rethrown.
     * The most common usage is to construct a new object serving as a cached result, as in:
     * <p>
     * {@snippet lang = java:
     *    private static IntKeyMapper KEY_MAPPER = IntKeyMapper.ofConstant(8);
     *    LazyReferenceArray<Long> cache = LazyReferenceArray.of(8);
     *    // ...
     *
     *    Long value = cache.mapAndApply(KEY_MAPPER, 16, n -> (1L << n), n -> 0);
     *    assertEquals(65536, value); // Value is mappable and entered into and taken from the cache
     *
     *    Long value2 = cache.mapAndApply(KEY_MAPPER, 15, n -> (1L << n), n -> 0);
     *    assertEquals(0, value2); // Value is not mappable and will be obtained from the provided lambda
     *}
     * <p>
     * If another thread attempts to compute a lazy value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param intKeyMapper      to use when mapping a key
     * @param key               to map to an index
     * @param mappableHandler   to apply if the key {@linkplain IntKeyMapper#isMappable(int) is mappable}
     * @param unmappableHandler to apply if the key {@linkplain IntKeyMapper#isMappable(int) is NOT mappable}
     * @return a lazy value (pre-existing or newly computed) or another value from the provided {@code unmappableHandler}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set mapper was specified.
     * @throws NoSuchElementException if a lazy maper has previously thrown an exception for the
     *                                provided key mapping to an associated {@code index}.
     */
    public V mapIntAndApply(IntKeyMapper intKeyMapper,
                            int key,
                            IntFunction<? extends V> mappableHandler,
                            IntFunction<? extends V> unmappableHandler) {
        int index = intKeyMapper.keyToIndex(key);
        return (index >= 0 && index < lazyReferences.length)
                ? lazyReferences[index]
                .supplyIfEmpty(() -> mappableHandler.apply(key))
                : unmappableHandler.apply(key);
    }

    /**
     * Returns the lazy value associated with the provided {@code key} via the provided
     * {@code keyMapper}, or, if the key is not {@linkplain IntKeyMapper#isMappable(int) mappable}, applies
     * the provided {@code unmappableHandler} using the provided {@code key}.
     * <p>
     * If the underlying lazy pre-set mapper throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. If the provided {@code unmappableHandler}
     * throws an (unchecked) exception, the exeption is rethrown.
     * The most common usage is to construct a new object serving as a cached result, as in:
     * <p>
     * {@snippet lang = java:
     *    private static KeyMapper<String> KEY_MAPPER = KeyMapper.ofHashing("A", "B", "C");
     *    LazyReferenceArray<Long> cache = LazyReferenceArray.of(KEY_MAPPER.requiredLength());
     *    // ...
     *
     *    String value = cache.mapAndApply(KEY_MAPPER, "B", (String s) -> s.repeat(10), (String s) -> s.repeat(10));
     *    assertEquals("BBBBBBBBBB", value); // Value is mappable and entered into and taken from the cache
     *
     *    Long value2 = cache.mapAndApply(KEY_MAPPER, "Z", (String s) -> s.repeat(10), (String s) -> "");
     *    assertEquals("", value2); // Value is not mappable and will be obtained from the provided lambda
     *}
     * <p>
     * If another thread attempts to compute a lazy value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param <K>               key type
     * @param keyMapper         to use when mapping a key
     * @param key               to map to an index
     * @param mappableHandler   to apply if the key {@linkplain IntKeyMapper#isMappable(int) is mappable}
     * @param unmappableHandler to apply if the key {@linkplain IntKeyMapper#isMappable(int) is NOT mappable}
     * @return a lazy value (pre-existing or newly computed) or another value from the provided {@code unmappableHandler}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set mapper was specified.
     * @throws NoSuchElementException if a lazy maper has previously thrown an exception for the
     *                                provided key mapping to an associated {@code index}.
     */
    public <K> V mapAndApply(KeyMapper<? super K> keyMapper,
                             K key,
                             Function<? super K, ? extends V> mappableHandler,
                             Function<? super K, ? extends V> unmappableHandler) {
        int index = keyMapper.keyToIndex(key);
        return (index >= 0 && index < lazyReferences.length)
                ? lazyReferences[index]
                .supplyIfEmpty(() -> mappableHandler.apply(key))
                : unmappableHandler.apply(key);
    }

    @Override
    public String toString() {
        return IntStream.range(0, length())
                .mapToObj(i -> lazyReferences[i])
                .map(lazy -> switch (lazy.state()) {
                    case EMPTY -> "-";
                    case CONSTRUCTING -> "+";
                    case PRESENT -> Objects.toString(lazy.get());
                    case ERROR -> "!";
                })
                .collect(Collectors.joining(", ", "LazyReferenceArray[", "]"));
    }

    // Todo: Add supplyIfEmpty()?

    Supplier<V> toSupplier(int index,
                           IntFunction<? extends V> mappper) {
        return () -> mappper.apply(index);
    }

    V getOr(int index, V defaultValue) {
        var lazy = lazyReferences[index];
        var state = lazy.state();
        return switch (state) {
            case EMPTY, CONSTRUCTING -> defaultValue;
            case PRESENT -> lazy.get();
            case ERROR -> throw new NoSuchElementException();
        };
    }

    /**
     * A key mapper than can convert between external "keys" and internal indices. The mapper
     * is said to perform <em>inversly replicable conversions</em> meaning for a KeyMapper
     * {@code km} used by a LazyReferenceArray {@code lra}:
     * <ul>
     *     <li>
     *     The following holds: {@code km.isApplicable(lra.length))}.
     *     </li>
     *     <li>
     *      For any external value {@code key} for which the mapper {@linkplain IntKeyMapper#isMappable(int)} (int) is convertible},
     *       the following holds: {@code
     *           fromIndex(toIndex(key)) = key
     *       }
     *     </li>
     *     <li>
     *      For any external value {@code key} for which the mapper {@linkplain IntKeyMapper#isMappable(int) is NOT convertibla},
     *      the following will throw an ArrayOutOfBounds: {@code
     *           lra.get(toindex(e));
     *       }
     *     </li>
     * </ul>
     * <p>
     * Hence, the mapper is not guaranteed to always produce valid mappings but is guaranteed to provide
     * consistent results when applied to a LazyReferenceArray.
     * <p>
     * The mapper is useful when using a LazyReferenceArray as a cache in cases there is not
     * a one-to-one mapping between the keys used for caching and the actual indices in the
     * arrary.
     *
     * @see LazyReferenceArray#mapIntAndApply(IntKeyMapper, int, IntFunction, IntFunction)
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY)
    public interface IntKeyMapper {
        /**
         * {@return an index of the LazyReferenceArray by converting the
         * provided {@code key} or, an invalid index
         * if no such conversion can be made}.
         *
         * @param key to convert to an index
         */
        int keyToIndex(int key);

        /**
         * {@return if the provided {@code key} can be
         * mapped to an index}.
         *
         * @param key to test
         */
        boolean isMappable(int key);

        /**
         * {@return the required array length, or if no such array length exists
         * {@link OptionalInt#empty()}}.
         */
        OptionalInt requiredLength();

        /**
         * {@return if this KeyMapper can be used on an array with the
         * provided {@code arrayLength}}.
         *
         * @param arrayLength to test
         */
        boolean checkArrayLength(int arrayLength);

        /**
         * {@return an index mapper that will map external values to
         * indices by dividing with the provided {@code constant}}.
         *
         * @param constant used as a divisor when converting external values to indices.
         * @throws IllegalArgumentException if the provided {@code constant} is zero.
         */
        public static IntKeyMapper ofConstant(int constant) {
            if (constant == 0) {
                throw new IllegalArgumentException("The constant must be non-zero");
            }
            return new IntKeyMapper() {
                @Override
                public int keyToIndex(int key) {
                    if (!isMappable(key)) {
                        return -1;
                    }
                    return key / constant;
                }

                @Override
                public boolean isMappable(int key) {
                    return key % constant == 0;
                }

                @Override
                public boolean checkArrayLength(int arrayLength) {
                    // Todo: Use the maximum array size which is smaller than Integer.MAX_VALUE
                    return (long) arrayLength * (long) constant < Integer.MAX_VALUE;
                }

                @Override
                public OptionalInt requiredLength() {
                    return OptionalInt.empty();
                }

                @Override
                public String toString() {
                    return "IndexMapper.ofConstant(" + constant + ")";
                }
            };
        }


        /**
         * {@return a index mapper that is a one-to-one mapper where the
         * external values will be the same as the internal indices}.
         */
        // Todo: remove this
        public static IntKeyMapper ofIdentity() {
            return new IntKeyMapper() {
                @Override
                public int keyToIndex(int key) {
                    return key;
                }

                @Override
                public boolean isMappable(int key) {
                    return true;
                }

                @Override
                public boolean checkArrayLength(int arrayLength) {
                    return true;
                }

                @Override
                public OptionalInt requiredLength() {
                    return OptionalInt.empty();
                }
            };
        }
    }

    /**
     * A key mapper than can convert between external "keys" and internal indices. The mapper
     * is said to perform <em>inversly replicable conversions</em> meaning for a KeyMapper
     * {@code km} used by a LazyReferenceArray {@code lra}:
     * <ul>
     *     <li>
     *     The following holds: {@code km.isApplicable(lra.length))}.
     *     </li>
     *     <li>
     *      For any external value {@code key} for which the mapper {@linkplain KeyMapper#isMappable(Object)} is convertible},
     *       the following holds: {@code
     *           fromIndex(toIndex(key)) = key
     *       }
     *     </li>
     *     <li>
     *      For any external value {@code key} for which the mapper {@linkplain KeyMapper#isMappable(Object) is NOT convertibla},
     *      the following will throw an ArrayOutOfBounds: {@code
     *           lra.get(toindex(e));
     *       }
     *     </li>
     * </ul>
     * <p>
     * Hence, the mapper is not guaranteed to always produce valid mappings but is guaranteed to provide
     * consistent results when applied to a LazyReferenceArray.
     * <p>
     * The mapper is useful when using a LazyReferenceArray as a cache in cases there is not
     * a one-to-one mapping between the keys used for caching and the actual indices in the
     * arrary.
     *
     * @param <K> key type
     * @see LazyReferenceArray#mapIntAndApply(IntKeyMapper, int, IntFunction, IntFunction)
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY)
    public interface KeyMapper<K> {

        /**
         * {@return an index of the LazyReferenceArray by converting the
         * provided {@code key} or, an invalid index
         * if no such conversion can be made}.
         *
         * @param key to convert to an index
         */
        int keyToIndex(K key);

        /**
         * {@return if the provided {@code key} can be
         * mapped to an index}.
         *
         * @param key to test
         */
        boolean isMappable(K key);

        /**
         * {@return the required array length, or if no such array length exists,
         * {@link OptionalInt#empty()}}.
         */
        OptionalInt requiredLength();

        /**
         * {@return if this KeyMapper can be used on an array with the
         * provided {@code arrayLength}}.
         *
         * @param arrayLength to test
         */
        boolean checkArrayLength(int arrayLength);

        // Redundant but convenient...

        /**
         * {@return a KeyMapper using a non-colliding hash algoritm}.
         *
         * @param objects to use as keys
         * @param <T>     key types
         */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public static <T> KeyMapper<T> ofHashing(T... objects) {
            Objects.requireNonNull(objects);
            return of(polynomialMapper(objects));
        }

        /**
         * {@return a KeyMapper that uses the provided {@code mapper}}.
         *
         * @param mapper to use
         * @param <T>    type of key elements.
         */
        public static <T> KeyMapper<T> of(PolynomialMapperConfig<T> mapper) {
            return new KeyMapper<T>() {
                @Override
                public int keyToIndex(T key) {
                    T[] keys = mapper.keys();
                    int index = bucket(polynomialHash(mapper.polynom(), key.hashCode()), keys.length);
                    if (Objects.equals(key, keys[index])) {
                        return index;
                    }
                    int nextIndex = KeyMapper.bucket(index, keys.length);
                    if (Objects.equals(keys, keys[nextIndex])) {
                        return nextIndex;
                    }
                    return -1;
                }

                // Todo: Fix this. We do not want to comput the hash two times
                @Override
                public boolean isMappable(T key) {
                    int index = keyToIndex(key);
                    return (index >= 0 && index < mapper.keys().length);
                }

                @Override
                public OptionalInt requiredLength() {
                    return OptionalInt.of(mapper.keys().length);
                }

                @Override
                public boolean checkArrayLength(int arrayLength) {
                    return arrayLength >= requiredLength().getAsInt();
                }
            };
        }

        /**
         * {@return a PolynomialMapper for the provided {@code keys}}.
         *
         * @param keys to use later on
         * @param <T>  type of the keys
         */
        @SafeVarargs
        @SuppressWarnings("varargs")
        public static <T> PolynomialMapperConfig<T> polynomialMapper(T... keys) {
            @SuppressWarnings("unchecked")
            T[] sortedKeys = (T[]) new Object[keys.length];
            BitSet bitSet = new BitSet(keys.length);
            int[] primes = IntStream.of(
                            2, 3, 5, 7, 13, 17, 21, 23, 31,
                            127, 257, 509, 1021, 2053, 4099)
                    // No use of primes that are an even multiple of keys.length
                    .filter(i -> i % keys.length != 0)
                    .toArray();

            for (int l = 2; l < 5; l++) {
                int[] polynom = new int[l];

                // Todo: Replace this with a stack or an array of iterators
                switch (l) {
                    // Case 1 does not provide any spreading
                    case 1: {
                        for (int i0 = 0; i0 < primes.length; i0++) {
                            polynom[0] = primes[i0];
                            PolynomialMapperConfig<T> m = tryPolynom(polynom, keys);
                            if (m != null) {
                                return m;
                            }
                        }
                    }
                    break;
                    case 2: {
                        for (int i0 = 0; i0 < primes.length; i0++) {
                            for (int i1 = 0; i1 < primes.length; i1++) {
                                polynom[0] = primes[i0];
                                polynom[1] = primes[i1];
                                PolynomialMapperConfig<T> m = tryPolynom(polynom, keys);
                                if (m != null) {
                                    return m;
                                }
                            }
                        }
                    }
                    break;
                    case 3: {
                        for (int i0 = 0; i0 < primes.length; i0++) {
                            for (int i1 = 0; i1 < primes.length; i1++) {
                                for (int i2 = 0; i2 < primes.length; i2++) {
                                    polynom[0] = primes[i0];
                                    polynom[1] = primes[i1];
                                    polynom[2] = primes[i2];
                                    PolynomialMapperConfig<T> m = tryPolynom(polynom, keys);
                                    if (m != null) {
                                        return m;
                                    }
                                }
                            }
                        }
                        break;
                    }
                    case 4: {
                        for (int i0 = 0; i0 < primes.length; i0++) {
                            for (int i1 = 0; i1 < primes.length; i1++) {
                                for (int i2 = 0; i2 < primes.length; i2++) {
                                    for (int i3 = 0; i3 < primes.length; i3++) {
                                        polynom[0] = primes[i0];
                                        polynom[1] = primes[i1];
                                        polynom[2] = primes[i2];
                                        polynom[3] = primes[i3];
                                        PolynomialMapperConfig<T> m = tryPolynom(polynom, keys);
                                        if (m != null) {
                                            return m;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            throw new NoSuchElementException("Unable: " + Arrays.toString(keys));
        }

        private static <T> PolynomialMapperConfig<T> tryPolynom(int[] polynom, T[] keys) {
            BitSet bitSet = new BitSet(keys.length);
            @SuppressWarnings("unchecked")
            T[] sortedKeys = (T[]) new Object[keys.length];
            for (int i = 0; i < keys.length; i++) {
                T key = keys[i];
                int index = bucket(polynomialHash(polynom, Objects.hash(key)), keys.length);
                if (bitSet.get(index)) {
                    // Try one position ahead
                    int nextIndex = bucket(index, keys.length);
                    bitSet.set(nextIndex);
                    sortedKeys[nextIndex] = key;
                } else {
                    bitSet.set(index);
                    sortedKeys[index] = key;
                }
            }
            System.out.println("Tried (" + bitSet.cardinality() + ") " + Arrays.toString(polynom) + " -> " + Arrays.toString(sortedKeys));
            if (bitSet.cardinality() > hi) {
                hi = bitSet.cardinality();
                System.out.println("hi=" + hi);
            }
            if (bitSet.cardinality() == keys.length) {
                System.out.println("Yehaa!");
                return new PolynomialMapperConfig<>(polynom, keys);
            }
            return null;
        }

        /**
         * A polynomial mapper with the provided {@code polynom} and {@code keys}}.
         *
         * @param polynom to apply for keys
         * @param keys    that are a member of the mapping
         * @param <T>     type of the keys
         */
        @SuppressWarnings("unchecked")
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY)
        public record PolynomialMapperConfig<T>(int[] polynom, T... keys) {
            /**
             * Constructor
             *
             * @param polynom to apply for keys
             * @param keys    that are a member of the mapping
             */
            public PolynomialMapperConfig {
                if (polynom.length <= 0) {
                    throw new IllegalArgumentException("polynom lenght must be positive: " + polynom.length);
                }
            }

            @Override
            public String toString() {
                return PolynomialMapperConfig.class.getSimpleName() + "={" +
                        "polynom=" + Arrays.toString(polynom) + ", " +
                        "keys=" + Arrays.toString(keys) + "}";
            }
        }

        private static int bucket(int hash, int length) {
            return (hash % length) & Integer.MAX_VALUE;
        }

        private static int polynomialHash(int[] polynom, int initialHash) {
            int x = Objects.hashCode(initialHash);
            int h = 0;

            // Todo: use the Vector API
            // 3X^2+X+3
            for (int i = 1; i < polynom.length; i++) {
                h += x * polynom[i];
                x *= x;
            }
            h += polynom[0];
            return h;
        }
    }

    private final class ListView implements List<V> {

        private final V defaultValue;
        private final int begin;
        private final int end;

        ListView(int begin,
                 int end,
                 V defaultValue) {
            if (begin < 0) {
                throw new IndexOutOfBoundsException("begin: " + begin);
            }
            if (end > LazyReferenceArray.this.length()) {
                throw new IndexOutOfBoundsException("end: " + begin);
            }
            this.begin = begin;
            this.end = end;
            this.defaultValue = defaultValue;
        }

        ListView(V defaultValue) {
            this(0, LazyReferenceArray.this.length(), defaultValue);
        }

        @Override
        public int size() {
            return end - begin;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean contains(Object o) {
            for (int i = begin; i < end; i++) {
                if (Objects.equals(0, getOr(i, defaultValue))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<V> iterator() {
            return new ListIteratorView(0, length(), null);
        }

        @Override
        public Object[] toArray() {
            return IntStream.range(0, size())
                    .mapToObj(i -> LazyReferenceArray.this.getOr(i, defaultValue))
                    .toArray();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T1> T1[] toArray(T1[] a) {
            if (a.length < size()) {
                return (T1[]) Arrays.copyOf(toArray(), size(), a.getClass());
            }
            System.arraycopy(toArray(), 0, a, 0, size());
            if (a.length > size())
                a[size()] = null;
            return a;
        }

        @Override
        public boolean add(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean remove(Object o) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object e : c)
                if (!contains(e))
                    return false;
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean addAll(int index, Collection<? extends V> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public void clear() {
            throw newUnsupportedOperation();
        }

        @Override
        public V get(int index) {
            return getOr(index, defaultValue);
        }

        @Override
        public V set(int index, V element) {
            throw newUnsupportedOperation();
        }

        @Override
        public void add(int index, V element) {
            throw newUnsupportedOperation();
        }

        @Override
        public V remove(int index) {
            throw newUnsupportedOperation();
        }

        @Override
        public int indexOf(Object o) {
            for (int i = 0; i < size(); i++) {
                if (Objects.equals(o, getOr(i, defaultValue))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = size() - 1; i >= 0; i--) {
                if (Objects.equals(o, getOr(i, defaultValue))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public ListIterator<V> listIterator() {
            return new ListIteratorView(defaultValue);
        }

        @Override
        public ListIterator<V> listIterator(int index) {
            return new ListIteratorView(index, length(), defaultValue);
        }

        @Override
        public List<V> subList(int fromIndex, int toIndex) {
            if (fromIndex < 0) {
                throw new IndexOutOfBoundsException("fromIndex: " + fromIndex);
            }
            if (toIndex > size()) {
                throw new IndexOutOfBoundsException("toIndex: " + toIndex);
            }
            if (fromIndex > toIndex) {
                throw new IndexOutOfBoundsException("fromIndex > toIndex: " + fromIndex + ", " + toIndex);
            }
            return new ListView(begin + fromIndex, begin + toIndex, defaultValue);
        }

        @Override
        public void sort(Comparator<? super V> c) {
            throw newUnsupportedOperation();
        }
    }

    final class ListIteratorView implements ListIterator<V> {

        private final V defaultValue;
        private final int begin;
        private final int end;
        private int cursor;

        private ListIteratorView(V defaultValue) {
            this(0, LazyReferenceArray.this.length(), defaultValue);
        }

        private ListIteratorView(int begin,
                                 int end,
                                 V defaultValue) {
            this.begin = begin;
            this.end = end;
            this.defaultValue = defaultValue;
            this.cursor = begin;
        }

        @Override
        public boolean hasNext() {
            return cursor < end;
        }

        @Override
        public boolean hasPrevious() {
            return cursor != begin;
        }

        @Override
        public V previous() {
            int i = cursor - 1;
            if (i < begin)
                throw new NoSuchElementException();
            cursor = i;
            return getOr(i, defaultValue);
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void set(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public void add(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public V next() {
            var i = cursor + 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            cursor = i;
            return getOr(i, defaultValue);
        }

        @Override
        public void remove() {
            throw newUnsupportedOperation();
        }

        @Override
        public void forEachRemaining(Consumer<? super V> action) {
            for (; cursor < end; cursor++) {
                action.accept(getOr(cursor, defaultValue));
            }
        }
    }

    private UnsupportedOperationException newUnsupportedOperation() {
        return new UnsupportedOperationException("Not supported on an unmodifiable list.");
    }

}
