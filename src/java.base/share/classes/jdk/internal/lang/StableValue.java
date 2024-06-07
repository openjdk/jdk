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

import jdk.internal.lang.stable.StableValueImpl;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * A thin, atomic, thread-safe, set-at-most-once, stable value holder eligible for
 * certain JVM optimizations if set to a value.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 <p>
 * To create a new fresh (unset) StableValue, use the {@linkplain StableValue#newInstance()}
 * factory.
 * <p>
 * A StableValue can be created and computed by a background thread like this:
 * {@snippet lang = java:
 *     StableValue<Value> stableValue = StableValues.ofBackground(
 *         Thread.ofVirtual().factory(), Value::new);
 *}
 * A new background thread will be created from a factory (e.g. `Thread.ofVirtual.factory`)
 * and said thread will compute the returned StableValue's value using a supplier
 * (e.g. `Value::new`).
 * <p>
 * A List of stable values with a given {@code size} can be created the following way:
 * {@snippet lang = java :
 *     List<StableValue<E>> list = StableValues.ofList(size);
 * }
 * The list can be used to model stable arrays of one dimensions. If two or more
 * dimensional arrays are to be modeled, List of List of ... of StableValue can be used.
 * <p>
 * A Map of stable values with a given set of {@code keys} can be created like this:
 * {@snippet lang = java :
 *     Map<K, StableValue<V>> map = StableValues.ofMap(keys);
 * }
 * A memoized Supplier, where the given {@code original} supplier is guaranteed to be
 * successfully invoked at most once even in a multi-threaded environment, can be
 * created like this:
 * {@snippet lang = java :
 *     static <T> Supplier<T> memoizedSupplier(Supplier<T> original) {
 *         Objects.requireNonNull(original);
 *         final StableValue<T> stable = StableValue.newInstance();
 *         return () -> stable.computeIfUnset(original);
 *     }
 * }
 * A memoized IntFunction, for the allowed given {@code size} values and where the
 * given {@code original} IntFunction is guaranteed to be successfully invoked at most
 * once per inout index even in a multi-threaded environment, can be created like this:
 * {@snippet lang = java :
 *     static <R> IntFunction<R> memoizedIntFunction(int size,
 *                                                   IntFunction<? extends R> original) {
 *         List<StableValue<R>> backing = StableValues.ofList(size);
 *         return i -> backing.get(i)
 *                       .computeIfUnset(() -> original.apply(i));
 *     }
 * }
 * A memoized Function, for the allowed given {@code input} values and where the
 * given {@code original} function is guaranteed to be successfully invoked at most
 * once per input value even in a multi-threaded environment, can be created like this:
 * {@snippet lang = java :
 *     static <T, R> Function<T, R> memoizedFunction(Set<T> inputs,
 *                                                   Function<? super T, ? extends R> original) {
 *         Map<T, StableValue<R>> backing = StableValues.ofMap(keys);
 *         return t -> {
 *             if (!backing.containsKey(t)) {
 *                 throw new IllegalArgumentException("Input not allowed: "+t);
 *             }
 *             return backing.get(t)
 *                         .computeIfUnset(() -> original.apply(t));
 *         };
 *     }
 * }
 * <p>
 * The constructs above are eligible for similar JVM optimizations as the StableValue
 * itself.
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
 * @since 24
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


    // Factory

    /**
     * {@return a fresh stable value with an unset value}
     *
     * @param <T> the value type to set
     */
    static <T> StableValue<T> newInstance() {
        return StableValueImpl.newInstance();
    }

}