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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A thin, atomic, thread-safe, set-at-most-once, stable value holder eligible for
 * certain JVM optimizations if set to a value.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 * <p>
 * To create a new fresh (unset) StableValue, use the {@linkplain StableValue#newInstance()}
 * factory.
 * <p>
 * The utility class {@linkplain StableValues} contains a number of convenience methods
 * for creating constructs involving StableValue:
 * <p>
 * A List of StableValue elements with a given {@code size} can be created the following way:
 * {@snippet lang = java :
 *     List<StableValue<E>> list = StableValues.ofList(size);
 * }
 * The list can be used to model stable one-dimensional arrays. If two- or more
 * dimensional arrays are to be modeled, a List of List of ... of StableValue can be used.
 * <p>
 * A Map with a given set of {@code keys} associated with StableValue objects can be
 * created like this:
 * {@snippet lang = java :
 *     Map<K, StableValue<V>> map = StableValues.ofMap(keys);
 * }
 * A <em>memoized</em> Supplier, where a given {@code original} Supplier is guaranteed to
 * be successfully invoked at most once even in a multithreaded environment, can be
 * created like this:
 * {@snippet lang = java :
 *     Supplier<T> memoized = StableValues.memoizedSupplier(original, null);
 * }
 * The memoized supplier can also be lazily computed by a fresh background thread if a
 * thread factory is provided as a second parameter as shown here:
 * {@snippet lang = java :
 *     Supplier<T> memoized = StableValues.memoizedSupplier(original, Thread.ofVirtual().factory());
 * }
 * <p>
 * A memoized IntFunction, for the allowed given {@code size} values {@code [0, size)}
 * and where the given {@code original} IntFunction is guaranteed to be successfully
 * invoked at most once per inout index even in a multithreaded environment, can be
 * created like this:
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
 * once per input value even in a multithreaded environment, can be created like this:
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
 * class itself.
 * <p>
 * All methods that can set the stable value's holder value are guarded such that
 * competing set operations (by other threads) will block if another set operation is
 * already in progress.
 * <p>
 * Except for a StableValue's holder value itself, all method parameters must be
 * <em>non-null</em> or a {@link NullPointerException} will be thrown.
 *
 * @param <T> type of the holder value
 *
 * @since 24
 */
public sealed interface StableValue<T>
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the holder value was set to the provided {@code value},
     * otherwise returns {@code false}}
     *
     * @param value to set (nullable)
     */
    boolean trySet(T value);

    /**
     * {@return the set holder value (nullable) if set, otherwise return the
     * {@code other} value}
     * @param other to return if the stable holder value is not set
     */
    T orElse(T other);

    /**
     * {@return the set holder value if set, otherwise throws {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is set
     */
    T orElseThrow();

    /**
     * {@return {@code true} if a holder value is set, {@code false} otherwise}
     */
    boolean isSet();

    /**
     * If the holder value is unset, attempts to compute the holder value using the
     * provided {@code supplier} and enters the result into the holder value.
     *
     * <p>If the {@code supplier} itself throws an (unchecked) exception, the exception
     * is rethrown, and no holder value is set. The most common usage is to construct a
     * new object serving as an initial value or memoized result, as in:
     *
     * <pre> {@code
     * T t = stable.computeIfUnset(T::new);
     * }</pre>
     *
     * @implSpec
     * The implementation of this method is equivalent to the following steps for this
     * {@code stable} and a given non-null {@code supplier}:
     *
     * <pre> {@code
     * if (stable.isSet()) {
     *     return stable.getOrThrow();
     * }
     * T newValue = supplier.get();
     * stable.trySet(newValue);
     * return newValue;
     * }</pre>
     * Except, the method is atomic and thread-safe with respect to this and all other
     * methods that can set this StableValue's holder value.
     *
     * @param supplier the supplier to be used to compute a holder value
     * @return the current (existing or computed) holder value associated with
     *         this stable value
     */
    T computeIfUnset(Supplier<? extends T> supplier);

    /**
     * If the holder value is unset, attempts to compute the holder value using the
     * provided {@code mapper} applied to the provided {@code input} context and enters
     * the result into the holder value.
     *
     * <p>If the {@code mapper} itself throws an (unchecked) exception, the exception
     * is rethrown, and no holder value is set. The most common usage is to construct a
     * new object serving as an initial value or memoized result, as in:
     *
     * <pre> {@code
     * Map<K, StableValue<V>> map = StableValues.ofMap(...);
     * K key = ...;
     * T t = map.get(key)
     *          .computeIfUnset(key, Foo::valueFromKey);
     * }</pre>
     *
     * The method also allows static Functions/lambdas to be used, for example by
     * providing `this` as an {@code input} and the static Function/lambda accessing
     * properties of the `this` input.
     *
     * @implSpec
     * The implementation of this method is equivalent to the following steps for this
     * {@code stable} and a given non-null {@code mapper} and {@code inout}:
     *
     * <pre> {@code
     * if (stable.isSet()) {
     *     return stable.orElseThrow();
     * }
     * T newValue = mapper.apply(input);
     * stable.trySet(newValue);
     * return newValue;
     * }</pre>
     * Except, the method is atomic and thread-safe with respect to this and all other
     * methods that can set this StableValue's holder value.
     *
     * @param input  to be applied to the {@code mapper}
     * @param mapper the mapper to be used to compute a holder value
     * @return the current (existing or computed) holder value associated with
     *         this stable value
     */
    <I> T computeIfUnset(I input, Function<? super I, ? extends T> mapper);

    // Convenience methods

    /**
     * Sets the holder value to the provided {@code value}, or, if already set,
     * throws {@linkplain IllegalStateException}}
     *
     * @param value to set (nullable)
     * @throws IllegalStateException if a holder value is already set
     */
    default void setOrThrow(T value) {
        if (!trySet(value)) {
            throw new IllegalStateException("Cannot set the holder value to " + value +
                    " because a holder value is alredy set: " + this);
        }
    }


    // Factory

    /**
     * {@return a fresh stable value with an unset holder value}
     *
     * @param <T> type of the holder value
     */
    static <T> StableValue<T> newInstance() {
        return StableValueImpl.newInstance();
    }

}