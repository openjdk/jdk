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

package jdk.internal.lang;

import jdk.internal.access.SharedSecrets;
import jdk.internal.lang.stable.StableUtil;
import jdk.internal.lang.stable.StableValueImpl;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A thin, atomic, thread-safe, set-at-most-once, stable value holder eligible for
 * certain JVM optimizations if set to a value.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 <p>
 * To create a new fresh (unset) StableValue, use the {@linkplain StableValue#of()}
 * factory.
 * <p>
 * All methods that can set the stable value's value are guarded such that competing
 * set operations (by other threads) will block if another set operation is
 * already in progress.
 * <p>
 * Except for a StableValue's value itself, all method parameters must be <em>non-null</em>
 * or a {@link NullPointerException} will be thrown.
 *
 * @param <T> type of the wrapped value
 *
 * @since 23
 */
public sealed interface StableValue<T>
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the stable value was set to the provided {@code value},
     * otherwise returns {@code false}}
     *
     * @param value to set (nullable)
     */
    boolean trySet(T value);

    /**
     * {@return the set value (nullable) if set, otherwise return the {@code other} value}
     * @param other to return if the stable value is not set
     */
    T orElse(T other);

    /**
     * {@return the set value if set, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is set
     */
    T orElseThrow();

    /**
     * {@return {@code true} if a value is set, {@code false} otherwise}
     */
    boolean isSet();

    /**
     * If a value is set, performs the given action with the set value,
     * otherwise does nothing.
     *
     * @param action the action to be performed, if a value is set
     */
    void ifSet(Consumer<? super T> action);

    /**
     * If the stable value is unset, attempts to compute its value using the given
     * supplier function and enters it into this stable value.
     *
     * <p>If the supplier function itself throws an (unchecked) exception, the exception
     * is rethrown, and no value is set. The most common usage is to construct a new
     * object serving as an initial value or memoized result, as in:
     *
     * <pre> {@code
     * T t = stable.computeIfUnset(T::new);
     * }</pre>
     *
     * @implSpec
     * The default implementation is equivalent to the following steps for this
     * {@code stable}:
     *
     * <pre> {@code
     * if (stable.isSet()) {
     *     return stable.getOrThrow();
     * }
     * T newValue = supplier.apply(key);
     * stable.trySet(newValue);
     * return newValue;
     * }</pre>
     * Except, the method is atomic and thread-safe.
     *
     * @param supplier the mapping supplier to compute a value
     * @return the current (existing or computed) value associated with
     *         the stable value
     */
    T computeIfUnset(Supplier<? extends T> supplier);

    /**
     * If the stable value is unset, attempts to compute its value using the given
     * {@code input} parameter and the provided {@code mapper} function and enters
     * it into this stable value.
     *
     * <p>If the mapper function itself throws an (unchecked) exception, the exception
     * is rethrown, and no value is set. The most common usage is to construct a new
     * object serving as an initial value or memoized result in a {@code mop}, as in:
     *
     * <pre> {@code
     * Map<K, StableValue<T>> map = ...
     * K key = ...
     * T t = map.get(key).computeIfUnset(key, k -> new T(k));
     * }</pre>
     *
     * @implSpec
     * The default implementation is equivalent to the following steps for this
     * {@code stable} value:
     *
     * <pre> {@code
     * if (stable.isSet()) {
     *     return stable.getOrThrow();
     * }
     * T newValue = mapper.apply(input);
     * stable.trySet(newValue);
     * return newValue;
     * }</pre>
     * Except, the method is atomic and thread-safe.
     *
\    * @param input to be used with the provided {@code mapper}
     * @param mapper the mapping function to compute a value from the {@code input}
     * @return the current (existing or computed) value associated with
     *         the stable value
     */
    <I> T computeIfUnset(I input, Function<? super I, ? extends T> mapper);


    // Convenience methods

    /**
     * Sets the stable value to the provided {@code value}, or, if already set to a
     * non-null value, throws {@linkplain IllegalStateException}}
     *
     * @param value to set (nullable)
     * @throws IllegalArgumentException if a non-null value is already set
     */
    default void setOrThrow(T value) {
        if (!trySet(value)) {
            throw new IllegalStateException("Cannot set value to " + value +
                    " because a value is alredy set: " + this);
        }
    }



    // Factories

    /**
     * {@return a fresh stable value with an unset ({@code null}) value}
     *
     * @param <T> the value type to set
     */
    static <T> StableValue<T> of() {
        return StableValueImpl.of();
    }

    /**
     * {@return a lazily computed, atomic, thread-safe, set-at-most-once-per-index,
     * List of stable elements eligible for certain JVM optimizations}
     *
     * @param size   the size of the returned list
     * @param mapper to invoke when an element is to be computed
     * @param <E> the {@code List}'s element type
     */
    static <E> List<E> ofList(int size,
                              IntFunction<? extends E> mapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        if (size == 0) {
            return List.of();
        }
        Objects.requireNonNull(mapper);
        List<StableValue<E>> backing = Stream.generate(StableValue::<E>of)
                .limit(size)
                .toList();

        return SharedSecrets.getJavaUtilCollectionAccess()
                .listFromStable(backing, mapper);
    };

    /**
     * {@return a lazily computed, atomic, thread-safe, set-at-most-once-per-key,
     * Map of stable elements eligible for certain JVM optimizations}
     * @param keys   the keys in the {@code Map}
     * @param mapper to invoke when a value is to be computed
     * @param <K> the {@code Map}'s key type
     * @param <V> the {@code Map}'s value type
     */
    static <K, V> Map<K, V> ofMap(Set<K> keys,
                                  Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        Map<K, StableValue<V>> backing = keys.stream()
                .collect(Collectors.toMap(Function.identity(), _ -> StableValue.of()));
        return SharedSecrets.getJavaUtilCollectionAccess()
                .mapFromStable(backing, mapper);
    }

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value of the provided {@code original} supplier upon being first
     * accessed via {@linkplain Supplier#get()}}
     * <p>
     * The provided {@code original} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * {@linkplain Supplier#get()} method when a value is already under computation
     * will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a StackOverflowError will be thrown when the returned
     * Supplier's {@linkplain Function#apply(Object)}} method is invoked.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller. Subsequent read operations will incur a new invocation
     * of the provided {@code original}.
     *
     * @param original supplier
     * @param <T> the type of results supplied by the returned supplier
     */
    static <T> Supplier<T> memoizedSupplier(Supplier<T> original) {
        Objects.requireNonNull(original);
        final StableValue<T> stable = StableValue.of();
        return () -> stable.computeIfUnset(original);
    }

    /**
     * {@return a memoized IntFunction backed by a {@linkplain #ofList(int, IntFunction)
     * stable list} where the provided {@code original} will be invoked at most once per
     * index}
     * @param size     the allowed input values, [0, size)
     * @param original to invoke when an element is to be computed
     * @param <R>      the type of the result of the function
     */
    static <R> IntFunction<R> memoizedIntFunction(int size,
                                                  IntFunction<? extends R> original) {
        return ofList(size, original)::get;
    }

    /**
     * {@return a memoized Function backed by a {@linkplain #ofMap(Set, Function)
     * stable map} where the provided {@code original} will be invoked at most once per
     * index}
     * @param inputs   the allowed input values
     * @param original to invoke when an element is to be computed
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     */
    static <T, R> Function<T, R> memoizedFunction(Set<T> inputs,
                                                  Function<? super T, ? extends R> original) {
        final Map<T, R> backing = ofMap(inputs, original);
        return t -> {
            if (!backing.containsKey(t)) {
                throw new IllegalArgumentException("Input not allowed: "+t);
            }
            return backing.get(t);
        };
    }


}