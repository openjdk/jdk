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

import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A <em>monotonic value</em> that can be set at most once.
 * <p>
 * The value can only go from unbound to bound and consequently, a value can only be bound
 * at most once.
 *
 * @param <V> value type
 * @implSpec Implementations of this interface are immutable and thread-safe.
 * @since 23
 */
// Todo: MethodHandle for computeIfUnbound
// Todo: MethodHandle getter()
public sealed interface Monotonic<V> permits InternalMonotonic {

    /**
     * {@return the bound monotonic value}
     *
     * @throws NoSuchElementException if no value is bound
     */
    V get();

    /**
     * {@return true if, and only if, a value is bound}
     */
    boolean isBound();

    /**
     * Binds the monotonic value to the provided {@code value}}
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already bound
     * @throws NullPointerException  if the backing type is a primitive type and a
     *                               {@code value} of {@code null} is provided.
     */
    void bind(V value);

    /**
     * If a value is {@linkplain #isBound() not bound}, attempts to compute and bind a
     * value using the provided {@code supplier}.
     *
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * value is bound. The most common usage is to construct a new object serving as an
     * initial mapped value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = monotonic.computeIfUnbound(key, Value::new);
     * }</pre>
     *
     * @param supplier the supplier to compute a value
     * @return the current (existing or computed) bound value
     * @implSpec The implementation logic is equivalent to the following steps for this
     * {@code monotonic}:
     *
     * <pre> {@code
     * if (!monotonic.isBound()) {
     *     V newValue = supplier.get();
     *     monotonic.set(newValue);
     *     return newValue;
     * } else {
     *     return monotonic.get();
     * }
     * }</pre>
     * Except it is thread-safe and will only return the same witness value regardless if
     * invoked by several threads.
     *
     * <p>The implementation is guaranteed to be lock free but may invoke suppliers
     * from several threads. Hence, any given supplier may be invoked several times.
     */
    V computeIfUnbound(Supplier<? extends V> supplier);

    /**
     * {@return a MethodHandle that can be used to {@linkplain #get() get} the bound
     * value of this monotonic}
     * <p>
     * The returned getter's return type reflects directly on the backing type of this
     * monotonic value. The getter can be used to obtain the bound value without boxing
     * but will otherwise behave as the {@linkplain #get()} method.
     * <p>
     * The returned getter will have a sole parameter of type {@linkplain Monotonic} which
     * represents the "this" parameter.
     */
    MethodHandle getter();

    /**
     * {@return a new Supplier that will bind (memoize) the provided {@code supplier}'s
     * {@linkplain Supplier#get()} value to this monotonic}
     *
     * @param supplier for which it's get() result is to be memoized
     * @param <R>      the return type of the provided {@code supplier}
     */
    @SuppressWarnings("unchecked")
    default <R extends V> Supplier<R> asMemoized(Supplier<R> supplier) {
        Objects.requireNonNull(supplier);
        return () -> (R) computeIfUnbound(supplier);
    }

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
     */
    static <V> Monotonic<V> of(Class<V> backingType) {
        Objects.requireNonNull(backingType);
        return InternalMonotonic.of(backingType);
    }

    /**
     * {@return a new Monotonic for which a bound nullable value where non-null values can
     * be backed by the provided {@code backingType}}
     * <p>
     * It is up to to the implementation if the provided {@code backingType} is actually
     * used.
     *
     * @param backingType a class literal that (optionally) can be used to store a bound
     *                    non-null value
     * @param <V>         the type to bind
     */
    // Todo: Valhalla makes this redundant?
    static <V> Monotonic<V> ofNullable(Class<V> backingType) {
        Objects.requireNonNull(backingType);
        return InternalMonotonic.ofNullable(backingType);
    }

    /**
     * An immutable, lazy {@linkplain List} of monotonic values.
     *
     * @param <V> the monotonic value type
     */
    sealed interface MonotonicList<V>
            extends List<Monotonic<V>>, RandomAccess
            permits InternalMonotonicList {

        /**
         * {@return a cached {@linkplain IntFunction} that, for each index independently,
         * memoizes the result of calling the provided {@code mapper} with a particular
         * index}
         * <p>
         * The returned IntFunction will throw an {@linkplain IndexOutOfBoundsException}
         * if the returned IntFunction is invoked with an index that is less than zero or
         * greater or equal to the {@linkplain #size()} of this List. If the provided
         * {@code mapper} throws an exception, no value will be memoized and the exception
         * will be propagated to the caller.
         *
         * @param mapper to memoize
         * @param <R>    the type of value the provided {@code mapper} returns
         */
        @SuppressWarnings("unchecked")
        default <R extends V> IntFunction<R> asMemoized(IntFunction<R> mapper) {
            Objects.requireNonNull(mapper);
            return index -> (R) get(index)
                    .computeIfUnbound(() -> mapper.apply(index));
        }

    }

    /**
     * An immutable, lazy {@linkplain Map} with monotonic values.
     *
     * @param <K> the type of keys maintained by this map
     * @param <V> the type of monotonic values
     */
    interface MonotonicMap<K, V> extends Map<K, Monotonic<V>> {

        /**
         * {@return a cached {@linkplain Function} that, for each key independently,
         * memoizes the result of calling the provided {@code mapper} with a particular
         * key}
         * <p>
         * The returned Function will throw a {@linkplain NoSuchElementException} if the
         * returned Function is invoked with a key for which
         * {@linkplain #containsKey(Object)} is {@code false}. If the provided
         * {@code mapper} throws an exception, no value will be memoized and the exception
         * will be propagated to the caller.
         *
         * @param mapper to memoize
         * @param <R>    the type of value the provided {@code mapper} returns
         */
        @SuppressWarnings("unchecked")
        default <R extends V> Function<K, R> asMemoized(Function<? super K, R> mapper) {
            Objects.requireNonNull(mapper);
            return key -> {
                Monotonic<V> monotonic = get(key);
                if (monotonic == null) {
                    throw new NoSuchElementException(key.toString());
                }
                return (R) monotonic.computeIfUnbound(() -> mapper.apply(key));
            };
        }

    }

    /**
     * {@return a new immutable, lazy {@linkplain MonotonicList} where the monotonic
     * element's values can be backed by the provided {@code backingElementType} and with
     * a {@linkplain List#size()} equal to the provided {@code size}}
     *
     * @param backingElementType a class literal that (optionally) can be used to store a
     *                           bound monotonic value
     * @param size               the size of the returned monotonic list
     * @param <V>                the type of the monotonic values in the returned list
     */
    static <V> MonotonicList<V> ofList(Class<V> backingElementType,
                                       int size) {
        Objects.requireNonNull(backingElementType);
        return InternalMonotonic.ofList(backingElementType, size);
    }

    /**
     * {@return a new immutable, lazy {@linkplain MonotonicMap} where the monotonic
     * element's values can be backed by the provided {@code backingValueType} and where
     * the {@linkplain Map#keySet() keys} are the same as the provided {@code keys}}
     *
     * @param backingValueType a class literal that (optionally) can be used to store a
     *                         bound monotonic value
     * @param keys             the keys in the map
     * @param <K>              the type of keys maintained by the returned map
     * @param <V>              the type of the monotonic values in the returned map
     */
    static <K, V> MonotonicMap<K, V> ofMap(Class<V> backingValueType,
                                           Collection<? extends K> keys) {
        Objects.requireNonNull(keys);
        return InternalMonotonic.ofMap(backingValueType, keys);
    }

}
