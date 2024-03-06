/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import jdk.internal.lang.monotonic.InternalMonotonic;
import jdk.internal.lang.monotonic.InternalMonotonicList;
import jdk.internal.lang.monotonic.InternalMonotonicMap;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A <em>monotonic value</em> that can be bound at most once.
 * <p>
 * The state of a monotonic value can only go from absent to present and consequently, a
 * value can only be bound at most once.
 *
 * @implSpec Implementations of this interface are immutable and thread-safe.
 *
 * @param <V> value type
 * @since 23
 */
// Todo: If the supplier/mapper returns null, no value is recorded.
// Todo: Update fields.cpp to trust Monotonic, Monotonic.List, Monotonic.Map
//       This is an advantage with having separate types
// Todo: Remove ifPresent as this can be replaced with get(...) != null
public sealed interface Monotonic<V> permits InternalMonotonic {

    /**
     * {@return the monotonic value or throws an exception if no value is present}
     *
     * @throws NoSuchElementException if no value is present
     */
    V get();

    /**
     * {@return {@code true} if, and only if, a value is present}
     */
    boolean isPresent();

    /**
     * Binds the monotonic value to the provided {@code value}.
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already present
     * @throws NullPointerException  if the backing type is a primitive type and a
     *                               {@code value} of {@code null} is provided.
     */
    void put(V value);

    /**
     * Binds the monotonic value to the provided {@code value}} if a value is not present,
     * returning the present value.
     * <p>
     * If several threads invoke this method simultaneously, only one thread will succeed
     * in binding a value and that (witness) value will be returned to all threads.
     *
     * @param value to bind
     * @return the bound value
     * @throws NullPointerException if the backing type is a primitive type and a
     *                              {@code value} of {@code null} is provided.
     */
    V putIfAbsent(V value);

    /**
     * If a value is {@linkplain #isPresent() not present}, attempts to compute and bind a
     * value using the provided {@code supplier}.
     *
     * <p>
     * If the supplier returns {@code null}, no value is bound.
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * value is bound. The most common usage is to construct a new object serving as an
     * initial mapped value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = monotonic.computeIfAbsent(Value::new);
     * }</pre>
     *
     * @implSpec The implementation logic is equivalent to the following steps for this
     * {@code monotonic}:
     *
     * <pre> {@code
     * if (!monotonic.isPresent()) {
     *     V newValue = supplier.get();
     *     monotonic.put(newValue);
     *     return newValue;
     * } else {
     *     return monotonic.get();
     * }
     * }</pre>
     * Except it is thread-safe and will only return the same witness value regardless if
     * invoked by several threads.
     *
     * <p>The implementation guarantees the provided {@code supplier} is invoked
     * once (if successful) even if invoked from several threads.
     *
     * @param supplier the supplier to compute a value
     * @return the current (existing or computed) present value
     */
    V computeIfAbsent(Supplier<? extends V> supplier);

//    /**
//     * If a value is {@linkplain #isPresent() not present}, attempts to compute and bind a
//     * value using the provided {@code supplier}.
//     *
//     * <p>
//     * If the supplier returns {@code null}, no value is bound.
//     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
//     * value is bound. The most common usage is to construct a new object serving as an
//     * initial mapped value or memoized result, as in:
//     *
//     * <pre> {@code
//     * Value witness = monotonic.computeIfAbsent(handle);
//     * }</pre>
//     *
//     * @implSpec The implementation logic is equivalent to the following steps for this
//     * {@code monotonic}:
//     *
//     * <pre> {@code
//     * if (!monotonic.isPresent()) {
//     *     V newValue = (V) (Object) supplier.invokeExact(monotonic);
//     *     monotonic.put(newValue);
//     *     return newValue;
//     * } else {
//     *     return monotonic.get();
//     * }
//     * }</pre>
//     * Except it is thread-safe and will only return the same witness value regardless if
//     * invoked by several threads.
//     *
//     * <p>The implementation guarantees the provided {@code supplier} is invoked
//     * once (if successful) even if invoked from several threads.
//     *
//     * @param supplier the supplier to compute a value
//     * @return the current (existing or computed) present value
//     */
//    V computeIfAbsent(MethodHandle supplier);

//    /**
//     * {@return a MethodHandle that can be used to {@linkplain #get() get} the bound value
//     * of this monotonic}
//     * <p>
//     * The returned getter's return type reflects directly on the backing type of this
//     * monotonic value. The getter can be used to obtain the present value without boxing
//     * but will otherwise behave as the {@linkplain #get()} method.
//     * <p>
//     * The returned getter will have a sole parameter of type {@linkplain Monotonic} which
//     * represents the "this" parameter.
//     *
//     * @see #get()
//     */
//    MethodHandle getter();

    /**
     * A <em>monotonic list</em> where elements can be bound at most once.
     * <p>
     * Only non-null elements are supported.
     * <p>
     * The state of a monotonic element can only go from absent to present and
     * consequently, an element can only be bound at most once.
     *
     * @implSpec Implementations of this interface are immutable and thread-safe.
     *
     * @param <V> the monotonic value type
     */
    sealed interface List<V>
            extends java.util.List<V>, RandomAccess
            permits InternalMonotonicList {

        /**
         * {@inheritDoc}
         *
         * @throws IndexOutOfBoundsException if the index is out of range
         *                                   ({@code index < 0 || index >= size()})
         * // Todo: return 'null" instead as the Map::get operator does?
         * @throws NoSuchElementException    if no value is present at the provided
         *                                   {@code index}
         */
        V get(int index);

        /**
         * {@return {@code true} if, and only if, a value is present at the provided
         * {@code index}}
         * @param index of the element to inspect
         */
        boolean isPresent(int index);

        /**
         * Binds the monotonic element to the provided {@code element} at the provided
         * {@code index}.
         *
         * @param index of the element to bind
         * @param element to bind
         * @throws ClassCastException        if the class of the specified element
         *                                   prevents it from being added to this list
         * @throws NullPointerException      if the specified element is null
         * @throws IllegalArgumentException  if some property of the specified element
         *                                   prevents it from being added to this list
         * @throws IndexOutOfBoundsException if the index is out of range
         *                                   ({@code index < 0 || index >= size()})
         * @throws IllegalStateException     if an element is already present
         */
        default void put(int index, V element) {
            set(index, element);
        }

        /**
         * {@inheritDoc}
         *
         * @throws ClassCastException        if the class of the specified element
         *                                   prevents it from being added to this list
         * @throws NullPointerException      if the specified element is null
         * @throws IllegalArgumentException  if some property of the specified element
         *                                   prevents it from being added to this list
         * @throws IndexOutOfBoundsException if the index is out of range
         *                                   ({@code index < 0 || index >= size()})
         * @throws IllegalStateException     if an element is already present
         */
        V set(int index, V element);

        /**
         * Binds the monotonic element to the provided {@code element}} at the provided
         * {@code index} if an element is absent, returning the present element.
         * <p>
         * If several threads invoke this method simultaneously, only one thread will
         * succeed in binding an element and that (witness) element will be returned to all
         * threads.
         *
         * @param index index of the element to bind/return
         * @param element to bind
         * @return the bound element
         * @throws NullPointerException if the backing type is a primitive type and a
         *                              {@code element} of {@code null} is provided.
         */
        V putIfAbsent(int index, V element);

        /**
         * If a value is {@linkplain #isPresent() not present} at the provided
         * {@code index}, attempts to compute and bind a value by applying the provided
         * {@code mapper}.
         *
         * <p>
         * If the mapper function returns {@code null}, no value is bound.
         * If the mapper throws an (unchecked) exception, the exception is rethrown, and
         * no value is bound. The most common usage is to construct a new object serving
         * as an initial mapped value or memoized result, as in:
         *
         * <pre> {@code
         * Value witness = monotonicList.computeIfAbsent(index, Value::new);
         * }</pre>
         *
         * @implSpec The implementation logic is equivalent to the following steps for
         * this {@code monotonic}:
         *
         * <pre> {@code
         * if (!monotonicList.isPresent(index)) {
         *     V newValue = supplier.get(index);
         *     monotonic.put(index, newValue);
         *     return newValue;
         * } else {
         *     return monotonic.get();
         * }
         * }</pre>
         * Except it is thread-safe and will only return the same witness value regardless
         * if invoked by several threads.
         *
         * <p>The implementation guarantees the provided {@code mapper} is invoked
         * once per index (if successful) even if invoked from several threads.
         *
         * @param index of the element to bind/get
         * @param mapper the mapper to compute a value
         * @return the current (existing or computed) present value
         */
        V computeIfAbsent(int index, IntFunction<? extends V> mapper);

//        /**
//         * If a value is {@linkplain #isPresent() not present} at the provided
//         * {@code index}, attempts to compute and bind a value by applying the provided
//         * {@code mapper}.
//         *
//         * <p>
//         * If the mapper function returns {@code null}, no value is bound.
//         * If the mapper throws an (unchecked) exception, the exception is rethrown, and
//         * no value is bound. The most common usage is to construct a new object serving
//         * as an initial mapped value or memoized result, as in:
//         *
//         * <pre> {@code
//         * Value witness = monotonicList.computeIfAbsent(index, handle);
//         * }</pre>
//         *
//         * @implSpec The implementation logic is equivalent to the following steps for
//         * this {@code monotonic}:
//         *
//         * <pre> {@code
//         * if (!monotonicList.isPresent(index)) {
//         *     V newValue = (V) (Object) supplier.invokeExact(monotonicList, index);
//         *     monotonic.put(index, newValue);
//         *     return newValue;
//         * } else {
//         *     return monotonic.get();
//         * }
//         * }</pre>
//         * Except it is thread-safe and will only return the same witness value regardless
//         * if invoked by several threads.
//         *
//         * <p>The implementation guarantees the provided {@code mapper} is invoked
//         * once per index (if successful) even if invoked from several threads.
//         *
//         * @param index of the element to bind/get
//         * @param mapper the mapper to compute a value
//         * @return the current (existing or computed) present value
//         *
//         */
//        V computeIfAbsent(int index, MethodHandle mapper);

//        /**
//         * {@return a MethodHandle that can be used to {@linkplain #get() get} the bound
//         * value of this monotonic}
//         * <p>
//         * The returned getter's return type reflects directly on the backing type of this
//         * monotonic liat. The getter can be used to obtain the present value without
//         * boxing but will otherwise behave as the {@linkplain #get(int)} method.
//         * <p>
//         * The returned getter will have a first parameter of type
//         * {@linkplain Monotonic.List} which represents the "this" parameter and
//         * a second parameter of type {@code int} which represents the index.
//         *
//         * @see #get(int)
//         */
//         MethodHandle getter();
    }

    /**
     * A <em>monotonic map</em> where values can be bound at most once.
     * <p>
     * Only non-null keys and values are supported and the keys are restricted to
     * the ones provided at construction via the factory
     * {@linkplain Monotonic#ofMap(Class, Collection)}
     * <p>
     * The state of a monotonic value can only go from absent to present and
     * consequently, a value can only be bound at most once.
     *
     * @implSpec Implementations of this interface are immutable and thread-safe.
     *
     * @param <K> the type of keys maintained by this map
     * @param <V> the type of monotonic values
     */
    sealed interface Map<K, V>
            extends java.util.Map<K, V> permits InternalMonotonicMap {

        /**
         * {@inheritDoc}
         *
         * @throws NullPointerException   if the specified key is null
         */
        @Override
        V get(Object key);

        /**
         * {@return {@code true} if, and only if, a value is present for the provided
         * {@code key}}
         *
         * @param key of the element to inspect
         */
        default boolean isPresent(K key) {
            return containsKey(key);
        }

        /**
         * {@inheritDoc}
         *
         * @throws ClassCastException       if the class of the specified value
         *                                  prevents it from being added to this map
         * @throws NullPointerException     if the specified key or value is null
         * @throws IllegalArgumentException if some property of the specified value
         *                                  prevents it from being added to this map
         * @throws IllegalArgumentException if the key is not in the pre-set collection
         *                                  of keys specified at the time this map was
         *                                  created
         * @throws IllegalStateException    if a value is already present
         *
         */
        @Override
        V put(K key, V value);

        /**
         * {@inheritDoc}
         *
         * @throws ClassCastException       if the class of the specified value
         *                                  prevents it from being added to this map
         * @throws NullPointerException     if the specified key or value is null
         * @throws IllegalArgumentException if the key is not in the pre-set collection
         *                                  of keys specified at the time this map was
         *                                  created
         * @throws IllegalStateException    if a value is already present
         */
        @Override
        V putIfAbsent(K key, V value);

        /**
         * {@inheritDoc}
         *
         * @throws ClassCastException       if the class of the specified value
         *                                  prevents it from being added to this map
         * @throws NullPointerException     if the specified key or value is null
         * @throws IllegalArgumentException if the key is not in the pre-set collection
         *                                  of keys specified at the time this map was
         *                                  created
         */
        @Override
        V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

//        /**
//         * If a value is {@linkplain #isPresent() not present} for the provided
//         * {@code key}, attempts to compute and bind a value by applying the provided
//         * {@code mapper}.
//         *
//         * <p>
//         * If the mapper function returns {@code null}, no value is bound.
//         * If the mapper throws an (unchecked) exception, the exception is rethrown, and
//         * no value is bound. The most common usage is to construct a new object serving
//         * as an initial mapped value or memoized result, as in:
//         *
//         * <pre> {@code
//         * Value witness = monotonicMap.computeIfAbsent(key, handle);
//         * }</pre>
//         *
//         * @implSpec The implementation logic is equivalent to the following steps for
//         * this {@code monotonic}:
//         *
//         * <pre> {@code
//         * if (!monotonicMap.isPresent(key)) {
//         *     V newValue = (V) (Object) supplier.invokeExact(monotonicMap, index);
//         *     monotonic.put(index, newValue);
//         *     return newValue;
//         * } else {
//         *     return monotonic.get();
//         * }
//         * }</pre>
//         * Except it is thread-safe and will only return the same witness value regardless
//         * if invoked by several threads.
//         *
//         * <p>The implementation guarantees the provided {@code mapper} is invoked
//         * once per index (if successful) even if invoked from several threads.
//         *
//         * @param key of the value to bind/get
//         * @param mapper the mapper to compute a value
//         * @return the current (existing or computed) present value
//         *
//         */
//        V computeIfAbsent(K key, MethodHandle mapper);

//        /**
//         * {@return a MethodHandle that can be used to {@linkplain #get() get} the bound
//         * value of this monotonic}
//         * <p>
//         * The returned getter's return type reflects directly on the backing type of this
//         * monotonic map. The getter can be used to obtain the present value without
//         * boxing but will otherwise behave as the {@linkplain #get(Object)} method.
//         * <p>
//         * The returned getter will have a first parameter of type
//         * {@linkplain Monotonic.Map} which represents the "this" parameter and
//         * a second parameter of type {@code K} which represents the key.
//         *
//         * @see #get(Object)
//         */
//         MethodHandle getter();
    }

    // Factories

    /**
     * {@return a new Monotonic for which a bound non-null value can be backed by the
     * provided {@code backingType}}
     * <p>
     * It is up to to the implementation if the provided {@code backingType} is actually
     * used.
     *
     * @param backingType a class literal that (optionally) can be used to store a bound
     *                    value
     * @param <V>         the type to bind
     * @param <T>         the type of the backing class
     */
    static <T extends V, V> Monotonic<V> of(Class<T> backingType) {
        Objects.requireNonNull(backingType);
        return InternalMonotonic.of(backingType);
    }

//    /**
//     * {@return a new Monotonic for which a bound nullable value where non-null values can
//     * be backed by the provided {@code backingType}}
//     * <p>
//     * It is up to to the implementation if the provided {@code backingType} is actually
//     * used.
//     *
//     * @param backingType a class literal that (optionally) can be used to store a bound
//     *                    non-null value
//     * @param <V>         the type to bind
//     * @param <T>         the type of the backing class
//     */
//    // Todo: Valhalla makes this redundant?
//    static <T extends V, V> Monotonic<V> ofNullable(Class<T> backingType) {
//        Objects.requireNonNull(backingType);
//        return InternalMonotonic.ofNullable(backingType);
//    }

    /**
     * {@return a new monotonic, lazy {@linkplain List } where the element can be backed
     * by the provided {@code backingElementType} and with a
     * {@linkplain java.util.List#size()} equal or less than the provided {@code size}}
     *
     * @param backingElementType a class literal that (optionally) can be used to store a
     *                           bound monotonic value
     * @param size               the maximum size of the returned monotonic list
     * @param <V>                the type of the monotonic values in the returned list
     */
    static <V> Monotonic.List<V> ofList(Class<? extends V> backingElementType,
                                        int size) {
        Objects.requireNonNull(backingElementType);
        return InternalMonotonic.ofList(backingElementType, size);
    }

    /**
     * {@return a new monotonic, lazy {@linkplain Map } where the values can be backed by
     * the provided {@code backingValueType} and where the
     * {@linkplain java.util.Map#keySet() keys} can only contain the provided
     * {@code keys}}
     *
     * @param backingValueType a class literal that (optionally) can be used to store a
     *                         bound monotonic value
     * @param keys             the potential keys in the map
     * @param <K>              the type of keys maintained by the returned map
     * @param <V>              the type of the values in the returned map
     */
    static <K, V> Monotonic.Map<K, V> ofMap(Class<? extends V> backingValueType,
                                            Collection<? extends K> keys) {
        Objects.requireNonNull(keys);
        return InternalMonotonic.ofMap(backingValueType, keys);
    }

}
