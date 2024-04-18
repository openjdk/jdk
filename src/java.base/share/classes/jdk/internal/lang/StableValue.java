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

import jdk.internal.lang.stable.StableAccess;
import jdk.internal.lang.stable.StableValueElement;
import jdk.internal.lang.stable.StableValueImpl;

import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * An atomic, thread-safe, stable value holder for which the value can be set at most once.
 * <p>
 * Stable values are eligible for constant folding and other optimizations by the JVM.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 <p>
 * To create a new fresh (unset) StableValue, use the {@linkplain StableValue#of()}
 * factory.
 * <p>
 * To create collections of <em>wrapped stable elements</em>, that, in turn, are also
 * eligible for constant folding optimizations, the following factories can be used:
 * <ul>
 *     <li>{@linkplain StableValue#ofList(int)}</li>
 *     <li>{@linkplain StableValue#ofMap(Set)}</li>
 *</ul>
 * <p>
 * Except for a StableValue's value itself, all method parameters must be <em>non-null</em>
 * and all collections provided must only contain <em>non-null</em> elements or a
 * {@link NullPointerException} will be thrown.
 *
 * @param <V> value type
 * @since 23
 */
public sealed interface StableValue<V>
        permits StableValueImpl,
        StableValueElement {

    /**
     * {@return the set value (nullable) if set, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is set
     */
    V orThrow();

    /**
     * {@return {@code true} if a value is set, otherwise {@code false}}
     */
    boolean isSet();

    /**
     * Sets the stable value to the provided (nullable) {@code value} or throws an
     * {@linkplain IllegalStateException} if a value is already set.
     *
     * @param value to set (nullable)
     * @throws IllegalStateException if a value is already set
     */
    void setOrThrow(V value);

    /**
     * If no value is set, sets the stable value to the provided (nullable)
     * {@code value}, returning the (pre-existing or newly set) value.
     * <p>
     * If several threads invoke this method simultaneously, only one thread will succeed
     * in setting a value and that (witness) value will be returned to all threads.
     *
     * @param value to set (nullable)
     * @return the bound value
     */
    V setIfUnset(V value);

    /**
     * If no value {@linkplain #isSet() is set}, attempts to compute and set a
     * new (nullable) value using the provided {@code supplier}, returning the
     * (pre-existing or newly set) value.
     *
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * value is set. The most common usage is to construct a new object serving as a
     * lazily computed value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = stable.computeIfUnset(Value::new);
     * }</pre>
     *
     * @implSpec The implementation logic is equivalent to the following steps for this
     * {@code stable}:
     *
     * <pre> {@code
     * if (stable.isBound()) {
     *     return stable.get();
     * } else {
     *     V newValue = supplier.get();
     *     stable.setOrThrow(newValue);
     *     return newValue;
     * }
     * }</pre>
     * Except it is atomic, thread-safe and will only return the same witness value
     * regardless if invoked by several threads. Also, the provided {@code supplier}
     * will only be invoked once even if invoked from several threads.
     *
     * // Todo: Should we wrap supplier exceptions into a specific exception type?
     *
     * @param supplier to be used for computing a value
     * @return the current (pre-existing or computed) value
     */
    V computeIfUnset(Supplier<? extends V> supplier);

    // Factories

    /**
     * {@return a fresh stable value with an unset value}
     *
     * @param <V> the value type to set
     */
    static <V> StableValue<V> of() {
        return StableValueImpl.of();
    }

    /**
     * {@return a fresh stable value with an unset value where the returned stable's
     * value is computed in a separate fresh background thread using the provided
     * {@code supplier}}
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is ignored, and no
     * value is set.
     *
     * @param <V>      the value type to set
     * @param supplier to be used for computing a value
     * @see StableValue#of
     */
    static <V> StableValue<V> ofBackground(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return StableValueImpl.ofBackground(supplier);
    }

    // Collection factories

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable,
     * {@linkplain List} containing {@code size} {@linkplain StableValue } elements}
     * <p>
     * If non-empty, neither the returned list nor its elements are {@linkplain Serializable}.
     * <p>
     * The returned list and its elements are eligible for constant folding and other
     * optimizations by the JVM and is equivalent to:
     * {@snippet lang = java:
     * List<StableValue<V>> list = Stream.generate(StableValue::<V>of)
     *         .limit(size)
     *         .toList();
     *}
     * Except it requires less storage, does not return stable value instances with the
     * same identity, and is likely to exhibit better performance.
     * <p>
     * This static factory methods return list instances (and with all their elements)
     * that are <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>,
     * immutable and thread-safe. Programmers should not use list and element instances
     * for synchronization, or unpredictable behavior may occur. For example, in a
     * future release, synchronization may fail. Consequently,
     * query methods {@linkplain List#contains(Object)},
     * {@linkplain List#containsAll(Collection)} (if non-empty),
     * {@linkplain List#indexOf(Object)}, {@linkplain List#lastIndexOf(Object)}, and
     * methods that relies on these method or similar methods will always indicate
     * no match.
     *
     * @param <V>  the generic type of the stable value elements in the returned {@code List}
     * @param size the number of elements in the list
     * @throws IllegalArgumentException if the provided {@code size} is negative
     *
     * @since 23
     */
    static <V> List<StableValue<V>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return StableAccess.ofList(size);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, value-stable,
     * {@linkplain Map } where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided set of {@code keys} and where the
     * stable values are, in turn, lazily computed upon being accessed
     * (e.g. via {@linkplain Map#get(Object) get(key)})}
     * <p>
     * If non-empty, neither the returned map nor its values are {@linkplain Serializable}.
     * <p>
     * The returned map and its values are eligible for constant folding and other
     * optimizations by the JVM and is equivalent to:
     * {@snippet lang = java:
     * Map<K, StableValue<V>> map = Map.copyOf(keys.stream()
     *         .distinct()
     *         .map(Objects::requireNonNull)
     *         .collect(Collectors.toMap(Function.identity(), _ -> StableValue.of())));
     * }
     * Except it requires less storage, does not return stable value instances with the
     * same identity, and is likely to exhibit better performance.
     * <p>
     * This static factory methods return map instances (and with all their values)
     * that are <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>,
     * immutable and thread-safe. Programmers should not use map and value instances for
     * synchronization, or unpredictable behavior may occur. For example, in a
     * future release, synchronization may fail. Consequently,
     * the query methods {@linkplain Map#containsValue(Object)} and methods that relies
     * on this and similar method will always indicate no match.
     * <p>
     * As an emerging property, providing an {@linkplain EnumSet} as {@code keys} will
     * make the returned Map eligible for certain additional optimizations.
     *
     * @param keys the keys in the map
     * @param <K>  the type of keys maintained by the returned map
     * @param <V>  the type of mapped StableValue values
     */
    static <K, V> Map<K, StableValue<V>> ofMap(Set<? extends K> keys) {
        Objects.requireNonNull(keys);
        return StableAccess.ofMap(keys);
    }

    // Support functions for stable collections

    /**
     * If no value {@linkplain #isSet() is set} for the StableValue at the provided
     * {@code index}, attempts to compute and set it as per
     * {@linkplain StableValue#computeIfUnset(Supplier)} by applying the provided
     * {@code mapper}.
     * <p>
     * This is equivalent to:
     * {@snippet lang = java:
     * StableValue<V> stable = list.get(index);
     * if (stable.isSet()) {
     *     return stable.orThrow();
     * }
     * Supplier<V> supplier = () -> mapper.apply(index);
     * return stable.computeIfUnset(supplier);
     *}
     * Except it guarantees that the provided mapper is invoked at most once per distinct
     * index and might be more resource efficient and performant.
     *
     * @param list   from which to get a StableValue
     * @param index  for the StableValue
     * @param mapper to apply if the StableValue at the provided {@code index} is
     *               {@linkplain StableValue#isSet() not set}
     * @return the current (pre-existing or computed) value at the provided {@code index}
     * @param <V> the StableValue type to set
     * @throws IndexOutOfBoundsException if the provided {@code index} is less than
     *         zero or {@code index >= list.size()}
     */
    static <V> V computeIfUnset(List<StableValue<V>> list,
                                int index,
                                IntFunction<? extends V> mapper) {
        Objects.requireNonNull(list);
        Objects.checkIndex(index, list.size());
        Objects.requireNonNull(mapper);
        return StableAccess.computeIfUnset(list, index, mapper);
    }

    /**
     * If no value {@linkplain #isSet() is set} for the StableValue for the provided
     * {@code key}, attempts to compute and set it as per
     * {@linkplain StableValue#computeIfUnset(Supplier)} by applying the provided
     * {@code mapper}.
     * <p>
     * This is equivalent to:
     * {@snippet lang = java:
     * StableValue<V> stable = map.get(key);
     * if (stable == null) {
     *      throw new NoSuchElementException("Unknown key: "+key);
     * }
     * if (stable.isSet()) {
     *     return stable.orThrow();
     * }
     * Supplier<V> supplier = () -> mapper.apply(key);
     * return stable.computeIfUnset(supplier);
     *}
     * Except it guarantees that the provided mapper is invoked at most once per distinct
     * key and might be more resource efficient and performant.
     *
     * @param map    from which to get a Stab;eValue
     * @param key    associated with a StableValue
     * @param mapper to apply if the StableValue associated with the provided {@code key}
     *               is {@linkplain StableValue#isSet() not set}
     * @return the current (pre-existing or computed) value for the provided {@code key}
     * @param <K> the type of keys maintained by this map
     * @param <V> the StableValue value type to set
     * @throws NoSuchElementException if the provided {@code map} does not
     *         {@linkplain Map#containsKey(Object) contain} the provided {@code key}
     */
    static <K, V> V computeIfUnset(Map<K, StableValue<V>> map,
                                   K key,
                                   Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(key);
        Objects.requireNonNull(mapper);
        return StableAccess.computeIfUnset(map, key, mapper);
    }

    // Memoized factories

    /**
     * {@return a new <em>memoized</em> {@linkplain Supplier} backed by an internal
     * stable value where the provided {@code original} supplier will only be invoked
     * at most once}
     *
     * @param original the original Suppler to convert to a memoized Supplier
     * @param <T>      the memoized type
     */
    static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        Objects.requireNonNull(original);
        StableValue<T> stable = StableValue.of();
        return StableAccess.ofSupplier(stable, original);
    }

    /**
     * {@return a new <em>memoized</em> {@linkplain IntFunction } backed by an internal
     * stable list of the provided {@code size} where the provided {@code original}
     * IntFunction will only be invoked at most once per distinct {@code int} value}
     *
     * @param size     the number of elements in the backing list
     * @param original the original IntFunction to convert to a memoized IntFunction
     * @param <R>      the return type of the IntFunction
     */
    static <R> IntFunction<R> ofIntFunction(int size,
                                            IntFunction<? extends R> original) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(original);
        List<StableValue<R>> stableList = StableValue.ofList(size);
        return StableAccess.ofIntFunction(stableList, original);
    }

    /**
     * {@return a new <em>memoized</em> {@linkplain Function } backed by an internal
     * stable map with the provided {@code inputs} keys where the provided
     * {@code original} Function will only be invoked at most once per distinct input}
     *
     * @param original the original Function to convert to a memoized Function
     * @param inputs   the potential input values to the Function
     * @param <T>      the type of input values
     * @param <R>      the return type of the function
     */
    static <T, R> Function<T, R> ofFunction(Set<? extends T> inputs,
                                            Function<? super T, ? extends R> original) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(original);
        Map<T, StableValue<R>> stableMap = StableValue.ofMap(inputs);
        return StableAccess.ofFunction(stableMap, original);
    }

}
