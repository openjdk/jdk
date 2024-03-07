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
     * Binds the monotonic value to the provided {@code value} or throws an
     * exception if a value is already present.
     * .
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already present
     */
    void bind(V value);

    /**
     * Binds the monotonic value to the provided {@code value}} if a value is not present,
     * and returns the bound value.
     * <p>
     * If several threads invoke this method simultaneously, only one thread will succeed
     * in binding a value and that (witness) value will be returned to all threads.
     *
     * @param value to bind
     * @return the bound value
     */
    V bindIfAbsent(V value);

    /**
     * If a value is {@linkplain #isPresent() not present}, attempts to compute and bind a
     * value using the provided {@code supplier}.
     *
     * <p>
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
     * @param supplier to be used for computing a value
     * @return the current (existing or computed) present value
     */
    V computeIfAbsent(Supplier<? extends V> supplier);

    /**
     * A special {@code java.util.List<Monotonic<V>>} that is eligible for constant
     * folding by the JVM and that provides additional convenience methods.
     *
     * @implSpec Implementations of this interface are shallowly immutable and thread-safe.
     *
     * @param <B> the bound type for the Monotonic elements in this list
     */
    interface List<B> extends java.util.List<Monotonic<B>> {

        /**
         * If a monotonic value is {@linkplain #isPresent() not present} at the provided
         * {@code index}, attempts to compute and bind a value using the provided {@code mapper}.
         *
         * <p>
         * If the mapper throws an (unchecked) exception, the exception is rethrown, and no
         * value is bound.
         *
         * @param index to inspect
         * @param mapper to be used for computing a value
         *
         * @return the current (existing or computed) present monotonic value
         */
        B computeMonotonicIfAbsent(int index, IntFunction<? extends B> mapper);
    }

    /**
     * A special {@code java.util.Map<K, Monotonic<V>>} that is eligible for constant
     * folding by the JVM and that provides additional convenience methods.
     *
     * @implSpec Implementations of this interface are shallowly immutable and thread-safe.
     *
     * @param <K> the type of keys maintained by this map
     * @param <B> the bound type for the mapped Monotonic values in this map
     */
    interface Map<K, B> extends java.util.Map<K, Monotonic<B>> {

        /**
         * If a monotonic value is {@linkplain #isPresent() not present} for the provided
         * {@code key}, attempts to compute and bind a value using the provided {@code mapper}.
         *
         * <p>
         * If the mapper throws an (unchecked) exception, the exception is rethrown, and no
         * value is bound.
         *
         * @param key to inspect
         * @param mapper to be used for computing a value
         *
         * @return the current (existing or computed) present monotonic value
         * @throws IllegalArgumentException if no association exists for the provided
         *         {@code key}.
         */
        B computeMonotonicIfAbsent(K key, Function<? super K, ? extends B> mapper);
    }

    // Factories

    /**
     * {@return a new Monotonic}
     *
     * @param <V> the value type to bind
     */
    static <V> Monotonic<V> of() {
        return new InternalMonotonic<>();
    }

    /**
     * {@return a thread-safe, memoized supplier backed by a monotonic value where the
     * memoized value is obtained by invoking the provided {@code suppler}}
     *
     * @param supplier   to be used for computing a value
     * @param background if true, spawns a virtual background thread that per-computes
     *                   a memoized value
     * @param <V>        the type of the value to memoize
     * @see Monotonic#computeIfAbsent(Supplier)
     */
    static <V> Supplier<V> asMemoized(Supplier<? extends V> supplier,
                                      boolean background) {
        Objects.requireNonNull(supplier);
        Monotonic<V> monotonic = Monotonic.of();
        Supplier<V> result = () -> monotonic.computeIfAbsent(supplier);
        if (background) {
            Thread.startVirtualThread(result::get);
        }
        return result;
    }

    /**
     * {@return a new shallowly immutable, lazy {@linkplain List } of monotonic values
     * with a {@linkplain List#size()} equal to the provided {@code size}}
     *
     * @param size the size of the returned monotonic list
     * @param <B>  the bound type for the Monotonic elements in this list
     */
    static <B> Monotonic.List<B> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return InternalMonotonicList.of(size);
    }

    /**
     * {@return a new shallowly immutable, {@linkplain Map } where the
     * {@linkplain java.util.Map#keySet() keys} contains only the provided
     * {@code keys}}
     *
     * @param keys             the potential keys in the map
     * @param <K>              the type of keys maintained by the returned map
     * @param <V>              the type of the values in the returned map
     */
    static <K, V> Monotonic.Map<K, V> ofMap(Collection<? extends K> keys) {
        Objects.requireNonNull(keys);
        return InternalMonotonicMap.ofMap(keys);
    }

/*    static <V> V computeIfAbsent(List<Monotonic<V>> list,
                                 int index,
                                 IntFunction<? extends V> mapper) {
        Objects.requireNonNull(list);
        Objects.checkIndex(index, list.size());
        Objects.requireNonNull(mapper);
        Monotonic<V> monotonic = list.get(index);
        if (monotonic.isPresent()) {
            return monotonic.get();
        }
        synchronized (mapper) {
            if (monotonic.isPresent()) {
                return monotonic.get();
            }
            Supplier<V> supplier = () -> mapper.apply(index);
            return monotonic.computeIfAbsent(supplier);
        }
    }

    static <K, V> V computeIfAbsent(Map<K, Monotonic<V>> map,
                                    K key,
                                    Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(mapper);
        Monotonic<V> monotonic = Objects.requireNonNull(map.get(key), "No such key");
        if (monotonic.isPresent()) {
            return monotonic.get();
        }
        synchronized (mapper) {
            if (monotonic.isPresent()) {
                return monotonic.get();
            }
            Supplier<V> supplier = () -> mapper.apply(key);
            return monotonic.computeIfAbsent(supplier);
        }
    }*/

}
